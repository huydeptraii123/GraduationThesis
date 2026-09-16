package com.slatcut.cutting.repository;

import com.slatcut.cutting.domain.CuttingPlan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CuttingPlanRepository extends JpaRepository<CuttingPlan, Long> {}
