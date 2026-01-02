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
@Table(name = "ledger_data_detail")
@Data
@EntityListeners(AuditingEntityListener.class)  // 确保有这个注解
public class LedgerDataDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_id", nullable = false)
    private Long dataId;  // 台账数据ID

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;  // 字段名称

    @Column(name = "field_value", columnDefinition = "TEXT")
    private String fieldValue;  // 字段值

    @Column(name = "original_value", columnDefinition = "TEXT")
    private String originalValue;  // 原始值（未转换）

    @Column(name = "is_empty", nullable = false)
    private Boolean isEmpty = false;  // 是否为空

    @Column(name = "is_valid", nullable = false)
    private Boolean isValid = true;  // 是否有效

    @Column(name = "validation_message", length = 500)
    private String validationMessage;  // 验证信息

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;  // 排序序号

    @CreatedDate
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @LastModifiedDate
    @Column(name = "updated_time", nullable = false)
    private LocalDateTime updatedTime;
}