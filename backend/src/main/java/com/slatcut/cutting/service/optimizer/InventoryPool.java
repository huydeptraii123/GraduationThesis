package com.slatcut.cutting.service.optimizer;

import com.slatcut.cutting.domain.InventoryBatch;
import com.slatcut.cutting.domain.SlatMaterial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
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
            // bỏ qua ngay từ đây để TreeMap.ceilingEntry() ở findRestockFit() không bao giờ khớp nhầm vào
            // 1 độ dài đã hết thanh chỉ vì nó ngắn hơn độ dài thật sự còn hàng kế tiếp.
            if (batch.getSoThanh() == null || batch.getSoThanh() <= 0) {
                continue;
            }
            stockByMaterialId
                    .computeIfAbsent(batch.getSlatMaterial().getId(), id -> new TreeMap<>())
                    .merge(batch.getDoDaiThanhMm(), batch.getSoThanh(), Integer::sum);
        }
    }

    /**
     * Mức 4 — cắt để phần dư nhập lại được kho: tìm thanh NGẮN NHẤT vừa đủ dài để cắt
     * {@code cutLengthMm} <b>và</b> còn để lại phần dư <b>&gt; 3m</b>, trừ 1 thanh nếu tìm thấy.
     *
     * <p>Điều kiện "&gt; 3m" không phải tối ưu hóa mà là luật nghiệp vụ: phần dư rơi vào khoảng
     * 30cm–3m không đủ ngắn để bỏ qua cũng không đủ dài để tái sử dụng, nên doanh nghiệp không chấp
     * nhận tạo ra nó. Thanh tồn kho tuy đủ dài nhưng chỉ để lại phần dư trong khoảng đó thì KHÔNG
     * được đụng tới — để dành cho một đoạn khác khớp hơn ở lần chạy sau.
     *
     * <p>Vì {@link RemainderCategory#RESTOCK} là {@code remainder > 3000} (lớn hơn NGẶT), ngưỡng tra
     * cứu phải là {@code cutLengthMm + 3000 + 1}: thanh để lại đúng 3000mm vẫn bị phân loại
     * {@code WASTE} nên không đủ điều kiện.
     */
    public Optional<Integer> findRestockFit(SlatMaterial slatMaterial, int cutLengthMm) {
        NavigableMap<Integer, Integer> stock = stockByMaterialId.get(slatMaterial.getId());
        if (stock == null) {
            return Optional.empty();
        }
        Map.Entry<Integer, Integer> match = stock.ceilingEntry(cutLengthMm + RemainderCategory.RESTOCK_THRESHOLD_MM + 1);
        if (match == null) {
            return Optional.empty();
        }
        return Optional.of(consume(stock, match.getKey()));
    }

    /**
     * Mức 1 — khớp gần đúng: giống {@link #findRestockFit}, nhưng CHỈ tiêu thụ + trả về khi phần dư dự kiến
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

    /** Mức 2 — 1 thanh cắt ra {@code k} đoạn cùng độ dài (k≥2), tiêu thụ 1 thanh đó. */
    public record MultipleMatch(int stockLengthMm, int multiplier) {}

    /**
     * Mức 2 — cắt nhiều đoạn cùng độ dài trên một thanh. Duyệt tồn kho tăng dần theo độ dài, lấy thanh
     * NGẮN NHẤT mà trên đó cắt được {@code k ≥ 2} đoạn và phần dư còn lại <b>dưới 30cm</b> (kể cả 0).
     * Trên thanh đó cắt {@code k} lớn nhất có thể: {@code k = min(⌊độ dài thanh / cutLengthMm⌋,
     * availableCount)}, với {@code availableCount} là số đoạn cùng độ dài đang chờ trong hàng đợi (tính
     * cả X).
     *
     * <p>Không đòi thanh dài ĐÚNG bội số: thanh 3200mm cho hai đoạn 1500mm (dư 200mm, bỏ đi) cũng được
     * nhận. Phần dư từ 30cm trở lên thì bỏ qua thanh đó — trên 3m là việc của Mức 4, còn 30cm–3m là
     * vùng cấm. Không đụng vào tồn kho khi không tìm được.
     */
    public Optional<MultipleMatch> findMultipleOfSameLength(SlatMaterial slatMaterial, int cutLengthMm, int availableCount) {
        NavigableMap<Integer, Integer> stock = stockByMaterialId.get(slatMaterial.getId());
        if (stock == null) {
            return Optional.empty();
        }
        for (Map.Entry<Integer, Integer> entry : stock.entrySet()) {
            int lengthMm = entry.getKey();
            int multiplier = Math.min(lengthMm / cutLengthMm, availableCount);
            if (multiplier < 2) {
                continue;
            }
            if (lengthMm - multiplier * cutLengthMm >= RemainderCategory.DISCARD_THRESHOLD_MM) {
                continue;
            }
            consume(stock, lengthMm);
            return Optional.of(new MultipleMatch(lengthMm, multiplier));
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

    /**
     * Toàn bộ số thanh còn lại sau lần chạy, chỉ của những loại thanh nan được hỏi tới.
     *
     * <p>Đọc từ đây thay vì đọc lại CSDL sau khi trừ tồn kho: hai con số phải bằng nhau (cùng suy
     * ra từ một danh sách lát cắt), nhưng kho tạm này là nơi duy nhất biết cả phần dư trên 3m vừa
     * nhập lại giữa chừng lẫn những độ dài đã bị cắt hết sạch trong chính lần chạy.
     */
    public List<StockLine> remainingLines(Set<Long> slatMaterialIds) {
        List<StockLine> lines = new ArrayList<>();
        for (Map.Entry<Long, NavigableMap<Integer, Integer>> entry : stockByMaterialId.entrySet()) {
            if (!slatMaterialIds.contains(entry.getKey())) {
                continue;
            }
            for (Map.Entry<Integer, Integer> line : entry.getValue().entrySet()) {
                lines.add(new StockLine(entry.getKey(), line.getKey(), line.getValue()));
            }
        }
        return List.copyOf(lines);
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
