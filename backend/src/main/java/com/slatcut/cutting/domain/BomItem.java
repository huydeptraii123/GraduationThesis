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
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "bom_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BomItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "door_product_id", nullable = false)
    private DoorProduct doorProduct;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slat_material_id", nullable = false)
    private SlatMaterial slatMaterial;

    @Column(name = "width_offset_m")
    private BigDecimal widthOffsetM;

    @Column(name = "height_offset_m")
    private BigDecimal heightOffsetM;

    @Column(name = "slat_count_slope")
    private BigDecimal slatCountSlope;

    @Column(name = "slat_count_intercept")
    private BigDecimal slatCountIntercept;

    @Column(name = "dinh_muc_tb_m_per_bo_cua")
    private BigDecimal dinhMucTbMPerBoCua;

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
