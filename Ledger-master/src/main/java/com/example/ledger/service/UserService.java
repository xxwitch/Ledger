package com.example.ledger.service;

/**
 * @author 霜月
 * @create 2025/12/9 23:15
 */

import com.example.ledger.dto.request.*;
import com.example.ledger.dto.response.LoginResponse;
import com.example.ledger.dto.response.UserResponse;
import com.example.ledger.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UserService {

    /**
     * 用户登录
     */
    LoginResponse login(LoginRequest loginRequest, String ip);

    /**
     * 根据用户名获取用户
     */
    User getUserByUsername(String username);

    /**
     * 更新用户最后登录信息
     */
    void updateLoginInfo(Long userId, String ip);

    /**
     * 添加用户（管理员功能）
     */
    UserResponse addUser(AddUserRequest request);

    /**
     * 获取用户列表（管理员功能）- 支持分页和过滤
     */
    Page<UserResponse> getUserList(Pageable pageable, String username, String userType, Integer status);

    /**
     * 搜索用户（新增功能）- 根据用户名、昵称、状态查询
     */
    Page<UserResponse> searchUsers(Pageable pageable, String username, String nickname, Integer status);

    /**
     * 搜索用户列表（不分页）
     */
    List<UserResponse> searchUsersList(String username, String nickname, Integer status);

    /**
     * 获取用户详情
     */
    UserResponse getUserDetail(Long userId);

    /**
     * 更新用户状态
     */
    void updateUserStatus(Long userId, Integer status);

    /**
     * 重置用户密码（管理员功能）
     */
    void resetPassword(ResetPasswordRequest request);

    /**
     * 批量重置用户密码（管理员功能）
     */
    void batchResetPassword(List<Long> userIds);

    /**
     * 删除用户（逻辑删除）
     */
    void deleteUser(Long userId);

    /**
     * 更新用户信息
     */
    UserResponse updateUser(Long userId, AddUserRequest request);

    /**
     * 修改密码（用户自己修改）
     * @param username 用户名
     * @param request 修改密码请求
     */
    void changePassword(String username, ChangePasswordRequest request);

    /**
     * 管理员修改用户密码
     * @param request 管理员修改密码请求
     */
    void adminChangePassword(AdminChangePasswordRequest request);
}