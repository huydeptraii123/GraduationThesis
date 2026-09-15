package com.slatcut.cutting.service.optimizer;

import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.service.CuttingDemand;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Đúng 4 mức ưu tiên đã duyệt (docs/activity-diagrams.md mục "3. Luồng thuật toán sinh phương án
 * cắt"): mỗi nhóm slatMaterial xử lý độc lập, hàng đợi luôn lấy đoạn ưu tiên cao nhất
 * (reqdDeliveryDate, ycsx, item) — không bao giờ trì hoãn để chờ ghép. Với mỗi đoạn X, thử tuần tự
 * Mức 1 (khớp gần đúng) → Mức 2 (bội số) → Mức 3 (ghép đúng 2 đoạn — giới hạn không ghép nhiều hơn,
 * tìm tổ hợp tổng quát trên cả hàng đợi là bài toán NP-hard, không khả thi ở quy mô khóa luận) →
 * Mức 4 (best-fit, tái dùng ngay phần dư > 3m trong cùng lượt chạy).
 */
@Service
public class BestFitDecreasingStrategy implements CuttingStrategy {

    private static final Comparator<CuttingDemand> PRIORITY_ORDER = Comparator.comparing(CuttingDemand::reqdDeliveryDate)
            .thenComparing(CuttingDemand::ycsx)
            .thenComparing(CuttingDemand::item);

    @Override
    public CuttingPlanResult computePlan(List<CuttingDemand> demands, InventoryPool pool) {
        List<CutRecord> cuts = new ArrayList<>();
        List<ShortageEntry> shortages = new ArrayList<>();

        for (List<CuttingDemand> group : groupBySlatMaterial(demands).values()) {
            processGroup(group, pool, cuts, shortages);
        }
        return new CuttingPlanResult(cuts, shortages);
    }

    private void processGroup(
            List<CuttingDemand> group, InventoryPool pool, List<CutRecord> cuts, List<ShortageEntry> shortages) {
        LinkedList<CuttingDemand> queue = new LinkedList<>(expandByQuantity(group));
        queue.sort(PRIORITY_ORDER);

        while (!queue.isEmpty()) {
            CuttingDemand x = queue.pollFirst();
            SlatMaterial material = x.slatMaterial();

            Optional<Integer> nearFit = pool.findNearFit(material, x.cutLengthMm());
            if (nearFit.isPresent()) {
                cuts.add(buildCutRecord(material, nearFit.get(), List.of(x)));
                continue;
            }

            int sameLengthAvailable = 1 + countSameLength(queue, x.cutLengthMm());
            Optional<InventoryPool.MultipleMatch> multiple =
                    pool.findMultipleOfSameLength(material, x.cutLengthMm(), sameLengthAvailable);
            if (multiple.isPresent()) {
                List<CuttingDemand> pieces = new ArrayList<>();
                pieces.add(x);
                removeSameLength(queue, x.cutLengthMm(), multiple.get().multiplier() - 1, pieces);
                cuts.add(buildCutRecord(material, multiple.get().stockLengthMm(), pieces));
                continue;
            }

            CuttingDemand combinePartner = null;
            Integer combinedStockLengthMm = null;
            for (CuttingDemand candidate : queue) {
                Optional<Integer> combo = pool.findCombination(material, x.cutLengthMm(), candidate.cutLengthMm());
                if (combo.isPresent()) {
                    combinePartner = candidate;
                    combinedStockLengthMm = combo.get();
                    break;
                }
            }
            if (combinePartner != null) {
                queue.remove(combinePartner);
                cuts.add(buildCutRecord(material, combinedStockLengthMm, List.of(x, combinePartner)));
                continue;
            }

            Optional<Integer> bestFit = pool.bestFit(material, x.cutLengthMm());
            if (bestFit.isPresent()) {
                CutRecord cut = buildCutRecord(material, bestFit.get(), List.of(x));
                if (cut.remainderCategory() == RemainderCategory.RESTOCK) {
                    // Tái dùng ngay trong cùng lượt chạy — các đoạn xử lý sau (Mức 1-4) thấy được
                    // phần dư này như tồn kho thật, không phải chờ tới lần chạy kế tiếp.
                    pool.restock(material, cut.remainderMm());
                }
                cuts.add(cut);
            } else {
                shortages.add(new ShortageEntry(material, x));
            }
        }
    }

    private static CutRecord buildCutRecord(SlatMaterial material, int stockLengthMm, List<CuttingDemand> pieces) {
        int usedMm = pieces.stream().mapToInt(CuttingDemand::cutLengthMm).sum();
        int remainderMm = stockLengthMm - usedMm;
        return new CutRecord(material, stockLengthMm, pieces, remainderMm, RemainderCategory.classify(remainderMm));
    }

    private static int countSameLength(List<CuttingDemand> queue, int cutLengthMm) {
        return (int) queue.stream().filter(d -> d.cutLengthMm() == cutLengthMm).count();
    }

    /** Loại đúng {@code count} đoạn cùng độ dài khỏi hàng đợi, theo thứ tự ưu tiên đang có, thêm vào {@code pieces}. */
    private static void removeSameLength(LinkedList<CuttingDemand> queue, int cutLengthMm, int count, List<CuttingDemand> pieces) {
        var iterator = queue.iterator();
        int removed = 0;
        while (iterator.hasNext() && removed < count) {
            CuttingDemand candidate = iterator.next();
            if (candidate.cutLengthMm() == cutLengthMm) {
                pieces.add(candidate);
                iterator.remove();
                removed++;
            }
        }
    }

    private static Map<Long, List<CuttingDemand>> groupBySlatMaterial(List<CuttingDemand> demands) {
        return demands.stream().collect(Collectors.groupingBy(d -> d.slatMaterial().getId(), LinkedHashMap::new, Collectors.toList()));
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
