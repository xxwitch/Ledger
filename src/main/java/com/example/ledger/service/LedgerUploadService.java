package com.example.ledger.service;

/**
 * @author 霜月
 * @create 2025/12/21 01:26
 */
import com.example.ledger.dto.request.LedgerUploadRequest;
import com.example.ledger.dto.response.LedgerUploadResponse;
import com.example.ledger.entity.*;
import com.example.ledger.repository.*;
import com.example.ledger.util.SecurityUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Async;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.apache.commons.io.IOUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerUploadService {

    private final LedgerTemplateRepository ledgerTemplateRepository;
    private final TemplateFieldRepository templateFieldRepository;
    private final LedgerUploadRepository ledgerUploadRepository;
    private final LedgerDataRepository ledgerDataRepository;
    private final LedgerDataDetailRepository ledgerDataDetailRepository;
    private final RequiredFieldConfigRepository requiredFieldConfigRepository;
    private final FileStorageService fileStorageService;
    private final SecurityUtil securityUtil;

    @Qualifier("uploadTaskExecutor")
    private final Executor uploadTaskExecutor;

    @Value("${app.upload.batch-size:100}")
    private int batchSize;

    @Value("${app.upload.temp-dir:./temp}")
    private String tempDir;

    // 存储上传进度
    private final Map<Long, UploadProgress> uploadProgressMap = new ConcurrentHashMap<>();

    // 使用DataFormatter来保持Excel中的原始格式
    private final DataFormatter dataFormatter = new DataFormatter();

    /**
     * 初始化临时目录
     */
    @PostConstruct
    public void init() {
        File tempDirFile = new File(tempDir);
        if (!tempDirFile.exists()) {
            tempDirFile.mkdirs();
            log.info("创建临时目录: {}", tempDir);
        }
    }

    /**
     * 上传台账数据文件 - 支持覆盖更新模式
     */
    @Transactional
    public LedgerUploadResponse uploadLedgerData(LedgerUploadRequest request, String uploadIp) throws IOException {
        // 1. 获取当前用户ID
        Long userId = getCurrentUserId();

        // 2. 验证单位是否存在模板
        LedgerTemplate template = ledgerTemplateRepository.findByUnitNameAndDeletedFalse(request.getUnitName())
                .orElseThrow(() -> new RuntimeException("该单位没有找到对应的模板，请先创建模板：" + request.getUnitName()));

        // 3. 检查用户是否已有该单位的数据（用于日志记录）
        Long existingDataCount = ledgerDataRepository.countByUserIdAndUnitName(userId, request.getUnitName());
        if (existingDataCount > 0) {
            log.info("用户 {} 在单位 {} 已有 {} 条数据，将执行覆盖更新", userId, request.getUnitName(), existingDataCount);
        }

        // 4. 生成上传批次号
        String uploadNo = generateUploadNo();

        // 5. 保存上传文件（永久存储）
        String filePath = fileStorageService.storeUploadFile(request.getFile(), uploadNo);

        // 6. 同时复制文件到临时目录，供异步处理使用
        String tempFilePath = copyToTempFile(request.getFile(), uploadNo);

        // 7. 创建初始上传记录
        LedgerUpload upload = createUploadRecord(request, template, userId, uploadIp, uploadNo, filePath);

        // 保存并立即刷新
        upload = ledgerUploadRepository.save(upload);
        ledgerUploadRepository.flush(); // 强制刷新到数据库

        log.info("上传记录已保存，ID: {}, UploadNo: {}, 临时文件: {}，将执行覆盖更新",
                upload.getId(), upload.getUploadNo(), tempFilePath);

        // 8. 初始化进度信息
        UploadProgress progress = new UploadProgress(upload.getId());
        uploadProgressMap.put(upload.getId(), progress);

        // 9. 异步处理Excel数据 - 传递临时文件路径
        final Long uploadId = upload.getId();
        final Long templateId = template.getId();
        final String finalTempFilePath = tempFilePath;  // 使用临时文件路径
        final boolean validateRequiredFields = request.getValidateRequiredFields();
        final boolean skipInvalidRows = request.getSkipInvalidRows();

        CompletableFuture.runAsync(() -> {
            try {
                processExcelDataAsyncWrapper(uploadId, templateId, finalTempFilePath,
                        validateRequiredFields, skipInvalidRows, true);  // 最后一个参数表示覆盖更新
            } catch (Exception e) {
                log.error("异步处理任务启动失败，上传ID: {}", uploadId, e);
                updateUploadStatusOnError(uploadId, "异步处理启动失败: " + e.getMessage());
            }
        }, uploadTaskExecutor);

        // 10. 返回初始响应
        return createInitialResponse(upload, template, existingDataCount);
    }

    /**
     * 复制文件到临时目录
     */
    private String copyToTempFile(MultipartFile file, String uploadNo) throws IOException {
        // 确保临时目录存在
        File tempDirFile = new File(tempDir);
        if (!tempDirFile.exists()) {
            tempDirFile.mkdirs();
        }

        // 生成临时文件名
        String originalFilename = file.getOriginalFilename();
        String fileExt = originalFilename.substring(originalFilename.lastIndexOf('.'));
        String tempFilename = "upload_" + uploadNo + "_" + System.currentTimeMillis() + fileExt;
        String tempFilePath = tempDir + File.separator + tempFilename;

        // 复制文件
        try (InputStream inputStream = file.getInputStream();
             OutputStream outputStream = new FileOutputStream(tempFilePath)) {
            IOUtils.copy(inputStream, outputStream);
        }

        log.info("文件已复制到临时目录: {}", tempFilePath);
        return tempFilePath;
    }

    /**
     * 异步处理包装方法（支持覆盖更新）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processExcelDataAsyncWrapper(Long uploadId, Long templateId, String filePath,
                                             boolean validateRequiredFields, boolean skipInvalidRows,
                                             boolean replaceExisting) {
        try {
            log.info("开始异步处理，上传ID: {}, 文件路径: {}, 覆盖更新: {}", uploadId, filePath, replaceExisting);

            // 短暂等待，确保主事务已提交
            Thread.sleep(100);

            // 重新从数据库获取对象
            LedgerUpload upload = ledgerUploadRepository.findById(uploadId)
                    .orElseThrow(() -> new RuntimeException("上传记录不存在: " + uploadId));

            LedgerTemplate template = ledgerTemplateRepository.findById(templateId)
                    .orElseThrow(() -> new RuntimeException("模板不存在: " + templateId));

            log.info("获取到上传记录和模板，开始处理Excel");

            // 调用实际的异步处理方法
            processExcelDataAsync(upload, template, filePath, validateRequiredFields, skipInvalidRows, replaceExisting);
        } catch (Exception e) {
            log.error("异步处理包装方法异常，上传ID: {}", uploadId, e);
            updateUploadStatusOnError(uploadId, e.getMessage());
        }
    }

    /**
     * 异步处理Excel数据（支持覆盖更新）
     */
    @Async("uploadTaskExecutor")
    @Transactional
    public void processExcelDataAsync(LedgerUpload upload, LedgerTemplate template, String filePath,
                                      boolean validateRequiredFields, boolean skipInvalidRows,
                                      boolean replaceExisting) {
        log.info("开始异步处理Excel数据，上传ID: {}，单位: {}，文件路径: {}，覆盖更新: {}",
                upload.getId(), template.getUnitName(), filePath, replaceExisting);

        UploadProgress progress = uploadProgressMap.get(upload.getId());
        if (progress == null) {
            progress = new UploadProgress(upload.getId());
            uploadProgressMap.put(upload.getId(), progress);
        }

        try {
            // 1. 如果是覆盖更新，先删除用户在该单位的所有旧数据
            if (replaceExisting) {
                deleteExistingDataForUser(upload.getUserId(), template.getUnitName(), upload.getId());
                updateProgress(progress, 0, 0, 0, "正在清理旧数据...");
            }

            // 2. 获取模板字段
            List<TemplateField> templateFields = templateFieldRepository
                    .findByTemplateIdAndDeletedFalse(template.getId());

            if (templateFields.isEmpty()) {
                throw new RuntimeException("模板字段定义未配置，请先上传模板文件");
            }

            // 3. 获取必填项配置
            List<RequiredFieldConfig> requiredFields = requiredFieldConfigRepository
                    .findByTemplateIdAndRequiredTrue(template.getId());

            // 4. 解析Excel文件 - 使用文件路径
            parseExcelDataWithValidation(filePath, template, templateFields, requiredFields, upload,
                    validateRequiredFields, skipInvalidRows, progress);

            // 5. 更新上传状态
            if (upload.getTotalRows() > 0 && upload.getSuccessRows() > 0) {
                if (upload.getFailedRows() > 0) {
                    upload.setImportStatus("PARTIAL_SUCCESS");
                } else {
                    upload.setImportStatus("SUCCESS");
                }
            } else {
                upload.setImportStatus("FAILED");
            }

            upload.setCompletedTime(LocalDateTime.now());
            ledgerUploadRepository.save(upload);

            progress.setStatus(upload.getImportStatus());
            log.info("异步处理Excel数据完成，上传ID: {}，总行数: {}，成功行数: {}，失败行数: {}，覆盖更新: {}",
                    upload.getId(), upload.getTotalRows(), upload.getSuccessRows(),
                    upload.getFailedRows(), replaceExisting);

        } catch (Exception e) {
            log.error("异步处理Excel数据异常，上传ID: {}", upload.getId(), e);

            // 更新上传状态
            upload.setImportStatus("FAILED");
            upload.setErrorMessage(e.getMessage());
            upload.setCompletedTime(LocalDateTime.now());
            ledgerUploadRepository.save(upload);

            progress.setStatus("FAILED");
            progress.setErrorMessage(e.getMessage());
        } finally {
            // 清理临时文件
            cleanupTempFile(filePath);
            // 清理进度信息
            scheduleProgressCleanup(upload.getId());
        }
    }

    /**
     * 删除用户在某单位的现有数据（覆盖更新核心逻辑）
     */
    @Transactional
    protected void deleteExistingDataForUser(Long userId, String unitName, Long newUploadId) {
        try {
            LocalDateTime now = LocalDateTime.now();

            // 1. 查找用户在该单位的所有数据
            List<LedgerData> existingData = ledgerDataRepository.findByUserIdAndUnitName(userId, unitName);
            log.info("找到用户 {} 在单位 {} 的 {} 条待删除数据", userId, unitName, existingData.size());

            if (existingData.isEmpty()) {
                return;
            }

            // 2. 提取数据ID列表
            List<Long> dataIds = existingData.stream()
                    .map(LedgerData::getId)
                    .collect(Collectors.toList());

            // 3. 先删除明细数据（物理删除以提高性能）
            int deletedDetails = 0;
            int batchSize = 500;
            for (int i = 0; i < dataIds.size(); i += batchSize) {
                int end = Math.min(i + batchSize, dataIds.size());
                List<Long> batchIds = dataIds.subList(i, end);
                ledgerDataDetailRepository.deleteByDataIdIn(batchIds);
                deletedDetails += batchIds.size();
            }
            log.info("已删除 {} 条明细数据", deletedDetails);

            // 4. 删除主数据（物理删除）
            ledgerDataRepository.deleteAll(existingData);
            log.info("已删除 {} 条主数据", existingData.size());

            // 5. 标记旧的上传记录为已删除
            int markedUploads = ledgerUploadRepository.markUploadsAsDeleted(userId, unitName, now);
            log.info("已标记 {} 条旧上传记录为已删除", markedUploads);

        } catch (Exception e) {
            log.error("删除现有数据时发生错误，用户ID: {}, 单位: {}", userId, unitName, e);
            throw new RuntimeException("清理旧数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析Excel数据（使用文件路径）- 自动检测文件格式
     */
    private void parseExcelData(String filePath, LedgerTemplate template,
                                List<TemplateField> templateFields,
                                List<RequiredFieldConfig> requiredFields,
                                LedgerUpload upload,
                                boolean validateRequiredFields,
                                boolean skipInvalidRows,
                                UploadProgress progress) throws IOException {

        File excelFile = new File(filePath);
        if (!excelFile.exists()) {
            throw new IOException("Excel文件不存在: " + filePath);
        }

        Workbook workbook = null;

        try (FileInputStream fis = new FileInputStream(excelFile)) {
            // 使用WorkbookFactory自动检测文件格式（支持.xls和.xlsx）
            workbook = WorkbookFactory.create(fis);

            Sheet sheet = workbook.getSheetAt(0);
            int dataStartRow = template.getDataStartRow() - 1;

            // 准备进度信息
            progress.setTotalRows(0);  // 初始化为0，后面会计算

            // 先统计总行数
            int estimatedTotalRows = 0;
            for (int rowNum = dataStartRow; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row != null && !isEmptyRow(row)) {
                    estimatedTotalRows++;
                }
            }
            progress.setTotalRows(estimatedTotalRows);
            updateProgress(progress, estimatedTotalRows, 0, 0, "开始处理Excel数据...");

            // 批量处理数据 - 使用LinkedHashMap保持顺序
            Map<Integer, LedgerData> ledgerDataMap = new LinkedHashMap<>();
            Map<Integer, List<LedgerDataDetail>> detailMap = new LinkedHashMap<>();

            int processedRows = 0;
            int successRows = 0;
            int failedRows = 0;

            // 创建字段映射 - 使用修复后的方法
            Map<String, TemplateField> fieldMap = createFieldMap(templateFields);
            Set<String> requiredFieldNames = getRequiredFieldNames(requiredFields);

            // 处理每一行数据
            for (int rowNum = dataStartRow; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null || isEmptyRow(row)) {
                    continue;
                }

                processedRows++;

                try {
                    // 计算Excel中的行号（从1开始）
                    int excelRowNum = rowNum + 1;

                    // 计算台账数据中的行号（从数据起始行开始计算）
                    int dataRowNumber = excelRowNum - template.getDataStartRow() + 1;

                    log.debug("处理第{}行，台账行号: {}", excelRowNum, dataRowNumber);

                    // 创建台账数据
                    LedgerData ledgerData = createLedgerData(row, template, upload, rowNum, dataRowNumber);

                    // 创建字段详情
                    List<LedgerDataDetail> details = createLedgerDataDetails(
                            row, fieldMap, requiredFieldNames, dataRowNumber,
                            validateRequiredFields, upload.getUserId()
                    );

                    // 使用行号作为key，保持顺序
                    ledgerDataMap.put(dataRowNumber, ledgerData);
                    detailMap.put(dataRowNumber, details);

                    successRows++;

                    // 达到批处理大小时保存
                    if (ledgerDataMap.size() >= batchSize) {
                        saveBatchDataInOrder(ledgerDataMap, detailMap, template.getUnitName());
                        ledgerDataMap.clear();
                        detailMap.clear();

                        // 更新进度
                        updateProgress(progress, estimatedTotalRows, successRows, failedRows,
                                String.format("处理中... 已处理 %d/%d 行", processedRows, estimatedTotalRows));
                    }

                } catch (Exception e) {
                    failedRows++;
                    log.warn("第 {} 行处理失败: {}", rowNum + 1, e.getMessage());
                    log.error("详细错误信息:", e);

                    if (!skipInvalidRows) {
                        throw new RuntimeException("第" + (rowNum + 1) + "行处理失败: " + e.getMessage(), e);
                    }
                }

                // 每处理100行更新一次数据库状态
                if (processedRows % 100 == 0) {
                    upload.setTotalRows(estimatedTotalRows);
                    upload.setSuccessRows(successRows);
                    upload.setFailedRows(failedRows);
                    ledgerUploadRepository.save(upload);
                }
            }

            // 保存剩余数据
            if (!ledgerDataMap.isEmpty()) {
                saveBatchDataInOrder(ledgerDataMap, detailMap, template.getUnitName());
            }

            // 最终更新数据库
            upload.setTotalRows(estimatedTotalRows);
            upload.setSuccessRows(successRows);
            upload.setFailedRows(failedRows);

            // 根据处理结果设置状态
            if (failedRows > 0 && successRows == 0) {
                upload.setImportStatus("FAILED");
                upload.setErrorMessage("所有行处理失败");
            } else if (failedRows > 0) {
                upload.setImportStatus("PARTIAL_SUCCESS");
                upload.setErrorMessage("部分行处理失败，失败" + failedRows + "行");
            }

            ledgerUploadRepository.save(upload);

            // 更新最终进度
            updateProgress(progress, estimatedTotalRows, successRows, failedRows, "处理完成");

        } finally {
            // 确保Workbook被关闭
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (IOException e) {
                    log.warn("关闭Workbook时出错", e);
                }
            }
        }
    }

    /**
     * 增强的Excel数据解析方法，包含必填项验证
     */
    private void parseExcelDataWithValidation(String filePath, LedgerTemplate template,
                                              List<TemplateField> templateFields,
                                              List<RequiredFieldConfig> requiredFields,
                                              LedgerUpload upload,
                                              boolean validateRequiredFields,
                                              boolean skipInvalidRows,
                                              UploadProgress progress) throws IOException {

        File excelFile = new File(filePath);
        if (!excelFile.exists()) {
            throw new IOException("Excel文件不存在: " + filePath);
        }

        Workbook workbook = null;

        try (FileInputStream fis = new FileInputStream(excelFile)) {
            workbook = WorkbookFactory.create(fis);
            Sheet sheet = workbook.getSheetAt(0);
            int dataStartRow = template.getDataStartRow() - 1;

            // 准备进度信息
            progress.setTotalRows(0);

            // 先统计总行数
            int estimatedTotalRows = 0;
            for (int rowNum = dataStartRow; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row != null && !isEmptyRow(row)) {
                    estimatedTotalRows++;
                }
            }
            progress.setTotalRows(estimatedTotalRows);
            updateProgress(progress, estimatedTotalRows, 0, 0, "开始处理Excel数据...");

            // 批量处理数据
            Map<Integer, LedgerData> ledgerDataMap = new LinkedHashMap<>();
            Map<Integer, List<LedgerDataDetail>> detailMap = new LinkedHashMap<>();

            int processedRows = 0;
            int successRows = 0;
            int failedRows = 0;

            // 创建字段映射 - 使用修复后的方法
            Map<String, TemplateField> fieldMap = createFieldMap(templateFields);
            Set<String> requiredFieldNames = getRequiredFieldNames(requiredFields);

            // 记录详细的验证错误
            List<String> validationErrors = new ArrayList<>();

            // 处理每一行数据
            for (int rowNum = dataStartRow; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null || isEmptyRow(row)) {
                    continue;
                }

                processedRows++;

                try {
                    // 计算Excel中的行号（从1开始）
                    int excelRowNum = rowNum + 1;
                    int dataRowNumber = excelRowNum - template.getDataStartRow() + 1;

                    log.debug("处理第{}行，台账行号: {}", excelRowNum, dataRowNumber);

                    // 验证必填项
                    if (validateRequiredFields) {
                        List<String> rowValidationErrors = validateRowRequiredFields(
                                row, fieldMap, requiredFieldNames, requiredFields, excelRowNum);

                        if (!rowValidationErrors.isEmpty()) {
                            // 记录验证错误
                            validationErrors.addAll(rowValidationErrors);

                            // 严格模式：只要有一行验证失败，整个文件就失败
                            if (!skipInvalidRows) {
                                // 直接抛出异常，停止处理
                                throw new RuntimeException("第" + excelRowNum + "行必填项验证失败: " +
                                        String.join("; ", rowValidationErrors));
                            } else {
                                // 跳过无效行模式
                                failedRows++;
                                log.warn("第{}行必填项验证失败，已跳过: {}", excelRowNum, rowValidationErrors);
                                continue;
                            }
                        }
                    }

                    // 创建台账数据
                    LedgerData ledgerData = createLedgerData(row, template, upload, rowNum, dataRowNumber);

                    // 创建字段详情
                    List<LedgerDataDetail> details = createLedgerDataDetails(
                            row, fieldMap, requiredFieldNames, dataRowNumber,
                            validateRequiredFields, upload.getUserId()
                    );

                    // 使用行号作为key，保持顺序
                    ledgerDataMap.put(dataRowNumber, ledgerData);
                    detailMap.put(dataRowNumber, details);

                    successRows++;

                    // 达到批处理大小时保存
                    if (ledgerDataMap.size() >= batchSize) {
                        saveBatchDataInOrder(ledgerDataMap, detailMap, template.getUnitName());
                        ledgerDataMap.clear();
                        detailMap.clear();

                        updateProgress(progress, estimatedTotalRows, successRows, failedRows,
                                String.format("处理中... 已处理 %d/%d 行", processedRows, estimatedTotalRows));
                    }

                } catch (Exception e) {
                    failedRows++;
                    log.warn("第{}行处理失败: {}", rowNum + 1, e.getMessage());

                    // 如果是因为必填项验证失败，则添加到错误信息
                    if (e.getMessage().contains("必填项验证失败")) {
                        validationErrors.add(e.getMessage());
                    }

                    // 严格模式：有错误就停止
                    if (!skipInvalidRows) {
                        throw new RuntimeException("第" + (rowNum + 1) + "行处理失败: " + e.getMessage(), e);
                    }
                }

                // 每处理100行更新一次数据库状态
                if (processedRows % 100 == 0) {
                    upload.setTotalRows(estimatedTotalRows);
                    upload.setSuccessRows(successRows);
                    upload.setFailedRows(failedRows);
                    ledgerUploadRepository.save(upload);
                }
            }

            // 保存剩余数据
            if (!ledgerDataMap.isEmpty()) {
                saveBatchDataInOrder(ledgerDataMap, detailMap, template.getUnitName());
            }

            // 最终更新数据库
            upload.setTotalRows(estimatedTotalRows);
            upload.setSuccessRows(successRows);
            upload.setFailedRows(failedRows);

            // 根据处理结果设置状态
            if (failedRows > 0 && successRows == 0) {
                upload.setImportStatus("FAILED");
                upload.setErrorMessage("所有行处理失败");
            } else if (failedRows > 0) {
                upload.setImportStatus("PARTIAL_SUCCESS");
                upload.setErrorMessage("部分行处理失败，失败" + failedRows + "行");
            } else {
                upload.setImportStatus("SUCCESS");
            }

            ledgerUploadRepository.save(upload);

            // 更新最终进度
            updateProgress(progress, estimatedTotalRows, successRows, failedRows, "处理完成");

        } finally {
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (IOException e) {
                    log.warn("关闭Workbook时出错", e);
                }
            }
        }
    }

    /**
     * 创建字段映射 - 修复重复列名问题
     * 使用 字段标签+列索引 作为唯一键，避免重复键问题
     */
    private Map<String, TemplateField> createFieldMap(List<TemplateField> templateFields) {
        return templateFields.stream()
                .filter(field -> field.getFieldLabel() != null && !field.getFieldLabel().trim().isEmpty())
                .collect(Collectors.toMap(
                        // 使用字段标签+列字母作为复合键
                        field -> field.getFieldLabel() + "_" + field.getExcelColumn(),
                        Function.identity(),
                        // 如果真有完全相同的键，保留第一个
                        (f1, f2) -> {
                            log.warn("发现重复的字段标签+列组合: {}_{}, 保留第一个",
                                    f1.getFieldLabel(), f1.getExcelColumn());
                            return f1;
                        }
                ));
    }

    /**
     * 按列索引顺序获取字段列表
     */
    private List<TemplateField> getFieldsByColumnIndex(Map<String, TemplateField> fieldMap) {
        return fieldMap.values().stream()
                .sorted(Comparator.comparing(TemplateField::getSortOrder))
                .collect(Collectors.toList());
    }

    /**
     * 验证行的必填项 - 修复重复列名问题
     */
    private List<String> validateRowRequiredFields(Row row, Map<String, TemplateField> fieldMap,
                                                   Set<String> requiredFieldNames,
                                                   List<RequiredFieldConfig> requiredFields,
                                                   int excelRowNum) {
        List<String> errors = new ArrayList<>();

        // 通过模板字段列表直接遍历，而不是通过fieldMap
        for (TemplateField field : getFieldsByColumnIndex(fieldMap)) {
            int colIndex = excelColumnToIndex(field.getExcelColumn());
            Cell cell = row.getCell(colIndex);
            String cellValue = getCellValue(cell);

            // 检查是否为必填项
            if (requiredFieldNames.contains(field.getFieldName())) {
                if (cellValue == null || cellValue.trim().isEmpty()) {
                    // 获取必填项的自定义提示信息
                    String requiredMessage = requiredFields.stream()
                            .filter(config -> config.getFieldName().equals(field.getFieldName()))
                            .map(RequiredFieldConfig::getRequiredMessage)
                            .findFirst()
                            .orElse(field.getFieldLabel() + "为必填项");

                    errors.add("第" + excelRowNum + "行，" + field.getFieldLabel() + ": " + requiredMessage);
                }
            }
        }

        return errors;
    }

    /**
     * 按顺序批量保存数据
     */
    @Transactional
    protected void saveBatchDataInOrder(Map<Integer, LedgerData> ledgerDataMap,
                                        Map<Integer, List<LedgerDataDetail>> detailMap,
                                        String unitName) {
        if (ledgerDataMap.isEmpty()) {
            return;
        }

        try {
            // 1. 按顺序提取LedgerData
            List<LedgerData> ledgerDataList = new ArrayList<>(ledgerDataMap.values());

            // 2. 批量保存LedgerData - 单条插入以确保顺序
            List<LedgerData> savedLedgerData = new ArrayList<>();
            for (LedgerData data : ledgerDataList) {
                LedgerData savedData = ledgerDataRepository.save(data);
                savedLedgerData.add(savedData);
            }
            log.info("批量保存LedgerData成功，单位: {}，数量: {}", unitName, savedLedgerData.size());

            // 3. 建立行号到实际ID的映射
            Map<Integer, Long> rowNumberToIdMap = new HashMap<>();
            for (LedgerData savedData : savedLedgerData) {
                rowNumberToIdMap.put(savedData.getRowNumber(), savedData.getId());
            }

            // 4. 收集所有明细数据
            List<LedgerDataDetail> allDetails = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();

            // 5. 按行号顺序处理明细数据
            for (Map.Entry<Integer, List<LedgerDataDetail>> entry : detailMap.entrySet()) {
                Integer rowNumber = entry.getKey();
                Long dataId = rowNumberToIdMap.get(rowNumber);

                if (dataId == null) {
                    log.warn("找不到对应的dataId，行号: {}", rowNumber);
                    continue;
                }

                List<LedgerDataDetail> details = entry.getValue();
                for (LedgerDataDetail detail : details) {
                    LedgerDataDetail newDetail = new LedgerDataDetail();
                    newDetail.setDataId(dataId);

                    // 关键修改：使用 字段名_列字母 作为存储的字段名，避免重复字段名导致的唯一约束冲突
                    // 例如："计量单位_T" 和 "计量单位_AH"
                    String storedFieldName = detail.getFieldName();
                    if (detail.getFieldName() != null && detail.getFieldName().contains("_")) {
                        // 如果已经是复合格式，保持原样
                        storedFieldName = detail.getFieldName();
                    } else {
                        // 从原始值中提取列信息，或者使用默认
                        // 这里需要从原始数据中获取列字母，但当前设计无法获取
                        // 所以我们使用另一种方式：在创建明细时直接设置复合字段名
                        // 这需要在 createLedgerDataDetails 方法中实现
                    }

                    // 暂时使用原字段名，会在 createLedgerDataDetails 中修复
                    newDetail.setFieldName(detail.getFieldName());

                    newDetail.setFieldValue(detail.getFieldValue());
                    newDetail.setOriginalValue(detail.getOriginalValue());
                    newDetail.setIsEmpty(detail.getIsEmpty());
                    newDetail.setIsValid(detail.getIsValid());
                    newDetail.setValidationMessage(detail.getValidationMessage());
                    newDetail.setSortOrder(detail.getSortOrder());
                    newDetail.setCreatedTime(now);
                    newDetail.setUpdatedTime(now);

                    allDetails.add(newDetail);
                }
            }

            // 6. 批量保存LedgerDataDetail
            if (!allDetails.isEmpty()) {
                // 分批保存明细数据，避免一次插入过多
                int detailBatchSize = 500;
                for (int i = 0; i < allDetails.size(); i += detailBatchSize) {
                    int end = Math.min(i + detailBatchSize, allDetails.size());
                    List<LedgerDataDetail> batch = allDetails.subList(i, end);
                    ledgerDataDetailRepository.saveAll(batch);
                }
                log.info("批量保存LedgerDataDetail成功，单位: {}，数量: {}", unitName, allDetails.size());
            }

        } catch (Exception e) {
            log.error("批量保存数据失败，单位: {}", unitName, e);
            throw new RuntimeException("批量保存数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 更新进度信息
     */
    private void updateProgress(UploadProgress progress, int totalRows, int successRows,
                                int failedRows, String currentProcessing) {
        progress.setTotalRows(totalRows);
        progress.setSuccessRows(successRows);
        progress.setFailedRows(failedRows);
        progress.setCurrentProcessing(currentProcessing);

        if (totalRows > 0) {
            int processedRows = successRows + failedRows;
            int percentage = (int) ((float) processedRows / totalRows * 100);
            progress.setPercentage(Math.min(percentage, 100));
        }
    }

    /**
     * 清理临时文件
     */
    private void cleanupTempFile(String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                boolean deleted = file.delete();
                if (deleted) {
                    log.info("临时文件已删除: {}", filePath);
                } else {
                    log.warn("临时文件删除失败: {}", filePath);
                }
            }
        } catch (Exception e) {
            log.warn("清理临时文件异常: {}", filePath, e);
        }
    }

    /**
     * 安排进度信息清理
     */
    private void scheduleProgressCleanup(Long uploadId) {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                uploadProgressMap.remove(uploadId);
                timer.cancel();
            }
        }, 300000); // 5分钟后清理
    }

    /**
     * 更新上传记录的错误状态
     */
    private void updateUploadStatusOnError(Long uploadId, String errorMessage) {
        try {
            LedgerUpload upload = ledgerUploadRepository.findById(uploadId).orElse(null);
            if (upload != null) {
                upload.setImportStatus("FAILED");
                upload.setErrorMessage(errorMessage);
                upload.setCompletedTime(LocalDateTime.now());
                ledgerUploadRepository.save(upload);
            }

            // 更新进度信息
            UploadProgress progress = uploadProgressMap.get(uploadId);
            if (progress != null) {
                progress.setStatus("FAILED");
                progress.setErrorMessage(errorMessage);
            }
        } catch (Exception e) {
            log.error("更新上传状态失败，上传ID: {}", uploadId, e);
        }
    }

    /**
     * 获取上传进度
     */
    public Map<String, Object> getUploadProgress(Long uploadId) {
        UploadProgress progress = uploadProgressMap.get(uploadId);

        if (progress == null) {
            // 从数据库获取最新状态
            LedgerUpload upload = ledgerUploadRepository.findById(uploadId).orElse(null);
            if (upload == null) {
                return null;
            }

            progress = new UploadProgress(uploadId);
            progress.setTotalRows(upload.getTotalRows());
            progress.setSuccessRows(upload.getSuccessRows());
            progress.setFailedRows(upload.getFailedRows());
            progress.setStatus(upload.getImportStatus());

            if (upload.getTotalRows() > 0) {
                int processed = upload.getSuccessRows() + upload.getFailedRows();
                int percentage = (int) ((float) processed / upload.getTotalRows() * 100);
                progress.setPercentage(Math.min(percentage, 100));
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalRows", progress.getTotalRows());
        result.put("successRows", progress.getSuccessRows());
        result.put("failedRows", progress.getFailedRows());
        result.put("percentage", progress.getPercentage());
        result.put("status", progress.getStatus());
        result.put("currentProcessing", progress.getCurrentProcessing());
        result.put("errorMessage", progress.getErrorMessage());

        return result;
    }

    /**
     * 获取必填字段名集合
     */
    private Set<String> getRequiredFieldNames(List<RequiredFieldConfig> requiredFields) {
        return requiredFields.stream()
                .map(RequiredFieldConfig::getFieldName)
                .collect(Collectors.toSet());
    }

    /**
     * 判断是否为空行
     */
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

    /**
     * 获取单元格值 - FIXED: 修复整数溢出问题
     */
    private String getCellValue(Cell cell) {
        if (cell == null) {
            return null;
        }

        CellType cellType = cell.getCellType();
        if (cellType == CellType.FORMULA) {
            cellType = cell.getCachedFormulaResultType();
        }

        switch (cellType) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    try {
                        // 日期类型
                        return cell.getDateCellValue().toString();
                    } catch (Exception e) {
                        // 如果日期解析失败，使用原始数值
                        return String.valueOf(cell.getNumericCellValue());
                    }
                } else {
                    // 数字类型 - 修复整数溢出问题
                    double numericValue = cell.getNumericCellValue();

                    // 使用BigDecimal来避免精度损失
                    BigDecimal bd = BigDecimal.valueOf(numericValue);

                    // 尝试判断是否为整数
                    try {
                        if (bd.stripTrailingZeros().scale() <= 0) {
                            // 整数
                            try {
                                // 先尝试转为long
                                long longValue = bd.longValueExact();

                                // 检查是否在int范围内
                                if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                                    return String.valueOf((int) longValue);
                                } else {
                                    // 超出int范围，使用long
                                    return String.valueOf(longValue);
                                }
                            } catch (ArithmeticException e) {
                                // 不是精确的整数，使用BigDecimal字符串
                                return bd.toPlainString();
                            }
                        } else {
                            // 小数
                            return bd.toPlainString();
                        }
                    } catch (Exception e) {
                        log.warn("转换数字单元格时出错，使用原始值: {}", numericValue, e);
                        return String.valueOf(numericValue);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case BLANK:
                return null;
            case ERROR:
                // 错误单元格
                log.warn("单元格包含错误值: {}", FormulaError.forInt(cell.getErrorCellValue()));
                return null;
            default:
                return null;
        }
    }

    /**
     * 备用方法：使用DataFormatter获取单元格的显示值
     */
    private String getCellValueUsingDataFormatter(Cell cell) {
        if (cell == null) {
            return null;
        }

        try {
            // 使用DataFormatter获取单元格显示的值
            String value = dataFormatter.formatCellValue(cell);

            // 如果单元格是数字类型，可能包含小数位（如123.0），我们去掉小数部分
            if (cell.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
                // 尝试解析为数字
                try {
                    double doubleValue = cell.getNumericCellValue();
                    if (doubleValue == Math.floor(doubleValue) && !Double.isInfinite(doubleValue)) {
                        // 是整数，但可能很大
                        try {
                            // 使用BigDecimal避免精度损失
                            BigDecimal bd = BigDecimal.valueOf(doubleValue);
                            // 如果是整数，去掉小数位
                            if (bd.stripTrailingZeros().scale() <= 0) {
                                try {
                                    long longValue = bd.longValueExact();
                                    if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                                        return String.valueOf((int) longValue);
                                    } else {
                                        return String.valueOf(longValue);
                                    }
                                } catch (ArithmeticException e) {
                                    // 不是精确整数
                                    return bd.toPlainString();
                                }
                            }
                        } catch (Exception e) {
                            // 使用原始字符串
                        }
                    }
                } catch (Exception e) {
                    // 保持原值
                }
            }

            return value != null ? value.trim() : null;
        } catch (Exception e) {
            log.warn("使用DataFormatter获取单元格值失败", e);
            return null;
        }
    }

    /**
     * 创建台账数据对象
     */
    private LedgerData createLedgerData(Row row, LedgerTemplate template,
                                        LedgerUpload upload, int excelRowNum, int dataRowNumber) {
        LedgerData ledgerData = new LedgerData();
        ledgerData.setUploadId(upload.getId());
        ledgerData.setTemplateId(template.getId());
        ledgerData.setUnitName(template.getUnitName());
        ledgerData.setRowNumber(dataRowNumber); // 使用计算后的行号

        ledgerData.setDataStatus("ACTIVE");
        ledgerData.setValidationStatus("VALID");
        ledgerData.setDeleted(false);
        ledgerData.setCreatedBy(upload.getUserId());

        LocalDateTime now = LocalDateTime.now();
        ledgerData.setCreatedTime(now);
        ledgerData.setUpdatedTime(now);

        return ledgerData;
    }

    /**
     * 创建字段详情 - 修复重复列名问题
     * 关键修改：使用 字段名_列字母 作为存储的字段名
     */
    private List<LedgerDataDetail> createLedgerDataDetails(Row row, Map<String, TemplateField> fieldMap,
                                                           Set<String> requiredFieldNames,
                                                           int rowNumber,
                                                           boolean validateRequiredFields,
                                                           Long userId) {
        List<LedgerDataDetail> details = new ArrayList<>();

        // 按列索引顺序处理
        for (TemplateField field : getFieldsByColumnIndex(fieldMap)) {
            int colIndex = excelColumnToIndex(field.getExcelColumn());
            Cell cell = row.getCell(colIndex);

            LedgerDataDetail detail = new LedgerDataDetail();
            detail.setDataId((long) rowNumber); // 临时使用，会在保存时替换

            // 关键修改：使用 字段名_列字母 作为存储的字段名，避免重复字段名导致的唯一约束冲突
            // 例如："计量单位_T" 和 "计量单位_AH"
            String storedFieldName = field.getFieldName() + "_" + field.getExcelColumn();
            detail.setFieldName(storedFieldName);

            detail.setSortOrder(field.getSortOrder());

            String cellValue = getCellValue(cell);
            detail.setOriginalValue(cellValue);

            // 验证必填项
            if (validateRequiredFields && requiredFieldNames.contains(field.getFieldName())) {
                if (cellValue == null || cellValue.trim().isEmpty()) {
                    detail.setIsValid(false);
                    detail.setValidationMessage(field.getFieldLabel() + "为必填项");
                    detail.setIsEmpty(true);
                    detail.setFieldValue(null);
                } else {
                    detail.setIsValid(true);
                    detail.setFieldValue(cellValue.trim());
                    detail.setIsEmpty(false);
                }
            } else {
                detail.setIsValid(true);
                if (cellValue != null && !cellValue.trim().isEmpty()) {
                    detail.setFieldValue(cellValue.trim());
                    detail.setIsEmpty(false);
                } else {
                    detail.setIsEmpty(true);
                    detail.setFieldValue(null);
                }
            }

            details.add(detail);
        }

        return details;
    }

    /**
     * Excel列字母转索引
     */
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

    /**
     * 生成上传批次号
     */
    private String generateUploadNo() {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
        int randomNum = new Random().nextInt(1000);
        return "UPLOAD_" + dateStr + "_" + timeStr + "_" + String.format("%03d", randomNum);
    }

    /**
     * 创建上传记录
     */
    private LedgerUpload createUploadRecord(LedgerUploadRequest request, LedgerTemplate template,
                                            Long userId, String uploadIp, String uploadNo, String filePath) {
        LedgerUpload upload = new LedgerUpload();
        upload.setUploadNo(uploadNo);
        upload.setUserId(userId);
        upload.setUnitName(template.getUnitName());
        upload.setTemplateId(template.getId());
        upload.setFileName(request.getFile().getOriginalFilename());
        upload.setFilePath(filePath);
        upload.setFileSize(request.getFile().getSize());
        upload.setUploadIp(uploadIp);
        upload.setUploadTime(LocalDateTime.now());
        upload.setDeleted(false);
        upload.setImportStatus("PROCESSING");
        upload.setTotalRows(0);
        upload.setSuccessRows(0);
        upload.setFailedRows(0);
        return upload;
    }

    /**
     * 创建初始响应（包含数据统计）
     */
    private LedgerUploadResponse createInitialResponse(LedgerUpload upload, LedgerTemplate template, Long existingDataCount) {
        LedgerUploadResponse response = new LedgerUploadResponse();
        response.setId(upload.getId());
        response.setUploadNo(upload.getUploadNo());
        response.setUserId(upload.getUserId());
        response.setUnitName(upload.getUnitName());
        response.setTemplateId(upload.getTemplateId());
        response.setFileName(upload.getFileName());
        response.setFileSize(upload.getFileSize());
        response.setImportStatus(upload.getImportStatus());
        response.setUploadIp(upload.getUploadIp());
        response.setUploadTime(upload.getUploadTime());
        response.setTemplateName(template.getTemplateName());

        // 添加覆盖更新的信息
        if (existingDataCount != null && existingDataCount > 0) {
            response.setMessage("发现 " + existingDataCount + " 条旧数据，将执行覆盖更新");
            response.setOldDataCount(existingDataCount.intValue());
            response.setIsCoverageUpdate(true);
        } else {
            response.setMessage("开始上传处理");
            response.setIsCoverageUpdate(false);
        }

        return response;
    }

    /**
     * 转换上传记录为响应DTO
     */
    private LedgerUploadResponse convertToResponse(LedgerUpload upload, LedgerTemplate template, Long userId) {
        if (upload == null) {
            return null;
        }

        LedgerUploadResponse response = new LedgerUploadResponse();
        response.setId(upload.getId());
        response.setUploadNo(upload.getUploadNo());
        response.setUserId(upload.getUserId());
        response.setUnitName(upload.getUnitName());
        response.setTemplateId(upload.getTemplateId());
        response.setFileName(upload.getFileName());
        response.setFileSize(upload.getFileSize());
        response.setTotalRows(upload.getTotalRows());
        response.setSuccessRows(upload.getSuccessRows());
        response.setFailedRows(upload.getFailedRows());
        response.setImportStatus(upload.getImportStatus());
        response.setErrorMessage(upload.getErrorMessage());
        response.setUploadIp(upload.getUploadIp());
        response.setUploadTime(upload.getUploadTime());
        response.setCompletedTime(upload.getCompletedTime());

        if (template != null) {
            response.setTemplateName(template.getTemplateName());
        }

        return response;
    }

    /**
     * 获取当前用户ID
     */
    private Long getCurrentUserId() {
        return securityUtil.getCurrentUserId();
    }

    /**
     * 获取上传记录列表
     */
    public List<LedgerUploadResponse> getUploadHistory(String unitName, Long userId) {
        List<LedgerUpload> uploads;

        if (unitName != null) {
            uploads = ledgerUploadRepository.findByUnitNameAndDeletedFalse(unitName);
        } else if (userId != null) {
            uploads = ledgerUploadRepository.findByUserIdAndDeletedFalse(userId,
                    org.springframework.data.domain.Pageable.unpaged()).getContent();
        } else {
            uploads = ledgerUploadRepository.findAllActive(
                    org.springframework.data.domain.Pageable.unpaged()).getContent();
        }

        return uploads.stream()
                .map(u -> {
                    try {
                        LedgerTemplate template = ledgerTemplateRepository.findById(u.getTemplateId()).orElse(null);
                        return convertToResponse(u, template, u.getUserId());
                    } catch (Exception e) {
                        log.error("转换上传记录失败: {}", u.getId(), e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 获取上传详情
     */
    public LedgerUploadResponse getUploadDetail(Long uploadId) {
        LedgerUpload upload = ledgerUploadRepository.findById(uploadId)
                .orElseThrow(() -> new RuntimeException("上传记录不存在"));

        LedgerTemplate template = ledgerTemplateRepository.findById(upload.getTemplateId())
                .orElse(null);

        return convertToResponse(upload, template, upload.getUserId());
    }

    /**
     * 删除上传记录（逻辑删除）
     */
    @Transactional
    public void deleteUpload(Long uploadId) {
        LedgerUpload upload = ledgerUploadRepository.findById(uploadId)
                .orElseThrow(() -> new RuntimeException("上传记录不存在"));

        // 同时删除相关的台账数据
        List<LedgerData> ledgerDataList = ledgerDataRepository.findByUploadIdAndDeletedFalse(uploadId);
        for (LedgerData data : ledgerDataList) {
            data.setDeleted(true);
            data.setUpdatedTime(LocalDateTime.now());
        }
        ledgerDataRepository.saveAll(ledgerDataList);

        upload.setDeleted(true);
        upload.setCompletedTime(LocalDateTime.now());
        ledgerUploadRepository.save(upload);

        // 清理进度信息
        uploadProgressMap.remove(uploadId);

        log.info("删除上传记录成功，ID: {}", uploadId);
    }

    /**
     * 上传进度内部类
     */
    private static class UploadProgress {
        private final Long uploadId;
        private int totalRows;
        private int successRows;
        private int failedRows;
        private int percentage;
        private String status = "PROCESSING";
        private String currentProcessing = "";
        private String errorMessage = "";

        public UploadProgress(Long uploadId) {
            this.uploadId = uploadId;
        }

        // getters and setters
        public Long getUploadId() { return uploadId; }
        public int getTotalRows() { return totalRows; }
        public void setTotalRows(int totalRows) { this.totalRows = totalRows; }
        public int getSuccessRows() { return successRows; }
        public void setSuccessRows(int successRows) { this.successRows = successRows; }
        public int getFailedRows() { return failedRows; }
        public void setFailedRows(int failedRows) { this.failedRows = failedRows; }
        public int getPercentage() { return percentage; }
        public void setPercentage(int percentage) { this.percentage = percentage; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getCurrentProcessing() { return currentProcessing; }
        public void setCurrentProcessing(String currentProcessing) { this.currentProcessing = currentProcessing; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
}