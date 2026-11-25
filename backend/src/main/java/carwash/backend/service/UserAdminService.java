package carwash.backend.service;

import carwash.backend.domain.AuditEventType;
import carwash.backend.domain.Role;
import carwash.backend.domain.UserAccount;
import carwash.backend.dto.AdminUserResponse;
import carwash.backend.dto.CreateUserRequest;
import carwash.backend.dto.PageResponse;
import carwash.backend.dto.TemporaryPasswordResponse;
import carwash.backend.dto.UpdateUserRequest;
import carwash.backend.error.BusinessException;
import carwash.backend.error.ConflictException;
import carwash.backend.error.NotFoundException;
import carwash.backend.filter.OtherSpecifications;
import carwash.backend.repository.RefreshSessionRepository;
import carwash.backend.repository.UserAccountRepository;
import carwash.backend.security.TemporaryPasswordGenerator;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

// администрирование учётных записей (только ADMIN)
@Service
public class UserAdminService {

    private final UserAccountRepository users;
    private final RefreshSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator passwordGenerator;
    private final AuditService audit;
    private final Clock clock;

    public UserAdminService(UserAccountRepository users, RefreshSessionRepository sessions,
                            PasswordEncoder passwordEncoder, TemporaryPasswordGenerator passwordGenerator,
                            AuditService audit, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.passwordGenerator = passwordGenerator;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> search(String q, Boolean active, Role role, Pageable pageable) {
        Instant now = clock.instant();
        return PageResponse.of(users.findAll(OtherSpecifications.users(q, active, role), pageable),
                u -> AdminUserResponse.from(u, now));
    }

    @Transactional(readOnly = true)
    public AdminUserResponse get(UUID id) {
        return AdminUserResponse.from(load(id), clock.instant());
    }

    @Transactional
    public TemporaryPasswordResponse create(CreateUserRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException("DUPLICATE_EMAIL", "Пользователь с email " + email + " уже существует");
        }
        String temporaryPassword = passwordGenerator.generate();
        Instant now = clock.instant();

        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setActive(true);
        user.setMustChangePassword(true);
        user.setPasswordChangedAt(now);
        user.replaceRoles(request.roles());
        users.save(user);

        audit.log(AuditEventType.USER_CREATED, EntityTypes.USER, user.getId(),
                "Создан пользователь " + email + ", роли: " + request.roles());
        return new TemporaryPasswordResponse(AdminUserResponse.from(user, now), temporaryPassword);
    }

    @Transactional
    public AdminUserResponse update(UUID id, UpdateUserRequest request, UUID currentAdminId) {
        UserAccount user = load(id);
        if (!Objects.equals(user.getVersion(), request.version())) {
            throw ConflictException.version(user.getVersion());
        }
        String email = normalizeEmail(request.email());
        if (!email.equals(user.getEmail()) && users.existsByEmail(email)) {
            throw new ConflictException("DUPLICATE_EMAIL", "Пользователь с email " + email + " уже существует");
        }
        boolean active = request.active();
        Set<Role> roles = request.roles();

        // администратор не может случайно лишить доступа сам себя
        if (user.getId().equals(currentAdminId) && (!active || !roles.contains(Role.ADMIN))) {
            throw new BusinessException("SELF_LOCKOUT", "Нельзя отключить свою учётную запись или снять с себя роль администратора");
        }
        // в системе должен остаться хотя бы один активный администратор
        boolean wasActiveAdmin = user.isActive() && user.hasRole(Role.ADMIN);
        boolean staysActiveAdmin = active && roles.contains(Role.ADMIN);
        if (wasActiveAdmin && !staysActiveAdmin && users.countActiveWithRole(Role.ADMIN) <= 1) {
            throw new BusinessException("LAST_ADMIN", "Нельзя отключить последнего активного администратора");
        }

        StringBuilder changes = new StringBuilder();
        if (!email.equals(user.getEmail())) {
            changes.append(" email: ").append(user.getEmail()).append(" -> ").append(email).append(';');
        }
        if (!user.getRoles().equals(roles)) {
            changes.append(" роли: ").append(user.getRoles()).append(" -> ").append(roles).append(';');
        }
        if (user.isActive() != active) {
            changes.append(active ? " учётная запись включена;" : " учётная запись отключена;");
        }
        if (!user.getFullName().equals(request.fullName().trim())) {
            changes.append(" имя изменено;");
        }

        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.replaceRoles(roles);
        user.setActive(active);
        if (!active) {
            // отключённый пользователь теряет все сессии сразу
            sessions.revokeAllForUser(user.getId(), clock.instant());
        }
        users.flush();

        audit.log(AuditEventType.USER_UPDATED, EntityTypes.USER, user.getId(),
                "Изменён пользователь " + email + ":" + (changes.isEmpty() ? " без изменений" : changes));
        return AdminUserResponse.from(user, clock.instant());
    }

    // сброс пароля: новый временный пароль, обязательная смена, разблокировка, отзыв сессий
    @Transactional
    public TemporaryPasswordResponse resetPassword(UUID id) {
        UserAccount user = load(id);
        String temporaryPassword = passwordGenerator.generate();
        Instant now = clock.instant();
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setMustChangePassword(true);
        user.setPasswordChangedAt(now);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        sessions.revokeAllForUser(user.getId(), now);
        users.flush();
        audit.log(AuditEventType.PASSWORD_RESET, EntityTypes.USER, user.getId(),
                "Сброшен пароль пользователя " + user.getEmail());
        return new TemporaryPasswordResponse(AdminUserResponse.from(user, now), temporaryPassword);
    }

    private UserAccount load(UUID id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("Пользователь не найден"));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
