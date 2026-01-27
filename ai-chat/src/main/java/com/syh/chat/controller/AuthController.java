package com.syh.chat.controller;

import com.syh.chat.dto.AuthResponse;
import com.syh.chat.dto.LoginRequest;
import com.syh.chat.dto.RegisterRequest;
import com.syh.chat.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    private final AuthService authService;
    
    public AuthController(AuthService authService) {
        this.authService = authService;
    }
    
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        if (response.getToken() != null) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.badRequest().body(response);
    }
    
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        if (response.getToken() != null) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.badRequest().body(response);
    }
    
    @PostMapping("/validate")
    public ResponseEntity<Boolean> validateToken(@RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            Long userId = authService.validateTokenAndGetUserId(token);
            return ResponseEntity.ok(userId != null);
        }
        return ResponseEntity.ok(false);
    }
}

