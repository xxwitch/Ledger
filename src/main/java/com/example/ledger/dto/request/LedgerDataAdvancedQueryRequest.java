package com.example.ledger.dto.request;

/**
 * @author 霜月
 * @create 2025/12/21 10:08
 */

import lombok.Data;
import java.util.Map;

@Data
public class LedgerDataAdvancedQueryRequest {
    private String unitName;           // 单位名称
    private Long templateId;          // 模板ID
    private Map<String, Object> conditions; // 字段条件映射
    private Boolean matchAll = true;  // true: 所有条件必须满足, false: 满足任一条件
    private Integer page = 1;
    private Integer size = 10;

    // 时间范围
    private String timeField = "createdTime"; // 时间字段：createdTime, updatedTime
    private String startTime;
    private String endTime;
}