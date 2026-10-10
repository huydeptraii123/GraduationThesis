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
            Optional<String> blockReason = blockReason(bomItems);
            if (blockReason.isPresent()) {
                log.warn(
                        "Bỏ qua đơn hàng ycsx={} item={}: mẫu cửa id={} {}, tính các dòng còn lại sẽ ra nhu cầu"
                                + " cắt không đầy đủ",
                        order.getYcsx(),
                        order.getItem(),
                        order.getDoorProduct().getId(),
                        blockReason.get());
                continue;
            }
            for (BomItem bomItem : bomItems) {
                // Qua được blockReason thì dòng thiếu tham số chỉ có thể là nan chính thiếu hệ số.
                if (lacksCutParameters(bomItem)) {
                    logSkipped(order, bomItem, "nan chính thiếu hệ số tính số nan — profile phụ, không lập kế hoạch cắt");
                    continue;
                }
                buildDemand(order, bomItem).ifPresent(demands::add);
            }
        }
        return demands;
    }

    /**
     * Lý do bỏ qua CẢ bộ cửa, nếu có: có dòng RAIL thiếu {@code heightOffsetM}, hoặc mẫu cửa có dòng
     * MAIN_SLAT nhưng không dòng nào đủ hai hệ số tính số nan. Khi đó nan chính hay ray của bộ cửa
     * chưa từng được tính, nên tính các dòng còn lại sẽ ra một bộ cửa "đủ nan" sai.
     *
     * <p>Dòng MAIN_SLAT thiếu hệ số bên cạnh một dòng đủ hệ số KHÔNG làm bỏ cả bộ cửa: theo doanh
     * nghiệp chỉ nan lớn mới có hệ số, dòng như vậy là profile phụ nhỏ và {@link #buildDemands} bỏ
     * riêng nó. Nhóm OTHER không xét ở đây: nó không cắt từ thanh tồn kho (xem {@link #buildDemand}).
     *
     * <p><b>Luật này có hai bản sao phải sửa theo</b>: hằng {@code SalesOrderRepository.HAS_COMPLETE_BOM}
     * (phạm vi của hai chức năng tính và duyệt) và {@code SalesOrderSpecifications.hasCompleteBom}
     * (trạng thái "đang bị chặn" ở màn đơn hàng). Nhờ chúng, đơn như vậy không bao giờ vào tới đây
     * từ hai chức năng kia — kiểm tra ở đây là lớp chặn cuối để hàm này tự nó không bao giờ trả về
     * nhu cầu cắt thiếu thành phần. Vế "có ít nhất một dòng ngoài OTHER" của hai bản kia không cần
     * lặp lại ở đây: mẫu cửa như vậy tự sinh 0 nhu cầu.
     */
    private static Optional<String> blockReason(List<BomItem> bomItems) {
        Optional<BomItem> brokenRail = bomItems.stream()
                .filter(bomItem -> bomItem.getSlatMaterial().getSlatGroup() == SlatGroup.RAIL)
                .filter(CuttingDemandService::lacksCutParameters)
                .findFirst();
        if (brokenRail.isPresent()) {
            return Optional.of("có dòng ray id=" + brokenRail.get().getId() + " thiếu heightOffsetM");
        }
        List<BomItem> mainSlats = bomItems.stream()
                .filter(bomItem -> bomItem.getSlatMaterial().getSlatGroup() == SlatGroup.MAIN_SLAT)
                .toList();
        if (!mainSlats.isEmpty() && mainSlats.stream().allMatch(CuttingDemandService::lacksCutParameters)) {
            return Optional.of("có " + mainSlats.size() + " dòng nan chính nhưng không dòng nào đủ hệ số tính số nan");
        }
        return Optional.empty();
    }

    /**
     * Dòng định mức thuộc nhóm có công thức cắt nhưng thiếu tham số của chính công thức đó: MAIN_SLAT
     * thiếu một trong hai hệ số tính số nan, RAIL thiếu {@code heightOffsetM}. Switch phủ đủ mọi
     * nhóm để thêm nhóm mới thì không biên dịch được cho tới khi quyết định tham số bắt buộc của nó.
     */
    private static boolean lacksCutParameters(BomItem bomItem) {
        return switch (bomItem.getSlatMaterial().getSlatGroup()) {
            case MAIN_SLAT -> bomItem.getSlatCountSlope() == null || bomItem.getSlatCountIntercept() == null;
            case RAIL -> bomItem.getHeightOffsetM() == null;
            case SUB_SLAT, BOTTOM_BAR, OTHER -> false;
        };
    }

    /**
     * Gọi sau {@link #blockReason} và sau khi đã bỏ dòng nan chính thiếu hệ số: mọi tham số mà công
     * thức của nhóm cần đều đã có.
     */
    private Optional<CuttingDemand> buildDemand(SalesOrder order, BomItem bomItem) {
        SlatGroup slatGroup = bomItem.getSlatMaterial().getSlatGroup();
        BigDecimal cutDimM;
        int quantity;

        switch (slatGroup) {
            case MAIN_SLAT -> {
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
