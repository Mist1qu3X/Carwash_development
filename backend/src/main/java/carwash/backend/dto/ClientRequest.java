package carwash.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ClientRequest(
        @NotBlank(message = "Имя клиента обязательно")
        @Size(min = 2, max = 100, message = "Имя: от 2 до 100 символов")
        @Pattern(regexp = ValidationPatterns.PERSON_NAME, message = "Имя может содержать только буквы, пробелы, точку и дефис")
        String fullName,
        @NotBlank(message = "Телефон обязателен")
        @Pattern(regexp = ValidationPatterns.PHONE, message = "Телефон в формате +7-900-123-45-67")
        String phone,
        @Email(message = "Некорректный email") @Size(max = 100)
        String email,
        @PositiveOrZero Long version) {
}
