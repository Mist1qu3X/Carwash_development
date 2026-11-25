package carwash.backend.error;

import org.springframework.http.HttpStatus;

// 429: учётная запись временно заблокирована после неудачных попыток входа
public class TooManyAttemptsException extends ApiException {
    public TooManyAttemptsException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS", message);
    }
}
