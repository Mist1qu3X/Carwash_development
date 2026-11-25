package carwash.backend.filter;

import java.util.Locale;

/**
 * Безопасный шаблон для LIKE: пользовательская строка передаётся как параметр запроса,
 * а символы % и _ экранируются, чтобы "%" не превращался в "найти всё".
 */
public final class LikePatterns {

    public static final char ESCAPE = '\\';
    private static final int MAX_LENGTH = 100;

    private LikePatterns() {
    }

    public static String contains(String text) {
        String value = text.trim().toLowerCase(Locale.ROOT);
        if (value.length() > MAX_LENGTH) {
            value = value.substring(0, MAX_LENGTH);
        }
        String escaped = value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    public static boolean hasText(String text) {
        return text != null && !text.isBlank();
    }
}
