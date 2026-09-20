package com.slatcut.cutting.service;

import java.time.LocalDate;

/**
 * Phương án cắt trình cho PLANNER duyệt: đúng phần nội dung của một lần tính, cộng thêm hai thứ chỉ
 * có nghĩa trong luồng duyệt.
 *
 * @param scopeCutoffDate ngày giao xa nhất còn nằm trong đợt duyệt này (t+3). Chức năng tính không
 *     có mốc này — nó cố ý chạy trên toàn bộ đơn tồn — nên mốc không nằm trong
 *     {@link CuttingPlanPreview} mà ở đây
 * @param stateFingerprint ảnh chụp trạng thái đơn hàng và tồn kho tại đúng lượt đọc đã dựng nên
 *     phương án này. PLANNER gửi lại khi bấm duyệt; lệch nghĩa là dữ liệu đã đổi trong lúc xem xét
 *     và phương án đang hiển thị đã lỗi thời, hệ thống từ chối ghi thay vì trừ tồn kho những phôi
 *     thực tế không còn (docs/sequence-diagrams.md mục 2b)
 */
public record CuttingPlanApprovalPreview(
        CuttingPlanPreview plan, LocalDate scopeCutoffDate, String stateFingerprint) {}
