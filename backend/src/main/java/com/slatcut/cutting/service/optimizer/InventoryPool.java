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
 *
 * <p>TODO(8.3): thêm findNearFit()/findMultipleOfSameLength()/findCombination() cho Mức 1-3 của
 * thuật toán (docs/sequence-diagrams.md mục "2. Luồng sinh phương án cắt") — 8.2 mới chỉ có Mức 4
 * (bestFit thuần túy).
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
        int lengthMm = match.getKey();
        int remaining = match.getValue() - 1;
        if (remaining > 0) {
            stock.put(lengthMm, remaining);
        } else {
            stock.remove(lengthMm);
        }
        return Optional.of(lengthMm);
    }

    /** Chỉ dùng cho test — số thanh còn lại của 1 độ dài cụ thể, không dùng trong logic chính. */
    public int remainingCount(Long slatMaterialId, int lengthMm) {
        NavigableMap<Integer, Integer> stock = stockByMaterialId.get(slatMaterialId);
        return stock == null ? 0 : stock.getOrDefault(lengthMm, 0);
    }
}
