package com.slatcut.cutting.dto;

import com.slatcut.cutting.domain.SlatGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SlatMaterialRequest {

    @NotNull
    private Long slatMaterial;

    @NotBlank
    @Size(max = 255)
    private String slatMaterialName;

    @NotNull
    private SlatGroup slatGroup;
}
