import { useId, useLayoutEffect, useRef, useState } from 'react'
import { formatMeters } from '../utils/formatMeters'
import { CHART_SURFACE, CHART_TRACK, REMAINDER_COLOR, REMAINDER_TINT } from './charts/chartTheme'

export type RemainderType = 'DISCARDED' | 'WASTE' | 'RESTOCK'

export interface CuttingBarPiece {
  key: string
  lengthMm: number
  color: string
  label: string
}

export interface CuttingBarDiagramProps {
  sourceLengthMm: number
  pieces: CuttingBarPiece[]
  remainderMm: number
  remainderType: RemainderType
}

// Bề rộng dùng tạm cho tới khi đo được khung chứa (lượt vẽ đầu; hoặc khung rộng 0 lúc gắn vào trang).
const FALLBACK_WIDTH = 1000
const BAR_HEIGHT = 52
// Dải thang trục dưới thanh: vạch mốc dài TICK_MARK_LENGTH, nhãn mốc đặt đường chân chữ ở TICK_LABEL_BASELINE.
const TICK_MARK_LENGTH = 5
const TICK_LABEL_BASELINE = BAR_HEIGHT + 19
const DIAGRAM_HEIGHT = BAR_HEIGHT + 24
const TICK_COUNT = 6
// Bề rộng trung bình một ký tự (px) theo đúng cỡ chữ trong charts.css — chỉ để ước lượng nhãn có vừa chỗ.
const PIECE_LABEL_CHAR_WIDTH = 7.5 // .chart-bar-piece-label: 13px, đậm
const PIECE_LABEL_PADDING = 12
const TICK_LABEL_CHAR_WIDTH = 7 // .chart-bar-tick-label: 12px (mốc cuối in đậm)
const TICK_LABEL_GAP = 6

const REMAINDER_LABEL: Record<RemainderType, ((meters: string) => string) | null> = {
  DISCARDED: null,
  WASTE: (meters) => `⚠ Lãng phí ${meters} m`,
  RESTOCK: (meters) => `⬇ Nhập kho ${meters} m`,
}


function buildTicks(sourceLengthMm: number): string[] {
  const ticks: string[] = []
  for (let i = 0; i < TICK_COUNT; i++) {
    // Mốc giữa là vạch chia đều (độ dài phôi × i/6), không phải số đo — một chữ số là đủ đọc thang.
    ticks.push(`${((sourceLengthMm * i) / TICK_COUNT / 1000).toFixed(1)}m`)
  }
  // Mốc cuối chính là độ dài phôi: ghi đúng tới milimet như tiêu đề thẻ và file Excel, không làm tròn.
  ticks.push(`${formatMeters(sourceLengthMm)}m (Chuẩn)`)
  return ticks
}

// Không đo DOM thật — chỉ cần đủ rộng chứa chữ, không cần chính xác tuyệt đối.
function estimatePillWidth(text: string): number {
  return text.length * 6.5 + 32
}

/**
 * Nhãn mốc giữa nào hiện được: mốc đầu (neo trái) và mốc cuối (neo phải, dài hơn vì có "(Chuẩn)")
 * luôn hiện; mốc giữa nào ước lượng sẽ chạm vào hàng xóm đang hiện thì chỉ còn vạch mốc, không còn
 * chữ — xảy ra khi thẻ hẹp, mốc thứ 5 dễ đè lên nhãn mốc cuối.
 */
function visibleTickLabels(ticks: string[], width: number): boolean[] {
  const extent = (index: number): [number, number] => {
    const x = (width * index) / TICK_COUNT
    const textWidth = ticks[index].length * TICK_LABEL_CHAR_WIDTH
    if (index === 0) return [x, x + textWidth]
    if (index === TICK_COUNT) return [x - textWidth, x]
    return [x - textWidth / 2, x + textWidth / 2]
  }
  const visible = ticks.map(() => false)
  visible[0] = true
  visible[TICK_COUNT] = true
  let lastRight = extent(0)[1]
  const endLeft = extent(TICK_COUNT)[0]
  for (let i = 1; i < TICK_COUNT; i++) {
    const [left, right] = extent(i)
    if (left >= lastRight + TICK_LABEL_GAP && right + TICK_LABEL_GAP <= endLeft) {
      visible[i] = true
      lastRight = right
    }
  }
  return visible
}

