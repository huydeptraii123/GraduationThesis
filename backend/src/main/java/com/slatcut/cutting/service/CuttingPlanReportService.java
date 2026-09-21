package com.slatcut.cutting.service;

import com.slatcut.cutting.config.ResourceNotFoundException;
import com.slatcut.cutting.domain.CuttingPlanDetail;
import com.slatcut.cutting.domain.CuttingPlanDetailItem;
import com.slatcut.cutting.domain.SalesOrder;
import com.slatcut.cutting.domain.ShortageRecord;
import com.slatcut.cutting.domain.SlatGroup;
import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.dto.CuttingPlanDemandView;
import com.slatcut.cutting.repository.CuttingPlanDetailItemRepository;
import com.slatcut.cutting.repository.CuttingPlanDetailRepository;
import com.slatcut.cutting.repository.CuttingPlanRepository;
import com.slatcut.cutting.repository.ShortageRecordRepository;
import com.slatcut.cutting.service.optimizer.CutRecord;
import com.slatcut.cutting.service.optimizer.ShortageEntry;
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

    private final CuttingPlanRepository cuttingPlanRepository;
    private final CuttingPlanDetailRepository cuttingPlanDetailRepository;
    private final CuttingPlanDetailItemRepository cuttingPlanDetailItemRepository;
    private final ShortageRecordRepository shortageRecordRepository;

    public CuttingPlanReportService(
            CuttingPlanRepository cuttingPlanRepository,
            CuttingPlanDetailRepository cuttingPlanDetailRepository,
            CuttingPlanDetailItemRepository cuttingPlanDetailItemRepository,
            ShortageRecordRepository shortageRecordRepository) {
        this.cuttingPlanRepository = cuttingPlanRepository;
        this.cuttingPlanDetailRepository = cuttingPlanDetailRepository;
        this.cuttingPlanDetailItemRepository = cuttingPlanDetailItemRepository;
        this.shortageRecordRepository = shortageRecordRepository;
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
        Map<DemandKey, Accumulator> rows = new LinkedHashMap<>();

        for (CutRecord cut : preview.result().cuts()) {
            for (CuttingDemand piece : cut.pieces()) {
                add(rows, order(orderIndex, piece), piece.slatMaterial(), piece.cutLengthMm(), true, 1, 0);
            }
        }
        for (ShortageEntry shortage : preview.result().shortages()) {
            CuttingDemand demand = shortage.demand();
            add(rows, order(orderIndex, demand), shortage.slatMaterial(), demand.cutLengthMm(), true, 1, 1);
        }
        return assemble(rows.values());
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
        List<CuttingPlanDetail> details = cuttingPlanDetailRepository.findByCuttingPlan_Id(planId);
        Map<Long, SlatMaterial> materialByDetailId =
                details.stream().collect(Collectors.toMap(CuttingPlanDetail::getId, CuttingPlanDetail::getSlatMaterial));

        Map<DemandKey, Accumulator> rows = new LinkedHashMap<>();
        List<Long> detailIds = details.stream().map(CuttingPlanDetail::getId).toList();
        for (CuttingPlanDetailItem item : cuttingPlanDetailItemRepository.findByCuttingPlanDetail_IdIn(detailIds)) {
            SlatMaterial material = materialByDetailId.get(item.getCuttingPlanDetail().getId());
            add(rows, item.getSalesOrder(), material, item.getCutLengthMm(), true, item.getCutQuantity(), 0);
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
        return assemble(rows.values());
    }

    private static int averageCutLengthMm(ShortageRecord shortage) {
        return shortage
                .getMissingLengthM()
                .multiply(MM_PER_M)
                .divide(BigDecimal.valueOf(shortage.getMissingQuantity()), 0, RoundingMode.HALF_UP)
                .intValueExact();
    }

    private static SalesOrder order(Map<OrderKey, SalesOrder> orderIndex, CuttingDemand demand) {
        return orderIndex.get(new OrderKey(demand.ycsx(), demand.item()));
    }

    /**
     * Cộng dồn một phần đóng góp vào dòng báo cáo của cặp (bộ cửa, loại vật tư).
     *
     * @param exactLength {@code true} khi độ dài đoạn lấy được chính xác tới milimet. Độ dài chính
     *     xác luôn thắng độ dài suy ngược, bất kể phần nào được cộng vào trước — không dựa vào thứ
     *     tự gọi, vì một ngày nào đó thứ tự ấy sẽ đổi mà không ai nhớ ra đã có luật ngầm ở đây
     */
    private static void add(
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
    }

    /**
     * Sắp xếp, đánh hạng ưu tiên và chuyển thành danh sách trình bày.
     *
     * <p>Thứ tự dòng: gom theo loại vật tư rồi trong mỗi loại xếp theo đúng thứ tự ưu tiên mà thuật
     * toán đã xử lý. Đây là thứ tự mà thợ cắt đọc file — làm hết một loại thanh nan rồi mới sang
     * loại khác — và cũng làm cột hạng ưu tiên chạy liền 1, 2, 3 trong từng khối thay vì nhảy cóc.
     */
    private static List<CuttingPlanDemandView> assemble(Iterable<Accumulator> accumulators) {
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
            views.add(toView(row, rank, shortDoorSets));
        }
        return views;
    }

    private static CuttingPlanDemandView toView(Accumulator row, int rank, Set<OrderKey> shortDoorSets) {
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
                null,
                null,
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

    private static BigDecimal toMeters(long lengthMm, int scale) {
        return BigDecimal.valueOf(lengthMm).divide(MM_PER_M, scale, RoundingMode.HALF_UP);
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
    }
}
