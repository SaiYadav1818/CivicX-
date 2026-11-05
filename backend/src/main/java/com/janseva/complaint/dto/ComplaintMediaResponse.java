package com.janseva.complaint.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Complaint media metadata (no filesystem paths exposed)")
public record ComplaintMediaResponse(
        Long id,
        String originalFilename,
        String contentType,
        long fileSizeBytes,
        Integer width,
        Integer height,
        String processingStatus,
        Instant uploadedAt
) {
}
