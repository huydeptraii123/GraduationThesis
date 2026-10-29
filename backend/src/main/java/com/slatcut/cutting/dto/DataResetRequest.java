package com.slatcut.cutting.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Yêu cầu đặt lại dữ liệu vận hành. Cụm xác nhận được máy chủ kiểm lại chứ không chỉ giao diện: một
 * lời gọi API lạc (script, công cụ thử API, bấm nhầm trên một bản giao diện cũ) không thể xóa sạch
 * dữ liệu nếu không gõ đúng cụm này.
 */
public record DataResetRequest(@NotBlank String confirmation) {}
