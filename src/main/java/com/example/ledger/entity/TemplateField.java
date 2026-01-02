package com.example.ledger.entity;

/**
 * @author 霜月
 * @create 2025/12/20 22:26
 */

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;


@Entity
@Table(name = "template_field",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_template_field_column",
                columnNames = {"template_id", "excel_column"}
        ))
@Data
@EntityListeners(AuditingEntityListener.class)
public class TemplateField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;  // 字段名称（英文/编码）

    @Column(name = "field_label", nullable = false, length = 200)
    private String fieldLabel;  // 字段标签（中文显示名）

    @Column(name = "field_type", nullable = false, length = 50)
    private String fieldType = "STRING";  // 字段类型：STRING-字符串，NUMBER-数字，DATE-日期，BOOLEAN-布尔

    @Column(name = "data_type", length = 50)
    private String dataType;  // 数据类型：如INTEGER, DECIMAL, VARCHAR, DATETIME等

    @Column(name = "field_length")
    private Integer fieldLength;  // 字段长度

    @Column(name = "decimal_places")
    private Integer decimalPlaces;  // 小数位数（数字类型）

    @Column(name = "is_required", nullable = false)
    private Boolean isRequired = false;  // 是否必填

    @Column(name = "default_value", length = 500)
    private String defaultValue;  // 默认值

    @Column(name = "validation_rule", length = 500)
    private String validationRule;  // 验证规则（正则表达式）

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;  // 排序序号

    @Column(name = "excel_column", nullable = false, length = 10)
    private String excelColumn;  // Excel列标识（A, B, C...）

    @Column(name = "excel_header", nullable = false, length = 200)
    private String excelHeader;  // Excel表头名称

    @Column(length = 500)
    private String description;  // 字段描述

    @CreatedDate
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @LastModifiedDate
    @Column(name = "updated_time", nullable = false)
    private LocalDateTime updatedTime;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;
}