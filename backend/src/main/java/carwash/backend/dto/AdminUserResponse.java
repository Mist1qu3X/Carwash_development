package carwash.backend.dto;

import carwash.backend.domain.Role;
import carwash.backend.domain.UserAccount;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminUserResponse(UUID id, String email, String fullName, List<Role> roles, boolean active,
                                boolean mustChangePassword, boolean locked, Instant lockedUntil,
                                int failedLoginAttempts, Instant lastLoginAt, Instant createdAt,
                                Instant updatedAt, Long version) {

    public static AdminUserResponse from(UserAccount u, Instant now) {
        return new AdminUserResponse(u.getId(), u.getEmail(), u.getFullName(),
                u.getRoles().stream().sorted().toList(), u.isActive(), u.isMustChangePassword(),
                u.isLocked(now), u.getLockedUntil(), u.getFailedLoginAttempts(), u.getLastLoginAt(),
                u.getCreatedAt(), u.getUpdatedAt(), u.getVersion());
    }
}
