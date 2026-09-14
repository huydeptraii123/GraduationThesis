package com.slatcut.cutting.dto;

import java.math.BigDecimal;

public record BomItemResponse(
        Long id,
        Long doorProductId,
        String doorProductName,
        String doorProductMauSac,
        Long slatMaterialId,
        String slatMaterialName,
        BigDecimal widthOffsetM,
        BigDecimal heightOffsetM,
        BigDecimal slatCountSlope,
        BigDecimal slatCountIntercept,
        BigDecimal dinhMucTbMPerBoCua) {
}
