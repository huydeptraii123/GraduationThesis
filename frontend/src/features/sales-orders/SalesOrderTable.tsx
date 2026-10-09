import { PlusOutlined, SearchOutlined, UploadOutlined, WarningOutlined } from '@ant-design/icons'
import { App, Button, DatePicker, Input, Select, Space, Table, Tag, Tooltip } from 'antd'
import dayjs, { type Dayjs } from 'dayjs'
import { useCallback, useMemo, useState } from 'react'
import { extractErrorMessage } from '../../api/apiError'
import { ListLoadError } from '../../components/ListLoadError'
import { tablePagination, type PageParams } from '../../api/pagination'
import { useDebouncedValue } from '../../hooks/useDebouncedValue'
import { usePagedList } from '../../hooks/usePagedList'
import type { DoorProductResponse } from '../bom/types'
import { ImportSalesOrdersModal } from './ImportSalesOrdersModal'
import { SalesOrderFormDrawer } from './SalesOrderFormDrawer'
import { deleteSalesOrder, listSalesOrders } from './salesOrdersApi'
import type { CustomerResponse, SalesOrderProcessingStatus, SalesOrderResponse } from './types'

const { RangePicker } = DatePicker

/**
 * Nhãn và màu của từng trạng thái xử lý — một bảng dùng chung cho cả ô lọc lẫn cột, để hai chỗ
 * không gọi cùng một trạng thái bằng hai tên. Màu theo mockup: chưa xử lý xám, thiếu đỏ, đủ xanh;
 * "Đang bị chặn" không có trong mockup (đặc tả bổ sung sau) nên dùng màu cảnh báo.
 */
const PROCESSING_STATUS: Record<SalesOrderProcessingStatus, { label: string; color: string; hint?: string }> = {
  PENDING: { label: 'Chưa xử lý', color: 'default' },
  BLOCKED: {
    label: 'Đang bị chặn',
    color: 'warning',
    hint: 'Định mức của mẫu cửa chưa đầy đủ (chưa có, hoặc có dòng thiếu tham số tính đoạn cắt) nên đơn chưa vào được phạm vi xử lý — cần ADMIN bổ sung định mức.',
  },
  SUFFICIENT: { label: 'Đủ vật tư', color: 'success' },
  SHORTAGE: { label: 'Thiếu vật tư', color: 'error' },
}

const PROCESSING_STATUS_OPTIONS = (Object.keys(PROCESSING_STATUS) as SalesOrderProcessingStatus[]).map((value) => ({
  value,
  label: PROCESSING_STATUS[value].label,
}))

const APPROVED_ORDER_LOCKED = 'Đơn đã thuộc một phương án cắt được duyệt nên không sửa hay xóa được.'

/** Đơn đã duyệt là bất biến — backend từ chối sửa/xóa bằng 409, nên khóa nút ngay từ đây. */
function isApproved(status: SalesOrderProcessingStatus): boolean {
  return status === 'SUFFICIENT' || status === 'SHORTAGE'
}

function ProcessingStatusTag({ status }: { status: SalesOrderProcessingStatus }) {
  const { label, color, hint } = PROCESSING_STATUS[status]
  const tag = <Tag color={color}>{label}</Tag>
  return hint ? <Tooltip title={hint}>{tag}</Tooltip> : tag
}

interface Props {
  /** Danh sách khách hàng đầy đủ — nguồn cho dropdown lọc và form. */
  customers: CustomerResponse[]
  doorProducts: DoorProductResponse[]
  canEdit: boolean
  canImport: boolean
  /** Báo trang cha nạp lại tổng số đơn và danh mục sau khi bảng này thay đổi dữ liệu. */
  onChanged: () => void
}

type DeliveryUrgency = 'overdue' | 'soon' | 'normal'

/** Quá hạn: trước hôm nay. T+3: trong vòng 3 ngày tới (tính cả hôm nay). Còn lại: bình thường. */
function getDeliveryUrgency(date: string): DeliveryUrgency {
  const today = dayjs().startOf('day')
  const delivery = dayjs(date).startOf('day')
  const daysLeft = delivery.diff(today, 'day')
  if (daysLeft < 0) return 'overdue'
  if (daysLeft <= 3) return 'soon'
  return 'normal'
}

