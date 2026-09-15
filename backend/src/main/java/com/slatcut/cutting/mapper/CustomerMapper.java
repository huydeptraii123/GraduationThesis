package com.slatcut.cutting.mapper;

import com.slatcut.cutting.domain.Customer;
import com.slatcut.cutting.dto.CustomerResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

    CustomerResponse toResponse(Customer entity);
}
