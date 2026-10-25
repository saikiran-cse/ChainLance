package com.chainlance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateExtensionRequest(
        @NotNull Instant requestedDeadline,
        @NotBlank @Size(max = 2000) String reason
) {}
