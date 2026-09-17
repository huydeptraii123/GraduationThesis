package com.slatcut.cutting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CuttingPlanDetailItemResponse(
        Long id,
        Long salesOrderId,
        String ycsx,
        Integer item,
        String customerName,
        Long doorProductId,
        String doorProductName,
        LocalDate reqdDeliveryDate,
        BigDecimal chieuCaoDh,
        BigDecimal chieuRongDh,
        Integer cutLengthMm,
        Integer cutQuantity,
        boolean originalOrder) {
}
