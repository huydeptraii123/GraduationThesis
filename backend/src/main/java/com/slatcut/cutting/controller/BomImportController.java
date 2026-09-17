package com.slatcut.cutting.controller;

import com.slatcut.cutting.dto.BomImportResult;
import com.slatcut.cutting.service.BomImportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/bom-items")
public class BomImportController {

    private final BomImportService service;

    public BomImportController(BomImportService service) {
        this.service = service;
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    public BomImportResult importExcel(@RequestParam("file") MultipartFile file) {
        return service.importFromExcel(file);
    }
}
