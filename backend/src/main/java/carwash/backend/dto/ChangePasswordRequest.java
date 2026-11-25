package carwash.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Укажите текущий пароль") @Size(max = 128) String currentPassword,
        @NotBlank(message = "Укажите новый пароль") @Size(min = 12, max = 128, message = "Новый пароль: от 12 до 128 символов")
        String newPassword) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[***]";
    }
}
