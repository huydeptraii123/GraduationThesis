import { App, Form, Input, Modal } from 'antd'
import { useEffect, useState } from 'react'
import { extractErrorMessage } from '../../api/apiError'
import { changeOwnPassword } from '../users/usersApi'
import { PASSWORD_MAX_LENGTH, PASSWORD_RULES } from './passwordRules'

interface Props {
  open: boolean
  onClose: () => void
}

interface FormValues {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

/**
 * Đổi mật khẩu của chính mình — dùng chung cho cả ADMIN lẫn PLANNER.
 *
 * Ô xác nhận chỉ tồn tại ở phía giao diện: backend không có trường tương ứng, nó chỉ để bắt lỗi gõ
 * nhầm trước khi mật khẩu mới có hiệu lực và khóa người dùng ra ngoài.
 */
export function ChangePasswordModal({ open, onClose }: Props) {
  const { message } = App.useApp()
  const [form] = Form.useForm<FormValues>()
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (open) {
      form.resetFields()
    }
  }, [open, form])

  async function handleSubmit() {
    let values: FormValues
    try {
      values = await form.validateFields()
    } catch {
      return
    }
    setSaving(true)
    try {
      await changeOwnPassword({ currentPassword: values.currentPassword, newPassword: values.newPassword })
      message.success('Đã đổi mật khẩu. Lần đăng nhập sau hãy dùng mật khẩu mới.')
      onClose()
    } catch (error) {
      message.error(extractErrorMessage(error, 'Không đổi được mật khẩu.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      title="Đổi mật khẩu"
      open={open}
      onCancel={onClose}
      onOk={handleSubmit}
      confirmLoading={saving}
      okText="Đổi mật khẩu"
      cancelText="Hủy"
      destroyOnHidden
      width={440}
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="currentPassword"
          label="Mật khẩu hiện tại"
          rules={[{ required: true, message: 'Nhập mật khẩu hiện tại' }]}
        >
          <Input.Password maxLength={PASSWORD_MAX_LENGTH} autoFocus />
        </Form.Item>
        <Form.Item name="newPassword" label="Mật khẩu mới" rules={PASSWORD_RULES}>
          <Input.Password maxLength={PASSWORD_MAX_LENGTH} />
        </Form.Item>
        <Form.Item
          name="confirmPassword"
          label="Nhập lại mật khẩu mới"
          dependencies={['newPassword']}
          rules={[
            { required: true, message: 'Nhập lại mật khẩu mới' },
            ({ getFieldValue }) => ({
              validator: (_, value: string) =>
                !value || value === getFieldValue('newPassword')
                  ? Promise.resolve()
                  : Promise.reject(new Error('Hai mật khẩu chưa khớp nhau')),
            }),
          ]}
        >
          <Input.Password maxLength={PASSWORD_MAX_LENGTH} />
        </Form.Item>
      </Form>
    </Modal>
  )
}
