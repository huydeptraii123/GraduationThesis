import { Button, Layout, Space, Typography } from 'antd'
import { Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../features/auth/AuthContext'

const { Header, Content } = Layout

export function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography.Title level={4} style={{ color: '#fff', margin: 0 }}>
          Cutting Stock Optimization
        </Typography.Title>
        <Space>
          <Typography.Text style={{ color: '#fff' }}>
            {user?.username} ({user?.role})
          </Typography.Text>
          <Button onClick={handleLogout}>Đăng xuất</Button>
        </Space>
      </Header>
      <Content style={{ padding: 24 }}>
        <Outlet />
      </Content>
    </Layout>
  )
}
