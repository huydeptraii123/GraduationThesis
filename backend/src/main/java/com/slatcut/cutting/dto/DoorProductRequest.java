package com.slatcut.cutting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DoorProductRequest {

    @NotNull
    private Long material;

    @NotBlank
    @Size(max = 255)
    private String doorMaterialName;

    @NotBlank
    @Size(max = 20)
    private String mauSac;
}
