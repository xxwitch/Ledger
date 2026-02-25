package com.example.ledger.service;

/**
 * @author 霜月
 * @create 2025/12/21 10:55
 */

import com.example.ledger.dto.request.LedgerDataMultiFieldQueryRequest;
import com.example.ledger.dto.request.LedgerDataDynamicQueryRequest;
import com.example.ledger.dto.request.LedgerDataQueryRequest;
import com.example.ledger.dto.request.LedgerDataAdvancedQueryRequest;
import com.example.ledger.dto.response.LedgerDataDynamicPageResponse;
import com.example.ledger.dto.response.LedgerDataDynamicResponse;
import com.example.ledger.dto.response.LedgerDataPageResponse;
import com.example.ledger.dto.response.LedgerDataResponse;
import com.example.ledger.entity.*;
import com.example.ledger.repository.*;
import com.example.ledger.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.persistence.criteria.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerDataQueryService {

    private final LedgerDataRepository ledgerDataRepository;
    private final LedgerDataDetailRepository ledgerDataDetailRepository;
    private final LedgerUploadRepository ledgerUploadRepository;
    private final LedgerTemplateRepository ledgerTemplateRepository;
    private final TemplateFieldRepository templateFieldRepository;
    private final SecurityUtil securityUtil;

    /**
     * 获取字段的存储列名（用于查询）- 修复带下划线的字段
     */
    private String getStoredFieldName(String fieldName, Long templateId) {
        if (fieldName == null || templateId == null) {
            return fieldName;
        }

        // 查找模板字段定义
        List<TemplateField> fields = templateFieldRepository.findByTemplateIdAndDeletedFalse(templateId);

        // 先尝试精确匹配（原逻辑）
        for (TemplateField field : fields) {
            if (fieldName.equals(field.getFieldName())) {
                return buildStoredFieldName(field);
            }
        }

        // 如果精确匹配失败，尝试忽略尾部下划线的匹配
        for (TemplateField field : fields) {
            // 移除字段名尾部的下划线进行比较
            String normalizedTemplateFieldName = removeTrailingUnderscores(field.getFieldName());
            String normalizedSearchFieldName = removeTrailingUnderscores(fieldName);

            if (normalizedSearchFieldName.equals(normalizedTemplateFieldName)) {
                return buildStoredFieldName(field);
            }
        }

        // 如果还是找不到，尝试模糊匹配（原逻辑）
        for (TemplateField field : fields) {
            if (field.getFieldName().contains(fieldName) || fieldName.contains(field.getFieldName())) {
                return buildStoredFieldName(field);
            }
        }

        log.warn("未找到字段映射: fieldName={}, templateId={}", fieldName, templateId);
        return fieldName;
    }

    /**
     * 移除字符串尾部的下划线
     */
    private String removeTrailingUnderscores(String str) {
        if (str == null) return null;

        // 移除尾部的所有下划线
        while (str.endsWith("_")) {
            str = str.substring(0, str.length() - 1);
        }
        return str;
    }

    /**
     * 构建存储字段名
     */
    private String buildStoredFieldName(TemplateField field) {
        // 根据实际存储格式构建：field_name + "_" + excel_column
        // 注意：如果field_name已以下划线结尾，会形成双下划线
        String fieldName = field.getFieldName();
        String excelColumn = field.getExcelColumn();

        // 如果field_name已以下划线结尾，直接拼接列字母
        if (fieldName.endsWith("_")) {
            return fieldName + excelColumn;
        } else {
            return fieldName + "_" + excelColumn;
        }
    }

    /**
     * 从存储的字段名中提取原始字段名
     */
    private String extractOriginalFieldName(String storedFieldName) {
        if (storedFieldName == null) {
            return null;
        }

        // 查找最后一个下划线的位置
        int lastUnderscoreIndex = storedFieldName.lastIndexOf('_');
        if (lastUnderscoreIndex > 0) {
            // 返回下划线前的部分作为原始字段名
            return storedFieldName.substring(0, lastUnderscoreIndex);
        }
        return storedFieldName;
    }

    /**
     * 基础查询 - 支持多种条件组合
     */
    @Transactional(readOnly = true)
    public LedgerDataPageResponse queryLedgerData(LedgerDataQueryRequest request) {
        // 1. 构建查询条件
        Specification<LedgerData> spec = buildQuerySpecification(request);

        // 2. 构建分页和排序
        Pageable pageable = buildPageable(request);

        // 3. 执行查询
        Page<LedgerData> dataPage = ledgerDataRepository.findAll(spec, pageable);

        // 4. 转换为响应对象
        return convertToPageResponse(dataPage, request);
    }

    /**
     * 构建查询条件 - 针对 LedgerDataQueryRequest
     */
    private Specification<LedgerData> buildQuerySpecification(LedgerDataQueryRequest request) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 基础条件
            predicates.add(criteriaBuilder.equal(root.get("deleted"), false));

            // 单位条件
            if (StringUtils.hasText(request.getUnitName())) {
                predicates.add(criteriaBuilder.equal(root.get("unitName"), request.getUnitName()));
            }

            // 模板条件
            if (request.getTemplateId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("templateId"), request.getTemplateId()));
            }

            // 上传批次条件
            if (request.getUploadId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("uploadId"), request.getUploadId()));
            }

            // 用户条件
            if (request.getUserId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("createdBy"), request.getUserId()));
            } else if (request.getViewOwnOnly() != null && request.getViewOwnOnly()) {
                Long userId = getCurrentUserId();
                predicates.add(criteriaBuilder.equal(root.get("createdBy"), userId));
            }

            // 数据状态条件
            if (StringUtils.hasText(request.getDataStatus())) {
                predicates.add(criteriaBuilder.equal(root.get("dataStatus"), request.getDataStatus()));
            }

            // 验证状态条件
            if (StringUtils.hasText(request.getValidationStatus())) {
                predicates.add(criteriaBuilder.equal(root.get("validationStatus"), request.getValidationStatus()));
            }

            // 时间范围条件
            if (request.getStartTime() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdTime"), request.getStartTime()));
            }
            if (request.getEndTime() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdTime"), request.getEndTime()));
            }

            // 年份条件
            if (request.getYear() != null) {
                Expression<Integer> yearExpr = criteriaBuilder.function("YEAR", Integer.class, root.get("createdTime"));
                predicates.add(criteriaBuilder.equal(yearExpr, request.getYear()));

                // 月份条件
                if (request.getMonth() != null) {
                    Expression<Integer> monthExpr = criteriaBuilder.function("MONTH", Integer.class, root.get("createdTime"));
                    predicates.add(criteriaBuilder.equal(monthExpr, request.getMonth()));
                }
            }

            // 字段条件（通过子查询）- 修复字段名格式
            if (StringUtils.hasText(request.getFieldName()) &&
                    (StringUtils.hasText(request.getFieldValue()) || StringUtils.hasText(request.getFieldValueExact()))) {

                Subquery<Long> subquery = query.subquery(Long.class);
                Root<LedgerDataDetail> detailRoot = subquery.from(LedgerDataDetail.class);

                List<Predicate> detailPredicates = new ArrayList<>();
                detailPredicates.add(criteriaBuilder.equal(detailRoot.get("dataId"), root.get("id")));

                // 获取模板ID
                Long templateId = request.getTemplateId();
                if (templateId == null && StringUtils.hasText(request.getUnitName())) {
                    LedgerTemplate template = ledgerTemplateRepository.findByUnitNameAndDeletedFalse(request.getUnitName())
                            .orElse(null);
                    if (template != null) {
                        templateId = template.getId();
                    }
                }

                // 获取存储的字段名
                String storedFieldName = getStoredFieldName(request.getFieldName(), templateId);
                detailPredicates.add(criteriaBuilder.equal(detailRoot.get("fieldName"), storedFieldName));

                if (StringUtils.hasText(request.getFieldValueExact())) {
                    detailPredicates.add(criteriaBuilder.equal(detailRoot.get("fieldValue"), request.getFieldValueExact()));
                } else if (StringUtils.hasText(request.getFieldValue())) {
                    detailPredicates.add(criteriaBuilder.like(detailRoot.get("fieldValue"), "%" + request.getFieldValue() + "%"));
                }

                subquery.select(detailRoot.get("dataId"))
                        .where(detailPredicates.toArray(new Predicate[0]));

                predicates.add(criteriaBuilder.exists(subquery));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 构建分页和排序 - 针对 LedgerDataQueryRequest
     */
    private Pageable buildPageable(LedgerDataQueryRequest request) {
        Sort sort = Sort.by(Sort.Direction.fromString(request.getSortOrder()), request.getSortField());
        return PageRequest.of(request.getPage() - 1, request.getSize(), sort);
    }

    /**
     * 多字段查询 - 支持台账特定字段查询
     */
    @Transactional(readOnly = true)
    public LedgerDataPageResponse queryByMultiFields(LedgerDataMultiFieldQueryRequest request) {
        try {
            log.info("接收到多字段查询请求: {}", request);

            // 1. 构建查询条件
            Specification<LedgerData> spec = buildMultiFieldSpecification(request);

            // 2. 构建分页和排序
            Pageable pageable = buildPageableForMultiFields(request);

            // 3. 执行查询
            Page<LedgerData> dataPage = ledgerDataRepository.findAll(spec, pageable);

            // 4. 转换为响应对象
            return convertToPageResponse(dataPage, request);
        } catch (Exception e) {
            log.error("多字段查询失败", e);
            throw new RuntimeException("多字段查询失败: " + e.getMessage());
        }
    }

    /**
     * 高级查询 - 支持字段条件
     */
    @Transactional(readOnly = true)
    public LedgerDataPageResponse advancedQuery(LedgerDataAdvancedQueryRequest request) {
        try {
            log.info("接收到高级查询请求: {}", request);

            // 1. 先根据基本条件查询数据ID
            List<Long> dataIds = findDataIdsByBasicConditions(request);

            // 2. 根据字段条件进一步筛选
            if (request.getConditions() != null && !request.getConditions().isEmpty()) {
                dataIds = filterByFieldConditions(dataIds, request);
            }

            // 3. 分页查询数据
            return queryDataByIds(dataIds, request.getPage(), request.getSize());
        } catch (Exception e) {
            log.error("高级查询失败", e);
            throw new RuntimeException("高级查询失败: " + e.getMessage());
        }
    }

    /**
     * 创建字段查询条件 - 修复字段名格式问题
     */
    private Predicate createFieldPredicate(CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder,
                                           Root<LedgerData> root, String originalFieldName,
                                           String fieldValue, Boolean fuzzySearch,
                                           Long templateId, String unitName) {
        Subquery<Long> subquery = query.subquery(Long.class);
        Root<LedgerDataDetail> detailRoot = subquery.from(LedgerDataDetail.class);

        List<Predicate> detailPredicates = new ArrayList<>();
        detailPredicates.add(criteriaBuilder.equal(detailRoot.get("dataId"), root.get("id")));

        // 获取模板ID（如果未提供）
        if (templateId == null && StringUtils.hasText(unitName)) {
            LedgerTemplate template = ledgerTemplateRepository.findByUnitNameAndDeletedFalse(unitName)
                    .orElse(null);
            if (template != null) {
                templateId = template.getId();
            }
        }

        // 获取存储的字段名
        String storedFieldName = getStoredFieldName(originalFieldName, templateId);
        if (storedFieldName != null) {
            // 使用精确匹配存储的字段名
            detailPredicates.add(criteriaBuilder.equal(detailRoot.get("fieldName"), storedFieldName));
        } else {
            // 如果没有找到模板字段，使用原始字段名（兼容性）
            detailPredicates.add(criteriaBuilder.like(detailRoot.get("fieldName"), originalFieldName + "_%"));
        }

        if (Boolean.TRUE.equals(fuzzySearch)) {
            // 模糊查询
            detailPredicates.add(criteriaBuilder.like(detailRoot.get("fieldValue"), "%" + fieldValue.trim() + "%"));
        } else {
            // 精确查询
            detailPredicates.add(criteriaBuilder.equal(detailRoot.get("fieldValue"), fieldValue.trim()));
        }

        subquery.select(detailRoot.get("dataId"))
                .where(detailPredicates.toArray(new Predicate[0]));

        return criteriaBuilder.exists(subquery);
    }

    /**
     * 构建分页和排序
     */
    private Pageable buildPageableForMultiFields(LedgerDataMultiFieldQueryRequest request) {
        // 确定排序字段和方向
        String sortField = request.getSortField();
        Sort.Direction direction = Sort.Direction.fromString(request.getSortOrder());

        // 如果按uploadId排序，同时按rowNumber排序
        if ("uploadId".equals(sortField)) {
            if (Sort.Direction.ASC.equals(direction)) {
                // 正序：先按uploadId升序，再按rowNumber升序
                return PageRequest.of(
                        request.getPage() - 1,
                        request.getSize(),
                        Sort.by(Sort.Order.asc("uploadId"), Sort.Order.asc("rowNumber"))
                );
            } else {
                // 倒序：先按uploadId降序，再按rowNumber降序
                return PageRequest.of(
                        request.getPage() - 1,
                        request.getSize(),
                        Sort.by(Sort.Order.desc("uploadId"), Sort.Order.desc("rowNumber"))
                );
            }
        } else {
            // 其他字段按普通方式排序
            return PageRequest.of(
                    request.getPage() - 1,
                    request.getSize(),
                    Sort.by(direction, sortField)
            );
        }
    }

    /**
     * 转换分页数据为响应对象 - 针对 LedgerDataQueryRequest
     */
    private LedgerDataPageResponse convertToPageResponse(Page<LedgerData> dataPage, LedgerDataQueryRequest request) {
        // 获取数据详情
        List<LedgerData> dataList = dataPage.getContent();
        Map<Long, List<LedgerDataDetail>> detailsMap = getDataDetails(dataList);

        // 转换为响应列表
        List<LedgerDataResponse> responses = dataList.stream()
                .map(data -> convertToResponse(data, detailsMap.get(data.getId())))
                .collect(Collectors.toList());

        // 获取字段名称列表
        List<String> fieldNames = getFieldNames(request);

        // 获取单位名称列表
        List<String> unitNames = getUnitNames();

        // 构建统计信息
        LedgerDataPageResponse.QueryStats stats = buildQueryStats(request);

        return LedgerDataPageResponse.builder()
                .data(responses)
                .currentPage(dataPage.getNumber() + 1)
                .pageSize(dataPage.getSize())
                .totalElements(dataPage.getTotalElements())
                .totalPages(dataPage.getTotalPages())
                .hasPrevious(dataPage.hasPrevious())
                .hasNext(dataPage.hasNext())
                .fieldNames(fieldNames)
                .unitNames(unitNames)
                .stats(stats)
                .build();
    }

    /**
     * 转换分页数据为响应对象 - 针对 LedgerDataMultiFieldQueryRequest
     */
    private LedgerDataPageResponse convertToPageResponse(Page<LedgerData> dataPage, LedgerDataMultiFieldQueryRequest request) {
        // 获取数据详情
        List<LedgerData> dataList = dataPage.getContent();
        Map<Long, List<LedgerDataDetail>> detailsMap = getDataDetails(dataList);

        // 转换为响应列表
        List<LedgerDataResponse> responses = dataList.stream()
                .map(data -> convertToResponse(data, detailsMap.get(data.getId())))
                .collect(Collectors.toList());

        // 获取字段名称列表
        List<String> fieldNames = getFieldNames(request);

        // 获取单位名称列表
        List<String> unitNames = getUnitNames();

        // 构建统计信息
        LedgerDataPageResponse.QueryStats stats = buildQueryStats(request);

        return LedgerDataPageResponse.builder()
                .data(responses)
                .currentPage(dataPage.getNumber() + 1)
                .pageSize(dataPage.getSize())
                .totalElements(dataPage.getTotalElements())
                .totalPages(dataPage.getTotalPages())
                .hasPrevious(dataPage.hasPrevious())
                .hasNext(dataPage.hasNext())
                .fieldNames(fieldNames)
                .unitNames(unitNames)
                .stats(stats)
                .build();
    }

    /**
     * 获取字段名称列表 - 针对 LedgerDataQueryRequest
     */
    private List<String> getFieldNames(LedgerDataQueryRequest request) {
        if (request.getTemplateId() != null) {
            return templateFieldRepository.findByTemplateIdAndDeletedFalse(request.getTemplateId()).stream()
                    .map(TemplateField::getFieldName)
                    .collect(Collectors.toList());
        } else if (StringUtils.hasText(request.getUnitName())) {
            LedgerTemplate template = ledgerTemplateRepository.findByUnitNameAndDeletedFalse(request.getUnitName())
                    .orElse(null);
            if (template != null) {
                return templateFieldRepository.findByTemplateIdAndDeletedFalse(template.getId()).stream()
                        .map(TemplateField::getFieldName)
                        .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }

    /**
     * 获取字段名称列表 - 针对 LedgerDataMultiFieldQueryRequest
     */
    private List<String> getFieldNames(LedgerDataMultiFieldQueryRequest request) {
        if (request.getTemplateId() != null) {
            return templateFieldRepository.findByTemplateIdAndDeletedFalse(request.getTemplateId()).stream()
                    .map(TemplateField::getFieldName)
                    .collect(Collectors.toList());
        } else if (StringUtils.hasText(request.getUnitName())) {
            LedgerTemplate template = ledgerTemplateRepository.findByUnitNameAndDeletedFalse(request.getUnitName())
                    .orElse(null);
            if (template != null) {
                return templateFieldRepository.findByTemplateIdAndDeletedFalse(template.getId()).stream()
                        .map(TemplateField::getFieldName)
                        .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }

    /**
     * 构建查询统计 - 针对 LedgerDataQueryRequest
     */
    private LedgerDataPageResponse.QueryStats buildQueryStats(LedgerDataQueryRequest request) {
        return LedgerDataPageResponse.QueryStats.builder()
                .totalRecords(0L)
                .activeRecords(0L)
                .invalidRecords(0L)
                .unitDistribution(new HashMap<>())
                .statusDistribution(new HashMap<>())
                .build();
    }

    /**
     * 构建查询统计 - 针对 LedgerDataMultiFieldQueryRequest
     */
    private LedgerDataPageResponse.QueryStats buildQueryStats(LedgerDataMultiFieldQueryRequest request) {
        return LedgerDataPageResponse.QueryStats.builder()
                .totalRecords(0L)
                .activeRecords(0L)
                .invalidRecords(0L)
                .unitDistribution(new HashMap<>())
                .statusDistribution(new HashMap<>())
                .build();
    }

    /**
     * 获取数据详情
     */
    private Map<Long, List<LedgerDataDetail>> getDataDetails(List<LedgerData> dataList) {
        if (dataList.isEmpty()) {
            return new HashMap<>();
        }

        List<Long> dataIds = dataList.stream()
                .map(LedgerData::getId)
                .collect(Collectors.toList());

        List<LedgerDataDetail> details = ledgerDataDetailRepository.findByDataIdIn(dataIds);

        return details.stream()
                .collect(Collectors.groupingBy(LedgerDataDetail::getDataId));
    }

    /**
     * 转换单个数据为响应对象 - 修复字段名格式问题
     */
    private LedgerDataResponse convertToResponse(LedgerData data, List<LedgerDataDetail> details) {
        LedgerDataResponse response = new LedgerDataResponse();

        response.setId(data.getId());
        response.setUploadId(data.getUploadId());
        response.setTemplateId(data.getTemplateId());
        response.setUnitName(data.getUnitName());
        response.setRowNumber(data.getRowNumber());
        response.setDataStatus(data.getDataStatus());
        response.setValidationStatus(data.getValidationStatus());
        response.setValidationErrors(data.getValidationErrors());
        response.setCreatedBy(data.getCreatedBy());
        response.setCreatedTime(data.getCreatedTime());
        response.setUpdatedTime(data.getUpdatedTime());

        // 获取上传信息
        if (data.getUploadId() != null) {
            LedgerUpload upload = ledgerUploadRepository.findById(data.getUploadId()).orElse(null);
            if (upload != null) {
                response.setUploadNo(upload.getUploadNo());
            }
        }

        // 获取模板信息
        if (data.getTemplateId() != null) {
            LedgerTemplate template = ledgerTemplateRepository.findById(data.getTemplateId()).orElse(null);
            if (template != null) {
                response.setTemplateName(template.getTemplateName());
            }
        }

        // 获取用户信息
        if (data.getCreatedBy() != null) {
            response.setCreatedByName("用户" + data.getCreatedBy());
        }

        // 处理字段数据 - 修复字段名格式问题
        if (details != null) {
            Map<String, Object> fieldData = new LinkedHashMap<>();
            Map<String, Boolean> fieldStatus = new LinkedHashMap<>();
            Map<String, String> fieldValidation = new LinkedHashMap<>();

            int emptyCount = 0;
            int invalidCount = 0;

            // 获取模板字段定义，用于将存储字段名映射回原始字段名
            List<TemplateField> templateFields = templateFieldRepository
                    .findByTemplateIdAndDeletedFalse(data.getTemplateId());

            // 创建存储字段名到原始字段名的映射
            Map<String, String> storedToOriginalFieldName = new HashMap<>();
            for (TemplateField field : templateFields) {
                String storedName = field.getFieldName() + "_" + field.getExcelColumn();
                storedToOriginalFieldName.put(storedName, field.getFieldName());
            }

            for (LedgerDataDetail detail : details) {
                // 将存储字段名转换为原始字段名用于显示
                String originalFieldName = storedToOriginalFieldName.getOrDefault(
                        detail.getFieldName(),
                        extractOriginalFieldName(detail.getFieldName()));

                fieldData.put(originalFieldName, detail.getFieldValue());
                fieldStatus.put(originalFieldName, detail.getIsValid());

                if (detail.getValidationMessage() != null) {
                    fieldValidation.put(originalFieldName, detail.getValidationMessage());
                }

                if (detail.getIsEmpty()) {
                    emptyCount++;
                }
                if (!detail.getIsValid()) {
                    invalidCount++;
                }
            }

            response.setFieldData(fieldData);
            response.setFieldStatus(fieldStatus);
            response.setFieldValidation(fieldValidation);
            response.setEmptyFieldCount(emptyCount);
            response.setInvalidFieldCount(invalidCount);
            response.setTotalFieldCount(details.size());
        }

        return response;
    }

    /**
     * 查询自己的数据
     */
    @Transactional(readOnly = true)
    public LedgerDataPageResponse queryOwnData(Integer page, Integer size) {
        Long userId = getCurrentUserId();

        LedgerDataQueryRequest request = new LedgerDataQueryRequest();
        request.setUserId(userId);
        request.setViewOwnOnly(true);
        request.setPage(page);
        request.setSize(size);

        return queryLedgerData(request);
    }

    /**
     * 按单位查询数据
     */
    @Transactional(readOnly = true)
    public LedgerDataPageResponse queryByUnit(String unitName, Integer page, Integer size) {
        LedgerDataQueryRequest request = new LedgerDataQueryRequest();
        request.setUnitName(unitName);
        request.setPage(page);
        request.setSize(size);

        return queryLedgerData(request);
    }

    /**
     * 查询无效数据（验证失败）
     */
    @Transactional(readOnly = true)
    public LedgerDataPageResponse queryInvalidData(Integer page, Integer size) {
        LedgerDataQueryRequest request = new LedgerDataQueryRequest();
        request.setValidationStatus("INVALID");
        request.setPage(page);
        request.setSize(size);

        return queryLedgerData(request);
    }

    /**
     * 统计查询
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDataStatistics(String unitName) {
        Map<String, Object> stats = new HashMap<>();

        if (StringUtils.hasText(unitName)) {
            // 单位统计
            Long total = ledgerDataRepository.countByUnitNameAndDeletedFalse(unitName);
            Long valid = ledgerDataRepository.countByUnitNameAndValidationStatus(unitName, "VALID");
            Long invalid = ledgerDataRepository.countByUnitNameAndValidationStatus(unitName, "INVALID");

            stats.put("unitName", unitName);
            stats.put("totalRecords", total);
            stats.put("validRecords", valid);
            stats.put("invalidRecords", invalid);
            stats.put("validRate", total > 0 ? (double) valid / total : 0);

        } else {
            // 全局统计（管理员权限）
            List<Object[]> unitStats = ledgerDataRepository.countByUnitGroup();
            Map<String, Map<String, Long>> unitData = new HashMap<>();

            for (Object[] row : unitStats) {
                String uName = (String) row[0];
                Long total = (Long) row[1];
                Long valid = (Long) row[2];
                Long invalid = (Long) row[3];

                Map<String, Long> unitStat = new HashMap<>();
                unitStat.put("total", total);
                unitStat.put("valid", valid);
                unitStat.put("invalid", invalid);
                unitData.put(uName, unitStat);
            }

            stats.put("unitStats", unitData);
            stats.put("totalUnits", unitData.size());

            // 按用户统计
            List<Object[]> userStats = ledgerDataRepository.countByUserGroup();
            Map<String, Long> userData = userStats.stream()
                    .collect(Collectors.toMap(
                            row -> (String) row[0],
                            row -> (Long) row[1]
                    ));
            stats.put("userStats", userData);
        }

        return stats;
    }

    /**
     * 根据基本条件查询数据ID
     */
    private List<Long> findDataIdsByBasicConditions(LedgerDataAdvancedQueryRequest request) {
        Specification<LedgerData> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("deleted"), false));

            if (StringUtils.hasText(request.getUnitName())) {
                predicates.add(criteriaBuilder.equal(root.get("unitName"), request.getUnitName()));
            }

            if (request.getTemplateId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("templateId"), request.getTemplateId()));
            }

            // 时间范围条件
            if (StringUtils.hasText(request.getStartTime()) && StringUtils.hasText(request.getEndTime())) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                LocalDateTime start = LocalDateTime.parse(request.getStartTime(), formatter);
                LocalDateTime end = LocalDateTime.parse(request.getEndTime(), formatter);

                if ("updatedTime".equals(request.getTimeField())) {
                    predicates.add(criteriaBuilder.between(root.get("updatedTime"), start, end));
                } else {
                    predicates.add(criteriaBuilder.between(root.get("createdTime"), start, end));
                }
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        return ledgerDataRepository.findAll(spec).stream()
                .map(LedgerData::getId)
                .collect(Collectors.toList());
    }

    /**
     * 根据字段条件筛选数据ID - 修复字段名格式问题
     */
    private List<Long> filterByFieldConditions(List<Long> dataIds, LedgerDataAdvancedQueryRequest request) {
        if (dataIds.isEmpty()) {
            return dataIds;
        }

        // 获取模板信息
        Long templateId = request.getTemplateId();
        if (templateId == null && StringUtils.hasText(request.getUnitName())) {
            LedgerTemplate template = ledgerTemplateRepository.findByUnitNameAndDeletedFalse(request.getUnitName())
                    .orElse(null);
            if (template != null) {
                templateId = template.getId();
            }
        }

        // 创建原始字段名到存储字段名的映射
        Map<String, String> originalToStoredFieldName = new HashMap<>();
        if (templateId != null) {
            List<TemplateField> fields = templateFieldRepository.findByTemplateIdAndDeletedFalse(templateId);
            for (TemplateField field : fields) {
                String storedName = field.getFieldName() + "_" + field.getExcelColumn();
                originalToStoredFieldName.put(field.getFieldName(), storedName);
            }
        }

        // 分批处理，避免SQL过长
        List<Long> result = new ArrayList<>();
        int batchSize = 1000;

        for (int i = 0; i < dataIds.size(); i += batchSize) {
            List<Long> batchIds = dataIds.subList(i, Math.min(i + batchSize, dataIds.size()));

            List<LedgerDataDetail> details = ledgerDataDetailRepository.findByDataIdIn(batchIds);
            Map<Long, Map<String, String>> dataFieldMap = details.stream()
                    .collect(Collectors.groupingBy(
                            LedgerDataDetail::getDataId,
                            Collectors.toMap(
                                    LedgerDataDetail::getFieldName,
                                    detail -> detail.getFieldValue() != null ? detail.getFieldValue() : ""
                            )
                    ));

            // 根据条件筛选
            for (Long dataId : batchIds) {
                Map<String, String> fieldValues = dataFieldMap.get(dataId);
                if (fieldValues == null) {
                    continue;
                }

                boolean match = Boolean.TRUE.equals(request.getMatchAll()) ?
                        matchAllConditions(fieldValues, request.getConditions(), originalToStoredFieldName) :
                        matchAnyCondition(fieldValues, request.getConditions(), originalToStoredFieldName);

                if (match) {
                    result.add(dataId);
                }
            }
        }

        return result;
    }

    private boolean matchAllConditions(Map<String, String> fieldValues, Map<String, Object> conditions,
                                       Map<String, String> originalToStoredFieldName) {
        for (Map.Entry<String, Object> entry : conditions.entrySet()) {
            String originalFieldName = entry.getKey();
            Object expectedValue = entry.getValue();

            // 获取存储的字段名
            String storedFieldName = originalToStoredFieldName.get(originalFieldName);
            if (storedFieldName == null) {
                // 如果找不到映射，尝试使用原始字段名加上通配符
                storedFieldName = originalFieldName + "_";
            }

            // 查找匹配的字段
            boolean found = false;
            for (Map.Entry<String, String> fieldEntry : fieldValues.entrySet()) {
                String actualStoredFieldName = fieldEntry.getKey();
                String actualValue = fieldEntry.getValue();

                // 检查是否匹配存储字段名
                if (actualStoredFieldName.startsWith(storedFieldName)) {
                    if (matchesCondition(actualValue, expectedValue)) {
                        found = true;
                        break;
                    }
                }
            }

            if (!found) {
                return false;
            }
        }
        return true;
    }

    private boolean matchAnyCondition(Map<String, String> fieldValues, Map<String, Object> conditions,
                                      Map<String, String> originalToStoredFieldName) {
        for (Map.Entry<String, Object> entry : conditions.entrySet()) {
            String originalFieldName = entry.getKey();
            Object expectedValue = entry.getValue();

            // 获取存储的字段名
            String storedFieldName = originalToStoredFieldName.get(originalFieldName);
            if (storedFieldName == null) {
                // 如果找不到映射，尝试使用原始字段名加上通配符
                storedFieldName = originalFieldName + "_";
            }

            // 查找匹配的字段
            for (Map.Entry<String, String> fieldEntry : fieldValues.entrySet()) {
                String actualStoredFieldName = fieldEntry.getKey();
                String actualValue = fieldEntry.getValue();

                // 检查是否匹配存储字段名
                if (actualStoredFieldName.startsWith(storedFieldName)) {
                    if (matchesCondition(actualValue, expectedValue)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean matchesCondition(String actualValue, Object expectedValue) {
        if (expectedValue instanceof String) {
            String expected = (String) expectedValue;
            if (expected.startsWith("%") || expected.endsWith("%")) {
                // 模糊匹配
                String pattern = expected.replace("%", ".*");
                return actualValue != null && actualValue.matches(pattern);
            } else {
                // 精确匹配
                return actualValue != null && actualValue.equals(expected);
            }
        } else {
            // 其他类型比较
            return String.valueOf(expectedValue).equals(actualValue);
        }
    }

    /**
     * 根据ID列表查询数据
     */
    private LedgerDataPageResponse queryDataByIds(List<Long> dataIds, Integer page, Integer size) {
        if (dataIds.isEmpty()) {
            return LedgerDataPageResponse.builder()
                    .data(Collections.emptyList())
                    .currentPage(page)
                    .pageSize(size)
                    .totalElements(0L)
                    .totalPages(0)
                    .hasPrevious(false)
                    .hasNext(false)
                    .build();
        }

        // 分页处理
        int startIndex = (page - 1) * size;
        int endIndex = Math.min(startIndex + size, dataIds.size());

        if (startIndex >= dataIds.size()) {
            return LedgerDataPageResponse.builder()
                    .data(Collections.emptyList())
                    .currentPage(page)
                    .pageSize(size)
                    .totalElements((long) dataIds.size())
                    .totalPages((int) Math.ceil((double) dataIds.size() / size))
                    .hasPrevious(page > 1)
                    .hasNext(endIndex < dataIds.size())
                    .build();
        }

        List<Long> pageIds = dataIds.subList(startIndex, endIndex);

        // 查询数据
        List<LedgerData> dataList = ledgerDataRepository.findByIdInAndDeletedFalse(pageIds);
        Map<Long, List<LedgerDataDetail>> detailsMap = getDataDetails(dataList);

        // 转换为响应对象
        List<LedgerDataResponse> responses = dataList.stream()
                .map(data -> convertToResponse(data, detailsMap.get(data.getId())))
                .collect(Collectors.toList());

        return LedgerDataPageResponse.builder()
                .data(responses)
                .currentPage(page)
                .pageSize(size)
                .totalElements((long) dataIds.size())
                .totalPages((int) Math.ceil((double) dataIds.size() / size))
                .hasPrevious(page > 1)
                .hasNext(endIndex < dataIds.size())
                .build();
    }

    /**
     * 获取当前用户ID
     */
    private Long getCurrentUserId() {
        return securityUtil.getCurrentUserId();
    }

    /**
     * 动态列查询 - 专门用于前端表格展示
     */
    @Transactional(readOnly = true)
    public LedgerDataDynamicPageResponse queryDynamicLedgerData(LedgerDataDynamicQueryRequest request) {
        // 1. 构建查询条件
        Specification<LedgerData> spec = buildDynamicQuerySpecification(request);

        // 2. 构建分页
        Pageable pageable = PageRequest.of(
                request.getPage() - 1,
                request.getSize(),
                Sort.by(Sort.Direction.fromString(request.getSortOrder()), request.getSortField())
        );

        // 3. 执行查询
        Page<LedgerData> dataPage = ledgerDataRepository.findAll(spec, pageable);

        // 4. 获取模板字段信息
        List<TemplateField> templateFields = getTemplateFieldsByRequest(request);

        // 5. 转换为动态响应对象
        return convertToDynamicPageResponse(dataPage, templateFields);
    }

    /**
     * 获取当前查询的模板字段
     */
    private List<TemplateField> getTemplateFieldsByRequest(LedgerDataDynamicQueryRequest request) {
        if (request.getTemplateId() != null) {
            return templateFieldRepository.findByTemplateIdAndDeletedFalse(request.getTemplateId());
        } else if (StringUtils.hasText(request.getUnitName())) {
            // 根据单位名称获取模板
            return ledgerTemplateRepository.findByUnitNameAndDeletedFalse(request.getUnitName())
                    .map(template -> templateFieldRepository.findByTemplateIdAndDeletedFalse(template.getId()))
                    .orElse(Collections.emptyList());
        }
        return Collections.emptyList();
    }

    /**
     * 构建动态查询条件
     */
    private Specification<LedgerData> buildDynamicQuerySpecification(LedgerDataDynamicQueryRequest request) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 基础条件
            predicates.add(criteriaBuilder.equal(root.get("deleted"), false));

            // 单位条件
            if (StringUtils.hasText(request.getUnitName())) {
                predicates.add(criteriaBuilder.equal(root.get("unitName"), request.getUnitName()));
            }

            // 模板条件
            if (request.getTemplateId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("templateId"), request.getTemplateId()));
            }

            // 数据状态条件
            if (StringUtils.hasText(request.getDataStatus())) {
                predicates.add(criteriaBuilder.equal(root.get("dataStatus"), request.getDataStatus()));
            }

            // 验证状态条件
            if (StringUtils.hasText(request.getValidationStatus())) {
                predicates.add(criteriaBuilder.equal(root.get("validationStatus"), request.getValidationStatus()));
            }

            // 时间范围条件
            if (request.getStartTime() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdTime"), request.getStartTime()));
            }
            if (request.getEndTime() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdTime"), request.getEndTime()));
            }

            // 权限控制
            if (request.getViewOwnOnly() != null && request.getViewOwnOnly()) {
                Long userId = getCurrentUserId();
                predicates.add(criteriaBuilder.equal(root.get("createdBy"), userId));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 转换为动态分页响应
     */
    private LedgerDataDynamicPageResponse convertToDynamicPageResponse(
            Page<LedgerData> dataPage, List<TemplateField> templateFields) {

        // 获取数据详情
        List<LedgerData> dataList = dataPage.getContent();
        Map<Long, List<LedgerDataDetail>> detailsMap = getDataDetails(dataList);

        // 转换为动态响应列表
        List<LedgerDataDynamicResponse> responses = dataList.stream()
                .map(data -> convertToDynamicResponse(data, detailsMap.get(data.getId())))
                .collect(Collectors.toList());

        // 构建模板字段信息
        List<LedgerDataDynamicResponse.TemplateFieldInfo> fieldInfos = templateFields.stream()
                .map(this::convertToTemplateFieldInfo)
                .collect(Collectors.toList());

        return LedgerDataDynamicPageResponse.builder()
                .data(responses)
                .currentPage(dataPage.getNumber() + 1)
                .pageSize(dataPage.getSize())
                .totalElements(dataPage.getTotalElements())
                .totalPages(dataPage.getTotalPages())
                .hasPrevious(dataPage.hasPrevious())
                .hasNext(dataPage.hasNext())
                .currentTemplateFields(fieldInfos)
                .build();
    }

    /**
     * 转换为动态响应对象 - 修复字段名格式问题
     */
    private LedgerDataDynamicResponse convertToDynamicResponse(
            LedgerData data, List<LedgerDataDetail> details) {

        LedgerDataDynamicResponse response = LedgerDataDynamicResponse.builder()
                .id(data.getId())
                .unitName(data.getUnitName())
                .templateId(data.getTemplateId())
                .rowNumber(data.getRowNumber())
                .dataStatus(data.getDataStatus())
                .validationStatus(data.getValidationStatus())
                .createdTime(data.getCreatedTime())
                .updatedTime(data.getUpdatedTime())
                .build();

        // 获取上传信息
        if (data.getUploadId() != null) {
            ledgerUploadRepository.findById(data.getUploadId()).ifPresent(upload -> {
                response.setUploadNo(upload.getUploadNo());
            });
        }

        // 获取模板名称
        if (data.getTemplateId() != null) {
            ledgerTemplateRepository.findById(data.getTemplateId()).ifPresent(template -> {
                response.setTemplateName(template.getTemplateName());
            });
        }

        // 获取用户信息
        if (data.getCreatedBy() != null) {
            response.setCreatedByName("用户" + data.getCreatedBy());
        }

        // 处理字段数据 - 修复字段名格式问题
        if (details != null) {
            Map<String, Object> fieldData = new LinkedHashMap<>();
            int emptyCount = 0;
            int invalidCount = 0;

            // 获取模板字段定义
            List<TemplateField> templateFields = templateFieldRepository
                    .findByTemplateIdAndDeletedFalse(data.getTemplateId());

            // 创建存储字段名到原始字段名的映射
            Map<String, String> storedToOriginalFieldName = new HashMap<>();
            for (TemplateField field : templateFields) {
                String storedName = field.getFieldName() + "_" + field.getExcelColumn();
                storedToOriginalFieldName.put(storedName, field.getFieldName());
            }

            for (LedgerDataDetail detail : details) {
                // 将存储字段名转换为原始字段名
                String originalFieldName = storedToOriginalFieldName.getOrDefault(
                        detail.getFieldName(),
                        extractOriginalFieldName(detail.getFieldName()));

                fieldData.put(originalFieldName, detail.getFieldValue());
                if (detail.getIsEmpty()) emptyCount++;
                if (!detail.getIsValid()) invalidCount++;
            }

            response.setFieldData(fieldData);
            response.setEmptyFieldCount(emptyCount);
            response.setInvalidFieldCount(invalidCount);
        }

        return response;
    }

    /**
     * 转换为模板字段信息
     */
    private LedgerDataDynamicResponse.TemplateFieldInfo convertToTemplateFieldInfo(TemplateField field) {
        return LedgerDataDynamicResponse.TemplateFieldInfo.builder()
                .fieldName(field.getFieldName())
                .fieldLabel(field.getFieldLabel())
                .fieldType(field.getFieldType())
                .excelColumn(field.getExcelColumn())
                .sortOrder(field.getSortOrder())
                .build();
    }

    /**
     * 根据单位获取模板字段定义（新增方法）
     */
    public List<TemplateField> getTemplateFieldsByUnit(String unitName) {
        return ledgerTemplateRepository.findByUnitNameAndDeletedFalse(unitName)
                .map(template -> templateFieldRepository.findByTemplateIdAndDeletedFalse(template.getId()))
                .orElseThrow(() -> new RuntimeException("单位没有模板或模板不存在: " + unitName));
    }

    /**
     * 获取单位名称列表
     */
    public List<String> getUnitNames() {
        return ledgerTemplateRepository.findAllActive().stream()
                .map(LedgerTemplate::getUnitName)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 从数据库查询年份列表
     */
    private List<Integer> getYearsFromDatabase(String unitName) {
        List<LocalDateTime> createTimes;

        if (StringUtils.hasText(unitName)) {
            // 查询特定单位的创建时间
            List<LedgerData> dataList = ledgerDataRepository.findByUnitNameAndDeletedFalse(unitName);
            createTimes = dataList.stream()
                    .map(LedgerData::getCreatedTime)
                    .collect(Collectors.toList());
        } else {
            // 查询所有数据
            createTimes = ledgerDataRepository.findAll().stream()
                    .filter(data -> !data.getDeleted())
                    .map(LedgerData::getCreatedTime)
                    .collect(Collectors.toList());
        }

        // 提取不重复的年份
        return createTimes.stream()
                .map(time -> time.getYear())
                .distinct()
                .sorted(Comparator.reverseOrder()) // 倒序排列，最新的在前面
                .collect(Collectors.toList());
    }

    /**
     * 从数据库查询年月列表
     */
    private List<String> getYearMonthsFromDatabase(String unitName) {
        List<LocalDateTime> createTimes;

        if (StringUtils.hasText(unitName)) {
            // 查询特定单位的创建时间
            List<LedgerData> dataList = ledgerDataRepository.findByUnitNameAndDeletedFalse(unitName);
            createTimes = dataList.stream()
                    .map(LedgerData::getCreatedTime)
                    .collect(Collectors.toList());
        } else {
            // 查询所有数据
            createTimes = ledgerDataRepository.findAll().stream()
                    .filter(data -> !data.getDeleted())
                    .map(LedgerData::getCreatedTime)
                    .collect(Collectors.toList());
        }

        // 提取不重复的年月
        return createTimes.stream()
                .map(time -> time.getYear() + "-" + String.format("%02d", time.getMonthValue()))
                .distinct()
                .sorted(Comparator.reverseOrder()) // 倒序排列，最新的在前面
                .collect(Collectors.toList());
    }

    /**
     * 从数据库查询年份统计
     */
    private Map<Integer, Long> getYearStatisticsFromDatabase(String unitName) {
        List<LedgerData> dataList;

        if (StringUtils.hasText(unitName)) {
            // 查询特定单位的数据
            dataList = ledgerDataRepository.findByUnitNameAndDeletedFalse(unitName);
        } else {
            // 查询所有数据
            dataList = ledgerDataRepository.findAll().stream()
                    .filter(data -> !data.getDeleted())
                    .collect(Collectors.toList());
        }

        // 按年份统计数量
        return dataList.stream()
                .collect(Collectors.groupingBy(
                        data -> data.getCreatedTime().getYear(),
                        Collectors.counting()
                ));
    }

    /**
     * 获取当前年月字符串
     */
    private String getCurrentYearMonth() {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1; // 月份从0开始
        return year + "-" + String.format("%02d", month);
    }

    /**
     * 构建多字段查询条件 - 针对 LedgerDataMultiFieldQueryRequest（修复版本）
     */
    private Specification<LedgerData> buildMultiFieldSpecification(LedgerDataMultiFieldQueryRequest request) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 基础条件
            predicates.add(criteriaBuilder.equal(root.get("deleted"), false));

            // 单位条件
            if (StringUtils.hasText(request.getUnitName())) {
                predicates.add(criteriaBuilder.equal(root.get("unitName"), request.getUnitName()));
            }

            // 模板条件
            if (request.getTemplateId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("templateId"), request.getTemplateId()));
            }

            // 数据状态条件
            if (StringUtils.hasText(request.getDataStatus())) {
                predicates.add(criteriaBuilder.equal(root.get("dataStatus"), request.getDataStatus()));
            }

            // 验证状态条件
            if (StringUtils.hasText(request.getValidationStatus())) {
                predicates.add(criteriaBuilder.equal(root.get("validationStatus"), request.getValidationStatus()));
            }

            // 时间范围条件
            if (request.getStartTime() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdTime"), request.getStartTime()));
            }
            if (request.getEndTime() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdTime"), request.getEndTime()));
            }

            // 新增：年月查询条件
            if (request.getYear() != null) {
                // 使用JPA函数提取年份
                Expression<Integer> yearExpr = criteriaBuilder.function("YEAR", Integer.class, root.get("createdTime"));
                predicates.add(criteriaBuilder.equal(yearExpr, request.getYear()));

                if (request.getMonth() != null) {
                    // 使用JPA函数提取月份
                    Expression<Integer> monthExpr = criteriaBuilder.function("MONTH", Integer.class, root.get("createdTime"));
                    predicates.add(criteriaBuilder.equal(monthExpr, request.getMonth()));
                }
            }

            // 权限控制
            if (Boolean.TRUE.equals(request.getViewOwnOnly())) {
                Long userId = getCurrentUserId();
                predicates.add(criteriaBuilder.equal(root.get("createdBy"), userId));
            }

            // 台账字段查询条件 - 通过子查询实现
            List<Predicate> fieldPredicates = new ArrayList<>();

            // 项目名称查询
            if (StringUtils.hasText(request.getProjectName())) {
                fieldPredicates.add(createFieldPredicate(query, criteriaBuilder, root, "项目名称",
                        request.getProjectName(), request.getFuzzySearch(),
                        request.getTemplateId(), request.getUnitName()));
            }

//            // 物料组（9位码）查询
//            if (StringUtils.hasText(request.getMaterialGroup9())) {
//                fieldPredicates.add(createFieldPredicate(query, criteriaBuilder, root, "物料组（9位码）",
//                        request.getMaterialGroup9(), request.getFuzzySearch(),
//                        request.getTemplateId(), request.getUnitName()));
//            }
//
//            // ERP系统编码（11位码）查询
//            if (StringUtils.hasText(request.getErpCode11())) {
//                fieldPredicates.add(createFieldPredicate(query, criteriaBuilder, root, "大ERP系统编码（15位码）",
//                        request.getErpCode11(), request.getFuzzySearch(),
//                        request.getTemplateId(), request.getUnitName()));
//            }
//
//            // 大ERP物资描述查询
//            if (StringUtils.hasText(request.getErpMaterialDesc())) {
//                fieldPredicates.add(createFieldPredicate(query, criteriaBuilder, root, "大ERP物资描述（名称+规格型号）",
//                        request.getErpMaterialDesc(), request.getFuzzySearch(),
//                        request.getTemplateId(), request.getUnitName()));
//            }

            // 物料组（9位码）查询
            if (StringUtils.hasText(request.getMaterialGroup9())) {
                // 直接使用正确的存储字段名
                Subquery<Long> materialSubquery = query.subquery(Long.class);
                Root<LedgerDataDetail> materialDetailRoot = materialSubquery.from(LedgerDataDetail.class);

                List<Predicate> materialPredicates = new ArrayList<>();
                materialPredicates.add(criteriaBuilder.equal(materialDetailRoot.get("dataId"), root.get("id")));

                // 关键：直接使用ledger_data_detail中的字段名格式
                materialPredicates.add(criteriaBuilder.equal(
                        materialDetailRoot.get("fieldName"),
                        "物料组_9位码__D"  // 直接从数据库中复制的字段名
                ));

                // 字段值匹配
                materialPredicates.add(criteriaBuilder.like(
                        materialDetailRoot.get("fieldValue"),
                        "%" + request.getMaterialGroup9().trim() + "%"
                ));

                materialSubquery.select(materialDetailRoot.get("dataId"))
                        .where(materialPredicates.toArray(new Predicate[0]));

                fieldPredicates.add(criteriaBuilder.exists(materialSubquery));
            }

            if (StringUtils.hasText(request.getErpCode11())) {
                Subquery<Long> erpSubquery = query.subquery(Long.class);
                Root<LedgerDataDetail> erpDetailRoot = erpSubquery.from(LedgerDataDetail.class);

                List<Predicate> erpPredicates = new ArrayList<>();
                erpPredicates.add(criteriaBuilder.equal(erpDetailRoot.get("dataId"), root.get("id")));
                erpPredicates.add(criteriaBuilder.equal(
                        erpDetailRoot.get("fieldName"),
                        "大erp系统编码_15位码__E"
                ));
                erpPredicates.add(criteriaBuilder.like(
                        erpDetailRoot.get("fieldValue"),
                        "%" + request.getErpCode11().trim() + "%"
                ));

                erpSubquery.select(erpDetailRoot.get("dataId"))
                        .where(erpPredicates.toArray(new Predicate[0]));

                fieldPredicates.add(criteriaBuilder.exists(erpSubquery));
            }

            if (StringUtils.hasText(request.getErpMaterialDesc())) {
                Subquery<Long> descSubquery = query.subquery(Long.class);
                Root<LedgerDataDetail> descDetailRoot = descSubquery.from(LedgerDataDetail.class);

                List<Predicate> descPredicates = new ArrayList<>();
                descPredicates.add(criteriaBuilder.equal(descDetailRoot.get("dataId"), root.get("id")));
                descPredicates.add(criteriaBuilder.equal(
                        descDetailRoot.get("fieldName"),
                        "大erp物资描述_名称_规格型号__F"
                ));
                descPredicates.add(criteriaBuilder.like(
                        descDetailRoot.get("fieldValue"),
                        "%" + request.getErpMaterialDesc().trim() + "%"
                ));

                descSubquery.select(descDetailRoot.get("dataId"))
                        .where(descPredicates.toArray(new Predicate[0]));

                fieldPredicates.add(criteriaBuilder.exists(descSubquery));
            }

            // 采购业务经办人员查询
            if (StringUtils.hasText(request.getPurchaseAgent())) {
                fieldPredicates.add(createFieldPredicate(query, criteriaBuilder, root, "采购业务经办人员名字",
                        request.getPurchaseAgent(), request.getFuzzySearch(),
                        request.getTemplateId(), request.getUnitName()));
            }

            // 需求计划编号查询
            if (StringUtils.hasText(request.getDemandPlanNo())) {
                fieldPredicates.add(createFieldPredicate(query, criteriaBuilder, root, "需求计划跟踪编号",
                        request.getDemandPlanNo(), request.getFuzzySearch(),
                        request.getTemplateId(), request.getUnitName()));
            }

            // 采购计划单号查询
            if (StringUtils.hasText(request.getPurchasePlanNo())) {
                fieldPredicates.add(createFieldPredicate(query, criteriaBuilder, root, "采购计划单号",
                        request.getPurchasePlanNo(), request.getFuzzySearch(),
                        request.getTemplateId(), request.getUnitName()));
            }

            // 采购方案号查询
            if (StringUtils.hasText(request.getPurchaseSchemeNo())) {
                fieldPredicates.add(createFieldPredicate(query, criteriaBuilder, root, "采购方案号",
                        request.getPurchaseSchemeNo(), request.getFuzzySearch(),
                        request.getTemplateId(), request.getUnitName()));
            }

            // 合同号查询
            if (StringUtils.hasText(request.getContractNo())) {
                fieldPredicates.add(createFieldPredicate(query, criteriaBuilder, root, "_框架_一单一采_合同号",
                        request.getContractNo(), request.getFuzzySearch(),
                        request.getTemplateId(), request.getUnitName()));
            }

            // 报审序号查询
            if (StringUtils.hasText(request.getReportNo())) {
                fieldPredicates.add(createFieldPredicate(query, criteriaBuilder, root, "报审序号",
                        request.getReportNo(), request.getFuzzySearch(),
                        request.getTemplateId(), request.getUnitName()));
            }

            // 订单号查询
            if (StringUtils.hasText(request.getOrderNo())) {
                fieldPredicates.add(createFieldPredicate(query, criteriaBuilder, root, "_即买即结_框架下的订单_订单号",
                        request.getOrderNo(), request.getFuzzySearch(),
                        request.getTemplateId(), request.getUnitName()));
            }

            // 供应商编码查询
            if (StringUtils.hasText(request.getSupplierCode())) {
                fieldPredicates.add(createFieldPredicate(query, criteriaBuilder, root, "供应商编码",
                        request.getSupplierCode(), request.getFuzzySearch(),
                        request.getTemplateId(), request.getUnitName()));
            }

            // 供应商名称查询
            if (StringUtils.hasText(request.getSupplierName())) {
                fieldPredicates.add(createFieldPredicate(query, criteriaBuilder, root, "供应商名称",
                        request.getSupplierName(), request.getFuzzySearch(),
                        request.getTemplateId(), request.getUnitName()));
            }

            // 当前采购进度查询
            if (StringUtils.hasText(request.getPurchaseProgress())) {
                fieldPredicates.add(createFieldPredicate(query, criteriaBuilder, root, "当前采购进度",
                        request.getPurchaseProgress(), request.getFuzzySearch(),
                        request.getTemplateId(), request.getUnitName()));
            }

            // 将字段查询条件加入到主条件中
            if (!fieldPredicates.isEmpty()) {
                // 所有字段条件使用 AND 连接
                predicates.add(criteriaBuilder.and(fieldPredicates.toArray(new Predicate[0])));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}