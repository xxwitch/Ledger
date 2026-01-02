package com.example.ledger.service;

/**
 * @author 霜月
 * @create 2025/12/20 21:51
 */
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class FileStorageService {

    @Value("${app.file.template-dir}")
    private String templateDir;

    @Value("${app.file.upload-dir}")
    private String uploadDir;

    public String storeTemplateFile(MultipartFile file, String unitName) throws IOException {
        // 创建目录
        Path templatePath = Paths.get(templateDir);
        if (!Files.exists(templatePath)) {
            Files.createDirectories(templatePath);
        }

        // 生成文件名
        String originalFileName = file.getOriginalFilename();
        String fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
        String fileName = unitName + "_template_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) +
                fileExtension;

        // 存储文件
        Path targetLocation = templatePath.resolve(fileName);
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

        return targetLocation.toString();
    }

    public String storeUploadFile(MultipartFile file, String uploadNo) throws IOException {
        // 创建上传目录 - 添加这个方法
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // 生成文件名
        String originalFileName = file.getOriginalFilename();
        String fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
        String fileName = uploadNo + fileExtension;

        // 存储文件
        Path targetLocation = uploadPath.resolve(fileName);
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

        return targetLocation.toString();
    }

    public File getTemplateFile(String filePath) {
        return new File(filePath);
    }

    public boolean templateFileExists(String filePath) {
        if (filePath == null) {
            return false;
        }
        File file = new File(filePath);
        return file.exists() && file.isFile();
    }
}