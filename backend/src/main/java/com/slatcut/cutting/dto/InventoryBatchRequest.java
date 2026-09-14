package com.slatcut.cutting.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InventoryBatchRequest {

    @NotNull
    private Long slatMaterialId;

    @NotNull
    @Positive
    private Integer doDaiThanhMm;

    @NotNull
    @Min(0)
    private Integer soThanh;
}
