import { ClearOutlined } from '@ant-design/icons'
import { Alert, App, Button, Card, Col, Input, Modal, Row, Statistic, Typography } from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import { extractErrorMessage } from '../../api/apiError'
import { useAuth } from '../auth/AuthContext'
import { canResetOperationalData } from '../auth/permissions'
import { getBomSummary, listDoorProducts } from '../bom/bomApi'
import { listCuttingPlans } from '../cutting-plans/cuttingPlansApi'
import { clearLastSimulation } from '../dashboard/simulationStore'
import { getInventorySummary } from '../inventory/inventoryApi'
import { listCustomers, listSalesOrders } from '../sales-orders/salesOrdersApi'
import { DATA_RESET_CONFIRMATION_PHRASE, resetOperationalData } from './dataResetApi'

interface Counts {
  salesOrders: number
  customers: number
  inventoryBatches: number
  cuttingPlans: number
  bomItems: number
  doorProducts: number
}

const formatCount = (value: number) => value.toLocaleString('vi-VN')

const DELETED_TILES: [keyof Counts, string][] = [
  ['salesOrders', 'Đơn hàng'],
  ['customers', 'Khách hàng'],
  ['inventoryBatches', 'Lô tồn kho'],
  ['cuttingPlans', 'Phương án đã duyệt'],
]

const KEPT_TILES: [keyof Counts, string][] = [
  ['bomItems', 'Dòng định mức'],
  ['doorProducts', 'Mẫu cửa'],
]

/**
 * Chưa có số (đang tải hoặc tải lỗi) thì hiện "—", KHÔNG hiện 0: một ô "Đơn hàng 0" lúc tải lỗi đọc
 * như "không có gì để xóa" ngay trước một thao tác không hoàn tác được.
 */
function CountTile({ title, value, loading }: { title: string; value: number | undefined; loading: boolean }) {
  return <Statistic title={title} value={value ?? '—'} groupSeparator="." loading={loading} />
}

/**
 * Đặt lại dữ liệu vận hành theo yêu cầu của doanh nghiệp: đơn hàng hôm nay có thể bị khách đổi kích
 * thước ngày mai, nên cần xóa sạch để nhập lại từ đầu — giữ nguyên định mức, mẫu cửa, loại thanh nan
 * và tài khoản.
 *
 * Hai lớp xác nhận ở giao diện (bấm nút mở hộp thoại cảnh báo, rồi gõ đúng cụm xác nhận mới mở được
 * nút xóa) cộng một lớp ở máy chủ (kiểm lại chính cụm đó). Số dòng hiện trên màn là số sẽ mất, đọc
 * từ các endpoint tổng hợp sẵn có, không cần endpoint riêng.
 */
