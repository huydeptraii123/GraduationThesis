-- Trạng thái "đã duyệt" của đơn hàng, thay cho cách suy ra động từ việc đơn đã có
-- cutting_plan_detail_item/shortage_record nào tham chiếu tới hay chưa.
--
-- Lý do đổi: từ khi tách "tính phương án cắt" (không ghi dữ liệu) khỏi "duyệt phương án cắt",
-- sự tồn tại của bản ghi kết quả không còn đủ để nói đơn đã được chốt hay chưa. Cột này cũng
-- trả lời được câu hỏi "đơn đã duyệt trong phương án nào", thứ mà cách suy ra cũ phải quét
-- ngược hai bảng con mới ra.
ALTER TABLE sales_order
    ADD COLUMN approved_plan_id BIGINT NULL AFTER door_product_id,
    ADD CONSTRAINT fk_sales_order_approved_plan FOREIGN KEY (approved_plan_id) REFERENCES cutting_plan (id),
    ADD INDEX idx_sales_order_approved_plan_id (approved_plan_id);

-- Backfill để dữ liệu đang có giữ nguyên ngữ nghĩa: mọi đơn từng xuất hiện trong một phương án
-- (dù kết quả là cắt được hay thiếu vật tư) đều đã "xử lý xong" theo luật cũ, nên phải mang
-- trạng thái đã duyệt sau khi migrate — nếu không, chúng quay lại hàng chờ và bị đưa vào lần
-- duyệt kế tiếp trong khi tồn kho đã bị trừ từ lần trước.
--
-- Gán phương án có id nhỏ nhất đã dùng đơn đó: theo luật cũ một đơn chỉ được đưa vào đúng một
-- lần chạy, nên trên dữ liệu hợp lệ chỉ có một giá trị; lấy nhỏ nhất là để phòng trường hợp dữ
-- liệu thử nghiệm cũ có đơn lọt vào nhiều lần chạy, khi đó lần đầu tiên mới là lần thật sự
-- tiêu thụ tồn kho cho đơn.
UPDATE sales_order so
JOIN (
    SELECT cpdi.sales_order_id AS so_id, MIN(cpd.cutting_plan_id) AS plan_id
    FROM cutting_plan_detail_item cpdi
    JOIN cutting_plan_detail cpd ON cpd.id = cpdi.cutting_plan_detail_id
    GROUP BY cpdi.sales_order_id
) cut ON cut.so_id = so.id
SET so.approved_plan_id = cut.plan_id;

-- Đơn chỉ sinh ra thiếu vật tư thì không có dòng nào ở cutting_plan_detail_item — phải quét
-- thêm shortage_record. LEAST() giữ đúng "phương án đầu tiên" khi đơn có mặt ở cả hai bảng.
UPDATE sales_order so
JOIN (
    SELECT sr.sales_order_id AS so_id, MIN(sr.cutting_plan_id) AS plan_id
    FROM shortage_record sr
    GROUP BY sr.sales_order_id
) shortage ON shortage.so_id = so.id
SET so.approved_plan_id = LEAST(COALESCE(so.approved_plan_id, shortage.plan_id), shortage.plan_id);
