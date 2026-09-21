package com.slatcut.cutting.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Thân request của thao tác duyệt phương án cắt.
 *
 * <p>Chỉ mang đúng dấu vân trạng thái, không mang phương án: hệ thống tính lại bên trong giao dịch
 * duyệt thay vì tin vào kết quả do phía client gửi lên (xem
 * {@code CuttingPlanService.approve}).
 */
public record CuttingPlanApproveRequest(@NotBlank String stateFingerprint) {
}
