package com.example.ledger.service;

/**
 * @author 霜月
 * @create 2025/12/20 21:30
 */

import com.example.ledger.entity.*;
import com.example.ledger.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelExportService {

    private final LedgerTemplateRepository ledgerTemplateRepository;
    private final TemplateFieldRepository templateFieldRepository;
    private final TemplateStyleRepository templateStyleRepository;
    private final LedgerDataRepository ledgerDataRepository;
    private final LedgerDataDetailRepository ledgerDataDetailRepository;
    private final FileStorageService fileStorageService;

    /**
     * 根据模板ID获取所有数据ID
     */
    public List<Long> getAllDataIdsByTemplate(Long templateId) {
        log.info("获取模板 {} 的所有数据ID", templateId);

        List<LedgerData> dataList = ledgerDataRepository.findByTemplateIdAndDeletedFalse(templateId);
        if (dataList.isEmpty()) {
            log.info("模板 {} 没有数据", templateId);
            return Collections.emptyList();
        }

        List<Long> dataIds = dataList.stream()
                .map(LedgerData::getId)
                .collect(Collectors.toList());

        log.info("模板 {} 共找到 {} 条数据", templateId, dataIds.size());
        return dataIds;
    }

    /**
     * 导出台账数据（混合模式）- 完全重写，修复数据空白问题
     */
    @Transactional(readOnly = true)
    public byte[] exportLedgerData(Long templateId, List<Long> dataIds) throws IOException {
        log.info("开始导出数据，模板ID: {}, 数据ID数量: {}", templateId, dataIds == null ? 0 : dataIds.size());

        // 验证参数
        if (templateId == null) {
            throw new IllegalArgumentException("模板ID不能为空");
        }

        if (dataIds == null || dataIds.isEmpty()) {
            log.warn("导出数据ID列表为空，将导出空表格");
            return createEmptyWorkbook(templateId);
        }

        try {
            // 获取模板信息
            LedgerTemplate template = ledgerTemplateRepository.findById(templateId)
                    .orElseThrow(() -> new RuntimeException("模板不存在，ID: " + templateId));

            log.info("找到模板: {}, 单位: {}", template.getTemplateName(), template.getUnitName());

            // 获取字段定义
            List<TemplateField> fields = templateFieldRepository.findByTemplateIdAndDeletedFalse(templateId);
            if (fields.isEmpty()) {
                throw new RuntimeException("模板字段定义为空，模板ID: " + templateId);
            }

            log.info("获取到 {} 个字段定义", fields.size());

            // 获取要导出的数据
            List<LedgerData> dataList = ledgerDataRepository.findByIdInAndDeletedFalse(dataIds);
            if (dataList.isEmpty()) {
                log.warn("未找到对应的数据记录，将导出空表格");
                return createEmptyWorkbook(templateId);
            }

            log.info("获取到 {} 条数据记录", dataList.size());

            // 获取数据详情
            Map<Long, List<LedgerDataDetail>> detailsMap = getDataDetails(dataList);

            // 创建用于调试的输出
            debugDataDetails(dataList, detailsMap);

            Workbook workbook;

            // 混合模式：优先使用模板文件，否则动态生成
            boolean hasTemplateFile = template.getHasTemplateFile() != null &&
                    template.getHasTemplateFile() &&
                    template.getTemplateFilePath() != null &&
                    !template.getTemplateFilePath().trim().isEmpty();

            if (hasTemplateFile) {
                try {
                    log.info("尝试使用模板文件导出: {}", template.getTemplateFilePath());
                    workbook = exportWithTemplateFileCompletely(template, fields, dataList, detailsMap);
                } catch (Exception e) {
                    log.error("模板文件导出失败，将使用动态生成方式", e);
                    workbook = exportWithDynamicGeneration(template, fields, dataList, detailsMap);
                }
            } else {
                log.info("使用动态生成方式导出");
                workbook = exportWithDynamicGeneration(template, fields, dataList, detailsMap);
            }

            // 写入字节数组
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                workbook.write(outputStream);
                workbook.close();
                byte[] result = outputStream.toByteArray();
                log.info("导出成功，文件大小: {} bytes", result.length);
                return result;
            }

        } catch (Exception e) {
            log.error("导出过程中发生异常", e);
            throw new IOException("导出失败: " + e.getMessage(), e);
        }
    }

    /**
     * 直接填充行数据 - 根据列索引映射直接填充
     */
    private void fillRowDataDirectly(Row row, Map<Integer, String> columnToFieldMap,
                                     List<LedgerDataDetail> details) {
        if (details == null || details.isEmpty()) {
            log.debug("行 {} 没有明细数据", row.getRowNum());
            return;
        }

        // 创建字段值映射
        Map<String, String> detailMap = new HashMap<>();
        for (LedgerDataDetail detail : details) {
            detailMap.put(detail.getFieldName(),
                    detail.getFieldValue() != null ? detail.getFieldValue() : "");
        }

        // 为每个映射的列创建单元格
        for (Map.Entry<Integer, String> entry : columnToFieldMap.entrySet()) {
            int columnIndex = entry.getKey();
            String fieldName = entry.getValue();
            String value = detailMap.get(fieldName);

            if (value != null) {
                Cell cell = row.createCell(columnIndex);
                cell.setCellValue(value);

                if (!value.trim().isEmpty()) {
                    log.trace("填充: 行{},列{} = {}", row.getRowNum(), columnIndex, value);
                }
            } else {
                log.warn("字段 {} 在数据中没有对应的值", fieldName);
            }
        }
    }

    /**
     * 检查重复映射
     */
    private void checkDuplicateMappings(Map<Integer, String> columnToFieldMap, List<TemplateField> fields) {
        // 检查是否有多个字段映射到同一列
        Map<Integer, List<String>> indexToFields = new HashMap<>();
        for (TemplateField field : fields) {
            try {
                int columnIndex = excelColumnToIndex(field.getExcelColumn());
                indexToFields.computeIfAbsent(columnIndex, k -> new ArrayList<>())
                        .add(field.getFieldLabel() + " (" + field.getFieldName() + ")");
            } catch (Exception e) {
                // 忽略无法映射的字段
            }
        }

        for (Map.Entry<Integer, List<String>> entry : indexToFields.entrySet()) {
            if (entry.getValue().size() > 1) {
                log.warn("警告: 列 {} 有多个字段映射: {}", entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Excel列字母转索引（A=0, B=1...）
     */
    private int excelColumnToIndex(String excelColumn) {
        if (excelColumn == null || excelColumn.trim().isEmpty()) {
            throw new IllegalArgumentException("Excel列字母不能为空");
        }

        String column = excelColumn.toUpperCase().trim();
        int index = 0;
        for (int i = 0; i < column.length(); i++) {
            char c = column.charAt(i);
            if (c < 'A' || c > 'Z') {
                throw new IllegalArgumentException("无效的Excel列字母: " + excelColumn);
            }
            index = index * 26 + (c - 'A' + 1);
        }
        return index - 1;
    }

    /**
     * 创建空工作簿
     */
    private byte[] createEmptyWorkbook(Long templateId) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("台账数据");

        Row titleRow = sheet.createRow(0);
        titleRow.createCell(0).setCellValue("台账导出");

        Row infoRow = sheet.createRow(1);
        infoRow.createCell(0).setCellValue("模板ID: " + templateId);
        infoRow.createCell(1).setCellValue("导出时间: " + LocalDateTime.now());

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            workbook.write(outputStream);
            workbook.close();
            return outputStream.toByteArray();
        }
    }

    /**
     * 方式2：动态生成Excel（基于模板样式）- 作为后备方案
     */
    private Workbook exportWithDynamicGeneration(LedgerTemplate template,
                                                 List<TemplateField> fields,
                                                 List<LedgerData> dataList,
                                                 Map<Long, List<LedgerDataDetail>> detailsMap) {

        log.info("动态生成Excel，单位: {}, 数据量: {}", template.getUnitName(), dataList.size());

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("台账");

        // 1. 生成表头（前4行）
        generateHeaderRows(workbook, sheet, template, fields);

        // 2. 生成数据行
        int dataStartRow = template.getDataStartRow() != null ? template.getDataStartRow() : 5;
        int currentRowNum = dataStartRow - 1; // 数据起始行

        for (LedgerData data : dataList) {
            Row row = sheet.createRow(currentRowNum++);
            fillRowDataSimple(row, fields, detailsMap.get(data.getId()));
        }

        // 3. 自动调整列宽
        for (int i = 0; i < fields.size(); i++) {
            sheet.autoSizeColumn(i);
        }

        log.info("动态生成完成，共写入 {} 行数据", dataList.size());
        return workbook;
    }

    /**
     * 简单填充行数据
     */
    private void fillRowDataSimple(Row row, List<TemplateField> fields, List<LedgerDataDetail> details) {
        if (details == null || details.isEmpty()) {
            return;
        }

        Map<String, String> detailMap = details.stream()
                .collect(Collectors.toMap(LedgerDataDetail::getFieldName,
                        detail -> detail.getFieldValue() != null ? detail.getFieldValue() : "",
                        (v1, v2) -> v1));

        for (int i = 0; i < fields.size(); i++) {
            TemplateField field = fields.get(i);
            String value = detailMap.get(field.getFieldName());
            Cell cell = row.createCell(i);

            if (value != null && !value.trim().isEmpty()) {
                cell.setCellValue(value);
            }
        }
    }

    /**
     * 生成表头行（基于模板样式表）
     */
    private void generateHeaderRows(Workbook workbook, Sheet sheet,
                                    LedgerTemplate template, List<TemplateField> fields) {

        // 获取模板样式
        List<TemplateStyle> styles = templateStyleRepository.findByTemplateId(template.getId());

        if (styles.isEmpty()) {
            // 如果没有样式，生成简单表头
            generateSimpleHeader(workbook, sheet, template, fields);
        } else {
            // 使用存储的样式生成表头
            generateStyledHeader(workbook, sheet, template, styles);
        }
    }

    /**
     * 使用存储的样式生成表头
     */
    private void generateStyledHeader(Workbook workbook, Sheet sheet,
                                      LedgerTemplate template, List<TemplateStyle> styles) {

        // 按行分组
        Map<Integer, List<TemplateStyle>> stylesByRow = styles.stream()
                .collect(Collectors.groupingBy(TemplateStyle::getRowIndex));

        // 处理合并单元格
        Set<String> mergedRegions = new HashSet<>();

        for (TemplateStyle style : styles) {
            if (style.getIsMerged() != null && style.getIsMerged()) {
                String regionKey = style.getMergeStartRow() + "-" + style.getMergeEndRow() + "-" +
                        style.getMergeStartCol() + "-" + style.getMergeEndCol();
                if (!mergedRegions.contains(regionKey)) {
                    CellRangeAddress region = new CellRangeAddress(
                            style.getMergeStartRow(),
                            style.getMergeEndRow(),
                            style.getMergeStartCol(),
                            style.getMergeEndCol()
                    );
                    sheet.addMergedRegion(region);
                    mergedRegions.add(regionKey);
                }
            }
        }

        // 创建单元格并应用样式
        for (Map.Entry<Integer, List<TemplateStyle>> entry : stylesByRow.entrySet()) {
            int rowIndex = entry.getKey();
            Row row = sheet.createRow(rowIndex);

            for (TemplateStyle style : entry.getValue()) {
                Cell cell = row.createCell(style.getColumnIndex());
                if (style.getCellValue() != null) {
                    cell.setCellValue(style.getCellValue());
                }
                applyCellStyle(workbook, cell, style);
            }
        }
    }

    /**
     * 生成简单表头（如果没有样式）
     */
    private void generateSimpleHeader(Workbook workbook, Sheet sheet,
                                      LedgerTemplate template, List<TemplateField> fields) {

        // 第1行：标题
        Row row1 = sheet.createRow(0);
        Cell titleCell = row1.createCell(0);
        titleCell.setCellValue(template.getUnitName() + "一体化服务台账信息");

        // 合并单元格
        if (fields.size() > 1) {
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, fields.size() - 1));
        }

        // 第4行：字段名
        Row row4 = sheet.createRow(3);
        for (int i = 0; i < fields.size(); i++) {
            Cell cell = row4.createCell(i);
            cell.setCellValue(fields.get(i).getFieldLabel());

            // 设置表头样式
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            // 设置边框
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            // 设置背景色
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            cell.setCellStyle(headerStyle);
        }
    }

    /**
     * 应用单元格样式
     */
    private void applyCellStyle(Workbook workbook, Cell cell, TemplateStyle style) {
        try {
            CellStyle cellStyle = workbook.createCellStyle();

            // 创建字体
            Font font = workbook.createFont();

            // 字体
            if (style.getFontName() != null) {
                font.setFontName(style.getFontName());
            }
            if (style.getFontBold() != null) {
                font.setBold(style.getFontBold());
            }
            if (style.getFontSize() != null) {
                font.setFontHeightInPoints(style.getFontSize().shortValue());
            }

            // 字体颜色
            if (style.getFontColor() != null) {
                try {
                    // 对于 XSSFWorkbook，可以使用 XSSFColor
                    if (workbook instanceof XSSFWorkbook) {
                        XSSFFont xssfFont = (XSSFFont) font;
                        byte[] rgb = hexToRgb(style.getFontColor());
                        xssfFont.setColor(new XSSFColor(rgb, null));
                    }
                } catch (Exception e) {
                    log.warn("字体颜色解析失败: {}", style.getFontColor());
                }
            }
            cellStyle.setFont(font);

            // 对齐方式
            if (style.getAlignment() != null) {
                switch (style.getAlignment()) {
                    case "CENTER":
                        cellStyle.setAlignment(HorizontalAlignment.CENTER);
                        break;
                    case "RIGHT":
                        cellStyle.setAlignment(HorizontalAlignment.RIGHT);
                        break;
                    default:
                        cellStyle.setAlignment(HorizontalAlignment.LEFT);
                }
            } else {
                cellStyle.setAlignment(HorizontalAlignment.LEFT);
            }

            // 垂直对齐
            if (style.getVerticalAlignment() != null) {
                switch (style.getVerticalAlignment()) {
                    case "TOP":
                        cellStyle.setVerticalAlignment(VerticalAlignment.TOP);
                        break;
                    case "BOTTOM":
                        cellStyle.setVerticalAlignment(VerticalAlignment.BOTTOM);
                        break;
                    default:
                        cellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
                }
            } else {
                cellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            }

            // 背景色
            if (style.getBackgroundColor() != null) {
                try {
                    // 对于 XSSFWorkbook，可以使用 XSSFColor
                    if (workbook instanceof XSSFWorkbook) {
                        XSSFWorkbook xssfWorkbook = (XSSFWorkbook) workbook;
                        byte[] rgb = hexToRgb(style.getBackgroundColor());
                        XSSFColor color = new XSSFColor(rgb, null);
                        ((XSSFCellStyle) cellStyle).setFillForegroundColor(color);
                        cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                    }
                } catch (Exception e) {
                    log.warn("背景颜色解析失败: {}", style.getBackgroundColor());
                }
            }

            // 边框
            if (style.getBorderTop() != null && style.getBorderTop()) {
                cellStyle.setBorderTop(BorderStyle.THIN);
            }
            if (style.getBorderBottom() != null && style.getBorderBottom()) {
                cellStyle.setBorderBottom(BorderStyle.THIN);
            }
            if (style.getBorderLeft() != null && style.getBorderLeft()) {
                cellStyle.setBorderLeft(BorderStyle.THIN);
            }
            if (style.getBorderRight() != null && style.getBorderRight()) {
                cellStyle.setBorderRight(BorderStyle.THIN);
            }

            // 单元格格式
            if (style.getCellFormat() != null) {
                cellStyle.setDataFormat(workbook.createDataFormat().getFormat(style.getCellFormat()));
            }

            cell.setCellStyle(cellStyle);
        } catch (Exception e) {
            log.error("应用单元格样式失败", e);
        }
    }

    /**
     * 清空数据行
     */
    private void clearDataRows(Sheet sheet, int startRow) {
        int lastRowNum = sheet.getLastRowNum();
        for (int i = startRow; i <= lastRowNum; i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                sheet.removeRow(row);
            }
        }
    }

    /**
     * 获取数据详情
     */
    private Map<Long, List<LedgerDataDetail>> getDataDetails(List<LedgerData> dataList) {
        if (dataList.isEmpty()) return new HashMap<>();

        List<Long> dataIds = dataList.stream()
                .map(LedgerData::getId)
                .collect(Collectors.toList());

        List<LedgerDataDetail> details = ledgerDataDetailRepository.findByDataIdIn(dataIds);

        // 按数据ID分组
        Map<Long, List<LedgerDataDetail>> detailsMap = new HashMap<>();
        for (LedgerDataDetail detail : details) {
            detailsMap.computeIfAbsent(detail.getDataId(), k -> new ArrayList<>())
                    .add(detail);
        }

        log.info("获取到 {} 条数据详情，分组后: {} 组", details.size(), detailsMap.size());
        return detailsMap;
    }

    /**
     * 调试数据详情
     */
    private void debugDataDetails(List<LedgerData> dataList, Map<Long, List<LedgerDataDetail>> detailsMap) {
        log.info("====== 数据详情调试信息 ======");
        for (LedgerData data : dataList) {
            List<LedgerDataDetail> details = detailsMap.get(data.getId());
            log.info("数据ID: {}, 明细数量: {}", data.getId(), details != null ? details.size() : 0);
            if (details != null) {
                for (LedgerDataDetail detail : details) {
                    log.debug("  - 字段: {}, 值: {}", detail.getFieldName(), detail.getFieldValue());
                }
            }
        }
        log.info("====== 调试信息结束 ======");
    }

    /**
     * 十六进制颜色转RGB
     */
    private byte[] hexToRgb(String hexColor) {
        if (hexColor == null || hexColor.trim().isEmpty()) {
            return new byte[] {0, 0, 0}; // 默认黑色
        }

        String color = hexColor.trim();
        if (color.startsWith("#")) {
            color = color.substring(1);
        }

        try {
            if (color.length() == 6) {
                return new byte[] {
                        (byte) Integer.parseInt(color.substring(0, 2), 16),
                        (byte) Integer.parseInt(color.substring(2, 4), 16),
                        (byte) Integer.parseInt(color.substring(4, 6), 16)
                };
            } else if (color.length() == 8) {
                // 包含Alpha通道，只取RGB
                return new byte[] {
                        (byte) Integer.parseInt(color.substring(2, 4), 16),
                        (byte) Integer.parseInt(color.substring(4, 6), 16),
                        (byte) Integer.parseInt(color.substring(6, 8), 16)
                };
            } else if (color.length() == 3) {
                // 简写格式如 #FFF
                return new byte[] {
                        (byte) Integer.parseInt(color.substring(0, 1) + color.substring(0, 1), 16),
                        (byte) Integer.parseInt(color.substring(1, 2) + color.substring(1, 2), 16),
                        (byte) Integer.parseInt(color.substring(2, 3) + color.substring(2, 3), 16)
                };
            }
        } catch (Exception e) {
            log.warn("颜色格式转换失败: {}", color);
        }

        return new byte[] {0, 0, 0}; // 黑色
    }

    /**
     * 导出单位所有数据
     */
    @Transactional(readOnly = true)
    public byte[] exportAllLedgerDataByUnit(String unitName) throws IOException {
        log.info("开始导出单位所有数据，单位: {}", unitName);

        // 1. 根据单位名称获取模板
        LedgerTemplate template = ledgerTemplateRepository.findByUnitNameAndDeletedFalse(unitName)
                .orElseThrow(() -> new RuntimeException("单位不存在或已被删除: " + unitName));

        // 2. 获取该单位的所有数据
        List<LedgerData> dataList = ledgerDataRepository.findByUnitNameAndDeletedFalse(unitName);

        if (dataList.isEmpty()) {
            log.warn("该单位没有台账数据: {}", unitName);
            // 创建一个空的Excel
            return createEmptyExcelForUnit(template, unitName);
        }

        // 3. 提取数据ID
        List<Long> dataIds = dataList.stream()
                .map(LedgerData::getId)
                .collect(Collectors.toList());

        log.info("开始导出单位 {} 的数据，共 {} 条", unitName, dataIds.size());

        // 4. 调用已有的导出方法
        return exportLedgerData(template.getId(), dataIds);
    }

    /**
     * 为单位创建空Excel（如果没有数据时）
     */
    private byte[] createEmptyExcelForUnit(LedgerTemplate template, String unitName) throws IOException {
        log.info("为没有数据的单位创建空Excel: {}", unitName);

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(unitName);

        // 创建简单表头
        Row titleRow = sheet.createRow(0);
        titleRow.createCell(0).setCellValue(unitName + "一体化服务台账信息");
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));

        Row headerRow = sheet.createRow(1);
        headerRow.createCell(0).setCellValue("项目名称");
        headerRow.createCell(1).setCellValue("物料组");
        headerRow.createCell(2).setCellValue("需求数量");
        headerRow.createCell(3).setCellValue("备注");

        // 添加提示行
        Row infoRow = sheet.createRow(2);
        infoRow.createCell(0).setCellValue("当前单位没有台账数据");

        // 自动调整列宽
        for (int i = 0; i < 4; i++) {
            sheet.autoSizeColumn(i);
        }

        // 写入字节数组
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            workbook.write(outputStream);
            workbook.close();
            return outputStream.toByteArray();
        }
    }

    /**
     * 根据模板字段创建组合字段名映射
     */
    private Map<String, String> createCombinedFieldMapping(List<TemplateField> fields) {
        Map<String, String> fieldMapping = new HashMap<>();

        for (TemplateField field : fields) {
            // 创建组合字段名：fieldName_excelColumn
            String combinedFieldName = field.getFieldName() + "_" + field.getExcelColumn();
            fieldMapping.put(combinedFieldName, field.getFieldName());
            log.debug("创建字段映射: {} -> {}", combinedFieldName, field.getFieldName());
        }

        log.info("创建了 {} 个字段映射", fieldMapping.size());
        return fieldMapping;
    }

    /**
     * 填充行数据 - 使用组合字段名匹配
     */
    private void fillRowDataWithCombinedMapping(Row row, Map<Integer, String> columnToFieldMap,
                                                Map<String, String> combinedFieldMapping,
                                                Map<String, String> detailMap) {

        for (Map.Entry<Integer, String> entry : columnToFieldMap.entrySet()) {
            int columnIndex = entry.getKey();
            String combinedFieldName = entry.getValue();

            // 查找原始字段名
            String originalFieldName = combinedFieldMapping.get(combinedFieldName);
            if (originalFieldName != null) {
                // 使用组合字段名查找数据
                String value = detailMap.get(combinedFieldName);
                if (value == null) {
                    // 如果找不到，尝试用原始字段名查找
                    value = detailMap.get(originalFieldName);
                }

                if (value != null && !value.trim().isEmpty()) {
                    Cell cell = row.createCell(columnIndex);
                    cell.setCellValue(value);
                }
            } else {
                log.warn("找不到字段映射: {}", combinedFieldName);
            }
        }
    }

    /**
     * 方式1：基于模板文件导出 - 修复字段名匹配问题
     */
    private Workbook exportWithTemplateFileCompletely(LedgerTemplate template,
                                                      List<TemplateField> fields,
                                                      List<LedgerData> dataList,
                                                      Map<Long, List<LedgerDataDetail>> detailsMap) throws IOException {

        log.info("使用模板文件导出，文件路径: {}", template.getTemplateFilePath());

        File templateFile = fileStorageService.getTemplateFile(template.getTemplateFilePath());
        if (templateFile == null || !templateFile.exists()) {
            throw new FileNotFoundException("模板文件不存在: " + template.getTemplateFilePath());
        }

        try (FileInputStream fis = new FileInputStream(templateFile)) {
            Workbook workbook = new XSSFWorkbook(fis);
            Sheet sheet = workbook.getSheetAt(0);

            // 获取表头行数
            int headerRowCount = template.getHeaderRowCount() != null ? template.getHeaderRowCount() : 4;
            int dataStartRow = template.getDataStartRow() != null ? template.getDataStartRow() - 1 : headerRowCount;

            log.info("数据起始行: {} (表头行数: {})", dataStartRow, headerRowCount);

            // 清空原有数据
            clearDataRows(sheet, dataStartRow);

            // 创建组合字段名映射
            Map<String, String> combinedFieldMapping = createCombinedFieldMapping(fields);

            // 创建列索引到组合字段名的映射
            Map<Integer, String> columnToFieldMap = new HashMap<>();
            for (TemplateField field : fields) {
                try {
                    int columnIndex = excelColumnToIndex(field.getExcelColumn());
                    String combinedFieldName = field.getFieldName() + "_" + field.getExcelColumn();
                    columnToFieldMap.put(columnIndex, combinedFieldName);
                    log.debug("列映射: 列{} -> {}", columnIndex, combinedFieldName);
                } catch (Exception e) {
                    log.warn("字段 {} 无法通过excel_column {} 映射: {}",
                            field.getFieldName(), field.getExcelColumn(), e.getMessage());
                }
            }

            log.info("创建列映射，共{}个字段映射到{}列", fields.size(), columnToFieldMap.size());

            // 填充数据
            int currentRowNum = dataStartRow;
            for (LedgerData data : dataList) {
                Row row = sheet.createRow(currentRowNum);

                // 获取该数据的详情
                List<LedgerDataDetail> details = detailsMap.get(data.getId());
                if (details != null) {
                    // 创建字段值映射（使用组合字段名）
                    Map<String, String> detailMap = new HashMap<>();
                    for (LedgerDataDetail detail : details) {
                        detailMap.put(detail.getFieldName(),
                                detail.getFieldValue() != null ? detail.getFieldValue() : "");
                    }

                    // 填充行数据
                    fillRowDataWithCombinedMapping(row, columnToFieldMap, combinedFieldMapping, detailMap);
                }

                currentRowNum++;
            }

            log.info("模板文件导出完成，共写入 {} 行数据", dataList.size());
            return workbook;
        }
    }
}