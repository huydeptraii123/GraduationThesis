/**
 * Màu có nghĩa và kích thước dùng chung cho MỌI biểu đồ — nửa còn lại của `charts.css`.
 *
 * CSS lo phần khung (font, trục, lưới, chú giải, tooltip). File này lo thứ CSS không làm thay được:
 * màu của từng chuỗi số liệu, vì Recharts nhận màu đó qua props và dùng lại nó để tô ô màu của chú
 * giải lẫn tooltip. Mỗi biểu đồ tự khai màu lấy là cách chắc chắn để một ngày cùng một khái niệm
 * mang hai màu — đúng chuyện đã xảy ra: "đủ vật tư" từng xanh dương ở trang chủ nhưng xanh lá ở tab
 * tổng quan phương án cắt.
 *
 * Quy ước nghĩa của màu trên toàn ứng dụng:
 * - xanh dương = đủ vật tư, cam = thiếu vật tư;
 * - thang xám = phế (phần dư dưới 30cm bị bỏ, và tỷ lệ phế);
 * - xanh lá = phần dư trên 3m nhập lại kho; cam đất = phần dư 30cm–3m (lãng phí).
 */

import type { RemainderType } from '../CuttingBarDiagram'

/** Nền biểu đồ — khe 2px giữa hai đoạn của cột chồng được vẽ bằng đúng màu này, không phải viền. */
export const CHART_SURFACE = '#ffffff'

/** Rãnh nền dưới một thanh (phôi ở sơ đồ phôi) — cùng màu với lưới (`--chart-grid`). */
export const CHART_TRACK = '#f0f0f0'

/**
 * Hai trạng thái đáp ứng vật tư. Lấy theo dashboard doanh nghiệp đang dùng; đã chạy qua bộ kiểm
 * màu: ΔE 27,5 (protan) / 33,3 (tritan) / 33,6 (thị lực thường) — phân biệt được cả khi mù màu.
 */
export const SUFFICIENCY_COLOR = {
  sufficient: '#2196f3',
  short: '#e8672a',
} as const

/** Ba loại phần dư — một bộ màu cho cả biểu đồ tròn lẫn sơ đồ phôi. */
export const REMAINDER_COLOR: Record<RemainderType, string> = {
  DISCARDED: '#8c8c8c',
  WASTE: '#d46b08',
  // Đủ đậm để làm màu chữ trên nền trắng của nhãn "Nhập kho" trong sơ đồ phôi.
  RESTOCK: '#237804',
}

/** Nền nhạt của vân sọc ở sơ đồ phôi: sọc mang {@link REMAINDER_COLOR}, nền mang màu này. */
export const REMAINDER_TINT: Record<Exclude<RemainderType, 'DISCARDED'>, string> = {
  WASTE: '#ffe7ba',
  RESTOCK: '#d9f7be',
}

/**
 * Tỷ lệ phế mang một màu riêng không trùng nghĩa với màu nào khác trên biểu đồ. Không dùng lại cam
 * của phần dư 30cm–3m như trước (cùng một màu mà ở biểu đồ này là "lãng phí", biểu đồ kia là
 * "phế"), và không dùng xám: đã thử, trên màn thật một chuỗi xám đậm trông như biểu đồ bị tắt màu.
 */
export const WASTE_RATIO_COLOR = '#722ed1'
/** Chấm từng bộ cửa nhạt hơn đường gộp theo ngày để đường nổi lên trên. */
export const WASTE_RATIO_POINT_COLOR = '#b37feb'

/** Màu con số chỉ tiêu tốt/xấu (tỷ lệ phế so với mục tiêu) ở ô chỉ số cạnh biểu đồ. */
export const KPI_TONE = {
  good: '#3f8600',
  bad: '#cf1322',
} as const

/**
 * Màu gán cho từng đơn hàng trên sơ đồ phôi. Cố ý tránh tông cam và xanh lá của phân loại phần dư,
 * để "màu đơn hàng" không bị đọc nhầm thành "màu phần dư".
 */
export const ORDER_COLOR_PALETTE = [
  '#1677ff',
  '#722ed1',
  '#13c2c2',
  '#eb2f96',
  '#2f54eb',
  '#ad6800',
  '#9254de',
  '#f759ab',
  '#08979c',
  '#597ef7',
] as const

/** Chiều cao chuẩn của một biểu đồ; chỉ biểu đồ nhiều dòng (thanh ngang) mới được cao hơn. */
export const CHART_HEIGHT = 300

/** Cột mảnh hơn ô của nó — phần trống còn lại là khoảng thở, không phải chỗ để nới cột ra. */
export const BAR_SIZE = 24

/**
 * Thanh ngang: thanh 16px trong dòng 52px, để khe giữa hai thanh liền nhau là 36px.
 *
 * Khe đó phải chứa được HAI nhãn cùng lúc: nhãn đoạn hẹp của dòng trên đẩy xuống dưới, nhãn đoạn
 * hẹp của dòng dưới đẩy lên trên. Đo thật mỗi nhãn cao 16px cộng 2px khe với thanh, hai nhãn là
 * 36px — khe hẹp hơn thì chúng đè lên nhau.
 */
export const HORIZONTAL_BAR_SIZE = 16
export const HORIZONTAL_ROW_HEIGHT = 52
/** Phần không thuộc dòng nào của biểu đồ thanh ngang: trục số, chú giải và lề trên. */
export const HORIZONTAL_CHART_CHROME = 72

/** Chiều cao biểu đồ thanh ngang theo số dòng, không thấp hơn chiều cao chuẩn. */
export function horizontalChartHeight(rowCount: number): number {
  return Math.max(CHART_HEIGHT, rowCount * HORIZONTAL_ROW_HEIGHT + HORIZONTAL_CHART_CHROME)
}

/**
 * `dataKey` cho nhãn tổng của cột chồng: chỉ trả về `total` ở đoạn KHÁC 0 NGOÀI CÙNG của mỗi cột.
 *
 * Gắn cố định nhãn tổng vào chuỗi cuối là chưa đủ: Recharts không vẽ nhãn cho đoạn bằng 0, nên cột
 * nào không có đoạn cuối (vd toàn bộ cửa đủ vật tư) sẽ mất luôn con số tổng. Mỗi chuỗi gắn một
 * `<LabelList dataKey={stackTotalOn(keys, i)} />`, và chỉ đúng một chuỗi nhận được giá trị.
 */
export function stackTotalOn(keys: readonly string[], index: number) {
  return (row: Record<string, unknown>) => {
    const outermost = keys.reduce((last, key, i) => (Number(row[key] ?? 0) > 0 ? i : last), -1)
    return outermost === index ? row.total : undefined
  }
}

/** Khe hở vẽ bằng màu nền để hai đoạn cột chồng / hai lát tròn tách nhau mà không cần viền. */
export const SEGMENT_GAP = 2

/** Lề chuẩn: lề trên chừa chỗ cho nhãn số trên đỉnh cột. */
export const CHART_MARGIN = { top: 24, right: 8, bottom: 0, left: 0 }

/** Vành khuyên: số và tỷ lệ nằm ở chú giải, không có nhãn ngoài — nhãn ngoài bị cắt ở thẻ hẹp. */
export const DONUT = { innerRadius: 55, outerRadius: 85 }

/** Một mục chú giải. `shape` mặc định là ô vuông. */
export interface LegendEntry {
  id: string
  label: string
  color: string
  shape?: 'square' | 'circle' | 'line'
}
