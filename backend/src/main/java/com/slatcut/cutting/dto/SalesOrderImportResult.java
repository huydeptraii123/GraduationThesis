package com.slatcut.cutting.dto;

import java.util.List;

/**
 * Kết quả 1 lượt nhập đơn hàng. Ba con số/danh sách đứng riêng vì chúng trả lời ba câu hỏi khác
 * nhau: bao nhiêu dòng vào được hệ thống, bao nhiêu dòng bị bỏ vì không phải bộ cửa, và bộ cửa nào
 * bị bỏ qua vì đã thuộc phương án cắt được duyệt mà file nguồn lại mang dữ liệu khác.
 *
 * @param approvedOrderConflicts rỗng trong mọi lượt nhập bình thường — chỉ có phần tử khi dữ liệu
 *     nguồn của một bộ cửa đã cắt xong bị sửa lại, tình huống cần PLANNER quyết định chứ hệ thống
 *     không tự xử lý được
 */
public record SalesOrderImportResult(
        int totalRowsImported, int skippedNonDoorRows, List<ApprovedOrderConflict> approvedOrderConflicts) {
}
