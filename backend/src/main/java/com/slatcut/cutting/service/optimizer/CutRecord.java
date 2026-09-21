package com.slatcut.cutting.service.optimizer;

import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.service.CuttingDemand;
import java.util.List;

/**
 * 1 thanh tồn kho đã cắt cho 1 hoặc nhiều đơn vị CuttingDemand — {@code pieces} có nhiều hơn 1 phần
 * tử khi Mức 2 (bội số) hoặc Mức 3 (ghép 2 đoạn) ghép chung nhiều đơn vào cùng 1 thanh (phần tử đầu
 * luôn là đơn "gốc" X đang xét, đúng bản chất "đơn gốc + đơn ghép thêm" ở docs/requirements-functional.md).
 */
public record CutRecord(
        SlatMaterial slatMaterial,
        int stockLengthMm,
        List<CuttingDemand> pieces,
        int remainderMm,
        RemainderCategory remainderCategory,
        CutLevel cutLevel) {}
