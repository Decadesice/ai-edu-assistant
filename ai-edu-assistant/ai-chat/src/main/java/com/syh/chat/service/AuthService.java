package com.syh.chat.service;

import com.syh.chat.dto.AuthResponse;
import com.syh.chat.dto.LoginRequest;
import com.syh.chat.dto.RegisterRequest;
import com.syh.chat.entity.User;
import com.syh.chat.repository.UserRepository;
import com.syh.chat.util.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    
    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }
    
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            return new AuthResponse(null, null, null, "用户名已存在");
        }
        
        if (userRepository.existsByEmail(request.getEmail())) {
            return new AuthResponse(null, null, null, "邮箱已被注册");
        }
        
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setIsActive(true);
        
        user = userRepository.save(user);
        
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        
        return new AuthResponse(token, user.getId(), user.getUsername(), "注册成功");
    }
    
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElse(null);
        
        if (user == null) {
            return new AuthResponse(null, null, null, "用户不存在");
        }
        
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return new AuthResponse(null, null, null, "密码错误");
        }
        
        if (!user.getIsActive()) {
            return new AuthResponse(null, null, null, "账号已被禁用");
        }
        
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        
        return new AuthResponse(token, user.getId(), user.getUsername(), "登录成功");
    }
    
    public Long validateTokenAndGetUserId(String token) {
        try {
            return jwtUtil.extractUserId(token);
        } catch (Exception e) {
            return null;
        }
    }
}

