package carwash.backend.dto;

import carwash.backend.domain.Role;
import carwash.backend.domain.UserAccount;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// данные текущего пользователя (GET /api/auth/me); пароль и хеш не возвращаются
public record UserResponse(UUID id, String email, String fullName, List<Role> roles,
                           boolean mustChangePassword, Instant lastLoginAt) {

    public static UserResponse from(UserAccount user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getRoles().stream().sorted().toList(), user.isMustChangePassword(), user.getLastLoginAt());
    }
}
