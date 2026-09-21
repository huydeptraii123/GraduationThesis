import { httpClient } from '../../api/httpClient'
import type { Page, PageParams } from '../../api/pagination'
import type {
  CuttingPlanApprovalPreviewResponse,
  CuttingPlanResponse,
  CuttingPlanStatus,
  CuttingPlanSummaryResponse,
} from './types'

const CUTTING_PLANS_URL = '/api/v1/cutting-plans'

export interface CuttingPlanFilterParams extends PageParams {
  /** Mã lần chạy (#CP-<id>) — tra chính xác chứ không tìm gần đúng. */
  planId?: number | null
  status?: CuttingPlanStatus | null
  /** Định dạng YYYY-MM-DD, lọc theo thời điểm chạy. */
  runFrom?: string | null
  runTo?: string | null
}

export function listCuttingPlans(params: CuttingPlanFilterParams): Promise<Page<CuttingPlanSummaryResponse>> {
  return httpClient.get<Page<CuttingPlanSummaryResponse>>(CUTTING_PLANS_URL, { params }).then((res) => res.data)
}

export function getCuttingPlan(id: number): Promise<CuttingPlanResponse> {
  return httpClient.get<CuttingPlanResponse>(`${CUTTING_PLANS_URL}/${id}`).then((res) => res.data)
}

/** Phương án đề xuất cho đợt duyệt kế tiếp. Không ghi gì — chỉ khi bấm duyệt dữ liệu mới đổi. */
export function getApprovalPreview(): Promise<CuttingPlanApprovalPreviewResponse> {
  return httpClient
    .get<CuttingPlanApprovalPreviewResponse>(`${CUTTING_PLANS_URL}/approval-preview`)
    .then((res) => res.data)
}

/**
 * Duyệt phương án cắt. Gửi lại đúng dấu vân trạng thái đi kèm phương án đang xem; nếu đơn hàng
 * hoặc tồn kho đã đổi trong lúc xem xét, backend trả 409 và không ghi dòng nào.
 */
export function approveCuttingPlan(stateFingerprint: string): Promise<CuttingPlanResponse> {
  return httpClient
    .post<CuttingPlanResponse>(`${CUTTING_PLANS_URL}/approve`, { stateFingerprint })
    .then((res) => res.data)
}

export function exportCuttingPlan(id: number): Promise<Blob> {
  return httpClient.get(`${CUTTING_PLANS_URL}/${id}/export`, { responseType: 'blob' }).then((res) => res.data)
}

export function exportShortageReport(id: number): Promise<Blob> {
  return httpClient
    .get(`${CUTTING_PLANS_URL}/${id}/shortage-report`, { responseType: 'blob' })
    .then((res) => res.data)
}
