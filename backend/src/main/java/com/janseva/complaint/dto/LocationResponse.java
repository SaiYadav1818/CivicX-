package com.janseva.complaint.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Complaint location")
public record LocationResponse(
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal accuracyMeters,
        String address,
        String landmark,
        String city,
        String state,
        String postalCode,
        String locationSource
) {
}
