package com.example.ledger.dto.request;

/**
 * @author 霜月
 * @create 2025/12/25 21:53
 */

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 台账多字段查询请求
 */
@Data
public class LedgerDataMultiFieldQueryRequest {
    // 基本查询条件
    private String unitName;           // 单位名称
    private Long templateId;          // 模板ID
    private String dataStatus;        // 数据状态
    private String validationStatus;  // 验证状态

    // 台账特定字段查询条件
    private String projectName;       // 项目名称
    private String materialGroup9;    // 物料组（9位码）
    private String erpCode11;         // ERP系统编码（11位码）
    private String erpMaterialDesc;   // 大ERP物资描述（名称+规格型号）
    private String purchaseAgent;     // 采购业务经办人员名字
    private String demandPlanNo;      // 需求计划编号
    private String purchasePlanNo;    // 采购计划单号
    private String purchaseSchemeNo;  // 采购方案号
    private String contractNo;        // （框架、一单一采）合同号
    private String reportNo;          // 报审序号
    private String orderNo;           // （即买即结、框架下的订单）订单号
    private String supplierCode;      // 供应商编码
    private String supplierName;      // 供应商名称
    private String purchaseProgress;  // 当前采购进度

    // 模糊查询控制
    private Boolean fuzzySearch = true;  // 是否模糊查询

    // 新增：按年/月查询字段
    private Integer year;
    private Integer month;
    private String yearMonth;

    // 时间范围
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    // 分页和排序
    private Integer page = 1;
    private Integer size = 10;
    private String sortField = "createdTime";
    private String sortOrder = "DESC";

    // 权限控制
    private Boolean viewAll = false;
    private Boolean viewOwnOnly = false;
}