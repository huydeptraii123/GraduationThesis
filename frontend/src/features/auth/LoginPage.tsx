import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { Alert, Button, Card, Checkbox, ConfigProvider, Form, Input, Layout, Typography } from 'antd'
import {
  ArrowRightOutlined,
  InfoCircleFilled,
  LockFilled,
  QuestionCircleOutlined,
  ScissorOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { isAxiosError } from 'axios'
import { useAuth } from './AuthContext'

interface LoginFormValues {
  username: string
  password: string
  remember: boolean
}

const PRIMARY = '#1677ff'
const MUTED = '#8c8c8c'

/** Biểu tượng nhà máy — bộ icon của Ant Design không có hình này. */
function FactoryIcon() {
  return (
    <svg width="30" height="30" viewBox="0 0 24 24" fill={PRIMARY} aria-hidden>
      <path d="M2 21V9l5 3V9l5 3V9l5 3V4h4v17H2z" />
    </svg>
  )
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
      await login(values.username, values.password, values.remember)
      navigate('/', { replace: true })
    } catch (err) {
      const fallback = 'Đăng nhập thất bại, vui lòng thử lại.'
      setErrorMessage(isAxiosError(err) && typeof err.response?.data === 'string' ? err.response.data : fallback)
    } finally {
      setLoading(false)
    }
  }

  return (
    <Layout style={{ minHeight: '100vh', background: '#f0f2f5' }}>
      <Layout.Header
        style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', height: 80, paddingInline: 40 }}
      >
        <span style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          <span
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: 40,
              height: 40,
              background: 'rgba(255,255,255,0.2)',
              color: '#fff',
              fontSize: 22,
            }}
          >
            <ScissorOutlined />
          </span>
          <Typography.Title level={3} style={{ color: '#fff', margin: 0 }}>
            Cutting Stock Optimization
          </Typography.Title>
        </span>
        <span style={{ color: '#fff', fontSize: 16, display: 'flex', alignItems: 'center', gap: 8 }}>
          <QuestionCircleOutlined /> Hỗ trợ kỹ thuật: Nội bộ nhà máy
        </span>
      </Layout.Header>

      <Layout.Content style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', padding: '48px 16px' }}>
        <Card style={{ width: 524, border: '1px solid #e8e8e8' }} styles={{ body: { padding: '48px 50px' } }}>
          <div style={{ textAlign: 'center', marginBottom: 32 }}>
            <span
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                width: 60,
                height: 60,
                background: '#e6f4ff',
                marginBottom: 16,
              }}
            >
              <FactoryIcon />
            </span>
            <Typography.Title level={2} style={{ margin: '0 0 8px' }}>
              Đăng nhập hệ thống
            </Typography.Title>
            <Typography.Text style={{ color: MUTED, fontSize: 16 }}>
              Điều độ sản xuất &amp; Tối ưu cắt thanh nan cửa cuốn
            </Typography.Text>
          </div>

          {errorMessage && <Alert type="error" title={errorMessage} style={{ marginBottom: 16 }} showIcon />}

          {/* Nhãn to hơn mặc định và dấu * đặt SAU nhãn — đúng ảnh thiết kế của màn này. */}
          <ConfigProvider theme={{ components: { Form: { labelFontSize: 16 } } }}>
            <Form<LoginFormValues>
              layout="vertical"
              size="large"
              requiredMark={(label, { required }) => (
                <>
                  {label}
                  {required && <span style={{ color: '#ff4d4f', marginInlineStart: 4 }}>*</span>}
                </>
              )}
              initialValues={{ remember: true }}
              onFinish={handleFinish}
            >
              <Form.Item
                name="username"
                label="Tên đăng nhập"
                rules={[{ required: true, message: 'Vui lòng nhập tên đăng nhập' }]}
              >
                <Input prefix={<UserOutlined style={{ color: MUTED }} />} autoFocus autoComplete="username" />
              </Form.Item>
              <Form.Item name="password" label="Mật khẩu" rules={[{ required: true, message: 'Vui lòng nhập mật khẩu' }]}>
                <Input.Password prefix={<LockFilled style={{ color: MUTED }} />} autoComplete="current-password" />
              </Form.Item>

              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
                {/* Có tác dụng thật: bỏ tích thì phiên chỉ thuộc tab này, mất khi đóng tab — xem authStorage.ts. */}
                <Form.Item name="remember" valuePropName="checked" noStyle>
                  <Checkbox>Ghi nhớ đăng nhập</Checkbox>
                </Form.Item>
                <Typography.Text style={{ color: MUTED, fontSize: 14 }}>Hệ thống quản trị nội bộ</Typography.Text>
              </div>

              <Form.Item style={{ marginBottom: 24 }}>
                <Button type="primary" htmlType="submit" loading={loading} block style={{ height: 50, fontSize: 17 }}>
                  Đăng nhập <ArrowRightOutlined />
                </Button>
              </Form.Item>
            </Form>
          </ConfigProvider>

          <div
            style={{
              display: 'flex',
              alignItems: 'flex-start',
              gap: 12,
              padding: '14px 16px',
              background: '#fafafa',
              border: '1px solid #f0f0f0',
              color: '#595959',
              lineHeight: 1.7,
            }}
          >
            <InfoCircleFilled style={{ color: PRIMARY, fontSize: 16, marginTop: 4 }} />
            <span>
              Nếu bạn quên mật khẩu hoặc chưa có tài khoản, vui lòng liên hệ <b>Quản trị viên (ADMIN)</b> của phân
              xưởng để được cấp lại theo quy định bảo mật.
            </span>
          </div>
        </Card>
      </Layout.Content>

      <Layout.Footer style={{ textAlign: 'center', background: '#fff', borderTop: '1px solid #f0f0f0', padding: '20px 16px' }}>
        <div style={{ color: '#595959' }}>
          Hệ thống tối ưu cắt thanh nan cửa cuốn (1D Cutting Stock Problem)
          <span style={{ margin: '0 16px' }}>•</span>
          Phiên bản quản trị nội bộ
        </div>
        <div style={{ color: '#bfbfbf', marginTop: 4 }}>
          Tuân thủ nguyên tắc cân bằng vật liệu &amp; quy trình sản xuất cửa cuốn
        </div>
      </Layout.Footer>
    </Layout>
  )
}
