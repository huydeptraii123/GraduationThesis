/** Phản chiếu các DTO tương ứng ở backend (package com.slatcut.cutting.dto). */

// SlatMaterial là khái niệm dùng chung với màn tồn kho, không khai báo lại ở đây.
export type { SlatMaterialResponse } from '../inventory/types'

export interface DoorProductResponse {
  id: number
  material: number
  doorMaterialName: string
  mauSac: string
}

export interface DoorProductRequest {
  material: number
  doorMaterialName: string
  mauSac: string
}

export interface BomItemResponse {
  id: number
  doorProductId: number
  doorProductName: string
  doorProductMauSac: string
  slatMaterialId: number
  slatMaterialName: string
  widthOffsetM: number | null
  heightOffsetM: number | null
  slatCountSlope: number | null
  slatCountIntercept: number | null
  dinhMucTbMPerBoCua: number | null
}

export interface BomItemRequest {
  doorProductId: number
  slatMaterialId: number
  widthOffsetM: number | null
  heightOffsetM: number | null
  slatCountSlope: number | null
  slatCountIntercept: number | null
  dinhMucTbMPerBoCua: number | null
}
