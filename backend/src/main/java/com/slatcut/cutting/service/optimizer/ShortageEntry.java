package com.slatcut.cutting.service.optimizer;

import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.service.CuttingDemand;

/** 1 đơn vị CuttingDemand không còn thanh tồn kho nào đủ dài để cắt. */
public record ShortageEntry(SlatMaterial slatMaterial, CuttingDemand demand) {}
