package com.slatcut.cutting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "door_product")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DoorProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long material;

    private String doorMaterialName;

    @Column(name = "z_mau_sac")
    private String mauSac;

    /**
     * Model cửa (dòng sản phẩm), trục của biểu đồ "số bộ cửa theo model" ở báo cáo tổng quan.
     *
     * <p>Rỗng được: cột này chỉ có trong hồ sơ đơn hàng, trong khi mẫu cửa có thể được tạo ra từ cả
     * luồng nhập định mức — mẫu cửa biết tới qua đường đó sẽ tạm chưa có model cho tới khi một lượt
     * nhập đơn hàng gặp đúng nó.
     */
    @Column(name = "material_group")
    private String materialGroup;

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
