import { roundHalfUp, toCentimetres } from '../../utils/roundHalfUp'

/**
 * Tỷ lệ phế của MỘT phương án, đã làm tròn nửa lên tới 1 chữ số thập phân — nguồn duy nhất cho thẻ
 * "Tỷ lệ phế trung bình", tiêu đề trang chi tiết và cột tỷ lệ phế của bảng danh sách. Ba nơi từng tự
 * tính mỗi nơi một kiểu (Statistic cắt cụt, `toFixed` trên số thực) và cùng một phương án hiện
 * 1.0% ở thẻ nhưng 1.1% ở tiêu đề ngay phía trên.
 *
 * Chia trên centimet nguyên, nhân 100 trước: chỉ nhân 100 trước trên số mét lẻ vẫn lệch ở mốc nửa
 * (xem {@link toCentimetres}). Trả 0 khi chưa tiêu hao mét nào, như cách các màn này vẫn hiển thị.
 */
export function planWasteRatioPercent(plan: { totalWasteM: number; totalStockUsedM: number }): number {
  const stockUsedCm = toCentimetres(plan.totalStockUsedM)
  return stockUsedCm > 0 ? roundHalfUp((toCentimetres(plan.totalWasteM) * 100) / stockUsedCm, 1) : 0
}
