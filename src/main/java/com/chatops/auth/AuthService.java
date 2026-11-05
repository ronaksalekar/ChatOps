package com.chatops.auth;

import com.chatops.auth.dto.AuthResponse;
import com.chatops.auth.dto.LoginRequest;
import com.chatops.auth.dto.RegisterRequest;
import com.chatops.exception.ConflictException;
import com.chatops.user.User;
import com.chatops.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Handles user registration, login, token refresh, and logout.
 *
 * <p>Refresh tokens are opaque random strings stored in Redis with a TTL.
 * Access tokens are short-lived JWTs validated by {@link JwtService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String REFRESH_TOKEN_PREFIX = "auth:refresh:";
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.jwt.access-token-expiry-ms}")
    private long accessTokenExpiryMs;

    @Value("${app.jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiryMs;

    /**
     * Registers a new user and returns auth tokens.
     *
     * @throws ConflictException if username or email is already taken
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username '" + request.username() + "' is already taken");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email '" + request.email() + "' is already registered");
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.username())
                .build();

        userRepository.save(user);
        log.info("Registered new user: {}", user.getUsername());

        return buildAuthResponse(user);
    }

    /**
     * Authenticates a user with username and password.
     *
     * @throws org.springframework.security.core.AuthenticationException on bad credentials
     */
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new RuntimeException("User not found after authentication"));

        log.info("User logged in: {}", user.getUsername());
        return buildAuthResponse(user);
    }

    /**
     * Issues a new access token in exchange for a valid refresh token.
     *
     * @param refreshToken the opaque refresh token
     * @return new {@link AuthResponse} with a fresh access token
     * @throws IllegalArgumentException if the refresh token is invalid or expired
     */
    @Transactional
    public AuthResponse refresh(String refreshToken) {
        String redisKey = REFRESH_TOKEN_PREFIX + refreshToken;
        String username = redisTemplate.opsForValue().get(redisKey);

        if (username == null) {
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Rotate: delete old refresh token and issue new one
        redisTemplate.delete(redisKey);
        return buildAuthResponse(user);
    }

    /**
     * Revokes both the access token (blacklisted in Redis) and the refresh token.
     *
     * @param accessToken  the current access token to blacklist
     * @param refreshToken the refresh token to delete
     */
    public void logout(String accessToken, String refreshToken) {
        jwtService.blacklistToken(accessToken);
        if (refreshToken != null) {
            redisTemplate.delete(REFRESH_TOKEN_PREFIX + refreshToken);
        }
    }

    // ===== Helpers =====

    private AuthResponse buildAuthResponse(User user) {
        String accessToken  = jwtService.generateAccessToken(user);
        String refreshToken = generateAndStoreRefreshToken(user.getUsername());
        return AuthResponse.of(accessToken, refreshToken, accessTokenExpiryMs,
                user.getId(), user.getUsername());
    }

    private String generateAndStoreRefreshToken(String username) {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_PREFIX + token,
                username,
                refreshTokenExpiryMs,
                TimeUnit.MILLISECONDS
        );
        return token;
    }
}
