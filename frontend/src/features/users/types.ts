/** Phản chiếu các DTO tương ứng ở backend (package com.slatcut.cutting.dto). */

import type { Role } from '../auth/permissions'

export interface UserResponse {
  id: number
  username: string
  roleCode: Role
  enabled: boolean
  /** ISO-8601 do Jackson sinh từ LocalDateTime, không có múi giờ. */
  createdAt: string
}

export interface CreateUserRequest {
  username: string
  password: string
  roleCode: Role
}

/**
 * Không có `username`: tên đăng nhập là chủ thể của JWT nên bất biến sau khi tạo. Không có mật khẩu:
 * việc đó đi qua endpoint đặt lại riêng, để một lần bấm "Sửa" không vô tình ghi đè mật khẩu.
 */
export interface UpdateUserRequest {
  roleCode: Role
  enabled: boolean
}

export interface ResetPasswordRequest {
  newPassword: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}
