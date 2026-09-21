package com.slatcut.cutting.repository;

import com.slatcut.cutting.domain.ShortageRecord;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShortageRecordRepository extends JpaRepository<ShortageRecord, Long> {


    @EntityGraph(attributePaths = {"salesOrder.customer", "salesOrder.doorProduct", "slatMaterial"})
    List<ShortageRecord> findByCuttingPlan_Id(Long cuttingPlanId);

    /** Chốt xóa loại thanh nan: nó đã bị báo thiếu trong một phương án đã duyệt thì không xóa được. */
    boolean existsBySlatMaterial_Id(Long slatMaterialId);
}
