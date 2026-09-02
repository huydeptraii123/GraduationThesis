# 3.4 Sequence diagram các luồng nghiệp vụ chính

## 1. Luồng nhập dữ liệu từ Excel

Áp dụng chung cho cả 3 loại dữ liệu (đơn hàng, tồn kho, định mức BOM) — minh họa bằng luồng nhập đơn hàng. Toàn bộ lượt nhập được xử lý trong 1 transaction: nếu có dòng lỗi, hủy toàn bộ và trả lỗi chi tiết (đúng yêu cầu phi chức năng "tính toàn vẹn khi nhập liệu" ở `docs/requirements-functional.md`).

```mermaid
sequenceDiagram
    actor U as PLANNER
    participant FE as Frontend
    participant C as SalesOrderController
    participant IMP as ExcelImportService
    participant SVC as SalesOrderService
    participant REPO as SalesOrderRepository
    participant DB as MySQL

    U->>FE: Chọn file Excel, bấm "Nhập dữ liệu"
    FE->>C: POST /api/v1/sales-orders/import (multipart file)
    C->>IMP: parse(file)
    IMP-->>C: List<SalesOrderRow>, List<RowError>

    alt Có dòng lỗi định dạng / thiếu trường bắt buộc
        C-->>FE: 400 + danh sách lỗi chi tiết (không lưu gì)
        FE-->>U: Hiển thị lỗi theo từng dòng
    else Toàn bộ hợp lệ
        C->>SVC: upsertAll(rows)  // trong 1 transaction
        SVC->>REPO: upsert theo ycsx + z_item
        REPO->>DB: INSERT / UPDATE
        DB-->>REPO: OK
        REPO-->>SVC: OK
        SVC-->>C: số dòng đã nhập
        C-->>FE: 200 + tóm tắt kết quả
        FE-->>U: Hiển thị "Nhập thành công N dòng"
    end
```

## 2. Luồng sinh phương án cắt (luồng lõi)

Luồng quan trọng nhất của khóa luận — thể hiện đúng 4 mức ưu tiên đã chốt: khớp gần đúng → cắt theo bội số (cùng đợt xử lý) → ghép nối nhiều đơn (cùng đợt xử lý) → best-fit/nhập kho/shortage.

