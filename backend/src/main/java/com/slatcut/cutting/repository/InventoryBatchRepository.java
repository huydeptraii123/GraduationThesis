package com.slatcut.cutting.repository;

import com.slatcut.cutting.domain.InventoryBatch;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, Long> {

    Optional<InventoryBatch> findBySlatMaterial_IdAndDoDaiThanhMm(Long slatMaterialId, Integer doDaiThanhMm);

    boolean existsBySlatMaterial_Id(Long slatMaterialId);
}
