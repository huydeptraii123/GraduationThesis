package com.slatcut.cutting.service.optimizer;

import com.slatcut.cutting.service.CuttingDemand;
import java.util.List;

/** Strategy Pattern (docs/architecture.md 3.2.2) — mỗi cách tiếp cận sinh phương án cắt implement interface này. */
public interface CuttingStrategy {
    CuttingPlanResult computePlan(List<CuttingDemand> demands, InventoryPool pool);
}
