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

## 2. Luồng quản lý đơn hàng & BOM

Áp dụng chung cho luồng quản lý đơn hàng (PLANNER và ADMIN đều thao tác được) và luồng quản lý định mức BOM (chỉ ADMIN) — cùng là thao tác CRUD cơ bản (xem/tìm-lọc, thêm mới, sửa, xóa) trên dữ liệu thường đã có sẵn trong hệ thống từ luồng nhập Excel ở mục 1, nhưng khác nhau ở tác nhân thực hiện, khóa nghiệp vụ dùng để kiểm tra trùng lặp khi thêm mới, và điều kiện lọc danh sách: đơn hàng lọc theo ngày giao yêu cầu, khách hàng hoặc trạng thái xử lý (trạng thái này không phải cột lưu sẵn mà suy ra từ việc đơn đã có kết quả cắt/thiếu vật tư tham chiếu hay chưa — xem `docs/domain-model.md` mục 3.3.1); BOM lọc theo mẫu cửa hoặc nhóm thanh nan.

```mermaid
flowchart TD
    A([Bắt đầu]) --> B["Người dùng vào màn hình quản lý<br/>(đơn hàng: PLANNER hoặc ADMIN — BOM: chỉ ADMIN)"]
    B --> C["Nhập điều kiện tìm kiếm/lọc (nếu có)<br/>và xem danh sách bản ghi"]
    C --> D{"Chọn hành động"}

    D -- "Chỉ xem" --> Z([Kết thúc])

    D -- "Thêm mới" --> E["Nhập thông tin bản ghi mới"]
    E --> F{"Đủ trường bắt buộc<br/>và đúng định dạng?"}
    F -- "Không" --> F1["Báo lỗi, giữ nguyên form"]
    F1 --> E
    F -- "Có" --> G{"Đã tồn tại bản ghi<br/>cùng khóa nghiệp vụ?<br/>(đơn hàng: ycsx+z_item —<br/>BOM: mẫu cửa+loại thanh nan)"}
    G -- "Có" --> G1["Báo lỗi trùng khóa nghiệp vụ"]
    G1 --> E
    G -- "Không" --> H["Tạo bản ghi mới"]
    H --> R["Cập nhật lại danh sách hiển thị"]
    R --> Z

    D -- "Sửa" --> J["Chọn bản ghi, tải dữ liệu hiện tại lên form"]
    J --> K["Chỉnh sửa thông tin"]
    K --> L{"Đủ trường bắt buộc<br/>và đúng định dạng?"}
    L -- "Không" --> L1["Báo lỗi, giữ nguyên form"]
    L1 --> K
    L -- "Có" --> M["Cập nhật bản ghi"]
    M --> R

    D -- "Xóa" --> N["Chọn bản ghi cần xóa"]
    N --> O{"Là đơn hàng và đã có<br/>kết quả cắt tham chiếu?<br/>(CuttingPlanDetailItem/ShortageRecord)"}
    O -- "Có" --> O1["Từ chối xóa, báo lỗi<br/>'đơn đã được xử lý, không thể xóa'"]
    O1 --> Z
    O -- "Không" --> P["Xóa bản ghi"]
    P --> R
```

Điểm cần lưu ý: nhánh xóa có rẽ nhánh riêng cho đơn hàng — một khi đơn hàng đã được đưa vào ít nhất một lần chạy thuật toán (có `CuttingPlanDetailItem` hoặc `ShortageRecord` tham chiếu tới, xem `docs/domain-model.md` mục 3.3.1), hệ thống từ chối xóa thay vì để phát sinh lỗi ràng buộc khóa ngoại ở tầng cơ sở dữ liệu. BOM không có bảng con nào tham chiếu trực tiếp đến `BomItem` nên nhánh này luôn cho phép xóa bình thường. Luồng quản lý tồn kho thanh nan không nằm trong sơ đồ này — tồn kho được nhập/cập nhật chủ yếu qua luồng Excel ở mục 1 (bao gồm cả chỉnh sửa thủ công theo cùng khóa nghiệp vụ loại thanh + độ dài), không có luồng CRUD tách rời riêng.

## 3. Luồng thuật toán sinh phương án cắt (luồng lõi)

Đây là luồng nghiệp vụ quan trọng nhất của khóa luận — thuật toán Best Fit Decreasing mở rộng 4 mức ưu tiên. Sơ đồ dưới đây bổ sung góc nhìn ra quyết định cho luồng đã có ở `docs/sequence-diagrams.md` mục "2. Luồng sinh phương án cắt" (thể hiện thành phần nào gọi thành phần nào); công thức chi tiết sinh `CuttingDemand` từ `SalesOrder`+`BomItem` cũng đã trình bày đầy đủ ở đó, không lặp lại ở đây.

