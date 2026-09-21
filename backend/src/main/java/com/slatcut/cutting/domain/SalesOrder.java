package com.slatcut.cutting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sales_order")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SalesOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String ycsx;

    @Column(name = "z_item")
    private Integer item;

    private Long salesDocument;

    private Integer salesOrderItem;

    /**
     * Lệnh sản xuất của bộ cửa. Chỉ để in ra báo cáo cho khớp chứng từ xưởng đang dùng — không tham
     * gia khóa nghiệp vụ, không tham gia thứ tự ưu tiên, không ảnh hưởng tới cách cắt.
     *
     * <p>Khác {@code ycsx} (lô sản xuất gộp nhiều bộ cửa) và khác hẳn "lệnh sản xuất thanh nan" nói
     * tới ở báo cáo thiếu vật tư. Cột nguồn tên là {@code order}, trùng từ khóa dự trữ của MySQL
     * nên đổi tên khi ánh xạ.
     */
    @Column(name = "lenh_sx")
    private Long lenhSx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "door_product_id", nullable = false)
    private DoorProduct doorProduct;

    /**
     * Phương án cắt đã duyệt đơn này; null khi đơn còn trong hàng chờ.
     *
     * <p>Đây là trạng thái "đã duyệt" tường minh, ghi đúng một lần tại bước duyệt phương án cắt và
     * trong cùng transaction với {@link CuttingPlan} tương ứng — không phải cột đệm được cập nhật
     * rời rạc sau sự kiện, nên không có trạng thái trung gian nào để lệch.
     *
     * <p>Không suy ra từ việc đơn đã có CuttingPlanDetailItem/ShortageRecord hay chưa như thiết kế
     * trước: chức năng "tính phương án cắt" chạy trọn thuật toán nhưng không ghi gì, nên sự tồn tại
     * của bản ghi kết quả không còn phân biệt được "đã chốt" với "mới tính thử".
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_plan_id")
    private CuttingPlan approvedPlan;

    @Column(name = "z_chieu_cao_dh")
    private BigDecimal chieuCaoDh;

    @Column(name = "z_chieu_rong_dh")
    private BigDecimal chieuRongDh;

    @Column(name = "reqd_delivery_date")
    private LocalDate reqdDeliveryDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
