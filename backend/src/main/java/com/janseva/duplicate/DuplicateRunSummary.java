package com.janseva.duplicate;

public record DuplicateRunSummary(
        DuplicateEngineState state,
        int candidateCount,
        boolean duplicateReviewRequired
) {
}
