import { App, Form, Input, InputNumber, Modal } from 'antd'
import { useState } from 'react'
import { extractErrorMessage } from '../../api/apiError'
import { createDoorProduct } from './bomApi'
import type { DoorProductRequest, DoorProductResponse } from './types'

interface Props {
  open: boolean
  onClose: () => void
  /** Gọi lại với mẫu cửa vừa tạo, để modal cha tự chọn luôn vào Select — đúng hành vi mockup. */
  onCreated: (created: DoorProductResponse) => void
}

/** Popup tạo nhanh 1 mẫu cửa, mở từ link "+ Tạo mới" trong BomItemFormModal (theo đúng mockup gốc). */
export function QuickCreateDoorProductModal({ open, onClose, onCreated }: Props) {
  const { message } = App.useApp()
  const [form] = Form.useForm<DoorProductRequest>()
  const [saving, setSaving] = useState(false)

  function handleClose() {
    form.resetFields()
    onClose()
  }

  async function handleSubmit() {
    let values: DoorProductRequest
    try {
      values = await form.validateFields()
    } catch {
      return // AntD đã hiển thị lỗi ngay tại từng field, không cần báo thêm
    }
    setSaving(true)
    try {
      const created = await createDoorProduct(values)
      message.success('Đã tạo mẫu cửa mới.')
      form.resetFields()
      onCreated(created)
    } catch (error) {
      message.error(extractErrorMessage(error, 'Không tạo được mẫu cửa.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      title="Tạo mẫu cửa mới"
      open={open}
      onCancel={handleClose}
      onOk={handleSubmit}
      confirmLoading={saving}
      okText="Tạo và chọn"
      cancelText="Hủy"
      destroyOnHidden
      width={440}
      // z-index cao hơn modal cha để hiện đè lên đúng như popup lồng trong mockup
      zIndex={1050}
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="material"
          label="Mã vật tư"
          extra="Mã mẫu cửa từ hệ thống nguồn."
          rules={[{ required: true, message: 'Nhập mã vật tư' }]}
        >
          <InputNumber min={1} precision={0} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item
          name="doorMaterialName"
          label="Tên mẫu cửa"
          rules={[{ required: true, message: 'Nhập tên mẫu cửa' }]}
        >
          <Input maxLength={255} showCount />
        </Form.Item>
        <Form.Item name="mauSac" label="Màu" rules={[{ required: true, message: 'Nhập màu' }]}>
          <Input maxLength={20} showCount placeholder="Ví dụ: #01" />
        </Form.Item>
      </Form>
    </Modal>
  )
}
