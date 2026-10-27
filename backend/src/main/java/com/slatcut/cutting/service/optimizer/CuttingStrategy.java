package com.slatcut.cutting.service.optimizer;

import com.slatcut.cutting.service.CuttingDemand;
import java.util.List;
import java.util.function.Predicate;

/** Strategy Pattern (docs/architecture.md 3.2.2) — mỗi cách tiếp cận sinh phương án cắt implement interface này. */
public interface CuttingStrategy {

    /** Không bó Mức 3 — mọi đoạn của lần chạy đều ghép được với nhau. */
    Predicate<CuttingDemand> ANY_DEMAND = demand -> true;

    /**
     * @param level3Eligible đoạn nào được phép ghép ở Mức 3 — một cặp chỉ được ghép khi CẢ HAI đoạn
     *     thỏa điều kiện này. Chức năng tính dùng nó để bó Mức 3 vào phạm vi đợt duyệt (t+3, tối đa
     *     70 đơn) dù vẫn tính trên toàn bộ đơn chưa duyệt; các mức khác không đọc tham số này.
     */
    CuttingPlanResult computePlan(
            List<CuttingDemand> demands, InventoryPool pool, Predicate<CuttingDemand> level3Eligible);

    default CuttingPlanResult computePlan(List<CuttingDemand> demands, InventoryPool pool) {
        return computePlan(demands, pool, ANY_DEMAND);
    }
}
