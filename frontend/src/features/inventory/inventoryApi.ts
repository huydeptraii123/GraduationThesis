import { httpClient } from '../../api/httpClient'
import type {
  InventoryBatchRequest,
  InventoryBatchResponse,
  InventoryImportResult,
  SlatMaterialRequest,
  SlatMaterialResponse,
} from './types'

const BATCHES_URL = '/api/v1/inventory-batches'
const MATERIALS_URL = '/api/v1/slat-materials'

export function listBatches(): Promise<InventoryBatchResponse[]> {
  return httpClient.get<InventoryBatchResponse[]>(BATCHES_URL).then((res) => res.data)
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

export function listMaterials(): Promise<SlatMaterialResponse[]> {
  return httpClient.get<SlatMaterialResponse[]>(MATERIALS_URL).then((res) => res.data)
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
