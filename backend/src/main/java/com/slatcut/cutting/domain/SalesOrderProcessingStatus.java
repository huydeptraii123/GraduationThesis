package com.slatcut.cutting.domain;

/**
 * Trạng thái xử lý của một bộ cửa, hiển thị và dùng để lọc ở màn đơn hàng
 * (docs/requirements-functional.md Nhóm 1, công thức ở docs/domain-model.md mục 3.3.1).
 *
 * <p><b>Trạng thái suy ra, không phải cột.</b> Chỉ sự kiện "đã duyệt" có cột riêng
 * ({@code approved_plan_id}); phần còn lại đọc từ định mức của mẫu cửa và từ
 * {@code shortage_record} của phương án đã duyệt đơn đó. Thêm cột đệm cho đủ/thiếu sẽ tạo ra
 * đúng rủi ro trạng thái lệch với bảng con mà thiết kế đã tránh.
 *
 * <p>Bốn giá trị loại trừ nhau và phủ kín mọi đơn: hai giá trị đầu chỉ có ở đơn chưa duyệt, hai
 * giá trị sau chỉ có ở đơn đã duyệt. Điều kiện cụ thể nằm ở một chỗ duy nhất,
 * {@code SalesOrderSpecifications.statusIs}, dùng chung cho cả bộ lọc lẫn cột hiển thị.
 */
public enum SalesOrderProcessingStatus {
    /** Chưa duyệt, mẫu cửa có định mức dùng được — đang chờ tới lượt trong hàng chờ. */
    PENDING,
    /** Chưa duyệt, mẫu cửa không có dòng định mức nào dùng được — phải chờ ADMIN khai báo định mức. */
    BLOCKED,
    /** Đã duyệt, phương án duyệt nó không ghi nhận thiếu loại thanh nào. */
    SUFFICIENT,
    /** Đã duyệt, phương án duyệt nó ghi nhận thiếu ít nhất một loại thanh. */
    SHORTAGE
}
