package com.slatcut.cutting.repository;

import java.time.LocalDateTime;

/**
 * Trạng thái tóm tắt của một bảng đầu vào của thuật toán cắt: số dòng và mốc sửa gần nhất. Dùng để
 * dựng dấu vân trạng thái ở bước duyệt phương án cắt.
 *
 * <p>Đây là projection duy nhất được dùng chung giữa nhiều repository, khác quy ước "mỗi repository
 * khai báo projection lồng bên trong" của các bảng tổng hợp khác. Lý do: dấu vân là MỘT khái niệm
 * trải trên nhiều bảng, và ba bảng dưới đây phải trả về đúng cùng một dạng dữ liệu thì mới ghép lại
 * thành chuỗi chuẩn hóa được. Tách thành ba interface giống hệt nhau chỉ tạo ba chỗ để lệch.
 *
 * <p>{@code getLastUpdatedAt()} trả về null khi bảng rỗng — {@code MAX} trên tập rỗng là null, và
 * đó là giá trị hợp lệ cần giữ nguyên chứ không thay bằng mặc định: "bảng chưa có dòng nào" và
 * "bảng vừa sửa lúc 0h" là hai trạng thái khác nhau.
 *
 * <p><b>Giới hạn đã biết:</b> {@code updated_at} có độ phân giải 1 giây, nên một lần sửa xảy ra
 * trong cùng giây với lần đọc dấu vân mà không làm đổi số dòng sẽ lọt lưới. Cửa sổ này hẹp hơn hẳn
 * khoảng thời gian PLANNER xem xét phương án (tính bằng phút); thu hẹp nốt cần đổi kiểu cột ở mọi
 * bảng liên quan.
 */
public interface TableState {

    long getRowCount();

    LocalDateTime getLastUpdatedAt();
}
