package com.slatcut.cutting.controller;

import com.slatcut.cutting.domain.SlatGroup;
import com.slatcut.cutting.dto.InventoryBatchRequest;
import com.slatcut.cutting.dto.InventoryBatchResponse;
import com.slatcut.cutting.dto.InventorySummaryResponse;
import com.slatcut.cutting.dto.PageResponse;
import com.slatcut.cutting.service.InventoryBatchService;
import jakarta.validation.Valid;
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
 * Ghi tồn kho là tác vụ vận hành hằng ngày của PLANNER (docs/requirements-functional.md Nhóm 1);
 * ADMIN chỉ có thêm quyền trên định mức BOM, tài khoản người dùng và thao tác thủ công trên đơn hàng.
 */
@RestController
@RequestMapping("/api/v1/inventory-batches")
public class InventoryBatchController {

    private final InventoryBatchService service;

    public InventoryBatchController(InventoryBatchService service) {
        this.service = service;
    }

    /**
     * Sắp xếp mặc định theo tên vật tư rồi độ dài để trang nào cũng có thứ tự ổn định — thiếu sắp
     * xếp thì MySQL không đảm bảo thứ tự giữa các lần truy vấn, và cùng một dòng có thể xuất hiện ở
     * hai trang khác nhau.
     */
    @GetMapping
    public PageResponse<InventoryBatchResponse> getAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) SlatGroup slatGroup,
            @PageableDefault(size = 20, sort = {"slatMaterial.slatMaterialName", "doDaiThanhMm"},
                    direction = Sort.Direction.ASC) Pageable pageable) {
        return service.getPage(keyword, slatGroup, pageable);
    }

    /**
     * Đặt TRƯỚC {@code /{id}} — Spring khớp đường dẫn cụ thể trước biến đường dẫn nên thứ tự khai
     * báo không quyết định, nhưng để cạnh nhau cho người đọc thấy ngay hai lối vào khác nhau.
     */
    @GetMapping("/summary")
    public InventorySummaryResponse getSummary() {
        return service.getSummary();
    }

    @GetMapping("/{id}")
    public InventoryBatchResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('PLANNER')")
    public ResponseEntity<InventoryBatchResponse> create(@Valid @RequestBody InventoryBatchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PLANNER')")
    public InventoryBatchResponse update(@PathVariable Long id, @Valid @RequestBody InventoryBatchRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PLANNER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
