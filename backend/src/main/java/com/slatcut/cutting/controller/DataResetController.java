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
 * <p>Mở cho cả ADMIN lẫn PLANNER theo yêu cầu của doanh nghiệp: PLANNER là người nhập lại tồn kho và
 * đơn hàng sau khi đặt lại, nên không phải chờ ADMIN xóa giúp. Thao tác vẫn xóa luôn toàn bộ lịch sử
 * duyệt và không hoàn tác được — phần bảo vệ nằm ở cụm xác nhận mà máy chủ kiểm lại, không ở vai trò.
 */
@RestController
@RequestMapping("/api/v1/data-reset")
public class DataResetController {

    private final DataResetService service;

    public DataResetController(DataResetService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PLANNER')")
    public DataResetResult reset(@Valid @RequestBody DataResetRequest request, Authentication authentication) {
        return service.resetInventoryAndOrders(request.confirmation(), authentication.getName());
    }
}
