/**
 * Ma trận phân quyền phía giao diện — bản sao duy nhất của các `@PreAuthorize` ở backend.
 *
 * Trước task 10.4 các luật này nằm rải rác thành phép so chuỗi ngay trong từng `Page.tsx`, nên một
 * lần đổi quyền ở backend phải đi sửa nhiều file và rất dễ sót. Từ nay chỉ sửa ở đây.
 *
 * Lưu ý: đây CHỈ là trải nghiệm người dùng (ẩn nút, hiện lời giải thích), không phải bảo mật —
 * chạy trên máy người dùng nên luôn có thể bỏ qua. Chặn thật nằm ở `@PreAuthorize` của backend,
 * được khoá lại bằng `RolePermissionMatrixTest`.
 */

export type Role = 'ADMIN' | 'PLANNER'

export interface RoleHolder {
  role: string
}

export const ROLE_LABEL: Record<Role, string> = {
  ADMIN: 'Quản trị',
  PLANNER: 'Kế hoạch',
}

/** Nhãn rút gọn cạnh mục menu, nói "ai sửa được" chứ không phải "ai vào được". */
export const ROLE_SHORT_LABEL: Record<Role, string> = {
  ADMIN: 'ADM',
  PLANNER: 'PLN',
}

export const ROLE_COLOR: Record<Role, string> = {
  ADMIN: 'gold',
  PLANNER: 'blue',
}

const ROLES: Role[] = ['ADMIN', 'PLANNER']

/**
 * Thu hẹp chuỗi vai trò từ backend (hoặc từ localStorage của phiên cũ) về union đã biết.
 *
 * Không dùng `as Role`: ép kiểu mù khiến một mã vai trò lạ lọt vào, mọi vị từ quyền trả false và
 * giao diện trở thành chỉ-xem một cách âm thầm, kèm nhãn màu `undefined`. Trả null để nơi gọi xử
 * lý tường minh như một phiên không hợp lệ.
 */
export function parseRole(value: string | null | undefined): Role | null {
  return ROLES.find((role) => role === value) ?? null
}

function hasRole(user: RoleHolder | null | undefined, ...roles: Role[]): boolean {
  return user != null && roles.some((role) => role === user.role)
}

/** Ghi lô tồn kho: `@PreAuthorize("hasRole('PLANNER')")` — tác vụ vận hành hằng ngày. */
export function canEditInventoryBatch(user: RoleHolder | null | undefined): boolean {
  return hasRole(user, 'PLANNER')
}

/**
 * Ghi danh mục loại thanh nan: `@PreAuthorize("hasAnyRole('ADMIN','PLANNER')")`.
 *
 * Mở cho cả hai vì `slatGroup` trên bảng này điều khiển việc sinh nhu cầu cắt — ADMIN phải sửa
 * được mã bị tạo nhầm nhóm OTHER lúc nhập tồn kho, không phải nhờ PLANNER làm hộ.
 */
export function canEditSlatMaterial(user: RoleHolder | null | undefined): boolean {
  return hasRole(user, 'ADMIN', 'PLANNER')
}

/** Ghi định mức BOM + mẫu cửa (kể cả nhập Excel): `@PreAuthorize("hasRole('ADMIN')")`. */
export function canEditBom(user: RoleHolder | null | undefined): boolean {
  return hasRole(user, 'ADMIN')
}

/** Sửa đơn hàng thủ công: `@PreAuthorize("hasAnyRole('ADMIN','PLANNER')")`. */
export function canEditSalesOrder(user: RoleHolder | null | undefined): boolean {
  return hasRole(user, 'ADMIN', 'PLANNER')
}

/** Nhập đơn hàng hàng loạt là tác vụ vận hành hằng ngày, không giao cho ADMIN. */
export function canImportSalesOrder(user: RoleHolder | null | undefined): boolean {
  return hasRole(user, 'PLANNER')
}

/**
 * Duyệt phương án cắt: `@PreAuthorize("hasRole('PLANNER')")`.
 *
 * Đây là thao tác vận hành duy nhất của nhóm chức năng phương án cắt có ghi dữ liệu — trừ tồn kho
 * và đưa đơn ra khỏi hàng chờ. Việc TÍNH phương án thì mở cho cả hai vai trò vì nó chỉ đọc, nên
 * không có vị từ riêng: mọi vai trò đã đăng nhập đều gọi được.
 */
export function canApproveCuttingPlan(user: RoleHolder | null | undefined): boolean {
  return hasRole(user, 'PLANNER')
}

/**
 * Quản lý tài khoản người dùng: `@PreAuthorize("hasRole('ADMIN')")` trên MỌI phương thức của
 * `UserController`, kể cả GET.
 *
 * Đây là màn duy nhất mà cả quyền XEM cũng bị giới hạn — các module nghiệp vụ khác mở GET cho cả hai
 * vai trò vì đó là dữ liệu cả hai cùng cần để làm việc, còn danh sách tài khoản thì không.
 */
export function canManageUsers(user: RoleHolder | null | undefined): boolean {
  return hasRole(user, 'ADMIN')
}
