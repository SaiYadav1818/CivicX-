package com.janseva.duplicate.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "One possible duplicate candidate for admin review")
public record DuplicateCandidateResponse(
        Long id,
        Long matchedComplaintId,
        String matchedComplaintNumber,
        String matchedTitle,
        String matchedCategory,
        String matchedStatus,
        Double distanceMeters,
        Double textSimilarity,
        Double imageSimilarity,
        Boolean categoryMatch,
        Double duplicateScore,
        boolean possibleDuplicate,
        String recommendedAction,
        String reviewStatus,
        Instant reviewedAt,
        String reviewNote
) {
}
