package com.chainlance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitWorkRequest(
        @NotBlank @Size(max = 10000) String description,
        @Size(max = 2000) String links
) {}
