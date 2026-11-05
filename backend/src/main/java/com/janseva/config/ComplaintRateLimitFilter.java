package com.janseva.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ComplaintRateLimitFilter extends OncePerRequestFilter {

    private static final int WINDOW_SECONDS = 3600;

    private final int complaintPerHour;
    private final int imageUploadPerHour;
    private final ObjectMapper objectMapper;
    private final Map<String, Entry> counters = new ConcurrentHashMap<>();

    public ComplaintRateLimitFilter(AppProperties properties, ObjectMapper objectMapper) {
        this.complaintPerHour = properties.rateLimit().complaintPerHour();
        this.imageUploadPerHour = properties.rateLimit().imageUploadPerHour();
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        boolean complaintCreation = uri.equals("/api/v1/complaints") && "POST".equalsIgnoreCase(request.getMethod());
        boolean imageUpload = uri.matches("^/api/v1/complaints/\\d+/images$") && "POST".equalsIgnoreCase(request.getMethod());

        if (!complaintCreation && !imageUpload) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            filterChain.doFilter(request, response);
            return;
        }

        int limit = complaintCreation ? complaintPerHour : imageUploadPerHour;
        String key = userId + "|" + (complaintCreation ? "complaint" : "image");

        Instant now = Instant.now();
        Entry entry = counters.compute(key, (k, v) -> {
            if (v == null || v.windowStart.isBefore(now.minusSeconds(WINDOW_SECONDS))) {
                return new Entry(now, new AtomicInteger(1));
            }
            v.count.incrementAndGet();
            return v;
        });

        if (entry.count.get() > limit) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), Map.of(
                    "timestamp", Instant.now().toString(),
                    "status", 429,
                    "error", "TOO_MANY_REQUESTS",
                    "message", "Rate limit exceeded for complaint operations. Try again later."
            ));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private record Entry(Instant windowStart, AtomicInteger count) {
    }
}
