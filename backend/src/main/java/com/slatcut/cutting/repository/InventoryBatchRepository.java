package com.slatcut.cutting.repository;

import com.slatcut.cutting.domain.InventoryBatch;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryBatchRepository
        extends JpaRepository<InventoryBatch, Long>, JpaSpecificationExecutor<InventoryBatch> {

    @Override
    @EntityGraph(attributePaths = "slatMaterial")
    List<InventoryBatch> findAll();

    /**
     * Bản phân trang dùng cho màn hình danh sách. Override lại chỉ để gắn {@code @EntityGraph} —
     * thiếu nó thì mỗi dòng trong trang lại bắn thêm một truy vấn nạp {@code slatMaterial} (N+1),
     * đúng lỗi mà bản {@link #findAll()} không phân trang ở trên đã xử lý.
     */
    @Override
    @EntityGraph(attributePaths = "slatMaterial")
    Page<InventoryBatch> findAll(Specification<InventoryBatch> spec, Pageable pageable);

    Optional<InventoryBatch> findBySlatMaterial_IdAndDoDaiThanhMm(Long slatMaterialId, Integer doDaiThanhMm);

    boolean existsBySlatMaterial_Id(Long slatMaterialId);

    /** Lô còn hàng — lô bị cắt hết vẫn giữ lại dòng với so_thanh = 0 nên phải lọc, không đếm tất cả. */
    long countBySoThanhGreaterThan(int threshold);

    @Query("SELECT COALESCE(SUM(b.soThanh), 0) FROM InventoryBatch b WHERE b.soThanh > 0")
    long sumAvailableSticks();

    /**
     * Tổng hợp toàn kho cho 2 ô thống kê đầu màn hình tồn kho. Phải gộp ở CSDL: từ khi danh sách
     * phân trang, frontend chỉ còn giữ 20 dòng nên không tự cộng ra tổng thật được nữa.
     */
    @Query("""
            SELECT COUNT(b) AS batchCount,
                   COALESCE(SUM(b.soThanh), 0) AS totalSticks,
                   COALESCE(SUM(b.soThanh * b.doDaiThanhMm), 0) AS totalLengthMm
            FROM InventoryBatch b
            """)
    InventoryTotals sumInventoryTotals();

    interface InventoryTotals {
        long getBatchCount();

        long getTotalSticks();

        long getTotalLengthMm();
    }

    /**
     * Dấu vân trạng thái phía tồn kho: số lô, tổng số thanh và mốc sửa gần nhất.
     *
     * <p>Cần cả ba vì mỗi con số bắt một loại thay đổi khác nhau. Tổng số thanh bắt việc nhập/xuất
     * kho — kể cả khi nó đi qua {@link #applyDelta}, vốn là câu UPDATE hàng loạt nên KHÔNG chạm
     * {@code @PreUpdate} và không làm {@code updated_at} nhúc nhích. Ngược lại, mốc sửa bắt những
     * thay đổi mà tổng số thanh không nhìn thấy, ví dụ sửa độ dài của một lô. Số lô bắt việc thêm
     * hoặc xóa dòng.
     *
     * <p>Khác {@link #sumInventoryTotals()} ở mục đích: hàm kia phục vụ 2 ô thống kê trên màn hình
     * và cần tổng độ dài; hàm này phục vụ việc so sánh trạng thái và cần mốc thời gian. Tách riêng
     * để sửa một bên không âm thầm làm lệch bên kia.
     */
    @Query("""
            SELECT COUNT(b) AS rowCount,
                   COALESCE(SUM(b.soThanh), 0) AS totalSticks,
                   MAX(b.updatedAt) AS lastUpdatedAt
            FROM InventoryBatch b
            """)
    InventoryState readInventoryState();

    /** Ba thành phần tồn kho của dấu vân trạng thái — xem {@link #readInventoryState()}. */
    interface InventoryState extends TableState {
        long getTotalSticks();
    }

    /**
     * Cộng delta thẳng ở CSDL thay vì đọc-sửa-ghi trong Java. Trả về số dòng bị ảnh hưởng: 0 nghĩa
     * là lô ở độ dài đó chưa tồn tại.
     *
     * <p><b>Phạm vi bảo vệ:</b> chỉ chống mất mát ở khâu GHI — hai lượt chạy song song sẽ cùng trừ
     * đủ phần của mình thay vì lượt sau ghi đè lượt trước. KHÔNG bảo vệ khâu ĐỌC: mỗi lượt vẫn lập
     * kế hoạch trên ảnh chụp tồn kho lấy lúc bắt đầu, nên hai lượt cùng nhìn thấy một thanh và cùng
     * tiêu nó thì tổng trừ sẽ vượt quá số thanh có thật (so_thanh có thể xuống âm). Chặn triệt để
     * cần khóa lạc quan hoặc khóa dòng khi đọc; ở phạm vi hiện tại hệ thống chỉ có một PLANNER thao
     * tác tuần tự nên rủi ro đó chưa xảy ra, và đây là giới hạn đã biết chứ không phải đã xử lý.
     *
     * <p>{@code flushAutomatically} đẩy các thay đổi đang chờ xuống CSDL trước khi chạy, còn
     * {@code clearAutomatically} xoá persistence context sau đó — bắt buộc phải có cả hai, vì query
     * {@code @Modifying} không đi qua persistence context nên thiếu chúng thì đoạn code đọc lại
     * {@code soThanh} ngay sau đó trong cùng transaction sẽ thấy giá trị cũ.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE InventoryBatch b SET b.soThanh = b.soThanh + :delta
            WHERE b.slatMaterial.id = :slatMaterialId AND b.doDaiThanhMm = :lengthMm
            """)
    int applyDelta(
            @Param("slatMaterialId") Long slatMaterialId,
            @Param("lengthMm") Integer lengthMm,
            @Param("delta") int delta);
}
