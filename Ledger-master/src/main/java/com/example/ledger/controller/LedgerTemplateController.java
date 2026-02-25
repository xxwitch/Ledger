package com.example.ledger.controller;

/**
 * @author 霜月
 * @create 2025/12/20 22:30
 */

import com.example.ledger.dto.request.UploadTemplateRequest;
import com.example.ledger.dto.response.ApiResponse;
import com.example.ledger.dto.response.LedgerTemplateResponse;
import com.example.ledger.entity.LedgerTemplate;
import com.example.ledger.service.FileStorageService;
import com.example.ledger.service.LedgerTemplateService;
import com.example.ledger.service.TemplateUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/template")
@RequiredArgsConstructor
@Slf4j
public class LedgerTemplateController {

    private final LedgerTemplateService ledgerTemplateService;
    private final TemplateUploadService templateUploadService;
    private final FileStorageService fileStorageService;

    /**
     * 获取模板列表
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRODUCTION')")
    public ApiResponse<List<LedgerTemplateResponse>> getTemplateList() {
        try {
            List<LedgerTemplateResponse> templates = ledgerTemplateService.getAllTemplates();
            return ApiResponse.success("获取成功", templates);
        } catch (Exception e) {
            log.error("获取模板列表失败", e);
            return ApiResponse.error("获取模板列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取模板分页列表
     */
    @GetMapping("/page")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Page<LedgerTemplateResponse>> getTemplatePage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String unitName,
            @RequestParam(required = false) Integer status) {
        try {
            Page<LedgerTemplateResponse> templatePage = ledgerTemplateService.getTemplatePage(
                    page - 1, size, unitName, status);
            return ApiResponse.success("获取成功", templatePage);
        } catch (Exception e) {
            log.error("获取模板分页失败", e);
            return ApiResponse.error("获取模板分页失败: " + e.getMessage());
        }
    }

    /**
     * 获取模板详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRODUCTION')")
    public ApiResponse<LedgerTemplateResponse> getTemplateDetail(@PathVariable Long id) {
        try {
            LedgerTemplateResponse template = ledgerTemplateService.getTemplateDetail(id);
            return ApiResponse.success("获取成功", template);
        } catch (Exception e) {
            log.error("获取模板详情失败", e);
            return ApiResponse.error("获取模板详情失败: " + e.getMessage());
        }
    }

    /**
     * 获取未删除的模板列表
     */
    @GetMapping("/active")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRODUCTION')")
    public ApiResponse<List<LedgerTemplateResponse>> getActiveTemplateList() {
        try {
            List<LedgerTemplateResponse> templates = ledgerTemplateService.getAllActiveTemplates();
            return ApiResponse.success("获取成功", templates);
        } catch (Exception e) {
            log.error("获取模板列表失败", e);
            return ApiResponse.error("获取模板列表失败: " + e.getMessage());
        }
    }

    /**
     * 根据单位名称获取模板
     */
    @GetMapping("/unit/{unitName}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRODUCTION')")
    public ApiResponse<LedgerTemplateResponse> getTemplateByUnit(@PathVariable String unitName) {
        try {
            LedgerTemplateResponse template = ledgerTemplateService.getTemplateByUnitName(unitName);
            return ApiResponse.success("获取成功", template);
        } catch (Exception e) {
            log.error("获取模板失败", e);
            return ApiResponse.error("获取模板失败: " + e.getMessage());
        }
    }

    /**
     * 创建模板
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<LedgerTemplateResponse> createTemplate(@RequestBody LedgerTemplate template) {
        try {
            LedgerTemplateResponse createdTemplate = ledgerTemplateService.createTemplate(template);
            return ApiResponse.success("创建成功", createdTemplate);
        } catch (Exception e) {
            log.error("创建模板失败", e);
            return ApiResponse.error("创建模板失败: " + e.getMessage());
        }
    }

    /**
     * 更新模板
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<LedgerTemplateResponse> updateTemplate(@PathVariable Long id,
                                                              @RequestBody LedgerTemplate template) {
        try {
            template.setId(id);
            LedgerTemplateResponse updatedTemplate = ledgerTemplateService.updateTemplate(template);
            return ApiResponse.success("更新成功", updatedTemplate);
        } catch (Exception e) {
            log.error("更新模板失败", e);
            return ApiResponse.error("更新模板失败: " + e.getMessage());
        }
    }

    /**
     * 删除模板
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteTemplate(@PathVariable Long id) {
        try {
            ledgerTemplateService.deleteTemplate(id);
            return ApiResponse.success("删除成功", null);
        } catch (Exception e) {
            log.error("删除模板失败", e);
            return ApiResponse.error("删除模板失败: " + e.getMessage());
        }
    }

    /**
     * 上传模板文件
     */
    @PostMapping("/upload")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> uploadTemplateFile(@ModelAttribute UploadTemplateRequest request) {
        try {
            if (request.getFile() == null || request.getFile().isEmpty()) {
                return ApiResponse.error("请选择要上传的文件");
            }

            if (request.getTemplateId() == null) {
                return ApiResponse.error("请指定模板ID");
            }

            templateUploadService.uploadAndParseTemplate(request.getTemplateId(), request.getFile());
            return ApiResponse.success("模板文件上传成功", null);
        } catch (IOException e) {
            log.error("上传模板文件失败", e);
            return ApiResponse.error("上传模板文件失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("处理模板文件失败", e);
            return ApiResponse.error("处理模板文件失败: " + e.getMessage());
        }
    }

    /**
     * 检查模板文件是否存在
     */
    @GetMapping("/{id}/file-exists")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRODUCTION')")
    public ApiResponse<Boolean> checkTemplateFile(@PathVariable Long id) {
        try {
            LedgerTemplate template = ledgerTemplateService.getTemplateById(id);
            if (template == null) {
                return ApiResponse.error("模板不存在");
            }

            boolean exists = fileStorageService.templateFileExists(template.getTemplateFilePath());
            return ApiResponse.success("检查完成", exists);
        } catch (Exception e) {
            log.error("检查模板文件失败", e);
            return ApiResponse.error("检查模板文件失败: " + e.getMessage());
        }
    }

    /**
     * 下载模板文件
     */
    @GetMapping("/{id}/download")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRODUCTION')")
    public ApiResponse<String> downloadTemplateFile(@PathVariable Long id) {
        try {
            LedgerTemplate template = ledgerTemplateService.getTemplateById(id);
            if (template == null) {
                return ApiResponse.error("模板不存在");
            }

            String filePath = template.getTemplateFilePath();
            if (filePath == null || filePath.trim().isEmpty()) {
                return ApiResponse.error("模板文件不存在");
            }

            // 这里应该返回文件下载链接或文件内容
            // 为了简单，我们返回文件路径
            return ApiResponse.success("获取成功", filePath);
        } catch (Exception e) {
            log.error("获取模板文件失败", e);
            return ApiResponse.error("获取模板文件失败: " + e.getMessage());
        }
    }
}