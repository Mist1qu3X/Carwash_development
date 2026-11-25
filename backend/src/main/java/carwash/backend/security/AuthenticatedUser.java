package carwash.backend.security;

import carwash.backend.domain.Role;
import carwash.backend.domain.UserAccount;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Текущий пользователь, которого JwtAuthenticationFilter кладёт в SecurityContext.
 * Пока не сменён временный пароль, ролей у пользователя нет —
 * доступны только /api/auth/me, /api/auth/change-password и /api/auth/logout.
 */
public record AuthenticatedUser(UUID id, String email, String fullName, Set<Role> roles, boolean mustChangePassword) {

    public static AuthenticatedUser from(UserAccount user) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getFullName(),
                Set.copyOf(user.getRoles()), user.isMustChangePassword());
    }

    public List<GrantedAuthority> authorities() {
        if (mustChangePassword) {
            return List.of();
        }
        return roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role.authority()))
                .toList();
    }

    public boolean isAdmin() {
        return roles.contains(Role.ADMIN);
    }
}
