package com.slatcut.cutting.service.optimizer;

/**
 * Mức ưu tiên mà thuật toán đã dùng để cắt một phôi, ghi lại ngay tại nhánh đã cắt.
 *
 * <p>Phải ghi lại chứ không suy ngược được từ kết quả: một phôi cắt ra đúng một đoạn với phần dư
 * dưới 30cm có thể đến từ {@link #PA1} (khớp gần đúng ngay) hoặc từ {@link #PA3} (ghép nối nhưng
 * đối tác ghép chỉ đóng góp một đoạn) — hai phương án khác nhau, cùng một dấu vết. Báo cáo gửi
 * xuống xưởng phải nói đúng phương án nào đã được áp dụng.
 *
 * <p>Tên giữ nguyên ký hiệu doanh nghiệp đang dùng trong khuôn mẫu báo cáo của họ, không đổi sang
 * tên mô tả cho "dễ đọc" — file xuất ra phải khớp thứ người dùng đang đối chiếu hằng ngày.
 */
public enum CutLevel {

    /** Mức 1 — khớp gần đúng: thanh ngắn nhất đủ dài, phần dư dưới 30cm. */
    PA1,

    /** Mức 2 — cắt theo bội số: một thanh dài đúng k lần độ dài đoạn, không phát sinh phần dư. */
    PA2,

    /** Mức 3 — ghép nối: hai đoạn của hai đơn khác nhau dùng chung một thanh. */
    PA3,

    /** Mức 4 — cắt để lại phần dư trên 3m, nhập lại kho dùng tiếp. */
    PA4
}
