package com.janseva.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final int WINDOW_SECONDS = 60;

    private final int authPerMinute;
    private final ObjectMapper objectMapper;
    private final Map<String, Entry> counters = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(AppProperties properties, ObjectMapper objectMapper) {
        this.authPerMinute = properties.rateLimit().authPerMinute();
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/v1/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = clientKey(request);
        Instant now = Instant.now();
        Entry entry = counters.compute(key, (k, v) -> {
            if (v == null || v.windowStart.isBefore(now.minusSeconds(WINDOW_SECONDS))) {
                return new Entry(now, new AtomicInteger(1));
            }
            v.count.incrementAndGet();
            return v;
        });

        if (entry.count.get() > authPerMinute) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), Map.of(
                    "timestamp", Instant.now().toString(),
                    "status", 429,
                    "error", "TOO_MANY_REQUESTS",
                    "message", "Too many authentication attempts. Try again later."
            ));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        return ip + "|" + request.getRequestURI();
    }

    private record Entry(Instant windowStart, AtomicInteger count) {
    }
}
