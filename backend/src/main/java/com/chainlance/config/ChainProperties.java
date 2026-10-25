package com.chainlance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainlance.chain")
public record ChainProperties(
        Long chainId,
        String rpcUrl,
        String factoryAddress,
        String adminAddress,
        Integer requiredConfirmations
) {
    public ChainProperties {
        if (chainId == null) {
            chainId = 11155111L;
        }
        if (requiredConfirmations == null || requiredConfirmations < 1) {
            requiredConfirmations = 1;
        }
    }
}
