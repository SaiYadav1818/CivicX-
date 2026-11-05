package com.janseva.processing;

public record ComplaintProcessingMessage(
        Long complaintId,
        Long eventId
) {
}
