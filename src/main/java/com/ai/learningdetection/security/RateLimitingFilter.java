package com.ai.learningdetection.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory rate limiter for authentication endpoints.
 * Prevents brute-force attacks on login, registration, and OTP endpoints.
 *
 * Limits:
 * - Login/register: 10 requests per minute per IP
 * - OTP send:        5 requests per minute per IP
 * - OTP verify:      5 requests per minute per IP
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final int AUTH_LIMIT_PER_MINUTE = 10;
    private static final int OTP_LIMIT_PER_MINUTE = 5;
    private static final long WINDOW_MS = 60_000L; // 1 minute

    // Map<"ip:endpoint-group", BucketEntry>
    private final ConcurrentHashMap<String, BucketEntry> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Only rate-limit auth endpoints
        if (!isRateLimitedPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Only rate-limit POST methods (login, register, OTP actions)
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);
        String group = getEndpointGroup(path);
        int limit = isOtpPath(path) ? OTP_LIMIT_PER_MINUTE : AUTH_LIMIT_PER_MINUTE;

        String key = clientIp + ":" + group;

        BucketEntry bucket = buckets.compute(key, (k, existing) -> {
            long now = System.currentTimeMillis();
            if (existing == null || now - existing.windowStart > WINDOW_MS) {
                return new BucketEntry(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });

        if (bucket.count.get() > limit) {
            log.warn("Rate limit exceeded for IP {} on {}", clientIp, group);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                "{\"success\":false,\"message\":\"Too many requests. Please wait a moment and try again.\"}"
            );
            return;
        }

        // Periodic cleanup of stale buckets (every 1000 requests)
        if (buckets.size() > 10_000) {
            long now = System.currentTimeMillis();
            buckets.entrySet().removeIf(e -> now - e.getValue().windowStart > WINDOW_MS * 5);
        }

        filterChain.doFilter(request, response);
    }

    private boolean isRateLimitedPath(String path) {
        return path.contains("/auth/");
    }

    private boolean isOtpPath(String path) {
        return path.contains("/send-otp") || path.contains("/verify-otp");
    }

    private String getEndpointGroup(String path) {
        if (path.contains("/send-otp")) return "otp-send";
        if (path.contains("/verify-otp")) return "otp-verify";
        if (path.contains("/login")) return "login";
        if (path.contains("/register")) return "register";
        if (path.contains("/firebase-login")) return "firebase-login";
        return "auth-other";
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static class BucketEntry {
        final long windowStart;
        final AtomicInteger count;

        BucketEntry(long windowStart, AtomicInteger count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
