package com.example.ledger.controller;

/**
 * @author 霜月
 * @create 2025/12/9 23:21
 */

import com.example.ledger.dto.request.LoginRequest;
import com.example.ledger.dto.response.ApiResponse;
import com.example.ledger.dto.response.LoginResponse;
import com.example.ledger.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@Validated
@Slf4j
public class AuthController {

    @Autowired
    private UserService userService;

    @PostMapping("/login")
    // @ApiOperation("用户登录")  // 移除了 SpringFox 注解
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest,
                                            HttpServletRequest request) {
        try {
            String ip = getClientIp(request);
            LoginResponse loginResponse = userService.login(loginRequest, ip);

            log.info("用户 {} 登录成功，IP: {}", loginRequest.getUsername(), ip);

            return ApiResponse.success("登录成功", loginResponse);
        } catch (Exception e) {
            log.error("用户 {} 登录失败，原因: {}", loginRequest.getUsername(), e.getMessage());
            return ApiResponse.error(e.getMessage());
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}