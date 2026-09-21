package com.slatcut.cutting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Một dòng tồn kho tại thời điểm BẮT ĐẦU một lần duyệt: loại thanh nan nào, độ dài nào, còn bao
 * nhiêu thanh ngay trước khi thuật toán tiêu thụ.
 *
 * <p>Đây là bảng duy nhất trong hệ thống lưu dữ liệu chỉ để phục vụ báo cáo chứ không tham gia tính
 * toán, nên lý do tồn tại phải rõ: tồn kho đổi hằng ngày, còn báo cáo đã phát hành xuống xưởng thì
 * phải đọc lại được nguyên trạng. Không có bảng này, mở lại một phương án duyệt từ vài tuần trước
 * sẽ hiển thị tồn kho của <b>hôm nay</b> bên cạnh phương án cắt của <b>hôm đó</b> — hai con số
 * không cùng thời điểm đặt cạnh nhau, đúng kiểu sai lệch khó phát hiện nhất khi đối chiếu chứng từ.
 *
 * <p>Chỉ chụp những loại thanh nan thực sự có mặt trong lần chạy, không chụp cả kho: loại không
 * liên quan thì báo cáo không bao giờ hỏi tới. Chức năng tính phương án cắt không ghi bảng này —
 * nó không ghi gì cả, và bản tính lấy thẳng tồn kho hiện hành vì đang mô tả đúng thời điểm bấm.
 */
@Entity
@Table(name = "cutting_plan_stock_snapshot")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CuttingPlanStockSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cutting_plan_id", nullable = false)
    private CuttingPlan cuttingPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slat_material_id", nullable = false)
    private SlatMaterial slatMaterial;

    @Column(name = "do_dai_thanh_mm")
    private Integer doDaiThanhMm;

    @Column(name = "so_thanh")
    private Integer soThanh;
}
