package com.example.ledger.dto.request;

/**
*@author 霜月
*@create 2025/12/26 20:33
*/

import lombok.Data;
import jakarta.validation.constraints.NotNull;

@Data
public class RequiredFieldConfigRequest {
    private Long id;

    @NotNull(message = "模板ID不能为空")
    private Long templateId;

    @NotNull(message = "字段名称不能为空")
    private String fieldName;

    @NotNull(message = "是否必填不能为空")
    private Boolean isRequired = true;

    private String requiredMessage;
    private String validationRule;
    private String configType = "SYSTEM";
    private Long userId;
}