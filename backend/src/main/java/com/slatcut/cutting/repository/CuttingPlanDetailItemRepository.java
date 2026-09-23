package com.slatcut.cutting.repository;

import com.slatcut.cutting.domain.CuttingPlanDetailItem;
import com.slatcut.cutting.domain.RemainderType;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CuttingPlanDetailItemRepository extends JpaRepository<CuttingPlanDetailItem, Long> {


    @EntityGraph(attributePaths = {"salesOrder.customer", "salesOrder.doorProduct"})
    List<CuttingPlanDetailItem> findByCuttingPlanDetail_IdIn(List<Long> cuttingPlanDetailIds);

    /**
     * Nguyên liệu thô để quy phế về từng bộ cửa: mỗi dòng là một đoạn cắt, kèm các số liệu của
     * chính cái phôi chứa nó.
     *
     * <p>Không gộp sẵn ở tầng CSDL vì phép quy phế là một phép <b>chia theo tỷ lệ</b>: một phôi có
     * thể bị hai bộ cửa dùng chung (Mức 3 ghép hai đoạn của hai đơn khác nhau vào một thanh), nên
     * phần dư của phôi đó phải chia cho các bộ cửa theo đúng tỷ lệ độ dài mỗi bên đã cắt. Mẫu số
     * của phép chia là tổng độ dài cắt của cả phôi, tức phải biết hết các đoạn anh em rồi mới tính
     * được — việc đó làm ở {@code DashboardService}, nơi nhìn thấy trọn bộ dòng.
     *
     * <p>{@code cutQuantity} đã là tổng số đoạn trên <b>toàn bộ</b> {@code stickCount} phôi của
     * dòng chứ không phải trên mỗi phôi (xem cách gộp ở {@code CuttingResultGrouping}), nên
     * {@code cutLengthMm * cutQuantity} so sánh được trực tiếp với {@code stickCount * remainderMm}
     * mà không phải nhân thêm lần nữa.
     *
     * <p>{@code ORDER BY} là bắt buộc chứ không phải cho đẹp: thiếu nó thì thứ tự dòng do cơ sở dữ
     * liệu tự quyết và có thể đổi theo kế hoạch truy vấn, mà thứ tự dòng chính là thứ tự bộ cửa
     * được chèn vào bản đồ gộp ở {@code DashboardService}. Sắp theo {@code d.id} cho ra đúng thứ tự
     * các dòng phôi đã được ghi, tức thứ tự xử lý của thuật toán — một mốc cố định để phép sắp xếp
     * theo ngày giao ở tầng trên có cái mà sắp lại.
     */
    @Query(
            """
            SELECT d.id AS detailId,
                   o.id AS salesOrderId,
                   o.ycsx AS ycsx,
                   o.item AS item,
                   o.reqdDeliveryDate AS reqdDeliveryDate,
                   i.cutLengthMm * i.cutQuantity AS orderCutMm,
                   d.stickCount AS stickCount,
                   d.remainderMm AS remainderMm,
                   d.remainderType AS remainderType,
                   d.sourceLengthMm AS sourceLengthMm
            FROM CuttingPlanDetailItem i
            JOIN i.cuttingPlanDetail d
            JOIN i.salesOrder o
            ORDER BY d.id, i.id
            """)
    List<OrderCutShare> findOrderCutShares();

    interface OrderCutShare {
        Long getDetailId();

        Long getSalesOrderId();

        String getYcsx();

        Integer getItem();

        LocalDate getReqdDeliveryDate();

        Long getOrderCutMm();

        Integer getStickCount();

        Integer getRemainderMm();

        RemainderType getRemainderType();

        Integer getSourceLengthMm();
    }
}
