package com.chatops.auth;

import com.chatops.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JwtService}.
 * Redis is mocked to keep the test hermetic.
 */
@DisplayName("JwtService")
class JwtServiceTest {

    private static final String SECRET =
            "test-secret-key-that-is-at-least-64-characters-long-for-hs256-algorithm-padding";
    private static final long ACCESS_EXPIRY_MS = 3_600_000L; // 1 hour

    private JwtService jwtService;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        jwtService = new JwtService(SECRET, ACCESS_EXPIRY_MS, redisTemplate);
    }

    private User buildUser() {
        return User.builder()
                .id(1L)
                .username("alice")
                .email("alice@example.com")
                .passwordHash("hash")
                .build();
    }

    @Test
    @DisplayName("generateAccessToken — produces a non-blank JWT")
    void generateAccessToken_returnsNonBlankToken() {
        String token = jwtService.generateAccessToken(buildUser());
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("validateAndExtractClaims — parses subject correctly")
    void validateToken_parsesSubjectCorrectly() {
        User user = buildUser();
        String token = jwtService.generateAccessToken(user);

        Claims claims = jwtService.validateAndExtractClaims(token);

        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.get("userId", Long.class)).isEqualTo(1L);
    }

    @Test
    @DisplayName("validateAndExtractClaims — rejects a tampered token")
    void validateToken_rejectsModifiedToken() {
        String token = jwtService.generateAccessToken(buildUser());
        String tampered = token + "tampered";

        assertThatThrownBy(() -> jwtService.validateAndExtractClaims(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("validateAndExtractClaims — rejects a blacklisted token")
    void validateToken_rejectsBlacklistedToken() {
        User user = buildUser();
        String token = jwtService.generateAccessToken(user);
        Claims claims = jwtService.validateAndExtractClaims(token);

        // Simulate token being in blacklist
        when(redisTemplate.hasKey("jwt:blacklist:" + claims.getId())).thenReturn(true);

        assertThatThrownBy(() -> jwtService.validateAndExtractClaims(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    @DisplayName("blacklistToken — stores jti in Redis with correct TTL")
    void blacklistToken_storesJtiInRedis() {
        String token = jwtService.generateAccessToken(buildUser());
        jwtService.blacklistToken(token);

        verify(valueOps).set(
                argThat(key -> key.startsWith("jwt:blacklist:")),
                eq("revoked"),
                longThat(ttl -> ttl > 0 && ttl <= ACCESS_EXPIRY_MS),
                any()
        );
    }
}
