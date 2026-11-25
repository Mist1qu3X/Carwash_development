package carwash.backend.error;

import org.springframework.http.HttpStatus;

// 409: конфликт версий, дубликат, недопустимое состояние записи
public class ConflictException extends ApiException {
    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }

    public static ConflictException version(long currentVersion) {
        return new ConflictException("VERSION_CONFLICT",
                "Данные уже изменил другой пользователь (текущая версия " + currentVersion
                        + "). Обновите карточку и повторите изменение.");
    }
}
