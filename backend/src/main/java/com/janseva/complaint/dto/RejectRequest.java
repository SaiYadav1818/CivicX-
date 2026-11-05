package com.janseva.complaint.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request to reject a complaint")
public record RejectRequest(
        @Schema(description = "Mandatory reason for rejection", example = "Photo does not show any civic issue")
        @NotBlank @Size(max = 1000) String reason
) {
}
