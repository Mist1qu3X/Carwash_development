package carwash.backend.security;

import carwash.backend.config.AppProperties;
import carwash.backend.domain.Role;
import carwash.backend.domain.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

// выпуск и проверка короткоживущих access JWT (HS256)
@Service
public class JwtService {

    private final SecretKey key;
    private final String issuer;
    private final Duration accessTtl;
    private final Clock clock;

    public JwtService(AppProperties properties, Clock clock) {
        byte[] secret = Decoders.BASE64.decode(properties.jwt().secretBase64());
        if (secret.length < 32) {
            throw new IllegalStateException("JWT_SECRET_BASE64 должен содержать минимум 256 бит (32 байта)");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.issuer = properties.jwt().issuer();
        this.accessTtl = Duration.ofMinutes(properties.jwt().accessMinutes());
        this.clock = clock;
    }

    public String createAccessToken(UserAccount user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .claim("email", user.getEmail())
                .claim("roles", user.getRoles().stream().map(Role::name).sorted().toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    // пустой результат, если подпись неверна, токен просрочен или выдан другим issuer
    public Optional<AccessTokenClaims> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new AccessTokenClaims(UUID.fromString(claims.getSubject()),
                    claims.getIssuedAt().toInstant()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }

    public record AccessTokenClaims(UUID userId, Instant issuedAt) {
    }
}
