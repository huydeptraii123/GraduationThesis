/**
 * Làm tròn nửa lên tới `digits` chữ số thập phân — cùng quy tắc `RoundingMode.HALF_UP` mà backend
 * dùng cho các tỷ lệ phần trăm, nên một con số tính ở hai phía hiện ra giống nhau.
 *
 * Bắt buộc trước khi đưa số lẻ vào `<Statistic precision={n}>` của AntD: thuộc tính đó chỉ CẮT CỤT
 * phần thập phân (`decimal.slice(0, n)`), không làm tròn — 1,098% hiện thành "1.0" trong khi đúng
 * là "1.1". `precision` vẫn giữ lại để đệm số 0 ("1" → "1.0").
 *
 * Dịch dấu phẩy bằng số mũ dạng chuỗi thay vì nhân 10^n: 1.005 × 100 trong số thực ra
 * 100.49999999999999 và làm tròn thành 1.00, còn "1.005e2" đọc thẳng ra đúng 100.5.
 */
export function roundHalfUp(value: number, digits: number): number {
  const sign = value < 0 ? -1 : 1
  const abs = Math.abs(value)
  const text = String(abs)
  // Số đã ở dạng mũ (rất nhỏ hoặc rất lớn) thì không ghép thêm "e" được — quay về cách nhân thường.
  if (text.includes('e')) {
    return (sign * Math.round(abs * 10 ** digits)) / 10 ** digits
  }
  return sign * Number(`${Math.round(Number(`${text}e${digits}`))}e-${digits}`)
}
