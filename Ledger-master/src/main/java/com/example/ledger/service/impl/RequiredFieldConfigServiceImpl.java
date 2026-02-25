package com.example.ledger.service.impl;

/**
 * @author 霜月
 * @create 2025/12/26 20:35
 */

import com.example.ledger.dto.request.RequiredFieldConfigRequest;
import com.example.ledger.dto.request.RequiredFieldValidationRequest;
import com.example.ledger.dto.response.RequiredFieldConfigResponse;
import com.example.ledger.dto.response.RequiredFieldValidationResult;
import com.example.ledger.dto.response.UploadValidationResult;
import com.example.ledger.entity.*;
import com.example.ledger.repository.*;
import com.example.ledger.service.RequiredFieldConfigService;
import com.example.ledger.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RequiredFieldConfigServiceImpl implements RequiredFieldConfigService {

    private final RequiredFieldConfigRepository requiredFieldConfigRepository;
    private final LedgerTemplateRepository ledgerTemplateRepository;
    private final TemplateFieldRepository templateFieldRepository;
    private final UserRepository userRepository;
    private final SecurityUtil securityUtil;

    @Override
    @Transactional(readOnly = true)
    public List<RequiredFieldConfigResponse> getRequiredFieldsByTemplate(Long templateId) {
        log.info("获取模板必填项配置，模板ID: {}", templateId);

        // 验证模板是否存在
        LedgerTemplate template = ledgerTemplateRepository.findByIdAndDeletedFalse(templateId)
                .orElseThrow(() -> new RuntimeException("模板不存在: " + templateId));

        // 获取配置
        List<RequiredFieldConfig> configs = requiredFieldConfigRepository.findByTemplateIdAndRequiredTrue(templateId);

        // 获取模板字段定义
        List<TemplateField> templateFields = templateFieldRepository.findByTemplateIdAndDeletedFalse(templateId);
        Map<String, String> fieldLabelMap = templateFields.stream()
                .collect(Collectors.toMap(
                        field -> field.getFieldName() + "_" + field.getExcelColumn(),  // 使用复合键
                        TemplateField::getFieldLabel,
                        (v1, v2) -> v1  // 如果键重复，保留第一个
                ));

        // 转换为响应对象
        return configs.stream()
                .map(config -> convertToResponse(config, template, fieldLabelMap))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RequiredFieldConfigResponse> getRequiredFieldsByUnit(String unitName) {
        log.info("获取单位必填项配置，单位: {}", unitName);

        // 获取单位模板
        LedgerTemplate template = ledgerTemplateRepository.findByUnitNameAndDeletedFalse(unitName)
                .orElseThrow(() -> new RuntimeException("单位模板不存在: " + unitName));

        return getRequiredFieldsByTemplate(template.getId());
    }

    @Override
    @Transactional
    public RequiredFieldConfigResponse createRequiredFieldConfig(RequiredFieldConfigRequest request) {
        log.info("创建必填项配置，模板ID: {}, 字段名: {}", request.getTemplateId(), request.getFieldName());

        // 验证模板
        LedgerTemplate template = ledgerTemplateRepository.findByIdAndDeletedFalse(request.getTemplateId())
                .orElseThrow(() -> new RuntimeException("模板不存在: " + request.getTemplateId()));

        // 验证字段是否存在于模板中
        List<TemplateField> templateFields = templateFieldRepository.findByTemplateIdAndDeletedFalse(request.getTemplateId());
        boolean fieldExists = templateFields.stream()
                .anyMatch(field -> field.getFieldName().equals(request.getFieldName()));

        if (!fieldExists) {
            throw new RuntimeException("字段不存在于模板中: " + request.getFieldName());
        }

        // 检查是否已存在配置
        Optional<RequiredFieldConfig> existingConfig = requiredFieldConfigRepository
                .findByTemplateIdAndFieldName(request.getTemplateId(), request.getFieldName());

        RequiredFieldConfig config;
        if (existingConfig.isPresent()) {
            // 更新现有配置
            config = existingConfig.get();
            config.setIsRequired(request.getIsRequired());
            config.setRequiredMessage(request.getRequiredMessage());
            config.setValidationRule(request.getValidationRule());
            config.setConfigType(request.getConfigType());
            config.setUserId(request.getUserId());
            config.setUpdatedTime(LocalDateTime.now());
        } else {
            // 创建新配置
            config = new RequiredFieldConfig();
            config.setTemplateId(request.getTemplateId());
            config.setFieldName(request.getFieldName());
            config.setIsRequired(request.getIsRequired());
            config.setRequiredMessage(request.getRequiredMessage());
            config.setValidationRule(request.getValidationRule());
            config.setConfigType(request.getConfigType());
            config.setUserId(request.getUserId());
            config.setDeleted(false);
        }

        RequiredFieldConfig savedConfig = requiredFieldConfigRepository.save(config);

        // 获取字段标签
        String fieldLabel = templateFields.stream()
                .filter(field -> field.getFieldName().equals(request.getFieldName()))
                .findFirst()
                .map(TemplateField::getFieldLabel)
                .orElse(request.getFieldName());

        return convertToResponse(savedConfig, template, Map.of(request.getFieldName(), fieldLabel));
    }

    @Override
    @Transactional
    public List<RequiredFieldConfigResponse> batchCreateRequiredFieldConfig(List<RequiredFieldConfigRequest> requests) {
        log.info("批量创建必填项配置，数量: {}", requests.size());

        List<RequiredFieldConfigResponse> responses = new ArrayList<>();

        for (RequiredFieldConfigRequest request : requests) {
            try {
                RequiredFieldConfigResponse response = createRequiredFieldConfig(request);
                responses.add(response);
            } catch (Exception e) {
                log.error("创建必填项配置失败，模板ID: {}, 字段名: {}，错误: {}",
                        request.getTemplateId(), request.getFieldName(), e.getMessage());
                throw new RuntimeException("批量创建失败: " + e.getMessage());
            }
        }

        return responses;
    }

    @Override
    @Transactional
    public RequiredFieldConfigResponse updateRequiredFieldConfig(RequiredFieldConfigRequest request) {
        log.info("更新必填项配置，配置ID: {}", request.getId());

        RequiredFieldConfig config = requiredFieldConfigRepository.findById(request.getId())
                .orElseThrow(() -> new RuntimeException("必填项配置不存在: " + request.getId()));

        // 验证模板
        LedgerTemplate template = ledgerTemplateRepository.findByIdAndDeletedFalse(config.getTemplateId())
                .orElseThrow(() -> new RuntimeException("模板不存在: " + config.getTemplateId()));

        // 更新配置
        config.setIsRequired(request.getIsRequired());

        if (request.getIsRequired()) {
            // 如果是必填，设置提示信息和验证规则
            if (request.getRequiredMessage() != null) {
                config.setRequiredMessage(request.getRequiredMessage());
            } else if (config.getRequiredMessage() == null) {
                config.setRequiredMessage("此项不能为空");
            }

            if (request.getValidationRule() != null) {
                config.setValidationRule(request.getValidationRule());
            }
        } else {
            // 如果是非必填，清空提示信息和验证规则
            config.setRequiredMessage(null);
            config.setValidationRule(null);
        }

        if (request.getConfigType() != null) {
            config.setConfigType(request.getConfigType());
        }
        if (request.getUserId() != null) {
            config.setUserId(request.getUserId());
        }

        config.setUpdatedTime(LocalDateTime.now());

        RequiredFieldConfig savedConfig = requiredFieldConfigRepository.save(config);

        // 获取字段标签
        List<TemplateField> templateFields = templateFieldRepository.findByTemplateIdAndDeletedFalse(config.getTemplateId());
        String fieldLabel = templateFields.stream()
                .filter(field -> field.getFieldName().equals(config.getFieldName()))
                .findFirst()
                .map(TemplateField::getFieldLabel)
                .orElse(config.getFieldName());

        return convertToResponse(savedConfig, template, Map.of(config.getFieldName(), fieldLabel));
    }

    @Override
    @Transactional
    public void deleteRequiredFieldConfig(Long id) {
        log.info("删除必填项配置，配置ID: {}", id);

        RequiredFieldConfig config = requiredFieldConfigRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("必填项配置不存在: " + id));

        config.setDeleted(true);
        config.setUpdatedTime(LocalDateTime.now());

        requiredFieldConfigRepository.save(config);
    }

    @Override
    @Transactional
    public void batchDeleteRequiredFieldConfig(List<Long> ids) {
        log.info("批量删除必填项配置，数量: {}", ids.size());

        for (Long id : ids) {
            try {
                deleteRequiredFieldConfig(id);
            } catch (Exception e) {
                log.error("删除必填项配置失败，配置ID: {}，错误: {}", id, e.getMessage());
                throw new RuntimeException("批量删除失败: " + e.getMessage());
            }
        }
    }

    @Override
    @Transactional
    public void deleteRequiredFieldByTemplateAndField(Long templateId, String fieldName) {
        log.info("删除必填项配置，模板ID: {}, 字段名: {}", templateId, fieldName);

        RequiredFieldConfig config = requiredFieldConfigRepository
                .findByTemplateIdAndFieldName(templateId, fieldName)
                .orElseThrow(() -> new RuntimeException("必填项配置不存在，模板ID: " + templateId + ", 字段名: " + fieldName));

        deleteRequiredFieldConfig(config.getId());
    }

    @Override
    @Transactional
    public List<RequiredFieldConfigResponse> setDefaultRequiredFields(Long templateId, List<String> fieldNames) {
        log.info("设置模板默认必填项，模板ID: {}, 字段数量: {}", templateId, fieldNames.size());

        // 验证模板
        LedgerTemplate template = ledgerTemplateRepository.findByIdAndDeletedFalse(templateId)
                .orElseThrow(() -> new RuntimeException("模板不存在: " + templateId));

        // 获取当前配置
        List<RequiredFieldConfig> existingConfigs = requiredFieldConfigRepository.findByTemplateId(templateId);
        Map<String, RequiredFieldConfig> existingConfigMap = existingConfigs.stream()
                .collect(Collectors.toMap(RequiredFieldConfig::getFieldName, config -> config));

        List<RequiredFieldConfigResponse> responses = new ArrayList<>();

        // 获取当前用户ID
        Long currentUserId = securityUtil.getCurrentUserId();

        // 设置新配置
        for (String fieldName : fieldNames) {
            RequiredFieldConfig config;

            if (existingConfigMap.containsKey(fieldName)) {
                // 更新现有配置
                config = existingConfigMap.get(fieldName);
                config.setIsRequired(true);
                config.setConfigType("SYSTEM");
                config.setUserId(currentUserId);
                config.setUpdatedTime(LocalDateTime.now());
            } else {
                // 创建新配置
                config = new RequiredFieldConfig();
                config.setTemplateId(templateId);
                config.setFieldName(fieldName);
                config.setIsRequired(true);
                config.setConfigType("SYSTEM");
                config.setUserId(currentUserId);
                config.setDeleted(false);
            }

            RequiredFieldConfig savedConfig = requiredFieldConfigRepository.save(config);

            // 获取字段标签
            List<TemplateField> templateFields = templateFieldRepository.findByTemplateIdAndDeletedFalse(templateId);
            String fieldLabel = templateFields.stream()
                    .filter(field -> field.getFieldName().equals(fieldName))
                    .findFirst()
                    .map(TemplateField::getFieldLabel)
                    .orElse(fieldName);

            responses.add(convertToResponse(savedConfig, template, Map.of(fieldName, fieldLabel)));
        }

        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFieldRequired(Long templateId, String fieldName) {
        Optional<RequiredFieldConfig> config = requiredFieldConfigRepository
                .findByTemplateIdAndFieldName(templateId, fieldName);

        return config.map(RequiredFieldConfig::getIsRequired).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public RequiredFieldValidationResult validateRequiredFieldsForRow(RequiredFieldValidationRequest request) {
        log.info("验证数据行必填项，模板ID: {}, 字段数: {}",
                request.getTemplateId(), request.getFieldValues().size());

        // 获取模板和字段定义
        LedgerTemplate template = ledgerTemplateRepository.findByIdAndDeletedFalse(request.getTemplateId())
                .orElseThrow(() -> new RuntimeException("模板不存在: " + request.getTemplateId()));

        List<TemplateField> templateFields = templateFieldRepository.findByTemplateIdAndDeletedFalse(request.getTemplateId());
        Map<String, String> fieldLabelMap = templateFields.stream()
                .collect(Collectors.toMap(
                        field -> field.getFieldName() + "_" + field.getExcelColumn(),
                        TemplateField::getFieldLabel,
                        (v1, v2) -> v1
                ));

        // 获取必填项配置
        List<RequiredFieldConfig> requiredConfigs = requiredFieldConfigRepository.findByTemplateIdAndRequiredTrue(request.getTemplateId());
        Map<String, RequiredFieldConfig> requiredConfigMap = requiredConfigs.stream()
                .collect(Collectors.toMap(RequiredFieldConfig::getFieldName, config -> config));

        // 验证每个字段
        List<RequiredFieldValidationResult.FieldValidationDetail> details = new ArrayList<>();
        int emptyRequiredFields = 0;
        int totalFields = request.getFieldValues().size();
        int requiredFields = requiredConfigs.size();

        for (Map.Entry<String, String> entry : request.getFieldValues().entrySet()) {
            String fieldName = entry.getKey();
            String fieldValue = entry.getValue();

            RequiredFieldConfig config = requiredConfigMap.get(fieldName);
            boolean isRequired = config != null && Boolean.TRUE.equals(config.getIsRequired());
            boolean isEmpty = fieldValue == null || fieldValue.trim().isEmpty();

            RequiredFieldValidationResult.FieldValidationDetail detail =
                    RequiredFieldValidationResult.FieldValidationDetail.builder()
                            .fieldName(fieldName)
                            .fieldLabel(fieldLabelMap.getOrDefault(fieldName, fieldName))
                            .fieldValue(fieldValue)
                            .isRequired(isRequired)
                            .isEmpty(isEmpty)
                            .requiredMessage(config != null ? config.getRequiredMessage() : null)
                            .build();

            if (isRequired && isEmpty) {
                detail.setValidationMessage(fieldLabelMap.getOrDefault(fieldName, fieldName) + "为必填项，不能为空");
                emptyRequiredFields++;
            }
            // 移除验证规则检查，只检查是否必填

            details.add(detail);
        }

        // 检查是否有必填项未提供值
        for (RequiredFieldConfig config : requiredConfigs) {
            String fieldName = config.getFieldName();
            if (!request.getFieldValues().containsKey(fieldName)) {
                // 必填项未提供
                RequiredFieldValidationResult.FieldValidationDetail detail =
                        RequiredFieldValidationResult.FieldValidationDetail.builder()
                                .fieldName(fieldName)
                                .fieldLabel(fieldLabelMap.getOrDefault(fieldName, fieldName))
                                .fieldValue(null)
                                .isRequired(true)
                                .isEmpty(true)
                                .requiredMessage(config.getRequiredMessage())
                                .validationMessage(fieldLabelMap.getOrDefault(fieldName, fieldName) + "为必填项，未提供值")
                                .build();
                details.add(detail);
                emptyRequiredFields++;
            }
        }

        boolean isValid = emptyRequiredFields == 0;
        String summaryMessage = isValid ?
                "验证通过，所有必填项都已填写" :
                String.format("验证失败，%d个必填项未填写", emptyRequiredFields);

        return RequiredFieldValidationResult.builder()
                .isValid(isValid)
                .totalFields(totalFields)
                .requiredFields(requiredFields)
                .emptyRequiredFields(emptyRequiredFields)
                .validationDetails(details)
                .summaryMessage(summaryMessage)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UploadValidationResult validateExcelFile(MultipartFile file, Long templateId, Boolean validateRequiredFields) {
        log.info("验证Excel文件必填项，模板ID: {}, 文件名: {}", templateId, file.getOriginalFilename());

        // 如果不需要验证必填项，直接返回成功
        if (!Boolean.TRUE.equals(validateRequiredFields)) {
            return UploadValidationResult.builder()
                    .uploadValid(true)
                    .canProceed(true)
                    .totalRows(0)
                    .validRows(0)
                    .invalidRows(0)
                    .rowDetails(new ArrayList<>())
                    .errorMessages(new ArrayList<>())
                    .build();
        }

        // 获取模板信息
        LedgerTemplate template = ledgerTemplateRepository.findByIdAndDeletedFalse(templateId)
                .orElseThrow(() -> new RuntimeException("模板不存在: " + templateId));

        // 获取模板字段定义 - 修复重复列名问题
        List<TemplateField> templateFields = templateFieldRepository.findByTemplateIdAndDeletedFalse(templateId);

        // 修复：使用复合键避免重复列名问题
        Map<String, TemplateField> fieldMap = templateFields.stream()
                .collect(Collectors.toMap(
                        // 使用字段名称+列字母作为复合键
                        field -> field.getFieldName() + "_" + field.getExcelColumn(),
                        Function.identity(),
                        // 如果键重复，保留第一个并记录警告
                        (f1, f2) -> {
                            log.warn("发现重复的字段名+列组合: {}_{}，保留第一个",
                                    f1.getFieldName(), f1.getExcelColumn());
                            return f1;
                        }
                ));

        // 获取必填项配置
        List<RequiredFieldConfig> requiredConfigs = requiredFieldConfigRepository.findByTemplateIdAndRequiredTrue(templateId);
        Map<String, RequiredFieldConfig> requiredConfigMap = requiredConfigs.stream()
                .collect(Collectors.toMap(RequiredFieldConfig::getFieldName, config -> config));

        // 创建列索引到复合键的映射
        Map<Integer, String> columnIndexToCompoundKey = new HashMap<>();
        for (TemplateField field : templateFields) {
            int columnIndex = excelColumnToIndex(field.getExcelColumn());
            String compoundKey = field.getFieldName() + "_" + field.getExcelColumn();
            columnIndexToCompoundKey.put(columnIndex, compoundKey);
        }

        List<UploadValidationResult.RowValidationDetail> rowDetails = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            int dataStartRow = template.getDataStartRow() - 1;
            int totalRows = 0;
            int validRows = 0;
            int invalidRows = 0;

            // 逐行验证
            for (int rowNum = dataStartRow; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null || isEmptyRow(row)) {
                    continue;
                }

                totalRows++;
                int excelRowNum = rowNum + 1;
                int dataRowNumber = excelRowNum - template.getDataStartRow() + 1;

                List<UploadValidationResult.RowValidationDetail.FieldValidationDetail> fieldDetails = new ArrayList<>();
                boolean rowValid = true;
                StringBuilder rowValidationMessage = new StringBuilder();

                // 验证每个单元格
                for (Map.Entry<Integer, String> entry : columnIndexToCompoundKey.entrySet()) {
                    int colIndex = entry.getKey();
                    String compoundKey = entry.getValue();
                    Cell cell = row.getCell(colIndex);
                    String cellValue = getCellValue(cell);

                    TemplateField fieldDef = fieldMap.get(compoundKey);
                    if (fieldDef == null) {
                        log.warn("未找到字段定义，列索引: {}, 复合键: {}", colIndex, compoundKey);
                        continue;
                    }

                    String fieldName = fieldDef.getFieldName();
                    RequiredFieldConfig config = requiredConfigMap.get(fieldName);
                    boolean isRequired = config != null && Boolean.TRUE.equals(config.getIsRequired());
                    boolean isEmpty = cellValue == null || cellValue.trim().isEmpty();

                    UploadValidationResult.RowValidationDetail.FieldValidationDetail fieldDetail =
                            UploadValidationResult.RowValidationDetail.FieldValidationDetail.builder()
                                    .fieldName(fieldName)
                                    .fieldLabel(fieldDef.getFieldLabel())
                                    .fieldValue(cellValue)
                                    .isRequired(isRequired)
                                    .isEmpty(isEmpty)
                                    .build();

                    if (isRequired && isEmpty) {
                        // 必填项为空
                        String message = fieldDef.getFieldLabel();
                        message += "为必填项，不能为空";
                        if (config != null && config.getRequiredMessage() != null) {
                            message = config.getRequiredMessage();
                        }

                        fieldDetail.setValidationMessage(message);
                        rowValid = false;
                        if (rowValidationMessage.length() > 0) {
                            rowValidationMessage.append("; ");
                        }
                        rowValidationMessage.append(message);
                    }
                    // 移除验证规则检查，只检查是否必填

                    fieldDetails.add(fieldDetail);
                }

                // 检查是否有必填项未在Excel中定义列
                // 首先收集所有已定义的字段名
                Set<String> definedFieldNames = templateFields.stream()
                        .map(TemplateField::getFieldName)
                        .collect(Collectors.toSet());

                for (RequiredFieldConfig config : requiredConfigs) {
                    String fieldName = config.getFieldName();
                    if (!definedFieldNames.contains(fieldName)) {
                        // 必填项在Excel中没有对应列
                        rowValid = false;
                        if (rowValidationMessage.length() > 0) {
                            rowValidationMessage.append("; ");
                        }
                        String message = "必填项'" + fieldName + "'在Excel中未找到对应列";
                        rowValidationMessage.append(message);

                        // 添加一个字段级别的错误
                        UploadValidationResult.RowValidationDetail.FieldValidationDetail fieldDetail =
                                UploadValidationResult.RowValidationDetail.FieldValidationDetail.builder()
                                        .fieldName(fieldName)
                                        .fieldLabel(fieldName)
                                        .fieldValue(null)
                                        .isRequired(true)
                                        .isEmpty(true)
                                        .validationMessage(message)
                                        .build();
                        fieldDetails.add(fieldDetail);
                    }
                }

                UploadValidationResult.RowValidationDetail rowDetail =
                        UploadValidationResult.RowValidationDetail.builder()
                                .rowNumber(dataRowNumber)
                                .excelRowNumber(excelRowNum)
                                .isValid(rowValid)
                                .fieldDetails(fieldDetails)
                                .rowValidationMessage(rowValidationMessage.toString())
                                .build();

                rowDetails.add(rowDetail);

                if (rowValid) {
                    validRows++;
                } else {
                    invalidRows++;
                    String errorMsg = String.format("第%d行: %s", excelRowNum, rowValidationMessage.toString());
                    errorMessages.add(errorMsg);
                }
            }

            // 严格模式：只要有错误行，整个文件就不允许上传
            boolean uploadValid = invalidRows == 0;

            // 在严格模式下，只要有错误就不能继续
            boolean canProceed = uploadValid;

            return UploadValidationResult.builder()
                    .uploadValid(uploadValid)
                    .totalRows(totalRows)
                    .validRows(validRows)
                    .invalidRows(invalidRows)
                    .rowDetails(rowDetails)
                    .errorMessages(errorMessages)
                    .canProceed(canProceed)
                    .build();

        } catch (IOException e) {
            log.error("验证Excel文件失败", e);
            throw new RuntimeException("验证Excel文件失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getRequiredFieldNamesByTemplate(Long templateId) {
        return requiredFieldConfigRepository.findByTemplateIdAndRequiredTrue(templateId).stream()
                .map(RequiredFieldConfig::getFieldName)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Boolean> getRequiredFieldMapByTemplate(Long templateId) {
        return requiredFieldConfigRepository.findByTemplateId(templateId).stream()
                .collect(Collectors.toMap(
                        RequiredFieldConfig::getFieldName,
                        RequiredFieldConfig::getIsRequired,
                        (v1, v2) -> v1
                ));
    }

    // ========== 私有方法 ==========

    private RequiredFieldConfigResponse convertToResponse(RequiredFieldConfig config,
                                                          LedgerTemplate template,
                                                          Map<String, String> fieldLabelMap) {
        String userName = null;
        if (config.getUserId() != null) {
            userName = userRepository.findById(config.getUserId())
                    .map(User::getUsername)
                    .orElse(null);
        }

        // 构建复合键来获取字段标签
        String compoundKey = config.getFieldName() + "_" + getFieldExcelColumn(config.getTemplateId(), config.getFieldName());
        String fieldLabel = fieldLabelMap.getOrDefault(compoundKey, config.getFieldName());

        return RequiredFieldConfigResponse.builder()
                .id(config.getId())
                .templateId(config.getTemplateId())
                .unitName(template.getUnitName())
                .fieldName(config.getFieldName())
                .fieldLabel(fieldLabel)
                .isRequired(config.getIsRequired())
                .requiredMessage(config.getRequiredMessage())
                .validationRule(config.getValidationRule())
                .configType(config.getConfigType())
                .userId(config.getUserId())
                .userName(userName)
                .createdTime(config.getCreatedTime())
                .updatedTime(config.getUpdatedTime())
                .build();
    }

    /**
     * 获取字段的Excel列字母
     */
    private String getFieldExcelColumn(Long templateId, String fieldName) {
        return templateFieldRepository.findByTemplateIdAndDeletedFalse(templateId).stream()
                .filter(field -> field.getFieldName().equals(fieldName))
                .findFirst()
                .map(TemplateField::getExcelColumn)
                .orElse("A");
    }

    private String validateFieldByRule(String value, String rule) {
        if (value == null || rule == null) {
            return null;
        }

        // 这里可以实现具体的验证规则逻辑
        // 例如：正则表达式验证、范围验证等
        try {
            if (!value.matches(rule)) {
                return null;
            }
        } catch (Exception e) {
            log.warn("验证规则执行失败，规则: {}，值: {}", rule, value, e);
            return null;
        }

        return null;
    }

    private String getCellValue(Cell cell) {
        if (cell == null) {
            return null;
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    double value = cell.getNumericCellValue();
                    if (value == Math.floor(value)) {
                        return String.valueOf((int) value);
                    } else {
                        return String.valueOf(value);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        return String.valueOf(cell.getNumericCellValue());
                    } catch (Exception e2) {
                        return cell.getCellFormula();
                    }
                }
            default:
                return null;
        }
    }

    private boolean isEmptyRow(Row row) {
        if (row == null) {
            return true;
        }

        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null) {
                String value = getCellValue(cell);
                if (value != null && !value.trim().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private int excelColumnToIndex(String column) {
        if (column == null || column.trim().isEmpty()) {
            return 0;
        }

        column = column.toUpperCase().trim();
        int index = 0;
        for (int i = 0; i < column.length(); i++) {
            char ch = column.charAt(i);
            if (ch < 'A' || ch > 'Z') {
                throw new IllegalArgumentException("无效的列字母: " + column);
            }
            index = index * 26 + (ch - 'A' + 1);
        }
        return index - 1;
    }
}