package com.example.ledger.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * @author 霜月
 * @create 2025/12/22 09:41
 */
// LedgerDataDynamicResponse.java - 专门用于动态列展示
@Data
@Builder
public class LedgerDataDynamicResponse {
    private Long id;
    private String uploadNo;
    private Long templateId;
    private String templateName;
    private String unitName;
    private Integer rowNumber;
    private String dataStatus;
    private String validationStatus;
    private String createdByName;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    // 动态字段数据
    private Map<String, Object> fieldData; // 字段名 -> 字段值

    // 模板字段信息（用于前端显示列）
    private List<TemplateFieldInfo> templateFields;

    // 统计信息
    private Integer emptyFieldCount;
    private Integer invalidFieldCount;

    @Data
    @Builder
    public static class TemplateFieldInfo {
        private String fieldName;
        private String fieldLabel;
        private String fieldType;
        private String excelColumn;
        private Integer sortOrder;
    }
}