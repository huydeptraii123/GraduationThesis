import type { CuttingPlanResponse } from './types'

/** DoorProduct đã có UNIQUE (material, z_mau_sac) — 1 doorProductId xác định trọn 1 tổ hợp mẫu cửa+màu. */
const MAX_ORDERS_PER_BATCH = 7

export interface CuttingBatchOrderRow {
  salesOrderId: number
  doorProductId: number
  doorProductName: string
  reqdDeliveryDate: string
  ycsx: string
  item: number
  customerName: string
  chieuCaoDh: number
  chieuRongDh: number
  hasShortage: boolean
}

export interface CuttingBatch {
  batchNumber: number
  doorProductId: number
  doorProductName: string
  orders: CuttingBatchOrderRow[]
  earliestDeliveryDate: string
}

/** 1 dòng / bộ cửa (salesOrderId) duy nhất trong toàn bộ lần chạy — dùng cho tab tổng quan + đợt cắt. */
export function buildDedupedOrderRows(plan: CuttingPlanResponse): CuttingBatchOrderRow[] {
  const shortageOrderIds = new Set(plan.shortages.map((s) => s.salesOrderId))
  const rows = new Map<number, CuttingBatchOrderRow>()

  plan.details.forEach((detail) => {
    detail.items.forEach((item) => {
      if (!rows.has(item.salesOrderId)) {
        rows.set(item.salesOrderId, {
          salesOrderId: item.salesOrderId,
          doorProductId: item.doorProductId,
          doorProductName: item.doorProductName,
          reqdDeliveryDate: item.reqdDeliveryDate,
          ycsx: item.ycsx,
          item: item.item,
          customerName: item.customerName,
          chieuCaoDh: item.chieuCaoDh,
          chieuRongDh: item.chieuRongDh,
          hasShortage: shortageOrderIds.has(item.salesOrderId),
        })
      }
    })
  })

  plan.shortages.forEach((shortage) => {
    if (!rows.has(shortage.salesOrderId)) {
      rows.set(shortage.salesOrderId, {
        salesOrderId: shortage.salesOrderId,
        doorProductId: shortage.doorProductId,
        doorProductName: shortage.doorProductName,
        reqdDeliveryDate: shortage.reqdDeliveryDate,
        ycsx: shortage.ycsx,
        item: shortage.item,
        customerName: shortage.customerName,
        chieuCaoDh: shortage.chieuCaoDh,
        chieuRongDh: shortage.chieuRongDh,
        hasShortage: true,
      })
    }
  })

  return Array.from(rows.values())
}

function comparePriority(a: CuttingBatchOrderRow, b: CuttingBatchOrderRow): number {
  if (a.reqdDeliveryDate !== b.reqdDeliveryDate) {
    return a.reqdDeliveryDate.localeCompare(b.reqdDeliveryDate)
  }
  if (a.ycsx !== b.ycsx) {
    return a.ycsx.localeCompare(b.ycsx)
  }
  return a.item - b.item
}

/**
 * Nhóm theo mẫu cửa+màu (doorProductId), chia chunk tối đa 7 bộ/đợt lấp đầy theo đúng thứ tự ưu
 * tiên (reqdDeliveryDate, ycsx, item), rồi sắp xếp danh sách đợt theo ngày giao sớm nhất trong đợt
 * (tăng dần); nếu trùng, đợt có nhiều bộ cùng rơi vào ngày sớm nhất đó hơn được xếp trước — đúng
 * docs/requirements-functional.md dòng 11. Đây chỉ là góc nhìn hiển thị, không đổi thuật toán cắt.
 */
export function buildCuttingBatches(orders: CuttingBatchOrderRow[]): CuttingBatch[] {
  const sorted = [...orders].sort(comparePriority)

  const groups = new Map<number, { doorProductName: string; orders: CuttingBatchOrderRow[] }>()
  sorted.forEach((order) => {
    const group = groups.get(order.doorProductId)
    if (group) {
      group.orders.push(order)
    } else {
      groups.set(order.doorProductId, { doorProductName: order.doorProductName, orders: [order] })
    }
  })

  const batches: Omit<CuttingBatch, 'batchNumber'>[] = []
  groups.forEach((group, doorProductId) => {
    for (let i = 0; i < group.orders.length; i += MAX_ORDERS_PER_BATCH) {
      const chunk = group.orders.slice(i, i + MAX_ORDERS_PER_BATCH)
      const earliestDeliveryDate = chunk.reduce(
        (earliest, order) => (order.reqdDeliveryDate < earliest ? order.reqdDeliveryDate : earliest),
        chunk[0].reqdDeliveryDate,
      )
      batches.push({ doorProductId, doorProductName: group.doorProductName, orders: chunk, earliestDeliveryDate })
    }
  })

  batches.sort((a, b) => {
    if (a.earliestDeliveryDate !== b.earliestDeliveryDate) {
      return a.earliestDeliveryDate.localeCompare(b.earliestDeliveryDate)
    }
    const countA = a.orders.filter((order) => order.reqdDeliveryDate === a.earliestDeliveryDate).length
    const countB = b.orders.filter((order) => order.reqdDeliveryDate === b.earliestDeliveryDate).length
    return countB - countA
  })

  return batches.map((batch, index) => ({ ...batch, batchNumber: index + 1 }))
}
