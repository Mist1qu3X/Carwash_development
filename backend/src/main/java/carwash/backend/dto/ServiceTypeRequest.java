package carwash.backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ServiceTypeRequest(
        @NotBlank(message = "Укажите код услуги")
        @Pattern(regexp = ValidationPatterns.SERVICE_CODE, message = "Код: латиница в верхнем регистре, цифры и '_' (например, BODY_WASH)")
        String code,
        @NotBlank(message = "Укажите название") @Size(min = 2, max = 100, message = "Название: от 2 до 100 символов")
        String name,
        @Size(max = 500, message = "Описание — не длиннее 500 символов")
        String description,
        @NotNull(message = "Укажите базовую цену")
        @DecimalMin(value = "0.00", message = "Цена не может быть отрицательной")
        @DecimalMax(value = "1000000.00", message = "Цена слишком большая")
        @Digits(integer = 7, fraction = 2)
        BigDecimal basePrice,
        @NotNull(message = "Укажите длительность")
        @Min(value = 5, message = "Длительность — от 5 минут") @Max(value = 1440, message = "Длительность — не больше суток")
        Integer durationMinutes,
        @PositiveOrZero Long version) {
}
