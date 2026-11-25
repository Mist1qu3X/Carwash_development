package carwash.backend.error;

import carwash.backend.security.CurrentUser;
import carwash.backend.security.RestAccessDeniedHandler;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Единая обработка ошибок: всё превращается в ApiError.
 * 400 — неверные данные, 401 — нет авторизации, 403 — нет прав,
 * 404 — нет записи, 409 — конфликт версий/дубликат, 500 — без подробностей.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ApiErrorWriter errors;

    public GlobalExceptionHandler(ApiErrorWriter errors) {
        this.errors = errors;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException e, HttpServletRequest request) {
        return respond(e.getStatus(), e.getCode(), e.getMessage(), request, List.of());
    }

    // Bean Validation для @RequestBody и объектов-фильтров (@ParameterObject)
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiError> handleBind(BindException e, HttpServletRequest request) {
        List<ApiError.FieldViolation> details = new ArrayList<>();
        for (FieldError fieldError : e.getFieldErrors()) {
            String message = fieldError.isBindingFailure()
                    ? "Неверный формат значения"
                    : fieldError.getDefaultMessage();
            details.add(new ApiError.FieldViolation(fieldError.getField(), message));
        }
        e.getGlobalErrors().forEach(error ->
                details.add(new ApiError.FieldViolation(error.getObjectName(), error.getDefaultMessage())));
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Проверьте правильность заполнения полей", request, details);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException e, HttpServletRequest request) {
        List<ApiError.FieldViolation> details = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ApiError.FieldViolation(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Некорректные параметры запроса", request, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException e, HttpServletRequest request) {
        List<ApiError.FieldViolation> details = e.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Некорректные данные", request, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleNotReadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        String message = "Тело запроса не является корректным JSON";
        if (e.getCause() instanceof InvalidFormatException invalid) {
            message = "Поле '" + fieldPath(invalid) + "': недопустимое значение '" + invalid.getValue() + "'";
        } else if (e.getCause() instanceof MismatchedInputException mismatched && !mismatched.getPath().isEmpty()) {
            message = "Поле '" + fieldPath(mismatched) + "' имеет неверный тип";
        }
        return respond(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", message, request, List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "BAD_PARAMETER",
                "Параметр '" + e.getName() + "' имеет неверный формат", request, List.of());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ApiError> handleMissing(Exception e, HttpServletRequest request) {
        String name = e instanceof MissingServletRequestParameterException p ? p.getParameterName()
                : ((MissingServletRequestPartException) e).getRequestPartName();
        return respond(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", "Не передан параметр '" + name + "'", request, List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadSize(MaxUploadSizeExceededException e, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", "Файл слишком большой (максимум 2 МБ)", request, List.of());
    }

    // @Version: запись успели изменить в параллельной транзакции
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException e, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                "Данные уже изменил другой пользователь. Обновите их и повторите", request, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException e, HttpServletRequest request) {
        log.warn("Нарушение ограничения целостности: {}", e.getMostSpecificCause().getMessage());
        return respond(HttpStatus.CONFLICT, "DATA_CONFLICT",
                "Операция нарушает ограничения базы данных (дубликат или связанная запись)", request, List.of());
    }

    // @PreAuthorize бросает исключение внутри контроллера — отвечаем так же, как фильтр безопасности
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        if (CurrentUser.mustChangePassword()) {
            return respond(HttpStatus.FORBIDDEN, RestAccessDeniedHandler.PASSWORD_CHANGE_CODE,
                    RestAccessDeniedHandler.PASSWORD_CHANGE_MESSAGE, request, List.of());
        }
        return respond(HttpStatus.FORBIDDEN, "FORBIDDEN", RestAccessDeniedHandler.FORBIDDEN_MESSAGE, request, List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException e, HttpServletRequest request) {
        return respond(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Требуется авторизация", request, List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException e, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", "Адрес не найден", request, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "Метод " + e.getMethod() + " не поддерживается для этого адреса", request, List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleMediaType(HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Неподдерживаемый Content-Type", request, List.of());
    }

    // всё непредвиденное: подробности только в лог сервера, клиенту — общее сообщение
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception e, HttpServletRequest request) {
        log.error("Необработанная ошибка при запросе {} {}", request.getMethod(), request.getRequestURI(), e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Внутренняя ошибка сервера. Сообщите администратору correlationId", request, List.of());
    }

    private ResponseEntity<ApiError> respond(HttpStatus status, String code, String message,
                                             HttpServletRequest request, List<ApiError.FieldViolation> details) {
        return ResponseEntity.status(status).body(errors.build(status, code, message, request, details));
    }

    private static String fieldPath(MismatchedInputException e) {
        return e.getPath().stream()
                .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                .collect(Collectors.joining("."));
    }
}
