import type { ShortageRecordResponse } from './types'

type ShortageLength = Pick<ShortageRecordResponse, 'missingLengthM' | 'missingQuantity'>

/**
 * Độ dài một đoạn của dòng thiếu vật tư, chia ngược từ tổng độ dài đã lưu — đúng phép tính mà file
 * Excel dùng cho dòng thiếu toàn bộ (`averageCutLengthMm` ở backend, làm tròn nửa lên). Màn hình
 * phải tính y như file thì hai nơi mới in cùng một con số.
 *
 * Với phương án duyệt từ khi tổng được lưu đủ milimet, phép chia luôn ra số nguyên. Bước làm tròn
 * có tác dụng thật với phương án duyệt TRƯỚC đó, khi tổng còn làm tròn tới centimet: 11 thanh ×
 * 2345mm = 25,795m từng lưu thành 25,80m, chia lại vẫn ra đúng 2345mm.
 *
 * Phép chia làm trên milimet NGUYÊN, lấy thương và số dư như `BigDecimal` của backend, không chia
 * trên số mét lẻ: 2,01m × 1000 trong số thực ra 2009.9999999999998, chia 4 thanh ra
 * 502.49999999999994 và bị làm tròn xuống 502mm, trong khi file in 503mm (2010 / 4 = 502,5).
 */
export function shortageCutLengthMm(shortage: ShortageLength): number {
  const quantity = shortage.missingQuantity
  if (quantity <= 0) {
    return 0
  }
  // Tổng lưu tối đa 3 chữ số thập phân nên ×1000 chỉ lệch số nguyên một chút nhiễu số thực.
  const totalMm = Math.round(shortage.missingLengthM * 1000)
  const remainder = totalMm % quantity
  const quotient = (totalMm - remainder) / quantity
  return 2 * remainder >= quantity ? quotient + 1 : quotient
}

/**
 * Tổng độ dài thiếu tính bằng milimet = độ dài đoạn × số thanh — đúng con số file Excel in trong
 * ngoặc ở câu trạng thái. Không đọc thẳng tổng đã lưu: ở phương án cũ con số đó có thể đã mất
 * milimet (25,80m thay vì 25,795m) và màn hình sẽ lệch với file.
 */
export function shortageTotalMm(shortage: ShortageLength): number {
  return shortageCutLengthMm(shortage) * shortage.missingQuantity
}
