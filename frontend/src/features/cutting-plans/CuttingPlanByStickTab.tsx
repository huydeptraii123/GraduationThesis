import { Card, Pagination, Space, Table, Tag, Typography } from 'antd'
import { useMemo, useState } from 'react'
import { CuttingBarDiagram } from '../../components/CuttingBarDiagram'
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
  const legend = useMemo(() => {
    const seen = new Map<string, { label: string; color: string }>()
    plan.details.forEach((detail) => {
      detail.items.forEach((item) => {
        const key = String(item.salesOrderId)
        if (!seen.has(key)) {
          seen.set(key, { label: `${item.ycsx}/${item.item} — ${item.customerName}`, color: assignOrderColor(key) })
        }
      })
    })
    return Array.from(seen.values())
  }, [plan, assignOrderColor])

  // Giữ lại chỉ số gốc để nhãn "Phôi #n" vẫn là số thứ tự trong CẢ phương án, không phải trong trang.
  const pageDetails = useMemo(
    () =>
      plan.details
        .map((detail, index) => ({ detail, index }))
        .slice((current - 1) * pageSize, current * pageSize),
    [plan.details, current, pageSize],
  )

  return (
    <div>
      <Card size="small" style={{ marginBottom: 16 }}>
        <Space size={12} wrap>
          <Typography.Text strong>Đơn hàng:</Typography.Text>
          {legend.map((entry) => (
            <Tag key={entry.label} color={entry.color}>
              {entry.label}
            </Tag>
          ))}
          <Typography.Text strong style={{ marginLeft: 16 }}>
            Phần dư:
          </Typography.Text>
          <Tag>Bỏ (&lt;30cm)</Tag>
          <Tag color="success">Nhập kho (&gt;3m)</Tag>
        </Space>
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
            title={`Phôi #${index + 1} - ${detail.slatMaterialName} · ${(detail.sourceLengthMm / 1000).toFixed(2)}m`}
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
          pagination={{ pageSize: 20, showSizeChanger: true, showTotal: (total) => `${total} dòng phôi` }}
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
              render: (_, detail) => `${(detail.sourceLengthMm / 1000).toFixed(2)} m`,
            },
            { title: 'SL phôi', dataIndex: 'stickCount', align: 'right' },
            { title: 'Mã Pattern', dataIndex: 'patternCode' },
            {
              title: 'Phần dư',
              render: (_, detail) =>
                `${(detail.remainderMm / 1000).toFixed(2)} m (${REMAINDER_TYPE_LABEL[detail.remainderType]})`,
            },
          ]}
        />
      </Card>
    </div>
  )
}
