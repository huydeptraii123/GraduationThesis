-- Tổng độ dài thiếu lưu đủ tới milimet.
--
-- Mọi độ dài trong hệ thống là số nguyên milimet, nên quy ra mét cần đúng 3 chữ số thập phân. Hai
-- chữ số cũ làm mất milimet lẻ ngay lúc ghi: một đoạn 2345mm thiếu toàn bộ đọc lại thành 2350mm,
-- và báo cáo xuất xuống xưởng ghi sai độ dài cần cắt bù. Doanh nghiệp yêu cầu mọi độ dài trên báo
-- cáo chính xác tuyệt đối, không làm tròn.
--
-- Chỉ nới kiểu cột, không đổi giá trị: các dòng đã ghi trước migration này vẫn giữ con số đã làm
-- tròn tới centimet — milimet lẻ của chúng đã mất từ lúc ghi và không suy ngược lại được.
ALTER TABLE shortage_record
    MODIFY COLUMN missing_length_m DECIMAL(10, 3) NOT NULL;
