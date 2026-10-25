package com.chainlance.dto;

public record ChainConfigResponse(
        Long chainId,
        String factoryAddress,
        String adminAddress,
        int requiredConfirmations,
        String inrPerEth
) {
}
