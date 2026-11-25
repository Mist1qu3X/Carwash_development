package carwash.backend.dto;

import carwash.backend.domain.BookingStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Массовая операция: набор UUID + действие.
 * status нужен для CHANGE_STATUS, tag — для ADD_TAG и REMOVE_TAG.
 */
public record BulkRequest(
        @NotEmpty(message = "Выберите хотя бы одну запись")
        @Size(max = 200, message = "За один раз можно обработать не больше 200 записей")
        List<@NotNull UUID> ids,
        @NotNull(message = "Укажите действие")
        BulkAction action,
        BookingStatus status,
        @Pattern(regexp = ValidationPatterns.TAG, message = "Тег: 1–30 символов, буквы, цифры, '-' и '_'")
        String tag) {
}
