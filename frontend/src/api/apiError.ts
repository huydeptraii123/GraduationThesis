import { AxiosError } from 'axios'

/**
 * Backend trả lỗi theo 3 hình dạng khác nhau (xem GlobalExceptionHandler phía server):
 * - 404 / 409: body là chuỗi văn bản thuần;
 * - 400 của luồng import: body là mảng JSON các lỗi theo dòng;
 * - 400 do @Valid: không có handler riêng nên rơi về body lỗi mặc định của Spring.
 * Gom việc bóc tách vào một chỗ để mọi màn hình hiển thị lỗi giống nhau.
 */

export interface ImportRowError {
  rowNumber: number
  message: string
}

const STATUS_FALLBACK: Record<number, string> = {
  400: 'Dữ liệu gửi lên không hợp lệ, vui lòng kiểm tra lại các trường đã nhập.',
  401: 'Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.',
  403: 'Tài khoản của bạn không có quyền thực hiện thao tác này.',
  404: 'Không tìm thấy dữ liệu tương ứng.',
  409: 'Dữ liệu xung đột với ràng buộc hiện có.',
  413: 'File vượt quá dung lượng cho phép (tối đa 10MB).',
}

function isImportRowErrorList(data: unknown): data is ImportRowError[] {
  return (
    Array.isArray(data) &&
    data.length > 0 &&
    data.every((item) => typeof item === 'object' && item !== null && 'rowNumber' in item && 'message' in item)
  )
}

/** Trả về danh sách lỗi theo dòng của luồng import, hoặc null nếu lỗi không thuộc dạng đó. */
export function extractImportRowErrors(error: unknown): ImportRowError[] | null {
  if (error instanceof AxiosError && isImportRowErrorList(error.response?.data)) {
    return error.response.data
  }
  return null
}

/** Trả về một câu tiếng Việt hiển thị được cho người dùng, không bao giờ trả chuỗi rỗng. */
export function extractErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof AxiosError)) {
    return error instanceof Error && error.message ? error.message : fallback
  }

  const response = error.response
  if (!response) {
    return 'Không kết nối được tới máy chủ, vui lòng kiểm tra kết nối mạng.'
  }

  const data: unknown = response.data
  if (typeof data === 'string') {
    const text = data.trim()
    // Lỗi do proxy/nginx trả về là cả một trang HTML, không phải câu thông báo của backend.
    if (text && !text.startsWith('<')) {
      return text
    }
  }
  if (isImportRowErrorList(data)) {
    return `File có ${data.length} dòng dữ liệu không hợp lệ.`
  }
  if (typeof data === 'object' && data !== null) {
    const problem = data as Record<string, unknown>
    // Body JSON duy nhất mà backend trả về hiện nay là trang lỗi mặc định của Spring, trong đó
    // `error`/`message` chỉ là tên mã trạng thái hoặc câu kỹ thuật tiếng Anh ("Bad Request",
    // "Validation failed for object=..."), kém hơn hẳn câu tiếng Việt trong STATUS_FALLBACK.
    // Chỉ đọc `detail` để sẵn sàng cho trường hợp sau này bật ProblemDetail với mô tả thật.
    const detail = problem.detail
    if (typeof detail === 'string' && detail.trim()) {
      return detail.trim()
    }
  }

  return STATUS_FALLBACK[response.status] ?? fallback
}
