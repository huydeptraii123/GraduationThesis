package com.slatcut.cutting.dto;

import com.slatcut.cutting.domain.CuttingPlanStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CuttingPlanSummaryResponse(
        Long id,
        LocalDateTime runAt,
        CuttingPlanStatus status,
        BigDecimal totalWasteM,
        LocalDate scopeCutoffDate,
        Integer scopeOrderCount) {
}
