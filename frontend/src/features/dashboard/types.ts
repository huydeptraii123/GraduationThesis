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
}

export interface DashboardResponse {
  pendingOrderCount: number
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
  remainderBreakdown: RemainderBreakdownResponse[]
  wasteByGroup: SlatGroupWasteResponse[]
}
