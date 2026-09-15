/** Phản chiếu các DTO tương ứng ở backend (package com.slatcut.cutting.dto). */

// DoorProduct là khái niệm dùng chung với màn BOM, không khai báo lại ở đây.
export type { DoorProductResponse } from '../bom/types'

export interface CustomerResponse {
  id: number
  customer: number
  customerName: string
}

export interface SalesOrderResponse {
  id: number
  ycsx: string
  item: number
  /** Chỉ có giá trị khi đơn được tạo qua import Excel từ SAP; đơn tạo thủ công qua UI để trống. */
  salesDocument: number | null
  salesOrderItem: number | null
  customerId: number
  customerName: string
  doorProductId: number
  doorProductName: string
  doorProductMauSac: string
  chieuCaoDh: number
  chieuRongDh: number
  /** ISO yyyy-MM-dd. */
  reqdDeliveryDate: string
}

export interface SalesOrderRequest {
  ycsx: string
  item: number
  salesDocument?: number | null
  salesOrderItem?: number | null
  customerId: number
  doorProductId: number
  chieuCaoDh: number
  chieuRongDh: number
  reqdDeliveryDate: string
}

export interface SalesOrderImportResult {
  totalRowsImported: number
  skippedNonDoorRows: number
}
