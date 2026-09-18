import { Alert, Card, Space, Statistic, Typography } from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import { extractErrorMessage } from '../../api/apiError'
import type { DoorProductResponse } from '../bom/types'
import { useAuth } from '../auth/AuthContext'
import { RoleRestrictionNotice } from '../../components/RoleRestrictionNotice'
import { canEditSalesOrder, canImportSalesOrder } from '../auth/permissions'
import { SalesOrderTable } from './SalesOrderTable'
import { listCustomers, listDoorProducts, listSalesOrders } from './salesOrdersApi'
import type { CustomerResponse, SalesOrderResponse } from './types'

export function SalesOrderPage() {
  const { user } = useAuth()
  // Nhóm 1 (requirements-functional.md): cả ADMIN lẫn PLANNER đều thao tác đơn hàng thủ công.
  const canEdit = canEditSalesOrder(user)
  // Nhập Excel hàng loạt chỉ PLANNER, đúng SalesOrderImportController.
  const canImport = canImportSalesOrder(user)

  const [salesOrders, setSalesOrders] = useState<SalesOrderResponse[]>([])
  const [customers, setCustomers] = useState<CustomerResponse[]>([])
  const [doorProducts, setDoorProducts] = useState<DoorProductResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const latestLoadId = useRef(0)

  const reload = useCallback(async () => {
    const loadId = ++latestLoadId.current
    setLoading(true)
    try {
      const [loadedSalesOrders, loadedCustomers, loadedDoorProducts] = await Promise.all([
        listSalesOrders(),
        listCustomers(),
        listDoorProducts(),
      ])
      if (loadId !== latestLoadId.current) {
        return
      }
      setSalesOrders(loadedSalesOrders)
      setCustomers(loadedCustomers)
      setDoorProducts(loadedDoorProducts)
      setLoadError(null)
    } catch (error) {
      if (loadId !== latestLoadId.current) {
        return
      }
      setLoadError(extractErrorMessage(error, 'Không tải được dữ liệu đơn hàng.'))
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

  return (
    <div>
      <Typography.Title level={3} style={{ margin: 0 }}>
        Đơn hàng
      </Typography.Title>
      <Typography.Text type="secondary">Danh sách đơn hàng khách, tạo thủ công hoặc nhập từ Excel SAP.</Typography.Text>

      {!canImport && <RoleRestrictionNotice requiredRole="PLANNER" action="nhập đơn hàng hàng loạt từ Excel" />}

      {loadError && <Alert type="error" showIcon style={{ marginTop: 16 }} title={loadError} />}

      <Space size={16} style={{ margin: '16px 0' }} wrap>
        <Card size="small" style={{ width: 220 }}>
          <Statistic title="Tổng số đơn hàng" value={salesOrders.length} loading={loading} />
        </Card>
      </Space>

      <SalesOrderTable
        salesOrders={salesOrders}
        customers={customers}
        doorProducts={doorProducts}
        canEdit={canEdit}
        canImport={canImport}
        loading={loading}
        onChanged={() => void reload()}
      />
    </div>
  )
}
