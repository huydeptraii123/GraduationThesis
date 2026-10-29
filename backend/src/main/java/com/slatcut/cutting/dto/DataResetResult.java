package com.slatcut.cutting.dto;

/**
 * Số dòng đã xóa ở từng bảng trong một lần đặt lại dữ liệu vận hành — để giao diện báo lại đúng
 * những gì vừa mất, và để kiểm thử khẳng định được từng bảng.
 */
public record DataResetResult(
        int salesOrders,
        int customers,
        int inventoryBatches,
        int cuttingPlans,
        int cuttingPlanDetails,
        int cuttingPlanDetailItems,
        int shortageRecords,
        int stockSnapshots) {}
