import { App, Form, Input, Modal, Select } from 'antd'
import { useEffect, useState } from 'react'
import { extractErrorMessage } from '../../api/apiError'
import { PASSWORD_MAX_LENGTH, PASSWORD_RULES } from '../auth/passwordRules'
import { ROLE_LABEL, type Role } from '../auth/permissions'
import { createUser, getUser, updateUser } from './usersApi'
import type { UserResponse } from './types'

const ROLE_OPTIONS = (['ADMIN', 'PLANNER'] as Role[]).map((role) => ({
  value: role,
  label: `${ROLE_LABEL[role]} (${role})`,
}))

interface Props {
  open: boolean
  /** null = tạo mới; có giá trị = sửa tài khoản đang chọn. */
  user: UserResponse | null
  /** true khi dòng đang sửa chính là tài khoản đang đăng nhập — khóa ô vai trò lại. */
  editingSelf: boolean
  onClose: () => void
  onSaved: () => void
}

interface FormValues {
  username: string
  password: string
  roleCode: Role
}

export function UserFormModal({ open, user, editingSelf, onClose, onSaved }: Props) {
  const { message } = App.useApp()
  const [form] = Form.useForm<FormValues>()
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!open) {
      return
    }
    if (user) {
      form.setFieldsValue({ username: user.username, roleCode: user.roleCode })
    } else {
      form.resetFields()
      form.setFieldsValue({ roleCode: 'PLANNER' })
    }
  }, [open, user, form])

  async function handleSubmit() {
    let values: FormValues
    try {
      values = await form.validateFields()
    } catch {
      return // AntD đã hiển thị lỗi ngay tại từng field, không cần báo thêm
    }
    setSaving(true)
    try {
      if (user) {
        // Modal này chỉ đổi vai trò, còn khóa–mở khóa nằm ở nút riêng trên bảng; nhưng backend nhận
        // cả hai trường trong một lần nên vẫn phải gửi kèm `enabled`. Đọc lại trạng thái NGAY TRƯỚC
        // khi lưu thay vì lấy từ ảnh chụp danh sách: nếu một ADMIN khác vừa khóa tài khoản này thì
        // giá trị cũ trong ảnh chụp sẽ âm thầm mở khóa lại, và không ai nhìn thấy điều đó.
        const current = await getUser(user.id)
        await updateUser(user.id, { roleCode: values.roleCode, enabled: current.enabled })
        message.success('Đã cập nhật tài khoản.')
      } else {
        await createUser({ username: values.username, password: values.password, roleCode: values.roleCode })
        message.success('Đã tạo tài khoản mới.')
      }
      onSaved()
      onClose()
    } catch (error) {
      message.error(extractErrorMessage(error, 'Không lưu được tài khoản.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      title={user ? 'Sửa tài khoản' : 'Tạo tài khoản mới'}
      open={open}
      onCancel={onClose}
      onOk={handleSubmit}
      confirmLoading={saving}
      okText={user ? 'Lưu' : 'Tạo tài khoản'}
      cancelText="Hủy"
      destroyOnHidden
      width={480}
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="username"
          label="Tên đăng nhập"
          extra={user ? 'Tên đăng nhập không đổi được sau khi tạo.' : undefined}
          rules={user ? [] : [{ required: true, message: 'Nhập tên đăng nhập' }]}
        >
          {/* maxLength khớp @Size(max = 50) phía backend để chặn ngay tại form */}
          <Input maxLength={50} disabled={user != null} showCount={user == null} />
        </Form.Item>

        {!user && (
          <Form.Item
            name="password"
            label="Mật khẩu tạm thời"
            extra="Người dùng tự đổi lại sau khi đăng nhập lần đầu."
            rules={PASSWORD_RULES}
          >
            <Input.Password maxLength={PASSWORD_MAX_LENGTH} />
          </Form.Item>
        )}

        <Form.Item
          name="roleCode"
          label="Vai trò"
          extra={
            editingSelf
              ? 'Không đổi được vai trò của chính tài khoản đang đăng nhập, tránh tự hạ quyền rồi không còn ai quản trị.'
              : undefined
          }
          rules={[{ required: true, message: 'Chọn vai trò' }]}
        >
          <Select options={ROLE_OPTIONS} disabled={editingSelf} />
        </Form.Item>
      </Form>
    </Modal>
  )
}
