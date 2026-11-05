package com.janseva.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "civicx")
public record CivicxProperties(
        Ai ai,
        Duplicate duplicate,
        Outbox outbox
) {
    public record Ai(boolean enabled, String provider) {
    }

    public record Duplicate(
            boolean enabled,
            double locationRadiusMeters,
            double textThreshold,
            int imageHashThreshold,
            double imageEmbeddingThreshold,
            double duplicateThreshold,
            double reviewThreshold,
            Weights weights
    ) {
        public record Weights(double location, double text, double image, double category) {
        }
    }

    public record Outbox(long pollMs, int maxAttempts) {
    }
}
