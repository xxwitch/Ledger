package com.example.ledger.dto.request;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author 霜月
 * @create 2025/12/22 09:42
 */
@Data
public class LedgerDataDynamicQueryRequest {
    private String unitName;
    private Long templateId;
    private String dataStatus;
    private String validationStatus;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private Integer year;
    private Integer month;
    private String yearMonth;

    // 分页和排序 - 修改默认排序
    private Integer page = 1;
    private Integer size = 10;
    private String sortField = "uploadId"; // 默认按上传ID排序
    private String sortOrder = "ASC";      // 默认升序

    // 权限控制
    private Boolean viewAll = false;
    private Boolean viewOwnOnly = false;
}