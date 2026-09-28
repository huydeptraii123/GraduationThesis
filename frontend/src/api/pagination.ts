/**
 * Một trang kết quả từ backend (`PageResponse` phía Java).
 *
 * Lưu ý về cách đếm trang: backend đếm từ 0, còn `pagination.current` của AntD đếm từ 1. Mọi chỗ quy
 * đổi đi qua `toPageParam`/`toCurrentPage` dưới đây để không rải phép ±1 khắp các bảng — lệch một
 * đơn vị ở đây nghĩa là người dùng bấm trang 1 nhưng nhận dữ liệu trang 2.
 */
export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/** Tham số phân trang dùng chung cho mọi lời gọi API danh sách. */
export interface PageParams {
  page: number
  size: number
  /**
   * Dạng `field,asc` / `field,desc` đúng cú pháp Spring Data. Bỏ trống thì backend dùng thứ tự mặc
   * định của từng endpoint (vd đơn hàng sắp theo ngày giao).
   */
  sort?: string
}

/** AntD (1-based) → backend (0-based). */
export function toPageParam(current: number): number {
  return Math.max(0, current - 1)
}

/** backend (0-based) → AntD (1-based). */
export function toCurrentPage(page: number): number {
  return page + 1
}

/**
 * Cỡ trang mặc định của MỌI bảng: 10 dòng. Danh sách dài thì người dùng tự chọn cỡ lớn hơn — mặc định
 * 20 khiến một màn có hai bảng xếp chồng dài quá một màn hình mà phần lớn thời gian không cần.
 */
export const DEFAULT_PAGE_SIZE = 10

const PAGE_SIZE_OPTIONS = ['10', '20', '50', '100']

/** Trang rỗng dùng làm giá trị khởi tạo, tránh phải kiểm tra null ở mọi bảng. */
export function emptyPage<T>(size = DEFAULT_PAGE_SIZE): Page<T> {
  return { content: [], page: 0, size, totalElements: 0, totalPages: 0 }
}

/**
 * Cấu hình phân trang dùng chung cho `<Table>`: cùng một bộ cỡ trang và cùng cách hiển thị tổng số
 * ở mọi màn hình.
 *
 * `current`/`pageSize` lấy từ trạng thái của `usePagedList` chứ KHÔNG lấy từ trang mà máy chủ vừa
 * trả về: nếu lấy theo phản hồi thì một lượt tải lỗi sẽ khiến thanh phân trang nhảy ngược về trang
 * cũ, và người dùng bấm lại đúng trang đó cũng không tải lại được vì trạng thái không đổi.
 *
 * Cố ý KHÔNG gắn `onChange` ở đây — việc đổi trang đi qua `onChange` của chính `<Table>` (xem
 * `usePagedList().handleTableChange`) để đổi trang và đổi cột sắp xếp chỉ có một đường vào.
 */
export function tablePagination(
  state: { current: number; pageSize: number; total: number },
  showTotal: (total: number) => string,
) {
  return {
    current: state.current,
    pageSize: state.pageSize,
    total: state.total,
    showSizeChanger: true,
    pageSizeOptions: PAGE_SIZE_OPTIONS,
    showTotal,
  }
}

/**
 * Phân trang cho bảng có TOÀN BỘ dữ liệu sẵn ở trình duyệt (không gọi lại máy chủ khi đổi trang).
 *
 * Phải dùng `defaultPageSize`, KHÔNG dùng `pageSize`: truyền `pageSize` là biến cỡ trang thành giá trị
 * cố định do nơi gọi nắm giữ — người dùng chọn "20 / trang" thì bảng lập tức bị ép về số cũ, ô chọn
 * cỡ trang hiện ra nhưng bấm không có tác dụng gì. Đây đúng là lỗi từng có ở 5 bảng.
 */
export function localPagination(showTotal: (total: number) => string) {
  return {
    defaultPageSize: DEFAULT_PAGE_SIZE,
    showSizeChanger: true,
    pageSizeOptions: PAGE_SIZE_OPTIONS,
    showTotal,
  }
}
