package com.slatcut.cutting.controller;

import com.slatcut.cutting.domain.CuttingPlan;
import com.slatcut.cutting.domain.CuttingPlanStatus;
import com.slatcut.cutting.dto.CuttingPlanApprovalPreviewResponse;
import com.slatcut.cutting.dto.CuttingPlanApproveRequest;
import com.slatcut.cutting.dto.CuttingPlanPreviewResponse;
import com.slatcut.cutting.dto.CuttingPlanResponse;
import com.slatcut.cutting.dto.CuttingPlanSummaryResponse;
import com.slatcut.cutting.dto.PageResponse;
import com.slatcut.cutting.mapper.CuttingPlanPreviewMapper;
import com.slatcut.cutting.service.CuttingPlanApprovalPreview;
import com.slatcut.cutting.service.CuttingPlanPreview;
import com.slatcut.cutting.service.CuttingPlanReportService;
import com.slatcut.cutting.service.CuttingPlanService;
import com.slatcut.cutting.service.ExcelExportService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cutting-plans")
public class CuttingPlanController {

    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final CuttingPlanService service;
    private final CuttingPlanReportService reportService;
    private final CuttingPlanPreviewMapper previewMapper;
    private final ExcelExportService excelExportService;

    public CuttingPlanController(
            CuttingPlanService service,
            CuttingPlanReportService reportService,
            CuttingPlanPreviewMapper previewMapper,
            ExcelExportService excelExportService) {
        this.service = service;
        this.reportService = reportService;
        this.previewMapper = previewMapper;
        this.excelExportService = excelExportService;
    }

    /**
     * Tính phương án cắt cho toàn bộ đơn chưa duyệt và tồn kho tại thời điểm bấm. KHÔNG ghi dữ
     * liệu — mở cho cả hai vai trò vì nó chỉ đọc và không chốt quyết định sản xuất nào
     * (docs/requirements-functional.md Nhóm 3).
     *
     * <p>Là POST dù không ghi gì: mỗi lần gọi chạy trọn thuật toán trên toàn bộ đơn tồn nên kết quả
     * không được phép nằm lại ở bộ nhớ đệm của trình duyệt hay proxy — người dùng bấm "tính lại"
     * chính là để lấy con số của trạng thái lúc này.
     */
    @PostMapping("/simulate")
    @PreAuthorize("hasAnyRole('ADMIN','PLANNER')")
    public CuttingPlanPreviewResponse simulate() {
        CuttingPlanPreview preview = service.simulate();
        return previewMapper.toResponse(preview, reportService.buildFromPreview(preview));
    }

    /** Phương án đề xuất cho một đợt duyệt, kèm dấu vân trạng thái để gửi lại khi duyệt. Không ghi gì. */
    @GetMapping("/approval-preview")
    @PreAuthorize("hasRole('PLANNER')")
    public CuttingPlanApprovalPreviewResponse approvalPreview() {
        CuttingPlanApprovalPreview preview = service.approvalPreview();
        return previewMapper.toApprovalResponse(preview, reportService.buildFromPreview(preview.plan()));
    }

    /**
     * Duyệt phương án cắt — thao tác vận hành duy nhất ghi dữ liệu ở nhóm chức năng này, nên chỉ
     * PLANNER. Dấu vân lệch thì trả 409 và không dòng nào được ghi.
     */
    @PostMapping("/approve")
    @PreAuthorize("hasRole('PLANNER')")
    public CuttingPlanResponse approve(@Valid @RequestBody CuttingPlanApproveRequest request) {
        CuttingPlan plan = service.approve(request.stateFingerprint());
        return service.getById(plan.getId());
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
