import type { CuttingBarPiece } from '../../components/CuttingBarDiagram'
import type { CuttingPlanDetailResponse } from './types'

/**
 * 1 CuttingPlanDetailItem với cutQuantity=N giãn thành N đoạn liên tiếp cùng màu/nhãn — quyết định
 * trình bày thuộc về trang gọi CuttingBarDiagram, không phải của bản thân component vẽ (đã cố ý
 * bỏ ngỏ ở task 9.3).
 */
export function expandDetailToPieces(
  detail: CuttingPlanDetailResponse,
  assignOrderColor: (orderKey: string) => string,
): CuttingBarPiece[] {
  const pieces: CuttingBarPiece[] = []
  detail.items.forEach((item) => {
    const color = assignOrderColor(String(item.salesOrderId))
    const label = `${item.ycsx}/${item.item} (${(item.cutLengthMm / 1000).toFixed(2)}m)`
    for (let i = 0; i < item.cutQuantity; i++) {
      pieces.push({ key: `${item.id}-${i}`, lengthMm: item.cutLengthMm, color, label })
    }
  })
  return pieces
}
