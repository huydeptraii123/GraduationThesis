package com.slatcut.cutting.dto;

import java.math.BigDecimal;

public record ShortageRecordResponse(
        Long id,
        Long slatMaterialId,
        String slatMaterialName,
        Long salesOrderId,
        String ycsx,
        Integer item,
        Integer missingQuantity,
        BigDecimal missingLengthM) {
}
