package com.slatcut.cutting.controller;

import com.slatcut.cutting.dto.PageResponse;
import com.slatcut.cutting.dto.SalesOrderRequest;
import com.slatcut.cutting.dto.SalesOrderResponse;
import com.slatcut.cutting.service.SalesOrderService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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

@RestController
@RequestMapping("/api/v1/sales-orders")
public class SalesOrderController {

    private final SalesOrderService service;

    public SalesOrderController(SalesOrderService service) {
        this.service = service;
    }

    /**
     * Sắp xếp mặc định trùng với thứ tự ưu tiên cắt của nghiệp vụ (ngày giao, rồi ycsx, rồi bộ cửa)
     * nên người lập kế hoạch nhìn trang đầu là thấy ngay các đơn gấp nhất.
     */
    @GetMapping
    public PageResponse<SalesOrderResponse> getAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryTo,
            @PageableDefault(size = 20, sort = {"reqdDeliveryDate", "ycsx", "item"},
                    direction = Sort.Direction.ASC) Pageable pageable) {
        return service.getPage(keyword, customerId, deliveryFrom, deliveryTo, pageable);
    }

    @GetMapping("/{id}")
    public SalesOrderResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','PLANNER')")
    public ResponseEntity<SalesOrderResponse> create(@Valid @RequestBody SalesOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PLANNER')")
    public SalesOrderResponse update(@PathVariable Long id, @Valid @RequestBody SalesOrderRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PLANNER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
