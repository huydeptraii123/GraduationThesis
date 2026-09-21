package com.slatcut.cutting.service;

import com.slatcut.cutting.domain.SalesOrder;
import com.slatcut.cutting.service.optimizer.CuttingPlanResult;
import com.slatcut.cutting.service.optimizer.StockLine;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Một phương án cắt đã tính xong nhưng CHƯA được ghi xuống cơ sở dữ liệu — kết quả của chức năng
 * "tính phương án cắt", và cũng là thứ được trình cho PLANNER xem trước khi duyệt.
 *
 * <p>Không có {@code id} vì không có bản ghi nào tồn tại: đơn hàng và tồn kho thay đổi liên tục
 * trong ngày nên một lần tính chỉ có nghĩa với trạng thái tại đúng thời điểm bấm, lưu lại chỉ tạo
 * ra một lịch sử toàn số liệu đã lỗi thời (docs/requirements-functional.md Nhóm 3). Thời điểm tính
 * vì vậy phải đi kèm kết quả — nó là thứ duy nhất cho biết con số đang xem cũ tới mức nào.
 *
 * @param scopeOrders đơn hàng đã đưa vào lần tính này, giữ nguyên thứ tự ưu tiên đã sắp
 * @param blockedOrderCount số đơn chưa duyệt bị loại khỏi phạm vi vì mẫu cửa không có dòng định mức
 *     nào dùng được — đếm và hiển thị tách khỏi kết quả, vì đó là đơn đang BỊ CHẶN chờ khai báo
 *     định mức chứ không phải đơn đã được xem xét và thấy đủ vật tư
 * @param totalStockUsedM tồn kho thực tiêu hao, tức mẫu số của tỷ lệ phế
 * @param stockAtStart tồn kho của các loại thanh nan có mặt trong lần chạy, ngay TRƯỚC khi thuật
 *     toán tiêu thụ — báo cáo in nguyên danh sách này ở cột ảnh chụp tồn kho
 * @param stockAfterRun số thanh còn lại của từng (loại thanh nan, độ dài) SAU lần chạy — con số ở
 *     cuối mỗi mệnh đề của cột mô tả cách cắt
 */
public record CuttingPlanPreview(
        LocalDateTime computedAt,
        List<SalesOrder> scopeOrders,
        CuttingPlanResult result,
        long blockedOrderCount,
        BigDecimal totalWasteM,
        BigDecimal totalStockUsedM,
        List<StockLine> stockAtStart,
        List<StockLine> stockAfterRun) {}
