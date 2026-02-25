package com.example.ledger.dto.request;

/**
 * @author 霜月
 * @create 2025/12/21 01:26
 */

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class LedgerUploadRequest {
    private String unitName;  // 单位名称
    private MultipartFile file;  // Excel文件
    private String description;  // 上传描述（可选）
    private String processMode = "REPLACE";  // REPLACE-全量替换, UPDATE-智能更新, INSERT-仅新增
    private Boolean keepHistorical = true;
    private Boolean validateRequiredFields = true;  // 是否验证必填项
    private Boolean skipInvalidRows = false;  // 是否跳过无效行
}