/**
 * Hai trạng thái đáp ứng nan của một bộ cửa — nhãn và màu, khai báo đúng MỘT chỗ cho cả ba biểu đồ
 * của màn tính phương án cắt.
 *
 * Ba biểu đồ cùng nói về một phép phân loại, nên mỗi biểu đồ tự khai màu lấy là cách chắc chắn để
 * một ngày nào đó cùng một trạng thái mang hai màu trên cùng một màn hình, và người đọc phải học
 * lại nghĩa của màu ở mỗi khối.
 *
 * Chuỗi nhãn là **hằng số nghiệp vụ do backend sinh ra** (`CuttingPlanDemandView.doorSetStatus`),
 * giữ nguyên chính tả của khuôn mẫu doanh nghiệp kể cả hậu tố "TP" — đổi ở đây mà không đổi bên kia
 * thì phép gộp theo trạng thái ngừng khớp và biểu đồ rỗng.
 */

export const DOOR_SET_SUFFICIENT = 'Đủ nan TP'
export const DOOR_SET_SHORT = 'Thiếu nan'

export type DoorSetStatus = typeof DOOR_SET_SUFFICIENT | typeof DOOR_SET_SHORT

/**
 * Thứ tự cố định: đủ trước, thiếu sau — giống ảnh mẫu, và giữ nguyên qua mọi bộ lọc. Màu đi theo
 * trạng thái chứ không theo thứ hạng, nên một ngày không có bộ cửa nào thiếu nan vẫn không làm
 * "đủ nan" đổi sang màu cam.
 */
export const DOOR_SET_STATUSES: DoorSetStatus[] = [DOOR_SET_SUFFICIENT, DOOR_SET_SHORT]

/**
 * Màu lấy theo dashboard doanh nghiệp đang dùng để người đọc không phải học lại bảng màu khi đối
 * chiếu hai bên. Đã chạy qua bộ kiểm màu: ΔE 27,5 (protan) / 33,3 (tritan) / 33,6 (thị lực thường),
 * tương phản trên nền sáng đều đạt — phân biệt được cả khi mù màu, không chỉ dựa vào chú giải.
 */
export const DOOR_SET_STATUS_COLOR: Record<DoorSetStatus, string> = {
  [DOOR_SET_SUFFICIENT]: '#2196f3',
  [DOOR_SET_SHORT]: '#e8672a',
}

/** Nền biểu đồ — khe 2px giữa hai đoạn của cột chồng được vẽ bằng đúng màu này, không phải viền. */
export const CHART_SURFACE = '#ffffff'
