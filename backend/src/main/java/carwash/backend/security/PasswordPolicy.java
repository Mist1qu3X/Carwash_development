package carwash.backend.security;

import carwash.backend.error.BusinessException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Политика паролей: от 12 до 128 символов, заглавная и строчная буква,
 * цифра, спецсимвол, без пробелов и без части email.
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    public List<String> validate(String password, String email) {
        List<String> problems = new ArrayList<>();
        if (password == null || password.isEmpty()) {
            problems.add("Пароль не может быть пустым");
            return problems;
        }
        if (password.length() < MIN_LENGTH) {
            problems.add("Пароль должен содержать не меньше " + MIN_LENGTH + " символов");
        }
        if (password.length() > MAX_LENGTH) {
            problems.add("Пароль должен содержать не больше " + MAX_LENGTH + " символов");
        }
        if (password.chars().noneMatch(Character::isUpperCase)) {
            problems.add("Нужна хотя бы одна заглавная буква");
        }
        if (password.chars().noneMatch(Character::isLowerCase)) {
            problems.add("Нужна хотя бы одна строчная буква");
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            problems.add("Нужна хотя бы одна цифра");
        }
        if (password.chars().allMatch(Character::isLetterOrDigit)) {
            problems.add("Нужен хотя бы один спецсимвол (например, ! ? # % _ -)");
        }
        if (password.chars().anyMatch(Character::isWhitespace)) {
            problems.add("Пароль не должен содержать пробелы");
        }
        if (email != null && email.contains("@")) {
            String login = email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT);
            if (login.length() >= 3 && password.toLowerCase(Locale.ROOT).contains(login)) {
                problems.add("Пароль не должен содержать логин из email");
            }
        }
        return problems;
    }

    public void check(String password, String email) {
        List<String> problems = validate(password, email);
        if (!problems.isEmpty()) {
            throw new BusinessException("WEAK_PASSWORD", "Пароль не соответствует политике: " + String.join("; ", problems));
        }
    }
}
