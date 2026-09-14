package com.slatcut.cutting.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BomItemRequest {

    @NotNull
    private Long doorProductId;

    @NotNull
    private Long slatMaterialId;

    private BigDecimal widthOffsetM;

    private BigDecimal heightOffsetM;

    private BigDecimal slatCountSlope;

    private BigDecimal slatCountIntercept;

    private BigDecimal dinhMucTbMPerBoCua;
}
