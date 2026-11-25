package carwash.backend.service;

import carwash.backend.config.AppProperties;
import carwash.backend.domain.AuditEventType;
import carwash.backend.domain.RefreshSession;
import carwash.backend.domain.UserAccount;
import carwash.backend.dto.ChangePasswordRequest;
import carwash.backend.dto.LoginRequest;
import carwash.backend.dto.TokenResponse;
import carwash.backend.dto.UserResponse;
import carwash.backend.error.ApiException;
import carwash.backend.error.BusinessException;
import carwash.backend.error.NotFoundException;
import carwash.backend.error.TooManyAttemptsException;
import carwash.backend.error.UnauthorizedException;
import carwash.backend.repository.RefreshSessionRepository;
import carwash.backend.repository.UserAccountRepository;
import carwash.backend.security.JwtService;
import carwash.backend.security.PasswordPolicy;
import carwash.backend.security.TokenHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Вход, обновление токенов, выход и смена пароля.
 * Методы входа помечены noRollbackFor: при ошибке входа счётчик неудачных попыток
 * и запись аудита всё равно должны сохраниться.
 */
@Service
public class AuthService {

    private static final String BAD_CREDENTIALS = "Неверный email или пароль";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final UserAccountRepository users;
    private final RefreshSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PasswordPolicy passwordPolicy;
    private final AuditService audit;
    private final Clock clock;
    private final int maxFailedAttempts;
    private final Duration lockDuration;
    private final Duration refreshTtl;
    // хеш-заглушка: при несуществующем email тоже тратим время на BCrypt,
    // чтобы по времени ответа нельзя было понять, есть ли такой пользователь
    private final String dummyHash;

    public AuthService(UserAccountRepository users, RefreshSessionRepository sessions, PasswordEncoder passwordEncoder,
                       JwtService jwtService, PasswordPolicy passwordPolicy, AuditService audit,
                       AppProperties properties, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.passwordPolicy = passwordPolicy;
        this.audit = audit;
        this.clock = clock;
        this.maxFailedAttempts = properties.security().maxFailedAttempts();
        this.lockDuration = Duration.ofMinutes(properties.security().lockMinutes());
        this.refreshTtl = Duration.ofDays(properties.jwt().refreshDays());
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse login(LoginRequest request, RequestInfo info) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Instant now = clock.instant();
        UserAccount user = users.findByEmail(email).orElse(null);

        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            audit.logAs(AuditEventType.LOGIN_FAILED, null, email, EntityTypes.USER, null,
                    "Неудачный вход: пользователь " + email + " не найден");
            throw new UnauthorizedException("BAD_CREDENTIALS", BAD_CREDENTIALS);
        }
        if (user.isLocked(now)) {
            audit.logAs(AuditEventType.LOGIN_BLOCKED, user.getId(), email, EntityTypes.USER, user.getId(),
                    "Попытка входа в заблокированную учётную запись");
            throw new TooManyAttemptsException(lockMessage(user.getLockedUntil()));
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailedAttempt(user, now);
        }
        if (!user.isActive()) {
            audit.logAs(AuditEventType.LOGIN_FAILED, user.getId(), email, EntityTypes.USER, user.getId(),
                    "Неудачный вход: учётная запись отключена");
            throw new UnauthorizedException("ACCOUNT_DISABLED", "Учётная запись отключена. Обратитесь к администратору");
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        audit.logAs(AuditEventType.LOGIN_SUCCESS, user.getId(), email, EntityTypes.USER, user.getId(),
                user.isMustChangePassword() ? "Вход с временным паролем" : "Успешный вход");
        return issueTokens(user, info);
    }

