/** Phản chiếu DashboardResponse ở backend (com.slatcut.cutting.dto). */

import type { SlatGroup } from '../inventory/types'
import type { CuttingPlanStatus, RemainderType } from '../cutting-plans/types'

export interface WasteTrendPointResponse {
  planId: number
  runAt: string
  status: CuttingPlanStatus
  scopeOrderCount: number
  totalWasteM: number
  totalStockUsedM: number
  wasteRatioPercent: number
}

export interface RemainderBreakdownResponse {
  remainderType: RemainderType
  totalM: number
}

export interface SlatGroupWasteResponse {
  slatGroup: SlatGroup
  totalM: number
  /** Tồn kho thực tiêu hao của nhóm — mẫu số của tỷ lệ, đã trừ phần dư nhập lại kho. */
  stockUsedM: number
  wasteRatioPercent: number
}

/** 1 bộ cửa đã cắt, đặt tại ngày giao của nó trên biểu đồ xu hướng. */
export interface OrderWastePointResponse {
  salesOrderId: number
  ycsx: string
  item: number
  reqdDeliveryDate: string
  wasteM: number
  stockUsedM: number
  wasteRatioPercent: number
}

export interface DashboardResponse {
  pendingOrderCount: number
  /** Đơn bị bỏ qua vì mẫu cửa chưa có định mức — cần ADMIN cấu hình. */
  ordersMissingBomCount: number
  scopeCutoffDate: string
  readyBatchCount: number
  readyStickCount: number
  /** null khi chưa có lần chạy nào. */
  latestPlan: WasteTrendPointResponse | null
  cumulativeWasteM: number
  cumulativeStockUsedM: number
  cumulativeWasteRatioPercent: number
  /** Sắp xếp CŨ → MỚI, tối đa 10 lần chạy gần nhất. */
  wasteTrend: WasteTrendPointResponse[]
  /** Sắp theo ngày giao — nguồn của biểu đồ xu hướng tỷ lệ phế. */
  orderWasteTrend: OrderWastePointResponse[]
  remainderBreakdown: RemainderBreakdownResponse[]
  wasteByGroup: SlatGroupWasteResponse[]
}
