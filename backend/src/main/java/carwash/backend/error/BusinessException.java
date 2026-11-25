package carwash.backend.error;

import org.springframework.http.HttpStatus;

// 400: нарушение бизнес-правила (переход статуса, дата в прошлом и т.п.)
public class BusinessException extends ApiException {
    public BusinessException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }
}
