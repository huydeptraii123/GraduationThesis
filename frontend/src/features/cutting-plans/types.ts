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

/**
 * Một dòng ở mức chi tiết theo đơn hàng: một loại vật tư của một bộ cửa. Nguồn dữ liệu chung của
 * cả các khối biểu đồ lẫn file Excel, nên số liệu hai nơi không thể lệch nhau.
 *
 * Bốn trường `lenhSx`, `materialGroup`, `cutDetailText`, `stockSnapshotText` hiện luôn rỗng — hệ
 * thống chưa có nguồn dữ liệu cho chúng.
 */
export interface CuttingPlanDemandView {
  priorityRank: number
  ycsx: string
  item: number
  lenhSx: number | null
  soNumber: number | null
  customerName: string
  reqdDeliveryDate: string
  doorProductName: string
  materialGroup: string | null
  slatMaterialCode: number
  slatMaterialName: string
  slatGroup: string
  wsxM: number
  cutLengthMm: number
  quantityNeeded: number
  quantityMissing: number
  statusText: string
  cutDetailText: string | null
  stockSnapshotText: string | null
  doorSetStatus: string
}

/** Phương án đã tính xong nhưng CHƯA ghi xuống cơ sở dữ liệu — không có id vì không có bản ghi nào. */
export interface CuttingPlanPreviewResponse {
  computedAt: string
  scopeOrderCount: number
  blockedOrderCount: number
  totalWasteM: number
  totalStockUsedM: number
  demands: CuttingPlanDemandView[]
}

/**
 * Backend trả `id: null` cho phương án chưa lưu. Bỏ hẳn trường đó khỏi kiểu thay vì khai
 * `number | null`: dòng chưa lưu không có id để mà tra cứu, và màn hình duyệt tự gán khóa hiển
 * thị cục bộ trước khi đưa vào các bảng dùng chung.
 */
type WithoutId<T> = Omit<T, 'id'>

export interface CuttingPlanPreviewDetail extends WithoutId<Omit<CuttingPlanDetailResponse, 'items'>> {
  items: WithoutId<CuttingPlanDetailItemResponse>[]
}

export type CuttingPlanPreviewShortage = WithoutId<ShortageRecordResponse>

export interface CuttingPlanApprovalPreviewResponse {
  scopeCutoffDate: string
  /** Ảnh chụp trạng thái đã dựng nên phương án này; gửi lại khi duyệt để backend từ chối nếu dữ liệu đã đổi. */
  stateFingerprint: string
  plan: CuttingPlanPreviewResponse
  details: CuttingPlanPreviewDetail[]
  shortages: CuttingPlanPreviewShortage[]
}