/**
 * Vẽ 1 thanh phôi tồn kho đã cắt — thuần trình bày, không gọi API, không biết về CuttingPlanDetailResponse.
 * Trang gọi component này (task 9.4) tự chuyển đổi dữ liệu backend thành pieces (kể cả giãn 1 item
 * cutQuantity=N thành N đoạn liên tiếp) và tự quản lý màu theo đơn hàng xuyên suốt nhiều thanh.
 */
export function CuttingBarDiagram({ sourceLengthMm, pieces, remainderMm, remainderType }: CuttingBarDiagramProps) {
  const patternPrefix = useId().replace(/:/g, '')
  const restockPatternId = `${patternPrefix}-restock`
  const wastePatternId = `${patternPrefix}-waste`

  // viewBox luôn rộng đúng bằng khung chứa đo được, nên 1 đơn vị vẽ = 1 pixel: thanh lẫn thang trục
  // cùng trải từ mép trái tới mép phải khung, và cỡ chữ trong charts.css là pixel thật. Trước đây
  // viewBox cố định 1000 với chiều cao cố định khiến thẻ rộng hơn 1000px bị thu thanh về 1000px và
  // căn giữa, trong khi thang trục (div HTML riêng) trải hết bề rộng — hai bên lệch nhau.
  const containerRef = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState(FALLBACK_WIDTH)
  useLayoutEffect(() => {
    const element = containerRef.current
    if (!element) return
    const measure = () => {
      const measured = element.getBoundingClientRect().width
      if (measured > 0) setWidth(measured)
    }
    measure()
    if (typeof ResizeObserver === 'undefined') return
    const observer = new ResizeObserver(measure)
    observer.observe(element)
    return () => observer.disconnect()
  }, [])

  const { segments, cursor } = pieces.reduce<{ segments: (CuttingBarPiece & { x: number; width: number })[]; cursor: number }>(
    (acc, piece) => {
      const segmentWidth = (piece.lengthMm / sourceLengthMm) * width
      acc.segments.push({ ...piece, x: acc.cursor, width: segmentWidth })
      acc.cursor += segmentWidth
      return acc
    },
    { segments: [], cursor: 0 },
  )
  // Phần dư = phần thanh còn lại (không tính riêng theo remainderMm) để không lệch do làm tròn.
  const remainderX = cursor
  const remainderWidth = width - cursor

  // Cùng bộ màu phần dư với biểu đồ tròn "Cơ cấu phần dư" (chartTheme): phần bỏ tô đặc, hai loại
  // còn lại tô vân sọc để phân biệt được cả khi in trắng đen.
  const remainderFill =
    remainderType === 'DISCARDED'
      ? REMAINDER_COLOR.DISCARDED
      : `url(#${remainderType === 'RESTOCK' ? restockPatternId : wastePatternId})`
  const remainderMeters = formatMeters(remainderMm)
  const remainderLabel = REMAINDER_LABEL[remainderType]?.(remainderMeters) ?? null
  // So sánh trực tiếp với độ rộng pill ước lượng (không dùng một ngưỡng cố định rồi co hẹp pill
  // lại) — co hẹp pill mà vẫn hiện chữ cỡ đầy đủ từng làm chữ tràn ra ngoài pill khi
  // remainderWidth chỉ nhỉnh hơn ngưỡng một chút (phát hiện qua review).
  const pillWidth = remainderLabel !== null ? estimatePillWidth(remainderLabel) : 0
  const showRemainderLabel = remainderLabel !== null && remainderWidth - 16 >= pillWidth

  const ticks = buildTicks(sourceLengthMm)
  const tickLabelVisible = visibleTickLabels(ticks, width)

  return (
    <div ref={containerRef}>
      <svg
        viewBox={`0 0 ${width} ${DIAGRAM_HEIGHT}`}
        width="100%"
        height={DIAGRAM_HEIGHT}
        // Khi số đo chậm một nhịp so với khung (vừa đổi cỡ cửa sổ, hoặc lúc in trang) thì hình co
        // đều và neo trái — chữ không bị méo, còn thanh và thang trục vẫn trùng khít vì nằm trong
        // cùng một SVG.
        preserveAspectRatio="xMinYMin meet"
        role="img"
        aria-label={`Phôi dài ${formatMeters(sourceLengthMm)} m, ${pieces.length} đoạn cắt, phần dư ${remainderMeters} m`}
      >
        <defs>
          <pattern id={restockPatternId} width={8} height={8} patternTransform="rotate(45)" patternUnits="userSpaceOnUse">
            <rect width={8} height={8} fill={REMAINDER_TINT.RESTOCK} />
            <line x1={0} y1={0} x2={0} y2={8} stroke={REMAINDER_COLOR.RESTOCK} strokeWidth={3} />
          </pattern>
          <pattern id={wastePatternId} width={8} height={8} patternTransform="rotate(45)" patternUnits="userSpaceOnUse">
            <rect width={8} height={8} fill={REMAINDER_TINT.WASTE} />
            <line x1={0} y1={0} x2={0} y2={8} stroke={REMAINDER_COLOR.WASTE} strokeWidth={3} />
          </pattern>
        </defs>

        <rect x={0} y={0} width={width} height={BAR_HEIGHT} fill={CHART_TRACK} />

        {segments.map((segment) => (
          <g key={segment.key}>
            <rect x={segment.x} y={0} width={segment.width} height={BAR_HEIGHT} fill={segment.color} />
            {/* Cùng cách với nhãn phần dư: chỉ hiện khi chữ ước lượng vừa trong đoạn, để nhãn không tràn sang đoạn bên cạnh. */}
            {segment.width >= segment.label.length * PIECE_LABEL_CHAR_WIDTH + PIECE_LABEL_PADDING && (
              <text
                x={segment.x + segment.width / 2}
                y={BAR_HEIGHT / 2 + 5}
                textAnchor="middle"
                className="chart-bar-piece-label"
              >
                {segment.label}
              </text>
            )}
          </g>
        ))}

        {remainderWidth > 0 && (
          <g>
            <rect x={remainderX} y={0} width={remainderWidth} height={BAR_HEIGHT} fill={remainderFill} />
            {showRemainderLabel && (
              <>
                <rect
                  x={remainderX + remainderWidth / 2 - pillWidth / 2}
                  y={12}
                  width={pillWidth}
                  height={28}
                  fill={CHART_SURFACE}
                  fillOpacity={0.92}
                />
                <text
                  x={remainderX + remainderWidth / 2}
                  y={30}
                  textAnchor="middle"
                  className="chart-bar-remainder-label"
                  // Màu chữ là màu của loại phần dư (dữ liệu), nên đi qua style chứ không qua CSS.
                  style={{ fill: REMAINDER_COLOR[remainderType] }}
                >
                  {remainderLabel}
                </text>
              </>
            )}
          </g>
        )}

        {ticks.map((tick, index) => {
          const x = (width * index) / TICK_COUNT
          const isEnd = index === TICK_COUNT
          // Vạch ở hai mép lùi vào nửa nét để không bị mép SVG cắt mất một nửa.
          const markX = index === 0 ? 0.5 : isEnd ? width - 0.5 : x
          return (
            <g key={index}>
              <line x1={markX} y1={BAR_HEIGHT} x2={markX} y2={BAR_HEIGHT + TICK_MARK_LENGTH} className="chart-bar-tick" />
              {tickLabelVisible[index] && (
                <text
                  x={x}
                  y={TICK_LABEL_BASELINE}
                  textAnchor={index === 0 ? 'start' : isEnd ? 'end' : 'middle'}
                  className={isEnd ? 'chart-bar-tick-label chart-bar-tick-label--end' : 'chart-bar-tick-label'}
                >
                  {tick}
                </text>
              )}
            </g>
          )
        })}
      </svg>
    </div>
  )
}
