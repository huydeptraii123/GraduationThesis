# 3.1.1 Đối tượng người dùng và phạm vi hệ thống

## Đối tượng người dùng

Hệ thống phục vụ hai tác nhân, phân theo đúng ranh giới trách nhiệm nghiệp vụ thực tế của doanh nghiệp:

**PLANNER** (nhân viên kế hoạch) — người vận hành nghiệp vụ cắt hằng ngày: nhập đơn hàng khách và tồn kho thanh nan (hàng loạt từ Excel xuất ra từ hệ thống nghiệp vụ hiện có, hoặc chỉnh sửa thủ công khi cần điều chỉnh cục bộ), và là người duy nhất được chạy thuật toán sinh phương án cắt cùng xem/xuất kết quả để chỉ đạo sản xuất. Đây là vai trò tương tác với hệ thống thường xuyên nhất, cần các thao tác nhanh, rõ ràng để không làm gián đoạn nhịp làm việc hằng ngày của xưởng cắt.

**ADMIN** (quản trị viên) — người quản lý dữ liệu nền tảng ít thay đổi nhưng ảnh hưởng trực tiếp đến độ chính xác của toàn hệ thống: khai báo, chỉnh sửa, xóa định mức bóc tách vật tư (BOM), và quản lý tài khoản/phân quyền cho PLANNER. ADMIN cũng có đầy đủ quyền thao tác thủ công trên đơn hàng (xem, tạo mới, chỉnh sửa, xóa) như PLANNER để can thiệp khi cần, nhưng không đảm nhiệm các tác vụ vận hành hằng ngày (nhập đơn hàng hàng loạt từ Excel, chạy thuật toán sinh phương án cắt) — vốn là trách nhiệm riêng của PLANNER.

## Phạm vi hệ thống

Hệ thống bao quát trọn vòng đời một lần sinh phương án cắt: từ nhập dữ liệu đầu vào (đơn hàng khách, tồn kho thanh nan, định mức BOM) → sinh phương án cắt tối ưu trên tồn kho hiện có → xem/xuất kết quả để chỉ đạo sản xuất. Cụ thể, phạm vi MVP gồm 4 nhóm chức năng: quản lý đơn hàng & tồn kho, quản lý định mức BOM, sinh phương án cắt & trực quan hóa, và tài khoản & phân quyền (chi tiết từng nhóm ở `docs/requirements-functional.md`).

Một số phần chủ động để ngoài phạm vi khóa luận, đã xác định rõ trong quá trình thiết kế:

- **Lập kế hoạch sản xuất bù vật tư thiếu**: khi một đơn hàng bị đánh dấu thiếu vật tư (shortage), hệ thống chỉ ghi nhận đầy đủ thông tin thiếu hụt (loại thanh nan, số lượng/độ dài còn thiếu) làm căn cứ, không tự động lập kế hoạch sản xuất hay đề xuất thời điểm nhập thêm — việc này định hướng cho một hệ thống lập kế hoạch sản xuất vật tư ở giai đoạn phát triển sau.
- **Đa nhà máy**: hệ thống chỉ phục vụ đúng 1 nhà máy trong phạm vi khóa luận; nếu mở rộng đa nhà máy sau này cần bổ sung lại khóa nhà máy vào các ràng buộc dữ liệu liên quan.
- **Nhu cầu cắt cho nhóm vật tư `OTHER`/thiếu dữ liệu định mức**: với các định mức không xác định được công thức tính độ dài đoạn cắt cụ thể theo nhóm vật tư (chỉ có tổng định mức mét/bộ cửa), hệ thống không tự sinh được nhu cầu cắt — chỉ ghi log cảnh báo, chờ ADMIN bổ sung công thức riêng nếu phát sinh thực tế.
- **Hướng cuốn cửa** (trong/ngoài): dữ liệu thô có cột này nhưng chưa xác định được ảnh hưởng tới định mức BOM hay thuật toán cắt, nên chủ động để ngoài schema hiện tại.
- **Tích hợp trực tiếp với hệ thống nguồn của doanh nghiệp**: dữ liệu đầu vào được tiếp nhận qua file Excel xuất thủ công từ hệ thống nghiệp vụ hiện có, không có kết nối/đồng bộ tự động trực tiếp giữa hai hệ thống.

# 3.1.2 Biểu đồ ca sử dụng

Sơ đồ dưới đây thể hiện các ca sử dụng của hai tác nhân PLANNER và ADMIN đã mô tả ở mục 3.1.1. Hai tác nhân chia sẻ nhóm ca sử dụng chung về tài khoản (đăng nhập, đăng xuất, đổi mật khẩu); phần còn lại tách biệt rõ theo đúng ranh giới trách nhiệm — trừ nhóm ca sử dụng thao tác thủ công trên đơn hàng (xem/tìm/lọc, tạo mới, chỉnh sửa, xóa), dùng chung cho cả hai tác nhân.

![Biểu đồ ca sử dụng — Hệ thống tối ưu cắt nan cửa cuốn](diagrams/use-case-diagram.svg)

## Ghi chú từng nhóm ca sử dụng

