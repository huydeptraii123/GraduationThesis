import { Card, Space, Table, Tag, Typography } from 'antd'
import { useMemo } from 'react'
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

export function CuttingPlanByStickTab({ plan, assignOrderColor }: Props) {
  // Gọi assignOrderColor theo đúng thứ tự xuất hiện trước khi các card bên dưới gọi lại (idempotent,
  // cùng 1 map dùng chung) để chú thích màu và các thanh luôn khớp nhau.
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
          <Tag color="warning">Lãng phí (30cm–3m)</Tag>
          <Tag color="success">Nhập kho (&gt;3m)</Tag>
        </Space>
      </Card>

      <Space orientation="vertical" size={16} style={{ width: '100%' }}>
        {plan.details.map((detail, index) => (
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

      <Card size="small" title="Bảng tổng hợp phôi đã sử dụng" style={{ marginTop: 16 }}>
        <Table
          size="small"
          rowKey="id"
          pagination={false}
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
