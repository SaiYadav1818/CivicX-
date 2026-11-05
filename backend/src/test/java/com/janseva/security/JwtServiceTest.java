package com.janseva.security;

import com.janseva.config.AppProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(new AppProperties(
            new AppProperties.Jwt("test-secret-key-that-is-long-enough-for-hs256-signing", 30, 7),
            new AppProperties.Cors(List.of("http://localhost:3000")),
            new AppProperties.RateLimit(10, 20),
            new AppProperties.Admin("admin@test.local", "Admin@123")
    ));

    @Test
    void generatesAndParsesAccessToken() {
        String token = jwtService.generateAccessToken(42L, "citizen@test.local", List.of("CITIZEN", "ADMIN"));

        Claims claims = jwtService.parse(token);

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("email", String.class)).isEqualTo("citizen@test.local");
        assertThat(claims.get("roles", List.class)).containsExactly("CITIZEN", "ADMIN");
    }

    @Test
    void hashesTokensDeterministically() {
        String token = "abc123";
        assertThat(jwtService.hashToken(token)).isEqualTo(jwtService.hashToken(token));
        assertThat(jwtService.hashToken(token)).hasSize(64);
    }
}
