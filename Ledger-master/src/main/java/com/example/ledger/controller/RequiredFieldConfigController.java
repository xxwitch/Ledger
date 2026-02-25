package com.example.ledger.controller;

/**
*@author 霜月
*@create 2025/12/26 20:32
*/

import com.example.ledger.dto.request.RequiredFieldConfigRequest;
import com.example.ledger.dto.request.RequiredFieldValidationRequest;
import com.example.ledger.dto.response.ApiResponse;
import com.example.ledger.dto.response.RequiredFieldConfigResponse;
import com.example.ledger.dto.response.RequiredFieldValidationResult;
import com.example.ledger.service.RequiredFieldConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/required-field")
@RequiredArgsConstructor
@Slf4j
public class RequiredFieldConfigController {

    private final RequiredFieldConfigService requiredFieldConfigService;

    /**
     * 获取模板的必填项配置列表
     */
    @GetMapping("/template/{templateId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRODUCTION')")
    public ApiResponse<List<RequiredFieldConfigResponse>> getRequiredFieldsByTemplate(
            @PathVariable Long templateId) {
        try {
            log.info("获取模板必填项配置，模板ID: {}", templateId);
            List<RequiredFieldConfigResponse> configs =
                    requiredFieldConfigService.getRequiredFieldsByTemplate(templateId);
            return ApiResponse.success("获取成功", configs);
        } catch (Exception e) {
            log.error("获取必填项配置失败", e);
            return ApiResponse.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取单位的必填项配置列表
     */
    @GetMapping("/unit/{unitName}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRODUCTION')")
    public ApiResponse<List<RequiredFieldConfigResponse>> getRequiredFieldsByUnit(
            @PathVariable String unitName) {
        try {
            log.info("获取单位必填项配置，单位: {}", unitName);
            List<RequiredFieldConfigResponse> configs =
                    requiredFieldConfigService.getRequiredFieldsByUnit(unitName);
            return ApiResponse.success("获取成功", configs);
        } catch (Exception e) {
            log.error("获取必填项配置失败", e);
            return ApiResponse.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 创建必填项配置
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<RequiredFieldConfigResponse> createRequiredFieldConfig(
            @RequestBody RequiredFieldConfigRequest request) {
        try {
            log.info("创建必填项配置，模板ID: {}, 字段名: {}",
                    request.getTemplateId(), request.getFieldName());
            RequiredFieldConfigResponse config =
                    requiredFieldConfigService.createRequiredFieldConfig(request);
            return ApiResponse.success("创建成功", config);
        } catch (Exception e) {
            log.error("创建必填项配置失败", e);
            return ApiResponse.error("创建失败: " + e.getMessage());
        }
    }

    /**
     * 批量创建必填项配置
     */
    @PostMapping("/batch")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<RequiredFieldConfigResponse>> batchCreateRequiredFieldConfig(
            @RequestBody List<RequiredFieldConfigRequest> requests) {
        try {
            log.info("批量创建必填项配置，数量: {}", requests.size());
            List<RequiredFieldConfigResponse> configs =
                    requiredFieldConfigService.batchCreateRequiredFieldConfig(requests);
            return ApiResponse.success("批量创建成功", configs);
        } catch (Exception e) {
            log.error("批量创建必填项配置失败", e);
            return ApiResponse.error("批量创建失败: " + e.getMessage());
        }
    }

    /**
     * 更新必填项配置
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<RequiredFieldConfigResponse> updateRequiredFieldConfig(
            @PathVariable Long id,
            @RequestBody RequiredFieldConfigRequest request) {
        try {
            log.info("更新必填项配置，配置ID: {}", id);
            request.setId(id);
            RequiredFieldConfigResponse config =
                    requiredFieldConfigService.updateRequiredFieldConfig(request);
            return ApiResponse.success("更新成功", config);
        } catch (Exception e) {
            log.error("更新必填项配置失败", e);
            return ApiResponse.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除必填项配置
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteRequiredFieldConfig(@PathVariable Long id) {
        try {
            log.info("删除必填项配置，配置ID: {}", id);
            requiredFieldConfigService.deleteRequiredFieldConfig(id);
            return ApiResponse.success("删除成功", null);
        } catch (Exception e) {
            log.error("删除必填项配置失败", e);
            return ApiResponse.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 批量删除必填项配置
     */
    @DeleteMapping("/batch")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> batchDeleteRequiredFieldConfig(@RequestBody List<Long> ids) {
        try {
            log.info("批量删除必填项配置，数量: {}", ids.size());
            requiredFieldConfigService.batchDeleteRequiredFieldConfig(ids);
            return ApiResponse.success("批量删除成功", null);
        } catch (Exception e) {
            log.error("批量删除必填项配置失败", e);
            return ApiResponse.error("批量删除失败: " + e.getMessage());
        }
    }

    /**
     * 根据字段名删除配置
     */
    @DeleteMapping("/template/{templateId}/field/{fieldName}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteRequiredFieldByTemplateAndField(
            @PathVariable Long templateId,
            @PathVariable String fieldName) {
        try {
            log.info("删除必填项配置，模板ID: {}, 字段名: {}", templateId, fieldName);
            requiredFieldConfigService.deleteRequiredFieldByTemplateAndField(templateId, fieldName);
            return ApiResponse.success("删除成功", null);
        } catch (Exception e) {
            log.error("删除必填项配置失败", e);
            return ApiResponse.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 设置模板的默认必填项
     */
    @PostMapping("/template/{templateId}/set-default")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<RequiredFieldConfigResponse>> setDefaultRequiredFields(
            @PathVariable Long templateId,
            @RequestBody List<String> fieldNames) {
        try {
            log.info("设置模板默认必填项，模板ID: {}, 字段数量: {}", templateId, fieldNames.size());
            List<RequiredFieldConfigResponse> configs =
                    requiredFieldConfigService.setDefaultRequiredFields(templateId, fieldNames);
            return ApiResponse.success("设置成功", configs);
        } catch (Exception e) {
            log.error("设置默认必填项失败", e);
            return ApiResponse.error("设置失败: " + e.getMessage());
        }
    }

    /**
     * 检查字段是否为必填项
     */
    @GetMapping("/check")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRODUCTION')")
    public ApiResponse<Boolean> checkFieldIsRequired(
            @RequestParam Long templateId,
            @RequestParam String fieldName) {
        try {
            log.info("检查字段是否为必填项，模板ID: {}, 字段名: {}", templateId, fieldName);
            boolean isRequired = requiredFieldConfigService.isFieldRequired(templateId, fieldName);
            return ApiResponse.success("检查成功", isRequired);
        } catch (Exception e) {
            log.error("检查必填项失败", e);
            return ApiResponse.error("检查失败: " + e.getMessage());
        }
    }

    /**
     * 验证数据行的必填项
     */
    @PostMapping("/validate-row")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRODUCTION')")
    public ApiResponse<RequiredFieldValidationResult> validateRequiredFieldsForRow(
            @RequestBody RequiredFieldValidationRequest request) {
        try {
            log.info("验证数据行必填项，模板ID: {}, 字段数: {}",
                    request.getTemplateId(), request.getFieldValues().size());
            RequiredFieldValidationResult result =
                    requiredFieldConfigService.validateRequiredFieldsForRow(request);
            return ApiResponse.success("验证成功", result);
        } catch (Exception e) {
            log.error("验证必填项失败", e);
            return ApiResponse.error("验证失败: " + e.getMessage());
        }
    }
}