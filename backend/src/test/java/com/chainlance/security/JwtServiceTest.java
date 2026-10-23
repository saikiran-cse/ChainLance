package com.chainlance.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.chainlance.config.JwtProperties;
import com.chainlance.entity.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Pure unit test: no Spring, no database. */
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-0123456789";
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private static JwtService service(String secret, Instant now) {
        return new JwtService(new JwtProperties(secret, 120), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void issuedTokenCanBeParsedBack() {
        JwtService jwt = service(SECRET, T0);
        String token = jwt.issue(42L, "a@b.test", Role.FREELANCER);

        Claims claims = jwt.parse(token);
        assertEquals("42", claims.getSubject());
        assertEquals("FREELANCER", claims.get("role", String.class));
        assertEquals(42L, jwt.parseUserId(token).longValue());
        assertEquals(7200L, jwt.expiresInSeconds());
    }

    @Test
    void tamperedSignatureIsRejected() {
        JwtService jwt = service(SECRET, T0);
        String[] parts = jwt.issue(1L, "a@b.test", Role.CLIENT).split("\\.");
        String flipped = (parts[2].charAt(0) == 'A' ? "B" : "A") + parts[2].substring(1);
        String tampered = parts[0] + "." + parts[1] + "." + flipped;

        assertThrows(JwtException.class, () -> jwt.parse(tampered));
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = service(SECRET, T0).issue(1L, "a@b.test", Role.CLIENT);
        JwtService other = service("another-secret-another-secret-9876543210", T0);

        assertThrows(JwtException.class, () -> other.parse(token));
    }

    @Test
    void expiredTokenIsRejected() {
        String token = service(SECRET, T0).issue(1L, "a@b.test", Role.CLIENT);
        JwtService threeHoursLater = service(SECRET, T0.plusSeconds(3 * 3600));

        assertThrows(JwtException.class, () -> threeHoursLater.parse(token));
    }

    @Test
    void tokenStillValidBeforeExpiry() {
        String token = service(SECRET, T0).issue(1L, "a@b.test", Role.CLIENT);
        JwtService oneHourLater = service(SECRET, T0.plusSeconds(3600));

        assertEquals(1L, oneHourLater.parseUserId(token).longValue());
    }

    @Test
    void garbageIsRejected() {
        JwtService jwt = service(SECRET, T0);
        assertThrows(JwtException.class, () -> jwt.parse("not-a-jwt"));
    }

    @Test
    void missingOrShortSecretPreventsStartup() {
        assertThrows(IllegalStateException.class, () -> service(null, T0));
        assertThrows(IllegalStateException.class, () -> service("", T0));
        assertThrows(IllegalStateException.class, () -> service("x".repeat(31), T0));
    }

    @Test
    void nonPositiveLifetimePreventsStartup() {
        assertThrows(IllegalStateException.class,
                () -> new JwtService(new JwtProperties(SECRET, 0), Clock.fixed(T0, ZoneOffset.UTC)));
    }
}
