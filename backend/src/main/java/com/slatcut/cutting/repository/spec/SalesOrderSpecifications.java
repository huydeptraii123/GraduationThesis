package com.slatcut.cutting.repository.spec;

import com.slatcut.cutting.domain.BomItem;
import com.slatcut.cutting.domain.SalesOrder;
import com.slatcut.cutting.domain.SalesOrderProcessingStatus;
import com.slatcut.cutting.domain.ShortageRecord;
import com.slatcut.cutting.domain.SlatGroup;
import com.slatcut.cutting.domain.SlatMaterial;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.util.Collection;
import org.springframework.data.jpa.domain.Specification;

/**
 * Bộ lọc màn hình đơn hàng: tìm theo lệnh sản xuất/khách hàng, lọc theo khách, khoảng ngày giao và
 * trạng thái xử lý.
 */
public final class SalesOrderSpecifications {

    private SalesOrderSpecifications() {}

    public static Specification<SalesOrder> filter(
            String keyword,
            Long customerId,
            LocalDate deliveryFrom,
            LocalDate deliveryTo,
            SalesOrderProcessingStatus status) {
        return Specs.combine(
                keywordMatches(keyword),
                customerIs(customerId),
                deliveryFrom(deliveryFrom),
                deliveryTo(deliveryTo),
                statusIs(status));
    }

    /**
     * Đơn đang ở đúng trạng thái xử lý {@code status}, hoặc {@code null} (không lọc) khi
     * {@code status} rỗng. Đây là định nghĩa DUY NHẤT của bốn trạng thái: bộ lọc dùng nó trực tiếp,
     * còn cột trạng thái trên từng dòng cũng hỏi lại chính nó (SalesOrderService.resolveStatuses),
     * nên cột hiển thị và bộ lọc không thể nói hai điều khác nhau.
     *
     * <p>SUFFICIENT viết là "đã duyệt và không thiếu", không phải "có ít nhất một lát cắt và không
     * thiếu" như công thức trong docs/domain-model.md. Hai cách cho cùng kết quả, vì bước duyệt
     * không đánh dấu đơn nào không để lại kết quả (CuttingPlanService.markScopeApproved), nên không
     * có đơn "đã duyệt mà rỗng". Viết theo cách này để bốn trạng thái luôn phủ kín mọi đơn, kể cả
     * khi bất biến đó vỡ.
     *
     * <p>Giới hạn đã biết: BLOCKED chỉ xét định mức có ĐỦ tham số hay không, đúng như luật đếm đơn
     * bị chặn ở chức năng tính phương án. Nó không xét giá trị tính ra. Một mẫu cửa có hệ số cho ra 0
     * nan sẽ hiện "Chưa xử lý" dù mỗi lượt duyệt đều để nó lại hàng chờ; trường hợp đó do log cảnh
     * báo của bước duyệt chỉ ra.
     */
    public static Specification<SalesOrder> statusIs(SalesOrderProcessingStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> switch (status) {
            case PENDING -> cb.and(cb.isNull(root.get("approvedPlan")), hasCompleteBom(root, query, cb));
            case BLOCKED -> cb.and(cb.isNull(root.get("approvedPlan")), cb.not(hasCompleteBom(root, query, cb)));
            case SUFFICIENT -> cb.and(
                    cb.isNotNull(root.get("approvedPlan")), cb.not(hasShortageInApprovedPlan(root, query, cb)));
            case SHORTAGE -> cb.and(
                    cb.isNotNull(root.get("approvedPlan")), hasShortageInApprovedPlan(root, query, cb));
        };
    }

    /** Thu hẹp về đúng các đơn có id trong danh sách — để hỏi trạng thái cho một trang đã tải. */
    public static Specification<SalesOrder> idIn(Collection<Long> ids) {
        return (root, query, cb) -> root.get("id").in(ids);
    }

