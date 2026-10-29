package com.slatcut.cutting.controller;

import com.slatcut.cutting.dto.DataResetRequest;
import com.slatcut.cutting.dto.DataResetResult;
import com.slatcut.cutting.service.DataResetService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Đặt lại dữ liệu vận hành (đơn hàng, khách hàng, tồn kho, lịch sử duyệt) — xem
 * {@link DataResetService}.
 *
 * <p>Chỉ ADMIN, dù nhập đơn hàng và tồn kho hằng ngày là việc của PLANNER: thao tác này xóa luôn
 * toàn bộ lịch sử duyệt và không hoàn tác được, nặng hơn hẳn mọi việc vận hành thường ngày, nên đứng
 * cùng nhóm với quản lý tài khoản.
 */
@RestController
@RequestMapping("/api/v1/data-reset")
public class DataResetController {

    private final DataResetService service;

    public DataResetController(DataResetService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public DataResetResult reset(@Valid @RequestBody DataResetRequest request, Authentication authentication) {
        return service.resetInventoryAndOrders(request.confirmation(), authentication.getName());
    }
}
