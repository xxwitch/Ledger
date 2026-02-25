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
@Table(name = "template_style")
@Data
@EntityListeners(AuditingEntityListener.class)  // 添加这行！
public class TemplateStyle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "row_index", nullable = false)
    private Integer rowIndex;

    @Column(name = "column_index", nullable = false)
    private Integer columnIndex;

    @Column(name = "cell_value", columnDefinition = "TEXT")
    private String cellValue;

    @Column(name = "is_merged")
    private Boolean isMerged = false;

    @Column(name = "merge_start_row")
    private Integer mergeStartRow;

    @Column(name = "merge_end_row")
    private Integer mergeEndRow;

    @Column(name = "merge_start_col")
    private Integer mergeStartCol;

    @Column(name = "merge_end_col")
    private Integer mergeEndCol;

    @Column(name = "font_name", length = 50)
    private String fontName;

    @Column(name = "font_bold")
    private Boolean fontBold = false;

    @Column(name = "font_size")
    private Integer fontSize = 11;

    @Column(name = "font_color", length = 20)
    private String fontColor = "000000";

    @Column(name = "alignment", length = 20)
    private String alignment = "LEFT";

    @Column(name = "vertical_alignment", length = 20)
    private String verticalAlignment = "CENTER";

    @Column(name = "background_color", length = 20)
    private String backgroundColor;

    @Column(name = "border_top")
    private Boolean borderTop = false;

    @Column(name = "border_bottom")
    private Boolean borderBottom = false;

    @Column(name = "border_left")
    private Boolean borderLeft = false;

    @Column(name = "border_right")
    private Boolean borderRight = false;

    @Column(name = "cell_format", length = 50)
    private String cellFormat;

    @CreatedDate
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @LastModifiedDate
    @Column(name = "updated_time", nullable = false)
    private LocalDateTime updatedTime;
}