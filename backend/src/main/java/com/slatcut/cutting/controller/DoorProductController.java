package com.slatcut.cutting.controller;

import com.slatcut.cutting.dto.DoorProductRequest;
import com.slatcut.cutting.dto.DoorProductResponse;
import com.slatcut.cutting.service.DoorProductService;
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
@RequestMapping("/api/v1/door-products")
public class DoorProductController {

    private final DoorProductService service;

    public DoorProductController(DoorProductService service) {
        this.service = service;
    }

    @GetMapping
    public List<DoorProductResponse> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public DoorProductResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DoorProductResponse> create(@Valid @RequestBody DoorProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public DoorProductResponse update(@PathVariable Long id, @Valid @RequestBody DoorProductRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
