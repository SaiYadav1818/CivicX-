package com.janseva.complaint.dto;

import com.janseva.complaint.LocationSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Request to create a new civic complaint")
public record ComplaintCreateRequest(
        @Schema(description = "Complaint category id", example = "1")
        @NotNull Long categoryId,

        @Schema(description = "Short complaint title", example = "Garbage dumped beside main road")
        @NotBlank @Size(max = 200) String title,

        @Schema(description = "Detailed description of the issue", example = "Garbage has been accumulating near the bus stop for a week.")
        @NotBlank @Size(max = 2000) String description,

        @Schema(description = "Latitude between -90 and 90", example = "17.4840000")
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,

        @Schema(description = "Longitude between -180 and 180", example = "78.3910000")
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,

        @Schema(description = "GPS accuracy in meters", example = "12.5")
        @DecimalMin("0") @DecimalMax("100000") BigDecimal accuracyMeters,

        @Schema(description = "Human readable address", example = "Kukatpally, Hyderabad, Telangana")
        @Size(max = 500) String address,

        @Schema(description = "Nearby landmark", example = "Near the main bus stop")
        @Size(max = 200) String landmark,

        @Schema(description = "City", example = "Hyderabad")
        @Size(max = 100) String city,

        @Schema(description = "State", example = "Telangana")
        @Size(max = 100) String state,

        @Schema(description = "Postal code", example = "500072")
        @Size(max = 20) String postalCode,

        @Schema(description = "Ward id, if available", example = "1")
        Long wardId,

        @Schema(description = "Zone id, if available", example = "1")
        Long zoneId,

        @Schema(description = "How the location was captured", example = "DEVICE_GPS")
        LocationSource locationSource
) {
}
