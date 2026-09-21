import type {
  CuttingPlanApprovalPreviewResponse,
  CuttingPlanResponse,
} from './types'

/**
 * Gán khóa hiển thị cục bộ cho một phương án chưa được lưu, để ba tab của màn hình phương án cắt
 * dùng lại nguyên vẹn.
 *
 * Backend cố ý trả `id: null` cho mọi dòng: chưa có bản ghi nào tồn tại nên một id bịa ra là thứ
 * giao diện có thể vô tình đem đi tra cứu. Nhưng React lại cần khóa duy nhất cho mỗi dòng bảng, và
 * `null` thì trùng nhau hết — bảng sẽ vẽ sai khi sắp xếp hoặc lọc. Số thứ tự gán ở đây chỉ sống
 * trong lần hiển thị này và không bao giờ được gửi ngược lên máy chủ.
 *
 * Số thứ tự chạy liên tục qua mọi đoạn cắt của mọi phôi, không đánh lại từ 0 ở từng phôi: sơ đồ cắt
 * gom các đoạn của nhiều phôi vào cùng một danh sách nên khóa trùng sẽ làm hai đoạn khác nhau đè
 * lên nhau.
 */
export function toDisplayPlan(preview: CuttingPlanApprovalPreviewResponse): CuttingPlanResponse {
  let itemKey = 0
  return {
    // Phương án chưa lưu nên chưa có mã; các tab chỉ dùng trường này làm khóa nhớ lại bảng màu.
    id: 0,
    runAt: preview.plan.computedAt,
    status: 'COMPLETED',
    totalWasteM: preview.plan.totalWasteM,
    totalStockUsedM: preview.plan.totalStockUsedM,
    scopeCutoffDate: preview.scopeCutoffDate,
    scopeOrderCount: preview.plan.scopeOrderCount,
    details: preview.details.map((detail, detailIndex) => ({
      ...detail,
      id: detailIndex,
      items: detail.items.map((item) => ({ ...item, id: itemKey++ })),
    })),
    shortages: preview.shortages.map((shortage, index) => ({ ...shortage, id: index })),
  }
}
