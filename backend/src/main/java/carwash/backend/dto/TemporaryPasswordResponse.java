package carwash.backend.dto;

// временный пароль показывается один раз — при создании пользователя или сбросе пароля
public record TemporaryPasswordResponse(AdminUserResponse user, String temporaryPassword) {
}
