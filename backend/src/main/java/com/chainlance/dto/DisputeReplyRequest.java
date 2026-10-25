package com.chainlance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DisputeReplyRequest(
        @NotBlank @Size(max = 5000) String response,
        @Size(max = 2000) String evidenceLinks
) {}
