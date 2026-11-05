package com.janseva.complaint.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Citizen-facing summary of the automated analysis for their complaint")
public record AiSummaryResponse(
        String processingStatus,
        CategorySuggestion categorySuggestion,
        boolean possibleDuplicate,
        int duplicateCount,
        List<TopMatch> topMatches
) {
    @Schema(description = "Suggested category from the automated classifier")
    public record CategorySuggestion(
            String code,
            String name,
            double confidence,
            String method
    ) {
    }

    @Schema(description = "Redacted view of an existing complaint that may match this one. "
            + "No citizen names, emails or exact coordinates are exposed.")
    public record TopMatch(
            String complaintNumber,
            String title,
            String status,
            Double distanceMeters
    ) {
    }
}
