package com.slatcut.cutting.repository;

import com.slatcut.cutting.domain.CuttingPlanDetail;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CuttingPlanDetailRepository extends JpaRepository<CuttingPlanDetail, Long> {

    @EntityGraph(attributePaths = "slatMaterial")
    List<CuttingPlanDetail> findByCuttingPlan_Id(Long cuttingPlanId);
}