```mermaid
sequenceDiagram
    actor U as PLANNER
    participant FE as Frontend
    participant C as CuttingPlanController
    participant SVC as CuttingPlanService
    participant DS as CuttingDemandService
    participant CS as CuttingStrategy
    participant POOL as InventoryPool
    participant REPO as CuttingPlanRepository
    participant DB as MySQL

    U->>FE: Bấm "Sinh phương án cắt"
    FE->>C: POST /api/v1/cutting-plans/generate
    C->>SVC: generatePlan()
    SVC->>DS: buildDemands()
    DS->>DB: lấy SalesOrder CHƯA có CuttingPlanDetailItem/ShortageRecord nào tham chiếu tới (chưa xử lý), trong phạm vi đợt xử lý (reqd_delivery_date <= t+3, tổng đơn < 70, ngoài phạm vi -> "nhóm 99") + BomItem tương ứng
    DB-->>DS: rows
    DS-->>SVC: List<CuttingDemand> (slatMaterial, cutLengthMm, qty, reqd_delivery_date, ycsx, zItem)

    SVC->>POOL: load(InventoryBatch hiện có)
    POOL-->>SVC: pool sẵn sàng (số thanh còn lại theo từng slatMaterial + độ dài)

    SVC->>CS: computePlan(demands, pool)

    loop mỗi slatMaterial group
        CS->>CS: sort hàng đợi theo (reqd_delivery_date, ycsx, z_item)
        loop while hàng đợi còn đoạn X chưa cắt
            alt Mức 1 — khớp gần đúng (dư < 30cm)
                CS->>POOL: findNearFit(X)
                POOL-->>CS: thanh khớp -> cắt, dư "bỏ"
            else Mức 2 — cắt theo bội số (cùng đợt xử lý với X, không cần cùng ngày giao)
                CS->>POOL: findMultipleOfSameLength(X)
                POOL-->>CS: thanh dài gấp k lần -> cắt k đoạn, dư = 0
            else Mức 3 — ghép nối nhiều đơn (cùng đợt xử lý với X, không cần cùng ngày giao)
                CS->>POOL: findCombination(X, hàng đợi trong đợt xử lý hiện tại)
                POOL-->>CS: tổ hợp khớp 1 thanh -> cắt, gán đúng đơn, dư "bỏ"
            else Mức 4 — best-fit / nhập kho / lãng phí / shortage
                CS->>POOL: bestFit(X)
                alt còn thanh đủ dài
                    POOL-->>CS: cắt, phân loại dư (>3m nhập kho | 30cm-3m lãng phí)
                else không còn thanh đủ dài
                    POOL-->>CS: đánh dấu shortage cho X
                end
            end
        end
    end

    CS-->>SVC: CuttingPlanResult (chi tiết cắt từng thanh, danh sách shortage, tổng waste)
    SVC->>POOL: diff() — số thanh đã trừ theo từng slatMaterial/độ dài, số thanh mới thêm (nhập lại kho dư >3m)
    POOL-->>SVC: InventoryBatch cần cập nhật (UPDATE so_thanh giảm | UPSERT lô mới cho phần dư nhập kho)
    SVC->>REPO: save(CuttingPlan, CuttingPlanDetail[], CuttingPlanDetailItem[], ShortageRecord[], InventoryBatch cần cập nhật)  // trong 1 transaction
    REPO->>DB: INSERT CuttingPlan/CuttingPlanDetail[]/CuttingPlanDetailItem[]/ShortageRecord[] + UPDATE/UPSERT inventory_batch
    DB-->>REPO: OK
    REPO-->>SVC: CuttingPlan đã lưu (id)
    SVC-->>C: CuttingPlanDto
    C-->>FE: CuttingPlanDto
    FE-->>U: Vẽ sơ đồ cắt (CuttingBarDiagram) + trạng thái từng đơn (đủ vật tư / thiếu vật tư)
```

**Bổ sung so với bản trước — trừ tồn kho sau khi cắt.** Bản trước chỉ lưu `CuttingPlan`/`CuttingPlanDetail` mà không có bước nào cập nhật lại `inventory_batch.so_thanh` — nghĩa là tồn kho trong DB sẽ không bao giờ giảm sau mỗi lần chạy, vi phạm trực tiếp yêu cầu phi chức năng "cân bằng vật liệu" (lần chạy sau sẽ tính trên tồn kho không đúng thực tế). Toàn bộ việc trừ/cộng tồn kho phải nằm trong đúng 1 transaction với việc lưu `CuttingPlan`, để đảm bảo không có trạng thái nửa-lưu nếu có lỗi giữa chừng.

### Công thức tính nhu cầu cắt (`CuttingDemandService.buildDemands()`)

Bước `DS->>DB: lấy SalesOrder ... + BomItem tương ứng` ở trên sinh ra `CuttingDemand` cho từng cặp (`SalesOrder`, `BomItem` của `doorProductId` tương ứng) theo công thức đã xác nhận với PLANNER (nguồn gốc từ view nội bộ `v_door_slats_norm` → `v_mps_kc04_slats_demand` mà doanh nghiệp đang dùng). Gọi `doorAreaM2 = zChieuCaoDh × zChieuRongDh`, `slatGroup = bomItem.slatMaterial.slatGroup` (tra qua quan hệ `BomItem → SlatMaterial`, không phải cột riêng trên `BomItem`):

**Bước 1 — Chiều rộng sản xuất** (dùng chung cho các nhóm không phải Nan chính):
```
productionWidthM = zChieuRongDh - widthOffsetM
    (fallback nếu widthOffsetM NULL: zChieuRongDh × 0.976)
```

**Bước 2 — `cutDimM`** (chiều dài phôi cần cắt cho 1 thanh), khác nhau theo `slatGroup`:

