package com.slatcut.cutting.controller;

import com.slatcut.cutting.domain.CuttingPlan;
import com.slatcut.cutting.domain.CuttingPlanStatus;
import com.slatcut.cutting.dto.CuttingPlanResponse;
import com.slatcut.cutting.dto.CuttingPlanScopePreviewResponse;
import com.slatcut.cutting.dto.CuttingPlanSummaryResponse;
import com.slatcut.cutting.dto.PageResponse;
import com.slatcut.cutting.service.CuttingPlanService;
import com.slatcut.cutting.service.ExcelExportService;
import java.time.LocalDate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cutting-plans")
public class CuttingPlanController {

    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final CuttingPlanService service;
    private final ExcelExportService excelExportService;

    public CuttingPlanController(CuttingPlanService service, ExcelExportService excelExportService) {
        this.service = service;
        this.excelExportService = excelExportService;
    }

    @PostMapping("/generate")
    @PreAuthorize("hasRole('PLANNER')")
    public CuttingPlanResponse generate() {
        CuttingPlan plan = service.generate();
        return service.getById(plan.getId());
    }

    @GetMapping("/scope-preview")
    @PreAuthorize("hasRole('PLANNER')")
    public CuttingPlanScopePreviewResponse getScopePreview() {
        return service.getScopePreview();
    }

    /** Lần chạy mới nhất lên đầu — đây là thứ người dùng mở màn hình này để tìm. */
    @GetMapping
    public PageResponse<CuttingPlanSummaryResponse> getAll(
            @RequestParam(required = false) Long planId,
            @RequestParam(required = false) CuttingPlanStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate runFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate runTo,
            @PageableDefault(size = 10, sort = "runAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.getSummaryPage(planId, status, runFrom, runTo, pageable);
    }

    @GetMapping("/{id}")
    public CuttingPlanResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id) {
        return excelResponse(excelExportService.exportCuttingPlan(id), "phuong-an-cat-" + id + ".xlsx");
    }

    @GetMapping("/{id}/shortage-report")
    public ResponseEntity<byte[]> shortageReport(@PathVariable Long id) {
        return excelResponse(excelExportService.exportShortageReport(id), "bao-cao-thieu-vat-tu-" + id + ".xlsx");
    }

    private ResponseEntity<byte[]> excelResponse(byte[] file, String filename) {
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(file);
    }
}
