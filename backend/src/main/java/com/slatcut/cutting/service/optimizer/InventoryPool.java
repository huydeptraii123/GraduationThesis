package com.slatcut.cutting.service.optimizer;

import com.slatcut.cutting.domain.InventoryBatch;
import com.slatcut.cutting.domain.SlatMaterial;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Trạng thái tồn kho thanh nan cho đúng 1 lần chạy CuttingStrategy — KHÔNG phải Spring bean, tạo
 * mới mỗi lần gọi ({@code new InventoryPool(batches)}) vì trạng thái bị trừ dần trong quá trình cắt.
 */
public class InventoryPool {

    private final Map<Long, NavigableMap<Integer, Integer>> stockByMaterialId = new HashMap<>();

    public InventoryPool(List<InventoryBatch> batches) {
        for (InventoryBatch batch : batches) {
            // InventoryImportService zero-out các tổ hợp mất khỏi file thay vì xóa dòng (soThanh=0) —
            // bỏ qua ngay từ đây để TreeMap.ceilingEntry() ở bestFit() không bao giờ khớp nhầm vào
            // 1 độ dài đã hết thanh chỉ vì nó ngắn hơn độ dài thật sự còn hàng kế tiếp.
            if (batch.getSoThanh() == null || batch.getSoThanh() <= 0) {
                continue;
            }
            stockByMaterialId
                    .computeIfAbsent(batch.getSlatMaterial().getId(), id -> new TreeMap<>())
                    .merge(batch.getDoDaiThanhMm(), batch.getSoThanh(), Integer::sum);
        }
    }

    /** Tìm thanh tồn kho ngắn nhất còn đủ dài để cắt {@code cutLengthMm}, trừ 1 thanh nếu tìm thấy. */
    public Optional<Integer> bestFit(SlatMaterial slatMaterial, int cutLengthMm) {
        NavigableMap<Integer, Integer> stock = stockByMaterialId.get(slatMaterial.getId());
        if (stock == null) {
            return Optional.empty();
        }
        Map.Entry<Integer, Integer> match = stock.ceilingEntry(cutLengthMm);
        if (match == null) {
            return Optional.empty();
        }
        return Optional.of(consume(stock, match.getKey()));
    }

    /**
     * Mức 1 — khớp gần đúng: giống {@link #bestFit}, nhưng CHỈ tiêu thụ + trả về khi phần dư dự kiến
     * < 30cm — ngược lại trả rỗng và KHÔNG đụng vào state, để Mức 2/3/4 sau đó vẫn thấy nguyên tồn kho.
     */
    public Optional<Integer> findNearFit(SlatMaterial slatMaterial, int cutLengthMm) {
        NavigableMap<Integer, Integer> stock = stockByMaterialId.get(slatMaterial.getId());
        if (stock == null) {
            return Optional.empty();
        }
        Map.Entry<Integer, Integer> match = stock.ceilingEntry(cutLengthMm);
        if (match == null || match.getKey() - cutLengthMm >= RemainderCategory.DISCARD_THRESHOLD_MM) {
            return Optional.empty();
        }
        return Optional.of(consume(stock, match.getKey()));
    }

    /** Mức 2 — 1 thanh dài đúng {@code k} lần {@code cutLengthMm} (k≥2), tiêu thụ 1 thanh đó. */
    public record MultipleMatch(int stockLengthMm, int multiplier) {}

    /**
     * Duyệt tồn kho tăng dần theo độ dài, trả về thanh NGẮN NHẤT là bội số đúng của {@code cutLengthMm}
     * mà số bội ({@code k}) không vượt quá {@code availableCount} (số đoạn cùng độ dài đang chờ trong
     * hàng đợi, tính cả X) — giữ tồn kho dài hơn lại cho nhu cầu lớn hơn về sau.
     */
    public Optional<MultipleMatch> findMultipleOfSameLength(SlatMaterial slatMaterial, int cutLengthMm, int availableCount) {
        NavigableMap<Integer, Integer> stock = stockByMaterialId.get(slatMaterial.getId());
        if (stock == null) {
            return Optional.empty();
        }
        for (Map.Entry<Integer, Integer> entry : stock.entrySet()) {
            int lengthMm = entry.getKey();
            if (lengthMm % cutLengthMm != 0) {
                continue;
            }
            int multiplier = lengthMm / cutLengthMm;
            if (multiplier >= 2 && multiplier <= availableCount) {
                consume(stock, lengthMm);
                return Optional.of(new MultipleMatch(lengthMm, multiplier));
            }
        }
        return Optional.empty();
    }

    /** Mức 3 — ghép đúng 2 đoạn: giống {@link #findNearFit} nhưng khớp theo tổng {@code X+Y}. */
    public Optional<Integer> findCombination(SlatMaterial slatMaterial, int cutLengthMmX, int cutLengthMmY) {
        return findNearFit(slatMaterial, cutLengthMmX + cutLengthMmY);
    }

    /** Nhập lại 1 thanh (thường là phần dư > 3m vừa cắt) để các đoạn xử lý SAU trong cùng lượt chạy dùng được ngay. */
    public void restock(SlatMaterial slatMaterial, int lengthMm) {
        stockByMaterialId.computeIfAbsent(slatMaterial.getId(), id -> new TreeMap<>()).merge(lengthMm, 1, Integer::sum);
    }

    /** Chỉ dùng cho test — số thanh còn lại của 1 độ dài cụ thể, không dùng trong logic chính. */
    public int remainingCount(Long slatMaterialId, int lengthMm) {
        NavigableMap<Integer, Integer> stock = stockByMaterialId.get(slatMaterialId);
        return stock == null ? 0 : stock.getOrDefault(lengthMm, 0);
    }

    private static int consume(NavigableMap<Integer, Integer> stock, int lengthMm) {
        int remaining = stock.get(lengthMm) - 1;
        if (remaining > 0) {
            stock.put(lengthMm, remaining);
        } else {
            stock.remove(lengthMm);
        }
        return lengthMm;
    }
}
