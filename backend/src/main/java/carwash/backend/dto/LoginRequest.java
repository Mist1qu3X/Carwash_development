package carwash.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Укажите email") @Email(message = "Некорректный email") @Size(max = 254) String email,
        @NotBlank(message = "Укажите пароль") @Size(max = 128) String password) {

    // пароль не попадает в логи даже при случайном выводе объекта
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + "]";
    }
}
