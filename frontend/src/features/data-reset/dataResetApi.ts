import { httpClient } from '../../api/httpClient'

/**
 * Cụm người dùng phải gõ để xác nhận — bản sao đối ứng của `DataResetService.CONFIRMATION_PHRASE` ở
 * backend (máy chủ kiểm lại, sai là 422). Đổi một bên thì phải đổi cả bên kia.
 */
export const DATA_RESET_CONFIRMATION_PHRASE = 'XOA DU LIEU'

/** Số dòng đã xóa ở từng bảng — khớp `DataResetResult` ở backend. */
export interface DataResetResult {
  salesOrders: number
  customers: number
  inventoryBatches: number
  cuttingPlans: number
  cuttingPlanDetails: number
  cuttingPlanDetailItems: number
  shortageRecords: number
  stockSnapshots: number
}

export function resetOperationalData(confirmation: string): Promise<DataResetResult> {
  return httpClient.post<DataResetResult>('/api/v1/data-reset', { confirmation }).then((res) => res.data)
}
