package com.slatcut.cutting.service;

import com.slatcut.cutting.domain.CuttingPlan;
import com.slatcut.cutting.domain.RemainderType;
import com.slatcut.cutting.dto.DashboardResponse;
import com.slatcut.cutting.dto.OrderWastePointResponse;
import com.slatcut.cutting.dto.RemainderBreakdownResponse;
import com.slatcut.cutting.dto.SlatGroupWasteResponse;
import com.slatcut.cutting.dto.WasteTrendPointResponse;
import com.slatcut.cutting.repository.CuttingPlanDetailItemRepository;
import com.slatcut.cutting.repository.CuttingPlanDetailRepository;
import com.slatcut.cutting.repository.CuttingPlanRepository;
import com.slatcut.cutting.repository.InventoryBatchRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Số liệu tổng hợp cho trang chủ. Chỉ đọc, không sinh thêm trạng thái nào — mọi con số đều lấy lại
 * từ kết quả các lần chạy đã lưu, nên dashboard không bao giờ lệch với trang chi tiết phương án cắt.
 *
 * <p>Phạm vi "đơn chờ xử lý" hỏi thẳng {@link CuttingPlanService} thay vì chép lại quy tắc ngày
 * cutoff — chỉ có đúng 1 nơi định nghĩa phạm vi xử lý.
 */
@Service
public class DashboardService {

    /** Số lần chạy gần nhất đưa lên biểu đồ xu hướng — đủ thấy xu thế mà trục hoành không bị chen chúc. */
    private static final int TREND_SIZE = 10;

    private static final int METERS_SCALE = 2;

    /** Số lẻ giữ lại cho tỷ lệ chia phôi dùng chung — đủ để tổng phế quy về các bộ cửa không lệch. */
    private static final int SHARE_SCALE = 10;
    private static final int PERCENT_SCALE = 1;
    private static final BigDecimal MM_PER_METER = new BigDecimal("1000");

    private final CuttingPlanService cuttingPlanService;
    private final CuttingPlanRepository cuttingPlanRepository;
    private final CuttingPlanDetailRepository cuttingPlanDetailRepository;
    private final CuttingPlanDetailItemRepository cuttingPlanDetailItemRepository;
    private final InventoryBatchRepository inventoryBatchRepository;

    public DashboardService(
            CuttingPlanService cuttingPlanService,
            CuttingPlanRepository cuttingPlanRepository,
            CuttingPlanDetailRepository cuttingPlanDetailRepository,
            CuttingPlanDetailItemRepository cuttingPlanDetailItemRepository,
            InventoryBatchRepository inventoryBatchRepository) {
        this.cuttingPlanService = cuttingPlanService;
        this.cuttingPlanRepository = cuttingPlanRepository;
        this.cuttingPlanDetailRepository = cuttingPlanDetailRepository;
        this.cuttingPlanDetailItemRepository = cuttingPlanDetailItemRepository;
        this.inventoryBatchRepository = inventoryBatchRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        // Query lấy N lần chạy mới nhất theo (runAt DESC, id DESC); đảo ngược đúng khoá đó để biểu
        // đồ chạy từ cũ sang mới, không sắp lại theo khoá khác kẻo 2 khoá lệch nhau (đồng hồ lùi).
        List<WasteTrendPointResponse> trend =
                cuttingPlanRepository.findRecent(PageRequest.of(0, TREND_SIZE)).stream()
                        .map(DashboardService::toTrendPoint)
                        .sorted(Comparator.comparing(WasteTrendPointResponse::runAt)
                                .thenComparing(WasteTrendPointResponse::planId))
                        .toList();
        WasteTrendPointResponse latestPlan = trend.isEmpty() ? null : trend.get(trend.size() - 1);

        CuttingPlanRepository.CumulativeTotals totals = cuttingPlanRepository.sumTotals();
        BigDecimal cumulativeWasteM = scaleMeters(totals.getTotalWasteM());
        BigDecimal cumulativeStockUsedM = scaleMeters(totals.getTotalStockUsedM());

        return new DashboardResponse(
                cuttingPlanService.countPendingInScope(),
                cuttingPlanService.countPendingMissingBom(),
                cuttingPlanService.currentScopeCutoffDate(),
                inventoryBatchRepository.countBySoThanhGreaterThan(0),
                inventoryBatchRepository.sumAvailableSticks(),
                latestPlan,
                cumulativeWasteM,
                cumulativeStockUsedM,
                wasteRatioPercent(cumulativeWasteM, cumulativeStockUsedM),
                trend,
                orderWasteTrend(),
                remainderBreakdown(),
                wasteByGroup());
    }

