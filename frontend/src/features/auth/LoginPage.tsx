import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { Alert, Button, Card, Form, Input, Typography } from 'antd'
import { isAxiosError } from 'axios'
import { useAuth } from './AuthContext'

interface LoginFormValues {
  username: string
  password: string
}

export function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  if (user) {
    return <Navigate to="/" replace />
  }

  async function handleFinish(values: LoginFormValues) {
    setLoading(true)
    setErrorMessage(null)
    try {
      await login(values.username, values.password)
      navigate('/', { replace: true })
    } catch (err) {
      const fallback = 'Đăng nhập thất bại, vui lòng thử lại.'
      setErrorMessage(isAxiosError(err) && typeof err.response?.data === 'string' ? err.response.data : fallback)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh' }}>
      <Card style={{ width: 360 }}>
        <Typography.Title level={3} style={{ textAlign: 'center' }}>
          Đăng nhập
        </Typography.Title>
        {errorMessage && <Alert type="error" title={errorMessage} style={{ marginBottom: 16 }} showIcon />}
        <Form<LoginFormValues> layout="vertical" onFinish={handleFinish}>
          <Form.Item name="username" label="Tên đăng nhập" rules={[{ required: true, message: 'Vui lòng nhập tên đăng nhập' }]}>
            <Input autoFocus />
          </Form.Item>
          <Form.Item name="password" label="Mật khẩu" rules={[{ required: true, message: 'Vui lòng nhập mật khẩu' }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={loading} block>
              Đăng nhập
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  )
}
