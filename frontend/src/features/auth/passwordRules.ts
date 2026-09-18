import type { Rule } from 'antd/es/form'

/**
 * Khớp `PasswordRules` phía backend. Trần 72 là giới hạn thật của BCrypt (thuật toán chỉ băm 72 byte
 * đầu), không phải con số tùy chọn — chặn ngay tại form để người dùng không đặt một mật khẩu dài mà
 * phần đuôi chẳng bảo vệ gì.
 */
export const PASSWORD_MIN_LENGTH = 8
export const PASSWORD_MAX_LENGTH = 72

export const PASSWORD_RULES: Rule[] = [
  { required: true, message: 'Nhập mật khẩu' },
  { min: PASSWORD_MIN_LENGTH, message: `Mật khẩu cần ít nhất ${PASSWORD_MIN_LENGTH} ký tự` },
]
