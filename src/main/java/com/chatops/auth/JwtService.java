package com.chatops.auth;

import com.chatops.user.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Handles JWT access token generation, validation, and blacklisting via Redis.
 */
@Slf4j
@Service
public class JwtService {

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    private final SecretKey signingKey;
    private final long accessTokenExpiryMs;
    private final StringRedisTemplate redisTemplate;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiry-ms}") long accessTokenExpiryMs,
            StringRedisTemplate redisTemplate
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiryMs = accessTokenExpiryMs;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Generates a signed JWT access token for the given user.
     *
     * @param user the authenticated user
     * @return signed JWT string
     */
    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())          // jti — used for blacklisting
                .subject(user.getUsername())
                .claim("userId", user.getId())
                .claim("email", user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessTokenExpiryMs)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parses and validates a JWT string.
     *
     * @param token the raw JWT
     * @return parsed {@link Claims}
     * @throws JwtException if the token is invalid, expired, or blacklisted
     */
    public Claims validateAndExtractClaims(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        // Check token blacklist
        if (isBlacklisted(claims.getId())) {
            throw new JwtException("Token has been revoked");
        }

        return claims;
    }

    /**
     * Extracts the username (subject) from a token without full validation.
     * Only use this when you intend to validate the token separately.
     */
    public String extractUsername(String token) {
        return validateAndExtractClaims(token).getSubject();
    }

    /**
     * Blacklists a token by adding its jti to Redis with a TTL matching the token's remaining life.
     *
     * @param token the raw JWT to revoke
     */
    public void blacklistToken(String token) {
        try {
            Claims claims = validateAndExtractClaims(token);
            String jti = claims.getId();
            long remainingMs = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (remainingMs > 0) {
                redisTemplate.opsForValue().set(
                        BLACKLIST_PREFIX + jti,
                        "revoked",
                        remainingMs,
                        TimeUnit.MILLISECONDS
                );
                log.debug("Blacklisted JWT jti={} for {}ms", jti, remainingMs);
            }
        } catch (JwtException e) {
            log.warn("Attempted to blacklist an already invalid token: {}", e.getMessage());
        }
    }

    /**
     * Checks whether a token's jti is in the blacklist.
     */
    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
    }
}
