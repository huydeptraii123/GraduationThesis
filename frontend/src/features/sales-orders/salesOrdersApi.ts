import { httpClient } from '../../api/httpClient'
import type { Page, PageParams } from '../../api/pagination'
import type { CustomerResponse, SalesOrderImportResult, SalesOrderRequest, SalesOrderResponse } from './types'

// Tái dùng client GET /api/v1/door-products đã có ở màn BOM, không viết lại.
export { listDoorProducts } from '../bom/bomApi'

const CUSTOMERS_URL = '/api/v1/customers'
const SALES_ORDERS_URL = '/api/v1/sales-orders'

export function listCustomers(): Promise<CustomerResponse[]> {
  return httpClient.get<CustomerResponse[]>(CUSTOMERS_URL).then((res) => res.data)
}

export interface SalesOrderFilterParams extends PageParams {
  keyword?: string
  customerId?: number | null
  /** Định dạng YYYY-MM-DD; backend nhận bằng @DateTimeFormat(iso = DATE). */
  deliveryFrom?: string | null
  deliveryTo?: string | null
}

export function listSalesOrders(params: SalesOrderFilterParams): Promise<Page<SalesOrderResponse>> {
  return httpClient.get<Page<SalesOrderResponse>>(SALES_ORDERS_URL, { params }).then((res) => res.data)
}

export function createSalesOrder(payload: SalesOrderRequest): Promise<SalesOrderResponse> {
  return httpClient.post<SalesOrderResponse>(SALES_ORDERS_URL, payload).then((res) => res.data)
}

export function updateSalesOrder(id: number, payload: SalesOrderRequest): Promise<SalesOrderResponse> {
  return httpClient.put<SalesOrderResponse>(`${SALES_ORDERS_URL}/${id}`, payload).then((res) => res.data)
}

export function deleteSalesOrder(id: number): Promise<void> {
  return httpClient.delete(`${SALES_ORDERS_URL}/${id}`).then(() => undefined)
}

export function importSalesOrdersExcel(file: File): Promise<SalesOrderImportResult> {
  const formData = new FormData()
  formData.append('file', file)
  return httpClient.post<SalesOrderImportResult>(`${SALES_ORDERS_URL}/import`, formData).then((res) => res.data)
}
