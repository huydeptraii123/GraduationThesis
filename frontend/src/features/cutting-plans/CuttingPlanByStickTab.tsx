import { Card, Pagination, Space, Table, Tag } from 'antd'
import { useMemo, useState } from 'react'
import { CuttingBarDiagram } from '../../components/CuttingBarDiagram'
import { ChartLegend } from '../../components/charts/ChartParts'
import { REMAINDER_COLOR, type LegendEntry } from '../../components/charts/chartTheme'
import { localPagination } from '../../api/pagination'
import { formatMeters } from '../../utils/formatMeters'
import { expandDetailToPieces } from './expandDetailToPieces'
import { REMAINDER_TYPE_LABEL } from './remainderLabels'
import type { CuttingPlanDetailResponse, CuttingPlanResponse } from './types'

interface Props {
  plan: CuttingPlanResponse
  assignOrderColor: (orderKey: string) => string
}

function efficiencyPercent(detail: CuttingPlanDetailResponse): number {
  return ((detail.sourceLengthMm - detail.remainderMm) / detail.sourceLengthMm) * 100
}

function StatusChip({ detail }: { detail: CuttingPlanDetailResponse }) {
  const efficiency = efficiencyPercent(detail).toFixed(0)
  if (detail.remainderType === 'RESTOCK') {
    return <Tag color="success">Tái sinh tồn kho ({efficiency}%)</Tag>
  }
  if (detail.remainderType === 'WASTE') {
    // "Cao" nói về phần dư, không phải hiệu suất — dùng đúng tỉ lệ phần dư, không phải phần đã dùng.
    const wastePercent = (100 - Number(efficiency)).toFixed(0)
    return <Tag color="warning">Phần dư cao ({wastePercent}%)</Tag>
  }
  return <Tag>Hiệu suất {efficiency}%</Tag>
}

/** Chỉ hai loại phần dư thuật toán sinh ra; loại 30cm–3m không bao giờ xuất hiện (xem WasteStatsSection). */
const REMAINDER_LEGEND: LegendEntry[] = [
  { id: 'DISCARDED', label: 'Bỏ (<30cm)', color: REMAINDER_COLOR.DISCARDED },
  { id: 'RESTOCK', label: 'Nhập kho (>3m)', color: REMAINDER_COLOR.RESTOCK },
]

/** Số thẻ phôi vẽ mỗi trang. Mỗi thẻ là một SVG, nên số này là thứ quyết định trang có mượt không. */
const STICKS_PER_PAGE = 10

