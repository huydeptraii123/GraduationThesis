/**
 * Năm khối số liệu của một lần tính phương án cắt, dựng theo đúng bố cục dashboard doanh nghiệp
 * đang dùng: hai ô chỉ số, cột chồng theo ngày giao, thanh ngang theo model, và vành khuyên tỷ
 * trọng. Bên dưới là bảng vật tư còn thiếu — thứ họ cần để lập kế hoạch sản xuất bù thanh nan.
 *
 * Cả năm khối đọc chung một danh sách bộ cửa gộp ở {@link toDoorSets}, nên tổng của ba biểu đồ
 * luôn bằng ô "Số bộ cửa Open" bên cạnh.
 */

import { Card, Col, Empty, Row, Statistic, Table, Typography } from 'antd'
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
import type { CuttingPlanPreviewResponse } from '../cutting-plans/types'
import { SLAT_GROUP_LABEL } from '../inventory/constants'
import type { SlatGroup } from '../inventory/types'
import {
  byDeliveryDate,
  byModel,
  byStatus,
  shortagesByMaterial,
  toDoorSets,
  wasteRatioPercent,
  type ShortageByMaterial,
} from './demandAggregates'
import { ChartLegend, ChartTooltip } from '../../components/charts/ChartParts'
import { StackTotalLabel, StackedSegmentLabel } from '../../components/charts/chartLabels'
import {
  BAR_SIZE,
  CHART_MARGIN,
  CHART_SURFACE,
  DONUT,
  HORIZONTAL_BAR_SIZE,
  SEGMENT_GAP,
  horizontalChartHeight,
  stackTotalOn,
  type LegendEntry,
} from '../../components/charts/chartTheme'
import { DOOR_SET_STATUSES, DOOR_SET_STATUS_COLOR, type DoorSetStatus } from './simulationSeries'
import { formatMeters } from '../../utils/formatMeters'

interface Props {
  simulation: CuttingPlanPreviewResponse
}

function formatDeliveryDate(iso: string): string {
  return dayjs(iso).format('DD/MM/YYYY')
}

function formatDoorSets(value: number): string {
  return `${value} bộ cửa`
}

const STATUS_LEGEND: LegendEntry[] = DOOR_SET_STATUSES.map((status) => ({
  id: status,
  label: status,
  color: DOOR_SET_STATUS_COLOR[status],
}))

/**
 * Chú giải vành khuyên mang luôn con số và tỷ lệ: `Đủ nan TP 32 (22.22%)` — đúng khuôn chữ của
 * dashboard doanh nghiệp, hai chữ số thập phân.
 */
function buildDonutLegend(slices: { status: DoorSetStatus; count: number }[], total: number): LegendEntry[] {
  return slices.map((slice) => ({
    id: slice.status,
    label: `${slice.status} ${slice.count} (${total > 0 ? ((slice.count / total) * 100).toFixed(2) : '0.00'}%)`,
    color: DOOR_SET_STATUS_COLOR[slice.status],
  }))
}

