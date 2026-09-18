package com.slatcut.cutting.repository;

import com.slatcut.cutting.domain.InventoryBatch;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, Long> {

    @Override
    @EntityGraph(attributePaths = "slatMaterial")
    List<InventoryBatch> findAll();

    Optional<InventoryBatch> findBySlatMaterial_IdAndDoDaiThanhMm(Long slatMaterialId, Integer doDaiThanhMm);

    boolean existsBySlatMaterial_Id(Long slatMaterialId);

    /** Lô còn hàng — lô bị cắt hết vẫn giữ lại dòng với so_thanh = 0 nên phải lọc, không đếm tất cả. */
    long countBySoThanhGreaterThan(int threshold);

    @Query("SELECT COALESCE(SUM(b.soThanh), 0) FROM InventoryBatch b WHERE b.soThanh > 0")
    long sumAvailableSticks();

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
