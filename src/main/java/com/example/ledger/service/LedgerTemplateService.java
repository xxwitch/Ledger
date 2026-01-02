package com.example.ledger.service;

/**
 * @author 霜月
 * @create 2025/12/20 22:31
 */

import com.example.ledger.dto.response.LedgerTemplateResponse;
import com.example.ledger.entity.LedgerTemplate;
import com.example.ledger.entity.TemplateField;
import com.example.ledger.entity.LedgerData;
import com.example.ledger.repository.LedgerTemplateRepository;
import com.example.ledger.repository.TemplateFieldRepository;
import com.example.ledger.repository.LedgerDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerTemplateService {

    private final LedgerTemplateRepository ledgerTemplateRepository;
    private final TemplateFieldRepository templateFieldRepository;
    private final LedgerDataRepository ledgerDataRepository;

    /**
     * 获取所有模板
     */
    @Transactional(readOnly = true)
    public List<LedgerTemplateResponse> getAllTemplates() {
        List<LedgerTemplate> templates = ledgerTemplateRepository.findAll();
        return templates.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 分页获取模板
     */
    @Transactional(readOnly = true)
    public Page<LedgerTemplateResponse> getTemplatePage(int page, int size, String unitName, Integer status) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdTime"));
        Page<LedgerTemplate> templatePage;

        if (unitName != null && status != null) {
            templatePage = ledgerTemplateRepository.findByUnitNameContainingAndStatusAndDeletedFalse(
                    unitName, status, pageable);
        } else if (unitName != null) {
            templatePage = ledgerTemplateRepository.findByUnitNameContainingAndDeletedFalse(
                    unitName, pageable);
        } else if (status != null) {
            templatePage = ledgerTemplateRepository.findByStatusAndDeletedFalse(status, pageable);
        } else {
            templatePage = ledgerTemplateRepository.findByDeletedFalse(pageable);
        }

        return templatePage.map(this::convertToResponse);
    }

    /**
     * 获取模板详情
     */
    @Transactional(readOnly = true)
    public LedgerTemplateResponse getTemplateDetail(Long id) {
        LedgerTemplate template = ledgerTemplateRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new RuntimeException("模板不存在"));
        return convertToResponse(template);
    }

    /**
     * 根据单位名称获取模板
     */
    @Transactional(readOnly = true)
    public LedgerTemplateResponse getTemplateByUnitName(String unitName) {
        LedgerTemplate template = ledgerTemplateRepository.findByUnitNameAndDeletedFalse(unitName)
                .orElseThrow(() -> new RuntimeException("模板不存在: " + unitName));
        return convertToResponse(template);
    }

    /**
     * 获取模板实体
     */
    @Transactional(readOnly = true)
    public LedgerTemplate getTemplateById(Long id) {
        return ledgerTemplateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("模板不存在"));
    }

    /**
     * 创建模板
     */
    @Transactional
    public LedgerTemplateResponse createTemplate(LedgerTemplate template) {
        // 检查单位名称是否已存在
        if (ledgerTemplateRepository.existsByUnitNameAndDeletedFalse(template.getUnitName())) {
            throw new RuntimeException("该单位模板已存在: " + template.getUnitName());
        }

        template.setStatus(1);
        template.setDeleted(false);
        template.setHasTemplateFile(false);

        LedgerTemplate savedTemplate = ledgerTemplateRepository.save(template);
        log.info("创建模板成功: {}", savedTemplate.getUnitName());

        return convertToResponse(savedTemplate);
    }

    /**
     * 更新模板
     */
    @Transactional
    public LedgerTemplateResponse updateTemplate(LedgerTemplate template) {
        LedgerTemplate existingTemplate = ledgerTemplateRepository.findById(template.getId())
                .orElseThrow(() -> new RuntimeException("模板不存在"));

        // 更新字段
        existingTemplate.setTemplateName(template.getTemplateName());
        existingTemplate.setDescription(template.getDescription());
        existingTemplate.setVersion(template.getVersion());
        existingTemplate.setStatus(template.getStatus());
        existingTemplate.setUpdatedTime(LocalDateTime.now());

        LedgerTemplate savedTemplate = ledgerTemplateRepository.save(existingTemplate);
        log.info("更新模板成功: {}", savedTemplate.getUnitName());

        return convertToResponse(savedTemplate);
    }

    /**
     * 删除模板（逻辑删除）
     */
    @Transactional
    public void deleteTemplate(Long id) {
        LedgerTemplate template = ledgerTemplateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("模板不存在"));

        template.setDeleted(true);
        template.setUpdatedTime(LocalDateTime.now());

        ledgerTemplateRepository.save(template);
        log.info("删除模板成功: {}", template.getUnitName());
    }

    /**
     * 启用/禁用模板
     */
    @Transactional
    public void toggleTemplateStatus(Long id, Integer status) {
        if (status != 0 && status != 1) {
            throw new RuntimeException("状态值只能是0或1");
        }

        LedgerTemplate template = ledgerTemplateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("模板不存在"));

        template.setStatus(status);
        template.setUpdatedTime(LocalDateTime.now());

        ledgerTemplateRepository.save(template);
        log.info("更新模板状态: {} -> {}", template.getUnitName(), status);
    }

    /**
     * 获取模板统计信息
     */
    @Transactional(readOnly = true)
    public LedgerTemplateResponse getTemplateStats(Long id) {
        LedgerTemplateResponse response = getTemplateDetail(id);

        // 获取字段数量
        Long fieldCount = templateFieldRepository.countByTemplateIdAndDeletedFalse(id);
        response.setFieldCount(fieldCount.intValue());

        // 获取数据量
        Long dataCount = ledgerDataRepository.countByTemplateIdAndDeletedFalse(id);
        response.setDataCount(dataCount.intValue());

        return response;
    }

    /**
     * 获取所有未删除的模板
     */
    @Transactional(readOnly = true)
    public List<LedgerTemplateResponse> getAllActiveTemplates() {
        List<LedgerTemplate> templates = ledgerTemplateRepository.findAllActive();
        return templates.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 将模板实体转换为响应DTO
     */
    private LedgerTemplateResponse convertToResponse(LedgerTemplate template) {
        LedgerTemplateResponse response = new LedgerTemplateResponse();

        response.setId(template.getId());
        response.setUnitName(template.getUnitName());
        response.setTemplateName(template.getTemplateName());
        response.setDescription(template.getDescription());
        response.setVersion(template.getVersion());
        response.setStatus(template.getStatus());
        response.setCreatedBy(template.getCreatedBy());

        response.setTemplateFilePath(template.getTemplateFilePath());
        response.setTemplateFileName(template.getTemplateFileName());
        response.setHasTemplateFile(template.getHasTemplateFile());
        response.setHeaderRowCount(template.getHeaderRowCount());
        response.setDataStartRow(template.getDataStartRow());
        response.setColumnCount(template.getColumnCount());

        response.setCreatedTime(template.getCreatedTime());
        response.setUpdatedTime(template.getUpdatedTime());

        return response;
    }
}