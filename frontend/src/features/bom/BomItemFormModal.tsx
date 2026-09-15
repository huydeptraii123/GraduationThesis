import { PlusCircleOutlined } from '@ant-design/icons'
import { App, Button, Collapse, Form, InputNumber, Modal, Select, Typography } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { extractErrorMessage } from '../../api/apiError'
import { SLAT_GROUP_LABEL } from '../inventory/constants'
import type { SlatGroup, SlatMaterialResponse } from '../inventory/types'
import { QuickCreateDoorProductModal } from './QuickCreateDoorProductModal'
import { createBomItem, updateBomItem } from './bomApi'
import type { BomItemRequest, BomItemResponse, DoorProductResponse } from './types'

const { Text, Title } = Typography

interface Props {
  open: boolean
  /** null = thêm mới; có giá trị = sửa định mức đang chọn. */
  bomItem: BomItemResponse | null
  doorProducts: DoorProductResponse[]
  slatMaterials: SlatMaterialResponse[]
  onClose: () => void
  onSaved: () => void
  /** Gọi khi popup "+ Tạo mới" tạo xong 1 mẫu cửa, để BomPage nạp lại danh sách doorProducts. */
  onDoorProductCreated: () => void
}

const FORMULA_NOTES: Record<SlatGroup, string> = {
  MAIN_SLAT:
    'Áp dụng Offset W để tính chiều dài cắt nan: L_cắt = Chiều rộng cửa − Offset W. Số lượng nan = round(Hệ số × Chiều cao + Hằng số cơ bản).',
  RAIL: 'Áp dụng Offset H: luôn 2 thanh ray, chiều dài L_ray = Chiều cao cửa − Offset H.',
  BOTTOM_BAR: 'Luôn 1 thanh đáy, cắt theo đúng chiều rộng cửa. Không dùng offset hay hệ số nào.',
  SUB_SLAT: 'Luôn 1 thanh, cắt theo đúng chiều rộng cửa. Không dùng offset hay hệ số nào.',
  OTHER: 'Không xác định công thức theo nhóm — dùng định mức dự phòng (mét/bộ cửa) nếu có.',
}