    // неверный пароль: увеличиваем счётчик, после N попыток блокируем вход на lockMinutes
    private void registerFailedAttempt(UserAccount user, Instant now) {
        int attempts = user.getFailedLoginAttempts() + 1;
        if (attempts >= maxFailedAttempts) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(now.plus(lockDuration));
            audit.logAs(AuditEventType.LOGIN_LOCKED, user.getId(), user.getEmail(), EntityTypes.USER, user.getId(),
                    "Вход заблокирован на " + lockDuration.toMinutes() + " мин. после " + maxFailedAttempts + " неудачных попыток");
            throw new TooManyAttemptsException(lockMessage(user.getLockedUntil()));
        }
        user.setFailedLoginAttempts(attempts);
        audit.logAs(AuditEventType.LOGIN_FAILED, user.getId(), user.getEmail(), EntityTypes.USER, user.getId(),
                "Неверный пароль (попытка " + attempts + " из " + maxFailedAttempts + ")");
        throw new UnauthorizedException("BAD_CREDENTIALS", BAD_CREDENTIALS);
    }

    /**
     * Обновление access JWT по refresh-токену. Старый refresh-токен отзывается и выдаётся новый (ротация).
     * Если пришёл уже отозванный токен — возможно, его украли: отзываем все сессии пользователя.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse refresh(String rawToken, RequestInfo info) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new UnauthorizedException("REFRESH_TOKEN_MISSING", "Refresh-токен не передан");
        }
        RefreshSession session = sessions.findByTokenHash(TokenHasher.sha256(rawToken.trim()))
                .orElseThrow(() -> new UnauthorizedException("REFRESH_TOKEN_INVALID", "Refresh-токен недействителен"));
        Instant now = clock.instant();
        UserAccount user = session.getUser();

        if (session.isRevoked()) {
            int revoked = sessions.revokeAllForUser(user.getId(), now);
            audit.logAs(AuditEventType.TOKEN_REUSE, user.getId(), user.getEmail(), EntityTypes.USER, user.getId(),
                    "Повторно использован отозванный refresh-токен; отозвано сессий: " + revoked);
            throw new UnauthorizedException("REFRESH_TOKEN_REVOKED", "Сессия завершена. Войдите заново");
        }
        if (session.isExpired(now)) {
            throw new UnauthorizedException("REFRESH_TOKEN_EXPIRED", "Срок действия сессии истёк. Войдите заново");
        }
        if (!user.isActive()) {
            session.revoke(now);
            throw new UnauthorizedException("ACCOUNT_DISABLED", "Учётная запись отключена");
        }
        session.revoke(now);
        return issueTokens(user, info);
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        sessions.findByTokenHash(TokenHasher.sha256(rawToken.trim())).ifPresent(session -> {
            if (!session.isRevoked()) {
                session.revoke(clock.instant());
                UserAccount user = session.getUser();
                audit.logAs(AuditEventType.LOGOUT, user.getId(), user.getEmail(), EntityTypes.USER, user.getId(),
                        "Выход из системы");
            }
        });
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        return UserResponse.from(load(userId));
    }

    /**
     * Смена пароля (в том числе обязательная смена временного).
     * Все refresh-сессии отзываются, старые access-токены перестают действовать
     * (см. passwordChangedAt в JwtAuthenticationFilter), клиенту выдаётся новая пара токенов.
     */
    @Transactional
    public TokenResponse changePassword(UUID userId, ChangePasswordRequest request, RequestInfo info) {
        UserAccount user = load(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException("WRONG_PASSWORD", "Текущий пароль указан неверно");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BusinessException("SAME_PASSWORD", "Новый пароль должен отличаться от текущего");
        }
        passwordPolicy.check(request.newPassword(), user.getEmail());

        Instant now = clock.instant();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        user.setPasswordChangedAt(now);
        sessions.revokeAllForUser(user.getId(), now);
        audit.logAs(AuditEventType.PASSWORD_CHANGED, user.getId(), user.getEmail(), EntityTypes.USER, user.getId(),
                "Пользователь сменил пароль");
        return issueTokens(user, info);
    }

    private TokenResponse issueTokens(UserAccount user, RequestInfo info) {
        Instant now = clock.instant();
        String rawRefresh = TokenHasher.newToken();
        RefreshSession session = new RefreshSession();
        session.setUser(user);
        session.setTokenHash(TokenHasher.sha256(rawRefresh));
        session.setCreatedAt(now);
        session.setExpiresAt(now.plus(refreshTtl));
        session.setIpAddress(info.ipAddress());
        session.setUserAgent(info.userAgent());
        sessions.save(session);

        String accessToken = jwtService.createAccessToken(user);
        return new TokenResponse(accessToken, "Bearer", jwtService.accessTtlSeconds(), rawRefresh,
                session.getExpiresAt(), UserResponse.from(user));
    }

    private UserAccount load(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("Пользователь не найден"));
    }

    private String lockMessage(Instant lockedUntil) {
        String time = TIME.format(lockedUntil.atZone(clock.getZone() == null ? ZoneId.systemDefault() : clock.getZone()));
        return "Слишком много неудачных попыток входа. Вход заблокирован до " + time;
    }
}
