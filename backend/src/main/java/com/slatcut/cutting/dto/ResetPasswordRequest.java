package com.slatcut.cutting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** ADMIN đặt lại mật khẩu cho một tài khoản khác — không cần biết mật khẩu cũ. */
@Getter
@Setter
public class ResetPasswordRequest {

    @NotBlank
    @Size(min = PasswordRules.MIN_LENGTH, max = PasswordRules.MAX_LENGTH)
    private String newPassword;
}
