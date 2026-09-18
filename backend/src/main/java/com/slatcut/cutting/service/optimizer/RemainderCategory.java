package com.slatcut.cutting.service.optimizer;

/**
 * Ngưỡng phân loại phần dư sau cắt, theo yêu cầu nghiệp vụ của doanh nghiệp.
 *
 * <p>Ba mức này không đối xứng về vai trò: {@link #DISCARDED} và {@link #RESTOCK} là kết quả chấp
 * nhận được, còn {@link #WASTE} là thứ phải tránh tạo ra — và thuật toán hiện tại tránh bằng cách
 * không bao giờ cắt một thanh nếu phần dư rơi vào khoảng đó (xem
 * {@link InventoryPool#findRestockFit}), thà báo thiếu vật tư.
 */
public enum RemainderCategory {
    /** < 30cm — chấp nhận bỏ đi, hao tổn nhỏ không đáng kể. */
    DISCARDED,
    /**
     * 30cm-3m — không đủ tái sử dụng ngay và không đủ nhỏ để bỏ qua.
     *
     * <p>Thuật toán KHÔNG còn sinh ra nhãn này: cả 4 mức cắt đều chỉ nhận thanh cho phần dư &lt; 30cm
     * hoặc &gt; 3m, hết cách thì báo thiếu vật tư. Nhãn vẫn tồn tại vì các phương án cắt đã lưu từ
     * trước khi luật này chặt lại còn mang nó, và báo cáo phế liệu vẫn phải đọc được dữ liệu đó.
     */
    WASTE,
    /** > 3m — nhập lại kho, chờ ghép với đơn hàng phù hợp sau. */
    RESTOCK;

    /** Dùng lại ở InventoryPool.findNearFit()/findCombination() — cùng ngưỡng "dư dự kiến < 30cm". */
    public static final int DISCARD_THRESHOLD_MM = 300;
    public static final int RESTOCK_THRESHOLD_MM = 3000;

    public static RemainderCategory classify(int remainderMm) {
        if (remainderMm < DISCARD_THRESHOLD_MM) {
            return DISCARDED;
        }
        if (remainderMm > RESTOCK_THRESHOLD_MM) {
            return RESTOCK;
        }
        return WASTE;
    }
}
