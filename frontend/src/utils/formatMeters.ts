/**
 * Độ dài hiển thị theo mét, đúng tới milimet và KHÔNG làm tròn: 4745mm → "4.745", 4740mm → "4.74",
 * 4000mm → "4". Cùng một dạng với câu chữ trong file Excel xuất ra, để thợ đối chiếu màn hình với
 * file thấy khớp từng chữ số — doanh nghiệp yêu cầu mọi độ dài chính xác tuyệt đối vì xưởng cắt
 * theo đúng con số này.
 *
 * Tính trên số nguyên milimet (phần nguyên + 3 chữ số dư, bỏ số 0 cuối) chứ không qua `toFixed`,
 * vì `toFixed` bắt buộc chọn trước một số chữ số và làm tròn phần còn lại.
 *
 * `Math.round` ở đầu KHÔNG làm tròn giá trị: mọi độ dài trong hệ thống vốn là số nguyên milimet,
 * nhưng nơi gọi có số liệu theo mét (vd tổng độ dài thiếu) phải nhân 1000 và phép nhân số thực có
 * thể ra 2344.9999999 — bước này chỉ khôi phục lại đúng số nguyên đó.
 */
export function formatMeters(lengthMm: number): string {
  const mm = Math.round(lengthMm)
  const sign = mm < 0 ? '-' : ''
  const abs = Math.abs(mm)
  const whole = Math.floor(abs / 1000)
  const fraction = String(abs % 1000).padStart(3, '0').replace(/0+$/, '')
  return fraction ? `${sign}${whole}.${fraction}` : `${sign}${whole}`
}
