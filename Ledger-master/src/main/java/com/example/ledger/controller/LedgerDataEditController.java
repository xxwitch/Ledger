package com.example.ledger.controller;

/**
 * @author 霜月
 * @create 2025/12/21 16:40
 */

import com.example.ledger.dto.request.LedgerDataEditRequest;
import com.example.ledger.dto.request.LedgerDataBatchEditRequest;
import com.example.ledger.dto.request.LedgerDataDeleteRequest;
import com.example.ledger.dto.response.ApiResponse;
import com.example.ledger.service.LedgerDataEditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ledger/edit")
@RequiredArgsConstructor
@Slf4j
public class LedgerDataEditController {

    private final LedgerDataEditService ledgerDataEditService;

    /**
     * 编辑单个台账数据
     */
    @PutMapping("/single")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRODUCTION')")
    public ApiResponse<Map<String, Object>> editLedgerData(
            @RequestBody LedgerDataEditRequest request,
            HttpServletRequest httpRequest) {

        try {
            log.info("接收到台账数据编辑请求，数据ID: {}", request.getDataId());

            String ipAddress = getClientIp(httpRequest);
            Map<String, Object> result = ledgerDataEditService.editLedgerData(request, ipAddress);

            return ApiResponse.success("编辑完成", result);

        } catch (RuntimeException e) {
            log.error("台账数据编辑业务异常", e);
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("台账数据编辑未知异常", e);
            return ApiResponse.error("编辑失败: " + e.getMessage());
        }
    }

    /**
     * 批量编辑台账数据
     */
    @PutMapping("/batch")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRODUCTION')")
    public ApiResponse<Map<String, Object>> batchEditLedgerData(
            @RequestBody LedgerDataBatchEditRequest request,
            HttpServletRequest httpRequest) {

        try {
            log.info("接收到台账数据批量编辑请求，数据量: {}", request.getDataIds().size());

            String ipAddress = getClientIp(httpRequest);
            Map<String, Object> result = ledgerDataEditService.batchEditLedgerData(request, ipAddress);

            return ApiResponse.success("批量编辑完成", result);

        } catch (RuntimeException e) {
            log.error("台账数据批量编辑业务异常", e);
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("台账数据批量编辑未知异常", e);
            return ApiResponse.error("批量编辑失败: " + e.getMessage());
        }
    }

    /**
     * 删除台账数据
     */
    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRODUCTION')")
    public ApiResponse<Map<String, Object>> deleteLedgerData(
            @RequestBody LedgerDataDeleteRequest request,
            HttpServletRequest httpRequest) {

        try {
            log.info("接收到台账数据删除请求，数据量: {}，永久删除: {}",
                    request.getDataIds().size(), request.getPermanentDelete());

            String ipAddress = getClientIp(httpRequest);
            Map<String, Object> result = ledgerDataEditService.deleteLedgerData(request, ipAddress);

            return ApiResponse.success("删除完成", result);

        } catch (RuntimeException e) {
            log.error("台账数据删除业务异常", e);
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("台账数据删除未知异常", e);
            return ApiResponse.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 恢复已删除的台账数据
     */
    @PutMapping("/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> restoreLedgerData(
            @RequestParam List<Long> dataIds,
            @RequestParam(required = false) String restoreReason,
            HttpServletRequest httpRequest) {

        try {
            log.info("接收到台账数据恢复请求，数据量: {}", dataIds.size());

            String ipAddress = getClientIp(httpRequest);
            Map<String, Object> result = ledgerDataEditService.restoreLedgerData(
                    dataIds, restoreReason, ipAddress);

            return ApiResponse.success("恢复完成", result);

        } catch (RuntimeException e) {
            log.error("台账数据恢复业务异常", e);
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("台账数据恢复未知异常", e);
            return ApiResponse.error("恢复失败: " + e.getMessage());
        }
    }

    /**
     * 获取编辑历史
     */
    @GetMapping("/history/{dataId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PRODUCTION')")
    public ApiResponse<Map<String, Object>> getEditHistory(
            @PathVariable Long dataId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {

        try {
            log.info("获取台账数据编辑历史，数据ID: {}", dataId);

            Map<String, Object> history = ledgerDataEditService.getEditHistory(dataId, page, size);

            return ApiResponse.success("获取成功", history);

        } catch (RuntimeException e) {
            log.error("获取编辑历史业务异常", e);
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            log.error("获取编辑历史未知异常", e);
            return ApiResponse.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}