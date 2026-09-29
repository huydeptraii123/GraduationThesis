/**
 * Hai trạng thái đáp ứng nan của một bộ cửa — nhãn và màu, khai báo đúng MỘT chỗ cho cả ba biểu đồ
 * của màn tính phương án cắt.
 *
 * Chuỗi nhãn là **hằng số nghiệp vụ do backend sinh ra** (`CuttingPlanDemandView.doorSetStatus`),
 * giữ nguyên chính tả của khuôn mẫu doanh nghiệp kể cả hậu tố "TP" — đổi ở đây mà không đổi bên kia
 * thì phép gộp theo trạng thái ngừng khớp và biểu đồ rỗng.
 */

import { SUFFICIENCY_COLOR } from '../../components/charts/chartTheme'

export const DOOR_SET_SUFFICIENT = 'Đủ nan TP'
export const DOOR_SET_SHORT = 'Thiếu nan'

export type DoorSetStatus = typeof DOOR_SET_SUFFICIENT | typeof DOOR_SET_SHORT

/**
 * Thứ tự cố định: đủ trước, thiếu sau — giống ảnh mẫu, và giữ nguyên qua mọi bộ lọc. Màu đi theo
 * trạng thái chứ không theo thứ hạng, nên một ngày không có bộ cửa nào thiếu nan vẫn không làm
 * "đủ nan" đổi sang màu cam.
 */
export const DOOR_SET_STATUSES: DoorSetStatus[] = [DOOR_SET_SUFFICIENT, DOOR_SET_SHORT]

/** Màu lấy từ bảng màu chung của mọi biểu đồ (chartTheme) — cùng màu với tab tổng quan phương án. */
export const DOOR_SET_STATUS_COLOR: Record<DoorSetStatus, string> = {
  [DOOR_SET_SUFFICIENT]: SUFFICIENCY_COLOR.sufficient,
  [DOOR_SET_SHORT]: SUFFICIENCY_COLOR.short,
}
