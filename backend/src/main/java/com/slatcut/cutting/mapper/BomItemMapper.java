package com.slatcut.cutting.mapper;

import com.slatcut.cutting.domain.BomItem;
import com.slatcut.cutting.dto.BomItemRequest;
import com.slatcut.cutting.dto.BomItemResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface BomItemMapper {

    @Mapping(source = "doorProduct.id", target = "doorProductId")
    @Mapping(source = "doorProduct.doorMaterialName", target = "doorProductName")
    @Mapping(source = "doorProduct.mauSac", target = "doorProductMauSac")
    @Mapping(source = "slatMaterial.id", target = "slatMaterialId")
    @Mapping(source = "slatMaterial.slatMaterialName", target = "slatMaterialName")
    BomItemResponse toResponse(BomItem entity);

    @Mapping(target = "doorProduct", ignore = true)
    @Mapping(target = "slatMaterial", ignore = true)
    void updateEntity(BomItemRequest request, @MappingTarget BomItem entity);
}
