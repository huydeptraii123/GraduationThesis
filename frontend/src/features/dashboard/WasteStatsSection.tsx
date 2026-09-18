import { Card, Col, Empty, Row } from 'antd'
import { useMemo } from 'react'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip as RechartsTooltip,
  XAxis,
  YAxis,
} from 'recharts'
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

/** Cùng bảng màu với sơ đồ phôi (CuttingBarDiagram) để người dùng không phải học lại nghĩa của màu. */
const REMAINDER_COLOR: Record<RemainderType, string> = {
  DISCARDED: '#8c8c8c',
  WASTE: '#d46b08',
  RESTOCK: '#237804',
}

/** Mô tả đúng 3 ngưỡng nghiệp vụ để biểu đồ tự giải thích, không cần tra tài liệu. */
const REMAINDER_HINT: Record<RemainderType, string> = {
  DISCARDED: 'dưới 30cm',
  WASTE: '30cm–3m',
  RESTOCK: 'trên 3m',
}

const WASTE_BAR_COLOR = '#d46b08'

// Recharts truyền vào kiểu ValueType (số | chuỗi | mảng | undefined) nên nhận `unknown` rồi tự ép.
function formatMeters(value: unknown): string {
  return `${Number(value ?? 0).toFixed(2)} m`
}

function formatPercent(value: unknown): string {
  return `${Number(value ?? 0).toFixed(1)}%`
}

export function WasteStatsSection({ data }: Props) {
  const trend = useMemo(
    () =>
      data.wasteTrend.map((point) => ({
        label: `#CP-${point.planId}`,
        wasteRatioPercent: point.wasteRatioPercent,
        totalWasteM: point.totalWasteM,
      })),
    [data.wasteTrend],
  )

  const breakdown = useMemo(
    () =>
      data.remainderBreakdown.map((row) => ({
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
        totalM: row.totalM,
      })),
    [data.wasteByGroup],
  )

  return (
    <Row gutter={16} style={{ marginTop: 16 }}>
      <Col span={12}>
        <Card
          size="small"
          title="Xu hướng tỷ lệ phế theo lần chạy"
          extra={<span style={{ fontSize: 12, color: '#8c8c8c' }}>mục tiêu ≤ {TARGET_WASTE_RATIO_PERCENT}%</span>}
        >
          {trend.length === 0 ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Chưa có lần chạy nào" />
          ) : (
            <ResponsiveContainer width="100%" height={240}>
              <LineChart data={trend}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="label" />
                <YAxis unit="%" />
                <RechartsTooltip formatter={formatPercent} />
                <Legend />
                <Line
                  type="monotone"
                  dataKey="wasteRatioPercent"
                  name="Tỷ lệ phế"
                  stroke={WASTE_BAR_COLOR}
                  strokeWidth={2}
                  dot={{ r: 3 }}
                />
              </LineChart>
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
              <PieChart>
                <Pie data={breakdown} dataKey="value" nameKey="name" innerRadius={50} outerRadius={80}>
                  {breakdown.map((row) => (
                    <Cell key={row.key} fill={REMAINDER_COLOR[row.key]} />
                  ))}
                </Pie>
                <Legend />
                <RechartsTooltip formatter={formatMeters} />
              </PieChart>
            </ResponsiveContainer>
          )}
        </Card>
      </Col>

      <Col span={24}>
        <Card size="small" title="Phế liệu theo nhóm thanh nan (bỏ + lãng phí)" style={{ marginTop: 16 }}>
          {byGroup.length === 0 ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Chưa phát sinh phế liệu" />
          ) : (
            <ResponsiveContainer width="100%" height={240}>
              <BarChart data={byGroup}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="name" />
                <YAxis unit="m" />
                <RechartsTooltip formatter={formatMeters} />
                <Bar dataKey="totalM" name="Độ dài phế" fill={WASTE_BAR_COLOR} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </Card>
      </Col>
    </Row>
  )
}
