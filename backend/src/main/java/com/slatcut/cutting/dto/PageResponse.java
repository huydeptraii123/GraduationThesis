package com.slatcut.cutting.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Bao đóng một trang kết quả cho mọi API danh sách.
 *
 * <p>Cố ý KHÔNG trả thẳng {@link Page}/{@code PageImpl} của Spring ra ngoài: cấu trúc JSON của
 * {@code PageImpl} do Jackson tự suy ra từ getter nên đổi theo phiên bản Spring Data (Boot còn ghi
 * cảnh báo khi controller trả về nó), trong khi frontend phụ thuộc vào đúng các trường dưới đây.
 *
 * @param page số hiệu trang, đếm từ 0 — frontend dùng AntD đếm từ 1 nên phải tự quy đổi
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    /** Đổi một trang entity sang trang DTO bằng hàm map tương ứng. */
    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
