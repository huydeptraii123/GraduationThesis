package com.slatcut.cutting.dto;

import com.slatcut.cutting.domain.SlatGroup;

public record SlatMaterialResponse(Long id, Long slatMaterial, String slatMaterialName, SlatGroup slatGroup) {
}
