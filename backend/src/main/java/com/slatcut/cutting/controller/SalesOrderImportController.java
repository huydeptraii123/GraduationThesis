package com.slatcut.cutting.controller;

import com.slatcut.cutting.dto.SalesOrderImportResult;
import com.slatcut.cutting.service.ImportTemplateService;
import com.slatcut.cutting.service.SalesOrderImportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/sales-orders")
public class SalesOrderImportController {

    private final SalesOrderImportService service;
    private final ImportTemplateService templateService;

    public SalesOrderImportController(SalesOrderImportService service, ImportTemplateService templateService) {
        this.service = service;
        this.templateService = templateService;
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('PLANNER')")
    public SalesOrderImportResult importExcel(@RequestParam("file") MultipartFile file) {
        return service.importFromExcel(file);
    }

    /** File Excel mẫu hợp lệ để điền dữ liệu đơn hàng rồi nhập lên — cùng quyền với nút nhập. */
    @GetMapping("/import/template")
    @PreAuthorize("hasRole('PLANNER')")
    public ResponseEntity<byte[]> importTemplate() {
        return ExcelResponses.attachment(templateService.salesOrderTemplate(), ImportTemplateService.SALES_ORDER_FILENAME);
    }
}
