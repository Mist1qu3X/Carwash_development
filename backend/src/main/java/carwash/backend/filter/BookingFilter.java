package carwash.backend.filter;

import carwash.backend.domain.BookingStatus;
import carwash.backend.domain.PaymentRisk;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Параметры поиска записей (query-параметры GET /api/bookings и GET /api/bookings/export).
 * Все поля необязательные; незаданные условия не участвуют в запросе.
 */
public record BookingFilter(
        @Schema(description = "Свободный поиск: код, гос. номер, модель, имя или телефон клиента")
        String q,
        @Schema(description = "Вид услуги (UUID из справочника)")
        UUID serviceTypeId,
        @Schema(description = "Клиент (UUID)")
        UUID clientId,
        @Schema(description = "Статус записи")
        BookingStatus status,
        @Schema(description = "Оплачена ли запись")
        Boolean paid,
        @Schema(description = "Риск неоплаты (вычисляемый показатель)")
        PaymentRisk risk,
        @Schema(description = "Тег")
        String tag,
        @Schema(description = "ACTIVE (по умолчанию), ARCHIVED или ALL")
        ArchiveScope archive,
        @Schema(description = "Цена для сравнения")
        BigDecimal price,
        @Schema(description = "Операция сравнения цены: EQ, GT, GTE, LT, LTE (по умолчанию GTE)")
        NumberOperation priceOp,
        @Schema(description = "Дата записи с (ISO, например 2026-12-01T00:00)")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime scheduledFrom,
        @Schema(description = "Дата записи по")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime scheduledTo) {

    public static BookingFilter empty() {
        return new BookingFilter(null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public ArchiveScope archiveOrDefault() {
        return archive == null ? ArchiveScope.ACTIVE : archive;
    }
}
