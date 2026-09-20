package com.slatcut.cutting.repository;

import com.slatcut.cutting.domain.User;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    /** {@code role} là LAZY nên màn danh sách sẽ bắn thêm 1 query mỗi dòng nếu không nạp sẵn ở đây. */
    @Override
    @EntityGraph(attributePaths = "role")
    List<User> findAll();

    /** Bản phân trang cho màn hình danh sách; {@code @EntityGraph} giữ nguyên chống N+1 như trên. */
    @Override
    @EntityGraph(attributePaths = "role")
    Page<User> findAll(Specification<User> spec, Pageable pageable);

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    /**
     * Các tài khoản đang hoạt động của một vai trò — dùng để chặn việc khóa hoặc hạ quyền ADMIN
     * cuối cùng, tình huống khiến hệ thống không còn ai quản lý được định mức BOM lẫn tài khoản.
     *
     * <p>{@code PESSIMISTIC_WRITE} (tức {@code SELECT ... FOR UPDATE}) là phần không thể bỏ. Đếm
     * bằng câu lệnh thường thì hai ADMIN khóa chéo nhau cùng lúc sẽ chạm vào hai dòng khác nhau,
     * nên không xung đột: cả hai cùng đọc thấy "còn 2 ADMIN", cả hai cùng được đi tiếp, và hệ thống
     * kết thúc với 0 ADMIN — trạng thái không có đường quay lại, vì tạo tài khoản và đặt lại mật
     * khẩu đều đòi quyền ADMIN. Khóa dòng buộc hai lượt xếp hàng, lượt sau nhìn thấy kết quả của
     * lượt trước.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<User> findByRole_CodeAndEnabledTrue(String roleCode);
}
