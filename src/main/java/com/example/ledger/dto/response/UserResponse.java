package com.example.ledger.dto.response;

/**
 * @author 霜月
 * @create 2025/12/11 14:40
 * AddUserResponse用户信息响应DTO
 */

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息响应DTO
 */
@Data
public class UserResponse {

    private Long id;
    private String username;
    private String nickname;
    private String userType;
    private String email;
    private String phone;
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginTime;

    private String lastLoginIp;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}