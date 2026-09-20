import { PlusOutlined, SearchOutlined } from '@ant-design/icons'
import { App, Button, Input, Select, Space, Table, Tag } from 'antd'
import { useCallback, useMemo, useState } from 'react'
import { extractErrorMessage } from '../../api/apiError'
import { ListLoadError } from '../../components/ListLoadError'
import { tablePagination, type PageParams } from '../../api/pagination'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { usePagedList } from '../../hooks/usePagedList'
import { SLAT_GROUP_COLOR, SLAT_GROUP_LABEL, SLAT_GROUP_OPTIONS, buildSlatGroupLookup } from '../inventory/constants'
import type { SlatGroup, SlatMaterialResponse } from '../inventory/types'
import { BomItemFormModal } from './BomItemFormModal'
import { ImportBomModal } from './ImportBomModal'
import { deleteBomItem, listBomItems } from './bomApi'
import type { BomItemResponse, DoorProductResponse } from './types'

interface Props {
  doorProducts: DoorProductResponse[]
  /** Danh mục vật tư ĐẦY ĐỦ — cần tra nhóm cho mọi dòng, kể cả dòng ngoài trang đang xem. */
  slatMaterials: SlatMaterialResponse[]
  canEdit: boolean
  onChanged: () => void
}

export function BomItemTable({ doorProducts, slatMaterials, canEdit, onChanged }: Props) {
  const { message, modal } = App.useApp()
  const [keyword, setKeyword] = useState('')
  const [groupFilter, setGroupFilter] = useState<SlatGroup | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<BomItemResponse | null>(null)
  const [importOpen, setImportOpen] = useState(false)

  const debouncedKeyword = useDebouncedValue(keyword)
  const load = useCallback(
    (params: PageParams) => listBomItems({ ...params, keyword: debouncedKeyword, slatGroup: groupFilter }),
    [debouncedKeyword, groupFilter],
  )
  const { data, loading, error, current, pageSize, handleTableChange, reload } = usePagedList(load, [debouncedKeyword, groupFilter], {
    errorMessage: 'Không tải được danh sách định mức BOM.',
  })

  const groupBySlatMaterialId = useMemo(() => buildSlatGroupLookup(slatMaterials), [slatMaterials])

  const handleChanged = useCallback(() => {
    reload()
    onChanged()
  }, [reload, onChanged])

  function confirmDelete(item: BomItemResponse) {
    modal.confirm({
      title: 'Xóa định mức BOM này?',
      content: `${item.doorProductName} · ${item.doorProductMauSac} — ${item.slatMaterialName}`,
      okText: 'Xóa',
      okButtonProps: { danger: true },
      cancelText: 'Hủy',
      onOk: async () => {
        try {
          await deleteBomItem(item.id)
          message.success('Đã xóa định mức BOM.')
          handleChanged()
        } catch (error) {
          message.error(extractErrorMessage(error, 'Không xóa được định mức BOM.'))
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
            placeholder="Tìm theo mẫu cửa, mã nan"
            prefix={<SearchOutlined />}
            style={{ width: 280 }}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
          />
          <Select
            allowClear
            placeholder="Nhóm thanh nan"
            style={{ width: 200 }}
            options={SLAT_GROUP_OPTIONS}
            value={groupFilter}
            onChange={(value) => setGroupFilter(value ?? null)}
          />
        </Space>
        {canEdit && (
          <Space wrap>
            <Button onClick={() => setImportOpen(true)}>Nhập từ Excel</Button>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                setEditing(null)
                setFormOpen(true)
              }}
            >
              Thêm định mức
            </Button>
          </Space>
        )}
      </Space>

      {canEdit && doorProducts.length === 0 && (
        <div style={{ marginBottom: 16, color: '#8c8c8c' }}>
          Chưa có mẫu cửa nào — bấm "Thêm định mức" rồi dùng "+ Tạo mới" ngay trong form để tạo mẫu cửa đầu tiên.
        </div>
      )}

      <ListLoadError message={error} onRetry={reload} />

      <Table
        rowKey="id"
        loading={loading}
        dataSource={data.content}
        pagination={tablePagination({ current, pageSize, total: data.totalElements }, (total) => `${total} định mức BOM`)}
        onChange={handleTableChange}
        columns={[
          {
            title: 'Mẫu cửa (Door Product)',
            render: (_, item) => (
              <div>
                <div>{item.doorProductName}</div>
                <div style={{ color: '#8c8c8c', fontSize: 12 }}>{item.doorProductMauSac}</div>
              </div>
            ),
          },
          {
            title: 'Loại thanh nan / Nhóm',
            render: (_, item) => {
              const group = groupBySlatMaterialId.get(item.slatMaterialId)
              return (
                <Space>
                  <span>{item.slatMaterialName}</span>
                  {group && <Tag color={SLAT_GROUP_COLOR[group]}>{SLAT_GROUP_LABEL[group]}</Tag>}
                </Space>
              )
            },
          },
          {
            title: 'Offset cắt (m)',
            render: (_, item) => {
              if (item.widthOffsetM != null) return <span>W: {item.widthOffsetM.toFixed(3)} m</span>
              if (item.heightOffsetM != null) return <span>H: {item.heightOffsetM.toFixed(3)} m</span>
              return '—'
            },
          },
          ...(canEdit
            ? [
                {
                  title: 'Hành động',
                  width: 140,
                  render: (_: unknown, item: BomItemResponse) => (
                    <Space>
                      <Button
                        size="small"
                        onClick={() => {
                          setEditing(item)
                          setFormOpen(true)
                        }}
                      >
                        Sửa
                      </Button>
                      <Button size="small" danger onClick={() => confirmDelete(item)}>
                        Xóa
                      </Button>
                    </Space>
                  ),
                },
              ]
            : []),
        ]}
      />

      <BomItemFormModal
        open={formOpen}
        bomItem={editing}
        doorProducts={doorProducts}
        slatMaterials={slatMaterials}
        onClose={() => setFormOpen(false)}
        onSaved={handleChanged}
        onDoorProductCreated={onChanged}
      />

      <ImportBomModal open={importOpen} onClose={() => setImportOpen(false)} onImported={handleChanged} />
    </>
  )
}
