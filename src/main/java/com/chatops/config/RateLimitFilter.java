package com.chatops.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servlet filter that applies per-IP token-bucket rate limiting.
 *
 * <h3>Limits</h3>
 * <ul>
 *   <li>Auth endpoints ({@code /api/auth/**}): <b>10 requests / minute</b> — protects against
 *       brute-force login and registration spam.</li>
 *   <li>All other endpoints: <b>120 requests / minute</b> — a sensible default for API consumers.</li>
 * </ul>
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, SimpleTokenBucket> authBuckets = new ConcurrentHashMap<>();
    private final Map<String, SimpleTokenBucket> apiBuckets  = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String ip   = resolveClientIp(request);
        String path = request.getRequestURI();

        SimpleTokenBucket bucket = isAuthPath(path)
                ? authBuckets.computeIfAbsent(ip, k -> new SimpleTokenBucket(10, Duration.ofMinutes(1)))
                : apiBuckets.computeIfAbsent(ip, k -> new SimpleTokenBucket(120, Duration.ofMinutes(1)));

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for IP={} path={}", ip, path);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"status\":429,\"error\":\"Too Many Requests\"," +
                    "\"message\":\"Rate limit exceeded. Please slow down.\"}");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isAuthPath(String path) {
        return path.startsWith("/api/auth/");
    }

    /**
     * Resolves the real client IP, respecting common reverse-proxy headers.
     * Falls back to {@link HttpServletRequest#getRemoteAddr()} if no proxy header is present.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].strip();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.strip();
        }
        return request.getRemoteAddr();
    }

    /**
     * In-memory thread-safe Token Bucket implementation.
     */
    static class SimpleTokenBucket {
        private final long capacity;
        private final double refillRatePerMs;
        private double tokens;
        private long lastRefillTimestamp;

        public SimpleTokenBucket(long capacity, Duration refillPeriod) {
            this.capacity = capacity;
            this.refillRatePerMs = (double) capacity / refillPeriod.toMillis();
            this.tokens = capacity;
            this.lastRefillTimestamp = System.currentTimeMillis();
        }

        public synchronized boolean tryConsume(int count) {
            long now = System.currentTimeMillis();
            double refill = (now - lastRefillTimestamp) * refillRatePerMs;
            tokens = Math.min(capacity, tokens + refill);
            lastRefillTimestamp = now;

            if (tokens >= count) {
                tokens -= count;
                return true;
            }
            return false;
        }
    }
}
