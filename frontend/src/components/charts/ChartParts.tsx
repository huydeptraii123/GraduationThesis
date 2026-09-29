/**
 * Chú giải và tooltip dùng chung cho mọi biểu đồ — thay cho ba kiểu chú giải và hai kiểu tooltip
 * mà từng màn hình từng tự dựng. Hình thức (font, cỡ chữ, viền, khoảng cách) nằm ở `charts.css`;
 * ở đây chỉ có cấu trúc.
 *
 * File cố ý chỉ xuất COMPONENT (xem chartLabels.tsx để biết lý do); kiểu `LegendEntry` nằm ở
 * chartTheme.ts.
 */

import type { ReactNode } from 'react'
import type { LegendEntry } from './chartTheme'

function Swatch({ color, shape = 'square' }: { color: string; shape?: LegendEntry['shape'] }) {
  const modifier = shape === 'square' ? '' : ` chart-legend__swatch--${shape}`
  return <span aria-hidden className={`chart-legend__swatch${modifier}`} style={{ background: color }} />
}

interface ChartLegendProps {
  entries: LegendEntry[]
  /** Chữ in đậm đứng đầu dòng chú giải, vd "Phần dư:". */
  title?: ReactNode
  /** `start` cho chú giải đứng riêng ngoài biểu đồ (canh trái theo chữ của thẻ). */
  align?: 'center' | 'start'
}

/**
 * Chú giải dựng tay thay vì để Recharts tự suy từ các chuỗi: thứ tự tự suy chạy ngược chiều xếp
 * chồng, và một chuỗi vắng mặt trong dữ liệu sẽ biến mất khỏi chú giải — người đọc mất hẳn lời giải
 * nghĩa của màu còn lại. Dùng trong `<Legend content={<ChartLegend ... />} />`, hoặc đứng riêng.
 */
export function ChartLegend({ entries, title, align = 'center' }: ChartLegendProps) {
  return (
    <div className={align === 'start' ? 'chart-legend chart-legend--start' : 'chart-legend'}>
      {title != null && <span className="chart-legend__title">{title}</span>}
      {entries.map((entry) => (
        <span key={entry.id} className="chart-legend__item">
          <Swatch color={entry.color} shape={entry.shape} />
          {entry.label}
        </span>
      ))}
    </div>
  )
}

/** Một dòng Recharts truyền cho tooltip — mọi trường đều có thể vắng. */
interface TooltipEntry {
  name?: string | number
  value?: unknown
  color?: string
  payload?: { fill?: string } & Record<string, unknown>
}

interface ChartTooltipProps {
  active?: boolean
  payload?: readonly TooltipEntry[]
  label?: string | number
  /** Định dạng giá trị một dòng; mặc định in nguyên số. */
  formatValue?: (value: number, entry: TooltipEntry) => ReactNode
  /** Dòng ghi chú nhạt bên dưới, lấy từ dòng dữ liệu đầu tiên (vd "12 m phế / 300 m tiêu hao"). */
  note?: (row: Record<string, unknown>) => ReactNode
}

/**
 * Tooltip chuẩn: tiêu đề (nhãn trục) rồi mỗi chuỗi một dòng có ô màu. Dùng trong
 * `<Tooltip content={<ChartTooltip ... />} />` — Recharts tự bơm `active`/`payload`/`label`.
 */
export function ChartTooltip({ active, payload, label, formatValue, note }: ChartTooltipProps) {
  if (!active || !payload?.length) {
    return null
  }
  const firstRow = payload[0].payload
  return (
    <div className="chart-tooltip">
      {label != null && label !== '' && <div className="chart-tooltip__title">{label}</div>}
      {payload.map((entry, index) => {
        const color = entry.color ?? entry.payload?.fill
        const value = Number(entry.value ?? 0)
        return (
          <div key={`${entry.name ?? index}`} className="chart-tooltip__row">
            {color && <Swatch color={color} />}
            <span>
              {entry.name != null && `${entry.name}: `}
              {formatValue ? formatValue(value, entry) : value}
            </span>
          </div>
        )
      })}
      {note && firstRow && <div className="chart-tooltip__note">{note(firstRow)}</div>}
    </div>
  )
}
