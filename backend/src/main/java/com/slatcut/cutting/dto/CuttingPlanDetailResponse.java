package com.slatcut.cutting.dto;

import com.slatcut.cutting.domain.RemainderType;
import java.util.List;

public record CuttingPlanDetailResponse(
        Long id,
        Long slatMaterialId,
        String slatMaterialName,
        Long slatMaterialCode,
        Integer sourceLengthMm,
        String patternCode,
        Integer remainderMm,
        RemainderType remainderType,
        Integer stickCount,
        List<CuttingPlanDetailItemResponse> items) {
}
