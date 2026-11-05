package com.janseva.complaint.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Full complaint details")
public record ComplaintResponse(
        Long id,
        String complaintNumber,
        String status,
        String title,
        String description,
        CategoryResponse category,
        LocationResponse location,
        String ward,
        String zone,
        List<ComplaintMediaResponse> media,
        Instant createdAt,
        Instant updatedAt,
        Instant verifiedAt,
        Instant resolvedAt
) {
}
