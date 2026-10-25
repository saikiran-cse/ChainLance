package com.chainlance.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * chainlance.pricing.inr-per-eth: how many INR one ETH is worth. A fixed DEMO rate (default 250000).
 * Prices are negotiated in INR; the rate is applied once when an agreement is created and then frozen into the snapshot.
 */
@ConfigurationProperties(prefix = "chainlance.pricing")
public record PricingProperties(BigDecimal inrPerEth) {

    public PricingProperties {
        if (inrPerEth == null || inrPerEth.signum() <= 0) {
            inrPerEth = new BigDecimal("250000");
        }
    }
}
