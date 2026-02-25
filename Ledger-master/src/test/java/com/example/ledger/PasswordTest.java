package com.example.ledger;

/**
 * @author 霜月
 * @create 2025/12/10 01:39
 */

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public class PasswordTest {
    public static void main(String[] args) {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

        // 数据库中的加密密码
        String encryptedPassword = "$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM9lG9sFp/9wJb5iGJqW";
        String rawPassword = "Admin123!";

        // 验证密码是否匹配
        boolean matches = passwordEncoder.matches(rawPassword, encryptedPassword);
        System.out.println("密码匹配结果: " + matches);

        // 生成一个新的加密密码用于对比
        String newEncryptedPassword = passwordEncoder.encode(rawPassword);
        System.out.println("新生成的加密密码: " + newEncryptedPassword);

        // 验证新生成的密码
        boolean newMatches = passwordEncoder.matches(rawPassword, newEncryptedPassword);
        System.out.println("新密码匹配结果: " + newMatches);
    }
}