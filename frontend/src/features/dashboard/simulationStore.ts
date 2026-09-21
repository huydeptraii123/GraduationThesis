/**
 * Giữ kết quả lần tính phương án cắt gần nhất trong bộ nhớ của module.
 *
 * Vì sao không phải state của trang: người dùng rời trang chủ sang màn đơn hàng rồi quay lại thì
 * component đã bị gỡ và dựng lại, state mất sạch — mà một lần tính chạy thuật toán trên toàn bộ
 * đơn tồn, không phải thứ nên âm thầm chạy lại mỗi lần đổi trang.
 *
 * Vì sao không phải `sessionStorage`: kết quả chỉ có nghĩa với trạng thái đơn hàng và tồn kho tại
 * đúng thời điểm bấm. Lưu qua các lần tải lại trang là mời người dùng đọc một con số cũ mà không
 * biết nó cũ (quyết định nghiệp vụ Q2: trang chủ chỉ tính khi bấm nút, không bao giờ tự tính).
 *
 * Bộ nhớ này mất khi tải lại trang — đúng như mong muốn: lúc đó màn hình quay về trạng thái trống
 * kèm lời giải thích, chứ không hiện số liệu không rõ tính từ bao giờ. Nó cũng bị xóa khi đăng
 * xuất (xem {@link clearLastSimulation}), vì đăng xuất chỉ đổi trạng thái trong cùng một trang:
 * không xóa thì người đăng nhập kế tiếp thấy nguyên số liệu đơn hàng của phiên trước.
 */

import { simulateCuttingPlan } from '../cutting-plans/cuttingPlansApi'
import type { CuttingPlanPreviewResponse } from '../cutting-plans/types'

let lastSimulation: CuttingPlanPreviewResponse | null = null

/**
 * Lần tính đang chạy dở, nếu có.
 *
 * Giữ ở đây chứ không ở trang là để một lượt tính không bị mất khi người dùng rời trang giữa
 * chừng: lúc quay lại, trang nối vào đúng lượt đang chạy thay vì hiện màn hình trống và bắt họ
 * chạy lại trọn thuật toán trên toàn bộ sổ đơn.
 */
let pending: Promise<CuttingPlanPreviewResponse> | null = null

export function getLastSimulation(): CuttingPlanPreviewResponse | null {
  return lastSimulation
}

/** Lượt tính đang chạy dở, để trang vừa dựng lại nối vào thay vì bắt đầu một lượt mới. */
export function getPendingSimulation(): Promise<CuttingPlanPreviewResponse> | null {
  return pending
}

/**
 * Chạy một lượt tính, hoặc trả về lượt đang chạy dở nếu có.
 *
 * Gộp hai lời gọi chồng nhau thành một: bấm hai lần liên tiếp, hoặc rời trang rồi quay lại giữa
 * chừng, đều không làm thuật toán chạy thêm lần nào trên máy chủ.
 */
export function runSimulation(): Promise<CuttingPlanPreviewResponse> {
  pending ??= simulateCuttingPlan()
    .then((result) => {
      lastSimulation = result
      return result
    })
    .finally(() => {
      pending = null
    })
  return pending
}

/** Xóa kết quả đang giữ — dùng khi một lần tính thất bại và khi đăng xuất. */
export function clearLastSimulation(): void {
  lastSimulation = null
}
