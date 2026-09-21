package com.slatcut.cutting.service;

import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.service.optimizer.CutRecord;
import com.slatcut.cutting.service.optimizer.RemainderCategory;
import com.slatcut.cutting.service.optimizer.ShortageEntry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gộp kết quả thô của thuật toán thành đúng những dòng mà cả cơ sở dữ liệu lẫn màn hình đều dùng.
 * Thuần tính toán, không chạm repository nào.
 *
 * <p>Tách riêng khỏi {@link CuttingPlanService} vì từ nay có hai nơi cần đúng phép gộp này: bước
 * duyệt ghi nó xuống hai bảng con, còn màn hình duyệt phải trình bày y hệt kết quả đó <b>trước
 * khi</b> có bất cứ dòng nào được ghi. Nếu mỗi bên tự gộp lấy thì phương án PLANNER nhìn thấy và
 * phương án được lưu có thể khác nhau về cách nhóm phôi — đúng thứ không được phép lệch, vì người
 * duyệt chịu trách nhiệm trên cái họ đã xem.
 */
public final class CuttingResultGrouping {

    private static final BigDecimal MM_PER_M = new BigDecimal(1000);

    private CuttingResultGrouping() {}

    /**
     * Gộp các phôi giống hệt nhau thành một dòng, ghi số phôi thay vì tạo dòng mới. "Giống hệt"
     * nghĩa là cùng hình dạng cắt <b>và</b> cùng danh sách đoạn theo đúng thứ tự — đơn gốc phải
     * trùng nhau, không chỉ trùng tập đơn.
     */
    public static List<CutGroup> groupCuts(List<CutRecord> cuts) {
        Map<String, Builder> byGroupKey = new LinkedHashMap<>();
        for (CutRecord cut : cuts) {
            String patternCode = buildPatternCode(cut);
            Builder builder =
                    byGroupKey.computeIfAbsent(buildGroupKey(patternCode, cut), key -> new Builder(cut, patternCode));
            builder.stickCount++;
            builder.addPieces(cut.pieces());
        }
        List<CutGroup> groups = new ArrayList<>(byGroupKey.size());
        for (Builder builder : byGroupKey.values()) {
            groups.add(builder.build());
        }
        return List.copyOf(groups);
    }

    /**
     * 1 dòng cho mỗi (bộ cửa, loại thanh nan) — đúng khóa duy nhất của bảng thiếu vật tư, gộp mọi
     * đơn vị thiếu của cùng bộ cửa và cùng loại thanh.
     */
    public static List<ShortageGroup> groupShortages(List<ShortageEntry> shortages) {
        Map<ShortageKey, int[]> totalsByKey = new LinkedHashMap<>();
        Map<ShortageKey, ShortageEntry> firstByKey = new LinkedHashMap<>();
        for (ShortageEntry shortage : shortages) {
            CuttingDemand demand = shortage.demand();
            ShortageKey key = new ShortageKey(demand.ycsx(), demand.item(), shortage.slatMaterial().getId());
            firstByKey.putIfAbsent(key, shortage);
            int[] totals = totalsByKey.computeIfAbsent(key, k -> new int[2]);
            totals[0]++;
            totals[1] += demand.cutLengthMm();
        }

        List<ShortageGroup> groups = new ArrayList<>(totalsByKey.size());
        for (Map.Entry<ShortageKey, int[]> entry : totalsByKey.entrySet()) {
            ShortageEntry first = firstByKey.get(entry.getKey());
            groups.add(new ShortageGroup(
                    first.demand().ycsx(),
                    first.demand().item(),
                    first.slatMaterial(),
                    entry.getValue()[0],
                    toMeters(entry.getValue()[1])));
        }
        return List.copyOf(groups);
    }

