package com.slatcut.cutting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SalesOrderResponse(
        Long id,
        String ycsx,
        Integer item,
        Long salesDocument,
        Integer salesOrderItem,
        Long customerId,
        String customerName,
        Long doorProductId,
        String doorProductName,
        String doorProductMauSac,
        BigDecimal chieuCaoDh,
        BigDecimal chieuRongDh,
        LocalDate reqdDeliveryDate) {
}
