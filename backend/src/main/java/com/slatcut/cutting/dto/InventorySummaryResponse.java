package com.slatcut.cutting.dto;

import java.math.BigDecimal;

/**
 * Tổng hợp toàn bộ tồn kho, không phụ thuộc trang đang xem — nguồn cho 2 ô thống kê ở đầu màn hình
 * tồn kho.
 */
public record InventorySummaryResponse(long batchCount, long totalSticks, BigDecimal totalLengthM) {
}
