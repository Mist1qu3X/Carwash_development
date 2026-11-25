package carwash.backend.error;

import carwash.backend.config.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

// собирает ApiError; используется и в @RestControllerAdvice, и в фильтрах безопасности
@Component
public class ApiErrorWriter {

    private final ObjectMapper objectMapper;

    public ApiErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ApiError build(HttpStatus status, String code, String message, HttpServletRequest request,
                          List<ApiError.FieldViolation> details) {
        return new ApiError(Instant.now(), status.value(), code, message,
                request.getRequestURI(), MDC.get(CorrelationIdFilter.MDC_KEY), details == null ? List.of() : details);
    }

    public void write(HttpServletResponse response, HttpServletRequest request,
                      HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), build(status, code, message, request, List.of()));
    }
}
