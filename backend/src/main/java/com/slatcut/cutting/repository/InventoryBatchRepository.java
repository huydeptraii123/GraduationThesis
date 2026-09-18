package com.slatcut.cutting.repository;

import com.slatcut.cutting.domain.InventoryBatch;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, Long> {

    @Override
    @EntityGraph(attributePaths = "slatMaterial")
    List<InventoryBatch> findAll();

    Optional<InventoryBatch> findBySlatMaterial_IdAndDoDaiThanhMm(Long slatMaterialId, Integer doDaiThanhMm);

    boolean existsBySlatMaterial_Id(Long slatMaterialId);

    /** Lô còn hàng — lô bị cắt hết vẫn giữ lại dòng với so_thanh = 0 nên phải lọc, không đếm tất cả. */
    long countBySoThanhGreaterThan(int threshold);

    @Query("SELECT COALESCE(SUM(b.soThanh), 0) FROM InventoryBatch b WHERE b.soThanh > 0")
    long sumAvailableSticks();
}
