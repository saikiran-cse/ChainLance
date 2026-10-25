package com.chainlance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** The freelancer, the project and the status come from the login and the URL, never from this body. */
public record SubmitProposalRequest(
        @NotBlank @Size(max = 5000) String message,
        @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal priceInr,
        @NotNull @Min(1) @Max(3650) Integer durationDays,
        @Size(max = 1000) String portfolioLinks) {
}
