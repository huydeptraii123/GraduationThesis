-- Tử số của "tỷ lệ phế" theo cách doanh nghiệp tính: CHỈ phần dư bị bỏ đi (DISCARDED, dưới 30cm).
-- Phần dư 30cm–3m (WASTE) là vùng cấm mà thuật toán không tạo ra nên không thuộc công thức; dòng WASTE
-- chỉ còn ở dữ liệu lịch sử và nay không được cộng vào tổng phế. remainder_mm là của MỖI phôi nên nhân stick_count.
UPDATE cutting_plan p
SET p.total_waste_m = (
    SELECT COALESCE(SUM(d.stick_count * d.remainder_mm), 0) / 1000
    FROM cutting_plan_detail d
    WHERE d.cutting_plan_id = p.id
      AND d.remainder_type = 'DISCARDED'
);