export function CuttingPlanByStickTab({ plan, assignOrderColor }: Props) {
  const [current, setCurrent] = useState(1)
  const [pageSize, setPageSize] = useState(STICKS_PER_PAGE)

  // Gọi assignOrderColor theo đúng thứ tự xuất hiện trước khi các card bên dưới gọi lại (idempotent,
  // cùng 1 map dùng chung) để chú thích màu và các thanh luôn khớp nhau.
  //
  // PHẢI duyệt TOÀN BỘ plan.details, không phải chỉ trang đang xem: màu gán theo thứ tự đơn hàng
  // xuất hiện lần đầu, nên nếu để các thẻ ở trang sau tự gán thì mở thẳng trang 3 sẽ ra bảng màu
  // khác với khi lật từ trang 1 — cùng một đơn hàng đổi màu theo đường người dùng đi tới.
  const legendByOrder = useMemo(() => {
    const seen = new Map<string, { label: string; color: string }>()
    plan.details.forEach((detail) => {
      detail.items.forEach((item) => {
        const key = String(item.salesOrderId)
        if (!seen.has(key)) {
          seen.set(key, { label: `${item.ycsx}/${item.item} — ${item.customerName}`, color: assignOrderColor(key) })
        }
      })
    })
    return seen
  }, [plan, assignOrderColor])

  // Giữ lại chỉ số gốc để nhãn "Phôi #n" vẫn là số thứ tự trong CẢ phương án, không phải trong trang.
  const pageDetails = useMemo(
    () =>
      plan.details
        .map((detail, index) => ({ detail, index }))
        .slice((current - 1) * pageSize, current * pageSize),
    [plan.details, current, pageSize],
  )

  // Màu thì gán trên cả phương án (xem trên), nhưng chú giải chỉ HIỆN các đơn có mặt ở trang này:
  // một đợt duyệt có tới ~70 đơn, liệt kê hết thì khối chú giải chiếm trọn màn đầu và người xem
  // phải cuộn qua nó mới tới được sơ đồ đầu tiên.
  const legend = useMemo(() => {
    const onPage = new Set(pageDetails.flatMap(({ detail }) => detail.items.map((item) => String(item.salesOrderId))))
    return Array.from(legendByOrder.entries())
      .filter(([key]) => onPage.has(key))
      .map(([, entry]) => entry)
  }, [legendByOrder, pageDetails])

  return (
    <div>
      {/* Chú giải chung kiểu với mọi biểu đồ khác: ô màu + chữ, màu phần dư lấy đúng màu sơ đồ vẽ. */}
      <Card size="small" style={{ marginBottom: 16 }}>
        <ChartLegend
          align="start"
          title="Đơn hàng trên trang này:"
          entries={legend.map((entry) => ({ id: entry.label, label: entry.label, color: entry.color }))}
        />
        <ChartLegend align="start" title="Phần dư:" entries={REMAINDER_LEGEND} />
      </Card>

      <Pagination
        style={{ marginBottom: 16, textAlign: 'right' }}
        current={current}
        pageSize={pageSize}
        total={plan.details.length}
        showSizeChanger
        pageSizeOptions={['5', '10', '20', '50']}
        showTotal={(total) => `${total} phôi`}
        onChange={(nextCurrent, nextPageSize) => {
          setCurrent(nextCurrent)
          setPageSize(nextPageSize)
        }}
      />

      <Space orientation="vertical" size={16} style={{ width: '100%' }}>
        {pageDetails.map(({ detail, index }) => (
          <Card
            key={detail.id}
            size="small"
            title={`Phôi #${index + 1} - ${detail.slatMaterialName} · ${formatMeters(detail.sourceLengthMm)}m`}
            extra={
              <Space>
                <span>SL: {detail.stickCount} thanh</span>
                <span>Mã: {detail.patternCode}</span>
                <StatusChip detail={detail} />
              </Space>
            }
          >
            <CuttingBarDiagram
              sourceLengthMm={detail.sourceLengthMm}
              pieces={expandDetailToPieces(detail, assignOrderColor)}
              remainderMm={detail.remainderMm}
              remainderType={detail.remainderType}
            />
          </Card>
        ))}
      </Space>

      <Pagination
        style={{ marginTop: 16, textAlign: 'right' }}
        current={current}
        pageSize={pageSize}
        total={plan.details.length}
        showSizeChanger
        pageSizeOptions={['5', '10', '20', '50']}
        showTotal={(total) => `${total} phôi`}
        onChange={(nextCurrent, nextPageSize) => {
          setCurrent(nextCurrent)
          setPageSize(nextPageSize)
        }}
      />

      <Card size="small" title="Bảng tổng hợp phôi đã sử dụng" style={{ marginTop: 16 }}>
        <Table
          size="small"
          rowKey="id"
          pagination={localPagination((total) => `${total} dòng phôi`)}
          dataSource={plan.details}
          columns={[
            {
              title: 'Lệnh SX đơn gốc',
              render: (_, detail) => {
                const original = detail.items.find((item) => item.originalOrder) ?? detail.items[0]
                return original ? `${original.ycsx}/${original.item}` : '—'
              },
            },
            { title: 'Vật tư', dataIndex: 'slatMaterialName' },
            {
              title: 'Độ dài phôi',
              render: (_, detail) => `${formatMeters(detail.sourceLengthMm)} m`,
            },
            { title: 'SL phôi', dataIndex: 'stickCount', align: 'right' },
            { title: 'Mã Pattern', dataIndex: 'patternCode' },
            {
              title: 'Phần dư',
              render: (_, detail) =>
                `${formatMeters(detail.remainderMm)} m (${REMAINDER_TYPE_LABEL[detail.remainderType]})`,
            },
          ]}
        />
      </Card>
    </div>
  )
}
