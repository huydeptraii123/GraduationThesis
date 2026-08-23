# 3.1.2 Biểu đồ ca sử dụng

```mermaid
flowchart LR
    PLANNER(["🧑 PLANNER"])
    ADMIN(["🧑 ADMIN"])

    subgraph SYS["Hệ thống tối ưu cắt nan cửa cuốn"]
        direction TB
        UC1(["Đăng nhập"])
        UC2(["Đăng xuất"])
        UC3(["Đổi mật khẩu cá nhân"])
        UC4(["Nhập đơn hàng từ Excel"])
        UC5(["Xem / tìm kiếm / lọc đơn hàng"])
        UC6(["Chỉnh sửa đơn hàng thủ công"])
        UC7(["Nhập tồn kho từ Excel"])
        UC8(["Xem tồn kho theo loại thanh"])
        UC9(["Cập nhật tồn kho thủ công"])
        UC10(["Sinh phương án cắt"])
        UC11(["Xem / xuất kết quả phương án cắt"])
        UC12(["Quản lý định mức BOM"])
        UC13(["Nhập định mức BOM từ Excel"])
        UC14(["Tra cứu định mức BOM"])
        UC15(["Quản lý tài khoản người dùng"])
    end

    PLANNER --- UC1
    PLANNER --- UC2
    PLANNER --- UC3
    PLANNER --- UC4
    PLANNER --- UC5
    PLANNER --- UC6
    PLANNER --- UC7
    PLANNER --- UC8
    PLANNER --- UC9
    PLANNER --- UC10
    PLANNER --- UC11

    UC1 --- ADMIN
    UC2 --- ADMIN
    UC3 --- ADMIN
    UC12 --- ADMIN
    UC13 --- ADMIN
    UC14 --- ADMIN
    UC15 --- ADMIN
```

*(Mermaid không có ký hiệu "người que" như công cụ vẽ UML chuyên dụng — actor được thay bằng node oval có nhãn 🧑; các đường nối là quan hệ association thông thường, không có mũi tên include/extend vì ở mức MVP các ca sử dụng của hệ thống chưa cần tách biệt include/extend.)*

## Ghi chú từng nhóm ca sử dụng

- **Tài khoản (dùng chung)**: điểm khởi đầu bắt buộc cho cả hai tác nhân trước khi tiếp cận bất kỳ chức năng nào khác; đăng nhập trả về JWT dùng cho các request tiếp theo.
- **Nhóm 1 — Đơn hàng & tồn kho** (chỉ PLANNER): nhập/xem/chỉnh sửa dữ liệu đầu vào của quy trình cắt.
- **Nhóm 3 — Sinh phương án cắt** (chỉ PLANNER): ca sử dụng lõi của khóa luận — "Sinh phương án cắt" kéo theo (include) việc sinh nhu cầu cắt từ BOM và chạy thuật toán 4 mức ưu tiên (xem chi tiết luồng trong `docs/sequence-diagrams.md`).
- **Nhóm 2 — Định mức BOM** (chỉ ADMIN): quản lý dữ liệu nền tảng ít thay đổi nhưng quyết định độ chính xác của nhu cầu cắt sinh ra.
- **Nhóm 4 — Tài khoản & phân quyền** (chỉ ADMIN): quản lý tài khoản PLANNER, gán vai trò.
