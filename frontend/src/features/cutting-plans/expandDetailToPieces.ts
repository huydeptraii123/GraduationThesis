import type { CuttingBarPiece } from '../../components/CuttingBarDiagram'
import { formatMeters } from '../../utils/formatMeters'
import type { CuttingPlanDetailResponse } from './types'

/**
 * Các đoạn cắt trên MỘT phôi của dòng phương án — quyết định trình bày thuộc về trang gọi
 * CuttingBarDiagram, không phải của bản thân component vẽ (đã cố ý bỏ ngỏ ở task 9.3).
 *
 * Một dòng gộp `stickCount` phôi giống hệt nhau, còn `cutQuantity` của từng đoạn là tổng trên CẢ
 * `stickCount` phôi đó (cách gộp ở backend). Sơ đồ vẽ một thanh nên chỉ được vẽ phần của một phôi:
 * `cutQuantity / stickCount` đoạn mỗi loại. Vẽ đủ `cutQuantity` thì 2 phôi mỗi phôi một đoạn thành
 * 2 đoạn trên cùng một thanh — đoạn tràn ra ngoài thanh hoặc phần dư hiện hẹp hơn thật.
 *
 * Phép chia luôn chẵn: hai phôi chỉ được gộp khi giống hệt nhau tới từng đoạn theo đúng thứ tự,
 * nên mỗi loại đoạn xuất hiện cùng một số lần trên mọi phôi của dòng.
 */
export function expandDetailToPieces(
  detail: CuttingPlanDetailResponse,
  assignOrderColor: (orderKey: string) => string,
): CuttingBarPiece[] {
  const stickCount = detail.stickCount > 0 ? detail.stickCount : 1
  const pieces: CuttingBarPiece[] = []
  detail.items.forEach((item) => {
    const color = assignOrderColor(String(item.salesOrderId))
    const label = `${item.ycsx}/${item.item} (${formatMeters(item.cutLengthMm)}m)`
    const perStick = item.cutQuantity / stickCount
    for (let i = 0; i < perStick; i++) {
      pieces.push({ key: `${item.id}-${i}`, lengthMm: item.cutLengthMm, color, label })
    }
  })
  return pieces
}
