package com.slatcut.cutting.service.optimizer;

import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.service.CuttingDemand;

/** 1 thanh tồn kho đã cắt cho đúng 1 đơn vị CuttingDemand (không phải cả batch nhiều đơn vị). */
public record CutRecord(
        SlatMaterial slatMaterial,
        int stockLengthMm,
        CuttingDemand demand,
        int remainderMm,
        RemainderCategory remainderCategory) {}
