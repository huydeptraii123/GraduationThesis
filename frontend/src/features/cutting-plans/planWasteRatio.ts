import { roundHalfUp } from '../../utils/roundHalfUp'

/**
 * Tỷ lệ phế của MỘT phương án, đã làm tròn nửa lên tới 1 chữ số thập phân — nguồn duy nhất cho thẻ
 * "Tỷ lệ phế trung bình", tiêu đề trang chi tiết và cột tỷ lệ phế của bảng danh sách. Ba nơi từng tự
 * tính mỗi nơi một kiểu (Statistic cắt cụt, `toFixed` trên số thực) và cùng một phương án hiện
 * 1.0% ở thẻ nhưng 1.1% ở tiêu đề ngay phía trên.
 *
 * Nhân 100 trước rồi mới chia: (0.115 / 100) * 100 ra 0.11499999999999999, (0.115 * 100) / 100 ra
 * đúng 0.115. Trả 0 khi chưa tiêu hao mét nào, như cách các màn này vẫn hiển thị.
 */
export function planWasteRatioPercent(plan: { totalWasteM: number; totalStockUsedM: number }): number {
  return plan.totalStockUsedM > 0 ? roundHalfUp((plan.totalWasteM * 100) / plan.totalStockUsedM, 1) : 0
}
