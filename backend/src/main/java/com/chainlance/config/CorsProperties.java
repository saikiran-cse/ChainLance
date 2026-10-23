package com.chainlance.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bound from chainlance.cors.* (environment: CORS_ALLOWED_ORIGINS, comma separated). */
@ConfigurationProperties(prefix = "chainlance.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
