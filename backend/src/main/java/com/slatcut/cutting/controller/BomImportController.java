package com.slatcut.cutting.controller;

import com.slatcut.cutting.dto.BomImportResult;
import com.slatcut.cutting.service.ImportTemplateService;
import com.slatcut.cutting.service.BomImportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/bom-items")
public class BomImportController {

    private final BomImportService service;
    private final ImportTemplateService templateService;

    public BomImportController(BomImportService service, ImportTemplateService templateService) {
        this.service = service;
        this.templateService = templateService;
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    public BomImportResult importExcel(@RequestParam("file") MultipartFile file) {
        return service.importFromExcel(file);
    }

    /** File Excel mẫu hợp lệ để điền dữ liệu định mức rồi nhập lên — cùng quyền với nút nhập. */
    @GetMapping("/import/template")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> importTemplate() {
        return ExcelResponses.attachment(templateService.bomTemplate(), ImportTemplateService.BOM_FILENAME);
    }
}
