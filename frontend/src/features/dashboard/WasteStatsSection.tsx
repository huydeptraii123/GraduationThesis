import { Card, Col, Empty, Row } from 'antd'
import dayjs from 'dayjs'
import { useMemo } from 'react'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ComposedChart,
  LabelList,
  Legend,
  Line,
  Pie,
  PieChart,
  ResponsiveContainer,
  Scatter,
  Tooltip as RechartsTooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { BarTopPercentLabel, LinePercentLabel } from '../../components/chartLabels'
import { SLAT_GROUP_LABEL } from '../inventory/constants'
import { REMAINDER_TYPE_LABEL } from '../cutting-plans/remainderLabels'
import type { RemainderType } from '../cutting-plans/types'
import type { DashboardResponse } from './types'

interface Props {
  data: DashboardResponse
}

// Ngưỡng tham khảo cho PLANNER, không phải ràng buộc hệ thống kiểm tra hay chặn — giữ đúng con số
// đã dùng ở tab tổng quan phương án cắt để hai màn hình không nói hai mức khác nhau.
const TARGET_WASTE_RATIO_PERCENT = 5

/**
 * Ngưỡng "lãng phí" (30cm–3m) cố ý KHÔNG lên biểu đồ tròn.
 *
 * Thuật toán chỉ cắt khi phần dư rơi dưới 30cm hoặc trên 3m, hết mức thì báo thiếu vật tư chứ
 * không hạ chuẩn, nên nó không bao giờ tự sinh ra phần dư nằm giữa — lát này rỗng vĩnh viễn và chỉ
 * làm loãng chú giải. Phần dư loại này vẫn được backend thống kê và sơ đồ phôi (CuttingBarDiagram)
 * vẫn vẽ được, nên nếu một ngày nó phát sinh thật thì vẫn còn đường để lộ ra.
 */
const HIDDEN_REMAINDER_TYPE: RemainderType = 'WASTE'

/** Cùng bảng màu với sơ đồ phôi (CuttingBarDiagram) để người dùng không phải học lại nghĩa của màu. */
const REMAINDER_COLOR: Record<RemainderType, string> = {
  DISCARDED: '#8c8c8c',
  WASTE: '#d46b08',
  RESTOCK: '#237804',
}

/** Mô tả đúng ngưỡng nghiệp vụ để biểu đồ tự giải thích, không cần tra tài liệu. */
const REMAINDER_HINT: Record<RemainderType, string> = {
  DISCARDED: 'dưới 30cm',
  WASTE: '30cm–3m',
  RESTOCK: 'trên 3m',
}

const WASTE_BAR_COLOR = '#d46b08'
const ORDER_DOT_COLOR = '#faad14'


/** Một ngày, tính bằng mili giây — đơn vị đệm hai đầu trục thời gian. */
const ONE_DAY_MS = 24 * 60 * 60 * 1000

function formatMeters(value: number): string {
  return `${Number(value ?? 0).toFixed(2)} m`
}

function formatPercent(value: number): string {
  return `${Number(value ?? 0).toFixed(1)}%`
}

/**
 * Nhãn quanh vành khuyên: số mét kèm tỷ trọng, tên loại để ở chú giải bên dưới nên không lặp lại
 * ở đây — nhãn ngắn thì đường dẫn không kéo nhãn ra khỏi mép thẻ.
 */
function renderRemainderSliceLabel(entry: { value?: number; percent?: number }): string {
  const percent = ((entry.percent ?? 0) * 100).toFixed(1)
  return `${formatMeters(entry.value ?? 0)} (${percent}%)`
}

interface OrderPoint {
  ts: number
  wasteRatioPercent: number
  label: string
  wasteM: number
  stockUsedM: number
}

interface DayPoint {
  ts: number
  wasteRatioPercent: number
  orderCount: number
  wasteM: number
  stockUsedM: number
}

/**
 * Chú giải cho biểu đồ xu hướng. Recharts 3 đã bỏ prop `payload` của `<Legend>` nên muốn cố định
 * nội dung chú giải thì phải truyền qua `content`.
 */
function TrendLegend() {
  const entries = [
    { color: ORDER_DOT_COLOR, text: 'Từng bộ cửa', shape: 'circle' as const },
    { color: WASTE_BAR_COLOR, text: 'Tỷ lệ phế chung theo ngày', shape: 'line' as const },
  ]
  return (
    <div style={{ display: 'flex', gap: 16, justifyContent: 'center', fontSize: 12 }}>
      {entries.map((entry) => (
        <span key={entry.text} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
          <span
            style={{
              width: entry.shape === 'line' ? 14 : 8,
              height: entry.shape === 'line' ? 2 : 8,
              borderRadius: entry.shape === 'line' ? 0 : '50%',
              background: entry.color,
            }}
          />
          {entry.text}
        </span>
      ))}
    </div>
  )
}

