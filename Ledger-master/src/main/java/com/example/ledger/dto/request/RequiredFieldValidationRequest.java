package com.example.ledger.dto.request;

/**
 * @author 霜月
 * @create 2025/12/26 20:34
 */

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@Data
public class RequiredFieldValidationRequest {
    @NotNull(message = "模板ID不能为空")
    private Long templateId;

    @NotNull(message = "字段值不能为空")
    private Map<String, String> fieldValues;

    private Integer rowNumber;
    private Boolean strictMode = true;
}