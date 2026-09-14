package com.slatcut.cutting.dto;

public record InventoryBatchResponse(
        Long id,
        Long slatMaterialId,
        String slatMaterialName,
        Integer doDaiThanhMm,
        Integer soThanh) {
}
