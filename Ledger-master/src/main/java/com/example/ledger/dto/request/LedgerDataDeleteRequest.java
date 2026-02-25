package com.example.ledger.dto.request;

/**
 * @author 霜月
 * @create 2025/12/21 16:38
 */

import lombok.Data;
import java.util.List;

@Data
public class LedgerDataDeleteRequest {
    private List<Long> dataIds;              // 要删除的数据ID列表
    private String deleteReason;             // 删除原因
    private Boolean permanentDelete = false; // 是否永久删除（false=逻辑删除，true=物理删除）
    private Boolean forceDelete = false;     // 是否强制删除（跳过验证）
}