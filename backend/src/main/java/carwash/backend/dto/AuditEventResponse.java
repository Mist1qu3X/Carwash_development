package carwash.backend.dto;

import carwash.backend.domain.AuditEvent;
import carwash.backend.domain.AuditEventType;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(UUID id, Instant createdAt, UUID userId, String userEmail,
                                 AuditEventType eventType, String eventTitle, String entityType, UUID entityId,
                                 String description, String correlationId, String ipAddress) {

    public static AuditEventResponse from(AuditEvent e) {
        return new AuditEventResponse(e.getId(), e.getCreatedAt(), e.getUserId(), e.getUserEmail(),
                e.getEventType(), e.getEventType().getTitle(), e.getEntityType(), e.getEntityId(),
                e.getDescription(), e.getCorrelationId(), e.getIpAddress());
    }
}
