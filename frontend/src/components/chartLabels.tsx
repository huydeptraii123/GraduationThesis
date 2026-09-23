/**
 * Nhãn số hiển thị THẲNG trên biểu đồ, không phải chờ di chuột.
 *
 * Đây là yêu cầu của doanh nghiệp: người xem tổng quát cần đọc được con số ngay, di chuột từng
 * mark là mất thời gian. Gom vào một chỗ vì hai màn hình cùng vẽ biểu đồ có nhãn (tab tổng quan
 * phương án cắt và khu thống kê phế liệu) — hai bản sao đối ứng của cùng một quy ước nhãn là đúng
 * thứ đã gây lỗi ở chú giải biểu đồ trước đây.
 *
 * File này cố ý chỉ xuất COMPONENT, không xuất hằng số: xuất lẫn lộn cả hai làm hỏng hot-reload
 * của Vite và sinh cảnh báo `react(only-export-components)`. Nơi dùng chỉ việc cắm component vào
 * `content` của `<LabelList>`, không phải tự ráp màu và cỡ chữ.
 */

/** Màu chữ của nhãn nằm NGOÀI mark — cùng tông với nhãn trục, không cạnh tranh với màu của mark. */
const VALUE_LABEL_COLOR = '#595959'
const VALUE_LABEL_SIZE = 11

/**
 * Chiều cao tối thiểu để một đoạn cột chứa nổi chữ bên trong.
 *
 * Đo thật trên trình duyệt: nhãn 11px chiếm 15px chiều cao dòng, trong khi đoạn cột ứng với 1 bộ
 * cửa chỉ cao khoảng 5px. In chữ trắng vào đoạn đó thì chữ tràn sang đoạn màu bên cạnh và bị đọc
 * nhầm thành số của đoạn kia.
 */
const MIN_INNER_LABEL_HEIGHT = 16

/**
 * Viền trắng quanh chữ để đọc được khi nhãn nằm đè lên mark khác.
 *
 * `paintOrder: 'stroke'` bắt SVG vẽ viền TRƯỚC rồi mới vẽ ruột chữ; thiếu nó thì viền phủ lên
 * chính nét chữ và chữ bị dày bệt lại.
 */
const HALO = {
  stroke: '#ffffff',
  strokeWidth: 3,
  paintOrder: 'stroke' as const,
  strokeLinejoin: 'round' as const,
}

/** Hình học Recharts truyền cho `content` của `<LabelList>` — mọi trường đều có thể vắng. */
interface LabelGeometry {
  x?: number | string
  y?: number | string
  width?: number | string
  height?: number | string
  value?: number | string
}

interface StackedSegmentLabelProps extends LabelGeometry {
  /**
   * Độ lệch NGANG dùng khi đoạn quá mỏng, tính từ tim cột. Mỗi chuỗi dữ liệu truyền một giá trị
   * khác dấu để hai nhãn của hai đoạn mỏng liền nhau không đè lên nhau.
   */
  thinOffsetX?: number
  /** Màu chữ khi nhãn phải ra ngoài đoạn — lấy đúng màu của chuỗi để biết nó thuộc về đoạn nào. */
  thinColor?: string
}

function toNumber(value: number | string | undefined): number {
  return Number(value ?? 0)
}

function formatPercent(value: number | string | undefined): string {
  return `${toNumber(value).toFixed(1)}%`
}

/**
 * Nhãn số của một đoạn trong cột chồng, luôn hiện chứ không ẩn đi.
 *
 * Đoạn đủ cao thì in chữ trắng vào giữa đoạn, không có gì đặc biệt.
 *
 * Đoạn quá mỏng thì chữ không nằm lọt trong đoạn được, và <b>viền trắng thôi là chưa đủ</b>: đo
 * thật trên dữ liệu thật, một cột tổng 3 bộ cửa có hai đoạn cao 10px và 5px, hai nhãn cách nhau
 * chưa tới 8px trong khi mỗi dòng chữ cao 15px — chúng đè lên nhau và chữ dưới gần như mất hẳn.
 * Vì thế nhãn của đoạn mỏng được đẩy lệch NGANG khỏi tim cột, mỗi chuỗi dữ liệu một hướng. Cột
 * rộng gần 100px nên vẫn còn thừa chỗ ngang, trong khi chiều dọc thì hết — đẩy ngang là hướng duy
 * nhất còn chỗ. Chữ đổi sang màu của chính chuỗi đó kèm viền trắng để biết nó thuộc đoạn nào.
 *
 * Đoạn bằng 0 thì bỏ hẳn: in số 0 chồng lên đường trục chỉ gây nhiễu.
 */
export function StackedSegmentLabel({
  x,
  y,
  width,
  height,
  value,
  thinOffsetX = 0,
  thinColor,
}: StackedSegmentLabelProps) {
  const count = toNumber(value)
  if (count <= 0) {
    return null
  }
  const boxHeight = toNumber(height)
  const fitsInside = boxHeight >= MIN_INNER_LABEL_HEIGHT
  return (
    <text
      x={toNumber(x) + toNumber(width) / 2 + (fitsInside ? 0 : thinOffsetX)}
      y={toNumber(y) + boxHeight / 2}
      textAnchor="middle"
      dominantBaseline="central"
      fontSize={VALUE_LABEL_SIZE}
      fill={fitsInside ? '#ffffff' : (thinColor ?? VALUE_LABEL_COLOR)}
      {...(fitsInside ? {} : HALO)}
    >
      {count}
    </text>
  )
}

/** Tổng của cả cột chồng, đặt ngay trên đỉnh cột. Gắn vào đoạn TRÊN CÙNG mới ra đúng đỉnh. */
export function StackTotalLabel({ x, y, width, value }: LabelGeometry) {
  return (
    <text
      x={toNumber(x) + toNumber(width) / 2}
      y={toNumber(y) - 6}
      textAnchor="middle"
      fontSize={VALUE_LABEL_SIZE}
      fill={VALUE_LABEL_COLOR}
      {...HALO}
    >
      {toNumber(value)}
    </text>
  )
}

/** Phần trăm đặt trên đỉnh cột. */
export function BarTopPercentLabel({ x, y, width, value }: LabelGeometry) {
  return (
    <text
      x={toNumber(x) + toNumber(width) / 2}
      y={toNumber(y) - 6}
      textAnchor="middle"
      fontSize={VALUE_LABEL_SIZE}
      fill={VALUE_LABEL_COLOR}
      {...HALO}
    >
      {formatPercent(value)}
    </text>
  )
}

/**
 * Phần trăm của một điểm trên đường, đặt phía trên điểm và luôn có viền trắng.
 *
 * Viền là bắt buộc chứ không phải trang trí: biểu đồ xu hướng có các chấm của từng bộ cửa rải
 * quanh đường, và đo thật thì 3 trên 5 nhãn rơi trúng một chấm. Không có viền thì chữ lẫn vào chấm.
 */
export function LinePercentLabel({ x, y, value }: LabelGeometry) {
  return (
    <text
      x={toNumber(x)}
      y={toNumber(y) - 10}
      textAnchor="middle"
      fontSize={VALUE_LABEL_SIZE}
      fill={VALUE_LABEL_COLOR}
      {...HALO}
    >
      {formatPercent(value)}
    </text>
  )
}
