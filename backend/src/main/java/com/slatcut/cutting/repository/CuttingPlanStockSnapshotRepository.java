package com.slatcut.cutting.repository;

import com.slatcut.cutting.domain.CuttingPlanStockSnapshot;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CuttingPlanStockSnapshotRepository extends JpaRepository<CuttingPlanStockSnapshot, Long> {

    /**
     * Ảnh chụp tồn kho của một phương án đã duyệt. {@code @EntityGraph} vì báo cáo đọc mã loại
     * thanh nan trên từng dòng — thiếu nó thì mỗi dòng ảnh chụp lại bắn thêm một truy vấn.
     */
    @EntityGraph(attributePaths = "slatMaterial")
    List<CuttingPlanStockSnapshot> findByCuttingPlan_Id(Long cuttingPlanId);

    /** Chốt xóa loại thanh nan: nó đã có mặt trong ảnh chụp tồn kho của một phương án đã duyệt. */
    boolean existsBySlatMaterial_Id(Long slatMaterialId);
}
