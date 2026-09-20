/**
 * Vị từ lọc (JPA Criteria) dùng chung cho các API danh sách có phân trang.
 *
 * <p>Dùng {@code Specification} thay cho {@code @Query} có tham số nullable vì hai lý do: tránh bẫy
 * {@code :param IS NULL} với enum/ngày trên MySQL, và tránh nhân tổ hợp method khi một danh sách có
 * nhiều bộ lọc độc lập (đơn hàng có tới 3). Mọi vị từ trả {@code null} khi tham số rỗng — Spring
 * Data tự bỏ qua vị từ null khi ghép {@code Specification.allOf(...)}.
 */
package com.slatcut.cutting.repository.spec;
