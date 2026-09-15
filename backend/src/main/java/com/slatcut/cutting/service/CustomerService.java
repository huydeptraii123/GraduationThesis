package com.slatcut.cutting.service;

import com.slatcut.cutting.config.ResourceNotFoundException;
import com.slatcut.cutting.dto.CustomerResponse;
import com.slatcut.cutting.mapper.CustomerMapper;
import com.slatcut.cutting.repository.CustomerRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper mapper;

    public CustomerService(CustomerRepository customerRepository, CustomerMapper mapper) {
        this.customerRepository = customerRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> getAll() {
        return customerRepository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse getById(Long id) {
        return mapper.toResponse(customerRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng với id=" + id)));
    }
}
