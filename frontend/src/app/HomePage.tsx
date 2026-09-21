import {
  ArrowRightOutlined,
  CalculatorOutlined,
  DatabaseOutlined,
  DownloadOutlined,
  FileTextOutlined,
  ScissorOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import { App, Alert, Button, Card, Col, Empty, Row, Space, Spin, Typography } from 'antd'
import dayjs from 'dayjs'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { extractBlobErrorMessage, extractErrorMessage } from '../api/apiError'
import { downloadFile } from '../api/downloadFile'
import { RoleRestrictionNotice } from '../components/RoleRestrictionNotice'
import { useAuth } from '../features/auth/AuthContext'
import { canApproveCuttingPlan } from '../features/auth/permissions'
import { exportSimulation } from '../features/cutting-plans/cuttingPlansApi'
import type { CuttingPlanPreviewResponse } from '../features/cutting-plans/types'
import { SimulationDashboard } from '../features/dashboard/SimulationDashboard'
import {
  clearLastSimulation,
  getLastSimulation,
  getPendingSimulation,
  runSimulation,
} from '../features/dashboard/simulationStore'

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
    title: 'Phương án đã duyệt',
    description: 'Tra cứu lịch sử các đợt duyệt và số liệu phế liệu cộng dồn.',
    action: 'Xem lịch sử',
  },
]

/**
 * Trang chủ = màn **Tính phương án cắt**: chạy thuật toán trên toàn bộ đơn chưa duyệt để thấy sớm
 * bức tranh thiếu hụt vật tư, KHÔNG ghi bất kỳ dữ liệu nào.
 *
 * Màn hình cố ý **không tự tính khi mở trang**: một lần tính chạy thuật toán trên toàn bộ sổ đơn,
 * và con số chỉ có nghĩa với trạng thái tại đúng thời điểm bấm — tự chạy nền sẽ vừa tốn vừa khiến
 * người dùng đọc một con số không rõ tính từ lúc nào. Kết quả giữ ở bộ nhớ module nên chuyển trang
 * qua lại không mất, nhưng cũng không bao giờ tự làm mới.
 *
 * Số liệu cộng dồn qua các đợt đã duyệt nằm ở màn "Phương án đã duyệt", không phải ở đây: trộn hai
 * loại số trên cùng một màn hình thì "tỷ lệ phế" của lần tính này đứng cạnh "tỷ lệ phế cộng dồn"
 * và không ai đọc ra chúng khác nhau.
 */
