import { Alert, Tag, Typography } from 'antd'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ROLE_COLOR, type Role } from '../auth/permissions'

const STORAGE_KEY = 'slatcut.gettingStartedDismissed'

/**
 * Các bước theo đúng thứ tự dùng hệ thống. Tên màn hình phải trùng CHỮ với mục trên trình đơn
 * (`MENU_ITEMS` ở `AppLayout`) để người mới tìm thấy ngay — đổi tên mục menu thì sửa cả ở đây.
 */
const STEPS: { path: string; label: string; role?: Role; hint: string }[] = [
  { path: '/bom', label: 'Định mức BOM', role: 'ADMIN', hint: 'nhập định mức vật tư trước tiên.' },
  { path: '/inventory', label: 'Tồn kho thanh nan', role: 'PLANNER', hint: 'nhập tồn kho sau khi đã có định mức.' },
  { path: '/sales-orders', label: 'Đơn hàng', role: 'PLANNER', hint: 'nhập đơn hàng sau cùng.' },
  {
    path: '/',
    label: 'Trang chủ',
    hint: 'bấm "Tính phương án cắt" để xem trước thiếu hụt vật tư của toàn bộ đơn — chỉ đọc, không ghi gì.',
  },
  {
    path: '/cutting-plans/approval',
    label: 'Duyệt phương án cắt',
    role: 'PLANNER',
    hint: 'chốt đợt cắt (giao trong 3 ngày, tối đa 70 đơn) — trừ tồn kho và lưu lịch sử.',
  },
]

/** Đọc/ghi `localStorage` có thể ném lỗi (trình duyệt chặn lưu trữ) — khi đó coi như chưa đóng. */
function readDismissed(): boolean {
  try {
    return window.localStorage.getItem(STORAGE_KEY) === '1'
  } catch {
    return false
  }
}

function saveDismissed(): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, '1')
  } catch {
    // Không lưu được thì lần sau banner hiện lại — chấp nhận được, không phải lỗi cần báo.
  }
}

/**
 * Hướng dẫn thứ tự dùng hệ thống cho người mới, theo yêu cầu của doanh nghiệp: đóng được, và nhớ đã
 * đóng trên trình duyệt này để không hiện lại mỗi lần vào trang chủ.
 *
 * Thứ tự nhập định mức → tồn kho → đơn hàng là bắt buộc chứ không chỉ là gợi ý: nhập tồn kho trước
 * định mức thì mã thanh nan mới bị tạo vào nhóm "Khác", và lượt nhập định mức sau đó không ghi đè
 * nhóm của mã đã có — lỗi mà lần dùng thử đầu tiên đã gặp. Vì vậy câu cảnh báo đó nằm ngay trong
 * banner thay vì chỉ có trong tài liệu, kèm đúng cách sửa: sửa nhóm từng mã. Đặt lại dữ liệu KHÔNG
 * phải cách sửa — nó giữ nguyên danh mục loại thanh nan, nên hai câu được tách riêng để không ai
 * đọc thành "nhập sai thì đặt lại".
 */
export function GettingStartedBanner() {
  const [dismissed, setDismissed] = useState(readDismissed)

  if (dismissed) {
    return null
  }

  return (
    <Alert
      type="info"
      showIcon
      closable={{ 'aria-label': 'Đóng hướng dẫn' }}
      onClose={() => {
        saveDismissed()
        setDismissed(true)
      }}
      style={{ marginBottom: 16 }}
      title="Thứ tự dùng hệ thống"
      description={
        <div>
          <ol style={{ margin: '4px 0 8px', paddingInlineStart: 20 }}>
            {STEPS.map((step) => (
              <li key={step.path}>
                <Link to={step.path}>{step.label}</Link>
                {step.role && (
                  <Tag
                    color={ROLE_COLOR[step.role]}
                    style={{ marginInlineStart: 6, marginInlineEnd: 4, fontSize: 10, lineHeight: '16px' }}
                  >
                    {step.role}
                  </Tag>
                )}
                {step.role ? '' : ' '}— {step.hint}
              </li>
            ))}
            <li>
              <Link to="/cutting-plans">Lịch sử duyệt</Link> và{' '}
              <Link to="/shortage-report">Báo cáo thiếu vật tư</Link> — xem lại các đợt đã duyệt và vật tư còn thiếu
              của toàn hệ thống.
            </li>
          </ol>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 4 }}>
            Nhập sai thứ tự (tồn kho trước định mức) làm loại thanh nan mới bị xếp vào nhóm "Khác": đơn hàng bị chặn,
            hoặc bị bỏ sót một phần định mức mà vẫn có thể hiện "Đủ nan". Khi đó phải sửa nhóm của từng mã ở{' '}
            <Link to="/inventory">Tồn kho thanh nan</Link>, thẻ "Danh mục loại thanh nan" — đặt lại dữ liệu không sửa
            được việc này vì nó giữ nguyên danh mục loại thanh nan.
          </Typography.Paragraph>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            Cần nhập lại tồn kho và đơn hàng từ đầu (ví dụ khách đổi kích thước cửa) thì ADMIN dùng{' '}
            <Link to="/data-reset">Đặt lại dữ liệu</Link> — định mức được giữ nguyên.
          </Typography.Paragraph>
        </div>
      }
    />
  )
}
