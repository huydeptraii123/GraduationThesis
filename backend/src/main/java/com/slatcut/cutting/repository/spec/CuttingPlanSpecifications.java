package com.slatcut.cutting.repository.spec;

import com.slatcut.cutting.domain.CuttingPlan;
import com.slatcut.cutting.domain.CuttingPlanStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.data.jpa.domain.Specification;

/** Bộ lọc màn hình lịch sử phương án cắt: theo mã lần chạy, trạng thái và khoảng thời gian chạy. */
public final class CuttingPlanSpecifications {

    private CuttingPlanSpecifications() {}

    public static Specification<CuttingPlan> filter(
            Long planId, CuttingPlanStatus status, LocalDate runFrom, LocalDate runTo) {
        return Specs.combine(planIs(planId), statusIs(status), runFrom(runFrom), runTo(runTo));
    }

    private static Specification<CuttingPlan> planIs(Long planId) {
        if (planId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("id"), planId);
    }

    private static Specification<CuttingPlan> statusIs(CuttingPlanStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /** {@code runAt} là mốc thời gian nên phải so từ 00:00 của ngày bắt đầu. */
    private static Specification<CuttingPlan> runFrom(LocalDate from) {
        if (from == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("runAt"), from.atStartOfDay());
    }

    /** Và tới 23:59:59.999999999 của ngày kết thúc — lấy {@code atStartOfDay} sẽ cắt mất cả ngày cuối. */
    private static Specification<CuttingPlan> runTo(LocalDate to) {
        if (to == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("runAt"), to.atTime(LocalTime.MAX));
    }
}
