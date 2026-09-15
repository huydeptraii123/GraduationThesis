package com.slatcut.cutting.service.optimizer;

/** Ngưỡng phân loại phần dư sau cắt — đã chốt ở .claude/CLAUDE.md mục Bối cảnh. */
public enum RemainderCategory {
    /** < 30cm — chấp nhận bỏ đi, hao tổn nhỏ không đáng kể. */
    DISCARDED,
    /** 30cm-3m — cần hạn chế tối đa, không đủ tái sử dụng ngay và không đủ nhỏ để bỏ qua. */
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
