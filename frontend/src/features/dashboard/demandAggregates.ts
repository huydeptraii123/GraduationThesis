/**
 * Gộp danh sách nhu cầu cắt của một lần tính thành đúng những con số các khối trên màn hình cần.
 *
 * Thuần tính toán, không chạm React hay API. Mọi khối đều gộp lên từ **cùng một danh sách bộ cửa**
 * dựng ở {@link toDoorSets}, nên bất biến "tổng cột chồng = tổng bar ngang = tổng donut = số bộ cửa
 * Open" đúng theo cấu trúc chứ không nhờ ba phép đếm độc lập tình cờ khớp nhau.
 */

import type { CuttingPlanDemandView } from '../cutting-plans/types'
import { DOOR_SET_SHORT, DOOR_SET_SUFFICIENT, type DoorSetStatus } from './simulationSeries'

/** Model chưa khai báo vẫn phải lên biểu đồ — gộp im lặng vào nhóm khác thì số liệu nói dối. */
export const UNKNOWN_MODEL_LABEL = '(chưa có model)'

/** Số dòng tối đa của biểu đồ theo model, kể cả dòng "Khác" gộp phần đuôi. */
const MAX_MODEL_ROWS = 12

/**
 * Một bộ cửa trong lần tính. Khóa là `(ycsx, item)` — đúng khóa nghiệp vụ của một bộ cửa, và là
 * thứ mọi dòng nhu cầu cắt của cùng bộ cửa mang giống nhau.
 */
export interface DoorSetSummary {
  key: string
  ycsx: string
  item: number
  reqdDeliveryDate: string
  materialGroup: string
  status: DoorSetStatus
}

export interface StatusBreakdown {
  /** Nhãn trục — ngày giao đã định dạng, hoặc tên model. */
  label: string
  [DOOR_SET_SUFFICIENT]: number
  [DOOR_SET_SHORT]: number
  total: number
}

export interface ShortageByMaterial {
  slatMaterialCode: number
  slatMaterialName: string
  slatGroup: string
  missingSticks: number
  missingLengthM: number
  affectedDoorSetCount: number
}

/**
 * Gộp các dòng nhu cầu về từng bộ cửa.
 *
 * `doorSetStatus` backend trả về đã là trạng thái của CẢ bộ cửa (một loại thanh thiếu là cả bộ
 * tính thiếu), giống nhau trên mọi dòng của bộ cửa đó — nên lấy giá trị đầu tiên gặp được là đủ,
 * không phải tự suy lại từ `quantityMissing`. Tự suy lại là mở đường cho màn hình và file Excel
 * nói hai điều khác nhau về cùng một bộ cửa.
 */
export function toDoorSets(demands: CuttingPlanDemandView[]): DoorSetSummary[] {
  const byKey = new Map<string, DoorSetSummary>()
  for (const demand of demands) {
    const key = `${demand.ycsx}#${demand.item}`
    if (byKey.has(key)) {
      continue
    }
    byKey.set(key, {
      key,
      ycsx: demand.ycsx,
      item: demand.item,
      reqdDeliveryDate: demand.reqdDeliveryDate,
      materialGroup: demand.materialGroup ?? UNKNOWN_MODEL_LABEL,
      // Chuỗi lạ (backend đổi chính tả nhãn) quy về "thiếu nan", KHÔNG quy về "đủ": báo thừa một
      // bộ cửa thiếu chỉ tốn công kiểm lại, còn báo đủ nhầm thì biểu đồ nói 100% đủ nan trong khi
      // bảng cảnh báo ngay bên dưới vẫn liệt kê vật tư còn thiếu — và không ai biết bên nào sai.
      status: demand.doorSetStatus === DOOR_SET_SUFFICIENT ? DOOR_SET_SUFFICIENT : DOOR_SET_SHORT,
    })
  }
  return [...byKey.values()]
}

function emptyBreakdown(label: string): StatusBreakdown {
  return { label, [DOOR_SET_SUFFICIENT]: 0, [DOOR_SET_SHORT]: 0, total: 0 }
}

function tally(groups: Map<string, StatusBreakdown>, groupKey: string, label: string, status: DoorSetStatus): void {
  let group = groups.get(groupKey)
  if (group == null) {
    group = emptyBreakdown(label)
    groups.set(groupKey, group)
  }
  group[status] += 1
  group.total += 1
}

/**
 * Số bộ cửa theo ngày giao, sắp theo NGÀY THẬT tăng dần.
 *
 * Gom theo chuỗi ngày ISO rồi mới định dạng để hiển thị: sắp trên chuỗi `DD/MM/YYYY` sẽ xếp
 * 02/10 trước 28/09 vì so sánh chữ số ngày trước.
 */
