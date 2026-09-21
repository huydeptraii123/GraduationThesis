package com.slatcut.cutting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 1 hoặc nhiều phôi tồn kho vật lý giống hệt nhau (cùng độ dài nguồn, cùng pattern, cùng tập
 * SalesOrder phân bổ — gộp bằng {@code stickCount}, bất biến này do CuttingPlanService đảm bảo khi
 * persist, không có ràng buộc DB nào enforce được). Không có FK tới InventoryBatch: sourceLengthMm
 * copy trực tiếp tại thời điểm cắt để giữ lịch sử phương án cắt bất biến.
 */
@Entity
@Table(name = "cutting_plan_detail")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CuttingPlanDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cutting_plan_id", nullable = false)
    private CuttingPlan cuttingPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slat_material_id", nullable = false)
    private SlatMaterial slatMaterial;

    @Column(name = "source_length_mm")
    private Integer sourceLengthMm;

    @Column(name = "pattern_code")
    private String patternCode;

    @Column(name = "remainder_mm")
    private Integer remainderMm;

    @Enumerated(EnumType.STRING)
    @Column(name = "remainder_type")
    private RemainderType remainderType;

    @Column(name = "stick_count")
    private Integer stickCount;

    /**
     * Mức ưu tiên mà thuật toán đã dùng cho phôi này. Rỗng với phương án lưu trước khi hệ thống bắt
     * đầu ghi lại mức — không suy đoán ngược cho dữ liệu lịch sử.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "cut_level")
    private CutLevel cutLevel;

    /**
     * Số phôi cùng loại và cùng độ dài còn lại trong kho sau lần chạy.
     *
     * <p>Lưu lại thay vì đọc tồn kho lúc xuất báo cáo: tồn kho đổi hằng ngày, nên đọc lại thì cùng
     * một phương án xuất ra ở hai thời điểm cho hai con số khác nhau, trong khi chứng từ đã phát
     * hành xuống xưởng phải bất biến.
     */
    @Column(name = "remaining_sticks_after")
    private Integer remainingSticksAfter;
}