    /**
     * Mẫu cửa của đơn có định mức ĐẦY ĐỦ để tính nhu cầu cắt: có ít nhất một dòng ngoài nhóm OTHER,
     * không dòng RAIL nào thiếu {@code heightOffsetM}, và hoặc không có dòng MAIN_SLAT nào, hoặc có
     * ít nhất một dòng MAIN_SLAT đủ cả hai hệ số tính số nan (dòng nan chính thiếu hệ số bên cạnh nó
     * là profile phụ, bị bỏ riêng khi tính nhu cầu).
     *
     * <p>Bản Criteria của hằng {@code SalesOrderRepository.HAS_COMPLETE_BOM} — chính là luật mà hai
     * chức năng tính và duyệt phương án dùng để loại đơn khỏi phạm vi xử lý — và qua đó phản chiếu
     * các nhánh bỏ qua bộ cửa của {@code CuttingDemandService.buildDemands}. <b>Sửa
     * CuttingDemandService.buildDemands thì phải sửa cả hằng ở repository lẫn ở đây</b>;
     * SalesOrderServiceTest có test so trạng thái PENDING với đúng kết quả của truy vấn phạm vi để
     * khóa các nơi khỏi lệch nhau.
     *
     * <p>Viết lại bằng Criteria chứ không gọi được truy vấn JPQL kia: bộ lọc phải ghép được với các
     * điều kiện khác và với phân trang trong cùng một câu truy vấn.
     */
    private static Predicate hasCompleteBom(Root<SalesOrder> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<Integer> cuttable = query.subquery(Integer.class);
        Root<BomItem> bom = cuttable.from(BomItem.class);
        Join<BomItem, SlatMaterial> material = bom.join("slatMaterial");
        cuttable.select(cb.literal(1)).where(
                cb.equal(bom.get("doorProduct"), root.get("doorProduct")),
                cb.notEqual(material.get("slatGroup"), SlatGroup.OTHER));

        Subquery<Integer> brokenRail = query.subquery(Integer.class);
        Root<BomItem> railBom = brokenRail.from(BomItem.class);
        brokenRail.select(cb.literal(1)).where(
                cb.equal(railBom.get("doorProduct"), root.get("doorProduct")),
                cb.equal(railBom.join("slatMaterial").get("slatGroup"), SlatGroup.RAIL),
                cb.isNull(railBom.get("heightOffsetM")));

        Subquery<Integer> anyMain = query.subquery(Integer.class);
        Root<BomItem> mainBom = anyMain.from(BomItem.class);
        anyMain.select(cb.literal(1)).where(
                cb.equal(mainBom.get("doorProduct"), root.get("doorProduct")),
                cb.equal(mainBom.join("slatMaterial").get("slatGroup"), SlatGroup.MAIN_SLAT));

        Subquery<Integer> completeMain = query.subquery(Integer.class);
        Root<BomItem> completeBom = completeMain.from(BomItem.class);
        completeMain.select(cb.literal(1)).where(
                cb.equal(completeBom.get("doorProduct"), root.get("doorProduct")),
                cb.equal(completeBom.join("slatMaterial").get("slatGroup"), SlatGroup.MAIN_SLAT),
                cb.isNotNull(completeBom.get("slatCountSlope")),
                cb.isNotNull(completeBom.get("slatCountIntercept")));

        return cb.and(
                cb.exists(cuttable),
                cb.not(cb.exists(brokenRail)),
                cb.or(cb.not(cb.exists(anyMain)), cb.exists(completeMain)));
    }

    /**
     * Phương án ĐÃ DUYỆT đơn này ghi nhận đơn thiếu ít nhất một loại thanh. So cả phương án chứ
     * không chỉ đơn: trạng thái đủ/thiếu là kết quả của đúng lần duyệt đã chốt đơn, không phải của
     * bất kỳ bản ghi thiếu nào từng gắn với nó.
     */
    private static Predicate hasShortageInApprovedPlan(
            Root<SalesOrder> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<Integer> shortage = query.subquery(Integer.class);
        Root<ShortageRecord> record = shortage.from(ShortageRecord.class);
        shortage.select(cb.literal(1)).where(
                cb.equal(record.get("salesOrder"), root),
                cb.equal(record.get("cuttingPlan"), root.get("approvedPlan")));
        return cb.exists(shortage);
    }

    private static Specification<SalesOrder> keywordMatches(String keyword) {
        String pattern = Specs.likePattern(keyword);
        if (pattern == null) {
            return null;
        }
        return (root, query, cb) -> cb.or(
                Specs.likeLower(cb, root.get("ycsx"), pattern),
                Specs.likeLower(cb, root.join("customer", JoinType.INNER).get("customerName"), pattern));
    }

    private static Specification<SalesOrder> customerIs(Long customerId) {
        if (customerId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.join("customer", JoinType.INNER).get("id"), customerId);
    }

    /** Hai đầu khoảng ngày tách rời nhau để người dùng chọn được một phía mà vẫn lọc đúng. */
    private static Specification<SalesOrder> deliveryFrom(LocalDate from) {
        if (from == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("reqdDeliveryDate"), from);
    }

    private static Specification<SalesOrder> deliveryTo(LocalDate to) {
        if (to == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("reqdDeliveryDate"), to);
    }
}