export function byDeliveryDate(doorSets: DoorSetSummary[], formatDate: (iso: string) => string): StatusBreakdown[] {
  const groups = new Map<string, StatusBreakdown>()
  for (const doorSet of doorSets) {
    tally(groups, doorSet.reqdDeliveryDate, formatDate(doorSet.reqdDeliveryDate), doorSet.status)
  }
  return [...groups.entries()].sort(([left], [right]) => left.localeCompare(right)).map(([, group]) => group)
}

/**
 * Số bộ cửa theo model, nhiều nhất lên trên — thanh ngang đọc từ trên xuống như ảnh mẫu.
 *
 * Quá {@link MAX_MODEL_ROWS} model thì phần đuôi gộp thành một dòng "Khác": trục dọc có chiều cao
 * cố định nên thêm model chỉ làm các thanh mỏng dần tới lúc nhãn chồng lên nhau và không đọc được
 * dòng nào. Dòng gộp ghi rõ nó thay cho bao nhiêu model để tổng vẫn khớp số bộ cửa Open.
 */
export function byModel(doorSets: DoorSetSummary[]): StatusBreakdown[] {
  const groups = new Map<string, StatusBreakdown>()
  for (const doorSet of doorSets) {
    tally(groups, doorSet.materialGroup, doorSet.materialGroup, doorSet.status)
  }
  const sorted = [...groups.values()].sort(
    (left, right) => right.total - left.total || left.label.localeCompare(right.label),
  )
  if (sorted.length <= MAX_MODEL_ROWS) {
    return sorted
  }
  const head = sorted.slice(0, MAX_MODEL_ROWS - 1)
  const tail = sorted.slice(MAX_MODEL_ROWS - 1)
  const other = emptyBreakdown(`Khác (${tail.length} model)`)
  for (const group of tail) {
    other[DOOR_SET_SUFFICIENT] += group[DOOR_SET_SUFFICIENT]
    other[DOOR_SET_SHORT] += group[DOOR_SET_SHORT]
    other.total += group.total
  }
  return [...head, other]
}

/** Tỷ trọng đáp ứng nan — luôn đủ hai lát, kể cả lát bằng 0, để chú giải không nhảy màu. */
export function byStatus(doorSets: DoorSetSummary[]): { status: DoorSetStatus; count: number }[] {
  const sufficient = doorSets.filter((doorSet) => doorSet.status === DOOR_SET_SUFFICIENT).length
  return [
    { status: DOOR_SET_SUFFICIENT, count: sufficient },
    { status: DOOR_SET_SHORT, count: doorSets.length - sufficient },
  ]
}

/**
 * Vật tư còn thiếu, gộp theo loại thanh nan — căn cứ để lập kế hoạch sản xuất bù.
 *
 * Tổng mét thiếu tính bằng `số thanh thiếu × độ dài đoạn` của từng dòng chứ không nhân trên số đã
 * gộp: mỗi bộ cửa có độ dài đoạn riêng theo kích thước cửa, nên một độ dài đại diện cho cả nhóm là
 * con số không tồn tại.
 */
export function shortagesByMaterial(demands: CuttingPlanDemandView[]): ShortageByMaterial[] {
  const byCode = new Map<number, ShortageByMaterial & { doorSetKeys: Set<string> }>()
  for (const demand of demands) {
    if (demand.quantityMissing <= 0) {
      continue
    }
    let row = byCode.get(demand.slatMaterialCode)
    if (row == null) {
      row = {
        slatMaterialCode: demand.slatMaterialCode,
        slatMaterialName: demand.slatMaterialName,
        slatGroup: demand.slatGroup,
        missingSticks: 0,
        missingLengthM: 0,
        affectedDoorSetCount: 0,
        doorSetKeys: new Set<string>(),
      }
      byCode.set(demand.slatMaterialCode, row)
    }
    row.missingSticks += demand.quantityMissing
    row.missingLengthM += (demand.quantityMissing * demand.cutLengthMm) / 1000
    row.doorSetKeys.add(`${demand.ycsx}#${demand.item}`)
  }
  return [...byCode.values()]
    .map(({ doorSetKeys, ...row }) => ({ ...row, affectedDoorSetCount: doorSetKeys.size }))
    .sort((left, right) => right.missingLengthM - left.missingLengthM)
}

/**
 * Tỷ lệ phế của lần tính. Mẫu số là tồn kho THỰC TIÊU HAO (phôi xuất kho trừ phần dư đã nhập lại),
 * đúng con số backend trả về — không tự dựng mẫu số khác.
 *
 * Trả `null` khi chưa tiêu hao mét nào: một lần tính không cắt được gì thì tỷ lệ phế không tồn tại,
 * và in ra `0.00%` ở đó là nói rằng cắt rất sạch.
 */
export function wasteRatioPercent(totalWasteM: number, totalStockUsedM: number): number | null {
  return totalStockUsedM > 0 ? (totalWasteM / totalStockUsedM) * 100 : null
}
