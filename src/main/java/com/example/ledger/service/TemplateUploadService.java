package com.example.ledger.service;

/**
 * @author 霜月
 * @create 2025/12/20 21:52
 */

import com.example.ledger.entity.LedgerTemplate;
import com.example.ledger.entity.TemplateField;
import com.example.ledger.entity.TemplateStyle;
import com.example.ledger.repository.LedgerTemplateRepository;
import com.example.ledger.repository.TemplateFieldRepository;
import com.example.ledger.repository.TemplateStyleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateUploadService {

    private final LedgerTemplateRepository ledgerTemplateRepository;
    private final TemplateFieldRepository templateFieldRepository;
    private final TemplateStyleRepository templateStyleRepository;
    private final FileStorageService fileStorageService;

    /**
     * 上传并解析模板文件
     */
    @Transactional
    public void uploadAndParseTemplate(Long templateId, MultipartFile file) throws IOException {
        LedgerTemplate template = ledgerTemplateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("模板不存在"));

        // 1. 存储模板文件
        String filePath = fileStorageService.storeTemplateFile(file, template.getUnitName());

        // 2. 解析模板文件
        parseTemplateFile(templateId, file);

        // 3. 更新模板信息
        template.setTemplateFilePath(filePath);
        template.setTemplateFileName(file.getOriginalFilename());
        template.setHasTemplateFile(true);
        template.setUpdatedTime(LocalDateTime.now());

        ledgerTemplateRepository.save(template);
        log.info("模板文件上传成功，模板ID: {}, 文件: {}", templateId, file.getOriginalFilename());
    }

    /**
     * 解析模板文件，提取样式和字段定义
     */
    private void parseTemplateFile(Long templateId, MultipartFile file) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            // 1. 获取表头行数（默认4行）
            int headerRowCount = 4;

            // 2. 解析前4行样式
            parseHeaderStylesFixed(templateId, sheet, headerRowCount);

            // 3. 解析字段定义（第4行）
            parseFieldDefinitions(templateId, sheet, headerRowCount - 1);

            // 4. 更新模板的列数
            updateTemplateColumnCount(templateId, sheet);

            log.info("模板解析完成，模板ID: {}", templateId);
        }
    }

    /**
     * 解析表头样式
     */
    private void parseHeaderStylesFixed(Long templateId, Sheet sheet, int headerRowCount) {
        try {
            // 先查询再删除（更安全）
            List<TemplateStyle> existingStyles = templateStyleRepository.findByTemplateId(templateId);
            if (!existingStyles.isEmpty()) {
                log.info("删除模板 {} 的旧样式数据 {} 条", templateId, existingStyles.size());
                templateStyleRepository.deleteAll(existingStyles);
                templateStyleRepository.flush(); // 强制刷新
            }

            List<TemplateStyle> styles = new ArrayList<>();
            List<CellRangeAddress> mergedRegions = sheet.getMergedRegions();

            // 解析表头行
            for (int rowIndex = 0; rowIndex < headerRowCount; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;

                for (int colIndex = 0; colIndex < row.getLastCellNum(); colIndex++) {
                    Cell cell = row.getCell(colIndex);
                    if (cell == null) continue;

                    TemplateStyle style = createTemplateStyle(templateId, rowIndex, colIndex, cell, mergedRegions);
                    styles.add(style);
                }
            }

            // 保存样式 - 批量插入
            if (!styles.isEmpty()) {
                templateStyleRepository.saveAllAndFlush(styles);
                log.info("保存表头样式 {} 条，模板ID: {}", styles.size(), templateId);
            }
        } catch (Exception e) {
            log.error("解析表头样式失败，templateId: {}", templateId, e);
            throw new RuntimeException("解析表头样式失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建模板样式
     */
    private TemplateStyle createTemplateStyle(Long templateId, int rowIndex, int colIndex,
                                              Cell cell, List<CellRangeAddress> mergedRegions) {
        TemplateStyle style = new TemplateStyle();
        style.setTemplateId(templateId);
        style.setRowIndex(rowIndex);
        style.setColumnIndex(colIndex);
        style.setCellValue(getCellValue(cell));

        // 检查合并单元格
        CellRangeAddress mergedRegion = findMergedRegion(mergedRegions, rowIndex, colIndex);
        if (mergedRegion != null) {
            style.setIsMerged(true);
            style.setMergeStartRow(mergedRegion.getFirstRow());
            style.setMergeEndRow(mergedRegion.getLastRow());
            style.setMergeStartCol(mergedRegion.getFirstColumn());
            style.setMergeEndCol(mergedRegion.getLastColumn());
        }

        // 提取样式
        extractCellStyle(style, cell);

        return style;
    }

    /**
     * 解析字段定义 - 支持重复列名
     */
    private void parseFieldDefinitions(Long templateId, Sheet sheet, int headerRowIndex) {
        // 先删除旧的字段定义（逻辑删除）
        List<TemplateField> oldFields = templateFieldRepository.findByTemplateIdAndDeletedFalse(templateId);
        if (!oldFields.isEmpty()) {
            oldFields.forEach(field -> field.setDeleted(true));
            templateFieldRepository.saveAll(oldFields);
        }

        // 获取合并单元格信息
        List<CellRangeAddress> mergedRegions = sheet.getMergedRegions();
        List<TemplateField> fields = new ArrayList<>();

        // 确定列数（取最大的列索引）
        int maxColumnIndex = 0;
        for (int rowIndex = 0; rowIndex <= headerRowIndex; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null) {
                maxColumnIndex = Math.max(maxColumnIndex, row.getLastCellNum() - 1);
            }
        }

        // 遍历每一列
        for (int colIndex = 0; colIndex <= maxColumnIndex; colIndex++) {
            String headerText = null;
            String columnLetter = getColumnLetter(colIndex);

            // 方法1：优先取第4行的值
            Row row4 = sheet.getRow(headerRowIndex);
            if (row4 != null) {
                Cell cell4 = row4.getCell(colIndex);
                headerText = getCellValue(cell4);
            }

            // 方法2：如果第4行为空，检查是否是合并单元格，向上查找
            if (headerText == null || headerText.trim().isEmpty()) {
                CellRangeAddress mergedRegion = findMergedRegion(mergedRegions, headerRowIndex, colIndex);
                if (mergedRegion != null) {
                    Row firstRow = sheet.getRow(mergedRegion.getFirstRow());
                    if (firstRow != null) {
                        Cell firstCell = firstRow.getCell(mergedRegion.getFirstColumn());
                        headerText = getCellValue(firstCell);
                    }
                }
            }

            // 方法3：如果还是空，向上遍历查找非空值
            if (headerText == null || headerText.trim().isEmpty()) {
                for (int rowIndex = headerRowIndex - 1; rowIndex >= 0; rowIndex--) {
                    Row row = sheet.getRow(rowIndex);
                    if (row != null) {
                        Cell cell = row.getCell(colIndex);
                        String cellValue = getCellValue(cell);
                        if (cellValue != null && !cellValue.trim().isEmpty()) {
                            headerText = cellValue;
                            break;
                        }
                    }
                }
            }

            // 方法4：如果所有方法都失败，使用默认名称
            if (headerText == null || headerText.trim().isEmpty()) {
                headerText = "字段_" + (colIndex + 1);
            }

            // 清理headerText
            headerText = cleanHeaderText(headerText);

            // 创建字段定义
            TemplateField field = new TemplateField();
            field.setTemplateId(templateId);

            // 允许相同的fieldName，通过excel_column区分
            field.setFieldName(generateFieldName(headerText));
            field.setFieldLabel(headerText);
            field.setExcelColumn(columnLetter); // 这是关键，确保每个列都有唯一标识
            field.setExcelHeader(headerText);
            field.setSortOrder(colIndex);

            // 智能识别字段类型
            field.setFieldType(determineFieldType(headerText));
            field.setDeleted(false);
            field.setCreatedTime(LocalDateTime.now());
            field.setUpdatedTime(LocalDateTime.now());

            // 对于重复列名，可以在描述中注明重复信息
            if (isCommonRepeatedColumn(headerText)) {
                field.setDescription("重复列，位置: " + columnLetter);
            }

            fields.add(field);
        }

        // 保存字段定义
        if (!fields.isEmpty()) {
            templateFieldRepository.saveAll(fields);
            log.info("保存字段定义 {} 条，模板ID: {}", fields.size(), templateId);
        }
    }

    /**
     * 判断是否为常见的重复列
     */
    private boolean isCommonRepeatedColumn(String headerText) {
        if (headerText == null) return false;

        String text = headerText.toLowerCase();
        return text.contains("计量单位") ||
                text.contains("时间") ||
                text.contains("erp") ||
                text.contains("订单号");
    }

    /**
     * 清理表头文本
     */
    private String cleanHeaderText(String headerText) {
        if (headerText == null) return "";

        // 移除换行符，替换为空格
        headerText = headerText.replace("\n", " ")
                .replace("\r", " ")
                .trim();

        // 合并多个空格
        headerText = headerText.replaceAll("\\s+", " ");

        return headerText;
    }

    /**
     * 智能识别字段类型
     */
    private String determineFieldType(String headerText) {
        if (headerText == null) return "STRING";

        headerText = headerText.toLowerCase();

        // 日期/时间字段
        if (headerText.contains("时间") ||
                headerText.contains("日期") ||
                headerText.contains("time") ||
                headerText.contains("date")) {
            return "DATE";
        }

        // 数字字段
        if (headerText.contains("数量") ||
                headerText.contains("单价") ||
                headerText.contains("总价") ||
                headerText.contains("金额") ||
                headerText.contains("number") ||
                headerText.contains("price") ||
                headerText.contains("amount") ||
                headerText.contains("码") ||
                headerText.matches(".*[0-9]+.*")) {
            return "NUMBER";
        }

        // 布尔字段
        if (headerText.contains("是否") ||
                headerText.contains("bool") ||
                headerText.contains("flag")) {
            return "BOOLEAN";
        }

        return "STRING";
    }

    /**
     * 更新模板列数
     */
    private void updateTemplateColumnCount(Long templateId, Sheet sheet) {
        Row firstRow = sheet.getRow(0);
        if (firstRow != null) {
            int columnCount = firstRow.getLastCellNum();

            LedgerTemplate template = ledgerTemplateRepository.findById(templateId)
                    .orElseThrow(() -> new RuntimeException("模板不存在"));

            template.setColumnCount(columnCount);
            template.setUpdatedTime(LocalDateTime.now());

            ledgerTemplateRepository.save(template);
            log.info("更新模板列数: {} -> {}", templateId, columnCount);
        }
    }

    /**
     * 生成字段名称 - 允许重复
     */
    private String generateFieldName(String headerText) {
        if (headerText == null || headerText.trim().isEmpty()) {
            return "field";
        }

        // 移除特殊字符，只保留字母、数字、下划线
        String fieldName = headerText.trim()
                .replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9_]", "_")
                .replaceAll("_+", "_");

        // 如果开头是数字，添加前缀
        if (fieldName.matches("^\\d.*")) {
            fieldName = "field_" + fieldName;
        }

        // 如果fieldName是纯中文，转换为拼音或者使用默认
        if (fieldName.matches("[\\u4e00-\\u9fa5]+")) {
            // 这里可以添加中文转拼音的逻辑
            // 暂时使用简化的处理：直接使用中文作为字段名
            return fieldName.toLowerCase();
        }

        return fieldName.toLowerCase();
    }

    /**
     * 获取单元格值
     */
    private String getCellValue(Cell cell) {
        if (cell == null) return "";

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
                return "";
        }
    }

    /**
     * 查找合并单元格
     */
    private CellRangeAddress findMergedRegion(List<CellRangeAddress> mergedRegions, int row, int col) {
        for (CellRangeAddress region : mergedRegions) {
            if (region.isInRange(row, col)) {
                return region;
            }
        }
        return null;
    }

    /**
     * 提取单元格样式
     */
    private void extractCellStyle(TemplateStyle style, Cell cell) {
        CellStyle cellStyle = cell.getCellStyle();

        // 字体
        Font font = cell.getSheet().getWorkbook().getFontAt(cellStyle.getFontIndex());
        style.setFontName(font.getFontName());
        style.setFontBold(font.getBold());
        style.setFontSize((int) font.getFontHeightInPoints());

        // 对齐方式
        switch (cellStyle.getAlignment()) {
            case CENTER:
                style.setAlignment("CENTER");
                break;
            case RIGHT:
                style.setAlignment("RIGHT");
                break;
            default:
                style.setAlignment("LEFT");
        }

        // 垂直对齐
        switch (cellStyle.getVerticalAlignment()) {
            case TOP:
                style.setVerticalAlignment("TOP");
                break;
            case BOTTOM:
                style.setVerticalAlignment("BOTTOM");
                break;
            default:
                style.setVerticalAlignment("CENTER");
        }

        // 边框
        style.setBorderTop(cellStyle.getBorderTop() != BorderStyle.NONE);
        style.setBorderBottom(cellStyle.getBorderBottom() != BorderStyle.NONE);
        style.setBorderLeft(cellStyle.getBorderLeft() != BorderStyle.NONE);
        style.setBorderRight(cellStyle.getBorderRight() != BorderStyle.NONE);

        // 单元格格式
        style.setCellFormat(cellStyle.getDataFormatString());
    }

    /**
     * 列索引转字母
     */
    private String getColumnLetter(int columnIndex) {
        StringBuilder columnLetter = new StringBuilder();
        while (columnIndex >= 0) {
            columnLetter.insert(0, (char) ('A' + (columnIndex % 26)));
            columnIndex = (columnIndex / 26) - 1;
        }
        return columnLetter.toString();
    }
}