package com.slatcut.cutting.service;

import com.slatcut.cutting.domain.BomItem;
import com.slatcut.cutting.domain.SalesOrder;
import com.slatcut.cutting.domain.SlatGroup;
import com.slatcut.cutting.repository.BomItemRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sinh danh sách {@link CuttingDemand} từ SalesOrder + BomItem, theo đúng công thức đã chốt với
 * doanh nghiệp (docs/sequence-diagrams.md, mục "Công thức tính nhu cầu cắt"). Không tự chọn phạm vi
 * đơn hàng cần xử lý — việc đó thuộc CuttingPlanService (task 9), nơi đã có sẵn danh sách đơn truyền vào.
 */
@Service
public class CuttingDemandService {

    private static final Logger log = LoggerFactory.getLogger(CuttingDemandService.class);

    /** Fallback khi widthOffsetM chưa có: xác nhận với doanh nghiệp là zChieuRongDh × 0.976. */
    private static final BigDecimal WIDTH_FALLBACK_RATIO = new BigDecimal("0.976");
    private static final BigDecimal MM_PER_M = new BigDecimal(1000);

    private final BomItemRepository bomItemRepository;

    public CuttingDemandService(BomItemRepository bomItemRepository) {
        this.bomItemRepository = bomItemRepository;
    }

    @Transactional(readOnly = true)
    public List<CuttingDemand> buildDemands(List<SalesOrder> salesOrders) {
        if (salesOrders.isEmpty()) {
            return List.of();
        }

        List<Long> doorProductIds =
                salesOrders.stream().map(order -> order.getDoorProduct().getId()).distinct().toList();
        Map<Long, List<BomItem>> bomItemsByDoorProductId = bomItemRepository.findByDoorProduct_IdIn(doorProductIds)
                .stream()
                .collect(Collectors.groupingBy(bomItem -> bomItem.getDoorProduct().getId()));

        List<CuttingDemand> demands = new ArrayList<>();
        for (SalesOrder order : salesOrders) {
            List<BomItem> bomItems =
                    bomItemsByDoorProductId.getOrDefault(order.getDoorProduct().getId(), List.of());
            if (bomItems.isEmpty()) {
                log.warn(
                        "Bỏ qua đơn hàng ycsx={} item={}: mẫu cửa id={} chưa có định mức BOM nào",
                        order.getYcsx(),
                        order.getItem(),
                        order.getDoorProduct().getId());
                continue;
            }
            for (BomItem bomItem : bomItems) {
                buildDemand(order, bomItem).ifPresent(demands::add);
            }
        }
        return demands;
    }

    private Optional<CuttingDemand> buildDemand(SalesOrder order, BomItem bomItem) {
        SlatGroup slatGroup = bomItem.getSlatMaterial().getSlatGroup();
        BigDecimal cutDimM;
        int quantity;

        switch (slatGroup) {
            case MAIN_SLAT -> {
                if (bomItem.getSlatCountSlope() == null || bomItem.getSlatCountIntercept() == null) {
                    logSkipped(order, bomItem, "MAIN_SLAT thiếu slatCountSlope/slatCountIntercept");
                    return Optional.empty();
                }
                BigDecimal productionWidthM = bomItem.getWidthOffsetM() != null
                        ? order.getChieuRongDh().subtract(bomItem.getWidthOffsetM())
                        : order.getChieuRongDh().multiply(WIDTH_FALLBACK_RATIO);
                cutDimM = productionWidthM;
                quantity = round(bomItem.getSlatCountSlope()
                        .multiply(order.getChieuCaoDh())
                        .add(bomItem.getSlatCountIntercept()));
            }
            case BOTTOM_BAR, SUB_SLAT -> {
                cutDimM = order.getChieuRongDh();
                quantity = 1;
            }
            case RAIL -> {
                if (bomItem.getHeightOffsetM() == null) {
                    logSkipped(order, bomItem, "RAIL thiếu heightOffsetM");
                    return Optional.empty();
                }
                cutDimM = order.getChieuCaoDh().subtract(bomItem.getHeightOffsetM());
                quantity = 2;
            }
            default -> {
                logSkipped(order, bomItem, "slatGroup OTHER không xác định công thức cắt");
                return Optional.empty();
            }
        }

        int cutLengthMm = round(cutDimM.multiply(MM_PER_M));
        return Optional.of(new CuttingDemand(
                bomItem.getSlatMaterial(), cutLengthMm, quantity, order.getReqdDeliveryDate(), order.getYcsx(), order.getItem()));
    }

    private void logSkipped(SalesOrder order, BomItem bomItem, String reason) {
        log.warn(
                "Bỏ qua BomItem id={} (slatMaterial={}) cho đơn ycsx={} item={}: {}",
                bomItem.getId(),
                bomItem.getSlatMaterial().getId(),
                order.getYcsx(),
                order.getItem(),
                reason);
    }

    private static int round(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).intValueExact();
    }
}
