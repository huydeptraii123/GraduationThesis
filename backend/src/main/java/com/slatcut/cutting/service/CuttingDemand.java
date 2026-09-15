package com.slatcut.cutting.service;

import com.slatcut.cutting.domain.SlatMaterial;
import java.time.LocalDate;

/** Nhu cầu cắt cho 1 loại thanh nan của 1 đơn hàng — không phải entity, chỉ tính lại mỗi lần chạy thuật toán. */
public record CuttingDemand(
        SlatMaterial slatMaterial, int cutLengthMm, int quantity, LocalDate reqdDeliveryDate, String ycsx, Integer item) {}
