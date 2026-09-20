package com.slatcut.cutting.repository;

import com.slatcut.cutting.domain.BomItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface BomItemRepository extends JpaRepository<BomItem, Long>, JpaSpecificationExecutor<BomItem> {

    @Override
    @EntityGraph(attributePaths = {"doorProduct", "slatMaterial"})
    List<BomItem> findAll();

    /** Bản phân trang cho màn hình danh sách; {@code @EntityGraph} giữ nguyên chống N+1 như trên. */
    @Override
    @EntityGraph(attributePaths = {"doorProduct", "slatMaterial"})
    Page<BomItem> findAll(Specification<BomItem> spec, Pageable pageable);

    Optional<BomItem> findByDoorProduct_IdAndSlatMaterial_Id(Long doorProductId, Long slatMaterialId);

    @EntityGraph(attributePaths = {"slatMaterial"})
    List<BomItem> findByDoorProduct_IdIn(Collection<Long> doorProductIds);

    boolean existsByDoorProduct_Id(Long doorProductId);

    boolean existsBySlatMaterial_Id(Long slatMaterialId);

    /**
     * Tổng hợp toàn bộ định mức cho 3 ô thống kê đầu màn hình BOM. Phải gộp ở CSDL: từ khi danh
     * sách phân trang, frontend chỉ còn 20 dòng nên không đếm được số mẫu cửa hay số nhóm vật tư
     * thật sự đang có định mức.
     */
    @Query("""
            SELECT COUNT(b) AS totalItems,
                   COUNT(DISTINCT b.slatMaterial.slatGroup) AS groupCount,
                   COUNT(DISTINCT b.doorProduct.id) AS doorProductCount
            FROM BomItem b
            """)
    BomTotals sumBomTotals();

    /**
     * Dấu vân trạng thái phía định mức. Định mức là đầu vào của thuật toán ngang hàng với đơn hàng
     * và tồn kho: thêm một dòng định mức cho mẫu cửa đang bị chặn sẽ kéo cả đơn đó vào phạm vi, còn
     * sửa hệ số tính số nan làm đổi luôn độ dài các đoạn phải cắt. Thiếu thành phần này thì một
     * thay đổi định mức xảy ra trong lúc PLANNER xem xét sẽ khiến phương án ghi xuống khác hẳn
     * phương án vừa được duyệt, mà dấu vân vẫn báo khớp.
     */
    @Query("SELECT COUNT(b) AS rowCount, MAX(b.updatedAt) AS lastUpdatedAt FROM BomItem b")
    TableState readState();

    interface BomTotals {
        long getTotalItems();

        long getGroupCount();

        long getDoorProductCount();
    }
}
