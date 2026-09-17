package com.slatcut.cutting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Header 1 lần chạy CuttingPlanService.generate() — không có updated_at vì chỉ ghi 1 lần trong 1 transaction. */
@Entity
@Table(name = "cutting_plan")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CuttingPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_at")
    private LocalDateTime runAt;

    @Enumerated(EnumType.STRING)
    private CuttingPlanStatus status;

    @Column(name = "total_waste_m")
    private BigDecimal totalWasteM;

    @Column(name = "total_stock_used_m")
    private BigDecimal totalStockUsedM;

    @Column(name = "scope_cutoff_date")
    private LocalDate scopeCutoffDate;

    @Column(name = "scope_order_count")
    private Integer scopeOrderCount;
}
