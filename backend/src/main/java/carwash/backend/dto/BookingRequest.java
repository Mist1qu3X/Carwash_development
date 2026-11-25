package carwash.backend.dto;

import carwash.backend.domain.BookingStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Создание (POST) и изменение (PUT) записи.
 * code — необязателен при создании (сгенерируется CW-000013);
 * status — при создании по умолчанию CREATED;
 * version — обязателен при изменении (контроль конкурентного изменения).
 */
public record BookingRequest(
        @Pattern(regexp = ValidationPatterns.BOOKING_CODE, message = "Код: 3–20 символов (латиница, цифры, дефис)")
        String code,

        @NotNull(message = "Укажите клиента")
        UUID clientId,

        @NotBlank(message = "Гос. номер обязателен")
        @Pattern(regexp = ValidationPatterns.CAR_NUMBER,
                message = "Гос. номер: 4–15 символов, буквы и цифры (например, А123ВС77)")
        String carNumber,

        @Size(max = 50, message = "Модель — не длиннее 50 символов")
        String carModel,

        @Size(max = 500, message = "Комментарий — не длиннее 500 символов")
        String customerComment,

        @NotNull(message = "Укажите вид услуги")
        UUID serviceTypeId,

        BookingStatus status,

        @NotNull(message = "Укажите дату и время записи")
        LocalDateTime scheduledAt,

        @NotNull(message = "Укажите цену")
        @DecimalMin(value = "0.00", message = "Цена не может быть отрицательной")
        @DecimalMax(value = "1000000.00", message = "Цена слишком большая (максимум 1 000 000 руб.)")
        @Digits(integer = 7, fraction = 2, message = "Цена: не больше двух знаков после запятой")
        BigDecimal price,

        boolean paid,

        @Size(max = 10, message = "Не больше 10 тегов")
        Set<@NotBlank @Pattern(regexp = ValidationPatterns.TAG,
                message = "Тег: 1–30 символов, буквы, цифры, '-' и '_'") String> tags,

        @PositiveOrZero
        Long version) {
}
