package com.example.ledger.dto.request;

/**
 * @author 霜月
 * @create 2025/12/20 22:25
 */

import lombok.Data;
import java.util.List;

@Data
public class ExportLedgerRequest {
    private Long templateId;
    private List<Long> dataIds;
    private String unitName; // 可选，如果按单位导出
    private Boolean includeHeader = true; // 是否包含表头
    private String exportType = "SELECTED"; // ALL: 所有数据, SELECTED: 选择的数据
}