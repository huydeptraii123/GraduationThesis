import { httpClient } from '../../api/httpClient'
import type { CuttingPlanResponse, CuttingPlanScopePreviewResponse, CuttingPlanSummaryResponse } from './types'

const CUTTING_PLANS_URL = '/api/v1/cutting-plans'

export function listCuttingPlans(): Promise<CuttingPlanSummaryResponse[]> {
  return httpClient.get<CuttingPlanSummaryResponse[]>(CUTTING_PLANS_URL).then((res) => res.data)
}

export function getCuttingPlan(id: number): Promise<CuttingPlanResponse> {
  return httpClient.get<CuttingPlanResponse>(`${CUTTING_PLANS_URL}/${id}`).then((res) => res.data)
}

export function generateCuttingPlan(): Promise<CuttingPlanResponse> {
  return httpClient.post<CuttingPlanResponse>(`${CUTTING_PLANS_URL}/generate`).then((res) => res.data)
}

export function getScopePreview(): Promise<CuttingPlanScopePreviewResponse> {
  return httpClient.get<CuttingPlanScopePreviewResponse>(`${CUTTING_PLANS_URL}/scope-preview`).then((res) => res.data)
}

export function exportCuttingPlan(id: number): Promise<Blob> {
  return httpClient.get(`${CUTTING_PLANS_URL}/${id}/export`, { responseType: 'blob' }).then((res) => res.data)
}

export function exportShortageReport(id: number): Promise<Blob> {
  return httpClient
    .get(`${CUTTING_PLANS_URL}/${id}/shortage-report`, { responseType: 'blob' })
    .then((res) => res.data)
}
