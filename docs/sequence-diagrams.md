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
        SVC->>REPO: upsert theo sales_document + item
        REPO->>DB: INSERT / UPDATE
        DB-->>REPO: OK
        REPO-->>SVC: OK
        SVC-->>C: số dòng đã nhập
        C-->>FE: 200 + tóm tắt kết quả
        FE-->>U: Hiển thị "Nhập thành công N dòng"
    end
```

## 2. Luồng sinh phương án cắt (luồng lõi)

Luồng quan trọng nhất của khóa luận — thể hiện đúng 4 mức ưu tiên đã chốt (`.claude/PLAN.md` mục 3.4.4): khớp gần đúng → cắt theo bội số (cùng ngày giao) → ghép nối nhiều đơn (cùng ngày giao) → best-fit/nhập kho/shortage.

```mermaid
sequenceDiagram
    actor U as PLANNER
    participant FE as Frontend
    participant C as CuttingPlanController
    participant DS as CuttingDemandService
    participant CS as CuttingStrategy
    participant POOL as InventoryPool
    participant REPO as CuttingPlanRepository
    participant DB as MySQL

    U->>FE: Bấm "Sinh phương án cắt"
    FE->>C: POST /api/v1/cutting-plans/generate
    C->>DS: buildDemands()
    DS->>DB: lấy SalesOrder chưa cắt + BomItem tương ứng
    DB-->>DS: rows
    DS-->>C: List<CuttingDemand> (slatMaterial, cutLength, qty, reqd_delivery_date, soNumber)

    C->>POOL: load(InventoryBatch hiện có)
    POOL-->>C: pool sẵn sàng

    C->>CS: computePlan(demands, pool)

    loop mỗi slatMaterial group
        CS->>CS: sort hàng đợi theo (reqd_delivery_date, so_number)
        loop while hàng đợi còn đoạn X chưa cắt
            alt Mức 1 — khớp gần đúng (dư < 30cm)
                CS->>POOL: findNearFit(X)
                POOL-->>CS: thanh khớp -> cắt, dư "bỏ"
            else Mức 2 — cắt theo bội số (cùng reqd_delivery_date với X)
                CS->>POOL: findMultipleOfSameLength(X)
                POOL-->>CS: thanh dài gấp k lần -> cắt k đoạn, dư = 0
            else Mức 3 — ghép nối nhiều đơn (cùng reqd_delivery_date với X)
                CS->>POOL: findCombination(X, hàng đợi cùng ngày giao)
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

    CS-->>C: CuttingPlanResult (chi tiết cắt từng thanh, danh sách shortage, tổng waste)
    C->>REPO: save(CuttingPlan, CuttingPlanDetail[])
    REPO->>DB: INSERT
    DB-->>REPO: OK
    REPO-->>C: CuttingPlan đã lưu (id)
    C-->>FE: CuttingPlanDto
    FE-->>U: Vẽ sơ đồ cắt (CuttingBarDiagram) + trạng thái từng đơn (đủ vật tư / thiếu vật tư)
```

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
