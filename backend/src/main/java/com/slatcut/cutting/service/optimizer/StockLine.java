package com.slatcut.cutting.service.optimizer;

/**
 * Một dòng tồn kho ở dạng gọn nhất: loại thanh nan nào, độ dài nào, còn bao nhiêu thanh.
 *
 * <p>Dùng để mang trạng thái kho ra khỏi một lần chạy thuật toán — ảnh chụp lúc bắt đầu (đi vào
 * bảng ảnh chụp của phương án đã duyệt) và số thanh còn lại lúc kết thúc (đi vào cột "còn lại bao
 * nhiêu phôi" của báo cáo). Cố ý không mang theo entity: hai con số này được đọc lại rất lâu sau
 * lần chạy, khi mọi entity đã detached từ đời nào.
 */
public record StockLine(Long slatMaterialId, int lengthMm, int stickCount) {}
