package com.example.ledger.service;

/**
 * @author 霜月
 * @create 2025/12/21 16:40
 */

import com.example.ledger.dto.request.LedgerDataEditRequest;
import com.example.ledger.dto.request.LedgerDataBatchEditRequest;
import com.example.ledger.dto.request.LedgerDataDeleteRequest;
import com.example.ledger.entity.*;
import com.example.ledger.repository.*;
import com.example.ledger.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerDataEditService {

    private final LedgerDataRepository ledgerDataRepository;
    private final LedgerDataDetailRepository ledgerDataDetailRepository;
    private final LedgerEditHistoryRepository ledgerEditHistoryRepository;
    private final TemplateFieldRepository templateFieldRepository;
    private final RequiredFieldConfigRepository requiredFieldConfigRepository;
    private final UserRepository userRepository;
    private final SecurityUtil securityUtil;

    /**
     * 编辑单个台账数据
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> editLedgerData(LedgerDataEditRequest request, String ipAddress) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> editResults = new ArrayList<>();

        // 获取当前用户信息
        Long userId = getCurrentUserId();
        String userName = getCurrentUserName();

        // 获取台账数据
        LedgerData ledgerData = ledgerDataRepository.findById(request.getDataId())
                .orElseThrow(() -> new RuntimeException("台账数据不存在: " + request.getDataId()));

        // 检查数据是否已被删除
        if (ledgerData.getDeleted()) {
            throw new RuntimeException("台账数据已被删除，无法编辑");
        }

        // 获取模板字段定义
        List<TemplateField> templateFields = templateFieldRepository.findByTemplateIdAndDeletedFalse(ledgerData.getTemplateId());
        Map<String, TemplateField> fieldMap = templateFields.stream()
                .collect(Collectors.toMap(TemplateField::getFieldName, f -> f));

        // 获取必填项配置
        Set<String> requiredFields = requiredFieldConfigRepository.findByTemplateIdAndRequiredTrue(ledgerData.getTemplateId())
                .stream()
                .map(RequiredFieldConfig::getFieldName)
                .collect(Collectors.toSet());

        // 获取现有字段值
        List<LedgerDataDetail> existingDetails = ledgerDataDetailRepository.findByDataId(request.getDataId());
        Map<String, LedgerDataDetail> detailMap = existingDetails.stream()
                .collect(Collectors.toMap(LedgerDataDetail::getFieldName, d -> d));

        // 验证并更新字段
        for (Map.Entry<String, String> entry : request.getFieldValues().entrySet()) {
            String fieldName = entry.getKey();
            String newValue = entry.getValue();

            Map<String, Object> fieldResult = new HashMap<>();
            fieldResult.put("fieldName", fieldName);
            fieldResult.put("oldValue", null);
            fieldResult.put("newValue", newValue);

            try {
                // 检查字段是否存在
                TemplateField fieldDef = fieldMap.get(fieldName);
                if (fieldDef == null) {
                    fieldResult.put("status", "FAILED");
                    fieldResult.put("message", "字段不存在");
                    editResults.add(fieldResult);
                    continue;
                }

                // 验证必填项
                if (request.getValidate() && requiredFields.contains(fieldName)) {
                    if (newValue == null || newValue.trim().isEmpty()) {
                        fieldResult.put("status", "FAILED");
                        fieldResult.put("message", "字段为必填项");
                        editResults.add(fieldResult);
                        continue;
                    }
                }

                // 验证数据类型
                String validationMessage = validateFieldValue(newValue, fieldDef);
                if (validationMessage != null) {
                    fieldResult.put("status", "FAILED");
                    fieldResult.put("message", validationMessage);
                    editResults.add(fieldResult);
                    continue;
                }

                // 获取原值
                String oldValue = null;
                LedgerDataDetail existingDetail = detailMap.get(fieldName);
                if (existingDetail != null) {
                    oldValue = existingDetail.getFieldValue();
                }

                // 如果值没有变化，跳过
                if (Objects.equals(oldValue, newValue)) {
                    fieldResult.put("status", "SKIPPED");
                    fieldResult.put("message", "值未变化");
                    editResults.add(fieldResult);
                    continue;
                }

                // 更新或创建字段详情
                if (existingDetail != null) {
                    existingDetail.setFieldValue(newValue);
                    existingDetail.setOriginalValue(newValue);
                    existingDetail.setIsEmpty(newValue == null || newValue.trim().isEmpty());
                    existingDetail.setIsValid(true);
                    existingDetail.setValidationMessage(null);
                    existingDetail.setUpdatedTime(LocalDateTime.now());
                    ledgerDataDetailRepository.save(existingDetail);
                } else {
                    LedgerDataDetail newDetail = new LedgerDataDetail();
                    newDetail.setDataId(request.getDataId());
                    newDetail.setFieldName(fieldName);
                    newDetail.setFieldValue(newValue);
                    newDetail.setOriginalValue(newValue);
                    newDetail.setIsEmpty(newValue == null || newValue.trim().isEmpty());
                    newDetail.setIsValid(true);
                    newDetail.setSortOrder(fieldDef.getSortOrder());
                    newDetail.setCreatedTime(LocalDateTime.now());
                    newDetail.setUpdatedTime(LocalDateTime.now());
                    ledgerDataDetailRepository.save(newDetail);
                }

                // 记录编辑历史
                saveEditHistory(ledgerData, fieldName, oldValue, newValue,
                        request.getEditReason(), userId, userName, ipAddress,
                        "PASS", null);

                fieldResult.put("status", "SUCCESS");
                fieldResult.put("oldValue", oldValue);
                fieldResult.put("message", "更新成功");

            } catch (Exception e) {
                fieldResult.put("status", "ERROR");
                fieldResult.put("message", "更新失败: " + e.getMessage());
                log.error("更新字段失败: {}", fieldName, e);
            }

            editResults.add(fieldResult);
        }

        // 更新台账主数据的更新时间
        ledgerData.setUpdatedTime(LocalDateTime.now());
        ledgerData.setUpdatedBy(userId);
        ledgerDataRepository.save(ledgerData);

        // 重新计算验证状态
        updateValidationStatus(ledgerData);

        result.put("dataId", request.getDataId());
        result.put("editResults", editResults);
        result.put("successCount", editResults.stream().filter(r -> "SUCCESS".equals(r.get("status"))).count());
        result.put("failedCount", editResults.stream().filter(r -> "FAILED".equals(r.get("status"))).count());
        result.put("totalCount", editResults.size());

        log.info("台账数据编辑完成，数据ID: {}，成功: {}，失败: {}",
                request.getDataId(), result.get("successCount"), result.get("failedCount"));

        return result;
    }

    /**
     * 批量编辑台账数据
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batchEditLedgerData(LedgerDataBatchEditRequest request, String ipAddress) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> dataResults = new ArrayList<>();

        int successCount = 0;
        int failedCount = 0;

        for (Long dataId : request.getDataIds()) {
            Map<String, Object> dataResult = new HashMap<>();
            dataResult.put("dataId", dataId);

            try {
                // 为每条数据创建编辑请求
                LedgerDataEditRequest editRequest = new LedgerDataEditRequest();
                editRequest.setDataId(dataId);
                editRequest.setFieldValues(request.getFieldValues());
                editRequest.setEditReason(request.getEditReason());
                editRequest.setValidate(request.getValidate());

                Map<String, Object> editResult = editLedgerData(editRequest, ipAddress);

                dataResult.put("status", "SUCCESS");
                dataResult.put("result", editResult);
                successCount++;

            } catch (Exception e) {
                dataResult.put("status", "FAILED");
                dataResult.put("message", e.getMessage());
                failedCount++;
                log.error("批量编辑失败，数据ID: {}", dataId, e);
            }

            dataResults.add(dataResult);
        }

        result.put("dataResults", dataResults);
        result.put("successCount", successCount);
        result.put("failedCount", failedCount);
        result.put("totalCount", request.getDataIds().size());

        log.info("台账数据批量编辑完成，总数: {}，成功: {}，失败: {}",
                request.getDataIds().size(), successCount, failedCount);

        return result;
    }

    /**
     * 删除台账数据（逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> deleteLedgerData(LedgerDataDeleteRequest request, String ipAddress) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> deleteResults = new ArrayList<>();

        Long userId = getCurrentUserId();
        String userName = getCurrentUserName();

        for (Long dataId : request.getDataIds()) {
            Map<String, Object> dataResult = new HashMap<>();
            dataResult.put("dataId", dataId);

            try {
                LedgerData ledgerData = ledgerDataRepository.findById(dataId)
                        .orElseThrow(() -> new RuntimeException("台账数据不存在: " + dataId));

                // 如果数据已被删除
                if (ledgerData.getDeleted()) {
                    dataResult.put("status", "SKIPPED");
                    dataResult.put("message", "数据已被删除");
                    deleteResults.add(dataResult);
                    continue;
                }

                if (request.getPermanentDelete()) {
                    // 永久删除（物理删除）
                    if (request.getForceDelete() || hasAdminPermission()) {
                        // 先记录删除历史
                        recordDeleteHistory(ledgerData, request.getDeleteReason(), userId, userName, ipAddress);

                        // 删除明细数据
                        List<LedgerDataDetail> details = ledgerDataDetailRepository.findByDataId(dataId);
                        ledgerDataDetailRepository.deleteAll(details);

                        // 删除主数据
                        ledgerDataRepository.delete(ledgerData);

                        dataResult.put("status", "PERMANENTLY_DELETED");
                        dataResult.put("message", "数据已永久删除");
                    } else {
                        throw new RuntimeException("没有永久删除权限");
                    }
                } else {
                    // 逻辑删除
                    ledgerData.setDeleted(true);
                    ledgerData.setDataStatus("DELETED");
                    ledgerData.setUpdatedTime(LocalDateTime.now());
                    ledgerData.setUpdatedBy(userId);
                    ledgerDataRepository.save(ledgerData);

                    // 记录删除历史
                    recordDeleteHistory(ledgerData, request.getDeleteReason(), userId, userName, ipAddress);

                    dataResult.put("status", "DELETED");
                    dataResult.put("message", "数据已逻辑删除");
                }

                deleteResults.add(dataResult);

            } catch (Exception e) {
                dataResult.put("status", "FAILED");
                dataResult.put("message", e.getMessage());
                deleteResults.add(dataResult);
                log.error("删除台账数据失败，数据ID: {}", dataId, e);
            }
        }

        result.put("deleteResults", deleteResults);
        result.put("successCount", deleteResults.stream().filter(r ->
                "DELETED".equals(r.get("status")) || "PERMANENTLY_DELETED".equals(r.get("status"))).count());
        result.put("failedCount", deleteResults.stream().filter(r -> "FAILED".equals(r.get("status"))).count());
        result.put("totalCount", request.getDataIds().size());

        log.info("台账数据删除完成，总数: {}，成功: {}，失败: {}",
                request.getDataIds().size(), result.get("successCount"), result.get("failedCount"));

        return result;
    }

    /**
     * 恢复已删除的数据
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> restoreLedgerData(List<Long> dataIds, String restoreReason, String ipAddress) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> restoreResults = new ArrayList<>();

        Long userId = getCurrentUserId();
        String userName = getCurrentUserName();

        for (Long dataId : dataIds) {
            Map<String, Object> dataResult = new HashMap<>();
            dataResult.put("dataId", dataId);

            try {
                LedgerData ledgerData = ledgerDataRepository.findById(dataId)
                        .orElseThrow(() -> new RuntimeException("台账数据不存在: " + dataId));

                if (!ledgerData.getDeleted()) {
                    dataResult.put("status", "SKIPPED");
                    dataResult.put("message", "数据未被删除");
                    restoreResults.add(dataResult);
                    continue;
                }

                // 恢复数据
                ledgerData.setDeleted(false);
                ledgerData.setDataStatus("ACTIVE");
                ledgerData.setUpdatedTime(LocalDateTime.now());
                ledgerData.setUpdatedBy(userId);
                ledgerDataRepository.save(ledgerData);

                // 记录恢复历史
                saveEditHistory(ledgerData, "ALL", "DELETED", "ACTIVE",
                        restoreReason, userId, userName, ipAddress,
                        "PASS", "数据恢复");

                dataResult.put("status", "RESTORED");
                dataResult.put("message", "数据已恢复");
                restoreResults.add(dataResult);

            } catch (Exception e) {
                dataResult.put("status", "FAILED");
                dataResult.put("message", e.getMessage());
                restoreResults.add(dataResult);
                log.error("恢复台账数据失败，数据ID: {}", dataId, e);
            }
        }

        result.put("restoreResults", restoreResults);
        result.put("successCount", restoreResults.stream().filter(r -> "RESTORED".equals(r.get("status"))).count());
        result.put("failedCount", restoreResults.stream().filter(r -> "FAILED".equals(r.get("status"))).count());
        result.put("totalCount", dataIds.size());

        return result;
    }

    /**
     * 获取编辑历史
     */
    public Map<String, Object> getEditHistory(Long dataId, Integer page, Integer size) {
        Map<String, Object> result = new HashMap<>();

        List<LedgerEditHistory> historyList = ledgerEditHistoryRepository.findByDataId(dataId);

        // 转换历史记录
        List<Map<String, Object>> historyData = historyList.stream()
                .map(this::convertHistoryToMap)
                .collect(Collectors.toList());

        result.put("dataId", dataId);
        result.put("history", historyData);
        result.put("totalCount", historyData.size());

        return result;
    }

    /**
     * 验证字段值
     */
    private String validateFieldValue(String value, TemplateField fieldDef) {
        if (value == null || value.trim().isEmpty()) {
            return null; // 空值不验证
        }

        try {
            switch (fieldDef.getFieldType()) {
                case "NUMBER":
                    // 验证数字
                    try {
                        Double.parseDouble(value);
                    } catch (NumberFormatException e) {
                        return "值必须是数字";
                    }
                    break;

                case "DATE":
                    // 这里可以添加日期格式验证
                    // 例如：验证是否符合 yyyy-MM-dd 格式
                    if (!value.matches("\\d{4}-\\d{2}-\\d{2}")) {
                        return "日期格式必须为 yyyy-MM-dd";
                    }
                    break;

                case "BOOLEAN":
                    // 验证布尔值
                    if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                        return "值必须是 true 或 false";
                    }
                    break;

                case "STRING":
                    // 字符串长度验证
                    if (fieldDef.getFieldLength() != null && value.length() > fieldDef.getFieldLength()) {
                        return "长度不能超过 " + fieldDef.getFieldLength() + " 个字符";
                    }
                    break;

                default:
                    // 其他类型不验证
            }

            // 验证规则（正则表达式）
            if (StringUtils.hasText(fieldDef.getValidationRule())) {
                if (!value.matches(fieldDef.getValidationRule())) {
                    return "值不符合验证规则";
                }
            }

        } catch (Exception e) {
            return "验证失败: " + e.getMessage();
        }

        return null; // 验证通过
    }

    /**
     * 更新验证状态
     */
    private void updateValidationStatus(LedgerData ledgerData) {
        List<LedgerDataDetail> details = ledgerDataDetailRepository.findByDataId(ledgerData.getId());

        long invalidCount = details.stream()
                .filter(detail -> Boolean.FALSE.equals(detail.getIsValid()))
                .count();

        if (invalidCount > 0) {
            ledgerData.setValidationStatus("INVALID");
            // 收集错误信息
            String errors = details.stream()
                    .filter(detail -> Boolean.FALSE.equals(detail.getIsValid()))
                    .map(detail -> detail.getFieldName() + ": " + detail.getValidationMessage())
                    .collect(Collectors.joining("; "));
            ledgerData.setValidationErrors(errors);
        } else {
            ledgerData.setValidationStatus("VALID");
            ledgerData.setValidationErrors(null);
        }

        ledgerDataRepository.save(ledgerData);
    }

    /**
     * 保存编辑历史
     */
    private void saveEditHistory(LedgerData ledgerData, String fieldName, String oldValue,
                                 String newValue, String editReason, Long editedBy,
                                 String editedByName, String ipAddress,
                                 String validationResult, String validationMessage) {

        LedgerEditHistory history = new LedgerEditHistory();
        history.setDataId(ledgerData.getId());
        history.setFieldName(fieldName);
        history.setOldValue(oldValue);
        history.setNewValue(newValue);
        history.setEditType("UPDATE");
        history.setEditReason(editReason);
        history.setEditedBy(editedBy);
        history.setEditedByName(editedByName);
        history.setValidationResult(validationResult);
        history.setValidationMessage(validationMessage);
        history.setIpAddress(ipAddress);
        history.setEditTime(LocalDateTime.now());
        history.setDeleted(false);

        ledgerEditHistoryRepository.save(history);
    }

    /**
     * 记录删除历史
     */
    private void recordDeleteHistory(LedgerData ledgerData, String deleteReason,
                                     Long deletedBy, String deletedByName, String ipAddress) {

        LedgerEditHistory history = new LedgerEditHistory();
        history.setDataId(ledgerData.getId());
        history.setFieldName("ALL");
        history.setOldValue("ACTIVE");
        history.setNewValue("DELETED");
        history.setEditType("DELETE");
        history.setEditReason(deleteReason);
        history.setEditedBy(deletedBy);
        history.setEditedByName(deletedByName);
        history.setIpAddress(ipAddress);
        history.setEditTime(LocalDateTime.now());
        history.setDeleted(false);

        ledgerEditHistoryRepository.save(history);
    }

    /**
     * 转换历史记录为Map
     */
    private Map<String, Object> convertHistoryToMap(LedgerEditHistory history) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", history.getId());
        map.put("dataId", history.getDataId());
        map.put("fieldName", history.getFieldName());
        map.put("oldValue", history.getOldValue());
        map.put("newValue", history.getNewValue());
        map.put("editType", history.getEditType());
        map.put("editReason", history.getEditReason());
        map.put("editedBy", history.getEditedBy());
        map.put("editedByName", history.getEditedByName());
        map.put("validationResult", history.getValidationResult());
        map.put("validationMessage", history.getValidationMessage());
        map.put("ipAddress", history.getIpAddress());
        map.put("editTime", history.getEditTime());
        return map;
    }

    /**
     * 获取当前用户ID
     */
    private Long getCurrentUserId() {
        return securityUtil.getCurrentUserId();
    }

    /**
     * 获取当前用户名
     */
    private String getCurrentUserName() {
        return securityUtil.getCurrentUserNickname();
    }

    /**
     * 检查是否有管理员权限
     */
    private boolean hasAdminPermission() {
        return securityUtil.isAdmin();
    }
}