export function HomePage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const { message } = App.useApp()
  // Chỉ PLANNER duyệt được phương án cắt, khớp @PreAuthorize của POST /cutting-plans/approve.
  // Việc TÍNH thì mở cho cả hai vai trò nên không có vị từ quyền riêng.
  const canApprove = canApproveCuttingPlan(user)

  const [simulation, setSimulation] = useState<CuttingPlanPreviewResponse | null>(() => getLastSimulation())
  const [computing, setComputing] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)

  const latestRunId = useRef(0)

  const compute = useCallback(async () => {
    const runId = ++latestRunId.current
    setComputing(true)
    try {
      const computed = await runSimulation()
      if (runId !== latestRunId.current) {
        return
      }
      setSimulation(computed)
      setLoadError(null)
    } catch (error) {
      if (runId !== latestRunId.current) {
        return
      }
      // Xóa hẳn kết quả cũ: giữ lại nghĩa là màn hình hiện số liệu của một lần tính khác trong khi
      // nhãn lỗi nói lần này thất bại — người đọc không có cách nào biết mình đang nhìn cái nào.
      clearLastSimulation()
      setSimulation(null)
      setLoadError(extractErrorMessage(error, 'Không tính được phương án cắt.'))
    } finally {
      if (runId === latestRunId.current) {
        setComputing(false)
      }
    }
  }, [])

  // Nối lại vào lượt tính đang chạy dở khi người dùng rời trang giữa chừng rồi quay lại. Không có
  // bước này thì màn hình hiện trạng thái trống trong lúc máy chủ vẫn đang chạy, và người dùng bấm
  // tính thêm một lượt nữa cho cùng một câu hỏi. Cố ý KHÔNG tự chạy lượt mới ở đây.
  useEffect(() => {
    if (getPendingSimulation() == null) {
      return
    }
    // Đây đúng là trường hợp effect dùng để đồng bộ với một nguồn bên ngoài React (bộ nhớ module
    // đang giữ lượt tính dở), nên việc đặt state ngay trong effect là có chủ đích.
    // oxlint-disable-next-line react/set-state-in-effect
    void compute()
  }, [compute])

  const exportExcel = useCallback(async () => {
    setExporting(true)
    try {
      const blob = await exportSimulation()
      downloadFile(blob, `phuong-an-cat-du-kien-${dayjs().format('YYYYMMDD-HHmmss')}.xlsx`)
    } catch (error) {
      message.error(await extractBlobErrorMessage(error, 'Không xuất được file Excel.'))
    } finally {
      setExporting(false)
    }
  }, [message])

  return (
    <div>
      <Card>
        <Space style={{ width: '100%', justifyContent: 'space-between' }} align="start">
          <div>
            <Typography.Title level={3} style={{ margin: 0 }}>
              Tính phương án cắt
            </Typography.Title>
            <Typography.Text type="secondary">
              Toàn bộ đơn hàng chưa thuộc phương án nào được duyệt, tính trên tồn kho tại thời điểm bấm
            </Typography.Text>
          </div>
          <Space>
            <Button
              type="primary"
              icon={<CalculatorOutlined />}
              loading={computing}
              onClick={() => void compute()}
            >
              Tính phương án cắt
            </Button>
            <Button
              icon={<DownloadOutlined />}
              loading={exporting}
              disabled={simulation == null}
              onClick={() => void exportExcel()}
            >
              Xuất Excel
            </Button>
            {canApprove && (
              <Button icon={<ThunderboltOutlined />} onClick={() => navigate('/cutting-plans/approval')}>
                Duyệt phương án cắt
              </Button>
            )}
          </Space>
        </Space>
        {simulation != null && (
          <Typography.Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 8 }}>
            Tính lúc {dayjs(simulation.computedAt).format('HH:mm:ss DD/MM/YYYY')}. File Excel được máy chủ tính lại
            ngay lúc tải, nên nếu đơn hàng hoặc tồn kho vừa thay đổi thì số liệu trong file có thể khác màn hình.
          </Typography.Text>
        )}
      </Card>

      {simulation != null && simulation.blockedOrderCount > 0 && (
        <Alert
          type="warning"
          showIcon
          style={{ marginTop: 16 }}
          title={`${simulation.blockedOrderCount} đơn đang bị bỏ qua vì mẫu cửa chưa có định mức BOM.`}
          description={
            <span>
              Thuật toán không sinh được nhu cầu cắt cho những đơn này nên chúng không nằm trong các con số bên dưới.
              Cần tài khoản Quản trị (ADMIN) khai báo định mức cho mẫu cửa tương ứng tại{' '}
              <Link to="/bom">Định mức BOM</Link>.
            </span>
          }
        />
      )}

      {!canApprove && (
        <div style={{ marginTop: 16 }}>
          <RoleRestrictionNotice requiredRole="PLANNER" action="duyệt phương án cắt" />
        </div>
      )}

      {loadError && <Alert type="error" showIcon style={{ marginTop: 16 }} title={loadError} />}

      <Spin spinning={computing}>
        {simulation == null ? (
          <Card size="small" style={{ marginTop: 16 }}>
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={
                <Space direction="vertical" size={4}>
                  <Typography.Text strong>Chưa có số liệu</Typography.Text>
                  <Typography.Text type="secondary">
                    Bấm <b>Tính phương án cắt</b> để chạy thuật toán trên toàn bộ đơn hàng đang chờ và xem còn thiếu
                    những loại thanh nan nào. Thao tác này chỉ đọc dữ liệu — không trừ tồn kho và không đánh dấu đơn
                    hàng nào.
                  </Typography.Text>
                </Space>
              }
            />
          </Card>
        ) : (
          <SimulationDashboard simulation={simulation} />
        )}

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
      </Spin>
    </div>
  )
}
