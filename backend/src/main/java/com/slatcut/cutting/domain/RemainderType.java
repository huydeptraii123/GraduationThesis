package com.slatcut.cutting.domain;

/**
 * Phân loại phần dư lưu trên {@link CuttingPlanDetail} — cùng 3 giá trị và cùng ngưỡng với
 * {@code service.optimizer.RemainderCategory}, tách riêng vì domain không phụ thuộc ngược vào tầng
 * service (kiến trúc phân lớp Controller→Service→Repository→Domain ở docs/architecture.md).
 * {@code CuttingPlanService} ánh xạ 1-1 giữa 2 enum này khi persist.
 */
public enum RemainderType {
    DISCARDED,
    WASTE,
    RESTOCK
}