```mermaid
flowchart TD
    A([Bắt đầu]) --> A0["PLANNER bấm 'Sinh phương án cắt'"]
    A0 --> B["Xác định phạm vi đợt xử lý:<br/>SalesOrder chưa có kết quả cắt tham chiếu,<br/>reqd_delivery_date sớm hơn hoặc bằng t+3 ngày,<br/>tổng số đơn trong phạm vi dưới 70<br/>(ngoài phạm vi → 'nhóm 99', chờ lần chạy sau)"]
    B --> C["Sinh CuttingDemand từ SalesOrder + BomItem tương ứng<br/>(công thức chi tiết xem docs/sequence-diagrams.md)"]
    C --> D["Nạp InventoryPool từ tồn kho hiện có"]
    D --> E["Nhóm CuttingDemand theo slatMaterial"]
    E --> F{"Còn nhóm slatMaterial<br/>chưa xử lý?"}

    F -- "Không" --> G["Lưu CuttingPlan + CuttingPlanDetail + CuttingPlanDetailItem<br/>+ ShortageRecord, cập nhật tồn kho (trừ/cộng), trong 1 transaction"]
    G --> Z([Kết thúc])

    F -- "Có" --> H["Lấy 1 nhóm slatMaterial tiếp theo,<br/>sắp xếp hàng đợi đoạn cần cắt theo<br/>(reqd_delivery_date tăng dần, ycsx, z_item)"]
    H --> I{"Hàng đợi còn<br/>đoạn chưa cắt?"}
    I -- "Không" --> F

    I -- "Có" --> J["Lấy đoạn X ưu tiên cao nhất còn lại<br/>(không bao giờ trì hoãn đoạn ưu tiên cao nhất)"]

    J --> K{"Mức 1: tồn tại thanh khớp X,<br/>dư dự kiến dưới 30cm?"}
    K -- "Có" --> K1["Cắt thanh đó; dư dưới 30cm → 'bỏ';<br/>loại X khỏi hàng đợi"]
    K1 --> I

    K -- "Không" --> L{"Mức 2: có N-1 đoạn khác<br/>cùng độ dài X đang chờ trong<br/>cùng đợt xử lý, và tồn tại thanh<br/>dài gấp k lần cutLength(X), k≥2, đủ dùng?"}
    L -- "Có" --> L1["Cắt thanh thành k đoạn,<br/>gán X + (k−1) đoạn cùng độ dài;<br/>dư = 0; loại các đoạn đã gán"]
    L1 --> I

    L -- "Không" --> M{"Mức 3: tồn tại tổ hợp<br/>{X, 1 hoặc nhiều đoạn khác<br/>trong cùng đợt xử lý} khớp 1 thanh,<br/>dư dự kiến dưới 30cm?"}
    M -- "Có" --> M1["Cắt thanh đó, gán từng đoạn<br/>về đúng đơn của nó;<br/>dư dưới 30cm → 'bỏ'; loại các đoạn đã gán"]
    M1 --> I

    M -- "Không" --> N{"Mức 4: tìm thanh ngắn nhất<br/>đủ chứa X (best-fit, gồm cả<br/>phần dư trên 3m vừa nhập kho<br/>trong lần chạy này)?"}
    N -- "Có thanh đủ dài" --> N1["Cắt thanh đó"]
    N1 --> N2{"Phân loại phần dư"}
    N2 -- "Trên 3m" --> N3["'Nhập lại kho' — thêm vào pool"]
    N2 -- "Từ 30cm đến 3m" --> N4["'Lãng phí' — ghi nhận cảnh báo"]
    N3 --> N5["Loại X khỏi hàng đợi"]
    N4 --> N5
    N5 --> I

    N -- "Không còn thanh đủ dài" --> O["Đánh dấu shortage cho X<br/>(slatMaterial + số lượng/độ dài thiếu);<br/>loại X khỏi hàng đợi<br/>(không chặn đoạn ưu tiên thấp hơn)"]
    O --> I
```

Điểm dễ hiểu nhầm nhất, cần nhấn lại: thứ tự **xử lý** trong hàng đợi luôn theo đúng ưu tiên `(reqd_delivery_date, ycsx, z_item)` — đoạn X ở bước "Lấy đoạn X ưu tiên cao nhất còn lại" luôn là đoạn đầu hàng đợi, không bao giờ bị bỏ qua để chờ ghép; khác với phạm vi **ghép nối** ở Mức 2 và Mức 3, chỉ áp dụng giữa các đoạn cùng nằm trong đợt xử lý hiện tại (đã giới hạn bởi bước "Xác định phạm vi đợt xử lý" ở đầu sơ đồ), không bao giờ ghép với đơn thuộc "nhóm 99" hay đợt xử lý sau. Ngoài ra, mỗi nhóm `slatMaterial` ở vòng lặp ngoài được xử lý độc lập với nhau — vì tồn kho (`InventoryBatch`) đã tách riêng theo `slatMaterial`, không có ràng buộc chéo giữa các nhóm.
