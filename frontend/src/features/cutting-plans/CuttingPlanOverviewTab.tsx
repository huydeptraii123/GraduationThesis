import { Card, Col, Row, Statistic, Table, Tag, Typography } from 'antd'
import dayjs from 'dayjs'
import { useMemo } from 'react'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  LabelList,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip as RechartsTooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { ChartLegend, ChartTooltip } from '../../components/charts/ChartParts'
import { StackTotalLabel, StackedSegmentLabel } from '../../components/charts/chartLabels'
import {
  BAR_SIZE,
  CHART_HEIGHT,
  CHART_MARGIN,
  CHART_SURFACE,
  DONUT,
  HORIZONTAL_BAR_SIZE,
  KPI_TONE,
  SEGMENT_GAP,
  SUFFICIENCY_COLOR,
  horizontalChartHeight,
  stackTotalOn,
  type LegendEntry,
} from '../../components/charts/chartTheme'
import { localPagination } from '../../api/pagination'
import { formatMeters } from '../../utils/formatMeters'
import type { CuttingBatch, CuttingBatchOrderRow } from './cuttingBatches'
import { shortageTotalMm } from './shortageLengths'
import type { CuttingPlanResponse } from './types'

interface Props {
  plan: CuttingPlanResponse
  orderRows: CuttingBatchOrderRow[]
  batches: CuttingBatch[]
}

// Ngưỡng hiển thị tham khảo cho PLANNER, không phải ràng buộc hệ thống kiểm tra hay chặn.
const TARGET_WASTE_RATIO_PERCENT = 5

const SUFFICIENT_LABEL = 'Đủ vật tư'
const SHORTAGE_LABEL = 'Thiếu vật tư'
/** Thứ tự xếp chồng của hai chuỗi — đủ ở chân cột, thiếu ở ngoài. */
const STACK_KEYS = ['sufficient', 'shortage'] as const

const STATUS_LEGEND: LegendEntry[] = [
  { id: 'sufficient', label: SUFFICIENT_LABEL, color: SUFFICIENCY_COLOR.sufficient },
  { id: 'shortage', label: SHORTAGE_LABEL, color: SUFFICIENCY_COLOR.short },
]

/** Bề rộng cột tên mẫu cửa; tên dài hơn thì cắt bớt, tên đầy đủ nằm ở tooltip. */
const DOOR_PRODUCT_AXIS_WIDTH = 220
const DOOR_PRODUCT_LABEL_MAX = 34
/** Nhiều mẫu cửa thì cuộn trong khung thay vì kéo dài cả trang. */
const DOOR_PRODUCT_MAX_HEIGHT = 360

function formatDoorSets(value: number): string {
  return `${value} bộ cửa`
}

function shortenLabel(label: string): string {
  return label.length > DOOR_PRODUCT_LABEL_MAX ? `${label.slice(0, DOOR_PRODUCT_LABEL_MAX - 1)}…` : label
}

interface SufficiencyBucket {
  label: string
  sufficient: number
  shortage: number
  /** Tổng phải tính sẵn: LabelList chỉ đọc được trường có thật trong dữ liệu. */
  total: number
}

/** Đếm đủ/thiếu theo một khóa gộp, giữ thứ tự xuất hiện đầu tiên. */
function countBySufficiency(
  rows: CuttingBatchOrderRow[],
  keyOf: (row: CuttingBatchOrderRow) => string,
): SufficiencyBucket[] {
  const buckets = new Map<string, SufficiencyBucket>()
  rows.forEach((row) => {
    const key = keyOf(row)
    const bucket = buckets.get(key) ?? { label: key, sufficient: 0, shortage: 0, total: 0 }
    if (row.hasShortage) {
      bucket.shortage += 1
    } else {
      bucket.sufficient += 1
    }
    bucket.total += 1
    buckets.set(key, bucket)
  })
  return Array.from(buckets.values())
}

