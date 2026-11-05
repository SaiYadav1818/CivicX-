package com.janseva.complaint.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Complaint category")
public record CategoryResponse(
        Long id,
        String code,
        String name
) {
}
