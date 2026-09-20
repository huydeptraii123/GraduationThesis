package com.slatcut.cutting.controller;

import com.slatcut.cutting.domain.SlatGroup;
import com.slatcut.cutting.dto.PageResponse;
import com.slatcut.cutting.dto.SlatMaterialRequest;
import com.slatcut.cutting.dto.SlatMaterialResponse;
import com.slatcut.cutting.service.SlatMaterialService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Danh mục loại thanh nan là dữ liệu nền tảng dùng chung, nên ghi mở cho cả ADMIN lẫn PLANNER —
 * khác lô tồn kho (chỉ PLANNER). Lý do: nhóm vật tư (slatGroup) trên bảng này điều khiển trực tiếp
 * việc sinh nhu cầu cắt, và khi một mã bị tạo nhầm với nhóm OTHER lúc nhập tồn kho, ADMIN — người
 * sở hữu định mức BOM — phải tự sửa lại được mà không phải nhờ PLANNER.
 */
@RestController
@RequestMapping("/api/v1/slat-materials")
public class SlatMaterialController {

    private final SlatMaterialService service;

    public SlatMaterialController(SlatMaterialService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<SlatMaterialResponse> getAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) SlatGroup slatGroup,
            @PageableDefault(size = 20, sort = "slatMaterial", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.getPage(keyword, slatGroup, pageable);
    }

    /**
     * Danh mục đầy đủ cho dropdown và tra cứu mã/nhóm vật tư ở màn hình khác. Tách khỏi
     * {@link #getAll} vì danh sách phân trang không dùng làm nguồn lựa chọn được.
     */
    @GetMapping("/options")
    public List<SlatMaterialResponse> getOptions() {
        return service.getAllOptions();
    }

    @GetMapping("/{id}")
    public SlatMaterialResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','PLANNER')")
    public ResponseEntity<SlatMaterialResponse> create(@Valid @RequestBody SlatMaterialRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PLANNER')")
    public SlatMaterialResponse update(@PathVariable Long id, @Valid @RequestBody SlatMaterialRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PLANNER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
