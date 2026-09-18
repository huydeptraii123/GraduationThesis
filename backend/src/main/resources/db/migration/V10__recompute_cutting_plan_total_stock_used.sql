-- Tính lại total_stock_used_m theo đúng nghĩa "tồn kho thực tiêu hao": tổng độ dài phôi xuất kho
-- trừ phần dư được nhập lại kho (RESTOCK). Công thức cũ cộng thẳng độ dài mọi phôi nên phần dư >3m
-- vừa nhập lại kho vừa bị cắt tiếp trong cùng lượt chạy bị đếm hai lần, làm mẫu số của "tỷ lệ phế"
-- phồng lên. remainder_mm là phần dư của MỖI phôi nên phải nhân stick_count.
UPDATE cutting_plan p
SET p.total_stock_used_m = (
    SELECT COALESCE(SUM(
        d.stick_count * (d.source_length_mm - CASE WHEN d.remainder_type = 'RESTOCK' THEN d.remainder_mm ELSE 0 END)
    ), 0) / 1000
    FROM cutting_plan_detail d
    WHERE d.cutting_plan_id = p.id
);
