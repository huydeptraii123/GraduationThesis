import { useEffect, useState } from 'react'

/**
 * Trả về giá trị chỉ sau khi người dùng ngừng gõ `delay` mili giây.
 *
 * Từ khi danh sách lọc ở server, mỗi ký tự gõ vào ô tìm kiếm là một lượt gọi API; không có độ trễ
 * này thì gõ "nan chính" bắn đi 9 request, và request về sau có thể về trước request cuối.
 */
export function useDebouncedValue<T>(value: T, delay = 300): T {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delay)
    return () => window.clearTimeout(timer)
  }, [value, delay])

  return debounced
}
