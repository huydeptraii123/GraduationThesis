package com.slatcut.cutting.service.optimizer;

import java.util.List;

/** Kết quả tạm của 1 lần chạy CuttingStrategy — không phải entity, task 9.1 chuyển đổi sang CuttingPlan/CuttingPlanDetailItem/ShortageRecord. */
public record CuttingPlanResult(List<CutRecord> cuts, List<ShortageEntry> shortages) {}
