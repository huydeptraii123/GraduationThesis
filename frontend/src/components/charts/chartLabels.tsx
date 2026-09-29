/**
 * Nhãn số hiển thị THẲNG trên biểu đồ, không phải chờ di chuột.
 *
 * Đây là yêu cầu của doanh nghiệp: người xem tổng quát cần đọc được con số ngay, di chuột từng
 * mark là mất thời gian. Gom vào một chỗ vì nhiều màn hình cùng vẽ biểu đồ có nhãn — hai bản sao
 * đối ứng của cùng một quy ước nhãn là đúng thứ đã gây lỗi ở chú giải biểu đồ trước đây.
 *
 * Cỡ chữ, màu chữ và viền trắng nằm ở `charts.css` (các class `chart-value-label*`); ở đây chỉ có
 * VỊ TRÍ. Ngoại lệ duy nhất là màu của nhãn bị đẩy ra ngoài một đoạn quá mỏng: nó mang đúng màu
 * chuỗi của đoạn đó, truyền qua `style` vì màu chuỗi là dữ liệu, không phải phong cách.
 *
 * File này cố ý chỉ xuất COMPONENT, không xuất hằng số: xuất lẫn lộn cả hai làm hỏng hot-reload
 * của Vite và sinh cảnh báo `react(only-export-components)`. Nơi dùng chỉ việc cắm component vào
 * `content` của `<LabelList>`.
 */

/*
 * Các ngưỡng hình học dưới đây đo cho chữ 11px (`--chart-text-value` trong charts.css). Đổi cỡ chữ
 * ở đó thì phải đo lại ở đây.
 */

/**
 * Chiều cao tối thiểu để một đoạn cột chứa nổi chữ bên trong.
 *
 * Đo thật trên trình duyệt: nhãn 11px chiếm 15px chiều cao dòng, trong khi đoạn cột ứng với 1 bộ
 * cửa chỉ cao khoảng 5px. In chữ trắng vào đoạn đó thì chữ tràn sang đoạn màu bên cạnh và bị đọc
 * nhầm thành số của đoạn kia.
 */
const MIN_INNER_LABEL_HEIGHT = 16

/**
 * Bề rộng ước lượng của một chữ số 11px cộng phần đệm hai bên — dùng cho thanh NẰM NGANG, nơi thứ
 * quyết định chữ có lọt trong đoạn hay không là bề rộng chứ không phải chiều cao.
 */
const DIGIT_WIDTH = 7
const INNER_LABEL_PADDING_X = 6

/** Khe giữa mép cột nằm ngang và nhãn bị đẩy ra ngoài. */
const THIN_LABEL_GAP = 2

/** Khe giữa đỉnh cột (hoặc đầu thanh ngang) và nhãn tổng / phần trăm. */
const END_LABEL_GAP = 6

const LABEL_INSIDE = 'chart-value-label chart-value-label--inside'
const LABEL_HALO = 'chart-value-label chart-value-label--halo'

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
  /** Hướng của cột: `vertical` là cột đứng (mặc định), `horizontal` là thanh nằm ngang. */
  layout?: 'vertical' | 'horizontal'
  /**
   * Chỉ dùng với thanh nằm ngang: đoạn quá hẹp thì nhãn ra phía TRÊN hay phía DƯỚI thanh. Mỗi chuỗi
   * dữ liệu chọn một phía, cùng lý do với {@link thinOffsetX} của cột đứng.
   */
  thinSide?: 'above' | 'below'
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
 * Thanh NẰM NGANG là phép đối xứng của đúng quy tắc đó: thứ hết chỗ là bề RỘNG của đoạn (một bộ
 * đủ nan trong cột 141 bộ chỉ rộng chừng 1px), còn chỗ trống nằm ở chiều dày thanh — nên nhãn đoạn
 * hẹp được đẩy ra phía trên hoặc phía dưới thanh thay vì lệch ngang.
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
  layout = 'vertical',
  thinSide = 'above',
}: StackedSegmentLabelProps) {
  const count = toNumber(value)
  if (count <= 0) {
    return null
  }
  const thinStyle = thinColor ? { fill: thinColor } : undefined
  const boxHeight = toNumber(height)
  if (layout === 'horizontal') {
    const boxWidth = toNumber(width)
    const fitsInside = boxWidth >= String(count).length * DIGIT_WIDTH + INNER_LABEL_PADDING_X
    const centerX = toNumber(x) + boxWidth / 2
    if (fitsInside) {
      return (
        <text
          x={centerX}
          y={toNumber(y) + boxHeight / 2}
          textAnchor="middle"
          dominantBaseline="central"
          className={LABEL_INSIDE}
        >
          {count}
        </text>
      )
    }
    const above = thinSide === 'above'
    return (
      <text
        x={centerX}
        y={above ? toNumber(y) - THIN_LABEL_GAP : toNumber(y) + boxHeight + THIN_LABEL_GAP}
        textAnchor="middle"
        dominantBaseline={above ? 'text-after-edge' : 'text-before-edge'}
        className={LABEL_HALO}
        style={thinStyle}
      >
        {count}
      </text>
    )
  }
  const fitsInside = boxHeight >= MIN_INNER_LABEL_HEIGHT
  return (
    <text
      x={toNumber(x) + toNumber(width) / 2 + (fitsInside ? 0 : thinOffsetX)}
      y={toNumber(y) + boxHeight / 2}
      textAnchor="middle"
      dominantBaseline="central"
      className={fitsInside ? LABEL_INSIDE : LABEL_HALO}
      style={fitsInside ? undefined : thinStyle}
    >
      {count}
    </text>
  )
}

interface StackTotalLabelProps extends LabelGeometry {
  /** `vertical`: trên đỉnh cột đứng (mặc định). `horizontal`: ngay sau đầu thanh nằm ngang. */
  layout?: 'vertical' | 'horizontal'
}

/**
 * Tổng của cả cột chồng, đặt ở đầu tự do của đoạn ngoài cùng. Dùng cùng `stackTotalOn` (chartTheme):
 * mỗi chuỗi gắn một nhãn, chỉ đoạn khác 0 ngoài cùng nhận được giá trị — các đoạn khác nhận
 * `undefined` và không vẽ gì.
 */
export function StackTotalLabel({ x, y, width, height, value, layout = 'vertical' }: StackTotalLabelProps) {
  if (value == null) {
    return null
  }
  if (layout === 'horizontal') {
    return (
      <text
        x={toNumber(x) + toNumber(width) + END_LABEL_GAP}
        y={toNumber(y) + toNumber(height) / 2}
        dominantBaseline="central"
        className={LABEL_HALO}
      >
        {toNumber(value)}
      </text>
    )
  }
  return (
    <text x={toNumber(x) + toNumber(width) / 2} y={toNumber(y) - END_LABEL_GAP} textAnchor="middle" className={LABEL_HALO}>
      {toNumber(value)}
    </text>
  )
}

/** Phần trăm đặt trên đỉnh cột. */
export function BarTopPercentLabel({ x, y, width, value }: LabelGeometry) {
  return (
    <text x={toNumber(x) + toNumber(width) / 2} y={toNumber(y) - END_LABEL_GAP} textAnchor="middle" className={LABEL_HALO}>
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
    <text x={toNumber(x)} y={toNumber(y) - 10} textAnchor="middle" className={LABEL_HALO}>
      {formatPercent(value)}
    </text>
  )
}
