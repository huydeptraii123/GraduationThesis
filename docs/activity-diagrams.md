# 3.5 Activity diagram các luồng nghiệp vụ chính

Các sơ đồ dưới đây bổ sung góc nhìn **luồng hoạt động và quyết định** (thứ tự bước, rẽ nhánh, vòng lặp) cho từng luồng nghiệp vụ chính, khác với góc nhìn **tương tác giữa các lớp** đã thể hiện ở `docs/sequence-diagrams.md`. Hai loại sơ đồ mô tả cùng một luồng nghiệp vụ nhưng phục vụ hai mục đích đọc khác nhau: activity diagram cho biết *hệ thống ra quyết định như thế nào ở mỗi bước*, sequence diagram cho biết *thành phần nào gọi thành phần nào*.

## 1. Luồng nhập dữ liệu từ Excel

Áp dụng chung cho cả 3 loại dữ liệu (đơn hàng, tồn kho thanh nan, định mức BOM) — khóa nghiệp vụ dùng để upsert khác nhau theo loại dữ liệu (đơn hàng: `ycsx`+`z_item`; tồn kho: `slatMaterial`+`doDaiThanhMm`; BOM: `doorProduct`+`slatMaterial`), nhưng luồng xử lý và quy tắc toàn vẹn giao dịch (tất cả-hoặc-không-gì) là như nhau. Actor thực hiện khác nhau theo loại dữ liệu: PLANNER nhập đơn hàng/tồn kho, còn định mức BOM thuộc trách nhiệm của ADMIN (dữ liệu nền tảng, ảnh hưởng trực tiếp đến độ chính xác toàn hệ thống).

```mermaid
flowchart TD
    A([Bắt đầu]) --> B["Người dùng chọn loại dữ liệu cần nhập<br/>(đơn hàng / tồn kho: PLANNER — BOM: ADMIN)<br/>và chọn file Excel"]
    B --> C["Tải file lên qua API import"]
    C --> D["Backend parse file thành danh sách dòng dữ liệu"]
    D --> E{"Còn dòng chưa kiểm tra?"}
    E -- "Có" --> F["Kiểm tra định dạng + trường bắt buộc của dòng hiện tại"]
    F --> G{"Dòng hợp lệ?"}
    G -- "Không" --> H["Ghi nhận lỗi: số dòng + mô tả lỗi"]
    H --> E
    G -- "Có" --> E
    E -- "Không, đã duyệt hết" --> I{"Có dòng lỗi nào không?"}

    I -- "Có" --> J["Hủy toàn bộ, không lưu gì<br/>(đúng yêu cầu toàn vẹn khi nhập liệu)"]
    J --> K["Trả về danh sách lỗi chi tiết theo từng dòng"]
    K --> L["Người dùng xem lỗi, sửa file nguồn, nhập lại từ đầu"]
    L --> Z([Kết thúc])

    I -- "Không" --> M["Mở transaction"]
    M --> N{"Còn dòng chưa upsert?"}
    N -- "Có" --> O{"Bản ghi đã tồn tại theo khóa nghiệp vụ?"}
    O -- "Có" --> P["Cập nhật bản ghi hiện có"]
    O -- "Không" --> Q["Tạo bản ghi mới"]
    P --> N
    Q --> N
    N -- "Không, đã xử lý hết" --> R["Commit transaction"]
    R --> S["Trả về số dòng đã nhập thành công"]
    S --> T["Người dùng thấy thông báo 'Nhập thành công N dòng'"]
    T --> Z
```

Điểm cần lưu ý: hai vòng lặp (kiểm tra định dạng và upsert) được tách rời — chỉ bước sang giai đoạn upsert khi **toàn bộ** các dòng đã qua kiểm tra định dạng, tránh trường hợp nhập được một phần rồi mới phát hiện lỗi ở dòng sau.

Riêng luồng nhập tồn kho có thêm một hành vi không thể hiện ở sơ đồ trên để giữ sơ đồ chung đơn giản: nếu một tổ hợp (loại thanh, độ dài) từng tồn tại trong hệ thống nhưng không còn xuất hiện trong file mới nhập, hệ thống chủ động đưa số thanh còn lại về 0 thay vì giữ nguyên giá trị cũ — khác với hành vi upsert thuần túy (chỉ thêm/sửa, không xóa/reset) áp dụng cho đơn hàng và BOM.
