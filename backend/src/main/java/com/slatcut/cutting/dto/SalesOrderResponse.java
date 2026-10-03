package com.slatcut.cutting.dto;

import com.slatcut.cutting.domain.SalesOrderProcessingStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/** @param processingStatus trạng thái xử lý suy ra lúc đọc, không lưu trong bảng đơn hàng. */
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
        LocalDate reqdDeliveryDate,
        SalesOrderProcessingStatus processingStatus) {
}
