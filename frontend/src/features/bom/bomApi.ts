import { httpClient } from '../../api/httpClient'
import type { BomItemRequest, BomItemResponse, DoorProductRequest, DoorProductResponse } from './types'

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

export function listBomItems(): Promise<BomItemResponse[]> {
  return httpClient.get<BomItemResponse[]>(BOM_ITEMS_URL).then((res) => res.data)
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
