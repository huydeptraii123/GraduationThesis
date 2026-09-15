import { App, Form, InputNumber, Modal, Select } from 'antd'
import { useEffect, useState } from 'react'
import { extractErrorMessage } from '../../api/apiError'
import { createBatch, updateBatch } from './inventoryApi'
import type { InventoryBatchRequest, InventoryBatchResponse, SlatMaterialResponse } from './types'

interface Props {
  open: boolean
  /** null = thêm mới; có giá trị = sửa lô đang chọn. */
  batch: InventoryBatchResponse | null
  materials: SlatMaterialResponse[]
  onClose: () => void
  onSaved: () => void
}

export function InventoryBatchFormModal({ open, batch, materials, onClose, onSaved }: Props) {
  const { message } = App.useApp()
  const [form] = Form.useForm<InventoryBatchRequest>()
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!open) {
      return
    }
    if (batch) {
      form.setFieldsValue({
        slatMaterialId: batch.slatMaterialId,
        doDaiThanhMm: batch.doDaiThanhMm,
        soThanh: batch.soThanh,
      })
    } else {
      form.resetFields()
    }
  }, [open, batch, form])

  async function handleSubmit() {
    let values: InventoryBatchRequest
    try {
      values = await form.validateFields()
    } catch {
      return // AntD đã hiển thị lỗi ngay tại từng field, không cần báo thêm
    }
    setSaving(true)
    try {
      if (batch) {
        await updateBatch(batch.id, values)
        message.success('Đã cập nhật lô tồn kho.')
      } else {
        await createBatch(values)
        message.success('Đã thêm lô tồn kho.')
      }
      onSaved()
      onClose()
    } catch (error) {
      message.error(extractErrorMessage(error, 'Không lưu được lô tồn kho.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      title={batch ? 'Sửa lô tồn kho' : 'Thêm lô tồn kho'}
      open={open}
      onCancel={onClose}
      onOk={handleSubmit}
      confirmLoading={saving}
      okText="Lưu"
      cancelText="Hủy"
      destroyOnHidden
      width={480}
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="slatMaterialId"
          label="Loại thanh nan"
          rules={[{ required: true, message: 'Chọn loại thanh nan' }]}
        >
          <Select
            showSearch
            optionFilterProp="label"
            placeholder="Tìm theo mã hoặc tên"
            options={materials.map((m) => ({
              value: m.id,
              label: `${m.slatMaterial} — ${m.slatMaterialName}`,
            }))}
          />
        </Form.Item>
        <Form.Item
          name="doDaiThanhMm"
          label="Chiều dài thanh (mm)"
          rules={[{ required: true, message: 'Nhập chiều dài thanh' }]}
        >
          <InputNumber min={1} step={100} precision={0} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="soThanh" label="Số lượng thanh" rules={[{ required: true, message: 'Nhập số lượng thanh' }]}>
          <InputNumber min={0} precision={0} style={{ width: '100%' }} />
        </Form.Item>
      </Form>
    </Modal>
  )
}
