package carwash.backend.error;

import org.springframework.http.HttpStatus;

// 403: действие запрещено для текущего пользователя
public class ForbiddenException extends ApiException {
    public ForbiddenException(String code, String message) {
        super(HttpStatus.FORBIDDEN, code, message);
    }
}