export function DataResetPage() {
  const { user } = useAuth()
  const { message } = App.useApp()
  const canReset = canResetOperationalData(user)

  const [counts, setCounts] = useState<Counts | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [typedPhrase, setTypedPhrase] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  // Đánh số từng lượt nạp: lượt cũ về sau không được ghi đè số của lượt mới (vd ngay sau khi xóa).
  const latestLoadId = useRef(0)

  const reload = useCallback(async () => {
    if (!canReset) {
      setLoading(false)
      return
    }
    const loadId = ++latestLoadId.current
    setLoading(true)
    try {
      // Mẫu cửa đếm qua danh sách đầy đủ chứ không qua tổng hợp định mức: tổng hợp đó chỉ đếm mẫu cửa
      // CÓ dòng định mức, trong khi nhập đơn hàng cũng tạo mẫu cửa — và nút này giữ lại tất cả.
      const [orders, customers, inventory, plans, bom, doorProducts] = await Promise.all([
        listSalesOrders({ page: 0, size: 1 }),
        listCustomers(),
        getInventorySummary(),
        listCuttingPlans({ page: 0, size: 1 }),
        getBomSummary(),
        listDoorProducts(),
      ])
      if (loadId !== latestLoadId.current) {
        return
      }
      setCounts({
        salesOrders: orders.totalElements,
        customers: customers.length,
        inventoryBatches: inventory.batchCount,
        cuttingPlans: plans.totalElements,
        bomItems: bom.totalItems,
        doorProducts: doorProducts.length,
      })
      setLoadError(null)
    } catch (error) {
      if (loadId !== latestLoadId.current) {
        return
      }
      setLoadError(extractErrorMessage(error, 'Không tải được số liệu hiện có.'))
    } finally {
      if (loadId === latestLoadId.current) {
        setLoading(false)
      }
    }
  }, [canReset])

  useEffect(() => {
    // Nạp dữ liệu lần đầu từ backend — cờ loading buộc phải bật ngay trước khi gọi API.
    // oxlint-disable-next-line react/set-state-in-effect
    void reload()
  }, [reload])

  const openConfirm = () => {
    setTypedPhrase('')
    setSubmitError(null)
    setConfirmOpen(true)
  }

  const handleReset = async () => {
    setSubmitting(true)
    setSubmitError(null)
    try {
      const result = await resetOperationalData(typedPhrase)
      // Kết quả lần tính đang giữ ở trang chủ / màn báo cáo thiếu nay mô tả dữ liệu không còn tồn tại.
      clearLastSimulation()
      setConfirmOpen(false)
      message.success(
        `Đã xóa ${formatCount(result.salesOrders)} đơn hàng, ${formatCount(result.customers)} khách hàng, `
          + `${formatCount(result.inventoryBatches)} lô tồn kho và ${formatCount(result.cuttingPlans)} phương án `
          + 'đã duyệt. Định mức được giữ nguyên.',
      )
      void reload()
    } catch (error) {
      setSubmitError(extractErrorMessage(error, 'Không đặt lại được dữ liệu.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div>
      <Typography.Title level={3} style={{ marginTop: 0 }}>
        Đặt lại dữ liệu
      </Typography.Title>
      <div>
        <Typography.Text type="secondary">
          Xóa sạch đơn hàng, khách hàng, tồn kho thanh nan và toàn bộ lịch sử duyệt phương án cắt để nhập lại từ
          đầu. Định mức BOM, mẫu cửa, danh mục loại thanh nan và tài khoản người dùng được giữ nguyên.
        </Typography.Text>
      </div>

      {!canReset ? (
        <div style={{ marginTop: 16 }}>
          <Alert type="info" showIcon title="Tài khoản hiện tại không có quyền đặt lại dữ liệu." />
        </div>
      ) : (
        <>
          {loadError && <Alert type="error" showIcon style={{ marginTop: 16 }} title={loadError} />}

          <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
            <Col xs={24} xl={15}>
              <Card size="small" title="Sẽ bị xóa vĩnh viễn">
                <Row gutter={16}>
                  {DELETED_TILES.map(([key, title]) => (
                    <Col xs={12} md={6} key={key}>
                      <CountTile title={title} value={counts?.[key]} loading={loading} />
                    </Col>
                  ))}
                </Row>
              </Card>
            </Col>
            <Col xs={24} xl={9}>
              <Card size="small" title="Giữ nguyên">
                <Row gutter={16}>
                  {KEPT_TILES.map(([key, title]) => (
                    <Col span={12} key={key}>
                      <CountTile title={title} value={counts?.[key]} loading={loading} />
                    </Col>
                  ))}
                </Row>
              </Card>
            </Col>
          </Row>

          <Card size="small" style={{ marginTop: 16, borderColor: '#ffccc7' }}>
            <Typography.Paragraph style={{ marginBottom: 12 }}>
              Thao tác này <b>không thể hoàn tác</b>. Sau khi đặt lại, nhập lại dữ liệu theo đúng thứ tự: tồn kho
              thanh nan, rồi đơn hàng (định mức vẫn còn nên không cần nhập lại).
            </Typography.Paragraph>
            <Button danger type="primary" icon={<ClearOutlined />} onClick={openConfirm}>
              Đặt lại dữ liệu tồn kho và đơn hàng…
            </Button>
          </Card>

          <Modal
            title="Xác nhận đặt lại dữ liệu"
            open={confirmOpen}
            onOk={() => void handleReset()}
            onCancel={() => setConfirmOpen(false)}
            okText="Xóa vĩnh viễn"
            cancelText="Hủy"
            okButtonProps={{ danger: true, disabled: typedPhrase !== DATA_RESET_CONFIRMATION_PHRASE }}
            confirmLoading={submitting}
            destroyOnHidden
          >
            <Alert
              type="error"
              showIcon
              title="Toàn bộ đơn hàng, khách hàng, tồn kho và lịch sử duyệt sẽ bị xóa vĩnh viễn."
              description="Định mức BOM, mẫu cửa, danh mục loại thanh nan và tài khoản người dùng được giữ nguyên."
              style={{ marginBottom: 16 }}
            />
            <Typography.Paragraph style={{ marginBottom: 8 }}>
              Gõ <Typography.Text code>{DATA_RESET_CONFIRMATION_PHRASE}</Typography.Text> để xác nhận:
            </Typography.Paragraph>
            <Input
              value={typedPhrase}
              onChange={(event) => setTypedPhrase(event.target.value)}
              placeholder={DATA_RESET_CONFIRMATION_PHRASE}
              autoComplete="off"
              aria-label="Cụm xác nhận"
            />
            {submitError && <Alert type="error" showIcon style={{ marginTop: 16 }} title={submitError} />}
          </Modal>
        </>
      )}
    </div>
  )
}
