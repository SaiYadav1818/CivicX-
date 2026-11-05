package com.janseva.geo;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class GeoService {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    public double haversineMeters(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) {
            throw new IllegalArgumentException("Coordinates must not be null");
        }
        return haversineMeters(lat1.doubleValue(), lng1.doubleValue(), lat2.doubleValue(), lng2.doubleValue());
    }

    public double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    public BoundingBox boundingBox(double latitude, double longitude, double radiusMeters) {
        double deltaLat = radiusMeters / 111_320.0;
        double deltaLng = radiusMeters / (111_320.0 * Math.cos(Math.toRadians(latitude)));
        return new BoundingBox(
                latitude - deltaLat, latitude + deltaLat,
                longitude - deltaLng, longitude + deltaLng);
    }

    public record BoundingBox(double minLat, double maxLat, double minLng, double maxLng) {
    }
}
