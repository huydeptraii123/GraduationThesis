import { httpClient } from '../../api/httpClient'
import type { Page, PageParams } from '../../api/pagination'
import type {
  InventoryBatchRequest,
  InventoryBatchResponse,
  InventoryImportResult,
  InventorySummaryResponse,
  SlatGroup,
  SlatMaterialRequest,
  SlatMaterialResponse,
} from './types'

const BATCHES_URL = '/api/v1/inventory-batches'
const MATERIALS_URL = '/api/v1/slat-materials'

export interface SlatFilterParams extends PageParams {
  keyword?: string
  slatGroup?: SlatGroup | null
}

export function listBatches(params: SlatFilterParams): Promise<Page<InventoryBatchResponse>> {
  return httpClient.get<Page<InventoryBatchResponse>>(BATCHES_URL, { params }).then((res) => res.data)
}

/** Tổng tồn kho toàn hệ thống — phải hỏi riêng vì danh sách giờ chỉ trả về trang đang xem. */
export function getInventorySummary(): Promise<InventorySummaryResponse> {
  return httpClient.get<InventorySummaryResponse>(`${BATCHES_URL}/summary`).then((res) => res.data)
}

export function createBatch(payload: InventoryBatchRequest): Promise<InventoryBatchResponse> {
  return httpClient.post<InventoryBatchResponse>(BATCHES_URL, payload).then((res) => res.data)
}

export function updateBatch(id: number, payload: InventoryBatchRequest): Promise<InventoryBatchResponse> {
  return httpClient.put<InventoryBatchResponse>(`${BATCHES_URL}/${id}`, payload).then((res) => res.data)
}

export function deleteBatch(id: number): Promise<void> {
  return httpClient.delete(`${BATCHES_URL}/${id}`).then(() => undefined)
}

export function listMaterials(params: SlatFilterParams): Promise<Page<SlatMaterialResponse>> {
  return httpClient.get<Page<SlatMaterialResponse>>(MATERIALS_URL, { params }).then((res) => res.data)
}

/**
 * Danh mục vật tư ĐẦY ĐỦ, không phân trang. Dùng cho dropdown trong form và cho việc tra mã/nhóm
 * vật tư ở bảng lô tồn kho và bảng BOM — những chỗ đó cần tra được mọi id, kể cả id không nằm trong
 * trang đang xem.
 */
export function listMaterialOptions(): Promise<SlatMaterialResponse[]> {
  return httpClient.get<SlatMaterialResponse[]>(`${MATERIALS_URL}/options`).then((res) => res.data)
}

export function createMaterial(payload: SlatMaterialRequest): Promise<SlatMaterialResponse> {
  return httpClient.post<SlatMaterialResponse>(MATERIALS_URL, payload).then((res) => res.data)
}

export function updateMaterial(id: number, payload: SlatMaterialRequest): Promise<SlatMaterialResponse> {
  return httpClient.put<SlatMaterialResponse>(`${MATERIALS_URL}/${id}`, payload).then((res) => res.data)
}

export function deleteMaterial(id: number): Promise<void> {
  return httpClient.delete(`${MATERIALS_URL}/${id}`).then(() => undefined)
}

export function importInventoryExcel(file: File): Promise<InventoryImportResult> {
  const formData = new FormData()
  formData.append('file', file)
  return httpClient.post<InventoryImportResult>('/api/v1/inventory/import', formData).then((res) => res.data)
}
