import { DatabaseOutlined, HomeOutlined } from '@ant-design/icons'
import { Button, Layout, Menu, Space, Typography } from 'antd'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../features/auth/AuthContext'

const { Header, Content, Sider } = Layout

/** Các màn nghiệp vụ còn lại (BOM, đơn hàng, phương án cắt) sẽ thêm vào đây ở các task sau. */
const MENU_ITEMS = [
  { key: '/', icon: <HomeOutlined />, label: 'Trang chủ' },
  { key: '/inventory', icon: <DatabaseOutlined />, label: 'Tồn kho thanh nan' },
]

/** Khớp chính xác hoặc là tiền tố có dấu `/` để `/inventory` không nhận nhầm `/inventory-report` sau này. */
function isActive(pathname: string, key: string): boolean {
  return key === '/' ? pathname === '/' : pathname === key || pathname.startsWith(`${key}/`)
}

export function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

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
      <Layout>
        <Sider width={220} theme="light">
          <Menu
            mode="inline"
            style={{ height: '100%', borderInlineEnd: 0 }}
            selectedKeys={MENU_ITEMS.filter((item) => isActive(location.pathname, item.key)).map((item) => item.key)}
            items={MENU_ITEMS}
            onClick={({ key }) => navigate(key)}
          />
        </Sider>
        <Content style={{ padding: 24 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}
