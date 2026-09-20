package com.slatcut.cutting.repository.spec;

import com.slatcut.cutting.domain.SlatGroup;
import com.slatcut.cutting.domain.SlatMaterial;
import org.springframework.data.jpa.domain.Specification;

/** Bộ lọc màn hình danh mục loại thanh nan: tìm theo tên/mã và lọc theo nhóm. */
public final class SlatMaterialSpecifications {

    private SlatMaterialSpecifications() {}

    public static Specification<SlatMaterial> filter(String keyword, SlatGroup slatGroup) {
        return Specs.combine(keywordMatches(keyword), groupIs(slatGroup));
    }

    private static Specification<SlatMaterial> keywordMatches(String keyword) {
        String pattern = Specs.likePattern(keyword);
        if (pattern == null) {
            return null;
        }
        return (root, query, cb) -> cb.or(
                Specs.likeLower(cb, root.get("slatMaterialName"), pattern),
                Specs.likeNumber(cb, root.get("slatMaterial"), pattern));
    }

    private static Specification<SlatMaterial> groupIs(SlatGroup slatGroup) {
        if (slatGroup == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("slatGroup"), slatGroup);
    }
}