export function SimulationDashboard({ simulation }: Props) {
  const doorSets = useMemo(() => toDoorSets(simulation.demands), [simulation.demands])
  const byDate = useMemo(() => byDeliveryDate(doorSets, formatDeliveryDate), [doorSets])
  const byModelRows = useMemo(() => byModel(doorSets), [doorSets])
  const statusSlices = useMemo(() => byStatus(doorSets), [doorSets])
  const shortages = useMemo(() => shortagesByMaterial(simulation.demands), [simulation.demands])

  const wasteRatio = wasteRatioPercent(simulation.totalWasteM, simulation.totalStockUsedM)
  const doorSetCount = doorSets.length
  const donutLegend = buildDonutLegend(statusSlices, doorSetCount)
  // Cả hàng dùng chung một chiều cao: chỉ nới riêng biểu đồ theo model thì hai thẻ bên cạnh lùn
  // hơn và hàng lệch mép dưới.
  const rowChartHeight = horizontalChartHeight(byModelRows.length)

  if (doorSetCount === 0) {
    return (
      <Card size="small" style={{ marginTop: 16 }}>
        {/* Cố ý không nói "mọi đơn đã được duyệt": trạng thái này cũng xảy ra khi mọi đơn đang chờ
            đều bị loại vì thiếu định mức — lúc đó cảnh báo ngay phía trên nói điều ngược lại. */}
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="Không có bộ cửa nào sinh được nhu cầu cắt trong lần tính này."
        />
      </Card>
    )
  }

  return (
    <>
      <Row gutter={16} style={{ marginTop: 16 }} align="stretch">
        <Col span={5}>
          <Card size="small" style={{ marginBottom: 16 }}>
            <Statistic title="Số bộ cửa Open" value={doorSetCount} groupSeparator="." suffix="bộ" />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              Toàn bộ đơn chưa thuộc phương án nào được duyệt
            </Typography.Text>
          </Card>
          <Card size="small">
            {/* Hai chữ số thập phân theo khuôn mẫu doanh nghiệp; chưa tiêu hao mét nào thì tỷ lệ
                phế không tồn tại — hiện gạch ngang thay vì 0,00% (đọc nhầm thành "cắt rất sạch"). */}
            <Statistic
              title="Tỷ lệ phế"
              value={wasteRatio ?? '—'}
              precision={wasteRatio == null ? undefined : 2}
              suffix={wasteRatio == null ? '' : '%'}
            />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {simulation.totalWasteM.toFixed(1)} m phế / {simulation.totalStockUsedM.toFixed(1)} m tiêu hao
            </Typography.Text>
          </Card>
        </Col>

        <Col span={10}>
          <Card size="small" title="Số bộ cửa Open theo ngày giao hàng &amp; tình trạng đáp ứng nan">
            <ResponsiveContainer width="100%" height={rowChartHeight}>
              <BarChart data={byDate} margin={CHART_MARGIN}>
                <CartesianGrid vertical={false} />
                {/* Nhiều ngày giao thì thưa bớt nhãn thay vì để chúng chồng lên nhau — mốc đầu và
                    mốc cuối luôn giữ lại để người đọc biết trục trải từ đâu tới đâu. */}
                <XAxis dataKey="label" tickLine={false} interval="preserveStartEnd" minTickGap={24} />
                <YAxis allowDecimals={false} tickLine={false} axisLine={false} />
                <RechartsTooltip content={<ChartTooltip formatValue={formatDoorSets} />} />
                <Legend content={<ChartLegend entries={STATUS_LEGEND} />} />
                {DOOR_SET_STATUSES.map((status, index) => (
                  <Bar
                    key={status}
                    dataKey={status}
                    name={status}
                    stackId="doorSets"
                    fill={DOOR_SET_STATUS_COLOR[status]}
                    maxBarSize={BAR_SIZE}
                    // Khe hở giữa hai đoạn vẽ bằng nét cùng màu nền, không phải viền quanh mark.
                    stroke={CHART_SURFACE}
                    strokeWidth={SEGMENT_GAP}
                  >
                    {/* Đoạn quá mỏng thì nhãn không nằm lọt trong đoạn và hai nhãn liền nhau
                        đè lên nhau — đẩy lệch ngang mỗi chuỗi một hướng, xem chartLabels. */}
                    <LabelList
                      dataKey={status}
                      content={
                        <StackedSegmentLabel
                          thinOffsetX={index === 0 ? -20 : 20}
                          thinColor={DOOR_SET_STATUS_COLOR[status]}
                        />
                      }
                    />
                    <LabelList dataKey={stackTotalOn(DOOR_SET_STATUSES, index)} content={<StackTotalLabel />} />
                  </Bar>
                ))}
              </BarChart>
            </ResponsiveContainer>
          </Card>
        </Col>

        <Col span={5}>
          <Card size="small" title="Số bộ cửa Open theo model">
            <ResponsiveContainer width="100%" height={rowChartHeight}>
              <BarChart data={byModelRows} layout="vertical" margin={{ top: 8, right: 32, left: 8, bottom: 0 }}>
                <CartesianGrid horizontal={false} />
                <XAxis type="number" allowDecimals={false} tickLine={false} axisLine={false} />
                <YAxis
                  type="category"
                  dataKey="label"
                  width={90}
                  // Mặc định Recharts tự bỏ nhãn sắp chồng nhau — chính cách dòng lớn nhất từng
                  // mất tên. Chiều cao nay nới theo số dòng nên mọi nhãn đều có chỗ, bắt hiện hết.
                  interval={0}
                  tickLine={false}
                />
                <RechartsTooltip content={<ChartTooltip formatValue={formatDoorSets} />} />
                <Legend content={<ChartLegend entries={STATUS_LEGEND} />} />
                {DOOR_SET_STATUSES.map((status, index) => (
                  <Bar
                    key={status}
                    dataKey={status}
                    name={status}
                    stackId="doorSets"
                    fill={DOOR_SET_STATUS_COLOR[status]}
                    maxBarSize={HORIZONTAL_BAR_SIZE}
                    stroke={CHART_SURFACE}
                    strokeWidth={SEGMENT_GAP}
                  >
                    {/* Đoạn quá hẹp thì nhãn ra trên/dưới thanh, mỗi chuỗi một phía — xem chartLabels. */}
                    <LabelList
                      dataKey={status}
                      content={
                        <StackedSegmentLabel
                          layout="horizontal"
                          thinSide={index === 0 ? 'above' : 'below'}
                          thinColor={DOOR_SET_STATUS_COLOR[status]}
                        />
                      }
                    />
                    <LabelList
                      dataKey={stackTotalOn(DOOR_SET_STATUSES, index)}
                      content={<StackTotalLabel layout="horizontal" />}
                    />
                  </Bar>
                ))}
              </BarChart>
            </ResponsiveContainer>
          </Card>
        </Col>

        <Col span={4}>
          <Card size="small" title="Tỷ trọng đáp ứng nan">
            <ResponsiveContainer width="100%" height={rowChartHeight}>
              <PieChart>
                <Pie
                  data={statusSlices}
                  dataKey="count"
                  nameKey="status"
                  {...DONUT}
                  // Khe hở giữa hai lát, cùng kỹ thuật với cột chồng.
                  stroke={CHART_SURFACE}
                  strokeWidth={SEGMENT_GAP}
                >
                  {statusSlices.map((slice) => (
                    <Cell key={slice.status} fill={DOOR_SET_STATUS_COLOR[slice.status]} />
                  ))}
                </Pie>
                <RechartsTooltip content={<ChartTooltip formatValue={formatDoorSets} />} />
                {/* Số và tỷ lệ nằm ở chú giải chứ không phải nhãn ngoài vành khuyên: khối này hẹp
                    nhất hàng, nhãn ngoài dài như "Đủ nan TP 32 (22.22%)" bị cắt cụt ở cả hai mép. */}
                <Legend content={<ChartLegend entries={donutLegend} />} />
              </PieChart>
            </ResponsiveContainer>
          </Card>
        </Col>
      </Row>

      <Card size="small" style={{ marginTop: 16 }} title="Cảnh báo vật tư thiếu hụt (gộp theo loại thanh nan)">
        <Table<ShortageByMaterial>
          size="small"
          rowKey="slatMaterialCode"
          pagination={false}
          dataSource={shortages}
          locale={{ emptyText: 'Tồn kho đáp ứng đủ mọi bộ cửa đang chờ' }}
          columns={[
            { title: 'Mã vật tư', dataIndex: 'slatMaterialCode' },
            { title: 'Mô tả vật tư', dataIndex: 'slatMaterialName' },
            {
              title: 'Nhóm',
              dataIndex: 'slatGroup',
              render: (value: SlatGroup) => SLAT_GROUP_LABEL[value] ?? value,
            },
            {
              title: 'SL thanh thiếu',
              dataIndex: 'missingSticks',
              align: 'right',
              render: (value: number) => value.toLocaleString('vi-VN'),
            },
            {
              title: 'Tổng độ dài thiếu',
              dataIndex: 'missingLengthMm',
              align: 'right',
              // Đúng tới milimet như file Excel và màn báo thiếu vật tư — doanh nghiệp lập kế hoạch
              // sản xuất bù theo chính con số này.
              render: (value: number) => `${formatMeters(value)} m`,
            },
            { title: 'Số bộ cửa ảnh hưởng', dataIndex: 'affectedDoorSetCount', align: 'right' },
          ]}
        />
      </Card>
    </>
  )
}

