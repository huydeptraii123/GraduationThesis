package com.slatcut.cutting.dto;

/**
 * Tổng hợp toàn bộ định mức BOM, không phụ thuộc trang đang xem — nguồn cho 3 ô thống kê ở đầu màn
 * hình định mức.
 */
public record BomSummaryResponse(long totalItems, long groupCount, long doorProductCount) {
}
