package com.janseva.auth;

import com.janseva.auth.dto.AuthResponse;
import com.janseva.auth.dto.LoginRequest;
import com.janseva.auth.dto.RefreshRequest;
import com.janseva.auth.dto.RegisterRequest;
import com.janseva.auth.dto.UserDto;
import com.janseva.common.ApiException;
import com.janseva.config.AppProperties;
import com.janseva.security.JwtService;
import com.janseva.user.Role;
import com.janseva.user.RoleName;
import com.janseva.user.RoleRepository;
import com.janseva.user.User;
import com.janseva.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AppProperties properties;

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       AppProperties properties) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.properties = properties;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "Email is already registered");
        }
        if (request.phone() != null && userRepository.existsByPhone(request.phone())) {
            throw new ApiException(HttpStatus.CONFLICT, "Phone number is already registered");
        }

        User user = new User(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.phone(),
                request.fullName()
        );
        Role citizenRole = roleRepository.findByName(RoleName.CITIZEN)
                .orElseThrow(() -> new IllegalStateException("CITIZEN role is not seeded"));
        user.addRole(citizenRole);
        userRepository.save(user);

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        user.setLastLoginAt(Instant.now());
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String tokenHash = jwtService.hashToken(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (!stored.isValid(Instant.now())) {
            stored.revoke();
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }

        stored.revoke();
        return issueTokens(stored.getUser());
    }

    private AuthResponse issueTokens(User user) {
        List<String> roles = user.getRoles().stream()
                .map(r -> r.getName().name())
                .toList();
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), roles);

        String refreshToken = jwtService.generateRefreshToken();
        refreshTokenRepository.save(new RefreshToken(
                user,
                jwtService.hashToken(refreshToken),
                Instant.now().plusSeconds(jwtService.getRefreshTtlDays() * 24 * 3600)
        ));

        long accessTtl = properties.jwt().accessTokenTtlMinutes() * 60;
        return AuthResponse.of(accessToken, refreshToken, accessTtl, toDto(user));
    }

    private UserDto toDto(User user) {
        return new UserDto(user.getId(), user.getFullName(), user.getEmail(), user.getPhone());
    }
}
