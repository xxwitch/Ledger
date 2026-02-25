package com.example.ledger.controller;

/**
 * @author 霜月
 * @create 2025/12/20 21:01
 */
import com.example.ledger.dto.request.ExportLedgerRequest;
import com.example.ledger.repository.LedgerTemplateRepository;
import com.example.ledger.repository.TemplateFieldRepository;
import com.example.ledger.service.ExcelExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/ledger/export")
@RequiredArgsConstructor
@Slf4j
public class LedgerExportController {

    private final ExcelExportService excelExportService;
    private final TemplateFieldRepository templateFieldRepository;
    private final LedgerTemplateRepository ledgerTemplateRepository;

    /**
     * 1. 导出台账数据（选中的数据） - 新的使用 ExportLedgerRequest
     */
    @PostMapping("/selected")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRODUCTION')")
    public ResponseEntity<byte[]> exportSelectedLedgerData(@RequestBody ExportLedgerRequest request) {
        try {
            log.info("接收到导出请求，模板ID: {}, 数据量: {}",
                    request.getTemplateId(),
                    request.getDataIds() != null ? request.getDataIds().size() : 0);

            if (request.getTemplateId() == null) {
                throw new IllegalArgumentException("模板ID不能为空");
            }

            if (request.getDataIds() == null || request.getDataIds().isEmpty()) {
                log.warn("导出数据ID列表为空");
                return ResponseEntity.badRequest()
                        .body(("导出失败: 请选择要导出的数据").getBytes(StandardCharsets.UTF_8));
            }

            byte[] excelBytes = excelExportService.exportLedgerData(request.getTemplateId(), request.getDataIds());

            // 生成文件名
            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "台账导出_" + timestamp + ".xlsx";
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            // 设置响应头
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodedFileName)
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(excelBytes.length))
                    .body(excelBytes);

        } catch (IllegalArgumentException e) {
            log.error("导出参数错误", e);
            return ResponseEntity.badRequest()
                    .body(("导出失败: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.error("导出IO异常", e);
            return ResponseEntity.internalServerError()
                    .body(("导出失败: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("导出异常", e);
            return ResponseEntity.internalServerError()
                    .body(("导出失败: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * 2. 导出指定单位的所有台账数据
     */
    @GetMapping("/unit/{unitName}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRODUCTION')")
    public ResponseEntity<byte[]> exportUnitLedgerData(@PathVariable String unitName) {
        try {
            log.info("导出单位台账请求，单位名称: {}", unitName);

            if (unitName == null || unitName.trim().isEmpty()) {
                throw new IllegalArgumentException("单位名称不能为空");
            }

            byte[] excelBytes = excelExportService.exportAllLedgerDataByUnit(unitName);

            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = unitName + "_台账导出_" + timestamp + ".xlsx";
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodedFileName)
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(excelBytes.length))
                    .body(excelBytes);

        } catch (IllegalArgumentException e) {
            log.error("导出单位台账参数错误", e);
            return ResponseEntity.badRequest()
                    .body(("导出失败: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.error("导出单位台账IO异常", e);
            return ResponseEntity.internalServerError()
                    .body(("导出失败: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("导出单位台账异常", e);
            return ResponseEntity.internalServerError()
                    .body(("导出失败: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * 3. 测试导出功能
     */
    @GetMapping("/test")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRODUCTION')")
    public ResponseEntity<String> testExport() {
        try {
            log.info("导出测试接口被调用");
            return ResponseEntity.ok("导出服务运行正常");
        } catch (Exception e) {
            log.error("导出测试异常", e);
            return ResponseEntity.internalServerError()
                    .body("导出测试失败: " + e.getMessage());
        }
    }

    /**
     * 4. 按模板ID导出所有数据（新增）
     */
    @GetMapping("/template/{templateId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRODUCTION')")
    public ResponseEntity<byte[]> exportByTemplate(@PathVariable Long templateId) {
        try {
            log.info("按模板导出请求，模板ID: {}", templateId);

            if (templateId == null) {
                throw new IllegalArgumentException("模板ID不能为空");
            }

            // 获取模板的所有数据ID
            List<Long> allDataIds = excelExportService.getAllDataIdsByTemplate(templateId);

            if (allDataIds.isEmpty()) {
                log.info("模板 {} 没有数据，导出空表格", templateId);
                // 创建空表格
                byte[] excelBytes = createEmptyTemplateExport(templateId);

                String timestamp = LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String fileName = "模板" + templateId + "_导出_" + timestamp + ".xlsx";
                String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                        .replaceAll("\\+", "%20");

                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename*=UTF-8''" + encodedFileName)
                        .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(excelBytes.length))
                        .body(excelBytes);
            }

            // 调用导出方法
            byte[] excelBytes = excelExportService.exportLedgerData(templateId, allDataIds);

            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "模板" + templateId + "_导出_" + timestamp + ".xlsx";
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodedFileName)
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(excelBytes.length))
                    .body(excelBytes);

        } catch (IllegalArgumentException e) {
            log.error("按模板导出参数错误", e);
            return ResponseEntity.badRequest()
                    .body(("导出失败: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("按模板导出异常", e);
            return ResponseEntity.internalServerError()
                    .body(("导出失败: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * 创建空模板导出文件
     */
    private byte[] createEmptyTemplateExport(Long templateId) throws IOException {
        // 简单的空表格实现
        org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        org.apache.poi.xssf.usermodel.XSSFSheet sheet = workbook.createSheet("数据");

        org.apache.poi.xssf.usermodel.XSSFRow titleRow = sheet.createRow(0);
        titleRow.createCell(0).setCellValue("模板ID: " + templateId);
        titleRow.createCell(1).setCellValue("导出时间: " + LocalDateTime.now());

        org.apache.poi.xssf.usermodel.XSSFRow infoRow = sheet.createRow(1);
        infoRow.createCell(0).setCellValue("该模板没有数据");

        try (java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream()) {
            workbook.write(outputStream);
            workbook.close();
            return outputStream.toByteArray();
        }
    }

    /**
     * 5. 兼容旧的导出接口（保持向后兼容）
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRODUCTION')")
    public ResponseEntity<byte[]> exportLedgerDataLegacy(@RequestParam Long templateId,
                                                         @RequestBody List<Long> dataIds) {
        try {
            log.info("接收到旧版导出请求，模板ID: {}, 数据量: {}", templateId, dataIds.size());

            ExportLedgerRequest request = new ExportLedgerRequest();
            request.setTemplateId(templateId);
            request.setDataIds(dataIds);

            return exportSelectedLedgerData(request);

        } catch (Exception e) {
            log.error("旧版导出异常", e);
            return ResponseEntity.internalServerError()
                    .body(("导出失败: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Excel列字母转索引（用于调试接口）
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
}