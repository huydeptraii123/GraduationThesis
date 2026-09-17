/**
 * Màu gán cho từng đơn hàng xuyên suốt mọi thanh phôi ở tab "Chi tiết xuất kho theo phôi" của 1
 * trang chi tiết — tránh tông cam/xanh lá đã dùng cho phân loại phần dư (WASTE/RESTOCK) trong
 * CuttingBarDiagram để không gây nhầm lẫn giữa "màu đơn hàng" và "màu phân loại phần dư".
 */
const ORDER_COLOR_PALETTE = [
  '#1677ff',
  '#722ed1',
  '#13c2c2',
  '#eb2f96',
  '#2f54eb',
  '#ad6800',
  '#9254de',
  '#f759ab',
  '#08979c',
  '#597ef7',
]

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