- **Tài khoản (dùng chung)**: điểm khởi đầu bắt buộc cho cả hai tác nhân trước khi tiếp cận bất kỳ chức năng nào khác; đăng nhập trả về JWT dùng cho các request tiếp theo.
- **Nhóm 1 — Đơn hàng & tồn kho**: nhập/xem/chỉnh sửa dữ liệu đầu vào của quy trình cắt. Nhập hàng loạt từ Excel và quản lý tồn kho chỉ PLANNER; riêng 4 ca sử dụng thao tác thủ công trên đơn hàng (xem/tìm/lọc, tạo mới, chỉnh sửa, xóa) dùng chung cho cả PLANNER và ADMIN.
- **Nhóm 2 — Định mức BOM** (chỉ ADMIN): quản lý dữ liệu nền tảng ít thay đổi nhưng quyết định độ chính xác của nhu cầu cắt sinh ra.
- **Nhóm 3 — Sinh phương án cắt** (chỉ PLANNER): ca sử dụng lõi của khóa luận — "Sinh phương án cắt" kéo theo (include) việc sinh nhu cầu cắt từ BOM và chạy thuật toán 4 mức ưu tiên (xem chi tiết luồng trong `docs/sequence-diagrams.md`).
- **Nhóm 4 — Tài khoản & phân quyền** (chỉ ADMIN): quản lý tài khoản PLANNER, gán vai trò.

## Mô tả từng ca sử dụng

| Ca sử dụng | Tác nhân | Mô tả |
|---|---|---|
| Đăng nhập | PLANNER, ADMIN | Xác thực bằng tài khoản/mật khẩu, nhận JWT dùng cho các request tiếp theo trong phiên làm việc. |
| Đăng xuất | PLANNER, ADMIN | Kết thúc phiên làm việc hiện tại. |
| Đổi mật khẩu cá nhân | PLANNER, ADMIN | Tự thay đổi mật khẩu tài khoản của chính mình. |
| Nhập đơn hàng từ Excel | PLANNER | Nhập hàng loạt đơn hàng khách từ file Excel xuất từ hệ thống nghiệp vụ hiện có, upsert theo khóa (`ycsx`, `z_item`); toàn bộ lượt nhập xử lý trong 1 giao dịch. |
| Xem / tìm / lọc đơn hàng | PLANNER, ADMIN | Xem danh sách, tìm kiếm và lọc đơn hàng theo ngày giao, khách hàng hoặc trạng thái xử lý. |
| Thêm mới đơn hàng thủ công | PLANNER, ADMIN | Tạo thủ công 1 đơn hàng khi cần bổ sung bộ cửa phát sinh ngoài luồng nhập Excel. |
| Chỉnh sửa đơn hàng thủ công | PLANNER, ADMIN | Sửa thủ công 1 đơn hàng khi cần điều chỉnh cục bộ, chưa kịp đồng bộ từ hệ thống nguồn. |
| Xóa đơn hàng | PLANNER, ADMIN | Xóa thủ công 1 đơn hàng; bị từ chối nếu đơn đã được đưa vào ít nhất một lần chạy thuật toán sinh phương án cắt. |
| Nhập tồn kho từ Excel | PLANNER | Nhập hàng loạt tồn kho thanh nan (loại thanh, độ dài chuẩn, số lượng) từ file Excel, theo dạng snapshot ghi đè tại thời điểm nhập. |
| Xem tồn kho theo loại thanh | PLANNER | Xem tồn kho hiện có theo từng loại thanh nan và độ dài chuẩn. |
| Cập nhật tồn kho thủ công | PLANNER | Chỉnh sửa thủ công tồn kho khi có phát sinh nhập/xuất kho ngoài luồng chạy thuật toán. |
| Quản lý định mức BOM | ADMIN | Khai báo, chỉnh sửa hoặc xóa định mức bóc tách vật tư — quy tắc quy đổi từ mẫu cửa + màu sang loại thanh nan cần dùng, kèm công thức tính số lượng/độ dài đoạn cắt. |
| Nhập định mức BOM từ Excel | ADMIN | Nhập hàng loạt định mức BOM từ Excel, do khối lượng tổ hợp mẫu cửa × màu lớn trong thực tế. |
| Tra cứu định mức BOM | ADMIN | Tìm nhanh định mức hiện có theo mẫu cửa hoặc theo nhóm thanh nan để kiểm tra tính hợp lệ trước khi áp dụng. |
| Sinh phương án cắt | PLANNER | Kích hoạt thuật toán sinh phương án cắt tối ưu; bao gồm (include) sinh nhu cầu cắt từ BOM và chạy thuật toán 4 mức ưu tiên trên tồn kho hiện có. |
| Xem / xuất kết quả cắt | PLANNER | Xem lịch sử các lần chạy, xem chi tiết một phương án ở 3 mức (tổng quan, theo đơn hàng, theo phôi xuất kho), xuất kết quả ra Excel. |
| Quản lý tài khoản người dùng | ADMIN | Tạo, chỉnh sửa hoặc khóa tài khoản PLANNER, gán vai trò tương ứng cho từng tài khoản. |

Khác với một số hệ thống mà cùng 1 ca sử dụng dùng chung có thể mang sắc thái hành vi khác nhau theo từng tác nhân, ở hệ thống này 4 ca sử dụng dùng chung trên đơn hàng (xem/tìm/lọc, thêm mới, chỉnh sửa, xóa) hoàn toàn giống nhau giữa PLANNER và ADMIN — không có ràng buộc hay hành vi riêng theo actor, đúng theo thiết kế đã chốt ở mục 3.1.1.
