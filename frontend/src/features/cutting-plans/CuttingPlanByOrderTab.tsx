import { SearchOutlined } from '@ant-design/icons'
import { Button, Card, Col, Input, Row, Select, Space, Statistic, Table, Tag } from 'antd'
import { useMemo, useState } from 'react'
import { REMAINDER_TYPE_LABEL } from './remainderLabels'
import type { CuttingPlanResponse } from './types'

interface Props {
  plan: CuttingPlanResponse
}

interface DemandRow {
  key: string
  ycsx: string
  item: number
  customerName: string
  doorProductName: string
  slatMaterialName: string
  cutLengthMm: number
  quantityNeeded: number
  quantityMissing: number
  sufficient: boolean
  cutDescription: string | null
}

/** 1 dòng = 1 nhu cầu cắt (đúng docs/requirements-functional.md dòng 13) — gộp phẳng items (đủ vật tư) và shortages (thiếu). */
function buildRows(plan: CuttingPlanResponse): DemandRow[] {
  const rows: DemandRow[] = []

  plan.details.forEach((detail) => {
    const cutDescription = `Phôi ${(detail.sourceLengthMm / 1000).toFixed(2)}m → dư ${(detail.remainderMm / 1000).toFixed(2)}m (${REMAINDER_TYPE_LABEL[detail.remainderType]})`
    detail.items.forEach((item) => {
      rows.push({
        key: `item-${item.id}`,
        ycsx: item.ycsx,
        item: item.item,
        customerName: item.customerName,
        doorProductName: item.doorProductName,
        slatMaterialName: detail.slatMaterialName,
        cutLengthMm: item.cutLengthMm,
        quantityNeeded: item.cutQuantity,
        quantityMissing: 0,
        sufficient: true,
        cutDescription,
      })
    })
  })

  plan.shortages.forEach((shortage) => {
    rows.push({
      key: `shortage-${shortage.id}`,
      ycsx: shortage.ycsx,
      item: shortage.item,
      customerName: shortage.customerName,
      doorProductName: shortage.doorProductName,
      slatMaterialName: shortage.slatMaterialName,
      cutLengthMm:
        shortage.missingQuantity > 0 ? Math.round((shortage.missingLengthM * 1000) / shortage.missingQuantity) : 0,
      quantityNeeded: shortage.missingQuantity,
      quantityMissing: shortage.missingQuantity,
      sufficient: false,
      cutDescription: null,
    })
  })

  return rows
}

export function CuttingPlanByOrderTab({ plan }: Props) {
  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState<'SUFFICIENT' | 'SHORTAGE' | null>(null)
  const [doorProductFilter, setDoorProductFilter] = useState<string | null>(null)

  const allRows = useMemo(() => buildRows(plan), [plan])

  const doorProductOptions = useMemo(
    () => Array.from(new Set(allRows.map((row) => row.doorProductName))).map((name) => ({ value: name, label: name })),
    [allRows],
  )

  const filtered = useMemo(() => {
    const needle = keyword.trim().toLowerCase()
    return allRows.filter((row) => {
      if (statusFilter === 'SUFFICIENT' && !row.sufficient) {
        return false
      }
      if (statusFilter === 'SHORTAGE' && row.sufficient) {
        return false
      }
      if (doorProductFilter && row.doorProductName !== doorProductFilter) {
        return false
      }
      if (!needle) {
        return true
      }
      return (
        row.ycsx.toLowerCase().includes(needle) ||
        row.customerName.toLowerCase().includes(needle) ||
        row.slatMaterialName.toLowerCase().includes(needle)
      )
    })
  }, [allRows, keyword, statusFilter, doorProductFilter])

  const sufficientCount = allRows.filter((row) => row.sufficient).length
  const shortageCount = allRows.length - sufficientCount

  function resetFilters() {
    setKeyword('')
    setStatusFilter(null)
    setDoorProductFilter(null)
  }

  return (
    <div>
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={8}>
          <Card size="small">
            <Statistic title="Tổng nhu cầu cắt" value={allRows.length} />
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small">
            <Statistic title="Đáp ứng đủ" value={sufficientCount} styles={{ content: { color: '#3f8600' } }} />
          </Card>
        </Col>
        <Col span={8}>
          <Card size="small">
            <Statistic title="Thiếu hụt" value={shortageCount} styles={{ content: { color: '#cf1322' } }} />
          </Card>
        </Col>
      </Row>

      <Space style={{ marginBottom: 16 }} wrap>
        <Input
          allowClear
          placeholder="Tìm theo lệnh sản xuất, bộ cửa, khách hàng"
          prefix={<SearchOutlined />}
          style={{ width: 280 }}
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
        />
        <Select<'SUFFICIENT' | 'SHORTAGE' | null>
          allowClear
          placeholder="Trạng thái: Tất cả"
          style={{ width: 200 }}
          value={statusFilter}
          onChange={(value) => setStatusFilter(value ?? null)}
          options={[
            { value: 'SUFFICIENT', label: `Đủ vật tư (${sufficientCount})` },
            { value: 'SHORTAGE', label: `Thiếu vật tư (${shortageCount})` },
          ]}
        />
        <Select<string | null>
          allowClear
          placeholder="Mẫu cửa: Tất cả"
          style={{ width: 220 }}
          value={doorProductFilter}
          onChange={(value) => setDoorProductFilter(value ?? null)}
          options={doorProductOptions}
        />
        {(keyword || statusFilter || doorProductFilter) && <Button onClick={resetFilters}>Xóa lọc</Button>}
      </Space>

      <Table
        rowKey="key"
        dataSource={filtered}
        pagination={{ pageSize: 20, showSizeChanger: true, showTotal: (total) => `${total} dòng nhu cầu cắt` }}
        columns={[
          {
            title: 'Lệnh SX / Bộ cửa',
            render: (_, row) => (
              <div>
                <div style={{ fontWeight: 600 }}>{row.ycsx}</div>
                <div style={{ color: '#8c8c8c', fontSize: 12 }}>Bộ cửa #{row.item}</div>
              </div>
            ),
          },
          { title: 'Khách hàng', dataIndex: 'customerName' },
          { title: 'Vật tư', dataIndex: 'slatMaterialName' },
          {
            title: 'Độ dài cần cắt',
            align: 'right',
            render: (_, row) => `${(row.cutLengthMm / 1000).toFixed(2)} m`,
          },
          { title: 'SL thanh cần', dataIndex: 'quantityNeeded', align: 'right' },
          {
            title: 'SL thanh thiếu',
            align: 'right',
            render: (_, row) => (row.quantityMissing > 0 ? row.quantityMissing : '—'),
          },
          {
            title: 'Trạng thái',
            render: (_, row) =>
              row.sufficient ? (
                <Tag color="success">Đủ vật tư</Tag>
              ) : (
                <Tag color="error">Thiếu {row.quantityMissing} thanh</Tag>
              ),
          },
          {
            title: 'Cách cắt',
            render: (_, row) => row.cutDescription ?? <span style={{ color: '#bfbfbf' }}>—</span>,
          },
        ]}
      />
    </div>
  )
}
