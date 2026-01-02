package com.example.ledger.controller;

/**
 * @author 霜月
 * @create 2025/12/11 14:41
 * UserController用户管理控制器
 */
import com.example.ledger.dto.request.AddUserRequest;
import com.example.ledger.dto.request.AdminChangePasswordRequest;
import com.example.ledger.dto.request.ChangePasswordRequest;
import com.example.ledger.dto.request.ResetPasswordRequest;
import com.example.ledger.dto.response.ApiResponse;
import com.example.ledger.dto.response.UserResponse;
import com.example.ledger.entity.User;
import com.example.ledger.service.UserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理控制器
 */
@RestController
@RequestMapping("/users")
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;

    @Value("${app.default-password:User@Xk9#m!n7*}")
    private String defaultPassword;

    /**
     * 测试接口
     */
    @GetMapping("/test")
    public ApiResponse<String> test() {
        log.info("测试接口被调用");
        return ApiResponse.success("服务器正常运行", "测试成功");
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/me")
    public ApiResponse<UserResponse> getCurrentUser() {
        try {
            // 从SecurityContext获取当前认证用户
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return ApiResponse.error(401, "用户未认证");
            }

            String username = authentication.getName();
            log.info("获取当前用户信息，用户名: {}", username);

            // 获取用户信息
            User user = userService.getUserByUsername(username);
            UserResponse userResponse = convertToUserResponse(user);

            return ApiResponse.success("获取成功", userResponse);
        } catch (Exception e) {
            log.error("获取当前用户信息失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取用户信息失败: " + e.getMessage());
        }
    }

    /**
     * 修改当前用户密码
     */
    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        try {
            // 获取当前登录用户
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();

            log.info("用户 {} 修改密码", username);

            // 调用服务层修改密码
            userService.changePassword(username, request);

            return ApiResponse.success("密码修改成功", null);
        } catch (Exception e) {
            log.error("修改密码失败: {}", e.getMessage(), e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 管理员修改用户密码
     */
    @PutMapping("/{userId}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> adminChangePassword(
            @PathVariable Long userId,
            @Valid @RequestBody AdminChangePasswordRequest request) {
        try {
            log.info("管理员修改用户密码，用户ID: {}", userId);

            // 设置用户ID
            request.setUserId(userId);

            // 调用服务层修改密码
            userService.adminChangePassword(request);

            return ApiResponse.success("密码修改成功", null);
        } catch (Exception e) {
            log.error("管理员修改用户密码失败: {}", e.getMessage(), e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 添加用户（仅管理员）
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserResponse> addUser(@Valid @RequestBody AddUserRequest request) {
        try {
            log.info("管理员添加用户，用户名: {}, 用户类型: {}", request.getUsername(), request.getUserType());
            UserResponse userResponse = userService.addUser(request);
            return ApiResponse.success("用户添加成功", userResponse);
        } catch (Exception e) {
            log.error("添加用户失败: {}", e.getMessage(), e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取用户列表（仅管理员）- 支持分页
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Page<UserResponse>> getUserList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) Integer status) {
        try {
            // 创建分页对象，按创建时间倒序排列
            Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));

            // 调用服务层获取分页数据
            Page<UserResponse> userPage = userService.getUserList(pageable, username, userType, status);

            return ApiResponse.success("获取成功", userPage);
        } catch (Exception e) {
            log.error("获取用户列表失败: {}", e.getMessage(), e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 搜索用户（新增）- 根据用户名、昵称、状态查询（分页）
     */
    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Page<UserResponse>> searchUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,  // 改为keyword参数
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) Integer status) {
        try {
            // 创建分页对象，按创建时间倒序排列
            Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));

            log.info("搜索用户请求 - 关键字: {}, 用户类型: {}, 状态: {}, 页码: {}, 大小: {}",
                    keyword, userType, status, page, size);

            // 调用服务层搜索用户（使用优化后的方法）
            Page<UserResponse> userPage = userService.searchUsers(pageable, keyword, userType, status);

            return ApiResponse.success("搜索成功", userPage);
        } catch (Exception e) {
            log.error("搜索用户失败: {}", e.getMessage(), e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 搜索用户列表（新增）- 不分页版本
     */
    @GetMapping("/search/list")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<UserResponse>> searchUsersList(
            @RequestParam(required = false) String keyword,  // 改为keyword参数
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) Integer status) {
        try {
            log.info("搜索用户列表请求 - 关键字: {}, 用户类型: {}, 状态: {}",
                    keyword, userType, status);

            // 调用服务层搜索用户列表（不分页）
            List<UserResponse> userList = userService.searchUsersList(keyword, userType, status);

            return ApiResponse.success("搜索成功", userList);
        } catch (Exception e) {
            log.error("搜索用户列表失败: {}", e.getMessage(), e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取用户详情（管理员或本人）
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN') or authentication.principal.username == #username")
    public ApiResponse<UserResponse> getUserDetail(@PathVariable Long userId) {
        try {
            UserResponse userResponse = userService.getUserDetail(userId);
            return ApiResponse.success("获取成功", userResponse);
        } catch (Exception e) {
            log.error("获取用户详情失败: {}", e.getMessage(), e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 更新用户状态（仅管理员）
     */
    @PutMapping("/{userId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> updateUserStatus(@PathVariable Long userId,
                                              @RequestParam Integer status) {
        try {
            userService.updateUserStatus(userId, status);
            return ApiResponse.success("状态更新成功", null);
        } catch (Exception e) {
            log.error("更新用户状态失败: {}", e.getMessage(), e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 重置密码（请求体方式）
     */
    @PostMapping("/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        try {
            userService.resetPassword(request);
            return ApiResponse.success("密码重置成功，新密码为：" + defaultPassword, defaultPassword);
        } catch (Exception e) {
            log.error("重置密码失败: {}", e.getMessage(), e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 批量重置用户密码（仅管理员）
     * 修改：添加 @RequestBody 注解的正确使用
     */
    @PostMapping("/batch-reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> batchResetPassword(@Valid @RequestBody List<Long> userIds) {
        try {
            log.info("批量重置密码请求，用户ID列表：{}", userIds);

            if (userIds == null || userIds.isEmpty()) {
                return ApiResponse.error("用户ID列表不能为空");
            }

            userService.batchResetPassword(userIds);
            return ApiResponse.success("批量重置密码成功，新密码为：" + defaultPassword, defaultPassword);
        } catch (Exception e) {
            log.error("批量重置密码失败: {}", e.getMessage(), e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 快速重置密码（查询参数方式）
     */
    @PutMapping("/{userId}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> quickResetPassword(@PathVariable Long userId) {
        try {
            ResetPasswordRequest request = new ResetPasswordRequest();
            request.setUserId(userId);

            userService.resetPassword(request);
            return ApiResponse.success("密码重置成功，新密码为：" + defaultPassword, defaultPassword);
        } catch (Exception e) {
            log.error("重置密码失败: {}", e.getMessage(), e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 删除用户（仅管理员）
     */
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteUser(@PathVariable Long userId) {
        try {
            userService.deleteUser(userId);
            return ApiResponse.success("用户删除成功", null);
        } catch (Exception e) {
            log.error("删除用户失败: {}", e.getMessage(), e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 更新用户信息（仅管理员）
     */
    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserResponse> updateUser(@PathVariable Long userId,
                                                @Valid @RequestBody AddUserRequest request) {
        try {
            log.info("管理员更新用户信息，用户ID: {}", userId);
            UserResponse userResponse = userService.updateUser(userId, request);
            return ApiResponse.success("用户信息更新成功", userResponse);
        } catch (Exception e) {
            log.error("更新用户信息失败: {}", e.getMessage(), e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 转换User实体为UserResponse DTO
     */
    private UserResponse convertToUserResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setNickname(user.getNickname());
        response.setUserType(user.getUserType());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());
        response.setStatus(user.getStatus());
        response.setLastLoginTime(user.getLastLoginTime());
        response.setLastLoginIp(user.getLastLoginIp());
        response.setCreateTime(user.getCreateTime());
        response.setUpdateTime(user.getUpdateTime());
        return response;
    }
}