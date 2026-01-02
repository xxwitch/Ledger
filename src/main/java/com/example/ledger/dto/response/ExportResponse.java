package com.example.ledger.dto.response;

/**
 * @author 霜月
 * @create 2025/12/20 22:28
 */

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExportResponse {

    private String fileName;
    private Long fileSize;
    private String downloadUrl;
    private Integer rowCount;
    private String unitName;
    private String templateName;
    private String exportTime;
    private String message;

    public static ExportResponse success(String fileName, Long fileSize, Integer rowCount,
                                         String unitName, String templateName) {
        ExportResponse response = new ExportResponse();
        response.setFileName(fileName);
        response.setFileSize(fileSize);
        response.setRowCount(rowCount);
        response.setUnitName(unitName);
        response.setTemplateName(templateName);
        response.setMessage("导出成功");
        return response;
    }

    public static ExportResponse error(String message) {
        ExportResponse response = new ExportResponse();
        response.setMessage("导出失败: " + message);
        return response;
    }
}