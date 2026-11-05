package com.janseva.duplicate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Request to confirm or dismiss a duplicate candidate (note optional)")
public record DuplicateReviewRequest(
        @Schema(description = "Optional reviewer note", example = "Same location and photo as the earlier report")
        @Size(max = 1000) String note
) {
}