| `slatGroup` | `cutDimM` |
|---|---|
| MAIN_SLAT (Nan chính) | `zChieuRongDh` (đúng bằng chiều rộng cửa, KHÔNG trừ offset — nan có đột lỗ, phải chừa đầu) |
| BOTTOM_BAR, SUB_SLAT (Thanh đáy, Nan phụ) | `productionWidthM` |
| RAIL (Ray) | `zChieuCaoDh - heightOffsetM` |
| OTHER (Khác) | không xác định theo nhóm — luôn rơi vào fallback toàn phần ở Bước 4 |

**Bước 3 — `requiredPieces`** (số lượng thanh cần), khác nhau theo `slatGroup`:

| `slatGroup` | `requiredPieces` |
|---|---|
| MAIN_SLAT | Ưu tiên `ROUND(slatCountSlope × zChieuCaoDh + slatCountIntercept)` — chỉ dùng khi `slatCountR2 >= 0.5`; nếu không (NULL hoặc < 0.5): fallback `ROUND(doorAreaM2 × dinhMucMPerM2 / zChieuRongDh)` |
| BOTTOM_BAR, SUB_SLAT | luôn = 1 |
| RAIL | luôn = 2 |
| OTHER | không xác định theo nhóm |

**Bước 4 — Fallback toàn phần**: khi `slatGroup = OTHER` hoặc thiếu dữ liệu để tính `cutDimM`/`requiredPieces` ở trên, hệ thống **không tự suy ra được độ dài đoạn cần cắt** — chỉ có tổng độ dài ước tính `= dinhMucTbMPerBoCua` (mét/bộ cửa). Vì thuật toán cắt 1D cần biết độ dài từng đoạn cụ thể (không chỉ tổng mét), các `BomItem` rơi vào trường hợp này **không sinh được `CuttingDemand` tự động** — cần ghi log cảnh báo và loại khỏi phạm vi thuật toán ở giai đoạn khóa luận này, chờ ADMIN bổ sung công thức riêng nếu phát sinh thực tế (tới nay dữ liệu thật cho thấy đây là thiểu số).

`CuttingDemand.cutLength = cutDimM` (quy đổi mm), `CuttingDemand.quantity = requiredPieces` (không nhân thêm với "số lượng đặt hàng" vì mỗi `SalesOrder` đã luôn là đúng 1 bộ cửa — xem điểm 1, mục 3.3.1).

## 3. Luồng xem / xuất kết quả phương án cắt

```mermaid
sequenceDiagram
    actor U as PLANNER
    participant FE as Frontend
    participant C as CuttingPlanController
    participant REPO as CuttingPlanRepository
    participant EXP as ExcelExportService
    participant DB as MySQL

    U->>FE: Mở danh sách lịch sử phương án cắt
    FE->>C: GET /api/v1/cutting-plans
    C->>REPO: findAll()
    REPO->>DB: SELECT
    DB-->>REPO: rows
    REPO-->>C: List<CuttingPlan>
    C-->>FE: danh sách tóm tắt (thời điểm, tổng waste, trạng thái)
    FE-->>U: Hiển thị bảng lịch sử

    U->>FE: Chọn 1 phương án để xem chi tiết
    FE->>C: GET /api/v1/cutting-plans/{id}
    C->>REPO: findByIdWithDetails(id)
    REPO->>DB: SELECT ... JOIN CuttingPlanDetail
    DB-->>REPO: rows
    REPO-->>C: CuttingPlan + CuttingPlanDetail[]
    C-->>FE: CuttingPlanDetailDto
    FE-->>U: Vẽ sơ đồ cắt từng thanh (SVG)

    opt PLANNER bấm "Xuất Excel"
        FE->>C: GET /api/v1/cutting-plans/{id}/export
        C->>EXP: export(id)
        EXP->>REPO: findByIdWithDetails(id)
        REPO-->>EXP: CuttingPlan + CuttingPlanDetail[]
        EXP-->>C: file Excel (byte stream, Apache POI)
        C-->>FE: 200 + file
        FE-->>U: Tải file Excel kết quả cắt
    end
```
