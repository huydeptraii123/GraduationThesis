package com.slatcut.cutting.dto;

import com.slatcut.cutting.domain.CutLevel;
import com.slatcut.cutting.domain.RemainderType;
import java.util.List;

/**
 * @param cutLevel mức ưu tiên mà thuật toán đã dùng cho phôi này; rỗng với phương án lưu từ trước
 *     khi hệ thống bắt đầu ghi lại mức
 * @param remainingSticksAfter số phôi cùng loại và cùng độ dài còn lại sau lần chạy; rỗng cùng lý
 *     do trên
 */
public record CuttingPlanDetailResponse(
        Long id,
        Long slatMaterialId,
        String slatMaterialName,
        Long slatMaterialCode,
        Integer sourceLengthMm,
        String patternCode,
        Integer remainderMm,
        RemainderType remainderType,
        CutLevel cutLevel,
        Integer stickCount,
        Integer remainingSticksAfter,
        List<CuttingPlanDetailItemResponse> items) {
}
