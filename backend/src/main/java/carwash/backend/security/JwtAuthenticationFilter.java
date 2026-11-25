package carwash.backend.security;

import carwash.backend.domain.UserAccount;
import carwash.backend.repository.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.temporal.ChronoUnit;

/**
 * Читает заголовок "Authorization: Bearer ...", проверяет JWT и загружает пользователя из БД.
 * Пользователь берётся из базы при каждом запросе, поэтому отключение учётной записи
 * или смена ролей действуют сразу, не дожидаясь истечения токена.
 * Фильтр создаётся в SecurityConfig вручную (не @Component), чтобы не регистрироваться дважды.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final UserAccountRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserAccountRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER.length()).trim();
            jwtService.parse(token).ifPresent(claims -> userRepository.findById(claims.userId())
                    .filter(UserAccount::isActive)
                    // токены, выданные до смены/сброса пароля, больше не принимаются
                    .filter(user -> !claims.issuedAt().isBefore(
                            user.getPasswordChangedAt().truncatedTo(ChronoUnit.SECONDS)))
                    .ifPresent(user -> authenticate(request, user)));
        }
        chain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, UserAccount user) {
        AuthenticatedUser principal = AuthenticatedUser.from(user);
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
