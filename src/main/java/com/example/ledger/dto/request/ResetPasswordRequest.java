package com.example.ledger.dto.request;

/**
 * @author 霜月
 * @create 2025/12/11 15:28
 */

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 重置密码请求DTO
 */
@Data
public class ResetPasswordRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;
}