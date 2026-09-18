import {
  ArrowRightOutlined,
  CalculatorOutlined,
  DatabaseOutlined,
  FileTextOutlined,
  ScissorOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import { Alert, Button, Card, Col, Row, Space, Spin, Statistic, Table, Tag, Typography } from 'antd'
import dayjs from 'dayjs'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { extractErrorMessage } from '../api/apiError'
import { useAuth } from '../features/auth/AuthContext'
import { RoleRestrictionNotice } from '../components/RoleRestrictionNotice'
import { canGenerateCuttingPlan } from '../features/auth/permissions'
import { GenerateCuttingPlanModal } from '../features/cutting-plans/GenerateCuttingPlanModal'
import type { CuttingPlanStatus } from '../features/cutting-plans/types'
import { WasteStatsSection } from '../features/dashboard/WasteStatsSection'
import { getDashboard } from '../features/dashboard/dashboardApi'
import type { DashboardResponse, WasteTrendPointResponse } from '../features/dashboard/types'

const STATUS_LABEL: Record<CuttingPlanStatus, { text: string; color: string }> = {
  COMPLETED: { text: 'Hoàn tất', color: 'success' },
  FAILED: { text: 'Thất bại', color: 'error' },
}

const SHORTCUTS = [
  {
    path: '/sales-orders',
    icon: <FileTextOutlined />,
    title: 'Quản lý đơn hàng',
    description: 'Theo dõi đơn hàng sản xuất chờ cắt, nhập đơn từ Excel.',
    action: 'Truy cập đơn hàng',
  },
  {
    path: '/inventory',
    icon: <DatabaseOutlined />,
    title: 'Quản lý tồn kho thanh nan',
    description: 'Kiểm soát kho thanh nan nhôm và phần dư đã nhập lại kho.',
    action: 'Kiểm tra kho vật tư',
  },
  {
    path: '/cutting-plans',
    icon: <ScissorOutlined />,
    title: 'Phương án cắt',
    description: 'Sinh phương án cắt tối ưu và xem sơ đồ phôi thanh nan chi tiết.',
    action: 'Xem các phương án',
  },
]

export function HomePage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  // Chỉ PLANNER chạy được thuật toán, khớp @PreAuthorize của POST /cutting-plans/generate.
  const canGenerate = canGenerateCuttingPlan(user)

  const [data, setData] = useState<DashboardResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [generateOpen, setGenerateOpen] = useState(false)

  const latestLoadId = useRef(0)

  const reload = useCallback(async () => {
    const loadId = ++latestLoadId.current
    setLoading(true)
    try {
      const loaded = await getDashboard()
      if (loadId !== latestLoadId.current) {
        return
      }
      setData(loaded)
      setLoadError(null)
    } catch (error) {
      if (loadId !== latestLoadId.current) {
        return
      }
      setLoadError(extractErrorMessage(error, 'Không tải được số liệu trang chủ.'))
    } finally {
      if (loadId === latestLoadId.current) {
        setLoading(false)
      }
    }
  }, [])

  useEffect(() => {
    // oxlint-disable-next-line react/set-state-in-effect
    void reload()
  }, [reload])

  // Biểu đồ cần thứ tự cũ → mới (đúng như backend trả), bảng lịch sử cần mới → cũ.
  const recentPlans = useMemo(() => (data ? [...data.wasteTrend].reverse() : []), [data])

  return (
    <div>
      <Card>
        <Space style={{ width: '100%', justifyContent: 'space-between' }} align="start">
          <div>
            <Typography.Title level={3} style={{ margin: 0 }}>
              Xin chào, {user?.username}
            </Typography.Title>
            <Typography.Text type="secondary">
              Điều độ sản xuất &amp; tối ưu cắt phôi thanh nan nhôm cửa cuốn
            </Typography.Text>
          </div>
          {canGenerate && (
            <Button type="primary" icon={<ThunderboltOutlined />} onClick={() => setGenerateOpen(true)}>
              Sinh phương án cắt mới
            </Button>
          )}
        </Space>
      </Card>

      {data != null && data.ordersMissingBomCount > 0 && (
        <Alert
          type="warning"
          showIcon
          style={{ marginTop: 16 }}
          title={`${data.ordersMissingBomCount} đơn đang bị bỏ qua vì mẫu cửa chưa có định mức BOM.`}
          description={
            <span>
              Thuật toán không sinh được nhu cầu cắt cho những đơn này. Cần tài khoản Quản trị (ADMIN) khai báo định
              mức cho mẫu cửa tương ứng tại <Link to="/bom">Định mức BOM</Link>.
            </span>
          }
        />
      )}

      {!canGenerate && (
        <div style={{ marginTop: 16 }}>
          <RoleRestrictionNotice requiredRole="PLANNER" action="sinh phương án cắt mới" />
        </div>
      )}

      {loadError && <Alert type="error" showIcon style={{ marginTop: 16 }} title={loadError} />}

      <Spin spinning={loading}>
        <Row gutter={16} style={{ marginTop: 16 }}>
          <Col span={8}>
            <Card size="small">
              <Statistic
                title="Đơn hàng chờ xử lý"
                value={data?.pendingOrderCount ?? 0}
                groupSeparator="."
                suffix="đơn"
                prefix={<FileTextOutlined />}
              />
              {data && (
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  Giao đến hết {dayjs(data.scopeCutoffDate).format('DD/MM/YYYY')}
                </Typography.Text>
              )}
            </Card>
          </Col>
          <Col span={8}>
            <Card size="small">
              <Statistic
                title="Lô tồn kho sẵn sàng"
                value={data?.readyBatchCount ?? 0}
                groupSeparator="."
                suffix="lô"
                prefix={<DatabaseOutlined />}
              />
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                {(data?.readyStickCount ?? 0).toLocaleString('vi-VN')} thanh khả dụng
              </Typography.Text>
            </Card>
          </Col>
          <Col span={8}>
            <Card size="small">
              <Statistic
                title="Tỷ lệ phế cộng dồn"
                value={data?.cumulativeWasteRatioPercent ?? 0}
                precision={1}
                suffix="%"
                prefix={<CalculatorOutlined />}
              />
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                {(data?.cumulativeWasteM ?? 0).toFixed(2)} m phế / {(data?.cumulativeStockUsedM ?? 0).toFixed(2)} m tiêu
                hao
              </Typography.Text>
              {data?.latestPlan && (
                <div style={{ marginTop: 4 }}>
                  <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                    Gần nhất{' '}
                    <a onClick={() => navigate(`/cutting-plans/${data.latestPlan?.planId}`)}>
                      #CP-{data.latestPlan.planId}
                    </a>{' '}
                    · {data.latestPlan.wasteRatioPercent.toFixed(1)}%
                  </Typography.Text>
                </div>
              )}
            </Card>
          </Col>
        </Row>

        {data && <WasteStatsSection data={data} />}

        <Row gutter={16} style={{ marginTop: 16 }}>
          {SHORTCUTS.map((shortcut) => (
            <Col span={8} key={shortcut.path}>
              <Card
                size="small"
                title={
                  <Space>
                    {shortcut.icon}
                    {shortcut.title}
                  </Space>
                }
                actions={[
                  <a key="go" onClick={() => navigate(shortcut.path)}>
                    {shortcut.action} <ArrowRightOutlined />
                  </a>,
                ]}
              >
                <Typography.Text type="secondary">{shortcut.description}</Typography.Text>
              </Card>
            </Col>
          ))}
        </Row>

        <Card
          size="small"
          style={{ marginTop: 16 }}
          title="Lịch sử gần đây"
          extra={<a onClick={() => navigate('/cutting-plans')}>Xem tất cả →</a>}
        >
          <Table<WasteTrendPointResponse>
            size="small"
            rowKey="planId"
            pagination={false}
            dataSource={recentPlans}
            locale={{ emptyText: 'Chưa có lần chạy nào' }}
            columns={[
              {
                title: 'Mã phương án',
                render: (_, plan) => <Tag>#CP-{plan.planId}</Tag>,
              },
              {
                title: 'Thời gian',
                dataIndex: 'runAt',
                render: (value: string) => dayjs(value).format('DD/MM/YYYY HH:mm'),
              },
              {
                title: 'Số đơn',
                dataIndex: 'scopeOrderCount',
                align: 'right',
              },
              {
                title: 'Tỷ lệ phế',
                align: 'right',
                render: (_, plan) => `${plan.wasteRatioPercent.toFixed(1)}% / ${plan.totalWasteM.toFixed(2)} m`,
              },
              {
                title: 'Trạng thái',
                align: 'center',
                render: (_, plan) => (
                  <Tag color={STATUS_LABEL[plan.status].color}>{STATUS_LABEL[plan.status].text}</Tag>
                ),
              },
              {
                title: 'Hành động',
                align: 'right',
                render: (_, plan) => <a onClick={() => navigate(`/cutting-plans/${plan.planId}`)}>Xem chi tiết →</a>,
              },
            ]}
          />
        </Card>
      </Spin>

      <GenerateCuttingPlanModal
        open={generateOpen}
        onClose={() => setGenerateOpen(false)}
        onGenerated={(newPlanId) => {
          setGenerateOpen(false)
          void reload()
          navigate(`/cutting-plans/${newPlanId}`)
        }}
      />
    </div>
  )
}
