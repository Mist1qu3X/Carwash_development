package carwash.backend.security;

import carwash.backend.error.ApiErrorWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

// 403 в едином JSON-формате, если у пользователя недостаточно прав
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    public static final String PASSWORD_CHANGE_CODE = "PASSWORD_CHANGE_REQUIRED";
    public static final String PASSWORD_CHANGE_MESSAGE =
            "Необходимо сменить временный пароль (POST /api/auth/change-password)";
    public static final String FORBIDDEN_MESSAGE = "Недостаточно прав для выполнения операции";

    private final ApiErrorWriter errorWriter;

    public RestAccessDeniedHandler(ApiErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        if (CurrentUser.mustChangePassword()) {
            errorWriter.write(response, request, HttpStatus.FORBIDDEN, PASSWORD_CHANGE_CODE, PASSWORD_CHANGE_MESSAGE);
        } else {
            errorWriter.write(response, request, HttpStatus.FORBIDDEN, "FORBIDDEN", FORBIDDEN_MESSAGE);
        }
    }
}
