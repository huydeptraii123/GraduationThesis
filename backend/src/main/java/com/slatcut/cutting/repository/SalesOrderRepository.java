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
     * Điều kiện "mẫu cửa của đơn có định mức ĐẦY ĐỦ để tính nhu cầu cắt", viết một lần ở đây và
     * ghép vào cả ba truy vấn phạm vi bên dưới (phạm vi duyệt, phạm vi tính, bản đếm của phạm vi
     * duyệt) để ba bản không thể lệch nhau. Đơn chưa duyệt không thỏa điều kiện này là đơn ĐANG BỊ
     * CHẶN chờ ADMIN bổ sung định mức.
     *
     * <p>Hai vế, đúng bằng hai nhánh bỏ qua bộ cửa của CuttingDemandService.buildDemands:
     * <ul>
     *   <li>Có ít nhất một dòng định mức thuộc nhóm có công thức cắt (mọi nhóm trừ OTHER) — không có
     *       thì đơn sinh 0 nhu cầu cắt.
     *   <li>KHÔNG có dòng nào thiếu tham số của chính công thức đó: MAIN_SLAT thiếu một trong hai hệ
     *       số tính số nan, RAIL thiếu heightOffsetM. Mỗi dòng định mức là một thành phần bắt buộc của
     *       bộ cửa, nên thiếu một dòng là nhu cầu cắt của cả bộ không đầy đủ: cứ tính các dòng còn lại
     *       thì bộ cửa vẫn có thể hiện "đủ nan" trong khi nan chính chưa từng được tính. Dữ liệu thật
     *       có đúng trường hợp này ở quy mô lớn — một mẫu cửa có cả hai dòng nan chính thiếu hệ số
     *       chiếm phần lớn sổ đơn.
     * </ul>
     * Nhóm OTHER không có mặt ở vế nào: nó không cắt từ thanh tồn kho nên không làm đơn bị chặn.
     * Vế đầu chỉ cần hỏi "có dòng ngoài OTHER không" vì vế sau đã bảo đảm mọi dòng như vậy đều đủ
     * tham số.
     *
     * <p><b>Sửa CuttingDemandService.buildDemands thì phải sửa cả đây lẫn bản Criteria
     * {@code SalesOrderSpecifications.hasCompleteBom}</b> (bộ lọc trạng thái ở màn đơn hàng, phải
     * ghép được với điều kiện khác và phân trang nên không gọi lại được JPQL này).
     * SalesOrderServiceTest.processingStatus_blockedMatchesProcessingScopeRule khoá các bản khỏi lệch
     * nhau.
     */
    String HAS_COMPLETE_BOM = """
            EXISTS (
                  SELECT 1 FROM BomItem b JOIN b.slatMaterial sm
                  WHERE b.doorProduct = so.doorProduct
                    AND sm.slatGroup <> com.slatcut.cutting.domain.SlatGroup.OTHER)
              AND NOT EXISTS (
                  SELECT 1 FROM BomItem ib JOIN ib.slatMaterial ism
                  WHERE ib.doorProduct = so.doorProduct AND (
                       (ism.slatGroup = com.slatcut.cutting.domain.SlatGroup.MAIN_SLAT
                          AND (ib.slatCountSlope IS NULL OR ib.slatCountIntercept IS NULL))
                    OR (ism.slatGroup = com.slatcut.cutting.domain.SlatGroup.RAIL
                          AND ib.heightOffsetM IS NULL)))
            """;

    /**
     * Phạm vi 1 lần DUYỆT phương án cắt (docs/requirements-functional.md Nhóm 3): đơn chưa
     * duyệt (approvedPlan IS NULL) có reqdDeliveryDate <= cutoffDate, sắp theo đúng thứ tự ưu tiên
     * (reqdDeliveryDate, ycsx, item). Gọi với Pageable.ofSize(70) để giới hạn "tối đa 70 đơn" ngay
     * trong query — đơn ngoài phạm vi này thuộc "nhóm 99", không cần lọc riêng.
     *
     * <p>Khác {@link #findUnapproved()} đúng ở hai chỗ: có mốc ngày giao và có hạn mức số đơn.
     * Chức năng "tính phương án cắt" cố ý bỏ cả hai để nhìn được bức tranh thiếu hụt của toàn bộ
     * đơn tồn.
     *
     * <p>Điều kiện định mức ở cuối ({@link #HAS_COMPLETE_BOM}) là bắt buộc: đơn sinh ra 0 nhu cầu
     * cắt vẫn sẽ được gán approvedPlan nếu lọt vào phạm vi, tức bị đánh dấu "đã duyệt" trong khi
     * không sản xuất được gì; còn đơn chỉ tính được một phần định mức thì bị duyệt với kết quả đủ/thiếu
     * sai. Cả hai đều là đơn ĐANG BỊ CHẶN chờ ADMIN bổ sung định mức, không phải đơn đã xử lý xong.
     *
     * <p>Đơn bị loại ở đây được đếm riêng (xem {@link #countUnprocessedInScopeIgnoringBom}) và cảnh
     * báo trên trang chủ, không bị bỏ quên âm thầm.
     *
     * <p>JOIN FETCH customer/doorProduct vì phương án trình cho PLANNER được dựng thành báo cáo
     * SAU khi transaction đọc đã đóng: thiếu nó thì mọi lần đọc tên khách hàng hay mẫu cửa đều là
     * một proxy đã mất phiên. Cả hai quan hệ đều là nhiều-một nên JOIN FETCH không nhân bản dòng và
     * hạn mức 70 đơn vẫn được CSDL áp đúng.
     *
     * <p><b>DISTINCT là bắt buộc, không phải thừa:</b> MySQL có thể hiện thực EXISTS thành semi-join
     * và trả về đơn hàng lặp lại đúng bằng số dòng định mức khớp, tùy kế hoạch tối ưu nó chọn — nên
     * lỗi chỉ lộ ra khi thống kê bảng thay đổi (chạy cả bộ test thì hỏng, chạy riêng lớp test thì
     * không). Thiếu DISTINCT, {@code generate()} gom danh sách này vào Map theo (ycsx, item) sẽ ném
     * lỗi khóa trùng.
     */
    @Query("""
            SELECT DISTINCT so FROM SalesOrder so
            JOIN FETCH so.customer
            JOIN FETCH so.doorProduct
            WHERE so.reqdDeliveryDate <= :cutoffDate
              AND so.approvedPlan IS NULL
              AND
            """ + HAS_COMPLETE_BOM + """
            ORDER BY so.reqdDeliveryDate ASC, so.ycsx ASC, so.item ASC
            """)
    List<SalesOrder> findUnprocessedInScope(@Param("cutoffDate") LocalDate cutoffDate, Pageable pageable);

    /**
     * Phạm vi 1 lần TÍNH phương án cắt: toàn bộ đơn chưa duyệt có định mức đầy đủ để tính nhu cầu
     * cắt, KHÔNG lọc theo ngày giao và KHÔNG giới hạn số đơn (docs/requirements-functional.md Nhóm 3).
     * Bỏ hai giới hạn đó chính là giá trị của chức năng tính: nó trả lời được câu hỏi "với toàn bộ
     * đơn đang có và toàn bộ tồn kho hiện tại, còn thiếu loại thanh nan nào" — thứ mà một đợt duyệt
     * tối đa 70 đơn không bao giờ trả lời được.
     *
     * <p>Điều kiện lọc định mức là cùng hằng {@link #HAS_COMPLETE_BOM} với
     * {@link #findUnprocessedInScope}: đơn không sinh được nhu cầu cắt nào thì đưa vào cũng chỉ ra 0
     * dòng kết quả, còn đơn chỉ tính được một phần định mức thì ra trạng thái đủ/thiếu sai. Số đơn bị
     * loại vì hai lý do này được đếm riêng và cảnh báo trên trang chủ.
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
              AND
            """ + HAS_COMPLETE_BOM + """
            ORDER BY so.reqdDeliveryDate ASC, so.ycsx ASC, so.item ASC
            """)
    List<SalesOrder> findUnapproved();

    /**
     * Gán trạng thái đã duyệt cho toàn bộ đơn trong phạm vi bằng 1 câu UPDATE, thay vì nạp từng
     * entity ra rồi set — phạm vi có thể tới 70 đơn và không có lý do gì để kéo chúng vào
     * persistence context chỉ để đổi 1 khóa ngoại.
     *
     * <p><b>{@code AND so.approvedPlan IS NULL} là chốt chặn cuối cùng chống hai lượt duyệt chồng
     * nhau</b>, không phải điều kiện thừa. Dấu vân trạng thái chỉ là một lần đọc thường nên hai lượt
     * duyệt chạy song song — hoặc đơn giản là một cú nhấp đúp — đều đọc được dấu vân cũ và cùng vượt
     * qua nó. Câu UPDATE này thì khác: nó khóa dòng ở CSDL, nên lượt thứ hai phải chờ lượt thứ nhất
     * kết thúc rồi mới chạy, và khi chạy thì thấy các đơn đã có phương án nên không sửa được dòng
     * nào. Số dòng trả về vì vậy là câu trả lời đáng tin cho câu hỏi "mình có phải người duyệt
     * chúng không"; nơi gọi so số đó với số đơn định duyệt và hủy cả giao dịch nếu lệch.
     *
     * <p>{@code clearAutomatically}/{@code flushAutomatically} là bắt buộc: cùng transaction duyệt
     * có đọc lại SalesOrder (persist CuttingPlanDetailItem/ShortageRecord tham chiếu tới chúng),
     * nếu không xóa cache mức 1 thì các entity đã nạp trước đó vẫn mang approvedPlan cũ. Vì lý do
     * đó, gọi hàm này sau cùng trong transaction — mọi entity nạp trước nó đều trở thành detached.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE SalesOrder so SET so.approvedPlan = :plan WHERE so.id IN :ids AND so.approvedPlan IS NULL")
    int markApproved(@Param("plan") CuttingPlan plan, @Param("ids") List<Long> ids);

    /**
     * Cùng điều kiện "chưa duyệt và trong hạn giao" với {@link #findUnprocessedInScope} nhưng KHÔNG
     * giới hạn 70 đơn — dùng cho KPI tồn đọng ở trang chủ, nơi cần biết số đơn thật đang chờ chứ
     * không phải số đơn lấy được trong 1 lần chạy. Điều kiện định mức dùng chung hằng
     * {@link #HAS_COMPLETE_BOM}; hai điều kiện còn lại sửa ở 1 trong 2 query thì phải sửa cả hai.
     */
    @Query("""
            SELECT COUNT(DISTINCT so) FROM SalesOrder so
            WHERE so.reqdDeliveryDate <= :cutoffDate
              AND so.approvedPlan IS NULL
              AND
            """ + HAS_COMPLETE_BOM)
    long countUnprocessedInScope(@Param("cutoffDate") LocalDate cutoffDate);

    /**
     * Tổng số đơn chưa duyệt trên toàn hệ thống, KHÔNG lọc theo ngày giao và KHÔNG lọc theo định
     * mức — mẫu số để suy ra số đơn đang bị chặn trong phạm vi của chức năng tính, bằng đúng phép
     * trừ đã dùng ở {@link #countUnprocessedInScopeIgnoringBom}: số này trừ đi số dòng
     * {@link #findUnapproved()} trả về.
     */
    @Query("SELECT COUNT(so) FROM SalesOrder so WHERE so.approvedPlan IS NULL")
    long countUnapprovedIgnoringBom();

    /**
     * Dấu vân trạng thái phía đơn hàng: số đơn chưa duyệt trong hạn giao và mốc sửa gần nhất của
     * chúng. Hai con số này đổi khi có đơn được nhập thêm, bị xóa, được duyệt, hay bị sửa — tức
     * mọi đường làm phạm vi duyệt lệch đi so với lúc PLANNER nhìn thấy phương án.
     *
     * <p>Đọc trên cả tập trong hạn giao chứ không chỉ 70 đơn đang hiển thị: một đơn giao gấp vừa
     * được nhập sẽ CHEN vào phạm vi chứ không nằm yên ngoài nó, nên dấu vân chỉ phủ phần đang
     * hiển thị thì bỏ lọt đúng tình huống nguy hiểm nhất.
     *
     * <p>Nhưng cũng KHÔNG phủ rộng hơn hạn giao. Doanh nghiệp nhập đơn hằng ngày và phần lớn đơn
     * nhập vào có ngày giao còn xa; đơn như vậy không cách nào lọt vào đợt duyệt này, nên để chúng
     * làm lệch dấu vân chỉ khiến PLANNER bị từ chối bởi một thay đổi không liên quan — lặp lại
     * nhiều lần thì thao tác duyệt không bao giờ hoàn tất được. Một đơn được sửa ngày giao từ xa
     * về gần vẫn bị bắt, vì khi đó nó bước vào đúng cửa sổ này và làm đổi số đếm.
     *
     * <p>Cố ý KHÔNG lọc theo định mức, khác {@link #countUnprocessedInScope}: thay đổi phía định
     * mức đã có thành phần riêng trong dấu vân, tách ra để mỗi phần đo đúng một thứ.
     */
    @Query("""
            SELECT COUNT(so) AS rowCount, MAX(so.updatedAt) AS lastUpdatedAt
            FROM SalesOrder so
            WHERE so.approvedPlan IS NULL AND so.reqdDeliveryDate <= :cutoffDate
            """)
    TableState readScopeState(@Param("cutoffDate") LocalDate cutoffDate);

    /**
     * Như {@link #countUnprocessedInScope} nhưng BỎ HẲN điều kiện về định mức. Số đơn bị loại vì
     * định mức chưa đầy đủ suy ra bằng phép trừ giữa hai con số, thay vì viết một điều kiện phủ
     * định thứ ba — hai vế khi đó không thể lệch nhau dù quy tắc "định mức đầy đủ" có đổi.
     */
    @Query("""
            SELECT COUNT(DISTINCT so) FROM SalesOrder so
            WHERE so.reqdDeliveryDate <= :cutoffDate
              AND so.approvedPlan IS NULL
            """)
    long countUnprocessedInScopeIgnoringBom(@Param("cutoffDate") LocalDate cutoffDate);
}
