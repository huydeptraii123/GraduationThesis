-- Lệnh sản xuất của bộ cửa, để in ra báo cáo cho khớp chứng từ xưởng đang dùng.
--
-- Cột nguồn tên là `order` — trùng từ khóa dự trữ của MySQL nên phải đổi tên khi ánh xạ, cùng lý do
-- với `slat_group`. Để NULL được: đơn tạo thủ công không có mã này, và hồ sơ nguồn cũ nhập trước
-- khi có cột này cũng không có (xem docs/domain-model.md).
ALTER TABLE sales_order
    ADD COLUMN lenh_sx BIGINT NULL AFTER sales_order_item;
