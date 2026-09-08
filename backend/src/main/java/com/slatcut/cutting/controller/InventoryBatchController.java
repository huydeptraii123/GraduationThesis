package com.slatcut.cutting.controller;

import com.slatcut.cutting.dto.InventoryBatchRequest;
import com.slatcut.cutting.dto.InventoryBatchResponse;
import com.slatcut.cutting.service.InventoryBatchService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory-batches")
public class InventoryBatchController {

    private final InventoryBatchService service;

    public InventoryBatchController(InventoryBatchService service) {
        this.service = service;
    }

    @GetMapping
    public List<InventoryBatchResponse> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public InventoryBatchResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    public ResponseEntity<InventoryBatchResponse> create(@Valid @RequestBody InventoryBatchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public InventoryBatchResponse update(@PathVariable Long id, @Valid @RequestBody InventoryBatchRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
