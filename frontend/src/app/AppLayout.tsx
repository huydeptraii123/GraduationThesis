import {
  CalculatorOutlined,
  DatabaseOutlined,
  FileTextOutlined,
  HomeOutlined,
  ScissorOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import { Button, Layout, Menu, Space, Tag, Typography } from 'antd'
import { useState, type ReactNode } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../features/auth/AuthContext'
import { ChangePasswordModal } from '../features/auth/ChangePasswordModal'
import {
  ROLE_COLOR,
  ROLE_SHORT_LABEL,
  canManageUsers,
  type Role,
  type RoleHolder,
} from '../features/auth/permissions'

const { Header, Content, Sider } = Layout

/**
 * `editorRole` chỉ là nhãn cho biết vai trò nào ĐƯỢC GHI trên màn đó — mọi mục vẫn hiển thị cho cả
 * hai vai trò vì GET của mọi module đều mở (đúng mockup, và cũng đúng backend). Mục "Đơn hàng" và
 * "Phương án cắt" không gắn nhãn: cả hai vai trò đều thao tác được ở mức nào đó.
 */
const MENU_ITEMS: {
  key: string
  icon: ReactNode
  label: string
  editorRole?: Role
  /** Ghi đè chú thích khi màn hình có nhiều hơn 1 luật quyền, để nhãn rút gọn không nói sai. */
  editorHint?: string
  /**
   * Điều kiện hiện mục. Bỏ trống = hiện với mọi vai trò (mặc định, vì GET của các module nghiệp vụ
   * đều mở). Nhận thẳng vị từ từ `permissions.ts` để luật quyền vẫn nằm đúng một chỗ.
   */
  visible?: (user: RoleHolder | null) => boolean
}[] = [
  { key: '/', icon: <HomeOutlined />, label: 'Trang chủ' },
  { key: '/sales-orders', icon: <FileTextOutlined />, label: 'Đơn hàng' },
  {
    key: '/inventory',
    icon: <DatabaseOutlined />,
    label: 'Tồn kho thanh nan',
    editorRole: 'PLANNER',
    editorHint: 'Lô tồn kho: chỉ PLANNER sửa được. Danh mục loại thanh nan: cả ADMIN lẫn PLANNER.',
  },
  { key: '/bom', icon: <CalculatorOutlined />, label: 'Định mức BOM', editorRole: 'ADMIN' },
  {
    key: '/cutting-plans',
    icon: <ScissorOutlined />,
    label: 'Phương án cắt',
    editorRole: 'PLANNER',
    editorHint: 'Chỉ PLANNER sinh được phương án cắt mới; cả hai vai trò đều xem và xuất Excel được.',
  },
  // Mục duy nhất bị ẩn hẳn theo vai trò, không chỉ khóa nút ghi: endpoint danh sách tài khoản là
  // ADMIN-only nên PLANNER vào cũng chỉ nhận về một trang trống kèm lỗi quyền.
  {
    key: '/users',
    icon: <TeamOutlined />,
    label: 'Người dùng',
    editorRole: 'ADMIN',
    editorHint: 'Chỉ ADMIN xem và quản lý được tài khoản người dùng.',
    visible: canManageUsers,
  },
]

function menuLabel(item: (typeof MENU_ITEMS)[number]): ReactNode {
  if (!item.editorRole) {
    return item.label
  }
  return (
    <span style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
      {item.label}
      <Tag
        color={ROLE_COLOR[item.editorRole]}
        style={{ marginInlineEnd: 0, fontSize: 10, lineHeight: '16px' }}
        title={item.editorHint ?? `Chỉ ${item.editorRole} sửa được`}
      >
        {ROLE_SHORT_LABEL[item.editorRole]}
      </Tag>
    </span>
  )
}

/** Khớp chính xác hoặc là tiền tố có dấu `/` để `/inventory` không nhận nhầm `/inventory-report` sau này. */
function isActive(pathname: string, key: string): boolean {
  return key === '/' ? pathname === '/' : pathname === key || pathname.startsWith(`${key}/`)
}

export function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [changePasswordOpen, setChangePasswordOpen] = useState(false)

  const visibleMenuItems = MENU_ITEMS.filter((item) => item.visible?.(user) ?? true)

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
          <Typography.Text style={{ color: '#fff' }}>{user?.username}</Typography.Text>
          {user && <Tag color={ROLE_COLOR[user.role]}>{user.role}</Tag>}
          <Button onClick={() => setChangePasswordOpen(true)}>Đổi mật khẩu</Button>
          <Button onClick={handleLogout}>Đăng xuất</Button>
        </Space>
      </Header>
      <Layout>
        <Sider width={220} theme="light">
          <Menu
            mode="inline"
            style={{ height: '100%', borderInlineEnd: 0 }}
            selectedKeys={visibleMenuItems
              .filter((item) => isActive(location.pathname, item.key))
              .map((item) => item.key)}
            items={visibleMenuItems.map((item) => ({ key: item.key, icon: item.icon, label: menuLabel(item) }))}
            onClick={({ key }) => navigate(key)}
          />
        </Sider>
        <Content style={{ padding: 24 }}>
          <Outlet />
        </Content>
      </Layout>

      <ChangePasswordModal open={changePasswordOpen} onClose={() => setChangePasswordOpen(false)} />
    </Layout>
  )
}
