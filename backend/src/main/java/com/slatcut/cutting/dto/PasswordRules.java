package com.slatcut.cutting.dto;

import com.slatcut.cutting.config.ConflictException;
import java.nio.charset.StandardCharsets;

/**
 * Độ dài mật khẩu dùng chung cho mọi DTO có nhận mật khẩu.
 *
 * <p>Trần 72 không phải con số tùy ý: BCrypt chỉ băm 72 <b>byte</b> đầu và lặng lẽ bỏ phần còn lại.
 * Không chặn thì phần đuôi mà người dùng tưởng đang bảo vệ mình thật ra vô nghĩa.
 *
 * <p>Vì giới hạn tính theo byte chứ không theo ký tự, {@code @Size(max = 72)} một mình là chưa đủ:
 * tiếng Việt có dấu chiếm 2–3 byte mỗi chữ trong UTF-8, nên một cụm mật khẩu chỉ khoảng 30 ký tự đã
 * có thể vượt 72 byte mà vẫn lọt qua {@code @Size}. {@link #checkFitsBcrypt(String)} đo đúng số byte.
 */
public final class PasswordRules {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 72;

    /** Số byte UTF-8 tối đa BCrypt thật sự băm. */
    public static final int MAX_BYTES = 72;

    /** Ném {@link ConflictException} nếu mật khẩu dài quá phần BCrypt băm — thà báo còn hơn cắt lặng lẽ. */
    public static void checkFitsBcrypt(String rawPassword) {
        if (rawPassword != null && rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new ConflictException(
                    "Mật khẩu quá dài (tối đa " + MAX_BYTES + " byte; chữ tiếng Việt có dấu chiếm 2–3 byte mỗi chữ)");
        }
    }

    private PasswordRules() {}
}