    /** Chuỗi hình học thuần túy (không chứa thông tin đơn hàng): "{nguồn}={dài}x{sl}+...+R{dư}", sắp giảm dần theo độ dài đoạn. */
    private static String buildPatternCode(CutRecord cut) {
        Map<Integer, Long> countsByLength = new LinkedHashMap<>();
        for (CuttingDemand piece : cut.pieces()) {
            countsByLength.merge(piece.cutLengthMm(), 1L, Long::sum);
        }
        String segments = countsByLength.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByKey().reversed())
                .map(entry -> entry.getKey() + "x" + entry.getValue())
                .reduce((left, right) -> left + "+" + right)
                .orElse("");
        return cut.stockLengthMm() + "=" + segments + "+R" + cut.remainderMm();
    }

    /** patternCode + danh sách TUẦN TỰ (ycsx,item,cutLengthMm) — 2 phôi chỉ gộp khi giống hệt cả thứ tự (đơn gốc trùng nhau). */
    private static String buildGroupKey(String patternCode, CutRecord cut) {
        StringBuilder key = new StringBuilder(patternCode);
        for (CuttingDemand piece : cut.pieces()) {
            key.append('|').append(piece.ycsx()).append('#').append(piece.item()).append('#').append(piece.cutLengthMm());
        }
        return key.toString();
    }

    private static BigDecimal toMeters(int lengthMm) {
        return BigDecimal.valueOf(lengthMm).divide(MM_PER_M, 2, RoundingMode.HALF_UP);
    }

    /**
     * Một dòng phương án cắt: một hình dạng cắt, lặp lại {@code stickCount} lần trên những phôi
     * giống hệt nhau.
     */
    public record CutGroup(
            SlatMaterial slatMaterial,
            int sourceLengthMm,
            String patternCode,
            int remainderMm,
            RemainderCategory remainderCategory,
            int stickCount,
            List<CutItem> items) {}

    /**
     * Một đoạn cắt trên phôi: của bộ cửa nào, dài bao nhiêu, mấy đoạn.
     *
     * @param originalOrder đoạn "gốc" mà thuật toán đang xử lý khi chọn phôi này; các đoạn còn lại
     *     là đoạn ghép thêm ở Mức 2 hoặc Mức 3. Đúng một đoạn trên mỗi phôi mang cờ này
     */
    public record CutItem(String ycsx, Integer item, int cutLengthMm, int cutQuantity, boolean originalOrder) {}

    /** Một dòng thiếu vật tư: bộ cửa nào thiếu bao nhiêu thanh của loại nào, tổng bấy nhiêu mét. */
    public record ShortageGroup(
            String ycsx, Integer item, SlatMaterial slatMaterial, int missingQuantity, BigDecimal missingLengthM) {}

    private record ItemKey(String ycsx, Integer item, int cutLengthMm) {}

    private record ShortageKey(String ycsx, Integer item, Long slatMaterialId) {}

    /** Bộ gom dở cho một dòng phương án cắt — đổi giá trị liên tục nên không dùng record. */
    private static final class Builder {
        private final CutRecord first;
        private final String patternCode;
        private final Map<ItemKey, Integer> quantities = new LinkedHashMap<>();
        private ItemKey originalKey;
        private int stickCount;

        private Builder(CutRecord first, String patternCode) {
            this.first = first;
            this.patternCode = patternCode;
        }

        private void addPieces(List<CuttingDemand> pieces) {
            for (int i = 0; i < pieces.size(); i++) {
                CuttingDemand piece = pieces.get(i);
                ItemKey key = new ItemKey(piece.ycsx(), piece.item(), piece.cutLengthMm());
                quantities.merge(key, 1, Integer::sum);
                if (i == 0 && originalKey == null) {
                    originalKey = key;
                }
            }
        }

        private CutGroup build() {
            List<CutItem> items = new ArrayList<>(quantities.size());
            for (Map.Entry<ItemKey, Integer> entry : quantities.entrySet()) {
                ItemKey key = entry.getKey();
                items.add(new CutItem(
                        key.ycsx(), key.item(), key.cutLengthMm(), entry.getValue(), key.equals(originalKey)));
            }
            return new CutGroup(
                    first.slatMaterial(),
                    first.stockLengthMm(),
                    patternCode,
                    first.remainderMm(),
                    first.remainderCategory(),
                    stickCount,
                    List.copyOf(items));
        }
    }
}
