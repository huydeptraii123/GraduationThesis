import { App, Form, Input, Modal } from 'antd'
import { useEffect, useState } from 'react'
import { extractErrorMessage } from '../../api/apiError'
import { PASSWORD_MAX_LENGTH, PASSWORD_RULES } from '../auth/passwordRules'
import { resetPassword } from './usersApi'
import type { UserResponse } from './types'

interface Props {
  open: boolean
  user: UserResponse | null
  onClose: () => void
}

/** ADMIN đặt mật khẩu mới cho tài khoản khác — không cần biết mật khẩu cũ (luồng "người dùng quên"). */
export function ResetPasswordModal({ open, user, onClose }: Props) {
  const { message } = App.useApp()
  const [form] = Form.useForm<{ newPassword: string }>()
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (open) {
      form.resetFields()
    }
  }, [open, form])

  async function handleSubmit() {
    if (!user) {
      return
    }
    let values: { newPassword: string }
    try {
      values = await form.validateFields()
    } catch {
      return
    }
    setSaving(true)
    try {
      await resetPassword(user.id, values)
      message.success(`Đã đặt lại mật khẩu cho ${user.username}.`)
      onClose()
    } catch (error) {
      message.error(extractErrorMessage(error, 'Không đặt lại được mật khẩu.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      title={`Đặt lại mật khẩu — ${user?.username ?? ''}`}
      open={open}
      onCancel={onClose}
      onOk={handleSubmit}
      confirmLoading={saving}
      okText="Đặt lại mật khẩu"
      cancelText="Hủy"
      destroyOnHidden
      width={440}
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="newPassword"
          label="Mật khẩu mới"
          extra="Báo lại mật khẩu này cho người dùng và nhắc họ tự đổi sau khi đăng nhập."
          rules={PASSWORD_RULES}
        >
          <Input.Password maxLength={PASSWORD_MAX_LENGTH} autoFocus />
        </Form.Item>
      </Form>
    </Modal>
  )
}
