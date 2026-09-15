package com.slatcut.cutting.controller;

import com.slatcut.cutting.dto.SalesOrderImportResult;
import com.slatcut.cutting.service.SalesOrderImportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/sales-orders")
public class SalesOrderImportController {

    private final SalesOrderImportService service;

    public SalesOrderImportController(SalesOrderImportService service) {
        this.service = service;
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('PLANNER')")
    public SalesOrderImportResult importExcel(@RequestParam("file") MultipartFile file) {
        return service.importFromExcel(file);
    }
}
