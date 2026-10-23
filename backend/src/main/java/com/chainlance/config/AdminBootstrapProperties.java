package com.chainlance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bound from chainlance.admin-bootstrap.* (environment: ADMIN_EMAIL, ADMIN_PASSWORD). */
@ConfigurationProperties(prefix = "chainlance.admin-bootstrap")
public record AdminBootstrapProperties(String email, String password) {
}
