import { Alert, Button } from 'antd'

interface Props {
  message: string | null
  onRetry: () => void
}

/**
 * Báo lỗi tải danh sách kèm nút thử lại.
 *
 * Có nút thử lại vì các bảng phân trang tải theo trạng thái (trang, cỡ trang, bộ lọc): tải hỏng thì
 * trạng thái vẫn y nguyên, nên bấm lại đúng trang đang đứng sẽ không kích hoạt lượt tải nào cả.
 */
export function ListLoadError({ message, onRetry }: Props) {
  if (!message) {
    return null
  }
  return (
    <Alert
      type="error"
      showIcon
      style={{ marginBottom: 16 }}
      title={message}
      action={
        <Button size="small" onClick={onRetry}>
          Thử lại
        </Button>
      }
    />
  )
}
