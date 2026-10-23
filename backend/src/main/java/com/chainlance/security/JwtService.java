package com.chainlance.security;

import com.chainlance.config.JwtProperties;
import com.chainlance.entity.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Creates and verifies signed JWTs (HMAC). The application refuses to start if JWT_SECRET is missing or short.
 * Claims such as role are informational only: authorization always uses the role loaded from the database.
 */
@Service
public class JwtService {

    public static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public JwtService(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    JwtService(JwtProperties properties, Clock clock) {
        String secret = properties.secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET is missing or shorter than " + MIN_SECRET_BYTES
                    + " characters. Set JWT_SECRET to a long random value (see backend/.env.example).");
        }
        if (properties.expirationMinutes() <= 0) {
            throw new IllegalStateException("JWT_EXPIRATION_MINUTES must be greater than zero.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = Duration.ofMinutes(properties.expirationMinutes());
        this.clock = clock;
    }

    public String issue(Long userId, String email, Role role) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /** Verifies signature and expiry. Throws a JwtException (or IllegalArgumentException) if the token is not valid. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long parseUserId(String token) {
        return Long.valueOf(parse(token).getSubject());
    }

    public long expiresInSeconds() {
        return ttl.toSeconds();
    }
}
