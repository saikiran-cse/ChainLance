package com.chainlance.dto;

import jakarta.validation.constraints.NotBlank;

public record LinkWalletRequest(
        @NotBlank(message = "Wallet address is required")
        String address
) {
}
