package com.example.ledger.dto.request;

/**
 * @author 霜月
 * @create 2025/12/21 16:38
 */

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class LedgerDataBatchEditRequest {
    private List<Long> dataIds;              // 台账数据ID列表
    private Map<String, String> fieldValues; // 要更新的字段
    private String editReason;               // 编辑原因
    private Boolean validate = true;         // 是否验证
}