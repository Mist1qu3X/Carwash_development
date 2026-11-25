package carwash.backend.controller;

import carwash.backend.config.AppProperties;
import carwash.backend.dto.ChangePasswordRequest;
import carwash.backend.dto.LoginRequest;
import carwash.backend.dto.RefreshRequest;
import carwash.backend.dto.TokenResponse;
import carwash.backend.dto.UserResponse;
import carwash.backend.security.AuthenticatedUser;
import carwash.backend.service.AuthService;
import carwash.backend.service.RequestInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;

/**
 * Авторизация. Refresh-токен возвращается в теле ответа (для Android-клиента)
 * и дублируется в HttpOnly-cookie (для web-интерфейса, чтобы JS его не видел).
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Авторизация")
public class AuthController {

    static final String REFRESH_COOKIE = "carwash_refresh";
    private static final String COOKIE_PATH = "/api/auth";

    private final AuthService authService;
    private final AppProperties properties;
    private final Clock clock;

    public AuthController(AuthService authService, AppProperties properties, Clock clock) {
        this.authService = authService;
        this.properties = properties;
        this.clock = clock;
    }

    @PostMapping("/login")
    @Operation(summary = "Вход по email и паролю")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return withCookie(authService.login(request, RequestInfo.from(http)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Новый access JWT по refresh-токену (тело или cookie)")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody(required = false) RefreshRequest request,
                                                 @CookieValue(name = REFRESH_COOKIE, required = false) String cookieToken,
                                                 HttpServletRequest http) {
        return withCookie(authService.refresh(pickToken(request, cookieToken), RequestInfo.from(http)));
    }

    @PostMapping("/logout")
    @Operation(summary = "Выход с отзывом refresh-сессии")
    public ResponseEntity<Void> logout(@Valid @RequestBody(required = false) RefreshRequest request,
                                       @CookieValue(name = REFRESH_COOKIE, required = false) String cookieToken) {
        authService.logout(pickToken(request, cookieToken));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .build();
    }

    @GetMapping("/me")
    @Operation(summary = "Данные текущего пользователя")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return authService.me(user.id());
    }

    @PostMapping("/change-password")
    @Operation(summary = "Смена пароля (в том числе обязательная смена временного)")
    public ResponseEntity<TokenResponse> changePassword(@AuthenticationPrincipal AuthenticatedUser user,
                                                        @Valid @RequestBody ChangePasswordRequest request,
                                                        HttpServletRequest http) {
        return withCookie(authService.changePassword(user.id(), request, RequestInfo.from(http)));
    }

    private static String pickToken(RefreshRequest request, String cookieToken) {
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            return request.refreshToken();
        }
        return cookieToken;
    }

    private ResponseEntity<TokenResponse> withCookie(TokenResponse tokens) {
        Duration maxAge = Duration.between(clock.instant(), tokens.refreshExpiresAt());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(tokens.refreshToken(), maxAge).toString())
                .body(tokens);
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(properties.security().cookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
