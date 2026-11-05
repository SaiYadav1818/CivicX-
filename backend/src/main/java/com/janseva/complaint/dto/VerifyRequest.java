package com.janseva.complaint.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Request to verify a complaint (note optional)")
public record VerifyRequest(
        @Schema(description = "Optional internal review note", example = "Location confirmed by field officer")
        @Size(max = 1000) String note
) {
}
