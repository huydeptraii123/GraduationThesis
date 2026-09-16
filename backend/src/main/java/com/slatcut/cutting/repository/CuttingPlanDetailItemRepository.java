package com.slatcut.cutting.repository;

import com.slatcut.cutting.domain.CuttingPlanDetailItem;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CuttingPlanDetailItemRepository extends JpaRepository<CuttingPlanDetailItem, Long> {

    boolean existsBySalesOrder_Id(Long salesOrderId);

    @EntityGraph(attributePaths = "salesOrder")
    List<CuttingPlanDetailItem> findByCuttingPlanDetail_IdIn(List<Long> cuttingPlanDetailIds);
}
