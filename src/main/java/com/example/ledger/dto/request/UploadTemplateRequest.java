package com.example.ledger.dto.request;

/**
 * @author 霜月
 * @create 2025/12/20 22:25
 */

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class UploadTemplateRequest {
    private Long templateId;
    private MultipartFile file;
    private String description;
}