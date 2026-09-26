package carwash.exception;

// ошибка нарушения бизнес-правил
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}