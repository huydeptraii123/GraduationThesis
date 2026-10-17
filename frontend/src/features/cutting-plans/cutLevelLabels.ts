import type { CutLevel } from './types'

/**
 * Nhãn "cắt theo phương án mấy" cho công nhân — chủ nhà máy chỉ cần nói số 1/2/3/4 là đủ, không
 * cần nhắc tên kỹ thuật của từng mức (khớp gần đúng / bội số / ghép 2 đoạn / nhập lại kho).
 */
export const CUT_LEVEL_LABEL: Record<CutLevel, string> = {
  PA1: 'Phương án 1',
  PA2: 'Phương án 2',
  PA3: 'Phương án 3',
  PA4: 'Phương án 4',
}

/** Phương án lưu từ trước khi hệ thống bắt đầu ghi lại mức cắt (trước di trú V14). */
export const CUT_LEVEL_UNKNOWN_LABEL = 'Không rõ'

export function cutLevelLabel(cutLevel: CutLevel | null): string {
  return cutLevel ? CUT_LEVEL_LABEL[cutLevel] : CUT_LEVEL_UNKNOWN_LABEL
}
