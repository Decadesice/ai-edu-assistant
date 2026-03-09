package com.syh.chat.interceptor;

import com.syh.chat.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    
    private final AuthService authService;
    
    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }
    
    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        String path = request.getRequestURI();
        
        if (path.startsWith("/api/auth/") || path.startsWith("/static/") || path.equals("/") || path.equals("/index.html")) {
            return true;
        }
        
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"未授权访问\"}");
            return false;
        }
        
        String token = authHeader.substring(7);
        Long userId = authService.validateTokenAndGetUserId(token);
        
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Token无效或已过期\"}");
            return false;
        }
        
        request.setAttribute("userId", userId);
        return true;
    }
}

