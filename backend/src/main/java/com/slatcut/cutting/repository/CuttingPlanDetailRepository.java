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

    /** Chốt xóa loại thanh nan: nó đã được cắt trong một phương án đã duyệt thì không xóa được. */
    boolean existsBySlatMaterial_Id(Long slatMaterialId);

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

    /**
     * Phế thật và tồn kho thực tiêu hao của từng nhóm thanh nan, để dashboard vẽ được <b>tỷ lệ</b>
     * phế chứ không chỉ số mét.
     *
     * <p>Tính bằng mét thì nhóm nan chính luôn cao nhất chỉ vì nó chiếm gần hết vật tư, che mất
     * nhóm thật sự cắt kém. Mẫu số ở đây lặp đúng công thức {@code CuttingPlanService#totalStockUsedM}
     * — phần dư nhập lại kho bị trừ khỏi lượng tiêu hao vì nó còn dùng được — nên tỷ lệ theo nhóm
     * và tỷ lệ tổng của một lần chạy không bao giờ nói hai chuyện khác nhau.
     *
     * <p>Khác truy vấn cũ ở chỗ KHÔNG lọc bỏ dòng nhập lại kho: chúng không góp vào tử số nhưng
     * bắt buộc phải góp vào mẫu số, và một nhóm chỉ toàn phần dư nhập kho vẫn phải hiện lên với
     * tỷ lệ 0% thay vì biến mất khỏi biểu đồ.
     */
    @Query(
            """
            SELECT d.slatMaterial.slatGroup AS slatGroup,
                   SUM(CASE WHEN d.remainderType <> :restock THEN d.stickCount * d.remainderMm ELSE 0L END) AS wasteMm,
                   SUM(d.stickCount * (CASE WHEN d.remainderType = :restock
                                            THEN d.sourceLengthMm - d.remainderMm
                                            ELSE d.sourceLengthMm END)) AS stockUsedMm
            FROM CuttingPlanDetail d
            GROUP BY d.slatMaterial.slatGroup
            """)
    List<SlatGroupTotal> sumWasteAndStockUsedMmBySlatGroup(@Param("restock") RemainderType restock);

    interface RemainderTotal {
        RemainderType getRemainderType();

        Long getTotalMm();
    }

    interface SlatGroupTotal {
        SlatGroup getSlatGroup();

        Long getWasteMm();

        Long getStockUsedMm();
    }
}
