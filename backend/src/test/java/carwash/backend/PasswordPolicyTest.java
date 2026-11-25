package carwash.backend;

import carwash.backend.error.BusinessException;
import carwash.backend.security.PasswordPolicy;
import carwash.backend.security.TemporaryPasswordGenerator;
import carwash.backend.security.TokenHasher;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// тест элементов безопасности: политика паролей, временные пароли, хеши
class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsStrongPassword() {
        assertThat(policy.validate("Moika!Seasons2026", "ivan@mail.ru")).isEmpty();
    }

    @Test
    void rejectsWeakPasswords() {
        assertThat(policy.validate("short1!A", null)).anyMatch(m -> m.contains("12"));
        assertThat(policy.validate("alllowercase123!", null)).anyMatch(m -> m.contains("заглавная"));
        assertThat(policy.validate("NoDigitsHere!!!x", null)).anyMatch(m -> m.contains("цифра"));
        assertThat(policy.validate("NoSpecial12345x", null)).anyMatch(m -> m.contains("спецсимвол"));
        assertThat(policy.validate("With Space 123!A", null)).anyMatch(m -> m.contains("пробел"));
        assertThat(policy.validate("Manager!2026xyz", "manager@carwash.ru")).anyMatch(m -> m.contains("логин"));
    }

    @Test
    void checkThrowsBusinessException() {
        assertThatThrownBy(() -> policy.check("123", null)).isInstanceOf(BusinessException.class);
    }

    @RepeatedTest(20)
    void temporaryPasswordAlwaysSatisfiesPolicy() {
        String password = new TemporaryPasswordGenerator().generate();
        assertThat(policy.validate(password, "user@example.org")).isEmpty();
    }

    @Test
    void passwordIsStoredAsBcryptHash() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
        String hash = encoder.encode("Moika!Seasons2026");
        assertThat(hash).startsWith("$2a$").doesNotContain("Moika");
        assertThat(encoder.matches("Moika!Seasons2026", hash)).isTrue();
    }

    @Test
    void refreshTokenHashIsNotTheToken() {
        String token = TokenHasher.newToken();
        assertThat(TokenHasher.sha256(token)).hasSize(64).isNotEqualTo(token);
        assertThat(TokenHasher.newToken()).isNotEqualTo(token);
    }
}
