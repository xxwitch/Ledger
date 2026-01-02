package com.example.ledger.entity;

/**
 * @author 霜月
 * @create 2025/12/21 16:39
 */

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

@Entity
@Table(name = "ledger_edit_history")
@Data
public class LedgerEditHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_id", nullable = false)
    private Long dataId;  // 台账数据ID

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;  // 字段名称

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;   // 原值

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;   // 新值

    @Column(name = "edit_type", nullable = false, length = 20)
    private String editType = "UPDATE";  // 编辑类型：CREATE, UPDATE, DELETE

    @Column(name = "edit_reason", length = 500)
    private String editReason;  // 编辑原因

    @Column(name = "edited_by", nullable = false)
    private Long editedBy;  // 编辑人ID

    @Column(name = "edited_by_name", length = 50)
    private String editedByName;  // 编辑人姓名

    @Column(name = "validation_result", length = 20)
    private String validationResult;  // 验证结果：PASS, FAIL

    @Column(name = "validation_message", length = 500)
    private String validationMessage;  // 验证信息

    @Column(name = "ip_address", length = 50)
    private String ipAddress;  // IP地址

    @CreatedDate
    @Column(name = "edit_time", nullable = false, updatable = false)
    private LocalDateTime editTime;  // 编辑时间

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;
}