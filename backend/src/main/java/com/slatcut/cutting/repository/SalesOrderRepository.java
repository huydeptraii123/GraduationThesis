package com.slatcut.cutting.repository;

import com.slatcut.cutting.domain.SalesOrder;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    @Override
    @EntityGraph(attributePaths = {"customer", "doorProduct"})
    List<SalesOrder> findAll();

    Optional<SalesOrder> findByYcsxAndItem(String ycsx, Integer item);

    Optional<SalesOrder> findBySalesDocumentAndSalesOrderItem(Long salesDocument, Integer salesOrderItem);

    boolean existsByDoorProduct_Id(Long doorProductId);

    /**
     * Phạm vi đợt xử lý của 1 lần sinh phương án cắt (docs/requirements-functional.md Nhóm 3):
     * đơn "chưa xử lý" (chưa có CuttingPlanDetailItem lẫn ShortageRecord tham chiếu tới — suy ra
     * động, không phải cột trạng thái) có reqdDeliveryDate <= cutoffDate, sắp theo đúng thứ tự ưu
     * tiên (reqdDeliveryDate, ycsx, item). Gọi với Pageable.ofSize(70) để giới hạn "dưới 70 đơn"
     * ngay trong query — đơn ngoài phạm vi này thuộc "nhóm 99", không cần lọc riêng.
     */
    @Query("""
            SELECT so FROM SalesOrder so
            WHERE so.reqdDeliveryDate <= :cutoffDate
              AND NOT EXISTS (SELECT 1 FROM CuttingPlanDetailItem i WHERE i.salesOrder = so)
              AND NOT EXISTS (SELECT 1 FROM ShortageRecord sr WHERE sr.salesOrder = so)
            ORDER BY so.reqdDeliveryDate ASC, so.ycsx ASC, so.item ASC
            """)
    List<SalesOrder> findUnprocessedInScope(@Param("cutoffDate") LocalDate cutoffDate, Pageable pageable);
}
