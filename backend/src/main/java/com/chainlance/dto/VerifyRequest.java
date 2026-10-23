package com.chainlance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyRequest(
        @NotBlank @Size(max = 64) String nonce,
        @NotBlank @Size(max = 200) String signature) {
}
