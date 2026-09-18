import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../features/auth/AuthContext'

/**
 * Chặn route khi chưa đăng nhập. KHÔNG phân quyền theo vai trò: mọi màn hình nghiệp vụ đều cho cả
 * hai vai trò xem (GET của mọi module đều mở), khác biệt chỉ nằm ở các thao tác ghi — và việc đó
 * do `features/auth/permissions.ts` cùng `RoleRestrictionNotice` xử lý ngay trong trang.
 *
 * Đây chỉ là trải nghiệm người dùng, không phải bảo mật: chặn thật nằm ở `@PreAuthorize` của
 * backend, được khoá lại bằng `RolePermissionMatrixTest`.
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { user } = useAuth()

  if (!user) {
    return <Navigate to="/login" replace />
  }
  return <>{children}</>
}
