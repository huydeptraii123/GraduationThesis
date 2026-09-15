import { App, Button, DatePicker, Drawer, Form, Input, InputNumber, Select, Space, Typography } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { useEffect, useState } from 'react'
import { extractErrorMessage } from '../../api/apiError'
import type { DoorProductResponse } from '../bom/types'
import { createSalesOrder, updateSalesOrder } from './salesOrdersApi'
import type { CustomerResponse, SalesOrderRequest, SalesOrderResponse } from './types'

interface Props {
  open: boolean
  /** null = thêm mới; có giá trị = sửa đơn hàng đang chọn. */
  salesOrder: SalesOrderResponse | null
  customers: CustomerResponse[]
  doorProducts: DoorProductResponse[]
  onClose: () => void
  onSaved: () => void
}

type FormValues = Omit<SalesOrderRequest, 'reqdDeliveryDate'> & { reqdDeliveryDate: Dayjs }

export function SalesOrderFormDrawer({ open, salesOrder, customers, doorProducts, onClose, onSaved }: Props) {
  const { message } = App.useApp()
  const [form] = Form.useForm<FormValues>()
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!open) {
      return
    }
    if (salesOrder) {
      form.setFieldsValue({
        ycsx: salesOrder.ycsx,
        item: salesOrder.item,
        customerId: salesOrder.customerId,
        doorProductId: salesOrder.doorProductId,
        chieuCaoDh: salesOrder.chieuCaoDh,
        chieuRongDh: salesOrder.chieuRongDh,
        reqdDeliveryDate: dayjs(salesOrder.reqdDeliveryDate),
      })
    } else {
      form.resetFields()
    }
  }, [open, salesOrder, form])

  const customerOptions = customers.map((c) => ({ value: c.id, label: c.customerName }))
  const doorProductOptions = doorProducts.map((d) => ({ value: d.id, label: `${d.doorMaterialName} · ${d.mauSac}` }))

  async function handleSubmit() {
    let values: FormValues
    try {
      values = await form.validateFields()
    } catch {
      return // AntD đã hiển thị lỗi ngay tại từng field, không cần báo thêm
    }
    const payload: SalesOrderRequest = {
      ...values,
      reqdDeliveryDate: values.reqdDeliveryDate.format('YYYY-MM-DD'),
      // Không có trong form (đơn thủ công không có 2 giá trị SAP này) — khi sửa đơn được tạo qua
      // import Excel, phải giữ nguyên giá trị gốc, không được gửi null đè lên (mất liên kết SAP).
      salesDocument: salesOrder?.salesDocument ?? null,
      salesOrderItem: salesOrder?.salesOrderItem ?? null,
    }
    setSaving(true)
    try {
      if (salesOrder) {
        await updateSalesOrder(salesOrder.id, payload)
        message.success('Đã cập nhật đơn hàng.')
      } else {
        await createSalesOrder(payload)
        message.success('Đã thêm đơn hàng.')
      }
      onSaved()
      onClose()
    } catch (error) {
      message.error(extractErrorMessage(error, 'Không lưu được đơn hàng.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Drawer
      title={salesOrder ? 'Sửa đơn hàng' : 'Thêm đơn hàng mới'}
      open={open}
      onClose={onClose}
      width={480}
      extra={
        <Space>
          <Button onClick={onClose}>Hủy</Button>
          <Button type="primary" loading={saving} onClick={handleSubmit}>
            Lưu đơn hàng
          </Button>
        </Space>
      }
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="ycsx"
          label="Lệnh sản xuất (YCSX)"
          rules={[{ required: true, message: 'Nhập lệnh sản xuất' }, { max: 20, message: 'Tối đa 20 ký tự' }]}
        >
          <Input placeholder="VD: SX-2601-045" maxLength={20} />
        </Form.Item>
        <Form.Item
          name="item"
          label="Số thứ tự bộ cửa (zItem)"
          rules={[{ required: true, message: 'Nhập số thứ tự bộ cửa' }]}
        >
          <InputNumber min={1} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="customerId" label="Khách hàng" rules={[{ required: true, message: 'Chọn khách hàng' }]}>
          <Select showSearch optionFilterProp="label" placeholder="Chọn khách hàng" options={customerOptions} />
        </Form.Item>
        <Form.Item
          name="doorProductId"
          label="Mẫu cửa & Màu"
          rules={[{ required: true, message: 'Chọn mẫu cửa' }]}
        >
          <Select showSearch optionFilterProp="label" placeholder="Chọn mẫu cửa" options={doorProductOptions} />
        </Form.Item>
        <div style={{ display: 'flex', gap: 16 }}>
          <Form.Item
            name="chieuCaoDh"
            label="Chiều cao (H)"
            style={{ flex: 1 }}
            rules={[{ required: true, message: 'Nhập chiều cao' }]}
          >
            <InputNumber min={0.001} step={0.001} style={{ width: '100%' }} addonAfter="m" />
          </Form.Item>
          <Form.Item
            name="chieuRongDh"
            label="Chiều rộng (W)"
            style={{ flex: 1 }}
            rules={[{ required: true, message: 'Nhập chiều rộng' }]}
          >
            <InputNumber min={0.001} step={0.001} style={{ width: '100%' }} addonAfter="m" />
          </Form.Item>
        </div>
        <Form.Item
          name="reqdDeliveryDate"
          label="Ngày giao yêu cầu"
          rules={[{ required: true, message: 'Chọn ngày giao yêu cầu' }]}
        >
          <DatePicker style={{ width: '100%' }} format="DD/MM/YYYY" />
        </Form.Item>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          Tự động kích hoạt cảnh báo trễ hạn theo ngưỡng T+3 ngày.
        </Typography.Text>
      </Form>
    </Drawer>
  )
}
