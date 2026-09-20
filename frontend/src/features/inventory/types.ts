/** Phản chiếu các DTO tương ứng ở backend (package com.slatcut.cutting.dto). */

export type SlatGroup = 'MAIN_SLAT' | 'SUB_SLAT' | 'BOTTOM_BAR' | 'RAIL' | 'OTHER'

export interface SlatMaterialResponse {
  id: number
  /** Mã vật tư từ hệ thống nguồn, khác với `id` là khóa nội bộ của hệ thống này. */
  slatMaterial: number
  slatMaterialName: string
  slatGroup: SlatGroup
}

export interface SlatMaterialRequest {
  slatMaterial: number
  slatMaterialName: string
  slatGroup: SlatGroup
}

export interface InventoryBatchResponse {
  id: number
  slatMaterialId: number
  slatMaterialName: string
  doDaiThanhMm: number
  soThanh: number
}

export interface InventoryBatchRequest {
  slatMaterialId: number
  doDaiThanhMm: number
  soThanh: number
}

export interface InventorySummaryResponse {
  batchCount: number
  totalSticks: number
  /** Tổng chiều dài quy đổi sang mét, đã làm tròn ở backend. */
  totalLengthM: number
}

export interface InventoryImportResult {
  totalRowsImported: number
  /** Số tổ hợp tồn kho có trong hệ thống nhưng không còn trong file, bị đưa về 0. */
  zeroedOutCount: number
}
