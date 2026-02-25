package com.example.ledger.entity;

/**
 * @author 霜月
 * @create 2025/12/20 22:27
 */

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "ledger_data")
@Data
@EntityListeners(AuditingEntityListener.class)
public class LedgerData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "upload_id", nullable = false)
    private Long uploadId;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "unit_name", nullable = false, length = 100)
    private String unitName;

    // 将 row_number 改为 row_num（避开保留字）
    @Column(name = "row_num", nullable = false)
    private Integer rowNumber;  // Java字段名不变，只改数据库列名

    @Column(name = "data_status", nullable = false, length = 20)
    private String dataStatus = "ACTIVE";

    @Column(name = "validation_status", nullable = false, length = 20)
    private String validationStatus = "PENDING";

    @Column(name = "validation_errors", columnDefinition = "JSON")
    private String validationErrors;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @CreatedDate
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @Column(name = "updated_by")
    private Long updatedBy;

    @LastModifiedDate
    @Column(name = "updated_time", nullable = false)
    private LocalDateTime updatedTime;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    @Column(name = "is_latest", nullable = false)
    private Boolean isLatest = true;  // 是否为最新数据

    @Column(name = "upload_batch", nullable = false)
    private Integer uploadBatch = 1;  // 上传批次

    @Column(name = "historical_data_id")
    private Long historicalDataId;    // 历史数据ID

    @Column(name = "data_version", nullable = false)
    private Integer dataVersion = 1;  // 数据版本号
}