function DeliveryBadge({ date }: { date: string }) {
  const urgency = getDeliveryUrgency(date)
  const formatted = dayjs(date).format('DD/MM/YYYY')
  if (urgency === 'overdue') {
    return (
      <Tag icon={<WarningOutlined />} color="error">
        {formatted} (Quá hạn)
      </Tag>
    )
  }
  if (urgency === 'soon') {
    return (
      <Tag icon={<WarningOutlined />} color="warning">
        {formatted} (T+3)
      </Tag>
    )
  }
  return <span>{formatted}</span>
}

export function SalesOrderTable({
  customers,
  doorProducts,
  canEdit,
  canImport,
  onChanged,
}: Props) {
  const { message, modal } = App.useApp()
  const [keyword, setKeyword] = useState('')
  const [customerFilter, setCustomerFilter] = useState<number | null>(null)
  const [dateRange, setDateRange] = useState<[Dayjs, Dayjs] | null>(null)
  const [statusFilter, setStatusFilter] = useState<SalesOrderProcessingStatus | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [importOpen, setImportOpen] = useState(false)
  const [editing, setEditing] = useState<SalesOrderResponse | null>(null)

  const customerOptions = useMemo(
    () => customers.map((c) => ({ value: c.id, label: c.customerName })),
    [customers],
  )

  const debouncedKeyword = useDebouncedValue(keyword)
  const deliveryFrom = dateRange ? dateRange[0].format('YYYY-MM-DD') : null
  const deliveryTo = dateRange ? dateRange[1].format('YYYY-MM-DD') : null

  // Không sắp lại ở trình duyệt nữa: backend đã trả sẵn theo đúng thứ tự ưu tiên nghiệp vụ
  // (ngày giao → ycsx → bộ cửa), và sắp tại chỗ chỉ đụng được 20 dòng của trang đang xem.
  const load = useCallback(
    (params: PageParams) =>
      listSalesOrders({
        ...params,
        keyword: debouncedKeyword,
        customerId: customerFilter,
        deliveryFrom,
        deliveryTo,
        processingStatus: statusFilter,
      }),
    [debouncedKeyword, customerFilter, deliveryFrom, deliveryTo, statusFilter],
  )
  const { data, loading, error, current, pageSize, handleTableChange, reload } = usePagedList(
    load,
    [debouncedKeyword, customerFilter, deliveryFrom, deliveryTo, statusFilter],
    { errorMessage: 'Không tải được danh sách đơn hàng.' },
  )

  const handleChanged = useCallback(() => {
    reload()
    onChanged()
  }, [reload, onChanged])

  function resetFilters() {
    setKeyword('')
    setCustomerFilter(null)
    setDateRange(null)
    setStatusFilter(null)
  }

  function confirmDelete(order: SalesOrderResponse) {
    modal.confirm({
      title: 'Xóa đơn hàng này?',
      content: `${order.ycsx} · Bộ cửa #${order.item} — ${order.customerName}`,
      okText: 'Xóa',
      okButtonProps: { danger: true },
      cancelText: 'Hủy',
      onOk: async () => {
        try {
          await deleteSalesOrder(order.id)
          message.success('Đã xóa đơn hàng.')
          handleChanged()
        } catch (error) {
          message.error(extractErrorMessage(error, 'Không xóa được đơn hàng.'))
          // Ném lại để AntD giữ hộp thoại mở — đóng lại sẽ khiến người dùng tưởng đã xóa xong.
          throw error
        }
      },
    })
  }

  return (
    <>
      <Space style={{ marginBottom: 16, width: '100%', justifyContent: 'space-between' }} wrap>
        <Space wrap>
          <Input
            allowClear
            placeholder="Tìm theo lệnh sản xuất, khách hàng"
            prefix={<SearchOutlined />}
            style={{ width: 280 }}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
          />
          <RangePicker
            value={dateRange}
            onChange={(value) => setDateRange(value && value[0] && value[1] ? [value[0], value[1]] : null)}
          />
          <Select
            allowClear
            placeholder="Khách hàng: Tất cả"
            style={{ width: 200 }}
            options={customerOptions}
            value={customerFilter}
            onChange={(value) => setCustomerFilter(value ?? null)}
          />
          <Select
            allowClear
            placeholder="Trạng thái: Tất cả"
            style={{ width: 180 }}
            options={PROCESSING_STATUS_OPTIONS}
            value={statusFilter}
            onChange={(value) => setStatusFilter(value ?? null)}
          />
          {(keyword || customerFilter || dateRange || statusFilter) && <Button onClick={resetFilters}>Xóa lọc</Button>}
        </Space>
        <Space wrap>
          {canImport && (
            <Button icon={<UploadOutlined />} onClick={() => setImportOpen(true)}>
              Nhập từ Excel
            </Button>
          )}
          {canEdit && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => {
                setEditing(null)
                setFormOpen(true)
              }}
            >
              Thêm đơn hàng
            </Button>
          )}
        </Space>
      </Space>

      {canEdit && customers.length === 0 && (
        <div style={{ marginBottom: 16, color: '#8c8c8c' }}>
          Chưa có khách hàng nào — khách hàng được tạo tự động khi nhập Excel đơn hàng, chưa thể tạo đơn thủ công.
        </div>
      )}

      <ListLoadError message={error} onRetry={reload} />

      <Table
        rowKey="id"
        loading={loading}
        dataSource={data.content}
        pagination={tablePagination({ current, pageSize, total: data.totalElements }, (total) => `${total} đơn hàng`)}
        onChange={handleTableChange}
        columns={[
          {
            title: 'Lệnh SX / Bộ cửa',
            render: (_, order) => (
              <div>
                <div style={{ fontWeight: 600 }}>{order.ycsx}</div>
                <div style={{ color: '#8c8c8c', fontSize: 12 }}>Bộ cửa #{order.item}</div>
              </div>
            ),
          },
          { title: 'Khách hàng', dataIndex: 'customerName' },
          {
            title: 'Mẫu cửa & Màu',
            render: (_, order) => (
              <div>
                <div>{order.doorProductName}</div>
                <Tag>{order.doorProductMauSac}</Tag>
              </div>
            ),
          },
          {
            title: 'Kích thước (H×W)',
            render: (_, order) => (
              <span>
                {order.chieuCaoDh.toFixed(3)} × {order.chieuRongDh.toFixed(3)} m
              </span>
            ),
          },
          {
            title: 'Ngày giao yêu cầu',
            render: (_, order) => <DeliveryBadge date={order.reqdDeliveryDate} />,
          },
          {
            title: 'Trạng thái',
            render: (_, order) => <ProcessingStatusTag status={order.processingStatus} />,
          },
          ...(canEdit
            ? [
                {
                  title: 'Hành động',
                  width: 140,
                  render: (_: unknown, order: SalesOrderResponse) => {
                    const locked = isApproved(order.processingStatus)
                    return (
                      <Tooltip title={locked ? APPROVED_ORDER_LOCKED : undefined}>
                        <Space>
                          <Button
                            size="small"
                            disabled={locked}
                            onClick={() => {
                              setEditing(order)
                              setFormOpen(true)
                            }}
                          >
                            Sửa
                          </Button>
                          <Button size="small" danger disabled={locked} onClick={() => confirmDelete(order)}>
                            Xóa
                          </Button>
                        </Space>
                      </Tooltip>
                    )
                  },
                },
              ]
            : []),
        ]}
      />

      <SalesOrderFormDrawer
        open={formOpen}
        salesOrder={editing}
        customers={customers}
        doorProducts={doorProducts}
        onClose={() => setFormOpen(false)}
        onSaved={handleChanged}
      />

      <ImportSalesOrdersModal open={importOpen} onClose={() => setImportOpen(false)} onImported={handleChanged} />
    </>
  )
}
