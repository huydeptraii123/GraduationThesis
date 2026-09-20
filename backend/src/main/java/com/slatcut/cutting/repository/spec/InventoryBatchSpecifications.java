package com.slatcut.cutting.repository.spec;

import com.slatcut.cutting.domain.InventoryBatch;
import com.slatcut.cutting.domain.SlatGroup;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

/** Bộ lọc màn hình lô tồn kho: tìm theo tên/mã vật tư và lọc theo nhóm thanh nan. */
public final class InventoryBatchSpecifications {

    private InventoryBatchSpecifications() {}

    public static Specification<InventoryBatch> filter(String keyword, SlatGroup slatGroup) {
        return Specs.combine(keywordMatches(keyword), groupIs(slatGroup));
    }

    /** Khớp tên vật tư hoặc mã vật tư — đúng 2 cột mà bảng ở frontend đang tìm kiếm. */
    private static Specification<InventoryBatch> keywordMatches(String keyword) {
        String pattern = Specs.likePattern(keyword);
        if (pattern == null) {
            return null;
        }
        return (root, query, cb) -> {
            Join<InventoryBatch, ?> material = root.join("slatMaterial", JoinType.INNER);
            return cb.or(
                    Specs.likeLower(cb, material.get("slatMaterialName"), pattern),
                    Specs.likeNumber(cb, material.get("slatMaterial"), pattern));
        };
    }

    private static Specification<InventoryBatch> groupIs(SlatGroup slatGroup) {
        if (slatGroup == null) {
            return null;
        }
        return (root, query, cb) ->
                cb.equal(root.join("slatMaterial", JoinType.INNER).get("slatGroup"), slatGroup);
    }
}
