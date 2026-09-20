import { PlusOutlined, SearchOutlined } from '@ant-design/icons'
import { App, Button, Input, Select, Space, Table, Tag } from 'antd'
import { useCallback, useState } from 'react'
import { extractErrorMessage } from '../../api/apiError'
import { ListLoadError } from '../../components/ListLoadError'
import { tablePagination, type PageParams } from '../../api/pagination'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { usePagedList } from '../../hooks/usePagedList'
import { SlatMaterialFormModal } from './SlatMaterialFormModal'
import { SLAT_GROUP_COLOR, SLAT_GROUP_LABEL, SLAT_GROUP_OPTIONS } from './constants'
import { deleteMaterial, listMaterials } from './inventoryApi'
import type { SlatGroup, SlatMaterialResponse } from './types'

interface Props {
  canEdit: boolean
  /** Báo trang cha nạp lại danh mục vật tư đầy đủ (dropdown, tra mã ở bảng lô tồn kho). */
  onChanged: () => void
}

export function SlatMaterialTable({ canEdit, onChanged }: Props) {
  const { message, modal } = App.useApp()
  const [keyword, setKeyword] = useState('')
  const [groupFilter, setGroupFilter] = useState<SlatGroup | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<SlatMaterialResponse | null>(null)

  const debouncedKeyword = useDebouncedValue(keyword)
  const load = useCallback(
    (params: PageParams) => listMaterials({ ...params, keyword: debouncedKeyword, slatGroup: groupFilter }),
    [debouncedKeyword, groupFilter],
  )
  const { data, loading, error, current, pageSize, handleTableChange, reload } = usePagedList(load, [debouncedKeyword, groupFilter], {
    errorMessage: 'Không tải được danh mục loại thanh nan.',
  })

  const handleChanged = useCallback(() => {
    reload()
    onChanged()
  }, [reload, onChanged])

  function confirmDelete(material: SlatMaterialResponse) {
    modal.confirm({
      title: 'Xóa loại thanh nan này?',
      content: `${material.slatMaterial} — ${material.slatMaterialName}`,
      okText: 'Xóa',
      okButtonProps: { danger: true },
      cancelText: 'Hủy',
      onOk: async () => {
        try {
          await deleteMaterial(material.id)
          message.success('Đã xóa loại thanh nan.')
          handleChanged()
        } catch (error) {
          message.error(extractErrorMessage(error, 'Không xóa được loại thanh nan.'))
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
            placeholder="Tìm theo mã hoặc tên loại thanh nan"
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
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setEditing(null)
              setFormOpen(true)
            }}
          >
            Thêm loại thanh nan
          </Button>
        )}
      </Space>

      <ListLoadError message={error} onRetry={reload} />

      <Table
        rowKey="id"
        loading={loading}
        dataSource={data.content}
        pagination={tablePagination({ current, pageSize, total: data.totalElements }, (total) => `${total} loại thanh nan`)}
        onChange={handleTableChange}
        columns={[
          {
            title: 'Mã vật tư',
            dataIndex: 'slatMaterial',
            width: 140,
            sorter: true,
          },
          { title: 'Tên loại thanh nan', dataIndex: 'slatMaterialName' },
          {
            title: 'Nhóm vật tư',
            dataIndex: 'slatGroup',
            width: 180,
            render: (group: SlatGroup) => <Tag color={SLAT_GROUP_COLOR[group]}>{SLAT_GROUP_LABEL[group]}</Tag>,
          },
          ...(canEdit
            ? [
                {
                  title: 'Thao tác',
                  width: 140,
                  render: (_: unknown, material: SlatMaterialResponse) => (
                    <Space>
                      <Button
                        size="small"
                        onClick={() => {
                          setEditing(material)
                          setFormOpen(true)
                        }}
                      >
                        Sửa
                      </Button>
                      <Button size="small" danger onClick={() => confirmDelete(material)}>
                        Xóa
                      </Button>
                    </Space>
                  ),
                },
              ]
            : []),
        ]}
      />

      <SlatMaterialFormModal
        open={formOpen}
        material={editing}
        onClose={() => setFormOpen(false)}
        onSaved={handleChanged}
      />
    </>
  )
}
