import { httpClient } from '../../api/httpClient'
import type { CustomerResponse, SalesOrderImportResult, SalesOrderRequest, SalesOrderResponse } from './types'

// Tái dùng client GET /api/v1/door-products đã có ở màn BOM, không viết lại.
export { listDoorProducts } from '../bom/bomApi'

const CUSTOMERS_URL = '/api/v1/customers'
const SALES_ORDERS_URL = '/api/v1/sales-orders'

export function listCustomers(): Promise<CustomerResponse[]> {
  return httpClient.get<CustomerResponse[]>(CUSTOMERS_URL).then((res) => res.data)
}

export function listSalesOrders(): Promise<SalesOrderResponse[]> {
  return httpClient.get<SalesOrderResponse[]>(SALES_ORDERS_URL).then((res) => res.data)
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
