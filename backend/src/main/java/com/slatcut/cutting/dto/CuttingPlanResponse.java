package com.slatcut.cutting.dto;

import com.slatcut.cutting.domain.CuttingPlanStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CuttingPlanResponse(
        Long id,
        LocalDateTime runAt,
        CuttingPlanStatus status,
        BigDecimal totalWasteM,
        LocalDate scopeCutoffDate,
        Integer scopeOrderCount,
        List<CuttingPlanDetailResponse> details,
        List<ShortageRecordResponse> shortages) {
}
