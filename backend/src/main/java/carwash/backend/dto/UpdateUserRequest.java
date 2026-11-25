package carwash.backend.dto;

import carwash.backend.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateUserRequest(
        @NotBlank(message = "Укажите email") @Email(message = "Некорректный email") @Size(max = 254) String email,
        @NotBlank(message = "Укажите имя") @Size(min = 2, max = 100, message = "Имя: от 2 до 100 символов") String fullName,
        @NotEmpty(message = "Выберите хотя бы одну роль") Set<Role> roles,
        @NotNull(message = "Укажите признак активности") Boolean active,
        @NotNull(message = "Не передан version") @PositiveOrZero Long version) {
}
