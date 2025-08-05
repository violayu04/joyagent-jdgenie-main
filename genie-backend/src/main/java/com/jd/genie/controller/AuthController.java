package com.jd.genie.controller;

import com.jd.genie.dto.auth.AuthResponse;
import com.jd.genie.dto.auth.LoginRequest;
import com.jd.genie.dto.auth.RegisterRequest;
import com.jd.genie.entity.User;
import com.jd.genie.security.JwtUtil;
import com.jd.genie.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            // 检查用户名是否已存在
            if (userService.existsByUsername(request.getUsername())) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "用户名已存在");
                return ResponseEntity.badRequest().body(error);
            }

            // 检查邮箱是否已存在
            if (userService.existsByEmail(request.getEmail())) {
                Map<String, String> error = new HashMap<>();
                error.put("message", "邮箱已存在");
                return ResponseEntity.badRequest().body(error);
            }

            // 创建新用户
            User user = userService.createUser(request.getUsername(), request.getEmail(), request.getPassword());
            
            // 生成JWT令牌
            UserDetails userDetails = userService.loadUserByUsername(user.getUsername());
            String token = jwtUtil.generateToken(userDetails);

            log.info("User registered successfully: {}", user.getUsername());
            
            return ResponseEntity.ok(new AuthResponse(token, user.getUsername(), user.getEmail(), "注册成功"));
        } catch (Exception e) {
            log.error("Registration failed", e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "注册失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            // 验证用户凭据
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User user = userService.findByUsername(userDetails.getUsername()).orElseThrow();
            
            // 生成JWT令牌
            String token = jwtUtil.generateToken(userDetails);
            
            // 更新最后登录时间
            userService.updateLastLogin(request.getUsername());

            log.info("User logged in successfully: {}", user.getUsername());
            
            return ResponseEntity.ok(new AuthResponse(token, user.getUsername(), user.getEmail()));
        } catch (BadCredentialsException e) {
            log.warn("Login failed for user: {}", request.getUsername());
            Map<String, String> error = new HashMap<>();
            error.put("message", "用户名或密码错误");
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Login failed", e);
            Map<String, String> error = new HashMap<>();
            error.put("message", "登录失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        // JWT是无状态的，前端删除token即可
        Map<String, String> response = new HashMap<>();
        response.put("message", "注销成功");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                if (jwtUtil.validateToken(token)) {
                    String username = jwtUtil.extractUsername(token);
                    User user = userService.findByUsername(username).orElseThrow();
                    
                    Map<String, Object> response = new HashMap<>();
                    response.put("valid", true);
                    response.put("username", user.getUsername());
                    response.put("email", user.getEmail());
                    return ResponseEntity.ok(response);
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("valid", false);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("valid", false);
            return ResponseEntity.ok(response);
        }
    }
}