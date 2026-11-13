-- Đổi mẫu số của "tỷ lệ phế" theo cách doanh nghiệp tính: phần dư bị bỏ đi chia cho TỔNG ĐỘ DÀI
-- THANH đã cắt. total_stock_used_m nay là tổng độ dài mọi phôi đã cắt, không trừ phần dư nhập lại
-- kho (RESTOCK) như V10. remainder_mm/source_length_mm là của MỖI phôi nên phải nhân stick_count.
UPDATE cutting_plan p
SET p.total_stock_used_m = (
    SELECT COALESCE(SUM(d.stick_count * d.source_length_mm), 0) / 1000
    FROM cutting_plan_detail d
    WHERE d.cutting_plan_id = p.id
);
