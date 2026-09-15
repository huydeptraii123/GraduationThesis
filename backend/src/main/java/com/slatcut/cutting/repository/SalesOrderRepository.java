package com.slatcut.cutting.repository;

import com.slatcut.cutting.domain.SalesOrder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    @Override
    @EntityGraph(attributePaths = {"customer", "doorProduct"})
    List<SalesOrder> findAll();

    Optional<SalesOrder> findByYcsxAndItem(String ycsx, Integer item);

    Optional<SalesOrder> findBySalesDocumentAndSalesOrderItem(Long salesDocument, Integer salesOrderItem);

    boolean existsByDoorProduct_Id(Long doorProductId);
}
