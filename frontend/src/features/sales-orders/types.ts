/** Phản chiếu các DTO tương ứng ở backend (package com.slatcut.cutting.dto). */

// DoorProduct là khái niệm dùng chung với màn BOM, không khai báo lại ở đây.
export type { DoorProductResponse } from '../bom/types'

export interface CustomerResponse {
  id: number
  customer: number
  customerName: string
}

/**
 * Trạng thái xử lý do backend suy ra lúc đọc (không phải cột lưu): hai giá trị đầu chỉ có ở đơn chưa
 * duyệt, hai giá trị sau chỉ có ở đơn đã duyệt.
 */
export type SalesOrderProcessingStatus = 'PENDING' | 'BLOCKED' | 'SUFFICIENT' | 'SHORTAGE'

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
  processingStatus: SalesOrderProcessingStatus
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
