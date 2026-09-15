import { PlusOutlined, SearchOutlined, UploadOutlined } from '@ant-design/icons'
import { App, Button, Input, Select, Space, Table, Tag } from 'antd'
import { useMemo, useState } from 'react'
import { extractErrorMessage } from '../../api/apiError'
import { ImportInventoryModal } from './ImportInventoryModal'
import { InventoryBatchFormModal } from './InventoryBatchFormModal'
import { SLAT_GROUP_COLOR, SLAT_GROUP_LABEL, SLAT_GROUP_OPTIONS } from './constants'
import { deleteBatch } from './inventoryApi'
import type { InventoryBatchResponse, SlatGroup, SlatMaterialResponse } from './types'

interface Props {
  batches: InventoryBatchResponse[]
  materials: SlatMaterialResponse[]
  canEdit: boolean
  loading: boolean
  onChanged: () => void
}

export function InventoryBatchTable({ batches, materials, canEdit, loading, onChanged }: Props) {
  const { message, modal } = App.useApp()
  const [keyword, setKeyword] = useState('')
  const [groupFilter, setGroupFilter] = useState<SlatGroup | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<InventoryBatchResponse | null>(null)
  const [importOpen, setImportOpen] = useState(false)

  const groupByMaterialId = useMemo(() => {
    const map = new Map<number, SlatGroup>()
    materials.forEach((material) => map.set(material.id, material.slatGroup))
    return map
  }, [materials])

  const codeByMaterialId = useMemo(() => {
    const map = new Map<number, number>()
    materials.forEach((material) => map.set(material.id, material.slatMaterial))
    return map
  }, [materials])

  const filtered = useMemo(() => {
    const needle = keyword.trim().toLowerCase()
    return batches.filter((batch) => {
      if (groupFilter && groupByMaterialId.get(batch.slatMaterialId) !== groupFilter) {
        return false
      }
      if (!needle) {
        return true
      }
      const code = codeByMaterialId.get(batch.slatMaterialId)
      return (
        batch.slatMaterialName.toLowerCase().includes(needle) || (code !== undefined && String(code).includes(needle))
      )
    })
  }, [batches, keyword, groupFilter, groupByMaterialId, codeByMaterialId])

  function confirmDelete(batch: InventoryBatchResponse) {
    modal.confirm({
      title: 'Xóa lô tồn kho này?',
      content: `${batch.slatMaterialName} — ${batch.doDaiThanhMm.toLocaleString('vi-VN')} mm`,
      okText: 'Xóa',
      okButtonProps: { danger: true },
      cancelText: 'Hủy',
      onOk: async () => {
        try {
          await deleteBatch(batch.id)
          message.success('Đã xóa lô tồn kho.')
          onChanged()
        } catch (error) {
          message.error(extractErrorMessage(error, 'Không xóa được lô tồn kho.'))
          // Ném lại để AntD giữ hộp thoại mở — đóng lại sẽ khiến người dùng tưởng đã xóa xong.
          throw error
        }
      },
    })
  }

  return (
    <>
      <Space style={{ marginBottom: 16, width: '100%', justifyContent: 'space-between' }} wrap>
        <Space wrap>
          <Input
            allowClear
            placeholder="Tìm theo mã hoặc tên thanh nan"
            prefix={<SearchOutlined />}
            style={{ width: 280 }}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
          />
          <Select
            allowClear
            placeholder="Lọc theo nhóm vật tư"
            style={{ width: 200 }}
            options={SLAT_GROUP_OPTIONS}
            value={groupFilter}
            onChange={(value) => setGroupFilter(value ?? null)}
          />
        </Space>
        {canEdit && (
          <Space wrap>
            <Button icon={<UploadOutlined />} onClick={() => setImportOpen(true)}>
              Nhập từ Excel
            </Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                setEditing(null)
                setFormOpen(true)
              }}
            >
              Thêm lô tồn kho
            </Button>
          </Space>
        )}
      </Space>

      <Table
        rowKey="id"
        loading={loading}
        dataSource={filtered}
        pagination={{ pageSize: 20, showSizeChanger: true, showTotal: (total) => `${total} lô tồn kho` }}
        columns={[
          {
            title: 'Mã vật tư',
            width: 120,
            render: (_, batch) => codeByMaterialId.get(batch.slatMaterialId) ?? '—',
          },
          { title: 'Tên loại thanh nan', dataIndex: 'slatMaterialName' },
          {
            title: 'Nhóm vật tư',
            width: 160,
            render: (_, batch) => {
              const group = groupByMaterialId.get(batch.slatMaterialId)
              return group ? <Tag color={SLAT_GROUP_COLOR[group]}>{SLAT_GROUP_LABEL[group]}</Tag> : '—'
            },
          },
          {
            title: 'Chiều dài thanh (mm)',
            dataIndex: 'doDaiThanhMm',
            width: 180,
            align: 'right',
            sorter: (a, b) => a.doDaiThanhMm - b.doDaiThanhMm,
            render: (value: number) => value.toLocaleString('vi-VN'),
          },
          {
            title: 'Số lượng thanh',
            dataIndex: 'soThanh',
            width: 150,
            align: 'right',
            sorter: (a, b) => a.soThanh - b.soThanh,
            render: (value: number) => value.toLocaleString('vi-VN'),
          },
          ...(canEdit
            ? [
                {
                  title: 'Thao tác',
                  width: 140,
                  render: (_: unknown, batch: InventoryBatchResponse) => (
                    <Space>
                      <Button
                        size="small"
                        onClick={() => {
                          setEditing(batch)
                          setFormOpen(true)
                        }}
                      >
                        Sửa
                      </Button>
                      <Button size="small" danger onClick={() => confirmDelete(batch)}>
                        Xóa
                      </Button>
                    </Space>
                  ),
                },
              ]
            : []),
        ]}
      />

      <InventoryBatchFormModal
        open={formOpen}
        batch={editing}
        materials={materials}
        onClose={() => setFormOpen(false)}
        onSaved={onChanged}
      />
      <ImportInventoryModal open={importOpen} onClose={() => setImportOpen(false)} onImported={onChanged} />
    </>
  )
}
