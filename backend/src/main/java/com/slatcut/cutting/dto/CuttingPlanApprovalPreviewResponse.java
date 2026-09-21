package com.slatcut.cutting.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Phương án cắt trình cho PLANNER duyệt: nội dung của một lần tính, cộng những gì chỉ có nghĩa
 * trong luồng duyệt.
 *
 * @param scopeCutoffDate ngày giao xa nhất còn nằm trong đợt duyệt này. Chức năng tính không có mốc
 *     này — nó cố ý chạy trên toàn bộ đơn tồn
 * @param stateFingerprint ảnh chụp trạng thái đã dựng nên phương án này; gửi lại khi bấm duyệt để
 *     hệ thống từ chối ghi nếu dữ liệu đã đổi trong lúc xem xét
 * @param details phương án cắt theo từng phôi, đúng cách nhóm sẽ được ghi xuống nếu duyệt. Trường
 *     {@code id} của các dòng con còn rỗng vì chưa có bản ghi nào
 * @param shortages các dòng thiếu vật tư, cũng chưa được ghi
 */
public record CuttingPlanApprovalPreviewResponse(
        LocalDate scopeCutoffDate,
        String stateFingerprint,
        CuttingPlanPreviewResponse plan,
        List<CuttingPlanDetailResponse> details,
        List<ShortageRecordResponse> shortages) {
}
