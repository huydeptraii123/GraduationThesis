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
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ghi nhận 1 SalesOrder thiếu 1 loại SlatMaterial trong 1 lần chạy — căn cứ lập lệnh sản xuất bù. */
@Entity
@Table(name = "shortage_record")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ShortageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cutting_plan_id", nullable = false)
    private CuttingPlan cuttingPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_order_id", nullable = false)
    private SalesOrder salesOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slat_material_id", nullable = false)
    private SlatMaterial slatMaterial;

    @Column(name = "missing_quantity")
    private Integer missingQuantity;

    @Column(name = "missing_length_m")
    private BigDecimal missingLengthM;
}
