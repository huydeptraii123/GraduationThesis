/**
 * Màu gán cho từng đơn hàng xuyên suốt mọi thanh phôi ở tab "Chi tiết xuất kho theo phôi" của 1
 * trang chi tiết. Bảng màu nằm cùng các màu biểu đồ khác ở chartTheme.
 */
import { ORDER_COLOR_PALETTE } from '../../components/charts/chartTheme'

/** Gán màu theo thứ tự đơn hàng xuất hiện lần đầu, nhớ qua Map, quay vòng bảng màu khi hết. */
export function createOrderColorAssigner(): (orderKey: string) => string {
  const assigned = new Map<string, string>()
  return (orderKey: string) => {
    const existing = assigned.get(orderKey)
    if (existing) {
      return existing
    }
    const color = ORDER_COLOR_PALETTE[assigned.size % ORDER_COLOR_PALETTE.length]
    assigned.set(orderKey, color)
    return color
  }
}
