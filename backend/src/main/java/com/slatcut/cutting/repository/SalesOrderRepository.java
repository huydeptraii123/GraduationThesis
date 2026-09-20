package com.slatcut.cutting.repository;

import com.slatcut.cutting.domain.CuttingPlan;
import com.slatcut.cutting.domain.SalesOrder;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long>, JpaSpecificationExecutor<SalesOrder> {

    @Override
    @EntityGraph(attributePaths = {"customer", "doorProduct"})
    List<SalesOrder> findAll();

    /** Bản phân trang cho màn hình danh sách; {@code @EntityGraph} giữ nguyên chống N+1 như trên. */
    @Override
    @EntityGraph(attributePaths = {"customer", "doorProduct"})
    Page<SalesOrder> findAll(Specification<SalesOrder> spec, Pageable pageable);

    Optional<SalesOrder> findByYcsxAndItem(String ycsx, Integer item);

    Optional<SalesOrder> findBySalesDocumentAndSalesOrderItem(Long salesDocument, Integer salesOrderItem);

    boolean existsByDoorProduct_Id(Long doorProductId);

    /**
     * Phạm vi 1 lần DUYỆT phương án cắt (docs/requirements-functional.md Nhóm 3): đơn chưa
     * duyệt (approvedPlan IS NULL) có reqdDeliveryDate <= cutoffDate, sắp theo đúng thứ tự ưu tiên
     * (reqdDeliveryDate, ycsx, item). Gọi với Pageable.ofSize(70) để giới hạn "dưới 70 đơn" ngay
     * trong query — đơn ngoài phạm vi này thuộc "nhóm 99", không cần lọc riêng.
     *
     * <p>Khác {@link #findUnapproved()} đúng ở hai chỗ: có mốc ngày giao và có hạn mức số đơn.
     * Chức năng "tính phương án cắt" cố ý bỏ cả hai để nhìn được bức tranh thiếu hụt của toàn bộ
     * đơn tồn.
     *
     * <p>Điều kiện EXISTS ở cuối là bắt buộc: đơn sinh ra 0 nhu cầu cắt vẫn sẽ được gán
     * approvedPlan nếu lọt vào phạm vi, tức bị đánh dấu "đã duyệt" trong khi không sản xuất được gì
     * — nó là đơn ĐANG BỊ CHẶN chờ ADMIN khai báo định mức, không phải đơn đã xử lý xong. Không đủ
     * nếu chỉ hỏi "mẫu cửa có dòng định mức nào không": CuttingDemandService còn bỏ qua từng dòng
     * một (MAIN_SLAT thiếu hệ số tính số nan, RAIL thiếu heightOffsetM, nhóm OTHER không có công
     * thức cắt), nên mẫu cửa có định mức mà mọi dòng đều bị bỏ qua vẫn phải bị loại y hệt. Điều
     * kiện dưới đây phản chiếu đúng các quy tắc bỏ qua đó — <b>sửa CuttingDemandService.buildDemand
     * thì phải sửa cả đây</b>, và CuttingPlanServiceTest có test khoá hai nơi khỏi lệch nhau.
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
              AND so.approvedPlan IS NULL
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
     * Phạm vi 1 lần TÍNH phương án cắt: toàn bộ đơn chưa duyệt sinh được ít nhất 1 nhu cầu cắt,
     * KHÔNG lọc theo ngày giao và KHÔNG giới hạn số đơn (docs/requirements-functional.md Nhóm 3).
     * Bỏ hai giới hạn đó chính là giá trị của chức năng tính: nó trả lời được câu hỏi "với toàn bộ
     * đơn đang có và toàn bộ tồn kho hiện tại, còn thiếu loại thanh nan nào" — thứ mà một đợt duyệt
     * tối đa 70 đơn không bao giờ trả lời được.
     *
     * <p>Điều kiện lọc định mức giữ y nguyên {@link #findUnprocessedInScope}: đơn không sinh được
     * nhu cầu cắt nào thì đưa vào cũng chỉ ra 0 dòng kết quả, chỉ làm nhiễu báo cáo. Số đơn bị loại
     * vì lý do này được đếm riêng và cảnh báo trên trang chủ.
     *
     * <p>JOIN FETCH customer/doorProduct ở đây mà {@link #findUnprocessedInScope} không có: query
     * này quét toàn bảng (không có hạn mức 70 đơn) và mọi dòng đều bị đọc tên khách hàng/mẫu cửa
     * khi dựng báo cáo, nên N+1 ở đây là hàng trăm câu truy vấn chứ không phải vài chục. Cả hai
     * quan hệ đều là nhiều-một nên JOIN FETCH không nhân bản dòng.
     */
    @Query("""
            SELECT DISTINCT so FROM SalesOrder so
            JOIN FETCH so.customer
            JOIN FETCH so.doorProduct
            WHERE so.approvedPlan IS NULL
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
    List<SalesOrder> findUnapproved();

    /**
     * Gán trạng thái đã duyệt cho toàn bộ đơn trong phạm vi bằng 1 câu UPDATE, thay vì nạp từng
     * entity ra rồi set — phạm vi có thể tới 70 đơn và không có lý do gì để kéo chúng vào
     * persistence context chỉ để đổi 1 khóa ngoại.
     *
     * <p>{@code clearAutomatically}/{@code flushAutomatically} là bắt buộc: cùng transaction duyệt
     * có đọc lại SalesOrder (persist CuttingPlanDetailItem/ShortageRecord tham chiếu tới chúng),
     * nếu không xóa cache mức 1 thì các entity đã nạp trước đó vẫn mang approvedPlan cũ. Vì lý do
     * đó, gọi hàm này sau cùng trong transaction — mọi entity nạp trước nó đều trở thành detached.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE SalesOrder so SET so.approvedPlan = :plan WHERE so.id IN :ids")
    int markApproved(@Param("plan") CuttingPlan plan, @Param("ids") List<Long> ids);

    /**
     * Cùng điều kiện "chưa duyệt và trong hạn giao" với {@link #findUnprocessedInScope} nhưng KHÔNG
     * giới hạn 70 đơn — dùng cho KPI tồn đọng ở trang chủ, nơi cần biết số đơn thật đang chờ chứ
     * không phải số đơn lấy được trong 1 lần chạy. Sửa điều kiện ở 1 trong 2 query thì phải sửa cả
     * hai.
     */
    @Query("""
            SELECT COUNT(DISTINCT so) FROM SalesOrder so
            WHERE so.reqdDeliveryDate <= :cutoffDate
              AND so.approvedPlan IS NULL
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
              AND so.approvedPlan IS NULL
            """)
    long countUnprocessedInScopeIgnoringBom(@Param("cutoffDate") LocalDate cutoffDate);
}
