package carwash.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

// описание API для Swagger UI; кнопка Authorize принимает access JWT
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Carwash API",
                version = "1.0.0",
                description = "REST API системы записи на автомойку. Получите токен через POST /api/auth/login "
                        + "и вставьте accessToken в Authorize."),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
