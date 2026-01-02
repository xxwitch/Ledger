package com.example.ledger.controller;

/**
 * @author 霜月
 * @create 2025/12/21 01:27
 */

import com.example.ledger.dto.request.LedgerUploadRequest;
import com.example.ledger.dto.response.ApiResponse;
import com.example.ledger.dto.response.LedgerUploadResponse;
import com.example.ledger.entity.LedgerTemplate;
import com.example.ledger.repository.LedgerTemplateRepository;
import com.example.ledger.service.LedgerUploadService;
import com.example.ledger.service.RequiredFieldConfigService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ledger/upload")
@RequiredArgsConstructor
@Slf4j
public class LedgerUploadController {

    private final LedgerUploadService ledgerUploadService;
    private final LedgerTemplateRepository ledgerTemplateRepository;
    private final RequiredFieldConfigService requiredFieldConfigService;

    /**
     * 上传台账数据（异步处理）- 包含必填项验证
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRODUCTION')")
    public ApiResponse<LedgerUploadResponse> uploadLedgerData(
            @ModelAttribute LedgerUploadRequest request,
            HttpServletRequest httpRequest) {

        try {
            log.info("接收到台账数据上传请求，单位: {}，文件名: {}，验证必填项: {}，跳过无效行: {}",
                    request.getUnitName(),
                    request.getFile() != null ? request.getFile().getOriginalFilename() : "null",
                    request.getValidateRequiredFields(),
                    request.getSkipInvalidRows());

            // 验证文件是否存在
            if (request.getFile() == null || request.getFile().isEmpty()) {
                return ApiResponse.error("请选择要上传的文件");
            }

            // 验证单位名称
            if (request.getUnitName() == null || request.getUnitName().trim().isEmpty()) {
                return ApiResponse.error("单位名称不能为空");
            }

            // 获取客户端IP
            String uploadIp = getClientIp(httpRequest);

            // 如果开启了必填项验证，先验证文件
            if (Boolean.TRUE.equals(request.getValidateRequiredFields())) {
                try {
                    log.info("开始进行必填项验证...");

                    // 获取单位模板
                    LedgerTemplate template = ledgerTemplateRepository
                            .findByUnitNameAndDeletedFalse(request.getUnitName())
                            .orElseThrow(() -> new RuntimeException("单位模板不存在: " + request.getUnitName()));

                    log.info("找到模板: {}，模板ID: {}", template.getTemplateName(), template.getId());

                    // 验证Excel文件
                    com.example.ledger.dto.response.UploadValidationResult validationResult =
                            requiredFieldConfigService.validateExcelFile(
                                    request.getFile(),
                                    template.getId(),
                                    true);

                    log.info("文件验证完成，总行数: {}，有效行数: {}，无效行数: {}",
                            validationResult.getTotalRows(),
                            validationResult.getValidRows(),
                            validationResult.getInvalidRows());

                    if (!Boolean.TRUE.equals(validationResult.getUploadValid())) {
                        // 文件验证失败
                        List<String> errorMessages = validationResult.getErrorMessages();
                        String errorMessage;

                        if (errorMessages != null && !errorMessages.isEmpty()) {
                            // 限制错误信息长度
                            int maxErrors = 10;
                            int displayErrors = Math.min(maxErrors, errorMessages.size());
                            StringBuilder sb = new StringBuilder();
                            sb.append("文件验证失败，发现").append(validationResult.getInvalidRows())
                                    .append("个错误行：\n");

                            for (int i = 0; i < displayErrors; i++) {
                                sb.append(errorMessages.get(i)).append("\n");
                            }

                            if (errorMessages.size() > maxErrors) {
                                sb.append("... 还有").append(errorMessages.size() - maxErrors)
                                        .append("个错误未显示");
                            }

                            errorMessage = sb.toString();
                        } else {
                            errorMessage = "文件验证失败，请检查文件内容";
                        }

                        // 在严格模式下，只要验证失败就直接返回错误，不继续上传
                        // 无论skipInvalidRows是什么值，严格模式都不允许跳过
                        log.error("严格模式：文件验证失败，不允许继续，错误: {}", errorMessage);
                        return ApiResponse.error(errorMessage);

                    } else if (validationResult.getInvalidRows() > 0) {
                        // 如果验证通过但有警告信息
                        log.warn("文件验证通过，但有警告信息");
                    }
                } catch (Exception e) {
                    log.error("文件验证过程发生异常", e);
                    return ApiResponse.error("文件验证失败: " + e.getMessage());
                }
            }

            // 异步上传，立即返回
            LedgerUploadResponse response = ledgerUploadService.uploadLedgerData(request, uploadIp);
            return ApiResponse.success("上传已开始处理", response);

        } catch (IOException e) {
            log.error("文件上传IO异常", e);
            return ApiResponse.error("文件上传失败: " + e.getMessage());
        } catch (RuntimeException e) {
            log.error("台账数据上传业务异常", e);
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("台账数据上传未知异常", e);
            return ApiResponse.error("上传失败: " + e.getMessage());
        }
    }
    /**
     * 查询上传进度
     */
    @GetMapping("/progress/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRODUCTION')")
    public ApiResponse<Map<String, Object>> getUploadProgress(@PathVariable Long id) {
        try {
            Map<String, Object> progress = ledgerUploadService.getUploadProgress(id);
            if (progress == null) {
                return ApiResponse.error("上传记录不存在");
            }
            return ApiResponse.success("查询成功", progress);
        } catch (Exception e) {
            log.error("查询上传进度失败", e);
            return ApiResponse.error("查询上传进度失败: " + e.getMessage());
        }
    }

    /**
     * 查询上传状态
     */
    @GetMapping("/status/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRODUCTION')")
    public ApiResponse<LedgerUploadResponse> getUploadStatus(@PathVariable Long id) {
        try {
            LedgerUploadResponse response = ledgerUploadService.getUploadDetail(id);
            if (response == null) {
                return ApiResponse.error("上传记录不存在");
            }
            return ApiResponse.success("查询成功", response);
        } catch (Exception e) {
            log.error("查询上传状态失败", e);
            return ApiResponse.error("查询上传状态失败: " + e.getMessage());
        }
    }

    /**
     * 获取上传历史记录
     */
    @GetMapping("/history")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRODUCTION')")
    public ApiResponse<List<LedgerUploadResponse>> getUploadHistory(
            @RequestParam(required = false) String unitName,
            @RequestParam(required = false) Long userId) {

        try {
            List<LedgerUploadResponse> history = ledgerUploadService.getUploadHistory(unitName, userId);
            return ApiResponse.success("获取成功", history);
        } catch (Exception e) {
            log.error("获取上传历史失败", e);
            return ApiResponse.error("获取上传历史失败: " + e.getMessage());
        }
    }

    /**
     * 获取上传详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRODUCTION')")
    public ApiResponse<LedgerUploadResponse> getUploadDetail(@PathVariable Long id) {
        try {
            LedgerUploadResponse detail = ledgerUploadService.getUploadDetail(id);
            return ApiResponse.success("获取成功", detail);
        } catch (Exception e) {
            log.error("获取上传详情失败", e);
            return ApiResponse.error("获取上传详情失败: " + e.getMessage());
        }
    }

    /**
     * 删除上传记录
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteUpload(@PathVariable Long id) {
        try {
            ledgerUploadService.deleteUpload(id);
            return ApiResponse.success("删除成功", null);
        } catch (Exception e) {
            log.error("删除上传记录失败", e);
            return ApiResponse.error("删除上传记录失败: " + e.getMessage());
        }
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}