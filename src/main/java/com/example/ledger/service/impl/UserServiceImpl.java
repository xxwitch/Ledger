package com.example.ledger.service.impl;

/**
 * @author 霜月
 * @create 2025/12/9 23:16
 */
import com.example.ledger.dto.request.AddUserRequest;
import com.example.ledger.dto.request.AdminChangePasswordRequest;
import com.example.ledger.dto.request.ChangePasswordRequest;
import com.example.ledger.dto.request.LoginRequest;
import com.example.ledger.dto.request.ResetPasswordRequest;
import com.example.ledger.dto.response.LoginResponse;
import com.example.ledger.dto.response.UserResponse;
import com.example.ledger.entity.User;
import com.example.ledger.exception.BusinessException;
import com.example.ledger.repository.UserRepository;
import com.example.ledger.security.jwt.JwtTokenUtil;
import com.example.ledger.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Value("${app.default-password:User@Xk9#m!n7*}")
    private String defaultPassword;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest loginRequest, String ip) {
        try {
            // 验证用户名密码
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 生成token
            UserDetails userDetails = userDetailsService.loadUserByUsername(loginRequest.getUsername());
            String token = jwtTokenUtil.generateToken(userDetails);

            // 获取用户信息
            User user = getUserByUsername(loginRequest.getUsername());

            // 更新最后登录信息
            updateLoginInfo(user.getId(), ip);

            // 返回登录响应
            Long expiresIn = jwtTokenUtil.getRemainingExpiration(token);

            return new LoginResponse(
                    token,
                    user.getId(),
                    user.getUsername(),
                    user.getNickname(),
                    user.getUserType(),
                    expiresIn
            );
        } catch (BadCredentialsException e) {
            log.error("用户名或密码错误: {}", loginRequest.getUsername());
            throw new BusinessException("用户名或密码错误");
        } catch (Exception e) {
            log.error("登录失败: {}", e.getMessage(), e);
            throw new BusinessException("登录失败: " + e.getMessage());
        }
    }

    @Override
    public User getUserByUsername(String username) {
        return userRepository.findActiveUserByUsername(username)
                .orElseThrow(() -> new BusinessException("用户不存在或已被禁用"));
    }

    @Override
    @Transactional
    public void updateLoginInfo(Long userId, String ip) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        user.setLastLoginTime(LocalDateTime.now());
        user.setLastLoginIp(ip);

        userRepository.save(user);
    }

    @Override
    @Transactional
    public UserResponse addUser(AddUserRequest request) {
        // 检查用户名是否已存在
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("用户名已存在");
        }

        // 创建用户
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(defaultPassword))  // 使用默认密码
                .nickname(request.getNickname())
                .userType(request.getUserType())
                .email(null)  // 邮箱默认为空
                .phone(null)  // 手机号默认为空
                .status(1)    // 状态默认为正常
                .deleted(false)
                .build();

        User savedUser = userRepository.save(user);
        log.info("用户添加成功，用户名: {}, 用户类型: {}, 默认密码: {}",
                savedUser.getUsername(), savedUser.getUserType(), defaultPassword);

        return convertToResponse(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getUserList(Pageable pageable, String username, String userType, Integer status) {
        Page<User> userPage;

        // 根据条件进行查询
        if (StringUtils.hasText(username) || StringUtils.hasText(userType) || status != null) {
            // 构建查询条件
            userPage = userRepository.findUsersByConditions(
                    StringUtils.hasText(username) ? username : null,
                    userType,
                    status,
                    pageable
            );
        } else {
            // 获取所有用户
            userPage = userRepository.findAllActiveUsers(pageable);
        }

        return userPage.map(this::convertToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> searchUsers(Pageable pageable, String keyword, String userType, Integer status) {
        log.info("搜索用户，关键字: {}, 用户类型: {}, 状态: {}", keyword, userType, status);

        // 处理参数，确保空字符串转为null
        String keywordParam = StringUtils.hasText(keyword) ? keyword.trim() : null;
        String userTypeParam = StringUtils.hasText(userType) ? userType.trim() : null;
        Integer statusParam = (status != null && (status == 0 || status == 1)) ? status : null;

        Page<User> userPage = userRepository.searchUsers(
                keywordParam,
                userTypeParam,
                statusParam,
                pageable
        );

        log.info("搜索到 {} 条用户记录", userPage.getTotalElements());
        return userPage.map(this::convertToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> searchUsersList(String keyword, String userType, Integer status) {
        // 处理参数，确保空字符串转为null
        String keywordParam = StringUtils.hasText(keyword) ? keyword.trim() : null;
        String userTypeParam = StringUtils.hasText(userType) ? userType.trim() : null;
        Integer statusParam = (status != null && (status == 0 || status == 1)) ? status : null;

        // 调用新增的搜索方法（不分页）
        List<User> users = userRepository.searchUsersList(
                keywordParam,
                userTypeParam,
                statusParam
        );

        return users.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserDetail(Long userId) {
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        return convertToResponse(user);
    }

    @Override
    @Transactional
    public void updateUserStatus(Long userId, Integer status) {
        if (status != 0 && status != 1) {
            throw new BusinessException("状态值只能是0或1");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        user.setStatus(status);
        user.setUpdateTime(LocalDateTime.now());
        userRepository.save(user);
        log.info("更新用户状态，用户ID: {}, 新状态: {}", userId, status);
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        // 查找用户
        User user = userRepository.findByIdAndDeletedFalse(request.getUserId())
                .orElseThrow(() -> new BusinessException("用户不存在"));

        // 获取当前管理员信息（用于日志）
        String currentUsername = getCurrentUsername();

        // 重置密码
        String encodedPassword = passwordEncoder.encode(defaultPassword);
        user.setPassword(encodedPassword);
        user.setUpdateTime(LocalDateTime.now());

        userRepository.save(user);

        log.info("管理员 {} 重置了用户 {} 的密码，新密码为默认密码: {}",
                currentUsername, user.getUsername(), defaultPassword);
    }

    @Override
    @Transactional
    public void batchResetPassword(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new BusinessException("用户ID列表不能为空");
        }

        // 获取当前管理员信息
        String currentUsername = getCurrentUsername();

        int successCount = 0;
        int failCount = 0;

        for (Long userId : userIds) {
            try {
                User user = userRepository.findByIdAndDeletedFalse(userId)
                        .orElseThrow(() -> new BusinessException("用户不存在: " + userId));

                String encodedPassword = passwordEncoder.encode(defaultPassword);
                user.setPassword(encodedPassword);
                user.setUpdateTime(LocalDateTime.now());

                userRepository.save(user);
                successCount++;

                log.info("管理员 {} 批量重置了用户 {} 的密码",
                        currentUsername, user.getUsername());

            } catch (Exception e) {
                log.error("重置用户 {} 密码失败: {}", userId, e.getMessage());
                failCount++;
            }
        }

        log.info("批量重置密码完成，成功: {}，失败: {}", successCount, failCount);

        if (failCount > 0) {
            throw new BusinessException("批量重置密码完成，成功" + successCount + "个，失败" + failCount + "个");
        }
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        // 逻辑删除
        user.setDeleted(true);
        user.setUpdateTime(LocalDateTime.now());

        userRepository.save(user);
        log.info("用户 {} 已被删除", user.getUsername());
    }

    @Override
    @Transactional
    public UserResponse updateUser(Long userId, AddUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        // 更新用户信息
        user.setNickname(request.getNickname());
        user.setUserType(request.getUserType());
        user.setUpdateTime(LocalDateTime.now());

        // 注意：这里不更新用户名，因为用户名通常不允许修改
        // 邮箱和手机号可以在后续添加字段后更新

        User savedUser = userRepository.save(user);
        return convertToResponse(savedUser);
    }

    @Override
    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        // 验证两次输入的新密码是否一致
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("两次输入的密码不一致");
        }

        // 获取用户
        User user = getUserByUsername(username);

        // 验证旧密码
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException("旧密码错误");
        }

        // 检查新密码是否与旧密码相同
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BusinessException("新密码不能与旧密码相同");
        }

        // 更新密码
        String encodedPassword = passwordEncoder.encode(request.getNewPassword());
        user.setPassword(encodedPassword);
        user.setUpdateTime(LocalDateTime.now());

        userRepository.save(user);

        log.info("用户 {} 修改密码成功", username);
    }

    @Override
    @Transactional
    public void adminChangePassword(AdminChangePasswordRequest request) {
        // 验证两次输入的新密码是否一致
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("两次输入的密码不一致");
        }

        // 获取用户
        User user = userRepository.findByIdAndDeletedFalse(request.getUserId())
                .orElseThrow(() -> new BusinessException("用户不存在"));

        // 获取当前管理员信息
        String currentUsername = getCurrentUsername();

        // 检查新密码是否与当前密码相同
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BusinessException("新密码不能与当前密码相同");
        }

        // 更新密码
        String encodedPassword = passwordEncoder.encode(request.getNewPassword());
        user.setPassword(encodedPassword);
        user.setUpdateTime(LocalDateTime.now());

        userRepository.save(user);

        log.info("管理员 {} 修改了用户 {} 的密码",
                currentUsername, user.getUsername());
    }

    /**
     * 获取当前登录用户的用户名
     */
    private String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                return authentication.getName();
            }
        } catch (Exception e) {
            log.warn("获取当前用户信息失败: {}", e.getMessage());
        }
        return "未知管理员";
    }

    /**
     * 将User实体转换为UserResponse DTO
     */
    private UserResponse convertToResponse(User user) {
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