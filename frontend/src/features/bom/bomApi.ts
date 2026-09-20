import { httpClient } from '../../api/httpClient'
import type { Page, PageParams } from '../../api/pagination'
import type { SlatGroup } from '../inventory/types'
import type {
  BomImportResult,
  BomItemRequest,
  BomItemResponse,
  BomSummaryResponse,
  DoorProductRequest,
  DoorProductResponse,
} from './types'

const DOOR_PRODUCTS_URL = '/api/v1/door-products'
const BOM_ITEMS_URL = '/api/v1/bom-items'

export function listDoorProducts(): Promise<DoorProductResponse[]> {
  return httpClient.get<DoorProductResponse[]>(DOOR_PRODUCTS_URL).then((res) => res.data)
}

export function createDoorProduct(payload: DoorProductRequest): Promise<DoorProductResponse> {
  return httpClient.post<DoorProductResponse>(DOOR_PRODUCTS_URL, payload).then((res) => res.data)
}

export function updateDoorProduct(id: number, payload: DoorProductRequest): Promise<DoorProductResponse> {
  return httpClient.put<DoorProductResponse>(`${DOOR_PRODUCTS_URL}/${id}`, payload).then((res) => res.data)
}

export function deleteDoorProduct(id: number): Promise<void> {
  return httpClient.delete(`${DOOR_PRODUCTS_URL}/${id}`).then(() => undefined)
}

export interface BomItemFilterParams extends PageParams {
  keyword?: string
  slatGroup?: SlatGroup | null
}

export function listBomItems(params: BomItemFilterParams): Promise<Page<BomItemResponse>> {
  return httpClient.get<Page<BomItemResponse>>(BOM_ITEMS_URL, { params }).then((res) => res.data)
}

/** Số liệu tổng hợp toàn bộ định mức — danh sách giờ chỉ trả về trang đang xem nên không tự cộng được. */
export function getBomSummary(): Promise<BomSummaryResponse> {
  return httpClient.get<BomSummaryResponse>(`${BOM_ITEMS_URL}/summary`).then((res) => res.data)
}

export function createBomItem(payload: BomItemRequest): Promise<BomItemResponse> {
  return httpClient.post<BomItemResponse>(BOM_ITEMS_URL, payload).then((res) => res.data)
}

export function updateBomItem(id: number, payload: BomItemRequest): Promise<BomItemResponse> {
  return httpClient.put<BomItemResponse>(`${BOM_ITEMS_URL}/${id}`, payload).then((res) => res.data)
}

export function deleteBomItem(id: number): Promise<void> {
  return httpClient.delete(`${BOM_ITEMS_URL}/${id}`).then(() => undefined)
}

export function importBomExcel(file: File): Promise<BomImportResult> {
  const formData = new FormData()
  formData.append('file', file)
  return httpClient.post<BomImportResult>(`${BOM_ITEMS_URL}/import`, formData).then((res) => res.data)
}
