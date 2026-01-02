package com.example.ledger.entity;

/**
 * @author 霜月
 * @create 2025/12/21 00:58
 */
import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "required_field_config")
@Data
@EntityListeners(AuditingEntityListener.class)
public class RequiredFieldConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false)
    private Long templateId;  // 模板ID

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;  // 字段名称

    @Column(name = "is_required", nullable = false)
    private Boolean isRequired = true;  // 是否必填

    @Column(name = "required_message", length = 200)
    private String requiredMessage;  // 必填提示信息

    @Column(name = "validation_rule", length = 500)
    private String validationRule;  // 验证规则

    @Column(name = "config_type", nullable = false, length = 20)
    private String configType = "SYSTEM";  // 配置类型：SYSTEM-系统默认, USER-用户自定义

    @Column(name = "user_id")
    private Long userId;  // 配置用户ID（如果是用户自定义）

    @CreatedDate
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @LastModifiedDate
    @Column(name = "updated_time", nullable = false)
    private LocalDateTime updatedTime;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;
}