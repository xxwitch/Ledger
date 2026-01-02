package com.example.ledger.controller;

/**
 * @author 霜月
 * @create 2025/12/11 12:08
 */

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/test")
@Slf4j
public class TestController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        log.info("接收到测试请求 /ping");
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "后端服务正常运行");
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    @PostMapping("/echo")
    public Map<String, Object> echo(@RequestBody Map<String, Object> request) {
        log.info("接收到测试请求 /echo: {}", request);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "请求已接收");
        response.put("data", request);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
}