interface TrendTooltipProps {
  active?: boolean
  payload?: { name?: string; payload?: OrderPoint | DayPoint }[]
}

/**
 * Hai chuỗi dữ liệu nằm chung một biểu đồ nên tooltip phải tự phân biệt: điểm của một bộ cửa có
 * `label`, điểm gộp theo ngày có `orderCount`.
 */
function TrendTooltip({ active, payload }: TrendTooltipProps) {
  if (!active || !payload?.length) {
    return null
  }
  return (
    <div style={{ background: '#fff', border: '1px solid #d9d9d9', borderRadius: 4, padding: '6px 10px', fontSize: 12 }}>
      {payload.map((entry, index) => {
        const point = entry.payload
        if (!point) {
          return null
        }
        const isDay = 'orderCount' in point
        return (
          <div key={index} style={{ marginBottom: index === payload.length - 1 ? 0 : 6 }}>
            <div style={{ fontWeight: 600 }}>
              {isDay
                ? `${dayjs(point.ts).format('DD/MM/YYYY')} — ${point.orderCount} bộ cửa`
                : (point as OrderPoint).label}
            </div>
            <div>Tỷ lệ phế: {formatPercent(point.wasteRatioPercent)}</div>
            <div style={{ color: '#8c8c8c' }}>
              {formatMeters(point.wasteM)} phế / {formatMeters(point.stockUsedM)} tiêu hao
            </div>
          </div>
        )
      })}
    </div>
  )
}

