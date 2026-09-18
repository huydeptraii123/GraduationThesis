import { Alert } from 'antd'
import { ROLE_LABEL, type Role } from '../features/auth/permissions'

interface Props {
  /** Vai trò duy nhất thực hiện được thao tác này (khớp `@PreAuthorize` của endpoint tương ứng). */
  requiredRole: Role
  /** Cụm động từ mô tả đúng thao tác bị chặn, ví dụ "nhập đơn hàng hàng loạt từ Excel". */
  action: string
}

/**
 * Lời giải thích cho thao tác mà vai trò hiện tại không thực hiện được.
 *
 * Trước task 10.4 các nút thêm/sửa/xóa/nhập chỉ đơn giản biến mất, không một lời giải thích —
 * người dùng không biết là mình thiếu quyền hay hệ thống hỏng.
 *
 * Nhận cụm động từ thay vì tự ghép câu "chỉ xem": có màn chặn trọn quyền ghi (định mức BOM với
 * PLANNER), có màn chỉ chặn đúng một thao tác (ADMIN vẫn sửa được đơn hàng, chỉ không nhập Excel
 * hàng loạt) — gọi cả hai là "chế độ chỉ xem" thì sai với trường hợp thứ hai.
 */
export function RoleRestrictionNotice({ requiredRole, action }: Props) {
  return (
    <Alert
      type="info"
      showIcon
      style={{ marginBottom: 16 }}
      title={`Chỉ tài khoản ${ROLE_LABEL[requiredRole]} (${requiredRole}) mới ${action}.`}
    />
  )
}
