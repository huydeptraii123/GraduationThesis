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
import { StackedSegmentLabel } from '../../components/chartLabels'
import { CHART_SURFACE, DOOR_SET_STATUSES, DOOR_SET_STATUS_COLOR, type DoorSetStatus } from './simulationSeries'

interface Props {
  simulation: CuttingPlanPreviewResponse
}

const CHART_HEIGHT = 300

/** Cột mảnh hơn ô của nó — phần trống còn lại là khoảng thở, không phải chỗ để nới cột ra. */
const MAX_BAR_SIZE = 24

/** Khe hở vẽ bằng màu nền để hai đoạn của cột chồng tách nhau mà không cần viền quanh mark. */
const SEGMENT_GAP = 2

const AXIS_TICK = { fontSize: 12, fill: '#595959' }
const GRID_COLOR = '#f0f0f0'

function formatDeliveryDate(iso: string): string {
  return dayjs(iso).format('DD/MM/YYYY')
}

function formatDoorSets(value: unknown): string {
  return `${Number(value ?? 0)} bộ cửa`
}

/**
 * Chú giải dựng tay thay vì để Recharts tự suy từ các `Bar`: thứ tự tự suy chạy ngược chiều xếp
 * chồng, và một trạng thái vắng mặt trong dữ liệu sẽ biến mất khỏi chú giải — người đọc mất hẳn
 * lời giải nghĩa của màu còn lại.
 */
interface LegendEntry {
  id: string
  label: string
  color: string
}

const STATUS_LEGEND: LegendEntry[] = DOOR_SET_STATUSES.map((status) => ({
  id: status,
  label: status,
  color: DOOR_SET_STATUS_COLOR[status],
}))

/**
 * Chữ của chú giải mặc trang phục của chữ (ghi màu mực phụ), chỉ ô vuông nhỏ bên cạnh mang màu của
 * chuỗi số liệu — tô màu chuỗi lên chính dòng chữ làm nó khó đọc và khiến màu thành dấu hiệu duy
 * nhất phân biệt hai trạng thái.
 */
function StatusLegend({ entries }: { entries: LegendEntry[] }) {
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'center',
        flexWrap: 'wrap',
        gap: 16,
        paddingTop: 4,
        fontSize: 12,
        color: '#595959',
      }}
    >
      {entries.map((entry) => (
        <span key={entry.id} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
          <span
            aria-hidden
            style={{ width: 10, height: 10, borderRadius: 2, background: entry.color, display: 'inline-block' }}
          />
          {entry.label}
        </span>
      ))}
    </div>
  )
}

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