    /** Luôn trả đủ 3 ngưỡng kể cả khi chưa phát sinh — biểu đồ tròn giữ nguyên bộ nhãn/màu giữa các lần tải. */
    private List<RemainderBreakdownResponse> remainderBreakdown() {
        List<CuttingPlanDetailRepository.RemainderTotal> rows = cuttingPlanDetailRepository.sumRemainderMmByType();
        List<RemainderBreakdownResponse> breakdown = new ArrayList<>();
        for (RemainderType type : RemainderType.values()) {
            long totalMm = rows.stream()
                    .filter(row -> row.getRemainderType() == type)
                    .mapToLong(row -> row.getTotalMm() == null ? 0L : row.getTotalMm())
                    .sum();
            breakdown.add(new RemainderBreakdownResponse(type, toMeters(totalMm)));
        }
        return breakdown;
    }

    /**
     * Mức hao phí từng nhóm thanh nan, sắp theo <b>tỷ lệ</b> giảm dần để nhóm cắt kém nhất nằm đầu
     * biểu đồ cột.
     *
     * <p>Sắp theo số mét thì thứ hạng chỉ phản ánh nhóm nào tiêu thụ nhiều vật tư nhất: nan chính
     * chiếm phần lớn tồn kho nên luôn đứng đầu kể cả khi nó là nhóm cắt khít nhất. Lọc theo
     * {@code stockUsedM > 0} chứ không theo lượng phế — nhóm cắt hết sạch không phế phải hiện lên
     * ở mức 0%, vì với trục phần trăm thì 0% là thông tin tốt cần thấy, không phải lý do để biến
     * mất khỏi biểu đồ.
     */
    private List<SlatGroupWasteResponse> wasteByGroup() {
        return cuttingPlanDetailRepository.sumWasteAndStockUsedMmBySlatGroup(RemainderType.RESTOCK).stream()
                .map(row -> {
                    long wasteMm = row.getWasteMm() == null ? 0L : row.getWasteMm();
                    long stockUsedMm = row.getStockUsedMm() == null ? 0L : row.getStockUsedMm();
                    return new SlatGroupWasteResponse(
                            row.getSlatGroup(),
                            toMeters(wasteMm),
                            toMeters(stockUsedMm),
                            wasteRatioPercent(BigDecimal.valueOf(wasteMm), BigDecimal.valueOf(stockUsedMm)));
                })
                .filter(response -> response.stockUsedM().signum() > 0)
                .sorted(Comparator.comparing(SlatGroupWasteResponse::wasteRatioPercent)
                        .reversed()
                        .thenComparing(SlatGroupWasteResponse::slatGroup))
                .toList();
    }

