import { PlusOutlined, SearchOutlined } from '@ant-design/icons'
import { App, Button, Input, Select, Space, Table, Tag, Tooltip } from 'antd'
import { useMemo, useState } from 'react'
import { extractErrorMessage } from '../../api/apiError'
import { ROLE_COLOR, ROLE_LABEL, type Role } from '../auth/permissions'
import { ResetPasswordModal } from './ResetPasswordModal'
import { UserFormModal } from './UserFormModal'
import { updateUser } from './usersApi'
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
  users: UserResponse[]
  /** Tên đăng nhập của người đang xem — dùng để chặn các thao tác tự hại trên chính dòng của mình. */
  currentUsername: string
  loading: boolean
  onChanged: () => void
}

export function UserTable({ users, currentUsername, loading, onChanged }: Props) {
  const { message, modal } = App.useApp()
  const [keyword, setKeyword] = useState('')
  const [roleFilter, setRoleFilter] = useState<Role | null>(null)
  const [statusFilter, setStatusFilter] = useState<string | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [editing, setEditing] = useState<UserResponse | null>(null)
  const [resetting, setResetting] = useState<UserResponse | null>(null)

  const filtered = useMemo(() => {
    const needle = keyword.trim().toLowerCase()
    return users.filter((user) => {
      if (roleFilter && user.roleCode !== roleFilter) {
        return false
      }
      if (statusFilter === 'enabled' && !user.enabled) {
        return false
      }
      if (statusFilter === 'disabled' && user.enabled) {
        return false
      }
      return !needle || user.username.toLowerCase().includes(needle)
    })
  }, [users, keyword, roleFilter, statusFilter])

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
          onChanged()
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

      <Table
        rowKey="id"
        loading={loading}
        dataSource={filtered}
        pagination={{ pageSize: 20, showSizeChanger: true, showTotal: (total) => `${total} tài khoản` }}
        columns={[
          {
            title: 'Tên đăng nhập',
            dataIndex: 'username',
            sorter: (a, b) => a.username.localeCompare(b.username),
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
            sorter: (a, b) => a.createdAt.localeCompare(b.createdAt),
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
        onSaved={onChanged}
      />

      <ResetPasswordModal open={resetting != null} user={resetting} onClose={() => setResetting(null)} />
    </>
  )
}
