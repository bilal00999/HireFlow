package com.example.demo.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight in-memory fixed-window rate limiter for the abuse-prone endpoints:
 * login/registration (credential stuffing) and application submit (spam). Keyed
 * by client IP + a coarse endpoint bucket. Deliberately dependency-free (no
 * Redis) so it works on the free tier; the trade-off is that limits are
 * per-instance, which is fine for a single-node deployment.
 *
 * <p>Runs before the JWT filter so throttling happens before any auth work. On
 * limit breach it returns HTTP 429 with the standard {@link ErrorResponse} body.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final int limit;
    private final long windowMs;

    /** Per-key counter for the current window. */
    private final Map<String, Window> counters = new ConcurrentHashMap<>();

    public RateLimitFilter(ObjectMapper objectMapper,
                           @Value("${app.rate-limit.requests:20}") int limit,
                           @Value("${app.rate-limit.window-seconds:60}") long windowSeconds) {
        this.objectMapper = objectMapper;
        this.limit = limit;
        this.windowMs = windowSeconds * 1000;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return bucketFor(request) == null;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String key = clientIp(request) + ":" + bucketFor(request);
        if (!allow(key)) {
            writeTooManyRequests(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Which rate-limit bucket a request falls into, or null if it isn't limited.
     * POSTs to auth and application-submit are the only throttled routes.
     */
    private String bucketFor(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/v1/auth/")) {
            return "auth";
        }
        if (uri.equals("/api/v1/applications")) {
            return "apply";
        }
        return null;
    }

    /** Fixed-window check: reset the counter when the window rolls over. */
    private boolean allow(String key) {
        long now = System.currentTimeMillis();
        Window window = counters.compute(key, (k, existing) -> {
            if (existing == null || now - existing.start >= windowMs) {
                return new Window(now);
            }
            return existing;
        });
        return window.count.incrementAndGet() <= limit;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // First hop is the original client when behind a proxy/load balancer.
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        ErrorResponse body = ErrorResponse.of(
                "RATE_LIMITED",
                "Too many requests. Please slow down and try again shortly.",
                request.getRequestURI());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    /** A window start timestamp plus its running request count. */
    private static final class Window {
        final long start;
        final AtomicInteger count = new AtomicInteger(0);

        Window(long start) {
            this.start = start;
        }
    }
}
