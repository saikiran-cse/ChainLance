package com.chainlance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/** Used to create and to update a project. The client, status and timestamps are never taken from the request. */
public record ProjectRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 10000) String description,
        @NotBlank @Size(max = 80) String category,
        @Size(max = 500) String skills,
        @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal budgetInr,
        Instant deadline) {
}
