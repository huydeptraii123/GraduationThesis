import type { ThemeConfig } from 'antd'

/**
 * Token giao diện toàn hệ thống, lấy nguyên từ bộ nhận diện của bản thiết kế.
 *
 * Bo góc 0 không phải sở thích thẩm mỹ mà là góp ý vận hành của doanh nghiệp: giao diện công
 * nghiệp, cạnh sắc. Đặt ở đây một lần để mọi nút, thẻ, ô nhập và hộp thoại nhận cùng một luật,
 * thay vì mỗi màn tự ghi đè bằng `style`.
 */
export const APP_THEME: ThemeConfig = {
  token: {
    colorPrimary: '#1677ff',
    colorSuccess: '#52c41a',
    colorWarning: '#faad14',
    colorError: '#ff4d4f',
    colorInfo: '#1677ff',
    borderRadius: 0,
    fontFamily: 'Arial, Helvetica, sans-serif',
    colorBgLayout: '#f5f5f5',
  },
  components: {
    Layout: {
      headerBg: '#1677ff',
      headerHeight: 64,
      siderBg: '#ffffff',
      triggerBg: '#ffffff',
      triggerColor: '#595959',
    },
    // Lề của mục menu thu lại để nhãn dài nhất ("Duyệt phương án cắt") cộng tag vai trò vừa đúng
    // sider 220px: đo thật, vùng chữ mặc định chỉ rộng 147px trong khi cặp nhãn + tag cần 167px.
    Menu: {
      itemSelectedBg: '#e6f4ff',
      itemSelectedColor: '#1677ff',
      itemMarginInline: 0,
      itemPaddingInline: 8,
    },
  },
}
