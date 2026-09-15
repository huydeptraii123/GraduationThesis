package com.slatcut.cutting.repository;

import com.slatcut.cutting.domain.Customer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByCustomer(Long customer);
}
