package com.slatcut.cutting.controller;

import com.slatcut.cutting.dto.InventoryImportResult;
import com.slatcut.cutting.service.InventoryImportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryImportController {

    private final InventoryImportService service;

    public InventoryImportController(InventoryImportService service) {
        this.service = service;
    }

    @PostMapping("/import")
    @PreAuthorize("hasRole('PLANNER')")
    public InventoryImportResult importExcel(@RequestParam("file") MultipartFile file) {
        return service.importFromExcel(file);
    }
}
