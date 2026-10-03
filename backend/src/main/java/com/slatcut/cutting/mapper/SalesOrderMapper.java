package com.slatcut.cutting.mapper;

import com.slatcut.cutting.domain.SalesOrder;
import com.slatcut.cutting.domain.SalesOrderProcessingStatus;
import com.slatcut.cutting.dto.SalesOrderRequest;
import com.slatcut.cutting.dto.SalesOrderResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface SalesOrderMapper {

    /**
     * Trạng thái xử lý nhận từ ngoài vào vì nó không phải thuộc tính của entity: SalesOrderService
     * suy ra nó cho cả trang bằng truy vấn riêng.
     */
    @Mapping(source = "entity.id", target = "id")
    @Mapping(source = "entity.customer.id", target = "customerId")
    @Mapping(source = "entity.customer.customerName", target = "customerName")
    @Mapping(source = "entity.doorProduct.id", target = "doorProductId")
    @Mapping(source = "entity.doorProduct.doorMaterialName", target = "doorProductName")
    @Mapping(source = "entity.doorProduct.mauSac", target = "doorProductMauSac")
    @Mapping(source = "processingStatus", target = "processingStatus")
    SalesOrderResponse toResponse(SalesOrder entity, SalesOrderProcessingStatus processingStatus);

    @Mapping(target = "customer", ignore = true)
    @Mapping(target = "doorProduct", ignore = true)
    void updateEntity(SalesOrderRequest request, @MappingTarget SalesOrder entity);
}
