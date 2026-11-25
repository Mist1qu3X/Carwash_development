package carwash.backend.security;

import carwash.backend.domain.UserAccount;
import carwash.backend.repository.UserAccountRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Locale;

/**
 * Используется только цепочкой фильтров Swagger (HTTP Basic):
 * открыть /swagger-ui.html может только активный администратор со сменённым паролем.
 */
@Service
public class SwaggerUserDetailsService implements UserDetailsService {

    private final UserAccountRepository userRepository;
    private final Clock clock;

    public SwaggerUserDetailsService(UserAccountRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        UserAccount user = userRepository.findByEmail(username.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));
        return User.withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(AuthenticatedUser.from(user).authorities())
                .disabled(!user.isActive())
                .accountLocked(user.isLocked(clock.instant()))
                .build();
    }
}
