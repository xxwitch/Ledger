package com.example.ledger.init;

/**
 * @author 霜月
 * @create 2025/12/10 02:11
 */

import com.example.ledger.entity.User;
import com.example.ledger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

//初始化创建一次用
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        createAdminUser();
    }

    private void createAdminUser() {
        // 检查管理员账号是否已存在
        if (userRepository.existsByUsername("admin")) {
            log.info("管理员账号已存在，跳过创建");
            return;
        }

        // 创建管理员账号
        User admin = User.builder()
                .username("admin")
                .password(passwordEncoder.encode("Admin123!@#"))  // 这里会动态加密
                .nickname("系统管理员")
                .userType("ADMIN")
                .email("admin@example.com")
                .phone("13800138000")
                .status(1)
                .deleted(false)
                .lastLoginTime(null)
                .lastLoginIp(null)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        userRepository.save(admin);
        log.info("✅ 管理员账号创建成功");
        log.info("   用户名: admin");
        log.info("   密码: Admin123!@#");
        log.info("   用户类型: ADMIN");
    }
}