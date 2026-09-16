package com.slatcut.cutting.repository;

import com.slatcut.cutting.domain.CuttingPlanDetailItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CuttingPlanDetailItemRepository extends JpaRepository<CuttingPlanDetailItem, Long> {

    boolean existsBySalesOrder_Id(Long salesOrderId);
}
