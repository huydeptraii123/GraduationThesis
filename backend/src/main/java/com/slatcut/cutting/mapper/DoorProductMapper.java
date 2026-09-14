package com.slatcut.cutting.mapper;

import com.slatcut.cutting.domain.DoorProduct;
import com.slatcut.cutting.dto.DoorProductRequest;
import com.slatcut.cutting.dto.DoorProductResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface DoorProductMapper {

    DoorProductResponse toResponse(DoorProduct entity);

    void updateEntity(DoorProductRequest request, @MappingTarget DoorProduct entity);
}
