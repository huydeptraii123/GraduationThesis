/** Phản chiếu các DTO tương ứng ở backend (package com.slatcut.cutting.dto). */

export type CuttingPlanStatus = 'COMPLETED' | 'FAILED'

export type RemainderType = 'DISCARDED' | 'WASTE' | 'RESTOCK'

export interface CuttingPlanSummaryResponse {
  id: number
  runAt: string
  status: CuttingPlanStatus
  totalWasteM: number
  totalStockUsedM: number
  scopeCutoffDate: string
  scopeOrderCount: number
}

export interface CuttingPlanDetailItemResponse {
  id: number
  salesOrderId: number
  ycsx: string
  item: number
  customerName: string
  doorProductId: number
  doorProductName: string
  reqdDeliveryDate: string
  chieuCaoDh: number
  chieuRongDh: number
  cutLengthMm: number
  cutQuantity: number
  originalOrder: boolean
}

export interface ShortageRecordResponse {
  id: number
  slatMaterialId: number
  slatMaterialName: string
  salesOrderId: number
  ycsx: string
  item: number
  customerName: string
  doorProductId: number
  doorProductName: string
  reqdDeliveryDate: string
  chieuCaoDh: number
  chieuRongDh: number
  missingQuantity: number
  missingLengthM: number
}

export interface CuttingPlanDetailResponse {
  id: number
  slatMaterialId: number
  slatMaterialName: string
  sourceLengthMm: number
  patternCode: string
  remainderMm: number
  remainderType: RemainderType
  stickCount: number
  items: CuttingPlanDetailItemResponse[]
}

export interface CuttingPlanResponse extends CuttingPlanSummaryResponse {
  details: CuttingPlanDetailResponse[]
  shortages: ShortageRecordResponse[]
}

export interface CuttingPlanScopePreviewResponse {
  eligibleOrderCount: number
  scopeCutoffDate: string
}
