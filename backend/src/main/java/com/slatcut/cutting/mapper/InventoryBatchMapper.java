package com.slatcut.cutting.mapper;

import com.slatcut.cutting.domain.InventoryBatch;
import com.slatcut.cutting.dto.InventoryBatchRequest;
import com.slatcut.cutting.dto.InventoryBatchResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface InventoryBatchMapper {

    @Mapping(source = "slatMaterial.id", target = "slatMaterialId")
    @Mapping(source = "slatMaterial.slatMaterialName", target = "slatMaterialName")
    InventoryBatchResponse toResponse(InventoryBatch entity);

    @Mapping(target = "slatMaterial", ignore = true)
    void updateEntity(InventoryBatchRequest request, @MappingTarget InventoryBatch entity);
}
