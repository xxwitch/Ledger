package com.example.ledger.dto.response;

/**
 * @author 霜月
 * @create 2025/12/21 10:54
 */

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerDataPageResponse {
    private List<LedgerDataResponse> data;           // 当前页数据
    private Integer currentPage;                     // 当前页码
    private Integer pageSize;                        // 每页大小
    private Long totalElements;                      // 总记录数
    private Integer totalPages;                      // 总页数
    private Boolean hasPrevious;                     // 是否有上一页
    private Boolean hasNext;                         // 是否有下一页
    private List<String> fieldNames;                 // 字段名称列表
    private List<String> unitNames;                  // 单位名称列表
    private QueryStats stats;                        // 查询统计

    @Data
    @Builder
    public static class QueryStats {
        private Long totalRecords;                   // 总记录数
        private Long activeRecords;                  // 有效记录数
        private Long invalidRecords;                 // 无效记录数
        private Map<String, Long> unitDistribution;  // 单位分布
        private Map<String, Long> statusDistribution; // 状态分布
    }
}