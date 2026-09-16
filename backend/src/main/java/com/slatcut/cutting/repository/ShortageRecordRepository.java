package com.slatcut.cutting.repository;

import com.slatcut.cutting.domain.ShortageRecord;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShortageRecordRepository extends JpaRepository<ShortageRecord, Long> {

    boolean existsBySalesOrder_Id(Long salesOrderId);

    @EntityGraph(attributePaths = {"salesOrder", "slatMaterial"})
    List<ShortageRecord> findByCuttingPlan_Id(Long cuttingPlanId);
}
