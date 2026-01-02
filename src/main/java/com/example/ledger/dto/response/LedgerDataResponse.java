package com.example.ledger.dto.response;

/**
 * @author 霜月
 * @create 2025/12/21 10:32
 */

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class LedgerDataResponse {
    private Long id;
    private Long uploadId;
    private String uploadNo;      // 上传批次号
    private Long templateId;
    private String templateName;  // 模板名称
    private String unitName;
    private Integer rowNumber;    // 行号
    private String dataStatus;    // 数据状态
    private String validationStatus; // 验证状态
    private String validationErrors; // 验证错误信息
    private Long createdBy;       // 创建人ID
    private String createdByName; // 创建人姓名

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedTime;

    // 字段数据
    private Map<String, Object> fieldData; // 字段名 -> 字段值
    private Map<String, Boolean> fieldStatus; // 字段验证状态
    private Map<String, String> fieldValidation; // 字段验证信息

    // 统计信息
    private Integer emptyFieldCount;   // 空字段数量
    private Integer invalidFieldCount; // 无效字段数量
    private Integer totalFieldCount;   // 总字段数量
}