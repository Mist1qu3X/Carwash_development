package carwash.backend.filter;

import carwash.backend.domain.AuditEventType;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;
import java.util.UUID;

// параметры поиска по журналу аудита
public record AuditFilter(
        String q,
        AuditEventType eventType,
        String entityType,
        UUID entityId,
        String userEmail,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
}
