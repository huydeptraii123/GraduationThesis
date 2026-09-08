package com.slatcut.cutting.dto;

import com.slatcut.cutting.domain.SlatGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SlatMaterialRequest {

    @NotNull
    private Long slatMaterial;

    @NotBlank
    private String slatMaterialName;

    @NotNull
    private SlatGroup slatGroup;
}