    /**
     * Quy phế về từng bộ cửa để biểu đồ xu hướng chạy theo ngày giao thay vì theo lần chạy.
     *
     * <p>Một phôi có thể bị hai bộ cửa dùng chung (Mức 3 ghép hai đoạn của hai đơn khác nhau vào
     * chung một thanh), nên phần dư của nó được <b>chia theo tỷ lệ độ dài mỗi bên đã cắt trên
     * chính phôi đó</b>. Cách chia này bảo toàn tổng: mỗi mét phế được quy về đúng một lần, không
     * mất và không nhân đôi, nên cộng hết các bộ cửa lại vẫn ra đúng tổng phế của phương án. Mẫu
     * số của mỗi bộ cửa chia theo cùng tỷ lệ đó và cũng trừ phần dư nhập lại kho, đúng như
     * {@link #wasteByGroup()} và tỷ lệ tổng.
     *
     * <p>Sắp theo ngày giao rồi {@code ycsx}/{@code item} — cùng khóa ưu tiên thuật toán dùng để
     * xếp thứ tự cắt, nên đọc biểu đồ từ trái sang phải là đọc đúng thứ tự xử lý thật. Phép sắp xếp
     * này là bắt buộc chứ không phải trang trí: thứ tự tự nhiên của {@code byOrder} là thứ tự dòng
     * mà truy vấn trả về, tức thứ tự (loại thanh nan, id dòng phôi) — trùng với thứ tự ngày giao chỉ
     * khi cả đợt chỉ dùng đúng một loại thanh nan.
     *
     * <p><b>Hệ quả phân bổ cần biết khi đọc biểu đồ:</b> phôi cắt ở Mức 4 để lại phần dư trên 3m
     * được nhập lại kho, mà phần dư đó cho tử số 0 và bị trừ khỏi mẫu số. Nên bộ cửa <i>sinh ra</i>
     * phần dư trên 3m hiện 0% cho phôi đó, còn bộ cửa <i>tiêu thụ</i> phôi tái sử dụng về sau mới
     * gánh phần phế cuối cùng của nó. Đây là lựa chọn có chủ ý, nhất quán với cách
     * {@code CuttingPlanService#totalStockUsedM} định nghĩa tồn kho thực tiêu hao: vật liệu chỉ
     * được tính là mất đi ở lần nó thật sự bị bỏ, không tính ở lần nó quay về kho.
     */
    private List<OrderWastePointResponse> orderWasteTrend() {
        List<CuttingPlanDetailItemRepository.OrderCutShare> rows = cuttingPlanDetailItemRepository.findOrderCutShares();

        // Mẫu số của phép chia tỷ lệ: tổng độ dài đã cắt trên mỗi phôi, gom từ mọi đoạn anh em.
        Map<Long, Long> cutTotalByDetail = new LinkedHashMap<>();
        for (CuttingPlanDetailItemRepository.OrderCutShare row : rows) {
            cutTotalByDetail.merge(row.getDetailId(), orderCutMm(row), Long::sum);
        }

        Map<Long, OrderAccumulator> byOrder = new LinkedHashMap<>();
        for (CuttingPlanDetailItemRepository.OrderCutShare row : rows) {
            long cutTotalMm = cutTotalByDetail.getOrDefault(row.getDetailId(), 0L);
            if (cutTotalMm <= 0) {
                // Phôi không có đoạn nào thì không có bộ cửa nào để quy phế vào; bỏ qua thay vì chia cho 0.
                continue;
            }
            BigDecimal share = BigDecimal.valueOf(orderCutMm(row))
                    .divide(BigDecimal.valueOf(cutTotalMm), SHARE_SCALE, RoundingMode.HALF_UP);

            int stickCount = row.getStickCount() == null ? 0 : row.getStickCount();
            int remainderMm = row.getRemainderMm() == null ? 0 : row.getRemainderMm();
            int sourceLengthMm = row.getSourceLengthMm() == null ? 0 : row.getSourceLengthMm();
            boolean restock = row.getRemainderType() == RemainderType.RESTOCK;

            long wasteMm = restock ? 0L : (long) stickCount * remainderMm;
            long stockUsedMm = (long) stickCount * (restock ? sourceLengthMm - remainderMm : sourceLengthMm);

            OrderAccumulator accumulator = byOrder.computeIfAbsent(
                    row.getSalesOrderId(),
                    key -> new OrderAccumulator(
                            row.getSalesOrderId(), row.getYcsx(), row.getItem(), row.getReqdDeliveryDate()));
            accumulator.wasteMm = accumulator.wasteMm.add(BigDecimal.valueOf(wasteMm).multiply(share));
            accumulator.stockUsedMm = accumulator.stockUsedMm.add(BigDecimal.valueOf(stockUsedMm).multiply(share));
        }

        return byOrder.values().stream()
                .map(OrderAccumulator::toResponse)
                .sorted(Comparator.comparing(
                                OrderWastePointResponse::reqdDeliveryDate,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(OrderWastePointResponse::ycsx, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(OrderWastePointResponse::item, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private static long orderCutMm(CuttingPlanDetailItemRepository.OrderCutShare row) {
        return row.getOrderCutMm() == null ? 0L : row.getOrderCutMm();
    }

    /** Bộ cộng dồn cho 1 bộ cửa — giữ milimet ở dạng BigDecimal vì phép chia tỷ lệ sinh ra phần lẻ. */
    private static final class OrderAccumulator {
        private final Long salesOrderId;
        private final String ycsx;
        private final Integer item;
        private final LocalDate reqdDeliveryDate;
        private BigDecimal wasteMm = BigDecimal.ZERO;
        private BigDecimal stockUsedMm = BigDecimal.ZERO;

        private OrderAccumulator(Long salesOrderId, String ycsx, Integer item, LocalDate reqdDeliveryDate) {
            this.salesOrderId = salesOrderId;
            this.ycsx = ycsx;
            this.item = item;
            this.reqdDeliveryDate = reqdDeliveryDate;
        }

        private OrderWastePointResponse toResponse() {
            return new OrderWastePointResponse(
                    salesOrderId,
                    ycsx,
                    item,
                    reqdDeliveryDate,
                    toMeters(wasteMm),
                    toMeters(stockUsedMm),
                    wasteRatioPercent(wasteMm, stockUsedMm));
        }
    }

    private static WasteTrendPointResponse toTrendPoint(CuttingPlan plan) {
        return new WasteTrendPointResponse(
                plan.getId(),
                plan.getRunAt(),
                plan.getStatus(),
                plan.getScopeOrderCount(),
                plan.getTotalWasteM(),
                plan.getTotalStockUsedM(),
                wasteRatioPercent(plan.getTotalWasteM(), plan.getTotalStockUsedM()));
    }

    /**
     * Mẫu số là tồn kho THỰC TIÊU HAO (đã trừ phần dư nhập lại kho) — xem CuttingPlanService#totalStockUsedM.
     *
     * <p>Tử số và mẫu số phải cùng đơn vị, nhưng <b>đơn vị nào cũng được miễn là chưa bị làm tròn</b>:
     * chia trên milimet gốc rồi mới làm tròn kết quả, chứ không làm tròn về mét trước rồi mới chia.
     * Làm tròn trước sinh ra sai lệch có thật chứ không chỉ là chữ số cuối — hai bộ cửa dùng chung
     * một phôi bắt buộc có cùng một tỷ lệ (phép chia tỷ lệ nhân cả tử lẫn mẫu với cùng một hệ số nên
     * hệ số triệt tiêu), vậy mà làm tròn về mét trước sẽ hiển thị chúng thành 1,6% và 1,7%. Tệ hơn,
     * một bộ cửa phế 4mm sẽ bị làm tròn mất thành 0,00m rồi báo 0,0%.
     */
    private static BigDecimal wasteRatioPercent(BigDecimal wasteM, BigDecimal stockUsedM) {
        if (wasteM == null || stockUsedM == null || stockUsedM.signum() == 0) {
            return BigDecimal.ZERO.setScale(PERCENT_SCALE);
        }
        return wasteM.multiply(new BigDecimal("100")).divide(stockUsedM, PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal toMeters(long lengthMm) {
        return new BigDecimal(lengthMm).divide(MM_PER_METER, METERS_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal toMeters(BigDecimal lengthMm) {
        return lengthMm.divide(MM_PER_METER, METERS_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal scaleMeters(BigDecimal meters) {
        return meters == null
                ? BigDecimal.ZERO.setScale(METERS_SCALE)
                : meters.setScale(METERS_SCALE, RoundingMode.HALF_UP);
    }
}
