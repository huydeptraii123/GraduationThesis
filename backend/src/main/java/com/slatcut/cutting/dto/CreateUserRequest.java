package com.slatcut.cutting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateUserRequest {

    /** Khớp {@code app_user.username VARCHAR(50)}; cũng là chủ thể (sub) của JWT nên bất biến về sau. */
    @NotBlank
    @Size(max = 50)
    private String username;

    @NotBlank
    @Size(min = PasswordRules.MIN_LENGTH, max = PasswordRules.MAX_LENGTH)
    private String password;

    /** Mã vai trò (`role.code`) — đối chiếu với bảng `role` ở tầng service, không hard-code ở đây. */
    @NotBlank
    @Size(max = 20)
    private String roleCode;
}
