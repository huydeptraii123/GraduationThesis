package com.slatcut.cutting.controller;

import com.slatcut.cutting.domain.CuttingPlan;
import com.slatcut.cutting.dto.CuttingPlanResponse;
import com.slatcut.cutting.dto.CuttingPlanScopePreviewResponse;
import com.slatcut.cutting.dto.CuttingPlanSummaryResponse;
import com.slatcut.cutting.service.CuttingPlanService;
import com.slatcut.cutting.service.ExcelExportService;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @GetMapping
    public List<CuttingPlanSummaryResponse> getAll() {
        return service.getSummaries();
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
