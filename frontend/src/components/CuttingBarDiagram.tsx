import { useId } from 'react'

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

const VIEWBOX_WIDTH = 1000
const BAR_HEIGHT = 52
// Ngưỡng ẩn nhãn — đoạn hẹp hơn mức này (trên thang viewBox 1000) không đủ chỗ hiển thị chữ.
const MIN_LABEL_WIDTH = 60
const TICK_COUNT = 6

const REMAINDER_LABEL: Record<RemainderType, ((meters: string) => string) | null> = {
  DISCARDED: null,
  WASTE: (meters) => `⚠ Lãng phí ${meters} m`,
  RESTOCK: (meters) => `⬇ Nhập kho ${meters} m`,
}

const REMAINDER_LABEL_COLOR: Record<RemainderType, string> = {
  DISCARDED: '#000000',
  WASTE: '#d46b08',
  RESTOCK: '#237804',
}

function buildTicks(sourceLengthMm: number): string[] {
  const ticks: string[] = []
  for (let i = 0; i <= TICK_COUNT; i++) {
    const meters = (sourceLengthMm * i) / TICK_COUNT / 1000
    ticks.push(i === TICK_COUNT ? `${meters.toFixed(1)}m (Chuẩn)` : `${meters.toFixed(1)}m`)
  }
  return ticks
}

// Không đo DOM thật — chỉ cần đủ rộng chứa chữ, không cần chính xác tuyệt đối.
function estimatePillWidth(text: string): number {
  return text.length * 6.5 + 32
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

  const { segments, cursor } = pieces.reduce<{ segments: (CuttingBarPiece & { x: number; width: number })[]; cursor: number }>(
    (acc, piece) => {
      const width = (piece.lengthMm / sourceLengthMm) * VIEWBOX_WIDTH
      acc.segments.push({ ...piece, x: acc.cursor, width })
      acc.cursor += width
      return acc
    },
    { segments: [], cursor: 0 },
  )
  // Phần dư = phần viewBox còn lại (không tính riêng theo remainderMm) để không lệch do làm tròn.
  const remainderX = cursor
  const remainderWidth = VIEWBOX_WIDTH - cursor

  const remainderFill =
    remainderType === 'DISCARDED'
      ? '#d9dadb'
      : `url(#${remainderType === 'RESTOCK' ? restockPatternId : wastePatternId})`
  const remainderMeters = (remainderMm / 1000).toFixed(2)
  const remainderLabel = REMAINDER_LABEL[remainderType]?.(remainderMeters) ?? null
  // So sánh trực tiếp với độ rộng pill ước lượng (không dùng ngưỡng cố định MIN_LABEL_WIDTH rồi co
  // hẹp pill lại) — co hẹp pill mà vẫn hiện chữ cỡ đầy đủ từng làm chữ tràn ra ngoài pill khi
  // remainderWidth chỉ nhỉnh hơn ngưỡng một chút (phát hiện qua review).
  const pillWidth = remainderLabel !== null ? estimatePillWidth(remainderLabel) : 0
  const showRemainderLabel = remainderLabel !== null && remainderWidth - 16 >= pillWidth

  const ticks = buildTicks(sourceLengthMm)

  return (
    <div>
      <svg
        viewBox={`0 0 ${VIEWBOX_WIDTH} ${BAR_HEIGHT}`}
        width="100%"
        height={BAR_HEIGHT}
        role="img"
        aria-label={`Phôi dài ${(sourceLengthMm / 1000).toFixed(2)} m, ${pieces.length} đoạn cắt, phần dư ${remainderMeters} m`}
      >
        <defs>
          <pattern id={restockPatternId} width={8} height={8} patternTransform="rotate(45)" patternUnits="userSpaceOnUse">
            <rect width={8} height={8} fill="#d9f7be" fillOpacity={0.75} />
            <line x1={0} y1={0} x2={0} y2={8} stroke="#52c41a" strokeWidth={3} />
          </pattern>
          <pattern id={wastePatternId} width={8} height={8} patternTransform="rotate(45)" patternUnits="userSpaceOnUse">
            <rect width={8} height={8} fill="#ffe7ba" fillOpacity={0.8} />
            <line x1={0} y1={0} x2={0} y2={8} stroke="#fa8c16" strokeWidth={3} />
          </pattern>
        </defs>

        <rect x={0} y={0} width={VIEWBOX_WIDTH} height={BAR_HEIGHT} fill="#f0f0f0" />

        {segments.map((segment) => (
          <g key={segment.key}>
            <rect x={segment.x} y={0} width={segment.width} height={BAR_HEIGHT} fill={segment.color} />
            {segment.width >= MIN_LABEL_WIDTH && (
              <text
                x={segment.x + segment.width / 2}
                y={BAR_HEIGHT / 2 + 5}
                fill="#ffffff"
                fontSize={13}
                fontWeight={600}
                fontFamily="-apple-system, sans-serif"
                textAnchor="middle"
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
                  rx={4}
                  fill="#ffffff"
                  fillOpacity={0.92}
                />
                <text
                  x={remainderX + remainderWidth / 2}
                  y={30}
                  fill={REMAINDER_LABEL_COLOR[remainderType]}
                  fontSize={12}
                  fontWeight={700}
                  fontFamily="-apple-system, sans-serif"
                  textAnchor="middle"
                >
                  {remainderLabel}
                </text>
              </>
            )}
          </g>
        )}
      </svg>
      <div style={{ display: 'flex', justifyContent: 'space-between', paddingTop: 4, fontSize: 10, color: 'rgba(0,0,0,0.45)' }}>
        {ticks.map((tick, index) => (
          <span key={tick} style={index === ticks.length - 1 ? { fontWeight: 600, textAlign: 'right' } : undefined}>
            {tick}
          </span>
        ))}
      </div>
    </div>
  )
}
