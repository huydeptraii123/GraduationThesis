package com.slatcut.cutting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Toàn bộ số liệu trang chủ trong 1 lần gọi. {@code latestPlan} là null khi chưa có lần chạy nào;
 * {@code wasteTrend} sắp xếp CŨ → MỚI để vẽ biểu đồ đường theo thời gian.
 */
public record DashboardResponse(
        long pendingOrderCount,
        /** Đơn trong hạn giao bị bỏ qua vì mẫu cửa chưa có định mức — cần ADMIN cấu hình, không tự hết. */
        long ordersMissingBomCount,
        LocalDate scopeCutoffDate,
        long readyBatchCount,
        long readyStickCount,
        WasteTrendPointResponse latestPlan,
        BigDecimal cumulativeWasteM,
        BigDecimal cumulativeStockUsedM,
        BigDecimal cumulativeWasteRatioPercent,
        List<WasteTrendPointResponse> wasteTrend,
        /** Phế quy về từng bộ cửa, sắp theo ngày giao — trục thời gian của biểu đồ xu hướng. */
        List<OrderWastePointResponse> orderWasteTrend,
        List<RemainderBreakdownResponse> remainderBreakdown,
        List<SlatGroupWasteResponse> wasteByGroup) {
}
