package com.slatcut.cutting.repository.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Tiện ích dùng chung cho các lớp {@code *Specifications}. */
final class Specs {

    /** Ký tự thoát dùng chung cho mọi mệnh đề LIKE ở đây; phải khớp với ký tự mà {@link #likePattern} chèn. */
    private static final char LIKE_ESCAPE = '!';

    private Specs() {}

    /**
     * Đổi từ khóa người dùng gõ thành mẫu LIKE đã chuẩn hóa chữ thường, hoặc {@code null} nếu từ
     * khóa rỗng. Trả null để tầng gọi bỏ hẳn vị từ thay vì lọc theo {@code '%%'}.
     *
     * <p>Các ký tự đại diện của SQL ({@code %} và {@code _}) được thoát: người dùng gõ "100_" là
     * đang tìm đúng chuỗi đó, không phải "100 + một ký tự bất kỳ". Trước đây việc lọc chạy ở trình
     * duyệt bằng so khớp chuỗi thường nên không có khái niệm ký tự đại diện, và giữ nguyên hành vi
     * đó là cách để kết quả không đổi khi chuyển việc lọc sang CSDL.
     */
    static String likePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escaped = keyword.trim()
                .toLowerCase(Locale.ROOT)
                .replace(String.valueOf(LIKE_ESCAPE), LIKE_ESCAPE + String.valueOf(LIKE_ESCAPE))
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
        return "%" + escaped + "%";
    }

    /** LIKE không phân biệt hoa thường trên một cột chuỗi, kèm ký tự thoát của {@link #likePattern}. */
    static Predicate likeLower(CriteriaBuilder cb, Expression<String> text, String pattern) {
        return cb.like(cb.lower(text), pattern, LIKE_ESCAPE);
    }

    /** LIKE trên một cột số (mã vật tư...) — ép sang chuỗi để tìm được theo chuỗi con của mã. */
    static Predicate likeNumber(CriteriaBuilder cb, Expression<?> number, String pattern) {
        return cb.like(number.as(String.class), pattern, LIKE_ESCAPE);
    }

    /**
     * Ghép các vị từ, bỏ qua phần tử {@code null} (tương ứng bộ lọc người dùng không chọn). Không
     * còn vị từ nào thì trả về {@link Specification#unrestricted()} — nghĩa là lấy tất cả.
     *
     * <p>Tự lọc null thay vì trông vào {@code Specification.allOf(...)}: hợp đồng của {@code allOf}
     * không hứa bỏ qua null, nên để nó tự xử lý là dựa vào chi tiết cài đặt.
     */
    @SafeVarargs
    static <T> Specification<T> combine(Specification<T>... parts) {
        List<Specification<T>> present = Arrays.stream(parts).filter(java.util.Objects::nonNull).toList();
        if (present.isEmpty()) {
            return Specification.unrestricted();
        }
        return Specification.allOf(present);
    }
}
