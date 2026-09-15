import type { SlatGroup } from './types'

export const SLAT_GROUP_LABEL: Record<SlatGroup, string> = {
  MAIN_SLAT: 'Nan chính',
  SUB_SLAT: 'Nan phụ',
  BOTTOM_BAR: 'Thanh đáy',
  RAIL: 'Ray dẫn hướng',
  OTHER: 'Khác',
}

export const SLAT_GROUP_COLOR: Record<SlatGroup, string> = {
  MAIN_SLAT: 'blue',
  SUB_SLAT: 'cyan',
  BOTTOM_BAR: 'purple',
  RAIL: 'orange',
  OTHER: 'default',
}

export const SLAT_GROUP_OPTIONS = (Object.keys(SLAT_GROUP_LABEL) as SlatGroup[]).map((value) => ({
  value,
  label: SLAT_GROUP_LABEL[value],
}))
