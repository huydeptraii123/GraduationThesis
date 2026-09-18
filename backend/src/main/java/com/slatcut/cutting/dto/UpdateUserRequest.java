package com.slatcut.cutting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Sửa tài khoản: đổi vai trò và/hoặc khóa–mở khóa.
 *
 * <p>Không có {@code username}: đổi tên đăng nhập sẽ làm mọi JWT đang sống của tài khoản đó trỏ tới
 * một chủ thể không còn tồn tại. Cũng không có mật khẩu — việc đó đi qua endpoint đặt lại riêng để
 * một lần bấm "Sửa" không vô tình ghi đè mật khẩu.
 */
@Getter
@Setter
public class UpdateUserRequest {

    @NotBlank
    @Size(max = 20)
    private String roleCode;

    @NotNull
    private Boolean enabled;
}
