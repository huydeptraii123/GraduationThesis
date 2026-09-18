package com.slatcut.cutting.service;

import com.slatcut.cutting.domain.CuttingPlan;
import com.slatcut.cutting.domain.RemainderType;
import com.slatcut.cutting.dto.DashboardResponse;
import com.slatcut.cutting.dto.RemainderBreakdownResponse;
import com.slatcut.cutting.dto.SlatGroupWasteResponse;
import com.slatcut.cutting.dto.WasteTrendPointResponse;
import com.slatcut.cutting.repository.CuttingPlanDetailRepository;
import com.slatcut.cutting.repository.CuttingPlanRepository;
import com.slatcut.cutting.repository.InventoryBatchRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    private static final int PERCENT_SCALE = 1;
    private static final BigDecimal MM_PER_METER = new BigDecimal("1000");

    private final CuttingPlanService cuttingPlanService;
    private final CuttingPlanRepository cuttingPlanRepository;
    private final CuttingPlanDetailRepository cuttingPlanDetailRepository;
    private final InventoryBatchRepository inventoryBatchRepository;

    public DashboardService(
            CuttingPlanService cuttingPlanService,
            CuttingPlanRepository cuttingPlanRepository,
            CuttingPlanDetailRepository cuttingPlanDetailRepository,
            InventoryBatchRepository inventoryBatchRepository) {
        this.cuttingPlanService = cuttingPlanService;
        this.cuttingPlanRepository = cuttingPlanRepository;
        this.cuttingPlanDetailRepository = cuttingPlanDetailRepository;
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
                cuttingPlanService.currentScopeCutoffDate(),
                inventoryBatchRepository.countBySoThanhGreaterThan(0),
                inventoryBatchRepository.sumAvailableSticks(),
                latestPlan,
                cumulativeWasteM,
                cumulativeStockUsedM,
                wasteRatioPercent(cumulativeWasteM, cumulativeStockUsedM),
                trend,
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

    /** Chỉ các nhóm thật sự có phát sinh phế, sắp giảm dần để nhóm hao nhiều nhất nằm đầu biểu đồ cột. */
    private List<SlatGroupWasteResponse> wasteByGroup() {
        return cuttingPlanDetailRepository.sumWasteMmBySlatGroup(RemainderType.RESTOCK).stream()
                .map(row -> new SlatGroupWasteResponse(
                        row.getSlatGroup(), toMeters(row.getTotalMm() == null ? 0L : row.getTotalMm())))
                .filter(response -> response.totalM().signum() > 0)
                .sorted(Comparator.comparing(SlatGroupWasteResponse::totalM).reversed())
                .toList();
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

    /** Mẫu số là tồn kho THỰC TIÊU HAO (đã trừ phần dư nhập lại kho) — xem CuttingPlanService#totalStockUsedM. */
    private static BigDecimal wasteRatioPercent(BigDecimal wasteM, BigDecimal stockUsedM) {
        if (wasteM == null || stockUsedM == null || stockUsedM.signum() == 0) {
            return BigDecimal.ZERO.setScale(PERCENT_SCALE);
        }
        return wasteM.multiply(new BigDecimal("100")).divide(stockUsedM, PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal toMeters(long lengthMm) {
        return new BigDecimal(lengthMm).divide(MM_PER_METER, METERS_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal scaleMeters(BigDecimal meters) {
        return meters == null
                ? BigDecimal.ZERO.setScale(METERS_SCALE)
                : meters.setScale(METERS_SCALE, RoundingMode.HALF_UP);
    }
}
