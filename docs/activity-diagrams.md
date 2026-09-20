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
    O -- "Có" --> O2{"Là đơn hàng và<br/>đã được duyệt?"}
    O2 -- "Có" --> O3["Giữ nguyên bản ghi cũ;<br/>nếu dữ liệu nguồn khác thì ghi nhận<br/>cảnh báo: bộ cửa nào, trường nào khác"]
    O3 --> N
    O2 -- "Không" --> P["Cập nhật bản ghi hiện có"]
    O -- "Không" --> Q["Tạo bản ghi mới"]
    P --> N
    Q --> N
    N -- "Không, đã xử lý hết" --> R["Commit transaction"]
    R --> S["Trả về số dòng đã nhập + danh sách cảnh báo"]
    S --> T["Người dùng thấy 'Nhập thành công N dòng'<br/>kèm danh sách bộ cửa đã duyệt bị bỏ qua"]
    T --> Z
```

Nhánh "đã được duyệt" ở vòng lặp upsert là ranh giới bất biến của đơn hàng đã chốt: nan của bộ cửa đó đã cắt theo kích thước đã lưu, ghi đè chỉ làm hồ sơ lệch với vật tư đã ra khỏi kho mà không khiến bộ cửa được cắt lại. Cảnh báo **không** hủy lượt nhập — khác hẳn nhánh lỗi định dạng ở trên: dòng lỗi định dạng nghĩa là file nguồn sai và phải sửa rồi nhập lại, còn ở đây file nguồn không sai, chỉ là hệ thống không được phép tự ý áp thay đổi lên một việc đã làm xong; quyết định tạo bộ cửa mới để cắt lại thuộc về PLANNER.

Điểm cần lưu ý: hai vòng lặp (kiểm tra định dạng và upsert) được tách rời — chỉ bước sang giai đoạn upsert khi **toàn bộ** các dòng đã qua kiểm tra định dạng, tránh trường hợp nhập được một phần rồi mới phát hiện lỗi ở dòng sau.

Riêng luồng nhập tồn kho có thêm một hành vi không thể hiện ở sơ đồ trên để giữ sơ đồ chung đơn giản: nếu một tổ hợp (loại thanh, độ dài) từng tồn tại trong hệ thống nhưng không còn xuất hiện trong file mới nhập, hệ thống chủ động đưa số thanh còn lại về 0 thay vì giữ nguyên giá trị cũ — khác với hành vi upsert thuần túy (chỉ thêm/sửa, không xóa/reset) áp dụng cho đơn hàng và BOM.

## 2. Luồng quản lý đơn hàng & BOM

Áp dụng chung cho luồng quản lý đơn hàng (PLANNER và ADMIN đều thao tác được) và luồng quản lý định mức BOM (chỉ ADMIN) — cùng là thao tác CRUD cơ bản (xem/tìm-lọc, thêm mới, sửa, xóa) trên dữ liệu thường đã có sẵn trong hệ thống từ luồng nhập Excel ở mục 1, nhưng khác nhau ở tác nhân thực hiện, khóa nghiệp vụ dùng để kiểm tra trùng lặp khi thêm mới, và điều kiện lọc danh sách: đơn hàng lọc theo ngày giao yêu cầu, khách hàng hoặc trạng thái xử lý (bốn giá trị: chưa xử lý / đang bị chặn vì thiếu định mức / đủ vật tư / thiếu vật tư — việc đơn đã duyệt hay chưa đọc thẳng từ `approved_plan_id`, phần còn lại suy ra từ dữ liệu liên quan, xem `docs/domain-model.md` mục 3.3.1); BOM lọc theo mẫu cửa hoặc nhóm thanh nan.

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
    L -- "Có" --> L2{"Là đơn hàng và<br/>đã được duyệt?"}
    L2 -- "Có" --> L3["Từ chối sửa, báo lỗi<br/>'đơn đã thuộc một phương án cắt<br/>đã duyệt, không thể sửa'"]
    L3 --> Z
    L2 -- "Không" --> M["Cập nhật bản ghi"]
    M --> R

    D -- "Xóa" --> N["Chọn bản ghi cần xóa"]
    N --> O{"Là đơn hàng và<br/>đã được duyệt?<br/>(approved_plan_id khác rỗng)"}
    O -- "Có" --> O1["Từ chối xóa, báo lỗi<br/>'đơn đã thuộc một phương án cắt<br/>đã duyệt, không thể xóa'"]
    O1 --> Z
    O -- "Không" --> P["Xóa bản ghi"]
    P --> R
```

