package carwash.backend.dto;

import java.time.Instant;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        Instant refreshExpiresAt,
        UserResponse user) {
}
