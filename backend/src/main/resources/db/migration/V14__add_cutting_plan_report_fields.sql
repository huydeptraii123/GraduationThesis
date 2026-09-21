-- Ba thứ mà báo cáo phương án cắt cần đọc lại được NGUYÊN TRẠNG sau nhiều tuần.

-- Mức ưu tiên mà thuật toán đã dùng cho phôi này. Phải lưu chứ không suy ngược được từ kết quả:
-- một phôi cắt ra đúng một đoạn với phần dư dưới 30cm có thể đến từ mức 1 (khớp gần đúng ngay) hoặc
-- từ mức 3 (ghép nối nhưng đối tác ghép chỉ có một đoạn) — xem docs/domain-model.md.
--
-- Số phôi cùng loại và cùng độ dài còn lại sau lần chạy. Cũng phải lưu, vì tồn kho đổi hằng ngày:
-- đọc lại inventory_batch lúc xuất báo cáo thì cùng một phương án xuất ra ở hai thời điểm cho hai
-- con số khác nhau, trong khi chứng từ đã phát hành xuống xưởng thì phải bất biến.
--
-- Cả hai để NULL được: phương án đã lưu trước migration này không có thông tin đó, và không suy
-- đoán ngược cho dữ liệu lịch sử.
ALTER TABLE cutting_plan_detail
    ADD COLUMN cut_level ENUM('PA1', 'PA2', 'PA3', 'PA4') NULL AFTER pattern_code,
    ADD COLUMN remaining_sticks_after INT NULL AFTER stick_count;

-- Ảnh chụp tồn kho tại thời điểm bắt đầu một lần duyệt. Bảng DUY NHẤT trong hệ thống lưu dữ liệu
-- chỉ để phục vụ báo cáo chứ không tham gia tính toán: không có nó, mở lại phương án đã duyệt từ
-- vài tuần trước sẽ hiển thị tồn kho của HÔM NAY bên cạnh phương án cắt của HÔM ĐÓ.
CREATE TABLE cutting_plan_stock_snapshot (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    cutting_plan_id   BIGINT NOT NULL,
    slat_material_id  BIGINT NOT NULL,
    do_dai_thanh_mm   INT    NOT NULL,
    so_thanh          INT    NOT NULL,
    CONSTRAINT fk_cutting_plan_stock_snapshot_plan FOREIGN KEY (cutting_plan_id) REFERENCES cutting_plan (id),
    CONSTRAINT fk_cutting_plan_stock_snapshot_slat_material FOREIGN KEY (slat_material_id) REFERENCES slat_material (id),
    CONSTRAINT uk_cutting_plan_stock_snapshot UNIQUE (cutting_plan_id, slat_material_id, do_dai_thanh_mm),
    INDEX idx_cutting_plan_stock_snapshot_plan_material (cutting_plan_id, slat_material_id)
);
