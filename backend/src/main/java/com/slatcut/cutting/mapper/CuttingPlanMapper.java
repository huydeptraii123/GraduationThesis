package com.slatcut.cutting.mapper;

import com.slatcut.cutting.domain.CuttingPlan;
import com.slatcut.cutting.domain.CuttingPlanDetail;
import com.slatcut.cutting.domain.CuttingPlanDetailItem;
import com.slatcut.cutting.domain.ShortageRecord;
import com.slatcut.cutting.dto.CuttingPlanDetailItemResponse;
import com.slatcut.cutting.dto.CuttingPlanDetailResponse;
import com.slatcut.cutting.dto.CuttingPlanResponse;
import com.slatcut.cutting.dto.CuttingPlanSummaryResponse;
import com.slatcut.cutting.dto.ShortageRecordResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Việc gom CuttingPlanDetailItem theo detailId (chống N+1 khi tra ngược nhiều bảng con) là logic
 * truy vấn, nằm ở CuttingPlanService — mapper chỉ ráp lại cây kết quả qua các tham số nguồn phụ
 * (details/shortages/items) đã map sẵn, dùng đúng khả năng multi-source parameter chuẩn của MapStruct.
 */
@Mapper(componentModel = "spring")
public interface CuttingPlanMapper {

    CuttingPlanSummaryResponse toSummaryResponse(CuttingPlan entity);

    @Mapping(source = "salesOrder.id", target = "salesOrderId")
    @Mapping(source = "salesOrder.ycsx", target = "ycsx")
    @Mapping(source = "salesOrder.item", target = "item")
    @Mapping(source = "salesOrder.customer.customerName", target = "customerName")
    @Mapping(source = "salesOrder.doorProduct.id", target = "doorProductId")
    @Mapping(source = "salesOrder.doorProduct.doorMaterialName", target = "doorProductName")
    @Mapping(source = "salesOrder.reqdDeliveryDate", target = "reqdDeliveryDate")
    @Mapping(source = "salesOrder.chieuCaoDh", target = "chieuCaoDh")
    @Mapping(source = "salesOrder.chieuRongDh", target = "chieuRongDh")
    CuttingPlanDetailItemResponse toItemResponse(CuttingPlanDetailItem entity);

    @Mapping(source = "slatMaterial.id", target = "slatMaterialId")
    @Mapping(source = "slatMaterial.slatMaterialName", target = "slatMaterialName")
    @Mapping(source = "salesOrder.id", target = "salesOrderId")
    @Mapping(source = "salesOrder.ycsx", target = "ycsx")
    @Mapping(source = "salesOrder.item", target = "item")
    @Mapping(source = "salesOrder.customer.customerName", target = "customerName")
    @Mapping(source = "salesOrder.doorProduct.id", target = "doorProductId")
    @Mapping(source = "salesOrder.doorProduct.doorMaterialName", target = "doorProductName")
    @Mapping(source = "salesOrder.reqdDeliveryDate", target = "reqdDeliveryDate")
    @Mapping(source = "salesOrder.chieuCaoDh", target = "chieuCaoDh")
    @Mapping(source = "salesOrder.chieuRongDh", target = "chieuRongDh")
    ShortageRecordResponse toShortageResponse(ShortageRecord entity);

    @Mapping(source = "detail.id", target = "id")
    @Mapping(source = "detail.slatMaterial.id", target = "slatMaterialId")
    @Mapping(source = "detail.slatMaterial.slatMaterialName", target = "slatMaterialName")
    @Mapping(source = "detail.sourceLengthMm", target = "sourceLengthMm")
    @Mapping(source = "detail.patternCode", target = "patternCode")
    @Mapping(source = "detail.remainderMm", target = "remainderMm")
    @Mapping(source = "detail.remainderType", target = "remainderType")
    @Mapping(source = "detail.stickCount", target = "stickCount")
    CuttingPlanDetailResponse toDetailResponse(CuttingPlanDetail detail, List<CuttingPlanDetailItemResponse> items);

    @Mapping(source = "plan.id", target = "id")
    @Mapping(source = "plan.runAt", target = "runAt")
    @Mapping(source = "plan.status", target = "status")
    @Mapping(source = "plan.totalWasteM", target = "totalWasteM")
    @Mapping(source = "plan.totalStockUsedM", target = "totalStockUsedM")
    @Mapping(source = "plan.scopeCutoffDate", target = "scopeCutoffDate")
    @Mapping(source = "plan.scopeOrderCount", target = "scopeOrderCount")
    CuttingPlanResponse toResponse(
            CuttingPlan plan, List<CuttingPlanDetailResponse> details, List<ShortageRecordResponse> shortages);
}
