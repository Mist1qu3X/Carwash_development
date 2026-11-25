package carwash.backend.dto;

import carwash.backend.domain.ServiceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ServiceTypeResponse(UUID id, String code, String name, String description, BigDecimal basePrice,
                                  int durationMinutes, boolean active, Instant createdAt, Instant updatedAt, Long version) {

    public static ServiceTypeResponse from(ServiceType s) {
        return new ServiceTypeResponse(s.getId(), s.getCode(), s.getName(), s.getDescription(), s.getBasePrice(),
                s.getDurationMinutes(), s.isActive(), s.getCreatedAt(), s.getUpdatedAt(), s.getVersion());
    }
}
