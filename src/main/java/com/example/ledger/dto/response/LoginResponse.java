package com.example.ledger.dto.response;

/**
 * @author 霜月
 * @create 2025/12/9 23:03
 */

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private String tokenType = "Bearer";
    private Long userId;
    private String username;
    private String nickname;
    private String userType;
    private Long expiresIn;

    public LoginResponse(String token, Long userId, String username,
                         String nickname, String userType, Long expiresIn) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
        this.userType = userType;
        this.expiresIn = expiresIn;
    }
}