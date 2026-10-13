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

/**
 * Chuỗi `digits` chữ số thập phân, làm tròn nửa lên — thay cho `value.toFixed(digits)`.
 *
 * `toFixed` làm tròn giá trị NHỊ PHÂN chứ không phải số thập phân ta thấy: 0.15 thật ra là
 * 0.1499999… nên `(0.15).toFixed(1)` ra "0.1", trong khi backend (`BigDecimal` HALF_UP) ra 0.2.
 *
 * Kết quả chỉ đúng khi `value` là kết quả của MỘT phép chia trên số nguyên (nhân 100 trước rồi mới
 * chia): phép chia số thực trả về số gần đúng nhất, nên mọi giá trị đúng bằng nửa (0.15) đều in ra
 * đúng chuỗi "0.15" và {@link roundHalfUp} đọc được. Chia trên số mét lẻ rồi mới nhân 100 thì lệch:
 * (0.09 / 20) * 100 ra 0.44999999999999996 (làm tròn thành 0.4), còn (9 * 100) / 2000 ra đúng 0.45.
 */
export function formatHalfUp(value: number, digits: number): string {
  return roundHalfUp(value, digits).toFixed(digits)
}

/**
 * Số mét (backend trả tối đa 2 chữ số thập phân) đổi về centimet NGUYÊN, để một tỷ lệ tính từ hai
 * số mét là một phép chia trên số nguyên — điều kiện để {@link roundHalfUp} làm tròn đúng. Chỉ nhân
 * 100 trước rồi chia là chưa đủ khi số mét có phần lẻ: (0.29 * 100) / 20 ra 1.4499999999999997
 * (làm tròn thành 1.4), còn (29 * 100) / 2000 ra đúng 1.45.
 */
export function toCentimetres(meters: number): number {
  return Math.round(Number(meters ?? 0) * 100)
}
