package com.slatcut.cutting.repository;

import com.slatcut.cutting.domain.InventoryBatch;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, Long> {

    @Override
    @EntityGraph(attributePaths = "slatMaterial")
    List<InventoryBatch> findAll();

    Optional<InventoryBatch> findBySlatMaterial_IdAndDoDaiThanhMm(Long slatMaterialId, Integer doDaiThanhMm);

    boolean existsBySlatMaterial_Id(Long slatMaterialId);
}
