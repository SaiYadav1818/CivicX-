package com.janseva.complaint.dto;

import com.janseva.complaint.ComplaintStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Request to change a complaint status (admin only, workflow validated)")
public record StatusUpdateRequest(
        @Schema(description = "Target status", example = "UNDER_REVIEW")
        @NotNull ComplaintStatus status,

        @Schema(description = "Reason or review note (required for REJECTED)", example = "Issue confirmed at location")
        @Size(max = 1000) String reason
) {
}
