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
@Table(name = "ledger_upload")
@Data
@EntityListeners(AuditingEntityListener.class)
public class LedgerUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "upload_no", nullable = false, length = 50, unique = true)
    private String uploadNo;  // 上传批次号，格式：UPLOAD_年月日_序号

    @Column(name = "user_id", nullable = false)
    private Long userId;  // 上传用户ID

    @Column(name = "unit_name", nullable = false, length = 100)
    private String unitName;  // 单位名称

    @Column(name = "template_id", nullable = false)
    private Long templateId;  // 模板ID

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;  // 原始文件名

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;  // 服务器存储路径

    @Column(name = "file_size", nullable = false)
    private Long fileSize;  // 文件大小(字节)

    @Column(name = "total_rows", nullable = false)
    private Integer totalRows = 0;  // 总行数

    @Column(name = "success_rows", nullable = false)
    private Integer successRows = 0;  // 成功导入行数

    @Column(name = "failed_rows", nullable = false)
    private Integer failedRows = 0;  // 失败行数

    @Column(name = "import_status", nullable = false, length = 20)
    private String importStatus = "PENDING";  // 导入状态：PENDING-待处理, PROCESSING-处理中, SUCCESS-成功, PARTIAL_SUCCESS-部分成功, FAILED-失败

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;  // 错误信息

    @Column(name = "upload_ip", length = 50)
    private String uploadIp;  // 上传IP地址

    @CreatedDate
    @Column(name = "upload_time", nullable = false, updatable = false)
    private LocalDateTime uploadTime;  // 上传时间

    @Column(name = "completed_time")
    private LocalDateTime completedTime;  // 完成时间

    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    @Column(name = "process_mode", nullable = false, length = 20)
    private String processMode = "REPLACE";  // 处理模式

    @Column(name = "keep_historical", nullable = false)
    private Boolean keepHistorical = true;   // 是否保留历史数据

    @Column(name = "replace_count", nullable = false)
    private Integer replaceCount = 0;        // 替换次数
}
