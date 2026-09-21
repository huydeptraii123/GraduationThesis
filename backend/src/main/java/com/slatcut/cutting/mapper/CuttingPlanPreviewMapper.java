package com.slatcut.cutting.mapper;

import com.slatcut.cutting.domain.RemainderType;
import com.slatcut.cutting.domain.SalesOrder;
import com.slatcut.cutting.dto.CuttingPlanApprovalPreviewResponse;
import com.slatcut.cutting.dto.CuttingPlanDemandView;
import com.slatcut.cutting.dto.CuttingPlanDetailItemResponse;
import com.slatcut.cutting.dto.CuttingPlanDetailResponse;
import com.slatcut.cutting.dto.CuttingPlanPreviewResponse;
import com.slatcut.cutting.dto.ShortageRecordResponse;
import com.slatcut.cutting.service.CuttingPlanApprovalPreview;
import com.slatcut.cutting.service.CuttingPlanPreview;
import com.slatcut.cutting.service.CuttingResultGrouping;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Chuyển một phương án <b>chưa được lưu</b> sang đúng những DTO mà màn hình phương án cắt đang dùng
 * cho phương án đã lưu. Nhờ vậy màn hình duyệt tái sử dụng nguyên các thành phần hiển thị sẵn có
 * thay vì dựng một bộ song song chỉ khác ở chỗ dữ liệu chưa có id.
 *
 * <p>Viết tay thay vì khai báo MapStruct như {@link CuttingPlanMapper}: nguồn ở đây không phải
 * entity mà là kết quả gộp trong bộ nhớ, và mỗi dòng còn phải ghép thêm thông tin đơn hàng tra từ
 * danh sách đơn trong phạm vi — MapStruct không rút ngắn được phần đó.
 *
 * <p>Mọi trường {@code id} để rỗng. Đó là điều đúng đắn chứ không phải thiếu sót: chưa có bản ghi
 * nào tồn tại, nên một id bịa ra sẽ là thứ giao diện có thể vô tình đem đi tra cứu.
 */
@Component
public class CuttingPlanPreviewMapper {

    public CuttingPlanPreviewResponse toResponse(CuttingPlanPreview preview, List<CuttingPlanDemandView> demands) {
        return new CuttingPlanPreviewResponse(
                preview.computedAt(),
                preview.scopeOrders().size(),
                preview.blockedOrderCount(),
                preview.totalWasteM(),
                preview.totalStockUsedM(),
                demands);
    }

    /**
     * Phần {@code details}/{@code shortages} được gộp bằng đúng lớp mà bước duyệt dùng để ghi
     * xuống cơ sở dữ liệu, nên phương án PLANNER nhìn thấy và phương án sẽ được lưu không thể khác
     * nhau về cách nhóm phôi.
     */
    public CuttingPlanApprovalPreviewResponse toApprovalResponse(
            CuttingPlanApprovalPreview preview, List<CuttingPlanDemandView> demands) {
        CuttingPlanPreview plan = preview.plan();
        Map<OrderKey, SalesOrder> orderIndex = plan.scopeOrders().stream()
                .collect(Collectors.toMap(so -> new OrderKey(so.getYcsx(), so.getItem()), so -> so));
        return new CuttingPlanApprovalPreviewResponse(
                preview.scopeCutoffDate(),
                preview.stateFingerprint(),
                toResponse(plan, demands),
                toDetails(CuttingResultGrouping.groupCuts(plan.result().cuts()), orderIndex),
                toShortages(CuttingResultGrouping.groupShortages(plan.result().shortages()), orderIndex));
    }

    private static List<CuttingPlanDetailResponse> toDetails(
            List<CuttingResultGrouping.CutGroup> groups, Map<OrderKey, SalesOrder> orderIndex) {
        List<CuttingPlanDetailResponse> details = new ArrayList<>(groups.size());
        for (CuttingResultGrouping.CutGroup group : groups) {
            List<CuttingPlanDetailItemResponse> items = new ArrayList<>(group.items().size());
            for (CuttingResultGrouping.CutItem cutItem : group.items()) {
                SalesOrder order = orderIndex.get(new OrderKey(cutItem.ycsx(), cutItem.item()));
                items.add(new CuttingPlanDetailItemResponse(
                        null,
                        order.getId(),
                        order.getYcsx(),
                        order.getItem(),
                        order.getCustomer().getCustomerName(),
                        order.getDoorProduct().getId(),
                        order.getDoorProduct().getDoorMaterialName(),
                        order.getReqdDeliveryDate(),
                        order.getChieuCaoDh(),
                        order.getChieuRongDh(),
                        cutItem.cutLengthMm(),
                        cutItem.cutQuantity(),
                        cutItem.originalOrder()));
            }
            details.add(new CuttingPlanDetailResponse(
                    null,
                    group.slatMaterial().getId(),
                    group.slatMaterial().getSlatMaterialName(),
                    group.slatMaterial().getSlatMaterial(),
                    group.sourceLengthMm(),
                    group.patternCode(),
                    group.remainderMm(),
                    RemainderType.valueOf(group.remainderCategory().name()),
                    group.stickCount(),
                    items));
        }
        return details;
    }

    private static List<ShortageRecordResponse> toShortages(
            List<CuttingResultGrouping.ShortageGroup> groups, Map<OrderKey, SalesOrder> orderIndex) {
        List<ShortageRecordResponse> shortages = new ArrayList<>(groups.size());
        for (CuttingResultGrouping.ShortageGroup group : groups) {
            SalesOrder order = orderIndex.get(new OrderKey(group.ycsx(), group.item()));
            shortages.add(new ShortageRecordResponse(
                    null,
                    group.slatMaterial().getId(),
                    group.slatMaterial().getSlatMaterialName(),
                    group.slatMaterial().getSlatMaterial(),
                    order.getId(),
                    order.getYcsx(),
                    order.getItem(),
                    order.getCustomer().getCustomerName(),
                    order.getDoorProduct().getId(),
                    order.getDoorProduct().getDoorMaterialName(),
                    order.getReqdDeliveryDate(),
                    order.getChieuCaoDh(),
                    order.getChieuRongDh(),
                    group.missingQuantity(),
                    group.missingLengthM()));
        }
        return shortages;
    }

    private record OrderKey(String ycsx, Integer item) {}
}
