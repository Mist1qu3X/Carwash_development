package carwash.backend.config;

import carwash.backend.domain.AuditEventType;
import carwash.backend.domain.Role;
import carwash.backend.domain.UserAccount;
import carwash.backend.repository.UserAccountRepository;
import carwash.backend.service.AuditService;
import carwash.backend.service.EntityTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;
import java.util.Set;

/**
 * При первом запуске (таблица users пуста) создаёт администратора из BOOTSTRAP_ADMIN_* в .env.
 * Пароль из .env считается временным: после первого входа его нужно сменить.
 */
@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final AppProperties properties;
    private final Clock clock;

    public BootstrapAdminInitializer(UserAccountRepository users, PasswordEncoder passwordEncoder,
                                     AuditService audit, AppProperties properties, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        AppProperties.Bootstrap bootstrap = properties.bootstrap();
        if (bootstrap.email() == null || bootstrap.email().isBlank()
                || bootstrap.password() == null || bootstrap.password().isBlank()) {
            log.warn("Пользователей нет, а BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD не заданы — войти будет некому");
            return;
        }
        UserAccount admin = new UserAccount();
        admin.setEmail(bootstrap.email().trim().toLowerCase(Locale.ROOT));
        admin.setFullName(bootstrap.name() == null || bootstrap.name().isBlank() ? "Администратор" : bootstrap.name().trim());
        admin.setPasswordHash(passwordEncoder.encode(bootstrap.password()));
        admin.setActive(true);
        admin.setMustChangePassword(true);
        admin.setPasswordChangedAt(clock.instant());
        admin.replaceRoles(Set.of(Role.ADMIN));
        users.save(admin);
        audit.logAs(AuditEventType.USER_CREATED, null, "system", EntityTypes.USER, admin.getId(),
                "Создан администратор по умолчанию " + admin.getEmail());
        log.info("Создан администратор по умолчанию: {} (требуется смена временного пароля)", admin.getEmail());
    }
}
