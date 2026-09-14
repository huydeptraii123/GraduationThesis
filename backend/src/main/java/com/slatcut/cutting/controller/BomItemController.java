package com.slatcut.cutting.controller;

import com.slatcut.cutting.dto.BomItemRequest;
import com.slatcut.cutting.dto.BomItemResponse;
import com.slatcut.cutting.service.BomItemService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bom-items")
public class BomItemController {

    private final BomItemService service;

    public BomItemController(BomItemService service) {
        this.service = service;
    }

    @GetMapping
    public List<BomItemResponse> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public BomItemResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BomItemResponse> create(@Valid @RequestBody BomItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public BomItemResponse update(@PathVariable Long id, @Valid @RequestBody BomItemRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
