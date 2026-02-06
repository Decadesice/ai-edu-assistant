package com.syh.chat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syh.chat.service.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RateLimitFilter(RateLimitService rateLimitService, RateLimitProperties properties) {
        this.rateLimitService = rateLimitService;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (HttpMethod.POST.matches(method) && "/api/unified/chat/stream".equals(path)) {
            if (!allow(request, "model", properties.getModel())) {
                writeLimited(response);
                return;
            }
        }

        if (HttpMethod.POST.matches(method)
                && ("/api/knowledge/documents/upload".equals(path) || "/api/knowledge/documents/upload-async".equals(path))) {
            if (!allow(request, "upload", properties.getUpload())) {
                writeLimited(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean allow(HttpServletRequest request, String bucketName, RateLimitProperties.Limit limit) {
        String principal = principalKey(request);
        String key = "rate:" + bucketName + ":" + principal;
        return rateLimitService.allow(key, limit.getLimit(), limit.getWindowSeconds());
    }

    private String principalKey(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId instanceof Long id) {
            return "u:" + id;
        }
        String ip = extractClientIp(request);
        return "ip:" + (ip == null ? "unknown" : ip);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int idx = xff.indexOf(',');
            return (idx >= 0 ? xff.substring(0, idx) : xff).trim();
        }
        return request.getRemoteAddr();
    }

    private void writeLimited(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                Map.of("code", "RATE_LIMITED", "message", "请求过于频繁，请稍后再试")
        ));
    }
}
