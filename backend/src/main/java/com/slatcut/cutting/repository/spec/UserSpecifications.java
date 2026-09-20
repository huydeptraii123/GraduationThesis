package com.slatcut.cutting.repository.spec;

import com.slatcut.cutting.domain.User;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

/** Bộ lọc màn hình quản lý tài khoản: tìm theo tên đăng nhập, lọc theo vai trò và trạng thái khóa. */
public final class UserSpecifications {

    private UserSpecifications() {}

    public static Specification<User> filter(String keyword, String roleCode, Boolean enabled) {
        return Specs.combine(usernameMatches(keyword), roleIs(roleCode), enabledIs(enabled));
    }

    private static Specification<User> usernameMatches(String keyword) {
        String pattern = Specs.likePattern(keyword);
        if (pattern == null) {
            return null;
        }
        return (root, query, cb) -> Specs.likeLower(cb, root.get("username"), pattern);
    }

    private static Specification<User> roleIs(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.join("role", JoinType.INNER).get("code"), roleCode);
    }

    /** Ba trạng thái: {@code null} là không lọc, {@code true} còn hiệu lực, {@code false} đã khóa. */
    private static Specification<User> enabledIs(Boolean enabled) {
        if (enabled == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("enabled"), enabled);
    }
}