/** Nhãn số bên trong đoạn cột — ẩn khi đoạn bằng 0 để không in số 0 chồng lên đường trục. */
function renderSegmentLabel(value: unknown): string {
  const count = Number(value ?? 0)
  return count > 0 ? String(count) : ''
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
            <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
              <BarChart data={byDate} margin={{ top: 24, right: 8, left: 0, bottom: 0 }}>
                <CartesianGrid vertical={false} stroke={GRID_COLOR} />
                {/* Nhiều ngày giao thì thưa bớt nhãn thay vì để chúng chồng lên nhau — mốc đầu và
                    mốc cuối luôn giữ lại để người đọc biết trục trải từ đâu tới đâu. */}
                <XAxis
                  dataKey="label"
                  tick={AXIS_TICK}
                  tickLine={false}
                  axisLine={{ stroke: GRID_COLOR }}
                  interval="preserveStartEnd"
                  minTickGap={24}
                />
                <YAxis allowDecimals={false} tick={AXIS_TICK} tickLine={false} axisLine={false} />
                <RechartsTooltip formatter={formatDoorSets} />
                <Legend content={<StatusLegend entries={STATUS_LEGEND} />} />
                {DOOR_SET_STATUSES.map((status, index) => (
                  <Bar
                    key={status}
                    dataKey={status}
                    name={status}
                    stackId="doorSets"
                    fill={DOOR_SET_STATUS_COLOR[status]}
                    maxBarSize={MAX_BAR_SIZE}
                    // Khe hở giữa hai đoạn vẽ bằng nét cùng màu nền, không phải viền quanh mark.
                    stroke={CHART_SURFACE}
                    strokeWidth={SEGMENT_GAP}
                    // Bo 4px ở đầu tự do của cột, vuông ở chân trục — chỉ đoạn trên cùng có đầu tự do.
                    radius={index === DOOR_SET_STATUSES.length - 1 ? [4, 4, 0, 0] : undefined}
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
                    {index === DOOR_SET_STATUSES.length - 1 && (
                      <LabelList dataKey="total" position="top" fill="#595959" fontSize={12} />
                    )}
                  </Bar>
                ))}
              </BarChart>
            </ResponsiveContainer>
          </Card>
        </Col>

        <Col span={5}>
          <Card size="small" title="Số bộ cửa Open theo model">
            <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
              <BarChart data={byModelRows} layout="vertical" margin={{ top: 8, right: 32, left: 8, bottom: 0 }}>
                <CartesianGrid horizontal={false} stroke={GRID_COLOR} />
                <XAxis type="number" allowDecimals={false} tick={AXIS_TICK} tickLine={false} axisLine={false} />
                <YAxis
                  type="category"
                  dataKey="label"
                  width={90}
                  tick={AXIS_TICK}
                  tickLine={false}
                  axisLine={{ stroke: GRID_COLOR }}
                />
                <RechartsTooltip formatter={formatDoorSets} />
                <Legend content={<StatusLegend entries={STATUS_LEGEND} />} />
                {DOOR_SET_STATUSES.map((status, index) => (
                  <Bar
                    key={status}
                    dataKey={status}
                    name={status}
                    stackId="doorSets"
                    fill={DOOR_SET_STATUS_COLOR[status]}
                    maxBarSize={MAX_BAR_SIZE}
                    stroke={CHART_SURFACE}
                    strokeWidth={SEGMENT_GAP}
                    radius={index === DOOR_SET_STATUSES.length - 1 ? [0, 4, 4, 0] : undefined}
                  >
                    <LabelList
                      dataKey={status}
                      position="center"
                      fill="#ffffff"
                      fontSize={12}
                      formatter={renderSegmentLabel}
                    />
                    {index === DOOR_SET_STATUSES.length - 1 && (
                      <LabelList dataKey="total" position="right" fill="#595959" fontSize={12} />
                    )}
                  </Bar>
                ))}
              </BarChart>
            </ResponsiveContainer>
          </Card>
        </Col>

        <Col span={4}>
          <Card size="small" title="Tỷ trọng đáp ứng nan">
            <ResponsiveContainer width="100%" height={CHART_HEIGHT}>
              <PieChart>
                <Pie
                  data={statusSlices}
                  dataKey="count"
                  nameKey="status"
                  innerRadius={55}
                  outerRadius={85}
                  // Khe hở giữa hai lát, cùng kỹ thuật với cột chồng.
                  stroke={CHART_SURFACE}
                  strokeWidth={SEGMENT_GAP}
                >
                  {statusSlices.map((slice) => (
                    <Cell key={slice.status} fill={DOOR_SET_STATUS_COLOR[slice.status]} />
                  ))}
                </Pie>
                <RechartsTooltip formatter={formatDoorSets} />
                {/* Số và tỷ lệ nằm ở chú giải chứ không phải nhãn ngoài vành khuyên: khối này hẹp
                    nhất hàng, nhãn ngoài dài như "Đủ nan TP 32 (22.22%)" bị cắt cụt ở cả hai mép. */}
                <Legend content={<StatusLegend entries={donutLegend} />} />
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
              dataIndex: 'missingLengthM',
              align: 'right',
              render: (value: number) => `${value.toFixed(1)} m`,
            },
            { title: 'Số bộ cửa ảnh hưởng', dataIndex: 'affectedDoorSetCount', align: 'right' },
          ]}
        />
      </Card>
    </>
  )
}

