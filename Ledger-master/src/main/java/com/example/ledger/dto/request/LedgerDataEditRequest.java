package com.example.ledger.dto.request;

/**
 * @author 霜月
 * @create 2025/12/21 16:31
 */

import lombok.Data;
import java.util.Map;

@Data
public class LedgerDataEditRequest {
    private Long dataId;                     // 台账数据ID
    private Map<String, String> fieldValues; // 字段名 -> 新值
    private String editReason;               // 编辑原因
    private Boolean validate = true;         // 是否验证
}