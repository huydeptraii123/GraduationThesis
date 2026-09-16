package com.slatcut.cutting.dto;

public record CuttingPlanDetailItemResponse(
        Long id,
        Long salesOrderId,
        String ycsx,
        Integer item,
        Integer cutLengthMm,
        Integer cutQuantity,
        boolean originalOrder) {
}
