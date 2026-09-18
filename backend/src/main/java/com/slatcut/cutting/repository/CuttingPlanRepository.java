package com.slatcut.cutting.repository;

import com.slatcut.cutting.domain.CuttingPlan;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CuttingPlanRepository extends JpaRepository<CuttingPlan, Long> {

    List<CuttingPlan> findAllByOrderByRunAtDesc();

    /** N lần chạy gần nhất cho biểu đồ xu hướng — tie-break bằng id để thứ tự tái lập được khi 2 lần chạy trùng runAt. */
    @Query("SELECT p FROM CuttingPlan p ORDER BY p.runAt DESC, p.id DESC")
    List<CuttingPlan> findRecent(Pageable pageable);

    /** Cộng dồn toàn bộ lịch sử, gộp ở tầng DB thay vì tải hết bản ghi về rồi cộng trong Java. */
    @Query(
            """
            SELECT COALESCE(SUM(p.totalWasteM), 0) AS totalWasteM,
                   COALESCE(SUM(p.totalStockUsedM), 0) AS totalStockUsedM
            FROM CuttingPlan p
            """)
    CumulativeTotals sumTotals();

    interface CumulativeTotals {
        BigDecimal getTotalWasteM();

        BigDecimal getTotalStockUsedM();
    }
}
