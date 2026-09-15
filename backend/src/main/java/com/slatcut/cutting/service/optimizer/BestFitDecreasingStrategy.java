package com.slatcut.cutting.service.optimizer;

import com.slatcut.cutting.service.CuttingDemand;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Bản cơ bản (task 8.2) — chỉ Mức 4 (best-fit thuần túy, xử lý đoạn dài trước theo đúng "Decreasing"
 * của Best Fit Decreasing). Mức 1-3 (khớp gần đúng/bội số/ghép nối nhiều đơn) và thứ tự ưu tiên thật
 * theo (reqd_delivery_date, ycsx, z_item) là việc của task 8.3, chưa có ở đây.
 */
@Service
public class BestFitDecreasingStrategy implements CuttingStrategy {

    @Override
    public CuttingPlanResult computePlan(List<CuttingDemand> demands, InventoryPool pool) {
        List<CuttingDemand> units = expandByQuantity(demands);
        // TODO(8.3): sắp theo (reqd_delivery_date, ycsx, z_item) trước — BFD giảm dần theo
        // cutLengthMm chỉ áp dụng NỘI BỘ trong cùng 1 đợt ưu tiên, không phải thứ tự toàn cục.
        units.sort(Comparator.comparingInt(CuttingDemand::cutLengthMm).reversed());

        List<CutRecord> cuts = new ArrayList<>();
        List<ShortageEntry> shortages = new ArrayList<>();
        for (CuttingDemand unit : units) {
            Optional<Integer> matched = pool.bestFit(unit.slatMaterial(), unit.cutLengthMm());
            if (matched.isPresent()) {
                int stockLengthMm = matched.get();
                int remainderMm = stockLengthMm - unit.cutLengthMm();
                cuts.add(new CutRecord(
                        unit.slatMaterial(), stockLengthMm, unit, remainderMm, RemainderCategory.classify(remainderMm)));
            } else {
                shortages.add(new ShortageEntry(unit.slatMaterial(), unit));
            }
        }
        return new CuttingPlanResult(cuts, shortages);
    }

    /** Mỗi đơn vị trả về đại diện đúng 1 thanh cần cắt — quantity=1, không giữ nguyên quantity gốc
     * của demand cha, để mọi nơi đọc CutRecord/ShortageEntry sau này không đếm nhân đôi. */
    private static List<CuttingDemand> expandByQuantity(List<CuttingDemand> demands) {
        List<CuttingDemand> units = new ArrayList<>();
        for (CuttingDemand demand : demands) {
            for (int i = 0; i < demand.quantity(); i++) {
                units.add(new CuttingDemand(
                        demand.slatMaterial(),
                        demand.cutLengthMm(),
                        1,
                        demand.reqdDeliveryDate(),
                        demand.ycsx(),
                        demand.item()));
            }
        }
        return units;
    }
}