Điểm cần lưu ý: nhánh xóa có rẽ nhánh riêng cho đơn hàng — một khi đơn hàng đã thuộc một phương án cắt được duyệt (`approved_plan_id` khác rỗng, xem `docs/domain-model.md` mục 3.3.1), hệ thống từ chối xóa thay vì để phát sinh lỗi ràng buộc khóa ngoại ở tầng cơ sở dữ liệu. BOM không có bảng con nào tham chiếu trực tiếp đến `BomItem` nên nhánh này luôn cho phép xóa bình thường. Luồng quản lý tồn kho thanh nan không nằm trong sơ đồ này — tồn kho được nhập/cập nhật chủ yếu qua luồng Excel ở mục 1 (bao gồm cả chỉnh sửa thủ công theo cùng khóa nghiệp vụ loại thanh + độ dài), không có luồng CRUD tách rời riêng.

## 3. Luồng tính và duyệt phương án cắt (luồng lõi)

Đây là luồng nghiệp vụ quan trọng nhất của khóa luận. Nó được trình bày thành hai sơ đồ vì hai câu hỏi khác nhau cần trả lời riêng: sơ đồ 3.1 cho biết **đơn hàng nào được đưa vào và tới lúc nào dữ liệu mới thực sự thay đổi** — đây là phần khác nhau giữa chức năng tính và chức năng duyệt; sơ đồ 3.2 cho biết **thuật toán quyết định cắt thanh nào như thế nào** — phần này hai chức năng dùng chung y hệt, chỉ khác tập đơn đưa vào. Gộp cả hai vào một sơ đồ sẽ khiến nhánh quyết định của thuật toán bị lẫn với nhánh quyết định của quy trình phê duyệt, trong khi chúng thuộc hai tầng khác nhau.

Cả hai sơ đồ bổ sung góc nhìn ra quyết định cho luồng đã có ở `docs/sequence-diagrams.md` mục "2. Luồng tính và duyệt phương án cắt" (thể hiện thành phần nào gọi thành phần nào); công thức chi tiết sinh `CuttingDemand` từ `SalesOrder`+`BomItem` cũng đã trình bày đầy đủ ở đó, không lặp lại ở đây.

### 3.1. Phạm vi xử lý và ranh giới thay đổi dữ liệu

```mermaid
flowchart TD
    A([Bắt đầu]) --> B{"Người dùng chọn<br/>chức năng nào?"}

    B -- "Tính phương án cắt<br/>(PLANNER hoặc ADMIN)" --> C["Phạm vi: TOÀN BỘ đơn chưa duyệt<br/>(approved_plan_id rỗng và sinh được<br/>ít nhất 1 nhu cầu cắt);<br/>không giới hạn ngày giao, không giới hạn số đơn"]
    C --> D["Chạy thuật toán 4 mức ưu tiên<br/>(xem sơ đồ 3.2)"]
    D --> E["Hiển thị mức tổng quan + mức chi tiết theo đơn hàng,<br/>kèm số đơn đang bị chặn vì mẫu cửa thiếu định mức;<br/>cho phép xuất Excel"]
    E --> Z1([Kết thúc — KHÔNG thay đổi dữ liệu nào])

    B -- "Duyệt phương án cắt<br/>(chỉ PLANNER)" --> F["Trong CÙNG một lượt đọc: lấy phạm vi và ghi dấu vân trạng thái.<br/>Phạm vi = đơn chưa duyệt, sinh được ít nhất 1 nhu cầu cắt,<br/>reqd_delivery_date sớm hơn hoặc bằng t+3 ngày,<br/>tổng số đơn dưới 70 (ngoài phạm vi → 'nhóm 99', chờ lần duyệt sau)"]
    F --> G["Chạy thuật toán 4 mức ưu tiên<br/>(xem sơ đồ 3.2)"]
    G --> I["Trình phương án đề xuất kèm danh sách đợt cắt<br/>để PLANNER xem xét"]
    I --> J{"PLANNER chấp nhận<br/>phương án?"}
    J -- "Không" --> Z2([Kết thúc — KHÔNG thay đổi dữ liệu nào])
    J -- "Có" --> K{"Trạng thái đơn hàng và tồn kho<br/>còn khớp dấu vân đã ghi?"}
    K -- "Không" --> K1["Từ chối duyệt, báo 'dữ liệu đã thay đổi'"]
    K1 --> F
    K -- "Có" --> L["Trong 1 transaction: lưu CuttingPlan + CuttingPlanDetail<br/>+ CuttingPlanDetailItem + ShortageRecord + ảnh chụp tồn kho đầu lần chạy,<br/>trừ/cộng tồn kho, gán approved_plan_id cho MỌI đơn trong phạm vi"]
    L --> Z3([Kết thúc — dữ liệu đã thay đổi])
```

