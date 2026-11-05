package com.janseva.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Cors cors,
        RateLimit rateLimit,
        Admin admin,
        Storage storage
) {
    public record Jwt(String secret, long accessTokenTtlMinutes, long refreshTokenTtlDays) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record RateLimit(int authPerMinute, int complaintPerHour, int imageUploadPerHour) {
    }

    public record Admin(String email, String password) {
    }

    public record Storage(String localDir, long maxImageSizeBytes, int maxImagesPerComplaint) {
    }
}
