package com.LocalService.lsp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final Map<String, RateLimitBucket> rateLimitBuckets = new ConcurrentHashMap<>();
    private static final int MAX_REQUESTS_PER_MINUTE = 100;
    private static final long WINDOW_SIZE_MS = 60 * 1000; // 1 minute

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String clientId = getClientIdentifier(request);
        RateLimitBucket bucket = rateLimitBuckets.computeIfAbsent(clientId, k -> new RateLimitBucket());

        if (bucket.allowRequest()) {
            // Request allowed
            filterChain.doFilter(request, response);
        } else {
            // Rate limit exceeded
            response.setStatus(429); // TOO_MANY_REQUESTS
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Rate limit exceeded. Max " + MAX_REQUESTS_PER_MINUTE + " requests per minute.\"}");
        }
    }

    /**
     * Get client identifier (IP address or authenticated user)
     */
    private String getClientIdentifier(HttpServletRequest request) {
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isEmpty()) {
            clientIp = request.getRemoteAddr();
        }
        return clientIp;
    }

    /**
     * Token bucket implementation for rate limiting
     */
    private static class RateLimitBucket {
        private final AtomicLong lastRefillTime = new AtomicLong(System.currentTimeMillis());
        private final AtomicLong tokensAvailable = new AtomicLong(MAX_REQUESTS_PER_MINUTE);

        /**
         * Check if a request is allowed based on token bucket algorithm
         */
        synchronized boolean allowRequest() {
            long now = System.currentTimeMillis();
            long timePassed = now - lastRefillTime.get();

            // Refill tokens based on time passed
            if (timePassed >= WINDOW_SIZE_MS) {
                tokensAvailable.set(MAX_REQUESTS_PER_MINUTE);
                lastRefillTime.set(now);
            }

            // Check if we have tokens available
            if (tokensAvailable.get() > 0) {
                tokensAvailable.decrementAndGet();
                return true;
            }
            return false;
        }
    }
}