export function WasteStatsSection({ data }: Props) {
  const orderPoints = useMemo<OrderPoint[]>(
    () =>
      data.orderWasteTrend
        .filter((point) => Boolean(point.reqdDeliveryDate))
        .map((point) => ({
          ts: dayjs(point.reqdDeliveryDate).valueOf(),
          wasteRatioPercent: point.wasteRatioPercent,
          label: `${point.ycsx} / ${point.item} — giao ${dayjs(point.reqdDeliveryDate).format('DD/MM/YYYY')}`,
          wasteM: point.wasteM,
          stockUsedM: point.stockUsedM,
        })),
    [data.orderWasteTrend],
  )

  /**
   * Điểm gộp theo ngày là tỷ lệ phế CHUNG của mọi bộ cửa giao ngày đó — cộng tử số rồi chia tổng
   * mẫu số, KHÔNG phải trung bình cộng các tỷ lệ. Trung bình cộng sẽ cho một bộ cửa bé xíu cùng
   * tiếng nói với một bộ cửa ngốn gấp trăm lần vật tư, đúng kiểu sai lệch mà chính biểu đồ này
   * sinh ra để tránh.
   */
  const dayPoints = useMemo<DayPoint[]>(() => {
    const byDay = new Map<number, DayPoint>()
    for (const point of orderPoints) {
      const existing = byDay.get(point.ts)
      if (existing) {
        existing.wasteM += point.wasteM
        existing.stockUsedM += point.stockUsedM
        existing.orderCount += 1
      } else {
        byDay.set(point.ts, {
          ts: point.ts,
          wasteRatioPercent: 0,
          orderCount: 1,
          wasteM: point.wasteM,
          stockUsedM: point.stockUsedM,
        })
      }
    }
    return [...byDay.values()]
      .map((day) => ({
        ...day,
        wasteRatioPercent: day.stockUsedM > 0 ? (day.wasteM / day.stockUsedM) * 100 : 0,
      }))
      .sort((left, right) => left.ts - right.ts)
  }, [orderPoints])

  /**
   * Trục thời gian phải tự đệm hai đầu: mọi bộ cửa rơi đúng một ngày thì `dataMin === dataMax`,
   * miền rộng 0 và Recharts dồn hết điểm vào mép trái.
   */
  const timeDomain = useMemo<[number, number]>(() => {
    if (orderPoints.length === 0) {
      return [0, 0]
    }
    const values = orderPoints.map((point) => point.ts)
    const min = Math.min(...values)
    const max = Math.max(...values)
    const padding = Math.max(ONE_DAY_MS / 2, (max - min) * 0.08)
    return [min - padding, max + padding]
  }, [orderPoints])

  const breakdown = useMemo(
    () =>
      data.remainderBreakdown
        .filter((row) => row.remainderType !== HIDDEN_REMAINDER_TYPE)
        .map((row) => ({
          key: row.remainderType,
          name: `${REMAINDER_TYPE_LABEL[row.remainderType]} (${REMAINDER_HINT[row.remainderType]})`,
          value: row.totalM,
        })),
    [data.remainderBreakdown],
  )
  const breakdownTotal = breakdown.reduce((sum, row) => sum + row.value, 0)

  const byGroup = useMemo(
    () =>
      data.wasteByGroup.map((row) => ({
        name: SLAT_GROUP_LABEL[row.slatGroup],
        wasteRatioPercent: row.wasteRatioPercent,
        totalM: row.totalM,
        stockUsedM: row.stockUsedM,
      })),
    [data.wasteByGroup],
  )

  return (
    <Row gutter={16} style={{ marginTop: 16 }}>
      <Col span={12}>
        <Card
          size="small"
          title="Xu hướng tỷ lệ phế theo ngày giao"
          extra={<span style={{ fontSize: 12, color: '#8c8c8c' }}>mục tiêu ≤ {TARGET_WASTE_RATIO_PERCENT}%</span>}
        >
          {orderPoints.length === 0 ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Chưa có bộ cửa nào được cắt" />
          ) : (
            <ResponsiveContainer width="100%" height={240}>
              <ComposedChart margin={{ top: 22, right: 12, bottom: 0, left: 0 }}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis
                  dataKey="ts"
                  type="number"
                  scale="time"
                  domain={timeDomain}
                  tickFormatter={(value: number) => dayjs(value).format('DD/MM')}
                />
                <YAxis unit="%" />
                <RechartsTooltip content={<TrendTooltip />} cursor={{ strokeDasharray: '3 3' }} />
                <Legend content={<TrendLegend />} />
                <Scatter name="Từng bộ cửa" data={orderPoints} dataKey="wasteRatioPercent" fill={ORDER_DOT_COLOR} />
                {/* Chỉ đường gộp theo ngày mang nhãn số: 64 chấm bộ cửa dồn vào 5 ngày nên ghi
                    số lên từng chấm sẽ chồng đè thành vệt, không đọc nổi con số nào. Người xem
                    tổng quát cần đúng con số của từng ngày, còn chi tiết từng bộ cửa thì di chuột. */}
                <Line
                  name="Tỷ lệ phế chung theo ngày"
                  data={dayPoints}
                  type="monotone"
                  dataKey="wasteRatioPercent"
                  stroke={WASTE_BAR_COLOR}
                  strokeWidth={2}
                  dot={{ r: 3 }}
                >
                  <LabelList dataKey="wasteRatioPercent" content={<LinePercentLabel />} />
                </Line>
              </ComposedChart>
            </ResponsiveContainer>
          )}
        </Card>
      </Col>

      <Col span={12}>
        <Card size="small" title="Cơ cấu phần dư theo ngưỡng xử lý">
          {breakdownTotal === 0 ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Chưa phát sinh phần dư nào" />
          ) : (
            <ResponsiveContainer width="100%" height={240}>
              <PieChart margin={{ top: 8, right: 8, bottom: 0, left: 8 }}>
                <Pie
                  data={breakdown}
                  dataKey="value"
                  nameKey="name"
                  innerRadius={44}
                  outerRadius={70}
                  label={renderRemainderSliceLabel}
                  labelLine
                >
                  {breakdown.map((row) => (
                    <Cell key={row.key} fill={REMAINDER_COLOR[row.key]} />
                  ))}
                </Pie>
                <Legend />
                <RechartsTooltip formatter={(value) => formatMeters(Number(value ?? 0))} />
              </PieChart>
            </ResponsiveContainer>
          )}
        </Card>
      </Col>

      <Col span={24}>
        <Card size="small" title="Tỷ lệ phế theo nhóm thanh nan" style={{ marginTop: 16 }}>
          {byGroup.length === 0 ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Chưa tiêu hao vật tư nào" />
          ) : (
            <ResponsiveContainer width="100%" height={240}>
              <BarChart data={byGroup} margin={{ top: 22, right: 8, bottom: 0, left: 0 }}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="name" />
                <YAxis unit="%" />
                <RechartsTooltip
                  formatter={(value, _name, entry) => {
                    const row = entry?.payload as (typeof byGroup)[number] | undefined
                    return [
                      `${formatPercent(Number(value ?? 0))} (${formatMeters(row?.totalM ?? 0)} phế / ${formatMeters(
                        row?.stockUsedM ?? 0,
                      )} tiêu hao)`,
                      'Tỷ lệ phế',
                    ]
                  }}
                />
                <Bar dataKey="wasteRatioPercent" name="Tỷ lệ phế" fill={WASTE_BAR_COLOR}>
                  <LabelList dataKey="wasteRatioPercent" content={<BarTopPercentLabel />} />
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          )}
        </Card>
      </Col>
    </Row>
  )
}
