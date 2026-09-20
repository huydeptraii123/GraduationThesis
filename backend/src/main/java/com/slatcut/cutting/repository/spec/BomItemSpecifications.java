package com.slatcut.cutting.repository.spec;

import com.slatcut.cutting.domain.BomItem;
import com.slatcut.cutting.domain.SlatGroup;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

/** Bộ lọc màn hình định mức BOM: tìm theo tên mẫu cửa/tên vật tư, lọc theo nhóm thanh nan. */
public final class BomItemSpecifications {

    private BomItemSpecifications() {}

    public static Specification<BomItem> filter(String keyword, SlatGroup slatGroup) {
        return Specs.combine(keywordMatches(keyword), groupIs(slatGroup));
    }

    private static Specification<BomItem> keywordMatches(String keyword) {
        String pattern = Specs.likePattern(keyword);
        if (pattern == null) {
            return null;
        }
        return (root, query, cb) -> cb.or(
                Specs.likeLower(cb, root.join("doorProduct", JoinType.INNER).get("doorMaterialName"), pattern),
                Specs.likeLower(cb, root.join("slatMaterial", JoinType.INNER).get("slatMaterialName"), pattern));
    }

    /**
     * Nhóm thanh nan nằm ở bảng vật tư chứ không ở dòng định mức — frontend trước đây tự tra qua
     * danh sách vật tư đã tải sẵn, nay lọc thẳng bằng join để không cần tải cả danh mục.
     */
    private static Specification<BomItem> groupIs(SlatGroup slatGroup) {
        if (slatGroup == null) {
            return null;
        }
        return (root, query, cb) ->
                cb.equal(root.join("slatMaterial", JoinType.INNER).get("slatGroup"), slatGroup);
    }
}
