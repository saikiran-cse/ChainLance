package com.chainlance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** chainlance.wallet.challenge-ttl-minutes; falls back to 5 when unset. */
@ConfigurationProperties(prefix = "chainlance.wallet")
public record WalletProperties(long challengeTtlMinutes) {

    public WalletProperties {
        if (challengeTtlMinutes <= 0) {
            challengeTtlMinutes = 5;
        }
    }
}
