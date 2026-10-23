package com.chainlance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Email, role and password are deliberately not editable here. */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 100) String displayName,
        @Size(max = 2000) String bio) {
}
