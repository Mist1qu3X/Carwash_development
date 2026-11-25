package carwash.backend.dto;

import carwash.backend.domain.Booking;
import carwash.backend.domain.BookingStatus;
import carwash.backend.domain.PaymentRisk;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// запись на мойку в ответах API (JPA-сущность наружу не отдаётся)
public record BookingResponse(
        UUID id,
        String code,
        ClientRef client,
        String carNumber,
        String carModel,
        String customerComment,
        ServiceTypeRef serviceType,
        BookingStatus status,
        String statusTitle,
        LocalDateTime scheduledAt,
        BigDecimal price,
        boolean paid,
        PaymentRisk paymentRisk,
        String paymentRiskTitle,
        List<String> tags,
        boolean archived,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt,
        Long version) {

    public record ClientRef(UUID id, String fullName, String phone) {
    }

    public record ServiceTypeRef(UUID id, String code, String name, boolean active) {
    }

    public static BookingResponse from(Booking b) {
        return new BookingResponse(
                b.getId(),
                b.getCode(),
                new ClientRef(b.getClient().getId(), b.getClient().getFullName(), b.getClient().getPhone()),
                b.getCarNumber(),
                b.getCarModel(),
                b.getCustomerComment(),
                new ServiceTypeRef(b.getServiceType().getId(), b.getServiceType().getCode(),
                        b.getServiceType().getName(), b.getServiceType().isActive()),
                b.getStatus(),
                b.getStatus().getTitle(),
                b.getScheduledAt(),
                b.getPrice(),
                b.isPaid(),
                b.getPaymentRisk(),
                b.getPaymentRisk().getTitle(),
                b.getTags().stream().sorted().toList(),
                b.isArchived(),
                b.getArchivedAt(),
                b.getCreatedAt(),
                b.getUpdatedAt(),
                b.getVersion());
    }
}
