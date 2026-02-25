package com.example.ledger.service;

/**
*@author 霜月
*@create 2025/12/26 20:35
*/

import com.example.ledger.dto.request.RequiredFieldConfigRequest;
import com.example.ledger.dto.request.RequiredFieldValidationRequest;
import com.example.ledger.dto.response.RequiredFieldConfigResponse;
import com.example.ledger.dto.response.RequiredFieldValidationResult;
import com.example.ledger.dto.response.UploadValidationResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface RequiredFieldConfigService {

    /**
     * 获取模板的必填项配置
     */
    List<RequiredFieldConfigResponse> getRequiredFieldsByTemplate(Long templateId);

    /**
     * 获取单位的必填项配置
     */
    List<RequiredFieldConfigResponse> getRequiredFieldsByUnit(String unitName);

    /**
     * 创建必填项配置
     */
    RequiredFieldConfigResponse createRequiredFieldConfig(RequiredFieldConfigRequest request);

    /**
     * 批量创建必填项配置
     */
    List<RequiredFieldConfigResponse> batchCreateRequiredFieldConfig(List<RequiredFieldConfigRequest> requests);

    /**
     * 更新必填项配置
     */
    RequiredFieldConfigResponse updateRequiredFieldConfig(RequiredFieldConfigRequest request);

    /**
     * 删除必填项配置
     */
    void deleteRequiredFieldConfig(Long id);

    /**
     * 批量删除必填项配置
     */
    void batchDeleteRequiredFieldConfig(List<Long> ids);

    /**
     * 根据模板和字段名删除配置
     */
    void deleteRequiredFieldByTemplateAndField(Long templateId, String fieldName);

    /**
     * 设置模板的默认必填项
     */
    List<RequiredFieldConfigResponse> setDefaultRequiredFields(Long templateId, List<String> fieldNames);

    /**
     * 检查字段是否为必填项
     */
    boolean isFieldRequired(Long templateId, String fieldName);

    /**
     * 验证数据行的必填项
     */
    RequiredFieldValidationResult validateRequiredFieldsForRow(RequiredFieldValidationRequest request);

    /**
     * 验证整个Excel文件的必填项
     */
    UploadValidationResult validateExcelFile(MultipartFile file, Long templateId, Boolean validateRequiredFields);

    /**
     * 获取模板的所有必填字段
     */
    List<String> getRequiredFieldNamesByTemplate(Long templateId);

    /**
     * 获取模板的必填字段映射
     */
    Map<String, Boolean> getRequiredFieldMapByTemplate(Long templateId);
}