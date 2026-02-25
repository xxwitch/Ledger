package com.example.ledger.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * @author 霜月
 * @create 2025/12/22 09:42
 */
// LedgerDataDynamicPageResponse.java - 动态列分页响应
@Data
@Builder
public class LedgerDataDynamicPageResponse {
    private List<LedgerDataDynamicResponse> data;
    private Integer currentPage;
    private Integer pageSize;
    private Long totalElements;
    private Integer totalPages;
    private Boolean hasPrevious;
    private Boolean hasNext;

    // 额外信息：当前查询的模板字段定义
    private List<LedgerDataDynamicResponse.TemplateFieldInfo> currentTemplateFields;
}