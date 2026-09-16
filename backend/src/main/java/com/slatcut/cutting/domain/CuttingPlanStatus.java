package com.slatcut.cutting.domain;

/** Trạng thái 1 lần chạy CuttingPlanService.generate(). FAILED mang tính dự phòng — trong phạm vi
 * khóa luận, transaction rollback khi lỗi thì không có dòng nào được lưu, nên chỉ COMPLETED được set. */
public enum CuttingPlanStatus {
    COMPLETED,
    FAILED
}
