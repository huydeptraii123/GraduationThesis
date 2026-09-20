package com.slatcut.cutting.dto;

import java.util.List;

/**
 * Một bộ cửa đã thuộc phương án cắt được duyệt nhưng file nguồn mang dữ liệu khác với bản ghi đang
 * lưu. Lượt nhập giữ nguyên bản ghi cũ và trả về dòng này để PLANNER tự quyết định xử lý.
 *
 * <p>Không phải lỗi nhập liệu nên không hủy lượt nhập: file nguồn không sai, chỉ là hệ thống không
 * được phép tự ý áp thay đổi lên một bộ cửa mà nan đã cắt xong (xem
 * docs/requirements-functional.md Nhóm 1).
 *
 * @param changedFields mô tả từng trường bị lệch theo dạng "tên trường: giá trị đã lưu → giá trị
 *     trong file", đủ để PLANNER quyết định mà không phải tự mở lại file nguồn đối chiếu
 */
public record ApprovedOrderConflict(
        String ycsx, Integer item, Long approvedPlanId, List<String> changedFields) {}
