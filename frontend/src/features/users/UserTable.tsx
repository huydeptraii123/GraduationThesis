import { PlusOutlined, SearchOutlined } from '@ant-design/icons'
import { App, Button, Input, Select, Space, Table, Tag, Tooltip } from 'antd'
import { useCallback, useState } from 'react'
import { extractErrorMessage } from '../../api/apiError'
import { ListLoadError } from '../../components/ListLoadError'
import { tablePagination, type PageParams } from '../../api/pagination'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { usePagedList } from '../../hooks/usePagedList'
import { ROLE_COLOR, ROLE_LABEL, type Role } from '../auth/permissions'
import { ResetPasswordModal } from './ResetPasswordModal'
import { UserFormModal } from './UserFormModal'
import { listUsers, updateUser } from './usersApi'
import type { UserResponse } from './types'

const ROLE_FILTER_OPTIONS = (['ADMIN', 'PLANNER'] as Role[]).map((role) => ({
  value: role,
  label: `${ROLE_LABEL[role]} (${role})`,
}))

const STATUS_FILTER_OPTIONS = [
  { value: 'enabled', label: 'Đang hoạt động' },
  { value: 'disabled', label: 'Đã khóa' },
]

interface Props {
  /** Tên đăng nhập của người đang xem — dùng để chặn các thao tác tự hại trên chính dòng của mình. */
  currentUsername: string
  /** Báo trang cha nạp lại 2 ô thống kê (tổng số tài khoản, số đang hoạt động). */
  onChanged: () => void
}

export function UserTable({ currentUsername, onChanged }: Props) {
  const { message, modal } = App.useApp()
  const [keyword, setKeyword] = useState('')
  const [roleFilter, setRoleFilter] = useState<Role | null>(null)
  const [statusFilter, setStatusFilter] = useState<string | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<UserResponse | null>(null)
  const [resetting, setResetting] = useState<UserResponse | null>(null)

  const debouncedKeyword = useDebouncedValue(keyword)
  // Bộ lọc trạng thái ở giao diện là chuỗi 'enabled'/'disabled'; backend nhận cờ boolean, bỏ trống
  // nghĩa là không lọc.
  const enabledFilter = statusFilter === null ? null : statusFilter === 'enabled'
  const load = useCallback(
    (params: PageParams) =>
      listUsers({ ...params, keyword: debouncedKeyword, roleCode: roleFilter, enabled: enabledFilter }),
    [debouncedKeyword, roleFilter, enabledFilter],
  )
  const { data, loading, error, current, pageSize, handleTableChange, reload } = usePagedList(
    load,
    [debouncedKeyword, roleFilter, enabledFilter],
    { errorMessage: 'Không tải được danh sách tài khoản.' },
  )

  const handleChanged = useCallback(() => {
    reload()
    onChanged()
  }, [reload, onChanged])

  function confirmToggleEnabled(user: UserResponse) {
    const locking = user.enabled
    modal.confirm({
      title: locking ? `Khóa tài khoản ${user.username}?` : `Mở khóa tài khoản ${user.username}?`,
      content: locking
        ? 'Tài khoản bị khóa sẽ không đăng nhập được nữa; dữ liệu và lịch sử thao tác vẫn được giữ nguyên.'
        : 'Tài khoản sẽ đăng nhập lại được bằng mật khẩu hiện có.',
      okText: locking ? 'Khóa' : 'Mở khóa',
      okButtonProps: { danger: locking },
      cancelText: 'Hủy',
      onOk: async () => {
        try {
          await updateUser(user.id, { roleCode: user.roleCode, enabled: !user.enabled })
          message.success(locking ? 'Đã khóa tài khoản.' : 'Đã mở khóa tài khoản.')
          handleChanged()
        } catch (error) {
          message.error(extractErrorMessage(error, 'Không đổi được trạng thái tài khoản.'))
          // Ném lại để AntD giữ hộp thoại mở — đóng lại sẽ khiến người dùng tưởng đã xong.
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
            placeholder="Tìm theo tên đăng nhập"
            prefix={<SearchOutlined />}
            style={{ width: 260 }}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
          />
          <Select
            allowClear
            placeholder="Lọc theo vai trò"
            style={{ width: 190 }}
            options={ROLE_FILTER_OPTIONS}
            value={roleFilter}
            onChange={(value) => setRoleFilter(value ?? null)}
          />
          <Select
            allowClear
            placeholder="Lọc theo trạng thái"
            style={{ width: 170 }}
            options={STATUS_FILTER_OPTIONS}
            value={statusFilter}
            onChange={(value) => setStatusFilter(value ?? null)}
          />
        </Space>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => {
            setEditing(null)
            setFormOpen(true)
          }}
        >
          Tạo tài khoản mới
        </Button>
      </Space>

      <ListLoadError message={error} onRetry={reload} />

      <Table
        rowKey="id"
        loading={loading}
        dataSource={data.content}
        pagination={tablePagination({ current, pageSize, total: data.totalElements }, (total) => `${total} tài khoản`)}
        onChange={handleTableChange}
        columns={[
          {
            title: 'Tên đăng nhập',
            dataIndex: 'username',
            sorter: true,
            render: (username: string) =>
              username === currentUsername ? (
                <Space size={6}>
                  {username}
                  <Tag color="green">Bạn</Tag>
                </Space>
              ) : (
                username
              ),
          },
          {
            title: 'Vai trò',
            dataIndex: 'roleCode',
            width: 180,
            render: (role: Role) => (
              <Tag color={ROLE_COLOR[role]}>
                {ROLE_LABEL[role]} ({role})
              </Tag>
            ),
          },
          {
            title: 'Trạng thái',
            dataIndex: 'enabled',
            width: 150,
            render: (enabled: boolean) =>
              enabled ? <Tag color="green">Đang hoạt động</Tag> : <Tag color="red">Đã khóa</Tag>,
          },
          {
            title: 'Ngày tạo',
            dataIndex: 'createdAt',
            width: 160,
            sorter: true,
            render: (createdAt: string) => new Date(createdAt).toLocaleDateString('vi-VN'),
          },
          {
            title: 'Thao tác',
            width: 300,
            render: (_: unknown, user: UserResponse) => {
              const isSelf = user.username === currentUsername
              return (
                <Space>
                  <Button
                    size="small"
                    onClick={() => {
                      setEditing(user)
                      setFormOpen(true)
                    }}
                  >
                    Sửa
                  </Button>
                  <Button size="small" onClick={() => setResetting(user)}>
                    Đặt lại mật khẩu
                  </Button>
                  {/* Backend cũng chặn tự khóa; vô hiệu hóa ở đây để người dùng biết trước lý do
                      thay vì bấm xong mới nhận thông báo lỗi. */}
                  <Tooltip title={isSelf ? 'Không thể tự khóa tài khoản đang đăng nhập' : undefined}>
                    <Button
                      size="small"
                      danger={user.enabled}
                      disabled={isSelf}
                      onClick={() => confirmToggleEnabled(user)}
                    >
                      {user.enabled ? 'Khóa' : 'Mở khóa'}
                    </Button>
                  </Tooltip>
                </Space>
              )
            },
          },
        ]}
      />

      <UserFormModal
        open={formOpen}
        user={editing}
        editingSelf={editing?.username === currentUsername}
        onClose={() => setFormOpen(false)}
        onSaved={handleChanged}
      />

      <ResetPasswordModal open={resetting != null} user={resetting} onClose={() => setResetting(null)} />
    </>
  )
}
