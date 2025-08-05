package com.jd.genie.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String username;
    private String email;
    private String message;
    
    public AuthResponse(String token, String username, String email) {
        this.token = token;
        this.username = username;
        this.email = email;
        this.message = "登录成功";
    }
}