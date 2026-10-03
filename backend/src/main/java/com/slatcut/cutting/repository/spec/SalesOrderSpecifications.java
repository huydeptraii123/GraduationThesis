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
import jakarta.persistence.criteria.Path;
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
            case PENDING -> cb.and(cb.isNull(root.get("approvedPlan")), hasUsableBom(root, query, cb));
            case BLOCKED -> cb.and(cb.isNull(root.get("approvedPlan")), cb.not(hasUsableBom(root, query, cb)));
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
     * Mẫu cửa của đơn có ít nhất một dòng định mức sinh được nhu cầu cắt.
     *
     * <p>Phản chiếu TỪNG điều kiện của mệnh đề {@code EXISTS} trong
     * {@code SalesOrderRepository.findUnapproved()} — chính là luật mà chức năng tính phương án dùng
     * để đếm số đơn bị chặn — và qua đó phản chiếu các nhánh bỏ qua dòng định mức của
     * {@code CuttingDemandService.buildDemand}: MAIN_SLAT thiếu hệ số tính số nan, RAIL thiếu
     * {@code heightOffsetM}, nhóm OTHER không có công thức cắt. <b>Sửa CuttingDemandService.buildDemand
     * thì phải sửa cả truy vấn phạm vi ở repository lẫn ở đây</b>; SalesOrderServiceTest có test so
     * trạng thái PENDING với đúng kết quả của truy vấn phạm vi để khóa hai nơi khỏi lệch nhau.
     *
     * <p>Viết lại bằng Criteria chứ không gọi được truy vấn JPQL kia: bộ lọc phải ghép được với các
     * điều kiện khác và với phân trang trong cùng một câu truy vấn.
     */
    private static Predicate hasUsableBom(Root<SalesOrder> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
        Subquery<Integer> usable = query.subquery(Integer.class);
        Root<BomItem> bom = usable.from(BomItem.class);
        Join<BomItem, SlatMaterial> material = bom.join("slatMaterial");
        Path<SlatGroup> group = material.get("slatGroup");
        usable.select(cb.literal(1)).where(
                cb.equal(bom.get("doorProduct"), root.get("doorProduct")),
                cb.or(
                        group.in(SlatGroup.SUB_SLAT, SlatGroup.BOTTOM_BAR),
                        cb.and(
                                cb.equal(group, SlatGroup.MAIN_SLAT),
                                cb.isNotNull(bom.get("slatCountSlope")),
                                cb.isNotNull(bom.get("slatCountIntercept"))),
                        cb.and(cb.equal(group, SlatGroup.RAIL), cb.isNotNull(bom.get("heightOffsetM")))));
        return cb.exists(usable);
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
