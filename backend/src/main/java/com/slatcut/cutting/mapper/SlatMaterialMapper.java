package com.slatcut.cutting.mapper;

import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.dto.SlatMaterialRequest;
import com.slatcut.cutting.dto.SlatMaterialResponse;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SlatMaterialMapper {

    SlatMaterialResponse toResponse(SlatMaterial entity);

    void updateEntity(SlatMaterialRequest request, @MappingTarget SlatMaterial entity);
}
