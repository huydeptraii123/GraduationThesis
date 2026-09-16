package com.slatcut.cutting.repository;

import com.slatcut.cutting.domain.CuttingPlan;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CuttingPlanRepository extends JpaRepository<CuttingPlan, Long> {

    List<CuttingPlan> findAllByOrderByRunAtDesc();
}
