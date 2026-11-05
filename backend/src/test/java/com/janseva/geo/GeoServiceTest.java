package com.janseva.geo;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeoServiceTest {

    private final GeoService geoService = new GeoService();

    @Test
    void nearbyPointsAreClose() {
        double distance = geoService.haversineMeters(
                new BigDecimal("17.4840"), new BigDecimal("78.3910"),
                new BigDecimal("17.4842"), new BigDecimal("78.3912"));

        assertThat(distance).isGreaterThan(20.0).isLessThan(40.0);
    }

    @Test
    void distantPointsAreFar() {
        double distance = geoService.haversineMeters(
                new BigDecimal("17.4840"), new BigDecimal("78.3910"),
                new BigDecimal("18.0000"), new BigDecimal("80.0000"));

        assertThat(distance).isGreaterThan(100_000.0);
    }

    @Test
    void identicalPointsAreZero() {
        double distance = geoService.haversineMeters(17.4840, 78.3910, 17.4840, 78.3910);

        assertThat(distance).isZero();
    }

    @Test
    void boundingBoxCoversRadius() {
        GeoService.BoundingBox box = geoService.boundingBox(17.4840, 78.3910, 100);

        assertThat(box.minLat()).isLessThan(17.4840);
        assertThat(box.maxLat()).isGreaterThan(17.4840);
        assertThat(box.minLng()).isLessThan(78.3910);
        assertThat(box.maxLng()).isGreaterThan(78.3910);
    }

    @Test
    void nullCoordinatesRejected() {
        assertThatThrownBy(() -> geoService.haversineMeters(null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
