package com.slatcut.cutting.domain;

/**
 * Phân loại phần dư lưu trên {@link CuttingPlanDetail} — cùng 3 giá trị và cùng ngưỡng với
 * {@code service.optimizer.RemainderCategory}, tách riêng vì domain không phụ thuộc ngược vào tầng
 * service (kiến trúc phân lớp Controller→Service→Repository→Domain ở docs/architecture.md).
 * Ánh xạ 1-1 với {@code RemainderCategory} của thuật toán, ở hai nơi: {@code CuttingPlanService}
 * khi ghi xuống CSDL, và {@code CuttingPlanPreviewMapper} khi trình bày một phương án chưa lưu.
 */
public enum RemainderType {
    DISCARDED,
    WASTE,
    RESTOCK
}
