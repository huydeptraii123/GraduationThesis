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
 * Bảng nối N-N giữa CuttingPlanDetail (1 phôi) và SalesOrder (1 bộ cửa) — 1 phôi có thể phục vụ
 * nhiều SalesOrder khi thuật toán áp dụng Mức 2/3. isOriginalOrder phân biệt đơn "gốc" (đoạn X đang
 * xét) với đơn "ghép thêm" dùng chung phôi, phục vụ báo cáo mức chi tiết nhất.
 */
@Entity
@Table(name = "cutting_plan_detail_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CuttingPlanDetailItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cutting_plan_detail_id", nullable = false)
    private CuttingPlanDetail cuttingPlanDetail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_order_id", nullable = false)
    private SalesOrder salesOrder;

    @Column(name = "cut_length_mm")
    private Integer cutLengthMm;

    @Column(name = "cut_quantity")
    private Integer cutQuantity;

    @Column(name = "is_original_order")
    private boolean originalOrder;
}
