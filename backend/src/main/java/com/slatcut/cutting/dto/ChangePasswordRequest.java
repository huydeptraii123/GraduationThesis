package com.slatcut.cutting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Người dùng tự đổi mật khẩu của chính mình.
 *
 * <p>Khác với ADMIN đặt lại mật khẩu, luồng này bắt buộc nhập lại mật khẩu hiện tại: token có thể bị
 * lấy trộm (máy bỏ quên, tab chưa đăng xuất), và nếu chỉ cần token là đổi được mật khẩu thì kẻ lấy
 * được token sẽ khóa vĩnh viễn chủ tài khoản ra ngoài.
 *
 * <p><b>Giới hạn đã biết:</b> đổi mật khẩu KHÔNG thu hồi các token đã phát trước đó. Token là JWT tự
 * chứa, hệ thống không giữ danh sách phiên đang sống, nên một token bị lấy trộm vẫn dùng được tới
 * khi hết hạn (mặc định 8 giờ) dù mật khẩu đã đổi. Khóa tài khoản thì có hiệu lực ngay, vì
 * {@code JwtAuthenticationFilter} đọc lại cờ {@code enabled} từ CSDL ở mỗi request — nên khi nghi
 * token bị lộ, cách chặn thật sự hiện nay là nhờ ADMIN khóa tài khoản chứ không phải đổi mật khẩu.
 * Muốn đổi mật khẩu tự thu hồi token thì cần lưu mốc thời gian đổi mật khẩu và so với thời điểm
 * phát token.
 */
@Getter
@Setter
public class ChangePasswordRequest {

    @NotBlank
    private String currentPassword;

    @NotBlank
    @Size(min = PasswordRules.MIN_LENGTH, max = PasswordRules.MAX_LENGTH)
    private String newPassword;
}
