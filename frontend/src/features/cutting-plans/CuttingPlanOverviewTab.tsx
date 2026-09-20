import { Card, Col, Row, Statistic, Table, Tag, Typography } from 'antd'
import dayjs from 'dayjs'
import { useMemo } from 'react'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip as RechartsTooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type { CuttingBatch, CuttingBatchOrderRow } from './cuttingBatches'
import type { CuttingPlanResponse } from './types'

interface Props {
  plan: CuttingPlanResponse
  orderRows: CuttingBatchOrderRow[]
  batches: CuttingBatch[]
}

// Ngưỡng hiển thị tham khảo cho PLANNER, không phải ràng buộc hệ thống kiểm tra hay chặn.
const TARGET_WASTE_RATIO_PERCENT = 5

const SUFFICIENT_COLOR = '#52c41a'
const SHORTAGE_COLOR = '#f5222d'

export function CuttingPlanOverviewTab({ plan, orderRows, batches }: Props) {
  const wasteRatioPercent = plan.totalStockUsedM > 0 ? (plan.totalWasteM / plan.totalStockUsedM) * 100 : 0
  const sufficientCount = orderRows.filter((row) => !row.hasShortage).length
  const shortageCount = orderRows.filter((row) => row.hasShortage).length

  const byDeliveryDate = useMemo(() => {
    const buckets = new Map<string, { date: string; sufficient: number; shortage: number }>()
    orderRows.forEach((row) => {
      const bucket = buckets.get(row.reqdDeliveryDate) ?? { date: row.reqdDeliveryDate, sufficient: 0, shortage: 0 }
      if (row.hasShortage) {
        bucket.shortage += 1
      } else {
        bucket.sufficient += 1
      }
      buckets.set(row.reqdDeliveryDate, bucket)
    })
    return Array.from(buckets.values())
      .sort((a, b) => a.date.localeCompare(b.date))
      .map((bucket) => ({ ...bucket, label: dayjs(bucket.date).format('DD/MM') }))
  }, [orderRows])

  const byDoorProduct = useMemo(() => {
    const counts = new Map<string, number>()
    orderRows.forEach((row) => {
      counts.set(row.doorProductName, (counts.get(row.doorProductName) ?? 0) + 1)
    })
    const entries = Array.from(counts.entries()).sort((a, b) => b[1] - a[1])
    const max = entries.length > 0 ? entries[0][1] : 0
    return entries.map(([name, count]) => ({ name, count, percent: max > 0 ? (count / max) * 100 : 0 }))
  }, [orderRows])

  const shortagesByMaterial = useMemo(() => {
    const groups = new Map<
      number,
      { slatMaterialName: string; missingQuantity: number; missingLengthM: number; affectedOrders: Set<string> }
    >()
    plan.shortages.forEach((shortage) => {
      const group = groups.get(shortage.slatMaterialId) ?? {
        slatMaterialName: shortage.slatMaterialName,
        missingQuantity: 0,
        missingLengthM: 0,
        affectedOrders: new Set<string>(),
      }
      group.missingQuantity += shortage.missingQuantity
      group.missingLengthM += shortage.missingLengthM
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
              styles={{ content: { color: wasteRatioPercent > TARGET_WASTE_RATIO_PERCENT ? '#cf1322' : '#3f8600' } }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic
              title="Đủ vật tư"
              value={sufficientCount}
              suffix={`/ ${orderRows.length}`}
              styles={{ content: { color: SUFFICIENT_COLOR } }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic
              title="Thiếu vật tư"
              value={shortageCount}
              suffix={`/ ${orderRows.length}`}
              styles={{ content: { color: SHORTAGE_COLOR } }}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={16} style={{ marginTop: 16 }}>
        <Col span={14}>
          <Card size="small" title="Số bộ cửa theo ngày giao yêu cầu">
            <ResponsiveContainer width="100%" height={240}>
              <BarChart data={byDeliveryDate}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="label" />
                <YAxis allowDecimals={false} />
                <RechartsTooltip />
                <Legend />
                <Bar dataKey="sufficient" name="Đủ vật tư" stackId="orders" fill={SUFFICIENT_COLOR} />
                <Bar dataKey="shortage" name="Thiếu vật tư" stackId="orders" fill={SHORTAGE_COLOR} />
              </BarChart>
            </ResponsiveContainer>
          </Card>
        </Col>
        <Col span={10}>
          <Card size="small" title="Tỷ trọng đủ/thiếu vật tư">
            <ResponsiveContainer width="100%" height={240}>
              <PieChart>
                <Pie
                  data={[
                    { name: 'Đủ vật tư', value: sufficientCount },
                    { name: 'Thiếu vật tư', value: shortageCount },
                  ]}
                  dataKey="value"
                  nameKey="name"
                  innerRadius={50}
                  outerRadius={80}
                >
                  <Cell fill={SUFFICIENT_COLOR} />
                  <Cell fill={SHORTAGE_COLOR} />
                </Pie>
                <Legend />
                <RechartsTooltip />
              </PieChart>
            </ResponsiveContainer>
          </Card>
        </Col>
      </Row>

      <Card size="small" title="Số bộ cửa theo mẫu cửa" style={{ marginTop: 16 }}>
        {/* Cuộn trong khung thay vì kéo dài trang: một lần chạy có thể gồm hàng chục mẫu cửa. */}
        <div style={{ maxHeight: 320, overflowY: 'auto' }}>
        {byDoorProduct.map((row) => (
          <div key={row.name} style={{ marginBottom: 8 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12 }}>
              <span>{row.name}</span>
              <span>{row.count} bộ</span>
            </div>
            <div style={{ background: '#f0f0f0', borderRadius: 4, height: 8 }}>
              <div style={{ width: `${row.percent}%`, background: '#1677ff', height: 8, borderRadius: 4 }} />
            </div>
          </div>
        ))}
        </div>
      </Card>

      {shortagesByMaterial.length > 0 && (
        <Card size="small" title="Cảnh báo: Vật tư thiếu hụt" style={{ marginTop: 16 }}>
          <Table
            size="small"
            rowKey="slatMaterialId"
            pagination={{ pageSize: 10, showSizeChanger: true, showTotal: (total) => `${total} loại vật tư thiếu` }}
            dataSource={shortagesByMaterial}
            columns={[
              { title: 'Loại thanh nan', dataIndex: 'slatMaterialName' },
              { title: 'SL đoạn thiếu', dataIndex: 'missingQuantity', align: 'right' },
              {
                title: 'Tổng độ dài thiếu',
                dataIndex: 'missingLengthM',
                align: 'right',
                render: (value: number) => `${value.toFixed(2)} m`,
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
          pagination={{ pageSize: 10, showSizeChanger: true, showTotal: (total) => `${total} đợt cắt` }}
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
                    render: (_, order) => `${order.chieuCaoDh.toFixed(3)} × ${order.chieuRongDh.toFixed(3)} m`,
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