export function BomItemFormModal({
  open,
  bomItem,
  doorProducts,
  slatMaterials,
  onClose,
  onSaved,
  onDoorProductCreated,
}: Props) {
  const { message } = App.useApp()
  const [form] = Form.useForm<BomItemRequest & { slatMaterialId?: number }>()
  const [saving, setSaving] = useState(false)
  const [quickCreateOpen, setQuickCreateOpen] = useState(false)
  // Theo dõi riêng để render lại đúng lúc Select loại thanh nan đổi, không đọc qua form mỗi lần render.
  const [selectedSlatMaterialId, setSelectedSlatMaterialId] = useState<number | null>(null)

  const slatGroup: SlatGroup | null =
    slatMaterials.find((m) => m.id === selectedSlatMaterialId)?.slatGroup ?? null

  useEffect(() => {
    if (!open) {
      return
    }
    if (bomItem) {
      form.setFieldsValue({
        doorProductId: bomItem.doorProductId,
        slatMaterialId: bomItem.slatMaterialId,
        widthOffsetM: bomItem.widthOffsetM,
        heightOffsetM: bomItem.heightOffsetM,
        slatCountSlope: bomItem.slatCountSlope,
        slatCountIntercept: bomItem.slatCountIntercept,
        dinhMucTbMPerBoCua: bomItem.dinhMucTbMPerBoCua,
      })
      // Đồng bộ state hiển thị theo prop `bomItem` (hệ thống ngoài component) khi modal vừa mở để sửa —
      // đúng trường hợp effect dùng để làm, không phải giá trị suy được ngay lúc render.
      // oxlint-disable-next-line react/set-state-in-effect
      setSelectedSlatMaterialId(bomItem.slatMaterialId)
    } else {
      form.resetFields()
      setSelectedSlatMaterialId(null)
    }
  }, [open, bomItem, form])

  const doorProductOptions = useMemo(
    () => doorProducts.map((d) => ({ value: d.id, label: `${d.doorMaterialName} · ${d.mauSac}` })),
    [doorProducts],
  )

  const slatMaterialOptions = useMemo(
    () =>
      slatMaterials.map((m) => ({
        value: m.id,
        label: `${m.slatMaterialName} (${SLAT_GROUP_LABEL[m.slatGroup]})`,
      })),
    [slatMaterials],
  )

  function handleSlatMaterialChange(id: number) {
    const newGroup = slatMaterials.find((m) => m.id === id)?.slatGroup ?? null
    setSelectedSlatMaterialId(id)
    // Xóa giá trị của các trường vừa bị ẩn theo nhóm mới — nếu không, giá trị cũ (ví dụ heightOffsetM
    // khi vừa chọn từ RAIL sang MAIN_SLAT) vẫn còn trong form và bị gửi kèm lên backend dù ẩn khỏi UI.
    const clears: Partial<BomItemRequest> = {}
    if (newGroup !== 'MAIN_SLAT') {
      clears.widthOffsetM = null
      clears.slatCountSlope = null
      clears.slatCountIntercept = null
    }
    if (newGroup !== 'RAIL') {
      clears.heightOffsetM = null
    }
    form.setFieldsValue(clears)
  }

  async function handleSubmit() {
    let values: BomItemRequest
    try {
      values = await form.validateFields()
    } catch {
      return // AntD đã hiển thị lỗi ngay tại từng field, không cần báo thêm
    }
    setSaving(true)
    try {
      if (bomItem) {
        await updateBomItem(bomItem.id, values)
        message.success('Đã cập nhật định mức BOM.')
      } else {
        await createBomItem(values)
        message.success('Đã thêm định mức BOM.')
      }
      onSaved()
      onClose()
    } catch (error) {
      message.error(extractErrorMessage(error, 'Không lưu được định mức BOM.'))
    } finally {
      setSaving(false)
    }
  }

  function handleDoorProductCreated(created: DoorProductResponse) {
    setQuickCreateOpen(false)
    onDoorProductCreated()
    form.setFieldValue('doorProductId', created.id)
  }

  return (
    <>
      <Modal
        title={bomItem ? 'Sửa định mức BOM' : 'Thêm định mức BOM mới'}
        open={open}
        onCancel={onClose}
        onOk={handleSubmit}
        confirmLoading={saving}
        okText="Lưu định mức"
        cancelText="Hủy bỏ"
        destroyOnHidden
        width={720}
      >
        <Form form={form} layout="vertical">
          <Title level={5}>1. Nhóm liên kết vật tư</Title>
          <div style={{ display: 'flex', gap: 16 }}>
            <Form.Item
              name="doorProductId"
              label={
                <span style={{ display: 'flex', alignItems: 'center', gap: 8, width: '100%' }}>
                  <span style={{ flex: 1 }}>Mẫu cửa</span>
                  <Button
                    type="link"
                    size="small"
                    icon={<PlusCircleOutlined />}
                    style={{ padding: 0, height: 'auto' }}
                    onClick={() => setQuickCreateOpen(true)}
                  >
                    Tạo mới
                  </Button>
                </span>
              }
              rules={[{ required: true, message: 'Chọn mẫu cửa' }]}
              style={{ flex: 1 }}
            >
              <Select showSearch optionFilterProp="label" placeholder="Chọn mẫu cửa" options={doorProductOptions} />
            </Form.Item>
            <Form.Item
              name="slatMaterialId"
              label="Loại thanh nan / profile"
              rules={[{ required: true, message: 'Chọn loại thanh nan' }]}
              style={{ flex: 1 }}
            >
              <Select
                showSearch
                optionFilterProp="label"
                placeholder="Chọn thanh cấu thành"
                options={slatMaterialOptions}
                onChange={handleSlatMaterialChange}
              />
            </Form.Item>
          </div>

          <Title level={5}>2. Độ hụt dung sai (Offset cắt)</Title>
          {slatGroup === 'MAIN_SLAT' && (
            <Form.Item
              name="widthOffsetM"
              label="Offset chiều rộng (W) (m)"
              extra="L_cắt = W_thông_thủy − Offset W"
            >
              <InputNumber step={0.001} style={{ width: 240 }} />
            </Form.Item>
          )}
          {slatGroup === 'RAIL' && (
            <Form.Item
              name="heightOffsetM"
              label="Offset chiều cao (H) (m)"
              extra="L_cắt = H_thông_thủy − Offset H"
            >
              <InputNumber step={0.001} style={{ width: 240 }} />
            </Form.Item>
          )}
          {slatGroup && slatGroup !== 'MAIN_SLAT' && slatGroup !== 'RAIL' && (
            <Text type="secondary" italic style={{ display: 'block', marginBottom: 16 }}>
              Nhóm vật tư này không áp dụng dung sai offset cắt.
            </Text>
          )}
          {!slatGroup && (
            <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
              Chọn loại thanh nan để hiện trường offset tương ứng.
            </Text>
          )}

          {slatGroup === 'MAIN_SLAT' && (
            <>
              <Title level={5}>3. Thông số số lượng nan (do kỹ thuật cung cấp)</Title>
              <div style={{ display: 'flex', gap: 16 }}>
                <Form.Item name="slatCountSlope" label="Hệ số theo chiều cao (H)" style={{ flex: 1 }}>
                  <InputNumber step={0.01} style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item name="slatCountIntercept" label="Hằng số cơ bản" style={{ flex: 1 }}>
                  <InputNumber step={0.01} style={{ width: '100%' }} />
                </Form.Item>
              </div>
            </>
          )}

          <Title level={5}>4. Định mức dự phòng</Title>
          <Form.Item
            name="dinhMucTbMPerBoCua"
            label="Định mức trung bình (mét/bộ cửa)"
            extra="Dùng khi định mức này chưa được kỹ thuật cung cấp công thức cắt riêng — áp dụng cho mọi nhóm vật tư, không chỉ nhóm Khác."
          >
            <InputNumber step={0.1} style={{ width: 240 }} />
          </Form.Item>

          <Collapse
            style={{ marginTop: 8 }}
            items={[
              {
                key: 'formula',
                label: 'Công thức áp dụng theo từng nhóm vật tư',
                children: (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    {(Object.keys(FORMULA_NOTES) as SlatGroup[]).map((group) => (
                      <div key={group}>
                        <Text strong>{SLAT_GROUP_LABEL[group]} ({group}): </Text>
                        <Text type="secondary">{FORMULA_NOTES[group]}</Text>
                      </div>
                    ))}
                  </div>
                ),
              },
            ]}
          />
        </Form>
      </Modal>

      <QuickCreateDoorProductModal
        open={quickCreateOpen}
        onClose={() => setQuickCreateOpen(false)}
        onCreated={handleDoorProductCreated}
      />
    </>
  )
}
