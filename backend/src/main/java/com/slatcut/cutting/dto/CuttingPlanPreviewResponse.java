package com.slatcut.cutting.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Một phương án cắt đã tính xong nhưng chưa được ghi xuống cơ sở dữ liệu. Không có {@code id} vì
 * không có bản ghi nào tồn tại — mỗi lần bấm là một lần tính trên trạng thái tại đúng thời điểm đó
 * (docs/requirements-functional.md Nhóm 3).
 *
 * @param computedAt thời điểm tính; đi kèm kết quả vì đó là thứ duy nhất cho biết con số đang xem
 *     cũ tới mức nào, trong khi đơn hàng và tồn kho thì thay đổi liên tục trong ngày
 * @param blockedOrderCount số đơn chưa duyệt bị loại khỏi phạm vi vì mẫu cửa thiếu định mức dùng
 *     được — hiển thị tách khỏi kết quả, vì đó là đơn đang bị chặn chờ khai báo định mức chứ không
 *     phải đơn đã được xem xét và thấy đủ vật tư
 * @param demands mức chi tiết theo đơn hàng, nguồn dữ liệu của cả các khối biểu đồ lẫn file Excel
 */
public record CuttingPlanPreviewResponse(
        LocalDateTime computedAt,
        int scopeOrderCount,
        long blockedOrderCount,
        BigDecimal totalWasteM,
        BigDecimal totalStockUsedM,
        List<CuttingPlanDemandView> demands) {
}
