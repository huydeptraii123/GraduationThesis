package com.slatcut.cutting.dto;

import com.slatcut.cutting.domain.StockStatus;

public record InventoryBatchResponse(
        Long id,
        Long slatMaterialId,
        String slatMaterialName,
        Integer doDaiThanhMm,
        Integer soThanh,
        StockStatus stockStatus) {
}