Hai điểm quyết định cách đọc sơ đồ này. Thứ nhất, **nhánh tính không có ô nào ghi dữ liệu** — đó là toàn bộ lý do tách chức năng: người dùng chạy thử bao nhiêu lần tùy ý trên trạng thái đang có mà không làm lệch tồn kho, nên mới dám bỏ giới hạn t+3 ngày và giới hạn 70 đơn để nhìn bức tranh thiếu hụt của toàn bộ đơn tồn. Thứ hai, **ô kiểm dấu vân trạng thái trước khi ghi là bắt buộc, không phải tối ưu**: giữa lúc phương án được tính và lúc PLANNER bấm duyệt, một lượt nhập tồn kho hoặc một đơn vừa sửa có thể đã làm phương án lỗi thời; ghi xuống khi đó sẽ trừ tồn kho những phôi thực tế không còn, hoặc bỏ sót đơn vừa được bổ sung vào phạm vi. Dấu vân phải được lấy **trong cùng một lượt đọc** với danh sách đơn trong phạm vi, không phải sau khi thuật toán chạy xong: nếu chụp sau, dữ liệu đổi ngay trong lúc thuật toán chạy sẽ được ghi vào dấu vân như thể không có gì xảy ra, và cơ chế này mất tác dụng đúng ở tình huống nó sinh ra để chặn. Khi dấu vân lệch, luồng quay lại chính bước lấy phạm vi — không quay lại bước trình phương án, vì dấu vân cũ vẫn lệch thì PLANNER sẽ bị từ chối mãi.

Lưu ý ở nhánh duyệt: `approved_plan_id` được gán cho **mọi** đơn trong phạm vi, kể cả đơn chỉ nhận kết quả thiếu vật tư — nếu chỉ gán cho đơn cắt được thì đơn thiếu vật tư sẽ quay lại hàng chờ và bị đưa vào lần duyệt sau, trong khi vật tư bù chưa kịp về. Ngoại lệ duy nhất là lớp phòng vệ cho kẽ hở của điều kiện lọc phạm vi: đơn lọt vào phạm vi nhưng rốt cuộc không để lại kết quả nào thì giữ nguyên ở hàng chờ thay vì đánh dấu đã duyệt (xem `docs/domain-model.md`). Còn đơn có mẫu cửa không sinh được nhu cầu cắt nào thì ngay từ đầu đã nằm ngoài phạm vi của cả hai nhánh (xem điều kiện lọc ở `docs/requirements-functional.md` Nhóm 3), nên không bị đánh dấu đã duyệt một cách oan uổng.

### 3.2. Thuật toán cắt 4 mức ưu tiên

Sơ đồ dưới đây là thuật toán Best Fit Decreasing mở rộng, nhận vào danh sách đơn hàng trong phạm vi đã xác định ở sơ đồ 3.1 và trả về kết quả cắt — giống hệt nhau dù được gọi từ chức năng tính hay chức năng duyệt.

```mermaid
flowchart TD
    A([Nhận danh sách đơn hàng trong phạm vi<br/>từ sơ đồ 3.1]) --> C["Sinh CuttingDemand từ SalesOrder + BomItem tương ứng<br/>(công thức chi tiết xem docs/sequence-diagrams.md)"]
    C --> D["Nạp InventoryPool từ tồn kho hiện có"]
    D --> E["Nhóm CuttingDemand theo slatMaterial"]
    E --> F{"Còn nhóm slatMaterial<br/>chưa xử lý?"}

    F -- "Không" --> G([Trả kết quả về sơ đồ 3.1:<br/>chi tiết cắt từng phôi + mức ưu tiên đã dùng<br/>+ danh sách thiếu vật tư])

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

    L -- "Không" --> M{"Mức 3: tồn tại ĐÚNG 1 đoạn khác<br/>trong cùng phạm vi lần chạy, ghép với X<br/>vừa 1 thanh, dư dự kiến dưới 30cm?<br/>(lấy đoạn khớp ĐẦU TIÊN tìm được)"}
    M -- "Có" --> M1["Cắt thanh đó, gán 2 đoạn<br/>về đúng đơn của từng đoạn;<br/>dư dưới 30cm → 'bỏ'; loại 2 đoạn khỏi hàng đợi"]
    M1 --> I

    M -- "Không" --> N{"Mức 4: tìm thanh ngắn nhất<br/>chứa được X và còn để lại<br/>phần dư TRÊN 3m (gồm cả phần dư<br/>vừa nhập kho trong lần chạy này)?"}
    N -- "Có" --> N1["Cắt thanh đó;<br/>phần dư trên 3m → 'nhập lại kho',<br/>thêm vào pool dùng ngay trong lượt này"]
    N1 --> N2["Loại X khỏi hàng đợi"]
    N2 --> I

    N -- "Không" --> O["Đánh dấu shortage cho X<br/>(slatMaterial + số lượng/độ dài thiếu);<br/>loại X khỏi hàng đợi<br/>(không chặn đoạn ưu tiên thấp hơn)"]
    O --> I
```

