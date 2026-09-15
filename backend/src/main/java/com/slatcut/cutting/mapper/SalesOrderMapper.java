package com.slatcut.cutting.mapper;

import com.slatcut.cutting.domain.SalesOrder;
import com.slatcut.cutting.dto.SalesOrderRequest;
import com.slatcut.cutting.dto.SalesOrderResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface SalesOrderMapper {

    @Mapping(source = "customer.id", target = "customerId")
    @Mapping(source = "customer.customerName", target = "customerName")
    @Mapping(source = "doorProduct.id", target = "doorProductId")
    @Mapping(source = "doorProduct.doorMaterialName", target = "doorProductName")
    @Mapping(source = "doorProduct.mauSac", target = "doorProductMauSac")
    SalesOrderResponse toResponse(SalesOrder entity);

    @Mapping(target = "customer", ignore = true)
    @Mapping(target = "doorProduct", ignore = true)
    void updateEntity(SalesOrderRequest request, @MappingTarget SalesOrder entity);
}
