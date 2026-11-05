package com.janseva.complaint.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "One automated analysis result for a complaint")
public record AiResultResponse(
        Long id,
        String modelName,
        String modelVersion,
        String categoryCode,
        double confidence,
        String description,
        boolean requiresHumanReview,
        Instant createdAt
) {
}
