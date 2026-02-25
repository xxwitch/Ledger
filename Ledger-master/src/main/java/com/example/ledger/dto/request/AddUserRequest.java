package com.example.ledger.dto.request;

/**
 * @author 霜月
 * @create 2025/12/11 14:36
 * 添加用户请求DTO
 */

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 添加用户请求DTO
 */
@Data
public class AddUserRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度必须在3-50个字符之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String username;

    @NotBlank(message = "昵称不能为空")
    @Size(min = 2, max = 50, message = "昵称长度必须在2-50个字符之间")
    private String nickname;

    @NotBlank(message = "用户类型不能为空")
    @Pattern(regexp = "^(ADMIN|PRODUCTION)$", message = "用户类型只能是ADMIN或PRODUCTION")
    private String userType;
}