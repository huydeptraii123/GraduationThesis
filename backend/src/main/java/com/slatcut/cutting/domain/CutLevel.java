package com.slatcut.cutting.domain;

/**
 * Bản ánh xạ của {@code service.optimizer.CutLevel} xuống cột {@code cutting_plan_detail.cut_level}.
 *
 * <p>Hai enum tách rời nhau đúng như cặp {@code RemainderCategory}/{@link RemainderType} đã có: lớp
 * miền không phụ thuộc vào lớp thuật toán, và một mức mới thêm vào thuật toán không lặng lẽ trở
 * thành giá trị hợp lệ của cột CSDL trước khi migration kịp mở rộng ENUM.
 */
public enum CutLevel {
    PA1,
    PA2,
    PA3,
    PA4
}
