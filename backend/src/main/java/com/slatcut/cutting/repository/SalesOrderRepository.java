package com.slatcut.cutting.repository;

import com.slatcut.cutting.domain.SalesOrder;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    @Override
    @EntityGraph(attributePaths = {"customer", "doorProduct"})
    List<SalesOrder> findAll();

    Optional<SalesOrder> findByYcsxAndItem(String ycsx, Integer item);

    Optional<SalesOrder> findBySalesDocumentAndSalesOrderItem(Long salesDocument, Integer salesOrderItem);

    boolean existsByDoorProduct_Id(Long doorProductId);

    /**
     * Phạm vi đợt xử lý của 1 lần sinh phương án cắt (docs/requirements-functional.md Nhóm 3):
     * đơn "chưa xử lý" (chưa có CuttingPlanDetailItem lẫn ShortageRecord tham chiếu tới — suy ra
     * động, không phải cột trạng thái) có reqdDeliveryDate <= cutoffDate, sắp theo đúng thứ tự ưu
     * tiên (reqdDeliveryDate, ycsx, item). Gọi với Pageable.ofSize(70) để giới hạn "dưới 70 đơn"
     * ngay trong query — đơn ngoài phạm vi này thuộc "nhóm 99", không cần lọc riêng.
     *
     * <p>Điều kiện EXISTS ở cuối là bắt buộc: đơn sinh ra 0 nhu cầu cắt sẽ không để lại
     * CuttingPlanDetailItem lẫn ShortageRecord nên mãi mãi bị coi là "chưa xử lý", chiếm chỗ trong
     * hạn mức 70 đơn của MỌI lần chạy sau. Không đủ nếu chỉ hỏi "mẫu cửa có dòng định mức nào
     * không": CuttingDemandService còn bỏ qua từng dòng một (MAIN_SLAT thiếu hệ số tính số nan, RAIL
     * thiếu heightOffsetM, nhóm OTHER không có công thức cắt), nên mẫu cửa có định mức mà mọi dòng
     * đều bị bỏ qua vẫn kẹt y hệt. Điều kiện dưới đây phản chiếu đúng các quy tắc bỏ qua đó —
     * <b>sửa CuttingDemandService.buildDemand thì phải sửa cả đây</b>, và
     * CuttingPlanServiceTest có test khoá hai nơi khỏi lệch nhau.
     *
     * <p>Đơn bị loại ở đây được đếm riêng (xem {@link #countUnprocessedInScopeIgnoringBom}) và cảnh
     * báo trên trang chủ, không bị bỏ quên âm thầm.
     *
     * <p><b>DISTINCT là bắt buộc, không phải thừa:</b> MySQL có thể hiện thực EXISTS thành semi-join
     * và trả về đơn hàng lặp lại đúng bằng số dòng định mức khớp, tùy kế hoạch tối ưu nó chọn — nên
     * lỗi chỉ lộ ra khi thống kê bảng thay đổi (chạy cả bộ test thì hỏng, chạy riêng lớp test thì
     * không). Thiếu DISTINCT, {@code generate()} gom danh sách này vào Map theo (ycsx, item) sẽ ném
     * lỗi khóa trùng.
     */
    @Query("""
            SELECT DISTINCT so FROM SalesOrder so
            WHERE so.reqdDeliveryDate <= :cutoffDate
              AND NOT EXISTS (SELECT 1 FROM CuttingPlanDetailItem i WHERE i.salesOrder = so)
              AND NOT EXISTS (SELECT 1 FROM ShortageRecord sr WHERE sr.salesOrder = so)
              AND EXISTS (
                    SELECT 1 FROM BomItem b JOIN b.slatMaterial sm
                    WHERE b.doorProduct = so.doorProduct AND (
                         sm.slatGroup IN (com.slatcut.cutting.domain.SlatGroup.SUB_SLAT,
                                          com.slatcut.cutting.domain.SlatGroup.BOTTOM_BAR)
                      OR (sm.slatGroup = com.slatcut.cutting.domain.SlatGroup.MAIN_SLAT
                            AND b.slatCountSlope IS NOT NULL AND b.slatCountIntercept IS NOT NULL)
                      OR (sm.slatGroup = com.slatcut.cutting.domain.SlatGroup.RAIL
                            AND b.heightOffsetM IS NOT NULL)))
            ORDER BY so.reqdDeliveryDate ASC, so.ycsx ASC, so.item ASC
            """)
    List<SalesOrder> findUnprocessedInScope(@Param("cutoffDate") LocalDate cutoffDate, Pageable pageable);

    /**
     * Cùng điều kiện "chưa xử lý và trong hạn giao" với {@link #findUnprocessedInScope} nhưng KHÔNG
     * giới hạn 70 đơn — dùng cho KPI tồn đọng ở trang chủ, nơi cần biết số đơn thật đang chờ chứ
     * không phải số đơn lấy được trong 1 lần chạy. Sửa điều kiện ở 1 trong 2 query thì phải sửa cả
     * hai.
     */
    @Query("""
            SELECT COUNT(DISTINCT so) FROM SalesOrder so
            WHERE so.reqdDeliveryDate <= :cutoffDate
              AND NOT EXISTS (SELECT 1 FROM CuttingPlanDetailItem i WHERE i.salesOrder = so)
              AND NOT EXISTS (SELECT 1 FROM ShortageRecord sr WHERE sr.salesOrder = so)
              AND EXISTS (
                    SELECT 1 FROM BomItem b JOIN b.slatMaterial sm
                    WHERE b.doorProduct = so.doorProduct AND (
                         sm.slatGroup IN (com.slatcut.cutting.domain.SlatGroup.SUB_SLAT,
                                          com.slatcut.cutting.domain.SlatGroup.BOTTOM_BAR)
                      OR (sm.slatGroup = com.slatcut.cutting.domain.SlatGroup.MAIN_SLAT
                            AND b.slatCountSlope IS NOT NULL AND b.slatCountIntercept IS NOT NULL)
                      OR (sm.slatGroup = com.slatcut.cutting.domain.SlatGroup.RAIL
                            AND b.heightOffsetM IS NOT NULL)))
            """)
    long countUnprocessedInScope(@Param("cutoffDate") LocalDate cutoffDate);

    /**
     * Như {@link #countUnprocessedInScope} nhưng BỎ HẲN điều kiện về định mức. Số đơn bị loại vì
     * thiếu định mức dùng được suy ra bằng phép trừ giữa hai con số, thay vì viết một điều kiện phủ
     * định thứ ba — hai vế khi đó không thể lệch nhau dù quy tắc "dòng định mức dùng được" có đổi.
     */
    @Query("""
            SELECT COUNT(DISTINCT so) FROM SalesOrder so
            WHERE so.reqdDeliveryDate <= :cutoffDate
              AND NOT EXISTS (SELECT 1 FROM CuttingPlanDetailItem i WHERE i.salesOrder = so)
              AND NOT EXISTS (SELECT 1 FROM ShortageRecord sr WHERE sr.salesOrder = so)
            """)
    long countUnprocessedInScopeIgnoringBom(@Param("cutoffDate") LocalDate cutoffDate);
}
