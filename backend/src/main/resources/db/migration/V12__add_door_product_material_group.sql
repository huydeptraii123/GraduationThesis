-- Model cửa (CA-CA10, CA-AF100...) — trục của biểu đồ "số bộ cửa theo model" ở báo cáo tổng quan.
-- Giá trị luôn bắt đầu bằng "CA-": luồng nhập đơn hàng dùng chính tiền tố đó để nhận ra dòng cửa
-- giữa các dòng phụ kiện, nên dòng không phải cửa không bao giờ tới được cột này.
--
-- Để NULL được vì door_product được tạo ra từ CẢ HAI luồng nhập, mà cột này chỉ có trong file đơn
-- hàng: một mẫu cửa mới biết tới qua luồng định mức sẽ tạm chưa có model, và được điền khi luồng
-- nhập đơn hàng gặp đúng mẫu cửa đó (xem docs/domain-model.md).
ALTER TABLE door_product
    ADD COLUMN material_group VARCHAR(50) NULL AFTER z_mau_sac;
