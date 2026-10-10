import type { ShortageRecordResponse } from './types'

type ShortageLength = Pick<ShortageRecordResponse, 'missingLengthM' | 'missingQuantity'>

/**
 * Độ dài một đoạn của dòng thiếu vật tư, chia ngược từ tổng độ dài đã lưu — đúng phép tính mà file
 * Excel dùng cho dòng thiếu toàn bộ (`averageCutLengthMm` ở backend, làm tròn nửa lên). Màn hình
 * phải tính y như file thì hai nơi mới in cùng một con số.
 *
 * Với phương án duyệt từ khi tổng được lưu đủ milimet, phép chia luôn ra số nguyên và `Math.round`
 * chỉ gạt nhiễu số thực. Nó có tác dụng thật với phương án duyệt TRƯỚC đó, khi tổng còn làm tròn
 * tới centimet: 11 thanh × 2345mm = 25,795m từng lưu thành 25,80m, chia lại vẫn ra đúng 2345mm.
 */
export function shortageCutLengthMm(shortage: ShortageLength): number {
  return shortage.missingQuantity > 0
    ? Math.round((shortage.missingLengthM * 1000) / shortage.missingQuantity)
    : 0
}

/**
 * Tổng độ dài thiếu tính bằng milimet = độ dài đoạn × số thanh — đúng con số file Excel in trong
 * ngoặc ở câu trạng thái. Không đọc thẳng tổng đã lưu: ở phương án cũ con số đó có thể đã mất
 * milimet (25,80m thay vì 25,795m) và màn hình sẽ lệch với file.
 */
export function shortageTotalMm(shortage: ShortageLength): number {
  return shortageCutLengthMm(shortage) * shortage.missingQuantity
}
