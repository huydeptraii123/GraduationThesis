import {
  CalculatorOutlined,
  CheckCircleOutlined,
  DatabaseOutlined,
  DownOutlined,
  FileTextOutlined,
  HomeOutlined,
  KeyOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  ScissorOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import { Avatar, Breadcrumb, Dropdown, Layout, Menu, Tag, Typography } from 'antd'
import { useState, type ReactNode } from 'react'
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
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
    key: '/cutting-plans/approval',
    icon: <CheckCircleOutlined />,
    label: 'Duyệt phương án cắt',
    editorRole: 'PLANNER',
    editorHint: 'Chỉ PLANNER duyệt được phương án cắt — đây là thao tác làm thay đổi tồn kho.',
  },
  // Không gắn nhãn vai trò: lịch sử là màn chỉ-đọc, cả hai vai trò đều xem và xuất Excel được.
  { key: '/cutting-plans', icon: <ScissorOutlined />, label: 'Phương án đã duyệt' },
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

const SIDER_WIDTH = 220

function menuLabel(item: (typeof MENU_ITEMS)[number]): ReactNode {
  if (!item.editorRole) {
    return item.label
  }
  // Tag không được co lại: nhãn dài nhất ("Duyệt phương án cắt") cộng tag vừa khít bề ngang menu,
  // và để flexbox tự co thì chính cái tag bị cắt cụt mất chữ — đúng lỗi từng thấy.
  return (
    <span style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 6 }}>
      <span style={{ minWidth: 0 }}>{item.label}</span>
      <Tag
        color={ROLE_COLOR[item.editorRole]}
        style={{ marginInlineEnd: 0, fontSize: 10, lineHeight: '16px', paddingInline: 4, flex: 'none' }}
        title={item.editorHint ?? `Chỉ ${item.editorRole} sửa được`}
      >
        {ROLE_SHORT_LABEL[item.editorRole]}
      </Tag>
    </span>
  )
}

const MENU_LABEL_BY_KEY = new Map(MENU_ITEMS.map((item) => [item.key, item.label]))

/**
 * Đường dẫn → breadcrumb. Nhãn lấy lại từ chính `MENU_ITEMS` để menu và breadcrumb không bao giờ
 * gọi cùng một màn bằng hai tên khác nhau. Chỉ màn chi tiết phương án là cấp con không có mục menu
 * riêng, nên tự dựng thêm một hoặc hai mức.
 */
function breadcrumbItems(pathname: string, items: { key: string; label: string }[]) {
  const home = { title: <Link to="/">Trang chủ</Link> }
  if (pathname === '/') {
    return [{ title: 'Trang chủ' }]
  }
  const planDetail = /^\/cutting-plans\/(\d+)(\/shortages)?$/.exec(pathname)
  if (planDetail) {
    const [, id, shortages] = planDetail
    const detailLabel = `Chi tiết #CP-${id}`
    return [
      home,
      { title: <Link to="/cutting-plans">{MENU_LABEL_BY_KEY.get('/cutting-plans')}</Link> },
      shortages
        ? { title: <Link to={`/cutting-plans/${id}`}>{detailLabel}</Link> }
        : { title: detailLabel },
      ...(shortages ? [{ title: 'Đơn thiếu vật tư' }] : []),
    ]
  }
  const [current] = selectedMenuKeys(pathname, items)
  const label = items.find((item) => item.key === current)?.label
  return label ? [home, { title: label }] : [home]
}


/** Khớp chính xác hoặc là tiền tố có dấu `/` để `/inventory` không nhận nhầm `/inventory-report` sau này. */
function isActive(pathname: string, key: string): boolean {
  return key === '/' ? pathname === '/' : pathname === key || pathname.startsWith(`${key}/`)
}

/**
 * Chỉ sáng đúng MỘT mục menu. Từ khi có màn duyệt phương án, hai mục cùng khớp một đường dẫn:
 * `/cutting-plans/approval` khớp chính mục đó, đồng thời khớp `/cutting-plans` theo luật tiền tố.
 * Mục có khóa dài hơn là mục cụ thể hơn, và là nơi người dùng thật sự đang đứng.
 */
function selectedMenuKeys(pathname: string, items: { key: string }[]): string[] {
  const matched = items.filter((item) => isActive(pathname, item.key))
  if (matched.length === 0) {
    return []
  }
  return [matched.reduce((best, item) => (item.key.length > best.key.length ? item : best)).key]
}

export function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [changePasswordOpen, setChangePasswordOpen] = useState(false)
  const [collapsed, setCollapsed] = useState(false)

  const visibleMenuItems = MENU_ITEMS.filter((item) => item.visible?.(user) ?? true)

  function handleLogout() {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingInline: 24 }}>
        <Typography.Title level={4} style={{ color: '#fff', margin: 0 }}>
          Cutting Stock Optimization
        </Typography.Title>
        {user && (
          <Dropdown
            trigger={['click']}
            placement="bottomRight"
            menu={{
              items: [
                { key: 'password', icon: <KeyOutlined />, label: 'Đổi mật khẩu' },
                { type: 'divider' },
                { key: 'logout', icon: <LogoutOutlined />, label: 'Đăng xuất', danger: true },
              ],
              onClick: ({ key }) => (key === 'password' ? setChangePasswordOpen(true) : handleLogout()),
            }}
          >
            {/* Nền sáng bán trong suốt để tag vai trò (vốn có màu xanh) vẫn đọc được trên header xanh. */}
            <button
              type="button"
              aria-label="Menu tài khoản"
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                height: 40,
                padding: '0 12px',
                border: '1px solid rgba(255,255,255,0.35)',
                background: 'rgba(255,255,255,0.15)',
                color: '#fff',
                cursor: 'pointer',
                font: 'inherit',
              }}
            >
              <Avatar size={26} style={{ background: '#fff', color: '#1677ff', fontWeight: 600 }}>
                {user.username.charAt(0).toUpperCase()}
              </Avatar>
              <span>{user.username}</span>
              <Tag color={ROLE_COLOR[user.role]} style={{ marginInlineEnd: 0 }}>
                {user.role}
              </Tag>
              <DownOutlined style={{ fontSize: 10 }} />
            </button>
          </Dropdown>
        )}
      </Header>
      <Layout>
        <Sider
          width={SIDER_WIDTH}
          theme="light"
          collapsible
          collapsed={collapsed}
          onCollapse={setCollapsed}
          style={{ borderInlineEnd: '1px solid #f0f0f0' }}
          trigger={
            <span style={{ fontSize: 13 }}>
              {collapsed ? <MenuUnfoldOutlined /> : <><MenuFoldOutlined /> Thu gọn menu</>}
            </span>
          }
        >
          <Menu
            mode="inline"
            inlineIndent={16}
            style={{ borderInlineEnd: 0 }}
            selectedKeys={selectedMenuKeys(location.pathname, visibleMenuItems)}
            items={visibleMenuItems.map((item) => ({ key: item.key, icon: item.icon, label: menuLabel(item) }))}
            onClick={({ key }) => navigate(key)}
          />
        </Sider>
        <Content style={{ padding: 24 }}>
          <Breadcrumb style={{ marginBottom: 16 }} items={breadcrumbItems(location.pathname, visibleMenuItems)} />
          <Outlet />
        </Content>
      </Layout>

      <ChangePasswordModal open={changePasswordOpen} onClose={() => setChangePasswordOpen(false)} />
    </Layout>
  )
}
