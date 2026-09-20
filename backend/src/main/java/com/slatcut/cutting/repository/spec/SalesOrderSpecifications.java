package com.slatcut.cutting.repository.spec;

import com.slatcut.cutting.domain.SalesOrder;
import jakarta.persistence.criteria.JoinType;
import java.time.LocalDate;
import org.springframework.data.jpa.domain.Specification;

/** Bộ lọc màn hình đơn hàng: tìm theo lệnh sản xuất/khách hàng, lọc theo khách và khoảng ngày giao. */
public final class SalesOrderSpecifications {

    private SalesOrderSpecifications() {}

    public static Specification<SalesOrder> filter(
            String keyword, Long customerId, LocalDate deliveryFrom, LocalDate deliveryTo) {
        return Specs.combine(
                keywordMatches(keyword),
                customerIs(customerId),
                deliveryFrom(deliveryFrom),
                deliveryTo(deliveryTo));
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
