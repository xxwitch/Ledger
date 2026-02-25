package com.example.ledger.dto.response;

/**
 * @author 霜月
 * @create 2025/12/20 22:29
 */

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LedgerUploadResponse {

    private Long id;
    private String uploadNo;
    private Long userId;
    private String unitName;
    private Long templateId;
    private String fileName;
    private Long fileSize;
    private Integer totalRows;
    private Integer successRows;
    private Integer failedRows;
    private String importStatus;
    private String errorMessage;
    private String uploadIp;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime uploadTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedTime;

    private String uploadUserName;  // 上传用户名称
    private String templateName;    // 模板名称

    // 新增字段：用于返回消息提示
    private String message;
    private Integer oldDataCount;   // 旧数据数量
    private Boolean isCoverageUpdate; // 是否执行覆盖更新

    // 构造方法（可选）
    public LedgerUploadResponse() {
    }

    public LedgerUploadResponse(Long id, String uploadNo, String unitName, String fileName) {
        this.id = id;
        this.uploadNo = uploadNo;
        this.unitName = unitName;
        this.fileName = fileName;
    }

    // 静态工厂方法
    public static LedgerUploadResponse createWithMessage(Long id, String uploadNo, String unitName, String fileName, String message) {
        LedgerUploadResponse response = new LedgerUploadResponse(id, uploadNo, unitName, fileName);
        response.setMessage(message);
        return response;
    }

    public static LedgerUploadResponse createCoverageUpdateResponse(Long id, String uploadNo, String unitName, String fileName,
                                                                    Integer oldDataCount) {
        LedgerUploadResponse response = new LedgerUploadResponse(id, uploadNo, unitName, fileName);
        response.setOldDataCount(oldDataCount);
        response.setIsCoverageUpdate(true);
        response.setMessage("检测到 " + oldDataCount + " 条旧数据，将执行覆盖更新");
        return response;
    }
}