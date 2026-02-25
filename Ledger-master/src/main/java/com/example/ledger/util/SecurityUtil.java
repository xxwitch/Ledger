package com.example.ledger.util;

/**
 * @author 霜月
 * @create 2025/12/25 09:43
 */

import com.example.ledger.entity.User;
import com.example.ledger.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityUtil {

    private final UserRepository userRepository;

    /**
     * 获取当前登录用户ID
     */
    public Long getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("用户未认证，返回默认用户ID");
                return getDefaultUserId();
            }

            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails) {
                String username = ((UserDetails) principal).getUsername();
                Optional<User> user = userRepository.findByUsernameAndDeletedFalse(username);
                return user.map(User::getId)
                        .orElseGet(() -> {
                            log.warn("用户不存在，返回默认用户ID: {}", username);
                            return getDefaultUserId();
                        });
            } else if (principal instanceof String) {
                // 处理特殊情况
                String username = (String) principal;
                Optional<User> user = userRepository.findByUsernameAndDeletedFalse(username);
                return user.map(User::getId)
                        .orElseGet(() -> {
                            log.warn("用户不存在，返回默认用户ID: {}", username);
                            return getDefaultUserId();
                        });
            } else {
                log.warn("无法识别的principal类型: {}", principal.getClass());
                return getDefaultUserId();
            }
        } catch (Exception e) {
            log.error("获取当前用户ID失败: {}", e.getMessage(), e);
            return getDefaultUserId();
        }
    }

    /**
     * 获取当前登录用户名
     */
    public String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return "anonymous";
            }

            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails) {
                return ((UserDetails) principal).getUsername();
            } else if (principal instanceof String) {
                return (String) principal;
            } else {
                return "unknown";
            }
        } catch (Exception e) {
            log.error("获取当前用户名失败: {}", e.getMessage(), e);
            return "unknown";
        }
    }

    /**
     * 获取当前用户详细信息
     */
    public Optional<User> getCurrentUser() {
        try {
            String username = getCurrentUsername();
            if ("anonymous".equals(username) || "unknown".equals(username)) {
                return Optional.empty();
            }
            return userRepository.findByUsernameAndDeletedFalse(username);
        } catch (Exception e) {
            log.error("获取当前用户信息失败: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * 获取当前用户昵称
     */
    public String getCurrentUserNickname() {
        return getCurrentUser()
                .map(user -> user.getNickname() != null ? user.getNickname() : user.getUsername())
                .orElse("系统用户");
    }

    /**
     * 检查是否有权限
     */
    public boolean hasRole(String role) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null) {
                return false;
            }
            return authentication.getAuthorities().stream()
                    .anyMatch(auth -> auth.getAuthority().equals("ROLE_" + role));
        } catch (Exception e) {
            log.error("检查权限失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 检查是否是管理员
     */
    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    /**
     * 获取客户端IP
     */
    public String getClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return "unknown";
            }

            HttpServletRequest request = attributes.getRequest();
            return getClientIp(request);
        } catch (Exception e) {
            log.error("获取客户端IP失败", e);
            return "unknown";
        }
    }

    /**
     * 从HttpServletRequest获取客户端IP
     */
    public String getClientIp(HttpServletRequest request) {
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

        // 多个代理时，第一个IP为真实IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }

    /**
     * 获取默认用户ID（系统用户）
     */
    private Long getDefaultUserId() {
        // 这里可以配置默认的系统用户ID
        // 或者在配置文件中读取
        return 1L;
    }
}