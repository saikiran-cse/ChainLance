package com.chainlance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bound from chainlance.jwt.* in application.yml (environment: JWT_SECRET, JWT_EXPIRATION_MINUTES). */
@ConfigurationProperties(prefix = "chainlance.jwt")
public record JwtProperties(String secret, long expirationMinutes) {
}
