package com.janseva.auth;

import com.janseva.auth.dto.AuthResponse;
import com.janseva.auth.dto.LoginRequest;
import com.janseva.auth.dto.RegisterRequest;
import com.janseva.common.ApiException;
import com.janseva.config.AppProperties;
import com.janseva.security.JwtService;
import com.janseva.user.Role;
import com.janseva.user.RoleName;
import com.janseva.user.RoleRepository;
import com.janseva.user.User;
import com.janseva.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;

    private AuthService authService;
    private final Role citizenRole = new Role(RoleName.CITIZEN, "Citizen");
    private final AppProperties properties = new AppProperties(
            new AppProperties.Jwt("test-secret-key-that-is-long-enough-for-hs256-signing", 30, 7),
            new AppProperties.Cors(java.util.List.of("http://localhost:3000")),
            new AppProperties.RateLimit(10, 20, 30),
            new AppProperties.Admin("admin@test.local", "Admin@123"),
            new AppProperties.Storage("./target/test-storage", 5_242_880L, 5)
    );

    @BeforeEach
    void setUp() {
        JwtService jwtService = new JwtService(properties);
        authService = new AuthService(userRepository, roleRepository, refreshTokenRepository,
                passwordEncoder, authenticationManager, jwtService, properties);
    }

    @Test
    void registersNewCitizen() {
        when(userRepository.existsByEmail("new@test.local")).thenReturn(false);
        when(roleRepository.findByName(RoleName.CITIZEN)).thenReturn(Optional.of(citizenRole));
        when(passwordEncoder.encode("Password1")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return u;
        });
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.register(
                new RegisterRequest("New Citizen", "new@test.local", "Password1", null));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.user().email()).isEqualTo("new@test.local");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void rejectsDuplicateEmail() {
        when(userRepository.existsByEmail("dup@test.local")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("Dup", "dup@test.local", "Password1", null)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loginIssuesTokens() {
        User user = new User("citizen@test.local", "hashed", null, "Citizen One");
        when(userRepository.findByEmail("citizen@test.local")).thenReturn(Optional.of(user));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.login(new LoginRequest("citizen@test.local", "Password1"));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(user.getLastLoginAt()).isNotNull();
        verify(authenticationManager).authenticate(any());
    }

    @Test
    void refreshRejectsExpiredToken() {
        String raw = "some-refresh-token";
        JwtService jwtService = new JwtService(properties);
        RefreshToken expired = new RefreshToken(
                new User("c@test.local", "h", null, "C"),
                jwtService.hashToken(raw),
                Instant.now().minusSeconds(60)
        );
        when(refreshTokenRepository.findByTokenHash(jwtService.hashToken(raw))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh(new com.janseva.auth.dto.RefreshRequest(raw)))
                .isInstanceOf(ApiException.class);
    }
}
