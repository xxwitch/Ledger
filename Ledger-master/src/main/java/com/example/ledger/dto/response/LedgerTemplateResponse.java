package com.example.ledger.dto.response;

/**
 * @author 霜月
 * @create 2025/12/20 22:29
 */

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LedgerTemplateResponse {

    private Long id;
    private String unitName;
    private String templateName;
    private String description;
    private String version;
    private Integer status;
    private Long createdBy;

    private String templateFilePath;
    private String templateFileName;
    private Boolean hasTemplateFile;
    private Integer headerRowCount;
    private Integer dataStartRow;
    private Integer columnCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedTime;

    private Integer fieldCount;  // 字段数量
    private Integer dataCount;   // 数据量
}