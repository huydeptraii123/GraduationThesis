package com.slatcut.cutting.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SalesOrderRequest {

    @NotBlank
    @Size(max = 20)
    private String ycsx;

    @NotNull
    private Integer item;

    @NotNull
    private Long salesDocument;

    @NotNull
    private Integer salesOrderItem;

    @NotNull
    private Long customerId;

    @NotNull
    private Long doorProductId;

    @NotNull
    @Positive
    @Digits(integer = 3, fraction = 3)
    private BigDecimal chieuCaoDh;

    @NotNull
    @Positive
    @Digits(integer = 3, fraction = 3)
    private BigDecimal chieuRongDh;

    @NotNull
    private LocalDate reqdDeliveryDate;
}