Một hệ quả quan trọng của cách đặt điều kiện ở Mức 4: hệ thống **không bao giờ tự tạo ra phần dư nằm trong khoảng 30cm–3m**. Mức 1 và Mức 3 chỉ nhận thanh khi phần dư dự kiến dưới 30cm, Mức 2 luôn cho phần dư bằng 0, còn Mức 4 chỉ nhận thanh khi phần dư trên 3m — tức là mọi nhánh cắt đều dẫn tới một phần dư hoặc đủ nhỏ để bỏ đi, hoặc đủ dài để nhập lại kho. Khi không nhánh nào thỏa mãn, đoạn cắt được đánh dấu thiếu vật tư thay vì hạ chuẩn để cắt. Điều này có nghĩa một đoạn có thể bị báo thiếu **dù trong kho vẫn còn thanh đủ dài**: nếu thanh đó chỉ để lại phần dư trong khoảng không chấp nhận được, nó được giữ lại nguyên vẹn cho một đoạn khác khớp hơn ở lần chạy sau. Đây là lựa chọn nghiệp vụ có chủ đích — phần dư 30cm–3m không tái sử dụng ngay được mà cũng không đủ nhỏ để bỏ qua, nên doanh nghiệp coi việc bổ sung thanh nan đúng độ dài là cách xử lý đúng, thay vì phá một thanh đang dùng được cho nhu cầu khác.

Mức 3 cố ý chỉ ghép **đúng hai đoạn** và dừng ở đoạn khớp **đầu tiên** tìm được, không tìm tổ hợp ba đoạn trở lên cũng không duyệt hết hàng đợi để chọn tổ hợp tốt nhất: số tổ hợp tăng theo cấp số nhân với số đoạn được phép ghép, trong khi phần lợi thêm rất nhỏ vì điều kiện chấp nhận đã là phần dư dưới 30cm — và giới hạn này giữ cho kết quả tái lập được, không phụ thuộc thứ tự duyệt.

Điểm dễ hiểu nhầm nhất, cần nhấn lại: thứ tự **xử lý** trong hàng đợi luôn theo đúng ưu tiên `(reqd_delivery_date, ycsx, z_item)` — đoạn X ở bước "Lấy đoạn X ưu tiên cao nhất còn lại" luôn là đoạn đầu hàng đợi, không bao giờ bị bỏ qua để chờ ghép; khác với phạm vi **ghép nối** ở Mức 2 và Mức 3, chỉ áp dụng giữa các đoạn cùng nằm trong phạm vi của chính lần chạy đó (đã xác định ở sơ đồ 3.1 — toàn bộ đơn chưa duyệt nếu là chức năng tính, tập đơn đã giới hạn t+3/dưới 70 đơn nếu là chức năng duyệt), không bao giờ ghép với đơn nằm ngoài phạm vi. Ngoài ra, mỗi nhóm `slatMaterial` ở vòng lặp ngoài được xử lý độc lập với nhau — vì tồn kho (`InventoryBatch`) đã tách riêng theo `slatMaterial`, không có ràng buộc chéo giữa các nhóm.

## 4. Luồng xem/xuất kết quả phương án cắt

Luồng này áp dụng cho các phương án **đã được duyệt** và lưu lại — chức năng tính không tạo lịch sử nên không có gì để tra cứu về sau. Sơ đồ dưới đây bổ sung góc nhìn ra quyết định cho luồng đã có ở `docs/sequence-diagrams.md` mục "3. Luồng xem / xuất kết quả phương án cắt" (thể hiện thành phần nào gọi thành phần nào, khá tuyến tính: xem lịch sử → xem chi tiết → hai thao tác xuất Excel tùy chọn). Phần bổ sung giá trị nhất ở đây là cách hệ thống **tính và sắp xếp "đợt cắt"** để hiển thị ở mức tổng quan — quy tắc nghiệp vụ có nhiều rẽ nhánh nhất của luồng này, đã chốt ở `docs/requirements-functional.md` Nhóm 3 nhưng chưa từng thể hiện dưới dạng flowchart quyết định.

