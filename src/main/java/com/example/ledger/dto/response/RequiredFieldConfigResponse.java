package com.example.ledger.dto.response;

/**
*@author 霜月
*@create 2025/12/26 20:33
*/

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class RequiredFieldConfigResponse {
    private Long id;
    private Long templateId;
    private String unitName;
    private String fieldName;
    private String fieldLabel;
    private Boolean isRequired;
    private String requiredMessage;
    private String validationRule;
    private String configType;
    private Long userId;
    private String userName;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}