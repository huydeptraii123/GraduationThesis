package com.slatcut.cutting.controller;

import com.slatcut.cutting.dto.DashboardResponse;
import com.slatcut.cutting.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Số liệu trang chủ. Không giới hạn role (cùng tiền lệ với {@code GET /api/v1/cutting-plans}): cả
 * ADMIN lẫn PLANNER đều vào trang chủ, và endpoint chỉ đọc số liệu tổng hợp, không lộ chi tiết đơn
 * hàng nào.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    public DashboardResponse getDashboard() {
        return service.getDashboard();
    }
}
