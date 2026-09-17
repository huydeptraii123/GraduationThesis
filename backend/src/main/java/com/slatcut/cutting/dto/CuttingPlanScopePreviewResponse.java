package com.slatcut.cutting.dto;

import java.time.LocalDate;

public record CuttingPlanScopePreviewResponse(Integer eligibleOrderCount, LocalDate scopeCutoffDate) {
}