export function CuttingPlanOverviewTab({ plan, orderRows, batches }: Props) {
  const wasteRatioPercent = plan.totalStockUsedM > 0 ? (plan.totalWasteM / plan.totalStockUsedM) * 100 : 0
  const sufficientCount = orderRows.filter((row) => !row.hasShortage).length
  const shortageCount = orderRows.filter((row) => row.hasShortage).length

  const byDeliveryDate = useMemo(
    () =>
      countBySufficiency(
        [...orderRows].sort((a, b) => a.reqdDeliveryDate.localeCompare(b.reqdDeliveryDate)),
        (row) => dayjs(row.reqdDeliveryDate).format('DD/MM/YYYY'),
      ),
    [orderRows],
  )

  const byDoorProduct = useMemo(
    () => countBySufficiency(orderRows, (row) => row.doorProductName).sort((a, b) => b.total - a.total),
    [orderRows],
  )

  // Cùng khuôn chữ với vành khuyên trang chủ: tên, số bộ, tỷ lệ hai chữ số thập phân.
  const donutLegend: LegendEntry[] = [
    { ...STATUS_LEGEND[0], count: sufficientCount },
    { ...STATUS_LEGEND[1], count: shortageCount },
  ]
    .filter((entry) => entry.count > 0)
    .map(({ count, ...entry }) => ({
      ...entry,
      label: `${entry.label} ${count} (${orderRows.length > 0 ? ((count / orderRows.length) * 100).toFixed(2) : '0.00'}%)`,
    }))
  const donutSlices = [
    { name: SUFFICIENT_LABEL, value: sufficientCount, color: SUFFICIENCY_COLOR.sufficient },
    { name: SHORTAGE_LABEL, value: shortageCount, color: SUFFICIENCY_COLOR.short },
  ].filter((slice) => slice.value > 0)

  const shortagesByMaterial = useMemo(() => {
    const groups = new Map<
      number,
      { slatMaterialName: string; missingQuantity: number; missingLengthMm: number; affectedOrders: Set<string> }
    >()
    plan.shortages.forEach((shortage) => {
      const group = groups.get(shortage.slatMaterialId) ?? {
        slatMaterialName: shortage.slatMaterialName,
        missingQuantity: 0,
        missingLengthMm: 0,
        affectedOrders: new Set<string>(),
      }
      group.missingQuantity += shortage.missingQuantity
      group.missingLengthMm += shortageTotalMm(shortage)
      group.affectedOrders.add(`${shortage.ycsx}/${shortage.item}`)
      groups.set(shortage.slatMaterialId, group)
    })
    return Array.from(groups.entries()).map(([slatMaterialId, group]) => ({ slatMaterialId, ...group }))
  }, [plan.shortages])

  return (
    <div>
      <Row gutter={16}>
        <Col span={6}>
          <Card size="small">
            <Statistic title="Tổng số bộ cửa" value={orderRows.length} suffix="bộ" />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic
              title={`Tỷ lệ phế trung bình (mục tiêu ≤${TARGET_WASTE_RATIO_PERCENT}%)`}
              value={wasteRatioPercent}
              precision={1}
              suffix="%"
              styles={{
                content: { color: wasteRatioPercent > TARGET_WASTE_RATIO_PERCENT ? KPI_TONE.bad : KPI_TONE.good },
              }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic
              title={SUFFICIENT_LABEL}
              value={sufficientCount}
              suffix={`/ ${orderRows.length}`}
              styles={{ content: { color: SUFFICIENCY_COLOR.sufficient } }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic
              title={SHORTAGE_LABEL}
              value={shortageCount}
              suffix={`/ ${orderRows.length}`}
              styles={{ content: { color: SUFFICIENCY_COLOR.short } }}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={16} style={{ marginTop: 16 }}>
        <Col span={14}>
          <Card size="small" title="Số bộ cửa theo ngày giao yêu cầu">
            <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
              <BarChart data={byDeliveryDate} margin={CHART_MARGIN}>
                <CartesianGrid vertical={false} />
                <XAxis dataKey="label" tickLine={false} interval="preserveStartEnd" minTickGap={24} />
                <YAxis allowDecimals={false} tickLine={false} axisLine={false} />
                <RechartsTooltip content={<ChartTooltip formatValue={formatDoorSets} />} />
                <Legend content={<ChartLegend entries={STATUS_LEGEND} />} />
                <Bar
                  dataKey="sufficient"
                  name={SUFFICIENT_LABEL}
                  stackId="orders"
                  fill={SUFFICIENCY_COLOR.sufficient}
                  maxBarSize={BAR_SIZE}
                  stroke={CHART_SURFACE}
                  strokeWidth={SEGMENT_GAP}
                >
                  <LabelList
                    dataKey="sufficient"
                    content={<StackedSegmentLabel thinOffsetX={-20} thinColor={SUFFICIENCY_COLOR.sufficient} />}
                  />
                  <LabelList dataKey={stackTotalOn(STACK_KEYS, 0)} content={<StackTotalLabel />} />
                </Bar>
                <Bar
                  dataKey="shortage"
                  name={SHORTAGE_LABEL}
                  stackId="orders"
                  fill={SUFFICIENCY_COLOR.short}
                  maxBarSize={BAR_SIZE}
                  stroke={CHART_SURFACE}
                  strokeWidth={SEGMENT_GAP}
                >
                  <LabelList
                    dataKey="shortage"
                    content={<StackedSegmentLabel thinOffsetX={20} thinColor={SUFFICIENCY_COLOR.short} />}
                  />
                  <LabelList dataKey={stackTotalOn(STACK_KEYS, 1)} content={<StackTotalLabel />} />
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </Card>
        </Col>
        <Col span={10}>
          <Card size="small" title="Tỷ trọng đủ/thiếu vật tư">
            <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
              <PieChart>
                <Pie
                  data={donutSlices}
                  dataKey="value"
                  nameKey="name"
                  {...DONUT}
                  stroke={CHART_SURFACE}
                  strokeWidth={SEGMENT_GAP}
                >
                  {donutSlices.map((slice) => (
                    <Cell key={slice.name} fill={slice.color} />
                  ))}
                </Pie>
                <RechartsTooltip content={<ChartTooltip formatValue={formatDoorSets} />} />
                <Legend content={<ChartLegend entries={donutLegend} />} />
              </PieChart>
            </ResponsiveContainer>
          </Card>
        </Col>
      </Row>

      <Card size="small" title="Số bộ cửa theo mẫu cửa" style={{ marginTop: 16 }}>
        {/* Cùng kiểu với "Số bộ cửa Open theo model" ở trang chủ. Cuộn trong khung thay vì kéo dài
            trang: một lần chạy có thể gồm hàng chục mẫu cửa; chú giải nằm ngoài khung cuộn để
            không trôi mất theo. */}
        <div style={{ maxHeight: DOOR_PRODUCT_MAX_HEIGHT, overflowY: 'auto' }}>
          <ResponsiveContainer width="100%" height={horizontalChartHeight(byDoorProduct.length)}>
            <BarChart data={byDoorProduct} layout="vertical" margin={{ top: 8, right: 32, left: 8, bottom: 0 }}>
              <CartesianGrid horizontal={false} />
              <XAxis type="number" allowDecimals={false} tickLine={false} axisLine={false} />
              <YAxis
                type="category"
                dataKey="label"
                width={DOOR_PRODUCT_AXIS_WIDTH}
                interval={0}
                tickLine={false}
                tickFormatter={shortenLabel}
              />
              <RechartsTooltip content={<ChartTooltip formatValue={formatDoorSets} />} />
              <Bar
                dataKey="sufficient"
                name={SUFFICIENT_LABEL}
                stackId="doorProducts"
                fill={SUFFICIENCY_COLOR.sufficient}
                maxBarSize={HORIZONTAL_BAR_SIZE}
                stroke={CHART_SURFACE}
                strokeWidth={SEGMENT_GAP}
              >
                <LabelList
                  dataKey="sufficient"
                  content={
                    <StackedSegmentLabel
                      layout="horizontal"
                      thinSide="above"
                      thinColor={SUFFICIENCY_COLOR.sufficient}
                    />
                  }
                />
                <LabelList dataKey={stackTotalOn(STACK_KEYS, 0)} content={<StackTotalLabel layout="horizontal" />} />
              </Bar>
              <Bar
                dataKey="shortage"
                name={SHORTAGE_LABEL}
                stackId="doorProducts"
                fill={SUFFICIENCY_COLOR.short}
                maxBarSize={HORIZONTAL_BAR_SIZE}
                stroke={CHART_SURFACE}
                strokeWidth={SEGMENT_GAP}
              >
                <LabelList
                  dataKey="shortage"
                  content={
                    <StackedSegmentLabel layout="horizontal" thinSide="below" thinColor={SUFFICIENCY_COLOR.short} />
                  }
                />
                <LabelList dataKey={stackTotalOn(STACK_KEYS, 1)} content={<StackTotalLabel layout="horizontal" />} />
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
        <ChartLegend entries={STATUS_LEGEND} />
      </Card>

      {shortagesByMaterial.length > 0 && (
        <Card size="small" title="Cảnh báo: Vật tư thiếu hụt" style={{ marginTop: 16 }}>
          <Table
            size="small"
            rowKey="slatMaterialId"
            pagination={localPagination((total) => `${total} loại vật tư thiếu`)}
            dataSource={shortagesByMaterial}
            columns={[
              { title: 'Loại thanh nan', dataIndex: 'slatMaterialName' },
              { title: 'SL đoạn thiếu', dataIndex: 'missingQuantity', align: 'right' },
              {
                title: 'Tổng độ dài thiếu',
                dataIndex: 'missingLengthMm',
                align: 'right',
                render: (value: number) => `${formatMeters(value)} m`,
              },
              {
                title: 'Đơn hàng liên quan',
                render: (_, row) => Array.from(row.affectedOrders).join(', '),
              },
            ]}
          />
        </Card>
      )}

      <Card size="small" title="Danh sách đợt cắt" style={{ marginTop: 16 }}>
        <Typography.Text type="secondary" style={{ display: 'block', marginBottom: 8 }}>
          Nhóm theo Mẫu cửa + Màu, tối đa ≤7 bộ cửa/đợt, sắp xếp theo ngày giao sớm nhất.
        </Typography.Text>
        <Table
          size="small"
          rowKey="batchNumber"
          pagination={localPagination((total) => `${total} đợt cắt`)}
          dataSource={batches}
          columns={[
            { title: 'Đợt cắt', dataIndex: 'batchNumber', render: (value: number) => `Đợt ${value}` },
            { title: 'Mẫu cửa & Màu', dataIndex: 'doorProductName' },
            { title: 'Số bộ cửa/đợt', render: (_, batch) => batch.orders.length },
            {
              title: 'Ngày giao sớm nhất',
              dataIndex: 'earliestDeliveryDate',
              render: (value: string) => dayjs(value).format('DD/MM/YYYY'),
            },
          ]}
          expandable={{
            expandedRowRender: (batch) => (
              <Table
                size="small"
                rowKey="salesOrderId"
                pagination={false}
                dataSource={batch.orders}
                columns={[
                  { title: 'Mã SX & Bộ', render: (_, order) => `${order.ycsx}/${order.item}` },
                  { title: 'Khách hàng', dataIndex: 'customerName' },
                  {
                    title: 'Kích thước',
                    render: (_, order) => `${formatMeters(order.chieuCaoDh * 1000)} × ${formatMeters(order.chieuRongDh * 1000)} m`,
                  },
                  {
                    title: 'Hạn giao hàng',
                    dataIndex: 'reqdDeliveryDate',
                    render: (value: string) => dayjs(value).format('DD/MM/YYYY'),
                  },
                  {
                    title: 'Trạng thái',
                    render: (_, order) =>
                      order.hasShortage ? <Tag color="error">Thiếu vật tư</Tag> : <Tag color="success">Đủ vật tư</Tag>,
                  },
                ]}
              />
            ),
          }}
        />
      </Card>
    </div>
  )
}
