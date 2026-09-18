package com.slatcut.cutting.dto;

import java.time.LocalDateTime;

/**
 * Tài khoản nhìn từ phía API.
 *
 * <p>Cố ý KHÔNG có trường nào mang {@code passwordHash}: hash BCrypt tuy không đảo ngược được nhưng
 * vẫn là bí mật cho phép tấn công thử mật khẩu ngoại tuyến, và một khi đã lọt ra response thì mọi
 * màn hình, log hay bộ nhớ đệm của trình duyệt đều giữ lại bản sao.
 */
public record UserResponse(Long id, String username, String roleCode, boolean enabled, LocalDateTime createdAt) {
}
