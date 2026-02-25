package com.example.ledger.dto.request;

/**
 * @author 霜月
 * @create 2025/12/21 09:54
 */

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LedgerDataQueryRequest {
    // 基本查询条件
    private String unitName;           // 单位名称
    private Long templateId;          // 模板ID
    private Long uploadId;            // 上传批次ID
    private Long userId;              // 上传用户ID

    // 数据状态
    private String dataStatus;        // 数据状态：ACTIVE, INACTIVE, DELETED
    private String validationStatus;  // 验证状态：PENDING, VALID, INVALID

    // 新增：按年/月查询字段
    private Integer year;           // 年份，如：2024
    private Integer month;          // 月份，如：12 (1-12)
    private String yearMonth;

    // 时间范围
    private LocalDateTime startTime;  // 创建时间起始
    private LocalDateTime endTime;    // 创建时间结束

    // 字段条件
    private String fieldName;         // 字段名称
    private String fieldValue;        // 字段值（模糊查询）
    private String fieldValueExact;   // 字段值（精确查询）

    // 必填项相关
    private Boolean isEmpty;          // 是否为空
    private Boolean isValid;          // 是否有效

    // 分页和排序 - 修改默认排序为按上传ID和行号
    private Integer page = 1;         // 页码，默认1
    private Integer size = 10;        // 每页大小，默认10
    private String sortField = "uploadId"; // 默认按上传ID排序
    private String sortOrder = "ASC";        // 默认升序

    // 权限控制
    private Boolean viewAll = false;  // 是否查看所有数据（管理员权限）
    private Boolean viewOwnOnly = false; // 是否只看自己的数据
}