```mermaid
flowchart TD
    A([Bắt đầu]) --> A0["Người dùng mở màn hình lịch sử<br/>phương án cắt đã duyệt"]
    A0 --> B["Xem lịch sử các lần duyệt:<br/>thời điểm, tổng phế, trạng thái"]
    B --> C{"Chọn 1 phương án<br/>để xem chi tiết?"}
    C -- "Không" --> Z([Kết thúc])
    C -- "Có" --> D["Tải chi tiết phương án:<br/>CuttingPlanDetail + CuttingPlanDetailItem + ShortageRecord"]

    D --> E["Nhóm các đơn hàng đã xử lý<br/>theo DoorProduct (mẫu cửa + màu)"]
    E --> F{"Còn nhóm DoorProduct<br/>chưa chia đợt cắt?"}
    F -- "Có" --> G["Sắp đơn hàng trong nhóm theo<br/>(reqd_delivery_date, ycsx, z_item)"]
    G --> H{"Nhóm còn trên 7 bộ cửa<br/>chưa gán đợt?"}
    H -- "Có" --> I["Gán 7 bộ cửa ưu tiên cao nhất<br/>còn lại vào 1 đợt cắt mới"]
    I --> H
    H -- "Không" --> J["Gán toàn bộ số bộ còn lại<br/>(từ 1 đến 7 bộ) vào 1 đợt cắt cuối của nhóm"]
    J --> F
    F -- "Không" --> K["Sắp xếp toàn bộ đợt cắt theo ngày giao sớm nhất trong đợt;<br/>trùng ngày giao sớm nhất → đợt có nhiều bộ hơn cùng rơi<br/>đúng ngày đó được xếp trước"]

    K --> L["Hiển thị mức tổng quan: tổng số bộ cửa, tỷ lệ phế,<br/>biểu đồ cột chồng theo ngày giao, biểu đồ thanh ngang theo model,<br/>biểu đồ tròn tỷ trọng đủ/thiếu vật tư, bảng vật tư thiếu,<br/>danh sách đợt cắt đã sắp xếp"]

    L --> M{"Người dùng chọn thao tác tiếp theo"}
    M -- "Xem chi tiết theo đơn hàng" --> N["Hiển thị từng dòng nhu cầu cắt:<br/>thứ tự ưu tiên, thông tin đơn, model cửa, loại vật tư,<br/>độ dài cần cắt, số thanh cần và thiếu,<br/>trạng thái đáp ứng, mô tả cách cắt, tồn kho đầu lần chạy"]
    N --> M
    M -- "Lọc theo lệnh sản xuất / bộ cửa" --> P["Lọc lại dữ liệu đang hiển thị theo điều kiện nhập"]
    P --> M
    M -- "Xuất Excel kết quả cắt" --> Q["Sinh file Excel 1 sheet theo đúng bộ cột<br/>doanh nghiệp đang dùng, mỗi dòng là 1 nhu cầu cắt"]
    Q --> R["Người dùng tải file Excel về máy"]
    R --> M
    M -- "Mở màn hình đơn thiếu vật tư" --> S["Màn hình phụ: danh sách các đơn bị thiếu vật tư<br/>của lần chạy này"]
    S --> T{"Thao tác trên màn hình phụ"}
    T -- "Lọc lại danh sách" --> T1["Lọc theo loại thanh nan còn thiếu /<br/>ngày giao yêu cầu / lệnh sản xuất - bộ cửa"]
    T1 --> T
    T -- "Xuất báo cáo Excel" --> U["Sinh file Excel 1 sheet — bản lọc của mức<br/>chi tiết theo đơn hàng: chỉ các dòng còn thiếu thanh,<br/>chỉ các cột cần cho việc lập lệnh sản xuất thanh nan"]
    U --> U1["Người dùng tải file báo cáo về máy,<br/>vẫn ở lại màn hình phụ"]
    U1 --> T
    T -- "Quay lại phương án cắt" --> M
    M -- "Kết thúc xem" --> Z
```

Điểm cần lưu ý: "đợt cắt" không phải một entity lưu trữ riêng (xem `docs/domain-model.md` mục "Ghi chú khác") — toàn bộ khối tính đợt cắt (bước D đến K) là kết quả tính **tại thời điểm hiển thị**, không đọc từ cột trạng thái nào. Vì vậy mỗi lần PLANNER mở lại cùng một `CuttingPlan` đã lưu trước đó, thứ tự và nội dung các đợt cắt luôn được suy ra lại từ dữ liệu gốc (`CuttingPlanDetailItem`, `SalesOrder`) chứ không phải đọc một giá trị đã chốt cứng — đảm bảo không bị lệch (stale) nếu logic nhóm đợt cắt thay đổi sau này.
