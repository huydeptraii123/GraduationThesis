package com.slatcut.cutting.service;

import com.slatcut.cutting.config.ResourceNotFoundException;
import com.slatcut.cutting.domain.CuttingPlanDetail;
import com.slatcut.cutting.domain.CuttingPlanDetailItem;
import com.slatcut.cutting.domain.CuttingPlanStockSnapshot;
import com.slatcut.cutting.domain.RemainderType;
import com.slatcut.cutting.domain.SalesOrder;
import com.slatcut.cutting.domain.ShortageRecord;
import com.slatcut.cutting.domain.SlatGroup;
import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.dto.CuttingPlanDemandView;
import com.slatcut.cutting.repository.CuttingPlanDetailItemRepository;
import com.slatcut.cutting.repository.CuttingPlanDetailRepository;
import com.slatcut.cutting.repository.CuttingPlanRepository;
import com.slatcut.cutting.repository.CuttingPlanStockSnapshotRepository;
import com.slatcut.cutting.repository.ShortageRecordRepository;
import com.slatcut.cutting.service.CuttingResultGrouping.CutGroup;
import com.slatcut.cutting.service.CuttingResultGrouping.CutItem;
import com.slatcut.cutting.service.optimizer.RemainderCategory;
import com.slatcut.cutting.service.optimizer.ShortageEntry;
import com.slatcut.cutting.service.optimizer.StockLine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dựng mức chi tiết theo đơn hàng — danh sách {@link CuttingPlanDemandView} — từ kết quả thuật
 * toán. Đứng riêng khỏi {@link CuttingPlanService} vì hai lớp trả lời hai câu hỏi khác nhau: lớp
 * kia quyết định cắt thế nào và có ghi hay không, lớp này chỉ trình bày lại một kết quả đã có.
 *
 * <p>Hai đường vào, một định dạng ra:
 *
 * <ul>
 *   <li>{@link #buildFromPreview} — kết quả một lần tính còn nằm trong bộ nhớ, chưa từng được lưu;
 *   <li>{@link #buildFromApprovedPlan} — một phương án đã duyệt, đọc lại từ cơ sở dữ liệu.
 * </ul>
 *
 * <p>Cả hai quy về cùng một phép gộp trung gian nên không thể lệch nhau — điều mà hai hàm dựng
 * riêng rẽ không bảo đảm được, và đó chính là rủi ro cần tránh: cùng một phương án, xem trên màn
 * hình trước khi duyệt và xuất Excel sau khi duyệt, phải ra đúng những con số như nhau.
 */
@Service
public class CuttingPlanReportService {

    private static final BigDecimal MM_PER_M = new BigDecimal(1000);

    /** Độ dài một đoạn cần chính xác tới centimet để thợ cắt đối chiếu. */
    private static final int PIECE_SCALE = 2;

    /** Tổng độ dài chỉ dùng để ước lượng khối lượng vật tư bù nên làm tròn thưa hơn. */
    private static final int TOTAL_SCALE = 1;

    /** Dấu thanh tái sử dụng — giữ nguyên ký tự của khuôn mẫu doanh nghiệp đang đối chiếu hằng ngày. */
    private static final String RECYCLED_MARK = "♻️";

    private final CuttingPlanRepository cuttingPlanRepository;
    private final CuttingPlanDetailRepository cuttingPlanDetailRepository;
    private final CuttingPlanDetailItemRepository cuttingPlanDetailItemRepository;
    private final ShortageRecordRepository shortageRecordRepository;
    private final CuttingPlanStockSnapshotRepository stockSnapshotRepository;

    public CuttingPlanReportService(
            CuttingPlanRepository cuttingPlanRepository,
            CuttingPlanDetailRepository cuttingPlanDetailRepository,
            CuttingPlanDetailItemRepository cuttingPlanDetailItemRepository,
            ShortageRecordRepository shortageRecordRepository,
            CuttingPlanStockSnapshotRepository stockSnapshotRepository) {
        this.cuttingPlanRepository = cuttingPlanRepository;
        this.cuttingPlanDetailRepository = cuttingPlanDetailRepository;
        this.cuttingPlanDetailItemRepository = cuttingPlanDetailItemRepository;
        this.shortageRecordRepository = shortageRecordRepository;
        this.stockSnapshotRepository = stockSnapshotRepository;
    }

    /**
     * Dựng báo cáo từ một lần tính chưa lưu.
     *
     * <p>Số thanh cần suy ra từ chính kết quả thuật toán (số đoạn đã cắt cộng số đoạn báo thiếu)
     * chứ không đếm lại từ danh sách nhu cầu đầu vào. Hai cách phải cho cùng một con số — mỗi đơn
     * vị nhu cầu rốt cuộc hoặc nằm trên một phôi hoặc thành một dòng thiếu vật tư, không có đường
     * thứ ba — nên chọn cách đọc được cho cả phương án đã lưu, nơi danh sách nhu cầu đầu vào không
     * còn tồn tại. Một con số, một chỗ tính.
     */
    public List<CuttingPlanDemandView> buildFromPreview(CuttingPlanPreview preview) {
        Map<OrderKey, SalesOrder> orderIndex = preview.scopeOrders().stream()
                .collect(Collectors.toMap(so -> new OrderKey(so.getYcsx(), so.getItem()), so -> so));
        StockSnapshot snapshot = StockSnapshot.ofStockLines(preview.stockAtStart());
        Map<Long, Map<Integer, Integer>> remainingByMaterial = indexByMaterial(preview.stockAfterRun());
        Map<DemandKey, Accumulator> rows = new LinkedHashMap<>();

        for (CutGroup group : CuttingResultGrouping.groupCuts(preview.result().cuts())) {
            Long materialId = group.slatMaterial().getId();
            // Độ dài đã bị cắt hết sạch không còn dòng nào trong kho tạm — đó là 0 thanh còn lại,
            // không phải "không biết"; phân biệt với null của phương án duyệt trước khi hệ thống
            // bắt đầu lưu con số này, nơi mệnh đề "còn lại" bị bỏ hẳn thay vì bịa ra số 0.
            int remainingAfter = remainingByMaterial
                    .getOrDefault(materialId, Map.of())
                    .getOrDefault(group.sourceLengthMm(), 0);
            StickKey stick = new StickKey(
                    group.sourceLengthMm(),
                    group.remainderMm(),
                    group.remainderCategory() == RemainderCategory.RESTOCK,
                    group.cutLevel().name(),
                    snapshot.isRecycled(materialId, group.sourceLengthMm()));

            Map<Accumulator, Integer> nanByRow = new LinkedHashMap<>();
            for (CutItem item : group.items()) {
                Accumulator row = add(
                        rows,
                        order(orderIndex, item.ycsx(), item.item()),
                        group.slatMaterial(),
                        item.cutLengthMm(),
                        true,
                        item.cutQuantity(),
                        0);
                nanByRow.merge(row, item.cutQuantity(), Integer::sum);
            }
            nanByRow.forEach((row, nanCount) -> row.addStickUse(stick, group.stickCount(), nanCount, remainingAfter));
        }
        for (ShortageEntry shortage : preview.result().shortages()) {
            CuttingDemand demand = shortage.demand();
            add(rows, order(orderIndex, demand.ycsx(), demand.item()), shortage.slatMaterial(), demand.cutLengthMm(), true, 1, 1);
        }
        return assemble(rows.values(), snapshot);
    }

    /**
     * Dựng báo cáo từ một phương án đã duyệt.
     *
     * <p>Bảng thiếu vật tư lưu TỔNG độ dài thiếu chứ không lưu độ dài từng đoạn, và lưu ở đơn vị
     * centimet, nên độ dài đoạn suy ngược ra từ đó bị làm tròn lên bội số của 10mm: một đoạn
     * 2345mm đọc lại thành 2350mm. Vì vậy con số suy ngược chỉ được dùng khi dòng đó thiếu TOÀN BỘ
     * — khi đó không còn nguồn nào khác, và sai số nằm dưới mức làm tròn mà báo cáo hiển thị. Dòng
     * thiếu một phần thì đã có những đoạn cắt được của chính nó mang độ dài chính xác tới
     * milimet, và độ dài đó được ưu tiên.
     */
    @Transactional(readOnly = true)
    public List<CuttingPlanDemandView> buildFromApprovedPlan(Long planId) {
        if (!cuttingPlanRepository.existsById(planId)) {
            throw new ResourceNotFoundException("Không tìm thấy phương án cắt với id=" + planId);
        }
        // Duyệt theo id tăng dần, tức đúng thứ tự các phôi đã được ghi xuống, tức đúng thứ tự thuật
        // toán dùng tới chúng. Cả hai truy vấn dưới đây đều không có ORDER BY nên thứ tự cơ sở dữ
        // liệu trả về không hứa hẹn gì; bám thứ tự trả về ấy thì một dòng cắt từ nhiều độ dài phôi
        // khác nhau có thể in ra các mệnh đề theo thứ tự khác với thứ tự người duyệt đã xem.
        List<CuttingPlanDetail> details = new ArrayList<>(cuttingPlanDetailRepository.findByCuttingPlan_Id(planId));
        details.sort(Comparator.comparing(CuttingPlanDetail::getId));
        StockSnapshot snapshot = StockSnapshot.ofSnapshotRows(stockSnapshotRepository.findByCuttingPlan_Id(planId));

        Map<DemandKey, Accumulator> rows = new LinkedHashMap<>();
        List<Long> detailIds = details.stream().map(CuttingPlanDetail::getId).toList();
        Map<Long, List<CuttingPlanDetailItem>> itemsByDetailId =
                cuttingPlanDetailItemRepository.findByCuttingPlanDetail_IdIn(detailIds).stream()
                        .collect(Collectors.groupingBy(
                                item -> item.getCuttingPlanDetail().getId(),
                                LinkedHashMap::new,
                                Collectors.toList()));
        for (CuttingPlanDetail detail : details) {
            SlatMaterial material = detail.getSlatMaterial();
            StickKey stick = new StickKey(
                    detail.getSourceLengthMm(),
                    detail.getRemainderMm(),
                    detail.getRemainderType() == RemainderType.RESTOCK,
                    detail.getCutLevel() == null ? null : detail.getCutLevel().name(),
                    snapshot.isRecycled(material.getId(), detail.getSourceLengthMm()));

            Map<Accumulator, Integer> nanByRow = new LinkedHashMap<>();
            for (CuttingPlanDetailItem item : itemsByDetailId.getOrDefault(detail.getId(), List.of())) {
                Accumulator row = add(
                        rows, item.getSalesOrder(), material, item.getCutLengthMm(), true, item.getCutQuantity(), 0);
                nanByRow.merge(row, item.getCutQuantity(), Integer::sum);
            }
            nanByRow.forEach((row, nanCount) ->
                    row.addStickUse(stick, detail.getStickCount(), nanCount, detail.getRemainingSticksAfter()));
        }
        for (ShortageRecord shortage : shortageRecordRepository.findByCuttingPlan_Id(planId)) {
            int quantity = shortage.getMissingQuantity();
            add(
                    rows,
                    shortage.getSalesOrder(),
                    shortage.getSlatMaterial(),
                    averageCutLengthMm(shortage),
                    false,
                    quantity,
                    quantity);
        }
        return assemble(rows.values(), snapshot);
    }

    private static int averageCutLengthMm(ShortageRecord shortage) {
        return shortage
                .getMissingLengthM()
                .multiply(MM_PER_M)
                .divide(BigDecimal.valueOf(shortage.getMissingQuantity()), 0, RoundingMode.HALF_UP)
                .intValueExact();
    }

    private static SalesOrder order(Map<OrderKey, SalesOrder> orderIndex, String ycsx, Integer item) {
        return orderIndex.get(new OrderKey(ycsx, item));
    }

    /** Gom danh sách dòng tồn kho phẳng thành tra cứu 2 tầng (loại vật tư → độ dài → số thanh). */
    private static Map<Long, Map<Integer, Integer>> indexByMaterial(List<StockLine> lines) {
        Map<Long, Map<Integer, Integer>> byMaterial = new HashMap<>();
        for (StockLine line : lines) {
            byMaterial
                    .computeIfAbsent(line.slatMaterialId(), id -> new HashMap<>())
                    .merge(line.lengthMm(), line.stickCount(), Integer::sum);
        }
        return byMaterial;
    }

    /**
     * Cộng dồn một phần đóng góp vào dòng báo cáo của cặp (bộ cửa, loại vật tư).
     *
     * @param exactLength {@code true} khi độ dài đoạn lấy được chính xác tới milimet. Độ dài chính
     *     xác luôn thắng độ dài suy ngược, bất kể phần nào được cộng vào trước — không dựa vào thứ
     *     tự gọi, vì một ngày nào đó thứ tự ấy sẽ đổi mà không ai nhớ ra đã có luật ngầm ở đây
     * @return dòng vừa được cộng vào, để người gọi ghi tiếp phần đóng góp của phôi vào cùng dòng đó
     */
    private static Accumulator add(
            Map<DemandKey, Accumulator> rows,
            SalesOrder order,
            SlatMaterial material,
            int cutLengthMm,
            boolean exactLength,
            int needed,
            int missing) {
        DemandKey key = new DemandKey(order.getYcsx(), order.getItem(), material.getId());
        Accumulator row =
                rows.computeIfAbsent(key, k -> new Accumulator(order, material, cutLengthMm, exactLength));
        row.needed += needed;
        row.missing += missing;
        if (exactLength && !row.exactLength) {
            row.cutLengthMm = cutLengthMm;
            row.exactLength = true;
        }
        return row;
    }

    /**
     * Sắp xếp, đánh hạng ưu tiên và chuyển thành danh sách trình bày.
     *
     * <p>Thứ tự dòng: gom theo loại vật tư rồi trong mỗi loại xếp theo đúng thứ tự ưu tiên mà thuật
     * toán đã xử lý. Đây là thứ tự mà thợ cắt đọc file — làm hết một loại thanh nan rồi mới sang
     * loại khác — và cũng làm cột hạng ưu tiên chạy liền 1, 2, 3 trong từng khối thay vì nhảy cóc.
     */
    private static List<CuttingPlanDemandView> assemble(
            Iterable<Accumulator> accumulators, StockSnapshot snapshot) {
        List<Accumulator> rows = new ArrayList<>();
        Set<OrderKey> shortDoorSets = new HashSet<>();
        for (Accumulator row : accumulators) {
            rows.add(row);
            if (row.missing > 0) {
                shortDoorSets.add(new OrderKey(row.order.getYcsx(), row.order.getItem()));
            }
        }
        rows.sort(Comparator.comparing((Accumulator r) -> r.material.getSlatMaterial())
                .thenComparing(r -> r.order.getReqdDeliveryDate())
                .thenComparing(r -> r.order.getYcsx())
                .thenComparing(r -> r.order.getItem())
                .thenComparing(r -> r.cutLengthMm));

        Map<Long, Integer> rankByMaterial = new HashMap<>();
        List<CuttingPlanDemandView> views = new ArrayList<>(rows.size());
        for (Accumulator row : rows) {
            int rank = rankByMaterial.merge(row.material.getId(), 1, Integer::sum);
            views.add(toView(row, rank, shortDoorSets, snapshot));
        }
        return views;
    }

    private static CuttingPlanDemandView toView(
            Accumulator row, int rank, Set<OrderKey> shortDoorSets, StockSnapshot snapshot) {
        SalesOrder order = row.order;
        boolean doorSetShort = shortDoorSets.contains(new OrderKey(order.getYcsx(), order.getItem()));
        return new CuttingPlanDemandView(
                rank,
                order.getYcsx(),
                order.getItem(),
                order.getLenhSx(),
                order.getSalesDocument(),
                order.getCustomer().getCustomerName(),
                order.getReqdDeliveryDate(),
                order.getDoorProduct().getDoorMaterialName(),
                order.getDoorProduct().getMaterialGroup(),
                row.material.getSlatMaterial(),
                row.material.getSlatMaterialName(),
                row.material.getSlatGroup(),
                sourceDimensionM(order, row.material.getSlatGroup()),
                row.cutLengthMm,
                row.needed,
                row.missing,
                statusText(row),
                cutDetailText(row),
                snapshot.textFor(row.material.getId()),
                doorSetShort ? CuttingPlanDemandView.DOOR_SET_SHORT : CuttingPlanDemandView.DOOR_SET_SUFFICIENT);
    }

    /**
     * Kích thước gốc mà đoạn cắt được tính ra từ đó. Ray cắt theo chiều cao cửa, mọi nhóm còn lại
     * cắt theo chiều rộng — đúng công thức nhu cầu cắt ở docs/sequence-diagrams.md. Sửa công thức
     * bên đó thì phải sửa cả đây, nếu không cột này mô tả sai nguồn gốc của đoạn cắt.
     */
    private static BigDecimal sourceDimensionM(SalesOrder order, SlatGroup slatGroup) {
        return slatGroup == SlatGroup.RAIL ? order.getChieuCaoDh() : order.getChieuRongDh();
    }

    /** Khuôn mẫu ở docs/sequence-diagrams.md mục "Cách sinh hai cột mô tả của báo cáo". */
    private static String statusText(Accumulator row) {
        if (row.missing == 0) {
            return "✔Đủ";
        }
        BigDecimal pieceM = toMeters(row.cutLengthMm, PIECE_SCALE);
        BigDecimal totalM = toMeters((long) row.cutLengthMm * row.missing, TOTAL_SCALE);
        return row.missing >= row.needed
                ? "Thiếu toàn bộ %d nan %sm (%sm)".formatted(row.needed, pieceM, totalM)
                : "Thiếu %d nan %sm (%sm)".formatted(row.missing, pieceM, totalM);
    }

    /**
     * Mô tả cách cắt thực tế — khuôn mẫu ở docs/sequence-diagrams.md mục "Cách sinh hai cột mô tả
     * của báo cáo". Mỗi nhóm phôi giống hệt nhau góp một mệnh đề, nối nhau bằng dấu chấm phẩy theo
     * đúng thứ tự thuật toán đã dùng tới chúng.
     *
     * <p>Trả về {@code null} khi dòng không có phôi nào — bộ cửa thiếu toàn bộ loại thanh nan đó.
     * Để trống chứ không viết một câu kiểu "không cắt được": cột trạng thái đáp ứng ngay bên cạnh
     * đã nói rõ thiếu bao nhiêu, và khuôn mẫu của doanh nghiệp cũng để trống ô này.
     */
    private static String cutDetailText(Accumulator row) {
        if (row.stickUses.isEmpty()) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        for (Map.Entry<StickKey, StickUse> entry : row.stickUses.entrySet()) {
            if (!text.isEmpty()) {
                text.append("; ");
            }
            text.append(clause(entry.getKey(), entry.getValue()));
        }
        return text.toString();
    }

    private static String clause(StickKey stick, StickUse use) {
        StringBuilder clause = new StringBuilder("[TP] ");
        if (stick.recycledSource()) {
            clause.append(RECYCLED_MARK);
        }
        clause.append(stick.sourceLengthMm())
                .append("mm: ")
                .append(use.stickCount)
                .append(" phôi → ")
                .append(use.nanCount)
                .append(" nan [")
                .append(remainderText(stick));
        if (stick.cutLevel() != null) {
            clause.append(", ").append(stick.cutLevel());
        }
        clause.append(']');
        if (use.remainingAfter != null) {
            clause.append(" (còn lại ").append(use.remainingAfter).append(" phôi)");
        }
        return clause.toString();
    }

    /**
     * Phần dư trên 3m quay lại kho nên ghi nguyên độ dài milimet — đó là con số thủ kho dùng để
     * dán nhãn thanh tái sử dụng. Mọi phần dư còn lại là phế, ghi theo mét như khuôn mẫu, kể cả khi
     * bằng đúng 0 ở phôi cắt theo bội số: mỗi mệnh đề đều nêu tường minh phần dư của nó thay vì bắt
     * người đọc suy ra từ sự vắng mặt của một con số.
     */
    private static String remainderText(StickKey stick) {
        return stick.restock()
                ? "Cắt để lại %s%dmm".formatted(RECYCLED_MARK, stick.remainderMm())
                : "Cắt phế %sm".formatted(toMeters(stick.remainderMm(), PIECE_SCALE));
    }

    private static BigDecimal toMeters(long lengthMm, int scale) {
        return BigDecimal.valueOf(lengthMm).divide(MM_PER_M, scale, RoundingMode.HALF_UP);
    }

    /**
     * Một nhóm phôi giống hệt nhau đã đóng góp cho một dòng báo cáo. Mức cắt nằm trong khóa vì hai
     * phôi cùng độ dài và cùng phần dư vẫn có thể đến từ hai mức khác nhau — gộp lại thì mệnh đề
     * nói sai phương án đã áp dụng cho một phần số phôi.
     *
     * <p>Mức cắt mang theo dạng tên (`PA1`…`PA4`) chứ không phải kiểu liệt kê: hai đường vào của
     * lớp này đọc hai kiểu khác nhau — kiểu của tầng thuật toán và kiểu của tầng domain — và báo
     * cáo chỉ cần đúng cái tên in ra. Quy về chuỗi ngay tại đây thì phần dựng câu chữ bên dưới
     * không phải biết tới cả hai.
     *
     * @param recycledSource phôi nguồn vốn là một phần dư đã nhập lại kho trong chính lần chạy này
     */
    private record StickKey(
            int sourceLengthMm, int remainderMm, boolean restock, String cutLevel, boolean recycledSource) {}

    /** Số phôi và số nan mà một {@link StickKey} đóng góp cho đúng một dòng báo cáo. */
    private static final class StickUse {
        private int stickCount;
        private int nanCount;
        private Integer remainingAfter;
    }

    /**
     * Tồn kho của từng loại thanh nan ngay TRƯỚC khi thuật toán tiêu thụ — nguồn của cột ảnh chụp
     * tồn kho, và cũng là thứ cho biết một phôi có phải thanh tái sử dụng hay không.
     *
     * <p>Đọc ảnh chụp thay vì đọc tồn kho hiện hành là yêu cầu nghiệp vụ: tồn kho đổi hằng ngày
     * nên tính lại thì cùng một phương án xuất ra ở hai thời điểm cho hai con số khác nhau, không
     * còn đối chiếu được với chứng từ đã phát hành.
     */
    private static final class StockSnapshot {

        private final Map<Long, NavigableLengths> byMaterialId;

        private StockSnapshot(Map<Long, NavigableLengths> byMaterialId) {
            this.byMaterialId = byMaterialId;
        }

        static StockSnapshot ofStockLines(List<StockLine> lines) {
            Map<Long, NavigableLengths> byMaterialId = new HashMap<>();
            for (StockLine line : lines) {
                byMaterialId
                        .computeIfAbsent(line.slatMaterialId(), id -> new NavigableLengths())
                        .add(line.lengthMm(), line.stickCount());
            }
            return new StockSnapshot(byMaterialId);
        }

        static StockSnapshot ofSnapshotRows(List<CuttingPlanStockSnapshot> rows) {
            Map<Long, NavigableLengths> byMaterialId = new HashMap<>();
            for (CuttingPlanStockSnapshot row : rows) {
                byMaterialId
                        .computeIfAbsent(row.getSlatMaterial().getId(), id -> new NavigableLengths())
                        .add(row.getDoDaiThanhMm(), row.getSoThanh());
            }
            return new StockSnapshot(byMaterialId);
        }

        /**
         * Một độ dài không có mặt trong tồn kho đầu lần chạy chỉ có thể đến từ phần dư vừa được
         * nhập lại kho giữa chừng — đó là dấu hiệu duy nhất phân biệt được thanh tái sử dụng mà
         * không cần thêm cột dữ liệu nào.
         *
         * <p>Hạn chế đã biết và chấp nhận: nếu phần dư trùng đúng một độ dài vốn đã có trong kho
         * thì hai loại thanh lẫn vào nhau và phôi không được đánh dấu. Phân biệt triệt để đòi hỏi
         * theo vết từng thanh vật lý, thứ nằm ngoài phạm vi của mô hình tồn kho theo lô.
         *
         * <p>Phương án được duyệt từ TRƯỚC khi hệ thống bắt đầu lưu ảnh chụp tồn kho không có dòng
         * nào để đối chiếu. Khi đó câu trả lời đúng là "không biết", và hàm này cố ý trả về "không
         * phải thanh tái sử dụng": thà bỏ sót một dấu hiệu còn hơn dán nhãn tái sử dụng lên những
         * phôi chưa hề được kiểm chứng. Người đọc vẫn nhận ra ngay tình huống này vì cột ảnh chụp
         * tồn kho của chính những dòng đó để trống.
         */
        boolean isRecycled(Long slatMaterialId, int lengthMm) {
            NavigableLengths lengths = byMaterialId.get(slatMaterialId);
            return lengths != null && !lengths.contains(lengthMm);
        }

        /** {@code "4.00m 7 thanh, 4.20m 13 thanh"} — sắp theo độ dài tăng dần như khuôn mẫu. */
        String textFor(Long slatMaterialId) {
            NavigableLengths lengths = byMaterialId.get(slatMaterialId);
            return lengths == null ? null : lengths.text();
        }
    }

    /** Các độ dài của một loại thanh nan, giữ thứ tự tăng dần để in ra đúng khuôn mẫu. */
    private static final class NavigableLengths {

        private final TreeMap<Integer, Integer> countByLength = new TreeMap<>();

        void add(int lengthMm, int stickCount) {
            countByLength.merge(lengthMm, stickCount, Integer::sum);
        }

        boolean contains(int lengthMm) {
            return countByLength.containsKey(lengthMm);
        }

        String text() {
            return countByLength.entrySet().stream()
                    .map(entry -> "%sm %d thanh".formatted(toMeters(entry.getKey(), PIECE_SCALE), entry.getValue()))
                    .collect(Collectors.joining(", "));
        }
    }

    /**
     * Một dòng báo cáo: một loại vật tư của một bộ cửa.
     *
     * <p>Độ dài đoạn KHÔNG nằm trong khóa dù mỗi dòng đều có một độ dài. Một bộ cửa chỉ có đúng
     * một dòng định mức cho mỗi mã thanh nan (khóa duy nhất của bảng định mức), nên cặp này đã xác
     * định độ dài đoạn — thêm nó vào khóa không tách được dòng nào ra, mà lại khiến hai nguồn số
     * liệu chênh nhau vài milimet do làm tròn bị xé thành hai dòng riêng, mỗi dòng mang một nửa số
     * lượng và một trạng thái sai.
     */
    private record DemandKey(String ycsx, Integer item, Long slatMaterialId) {}

    private record OrderKey(String ycsx, Integer item) {}

    /** Bộ đếm đang gom dở cho một {@link DemandKey} — đổi giá trị liên tục nên không dùng record. */
    private static final class Accumulator {
        private final SalesOrder order;
        private final SlatMaterial material;

        /** Giữ thứ tự thuật toán dùng tới từng nhóm phôi — đó là thứ tự các mệnh đề in ra. */
        private final Map<StickKey, StickUse> stickUses = new LinkedHashMap<>();

        private int cutLengthMm;
        private boolean exactLength;
        private int needed;
        private int missing;

        private Accumulator(SalesOrder order, SlatMaterial material, int cutLengthMm, boolean exactLength) {
            this.order = order;
            this.material = material;
            this.cutLengthMm = cutLengthMm;
            this.exactLength = exactLength;
        }

        /**
         * Ghi phần đóng góp của một nhóm phôi vào dòng này.
         *
         * @param remainingAfter số thanh còn lại của đúng cặp (loại vật tư, độ dài) sau lần chạy;
         *     {@code null} với phương án được duyệt từ trước khi hệ thống bắt đầu lưu con số này —
         *     khi đó mệnh đề "còn lại" bị bỏ hẳn thay vì in ra một số 0 không có thật
         */
        private void addStickUse(StickKey key, int stickCount, int nanCount, Integer remainingAfter) {
            StickUse use = stickUses.computeIfAbsent(key, k -> new StickUse());
            use.stickCount += stickCount;
            use.nanCount += nanCount;
            if (use.remainingAfter == null) {
                use.remainingAfter = remainingAfter;
            }
        }
    }
}
