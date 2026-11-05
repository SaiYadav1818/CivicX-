package com.janseva.complaint.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "One entry of the complaint status history")
public record ComplaintHistoryResponse(
        Long id,
        String previousStatus,
        String newStatus,
        Long changedBy,
        String reason,
        Instant createdAt
) {
}
