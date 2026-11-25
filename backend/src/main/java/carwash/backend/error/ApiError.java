package carwash.backend.error;

import java.time.Instant;
import java.util.List;

/**
 * Единый JSON-формат ошибки для всех ответов API.
 * Stack trace и внутренние сообщения клиенту не передаются.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId,
        List<FieldViolation> details) {

    public record FieldViolation(String field, String message) {
    }
}
