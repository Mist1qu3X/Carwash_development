package carwash.backend.error;

import org.springframework.http.HttpStatus;

// 401: не удалось подтвердить личность (логин, refresh-токен)
public class UnauthorizedException extends ApiException {
    public UnauthorizedException(String code, String message) {
        super(HttpStatus.UNAUTHORIZED, code, message);
    }
}
