package carwash.backend.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

// параметры приложения из блока app.* в application.yml (значения приходят из .env)
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank String timeZone,
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Bootstrap bootstrap,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Security security,
        @Valid @NotNull Bookings bookings) {

    public record Jwt(@NotBlank String secretBase64, @NotBlank String issuer,
                      @Min(1) @Max(120) int accessMinutes, @Min(1) @Max(90) int refreshDays) {
    }

    public record Bootstrap(String email, String name, String password) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Security(@Min(1) int maxFailedAttempts, @Min(1) int lockMinutes, boolean cookieSecure) {
    }

    public record Bookings(boolean permanentDeleteEnabled, @Min(1) @Max(500) int maxPageSize,
                           @Min(1) int importMaxRows, @Min(1) int exportMaxRows) {
    }
}
