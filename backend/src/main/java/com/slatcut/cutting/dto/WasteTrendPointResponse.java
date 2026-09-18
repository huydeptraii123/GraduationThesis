package com.slatcut.cutting.dto;

import com.slatcut.cutting.domain.CuttingPlanStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 1 điểm trên biểu đồ xu hướng tỷ lệ phế của dashboard — tương ứng đúng 1 lần chạy thuật toán. */
public record WasteTrendPointResponse(
        Long planId,
        LocalDateTime runAt,
        CuttingPlanStatus status,
        Integer scopeOrderCount,
        BigDecimal totalWasteM,
        BigDecimal totalStockUsedM,
        BigDecimal wasteRatioPercent) {
}
