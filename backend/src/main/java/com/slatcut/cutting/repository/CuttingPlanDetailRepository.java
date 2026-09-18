package com.slatcut.cutting.repository;

import com.slatcut.cutting.domain.CuttingPlanDetail;
import com.slatcut.cutting.domain.RemainderType;
import com.slatcut.cutting.domain.SlatGroup;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CuttingPlanDetailRepository extends JpaRepository<CuttingPlanDetail, Long> {

    @EntityGraph(attributePaths = "slatMaterial")
    List<CuttingPlanDetail> findByCuttingPlan_Id(Long cuttingPlanId);

    /**
     * Tổng độ dài phần dư theo từng loại, gộp sẵn ở tầng DB cho dashboard. {@code remainderMm} là
     * phần dư của MỖI phôi nên bắt buộc nhân {@code stickCount} — 1 dòng detail có thể đại diện
     * nhiều phôi giống hệt nhau (bất biến gộp dòng của CuttingPlanService).
     */
    @Query(
            """
            SELECT d.remainderType AS remainderType, SUM(d.stickCount * d.remainderMm) AS totalMm
            FROM CuttingPlanDetail d
            GROUP BY d.remainderType
            """)
    List<RemainderTotal> sumRemainderMmByType();

    /** Chỉ phần phế thật (bỏ + lãng phí), tách theo nhóm thanh nan để biết nhóm nào hao nhiều nhất. */
    @Query(
            """
            SELECT d.slatMaterial.slatGroup AS slatGroup, SUM(d.stickCount * d.remainderMm) AS totalMm
            FROM CuttingPlanDetail d
            WHERE d.remainderType <> :restock
            GROUP BY d.slatMaterial.slatGroup
            """)
    List<SlatGroupTotal> sumWasteMmBySlatGroup(@Param("restock") RemainderType restock);

    interface RemainderTotal {
        RemainderType getRemainderType();

        Long getTotalMm();
    }

    interface SlatGroupTotal {
        SlatGroup getSlatGroup();

        Long getTotalMm();
    }
}
