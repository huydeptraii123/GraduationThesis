package com.slatcut.cutting.config;

/**
 * Yêu cầu hợp lệ về mặt hình thức nhưng không thực hiện được vì trạng thái nghiệp vụ hiện tại không
 * cho phép — khác {@link ConflictException} ở chỗ không có xung đột nào với dữ liệu đã có, đơn giản
 * là không có gì để làm.
 *
 * <p>Tách riêng khỏi {@code ConflictException} vì giao diện phải phản ứng khác nhau: mã 409 kéo
 * theo việc tính lại phương án trên trạng thái mới, còn mã 422 thì tính lại hoàn toàn vô ích và chỉ
 * cần hiện một thông báo. Gộp chung một mã sẽ buộc giao diện đoán ý nghĩa qua nội dung câu thông
 * báo (xem docs/sequence-diagrams.md, sơ đồ luồng duyệt phương án cắt).
 */
public class UnprocessableRequestException extends RuntimeException {

    public UnprocessableRequestException(String message) {
        super(message);
    }
}
