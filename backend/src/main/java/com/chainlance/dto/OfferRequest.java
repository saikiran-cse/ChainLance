package com.chainlance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** A new price offer or a counteroffer. Who is making it is taken from the login. */
public record OfferRequest(
        @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal priceInr,
        @NotNull @Min(1) @Max(3650) Integer durationDays,
        @Size(max = 1000) String note) {
}
