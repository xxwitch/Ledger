package com.example.ledger.entity;

/**
 * @author 霜月
 * @create 2025/12/20 20:42
 */
import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "ledger_template")
@Data
@EntityListeners(AuditingEntityListener.class)
public class LedgerTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "unit_name", nullable = false, length = 100)
    private String unitName;

    @Column(name = "template_name", nullable = false, length = 100)
    private String templateName;

    private String description;

    @Column(length = 20)
    private String version = "1.0";

    private Integer status = 1;

    @Column(name = "created_by")
    private Long createdBy;

    @CreatedDate
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @LastModifiedDate
    @Column(name = "updated_time", nullable = false)
    private LocalDateTime updatedTime;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    // 新增字段
    @Column(name = "template_file_path", length = 500)
    private String templateFilePath;

    @Column(name = "template_file_name", length = 255)
    private String templateFileName;

    @Column(name = "header_row_count", nullable = false)
    private Integer headerRowCount = 4;

    @Column(name = "data_start_row", nullable = false)
    private Integer dataStartRow = 5;

    @Column(name = "column_count")
    private Integer columnCount;

    @Column(name = "has_template_file")
    private Boolean hasTemplateFile = false;
}