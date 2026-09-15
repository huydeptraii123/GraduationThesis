import { App, Form, Input, InputNumber, Modal, Select } from 'antd'
import { useEffect, useState } from 'react'
import { extractErrorMessage } from '../../api/apiError'
import { SLAT_GROUP_OPTIONS } from './constants'
import { createMaterial, updateMaterial } from './inventoryApi'
import type { SlatMaterialRequest, SlatMaterialResponse } from './types'

interface Props {
  open: boolean
  /** null = thêm mới; có giá trị = sửa loại thanh nan đang chọn. */
  material: SlatMaterialResponse | null
  onClose: () => void
  onSaved: () => void
}

export function SlatMaterialFormModal({ open, material, onClose, onSaved }: Props) {
  const { message } = App.useApp()
  const [form] = Form.useForm<SlatMaterialRequest>()
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!open) {
      return
    }
    if (material) {
      form.setFieldsValue({
        slatMaterial: material.slatMaterial,
        slatMaterialName: material.slatMaterialName,
        slatGroup: material.slatGroup,
      })
    } else {
      form.resetFields()
    }
  }, [open, material, form])

  async function handleSubmit() {
    let values: SlatMaterialRequest
    try {
      values = await form.validateFields()
    } catch {
      return // AntD đã hiển thị lỗi ngay tại từng field, không cần báo thêm
    }
    setSaving(true)
    try {
      if (material) {
        await updateMaterial(material.id, values)
        message.success('Đã cập nhật loại thanh nan.')
      } else {
        await createMaterial(values)
        message.success('Đã thêm loại thanh nan.')
      }
      onSaved()
      onClose()
    } catch (error) {
      message.error(extractErrorMessage(error, 'Không lưu được loại thanh nan.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      title={material ? 'Sửa loại thanh nan' : 'Thêm loại thanh nan'}
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
          name="slatMaterial"
          label="Mã vật tư"
          extra="Mã lấy từ hệ thống nghiệp vụ nguồn, không được trùng."
          rules={[{ required: true, message: 'Nhập mã vật tư' }]}
        >
          <InputNumber min={1} precision={0} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item
          name="slatMaterialName"
          label="Tên loại thanh nan"
          rules={[{ required: true, message: 'Nhập tên loại thanh nan' }]}
        >
          {/* maxLength khớp @Size(max = 255) phía backend để chặn ngay tại form */}
          <Input maxLength={255} showCount />
        </Form.Item>
        <Form.Item
          name="slatGroup"
          label="Nhóm vật tư"
          extra="Nhóm vật tư quyết định cách áp offset khi sinh nhu cầu cắt, cần đặt đúng cho các mã do nhập Excel tự tạo."
          rules={[{ required: true, message: 'Chọn nhóm vật tư' }]}
        >
          <Select options={SLAT_GROUP_OPTIONS} />
        </Form.Item>
      </Form>
    </Modal>
  )
}
