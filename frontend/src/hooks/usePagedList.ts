import type { SorterResult, TablePaginationConfig } from 'antd/es/table/interface'
import { useCallback, useEffect, useRef, useState } from 'react'
import { extractErrorMessage } from '../api/apiError'
import { emptyPage, toPageParam, type Page, type PageParams } from '../api/pagination'

interface Options {
  initialPageSize?: number
  errorMessage?: string
}

/**
 * Đổi mô tả sắp xếp của AntD sang cú pháp `field,asc` của Spring Data. Trả `undefined` khi người
 * dùng bỏ sắp xếp, để backend quay về thứ tự mặc định của endpoint.
 *
 * Luôn kèm `id` làm tiêu chí phụ (cú pháp `field,id,asc` của Spring: hướng ở cuối áp cho mọi cột
 * đứng trước). Sắp theo một cột nhiều giá trị trùng — số lượng thanh, ngày tạo — không cho ra thứ
 * tự duy nhất, mà LIMIT/OFFSET lại cắt theo đúng thứ tự máy chủ trả về: hai lần truy vấn xếp các
 * dòng bằng nhau theo hai cách khác nhau thì có dòng hiện hai lần ở hai trang, có dòng không hiện
 * lần nào. Khóa chính phá thế hòa nên thứ tự luôn xác định.
 */
function toSortParam<T>(sorter: SorterResult<T> | SorterResult<T>[]): string | undefined {
  const single = Array.isArray(sorter) ? sorter[0] : sorter
  if (!single?.order || !single.field) {
    return undefined
  }
  const field = Array.isArray(single.field) ? single.field.join('.') : String(single.field)
  const direction = single.order === 'ascend' ? 'asc' : 'desc'
  return field === 'id' ? `id,${direction}` : `${field},id,${direction}`
}

/**
 * Tải một trang dữ liệu từ backend và giữ trạng thái trang/cỡ trang cho `<Table>`.
 *
 * Hai điều dễ sai mà hook này gánh giúp, thay vì chép lại ở cả 6 bảng:
 *
 * <ul>
 *   <li><b>Đổi bộ lọc phải quay về trang 1.</b> Đang ở trang 7 rồi gõ từ khóa chỉ còn 2 trang kết
 *       quả thì backend trả trang rỗng, người dùng tưởng không tìm thấy gì.
 *   <li><b>Bỏ kết quả của lượt tải cũ.</b> Hai lượt chồng nhau (gõ nhanh, bấm trang liên tiếp) mà
 *       lượt cũ về sau sẽ ghi đè dữ liệu mới hơn.
 * </ul>
 *
 * @param load hàm gọi API — ĐƯỢC PHÉP tạo mới mỗi lần render, hook giữ qua ref nên không gây vòng lặp
 * @param filterDeps các giá trị bộ lọc; đổi một trong số đó thì tải lại từ trang 1
 */
export function usePagedList<T>(
  load: (params: PageParams) => Promise<Page<T>>,
  filterDeps: unknown[],
  options: Options = {},
) {
  const { initialPageSize = 20, errorMessage = 'Không tải được dữ liệu.' } = options

  const [current, setCurrent] = useState(1)
  const [pageSize, setPageSize] = useState(initialPageSize)
  const [sort, setSort] = useState<string | undefined>(undefined)
  const [data, setData] = useState<Page<T>>(() => emptyPage<T>(initialPageSize))
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const loadRef = useRef(load)
  useEffect(() => {
    loadRef.current = load
  })

  const latestLoadId = useRef(0)
  const isFirstRender = useRef(true)
  // Mảng phụ thuộc phải là giá trị nguyên thủy: mảng bộ lọc được tạo mới mỗi lần render nên so sánh
  // theo tham chiếu sẽ tải lại vô hạn.
  const serializedFilters = JSON.stringify(filterDeps)

  useEffect(() => {
    if (isFirstRender.current) {
      isFirstRender.current = false
      return
    }
    setCurrent(1)
  }, [serializedFilters])

  const fetchPage = useCallback(
    async (page: number, size: number, sortParam?: string) => {
      const loadId = ++latestLoadId.current
      setLoading(true)
      try {
        const loaded = await loadRef.current({ page, size, sort: sortParam })
        if (loadId !== latestLoadId.current) {
          return
        }
        setData(loaded)
        setError(null)
        // Trang vừa xin đã vượt quá số trang còn lại — xảy ra khi xóa dòng cuối của trang cuối,
        // hoặc khi nhập Excel làm dữ liệu co lại. Lùi về trang cuối cùng còn dữ liệu thay vì để
        // người dùng nhìn bảng trống trong khi chân bảng vẫn ghi còn vài nghìn dòng.
        if (loaded.content.length === 0 && loaded.totalPages > 0 && page >= loaded.totalPages) {
          setCurrent(loaded.totalPages)
        }
      } catch (caught) {
        if (loadId !== latestLoadId.current) {
          return
        }
        setError(extractErrorMessage(caught, errorMessage))
      } finally {
        if (loadId === latestLoadId.current) {
          setLoading(false)
        }
      }
    },
    [errorMessage],
  )

  // Đổi bộ lọc khi đang ở trang > 1 sẽ bắn 2 lượt tải: một lượt với số trang cũ (do effect này chạy
  // cùng lượt render mà effect reset ở trên mới chỉ kịp lên lịch), rồi một lượt với trang 1. Đó là
  // lý do phải có bộ đếm loadId: lượt phát sau luôn là lượt được giữ, bất kể lượt nào về trước.
  // Từng thử "bỏ qua lượt thừa" bằng một cờ ref — hỏng, vì React chạy effect hai lần ở chế độ phát
  // triển nên cờ bị tiêu mất và bảng kẹt lại ở trang cũ với dữ liệu đã lọc.
  useEffect(() => {
    // oxlint-disable-next-line react/set-state-in-effect
    void fetchPage(toPageParam(current), pageSize, sort)
  }, [fetchPage, current, pageSize, sort, serializedFilters])

  /** Tải lại đúng trang đang xem — dùng sau khi thêm/sửa/xóa/nhập Excel. */
  const reload = useCallback(() => {
    void fetchPage(toPageParam(current), pageSize, sort)
  }, [fetchPage, current, pageSize, sort])

  /**
   * Gắn vào `onChange` của `<Table>`: đổi trang, đổi cỡ trang và đổi cột sắp xếp đều đi qua đây.
   * Sắp xếp phải do backend làm — sắp ở trình duyệt chỉ đụng được 20 dòng của trang đang xem, nên
   * "thanh dài nhất" hiện ra sẽ là thanh dài nhất của trang chứ không phải của cả kho.
   */
  const handleTableChange = useCallback(
    (pagination: TablePaginationConfig, _filters: unknown, sorter: SorterResult<T> | SorterResult<T>[]) => {
      const nextSort = toSortParam(sorter)
      if (nextSort !== sort) {
        // Đổi cột sắp xếp thì thứ tự toàn bộ tập kết quả đổi theo; ở lại trang 5 là xem một lát cắt
        // không còn liên quan đến thao tác vừa rồi.
        setCurrent(1)
        setSort(nextSort)
        return
      }
      setCurrent(pagination.current ?? 1)
      setPageSize(pagination.pageSize ?? initialPageSize)
    },
    [sort, initialPageSize],
  )

  return { data, loading, error, current, pageSize, handleTableChange, reload }
}
