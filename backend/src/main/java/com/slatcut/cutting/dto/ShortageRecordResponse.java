package com.slatcut.cutting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ShortageRecordResponse(
        Long id,
        Long slatMaterialId,
        String slatMaterialName,
        Long salesOrderId,
        String ycsx,
        Integer item,
        String customerName,
        Long doorProductId,
        String doorProductName,
        LocalDate reqdDeliveryDate,
        BigDecimal chieuCaoDh,
        BigDecimal chieuRongDh,
        Integer missingQuantity,
        BigDecimal missingLengthM) {
}
