package com.janseva.complaint.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Compact complaint summary for lists")
public record ComplaintSummaryResponse(
        Long id,
        String complaintNumber,
        String status,
        String title,
        String categoryName,
        Instant createdAt,
        Instant updatedAt
) {
}
