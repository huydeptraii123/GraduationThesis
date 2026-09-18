package com.slatcut.cutting.service;

import com.slatcut.cutting.config.ResourceNotFoundException;
import com.slatcut.cutting.domain.CuttingPlan;
import com.slatcut.cutting.domain.CuttingPlanDetail;
import com.slatcut.cutting.domain.CuttingPlanDetailItem;
import com.slatcut.cutting.domain.CuttingPlanStatus;
import com.slatcut.cutting.domain.InventoryBatch;
import com.slatcut.cutting.domain.RemainderType;
import com.slatcut.cutting.domain.SalesOrder;
import com.slatcut.cutting.domain.ShortageRecord;
import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.dto.CuttingPlanDetailItemResponse;
import com.slatcut.cutting.dto.CuttingPlanDetailResponse;
import com.slatcut.cutting.dto.CuttingPlanResponse;
import com.slatcut.cutting.dto.CuttingPlanScopePreviewResponse;
import com.slatcut.cutting.dto.CuttingPlanSummaryResponse;
import com.slatcut.cutting.dto.ShortageRecordResponse;
import com.slatcut.cutting.mapper.CuttingPlanMapper;
import com.slatcut.cutting.repository.CuttingPlanDetailItemRepository;
import com.slatcut.cutting.repository.CuttingPlanDetailRepository;
import com.slatcut.cutting.repository.CuttingPlanRepository;
import com.slatcut.cutting.repository.InventoryBatchRepository;
import com.slatcut.cutting.repository.SalesOrderRepository;
import com.slatcut.cutting.repository.ShortageRecordRepository;
import com.slatcut.cutting.service.optimizer.CutRecord;
import com.slatcut.cutting.service.optimizer.CuttingPlanResult;
import com.slatcut.cutting.service.optimizer.CuttingStrategy;
import com.slatcut.cutting.service.optimizer.InventoryPool;
import com.slatcut.cutting.service.optimizer.RemainderCategory;
import com.slatcut.cutting.service.optimizer.ShortageEntry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nối CuttingDemandService (7.3) và CuttingStrategy (8.1-8.4) thành 1 luồng thật, đúng phạm vi đợt
 * xử lý đã chốt ở docs/requirements-functional.md Nhóm 3: chỉ SalesOrder chưa từng có kết quả cắt
 * nào (chưa có CuttingPlanDetailItem lẫn ShortageRecord tham chiếu tới — "nhóm 99" không cần cột
 * trạng thái riêng, suy ra động qua SalesOrderRepository.findUnprocessedInScope), reqdDeliveryDate
 * &lt;= t+3, tối đa 70 đơn.
 */
@Service
public class CuttingPlanService {

    private static final int SCOPE_CUTOFF_DAYS = 3;
    private static final int SCOPE_MAX_ORDERS = 70;
    private static final BigDecimal MM_PER_M = new BigDecimal(1000);

    private final SalesOrderRepository salesOrderRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final CuttingDemandService cuttingDemandService;
    private final CuttingStrategy cuttingStrategy;
    private final CuttingPlanRepository cuttingPlanRepository;
    private final CuttingPlanDetailRepository cuttingPlanDetailRepository;
    private final CuttingPlanDetailItemRepository cuttingPlanDetailItemRepository;
    private final ShortageRecordRepository shortageRecordRepository;
    private final CuttingPlanMapper mapper;

    public CuttingPlanService(
            SalesOrderRepository salesOrderRepository,
            InventoryBatchRepository inventoryBatchRepository,
            CuttingDemandService cuttingDemandService,
            CuttingStrategy cuttingStrategy,
            CuttingPlanRepository cuttingPlanRepository,
            CuttingPlanDetailRepository cuttingPlanDetailRepository,
            CuttingPlanDetailItemRepository cuttingPlanDetailItemRepository,
            ShortageRecordRepository shortageRecordRepository,
            CuttingPlanMapper mapper) {
        this.salesOrderRepository = salesOrderRepository;
        this.inventoryBatchRepository = inventoryBatchRepository;
        this.cuttingDemandService = cuttingDemandService;
        this.cuttingStrategy = cuttingStrategy;
        this.cuttingPlanRepository = cuttingPlanRepository;
        this.cuttingPlanDetailRepository = cuttingPlanDetailRepository;
        this.cuttingPlanDetailItemRepository = cuttingPlanDetailItemRepository;
        this.shortageRecordRepository = shortageRecordRepository;
        this.mapper = mapper;
    }

    @Transactional
    public CuttingPlan generate() {
        LocalDate cutoffDate = scopeCutoffDate();
        List<SalesOrder> scopeOrders = findScopeOrders(cutoffDate);
        Map<OrderKey, SalesOrder> orderIndex = scopeOrders.stream()
                .collect(Collectors.toMap(so -> new OrderKey(so.getYcsx(), so.getItem()), so -> so));

        List<CuttingDemand> demands = cuttingDemandService.buildDemands(scopeOrders);
        InventoryPool pool = new InventoryPool(inventoryBatchRepository.findAll());
        CuttingPlanResult result = cuttingStrategy.computePlan(demands, pool);

        CuttingPlan plan = new CuttingPlan();
        plan.setRunAt(LocalDateTime.now());
        plan.setStatus(CuttingPlanStatus.COMPLETED);
        plan.setScopeCutoffDate(cutoffDate);
        plan.setScopeOrderCount(scopeOrders.size());
        plan.setTotalWasteM(totalWasteM(result.cuts()));
        plan.setTotalStockUsedM(totalStockUsedM(result.cuts()));
        cuttingPlanRepository.save(plan);

        persistCuts(plan, result.cuts(), orderIndex);
        persistShortages(plan, result.shortages(), orderIndex);
        applyInventoryChanges(result.cuts());
        return plan;
    }

    /**
     * Ghi lại tồn kho sau khi cắt, trong đúng transaction đang chạy (docs/domain-model.md dòng 291,
     * docs/sequence-diagrams.md mục "trừ tồn kho sau khi cắt"): mỗi CutRecord tiêu thụ 1 phôi ở
     * (slatMaterial, stockLengthMm), phần dư RESTOCK nhập lại 1 thanh ở (slatMaterial, remainderMm).
     *
     * <p>Delta suy ra thẳng từ danh sách CutRecord thay vì so sánh trạng thái InventoryPool: phần dư
     * &gt;3m được {@code pool.restock()} giữa chừng rồi bị cắt tiếp ngay trong cùng lượt chạy sẽ xuất
     * hiện 1 lần +1 (lúc nhập lại) và 1 lần -1 (lúc dùng làm phôi nguồn) — cộng dồn ra 0, đúng thực
     * tế vật lý là thanh đó chưa từng rời xưởng.
     */
    private void applyInventoryChanges(List<CutRecord> cuts) {
        Map<StockKey, Integer> deltas = new LinkedHashMap<>();
        Map<Long, SlatMaterial> materialsById = new LinkedHashMap<>();
        for (CutRecord cut : cuts) {
            SlatMaterial slatMaterial = cut.slatMaterial();
            materialsById.putIfAbsent(slatMaterial.getId(), slatMaterial);
            deltas.merge(new StockKey(slatMaterial.getId(), cut.stockLengthMm()), -1, Integer::sum);
            if (cut.remainderCategory() == RemainderCategory.RESTOCK) {
                deltas.merge(new StockKey(slatMaterial.getId(), cut.remainderMm()), 1, Integer::sum);
            }
        }

        for (Map.Entry<StockKey, Integer> entry : deltas.entrySet()) {
            int delta = entry.getValue();
            if (delta == 0) {
                continue;
            }
            StockKey key = entry.getKey();
            InventoryBatch batch = inventoryBatchRepository
                    .findBySlatMaterial_IdAndDoDaiThanhMm(key.slatMaterialId(), key.lengthMm())
                    .orElseGet(() -> newBatchFor(materialsById.get(key.slatMaterialId()), key.lengthMm()));
            batch.setSoThanh(batch.getSoThanh() + delta);
            inventoryBatchRepository.save(batch);
        }
    }

    /** Độ dài phần dư nhập lại kho có thể chưa từng tồn tại thành lô riêng — tạo dòng mới với 0 thanh rồi mới cộng delta. */
    private static InventoryBatch newBatchFor(SlatMaterial slatMaterial, int lengthMm) {
        InventoryBatch batch = new InventoryBatch();
        batch.setSlatMaterial(slatMaterial);
        batch.setDoDaiThanhMm(lengthMm);
        batch.setSoThanh(0);
        return batch;
    }

    /** Không lưu gì — chỉ đếm trước theo đúng quy tắc phạm vi của {@link #generate()}, phục vụ modal xác nhận ở FE. */
    @Transactional(readOnly = true)
    public CuttingPlanScopePreviewResponse getScopePreview() {
        LocalDate cutoffDate = scopeCutoffDate();
        int eligibleOrderCount = findScopeOrders(cutoffDate).size();
        return new CuttingPlanScopePreviewResponse(eligibleOrderCount, cutoffDate);
    }

    private LocalDate scopeCutoffDate() {
        return LocalDate.now().plusDays(SCOPE_CUTOFF_DAYS);
    }

    private List<SalesOrder> findScopeOrders(LocalDate cutoffDate) {
        return salesOrderRepository.findUnprocessedInScope(cutoffDate, PageRequest.of(0, SCOPE_MAX_ORDERS));
    }

    @Transactional(readOnly = true)
    public List<CuttingPlanSummaryResponse> getSummaries() {
        return cuttingPlanRepository.findAllByOrderByRunAtDesc().stream()
                .map(mapper::toSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CuttingPlanResponse getById(Long id) {
        CuttingPlan plan = cuttingPlanRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phương án cắt với id=" + id));

        List<CuttingPlanDetail> details = cuttingPlanDetailRepository.findByCuttingPlan_Id(id);
        List<Long> detailIds = details.stream().map(CuttingPlanDetail::getId).toList();
        Map<Long, List<CuttingPlanDetailItemResponse>> itemsByDetailId =
                cuttingPlanDetailItemRepository.findByCuttingPlanDetail_IdIn(detailIds).stream()
                        .collect(Collectors.groupingBy(
                                item -> item.getCuttingPlanDetail().getId(),
                                Collectors.mapping(mapper::toItemResponse, Collectors.toList())));
        List<CuttingPlanDetailResponse> detailResponses = details.stream()
                .map(detail -> mapper.toDetailResponse(detail, itemsByDetailId.getOrDefault(detail.getId(), List.of())))
                .toList();

        List<ShortageRecordResponse> shortageResponses = shortageRecordRepository.findByCuttingPlan_Id(id).stream()
                .map(mapper::toShortageResponse)
                .toList();

        return mapper.toResponse(plan, detailResponses, shortageResponses);
    }

    /** Đúng công thức "tỷ lệ phế" ở docs/requirements-functional.md dòng 11: tổng "bỏ" + "lãng phí", KHÔNG tính RESTOCK. */
    private BigDecimal totalWasteM(List<CutRecord> cuts) {
        int totalMm = cuts.stream()
                .filter(cut -> cut.remainderCategory() == RemainderCategory.DISCARDED
                        || cut.remainderCategory() == RemainderCategory.WASTE)
                .mapToInt(CutRecord::remainderMm)
                .sum();
        return toMeters(totalMm);
    }

    /**
     * Tổng độ dài tồn kho THỰC TIÊU HAO trong lần chạy — mẫu số của "tỷ lệ phế" hiển thị ở FE:
     * tổng độ dài phôi xuất kho trừ đi phần dư được nhập lại kho (RESTOCK), tức đúng bằng
     * {@code tổng độ dài các đoạn đã cắt + phần "bỏ" + phần "lãng phí"}.
     *
     * <p>Không cộng thẳng {@code stockLengthMm} của mọi CutRecord: phần dư &gt;3m được
     * {@code pool.restock()} giữa chừng rồi bị cắt tiếp ngay trong cùng lượt chạy sẽ bị đếm hai lần
     * (một lần nằm trong thanh nguồn ban đầu, một lần với tư cách phôi nguồn của lát cắt sau), làm
     * mẫu số phồng lên và tỷ lệ phế hiển thị thấp hơn thực tế. Trừ phần RESTOCK triệt tiêu đúng
     * phần đếm trùng đó — cùng cách suy delta đã dùng ở {@link #applyInventoryChanges(List)}.
     */
    private BigDecimal totalStockUsedM(List<CutRecord> cuts) {
        int totalMm = cuts.stream()
                .mapToInt(cut -> cut.remainderCategory() == RemainderCategory.RESTOCK
                        ? cut.stockLengthMm() - cut.remainderMm()
                        : cut.stockLengthMm())
                .sum();
        return toMeters(totalMm);
    }

    /**
     * Gộp CutRecord thành CuttingPlanDetail: 2 bản ghi cắt cùng patternCode và cùng tập đơn hàng
     * phân bổ (theo đúng thứ tự — phần tử đầu luôn là đơn gốc) được gộp vào 1 dòng, tăng stickCount
     * thay vì tạo dòng mới (docs/domain-model.md dòng 348 — bất biến do tầng Service đảm bảo).
     */
    private void persistCuts(CuttingPlan plan, List<CutRecord> cuts, Map<OrderKey, SalesOrder> orderIndex) {
        Map<String, DetailGroup> detailsByGroupKey = new LinkedHashMap<>();
        for (CutRecord cut : cuts) {
            String patternCode = buildPatternCode(cut);
            String groupKey = buildGroupKey(patternCode, cut);
            DetailGroup group = detailsByGroupKey.get(groupKey);
            if (group != null) {
                group.detail().setStickCount(group.detail().getStickCount() + 1);
                cuttingPlanDetailRepository.save(group.detail());
                mergePieces(group, cut.pieces());
                continue;
            }

            CuttingPlanDetail detail = new CuttingPlanDetail();
            detail.setCuttingPlan(plan);
            detail.setSlatMaterial(cut.slatMaterial());
            detail.setSourceLengthMm(cut.stockLengthMm());
            detail.setPatternCode(patternCode);
            detail.setRemainderMm(cut.remainderMm());
            detail.setRemainderType(RemainderType.valueOf(cut.remainderCategory().name()));
            detail.setStickCount(1);
            cuttingPlanDetailRepository.save(detail);

            DetailGroup newGroup = new DetailGroup(detail, new LinkedHashMap<>());
            detailsByGroupKey.put(groupKey, newGroup);
            persistItems(newGroup, cut.pieces(), orderIndex);
        }
    }

    /**
     * 1 dòng/(ycsx,item,cutLengthMm) trên 1 phôi — nhiều đơn vị cùng đơn+cùng độ dài (Mức 2 tự ghép)
     * gộp vào cutQuantity. Tạo mới CuttingPlanDetailItem cho lần xuất hiện đầu tiên của groupKey.
     */
    private void persistItems(DetailGroup group, List<CuttingDemand> pieces, Map<OrderKey, SalesOrder> orderIndex) {
        PieceGroups pieceGroups = groupPieces(pieces);
        for (Map.Entry<ItemKey, Integer> entry : pieceGroups.quantities().entrySet()) {
            ItemKey key = entry.getKey();
            CuttingPlanDetailItem item = new CuttingPlanDetailItem();
            item.setCuttingPlanDetail(group.detail());
            item.setSalesOrder(orderIndex.get(new OrderKey(key.ycsx(), key.item())));
            item.setCutLengthMm(key.cutLengthMm());
            item.setCutQuantity(entry.getValue());
            item.setOriginalOrder(key.equals(pieceGroups.originalKey()));
            cuttingPlanDetailItemRepository.save(item);
            group.items().put(key, item);
        }
    }

    /**
     * Cùng groupKey (patternCode + đúng thứ tự (ycsx,item,cutLengthMm)) nghĩa là stick vừa gộp mang
     * ĐÚNG cùng tập piece như stick đầu tiên đã tạo item — cộng dồn cutQuantity thay vì tạo dòng mới,
     * khắc phục bug đã phát hiện qua review: bỏ qua persistItems() khi gộp làm cutQuantity bị đứng
     * yên ở giá trị của stick đầu tiên, sai với số lượng thật đã cắt cho đơn đó (RAIL quantity=2,
     * Mức 2 "15 thanh 6m cắt đôi" ở docs/domain-model.md dòng 348).
     */
    private void mergePieces(DetailGroup group, List<CuttingDemand> pieces) {
        PieceGroups pieceGroups = groupPieces(pieces);
        for (Map.Entry<ItemKey, Integer> entry : pieceGroups.quantities().entrySet()) {
            CuttingPlanDetailItem item = group.items().get(entry.getKey());
            item.setCutQuantity(item.getCutQuantity() + entry.getValue());
            cuttingPlanDetailItemRepository.save(item);
        }
    }

    private static PieceGroups groupPieces(List<CuttingDemand> pieces) {
        Map<ItemKey, Integer> quantities = new LinkedHashMap<>();
        ItemKey originalKey = null;
        for (int i = 0; i < pieces.size(); i++) {
            CuttingDemand piece = pieces.get(i);
            ItemKey key = new ItemKey(piece.ycsx(), piece.item(), piece.cutLengthMm());
            quantities.merge(key, 1, Integer::sum);
            if (i == 0) {
                originalKey = key;
            }
        }
        return new PieceGroups(quantities, originalKey);
    }

    /** 1 dòng/(ycsx,item,slatMaterial) theo đúng UNIQUE của shortage_record — gộp mọi đơn vị thiếu cùng đơn+cùng loại thanh. */
    private void persistShortages(CuttingPlan plan, List<ShortageEntry> shortages, Map<OrderKey, SalesOrder> orderIndex) {
        Map<ShortageKey, List<ShortageEntry>> grouped = shortages.stream()
                .collect(Collectors.groupingBy(
                        s -> new ShortageKey(s.demand().ycsx(), s.demand().item(), s.slatMaterial().getId()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        for (Map.Entry<ShortageKey, List<ShortageEntry>> entry : grouped.entrySet()) {
            ShortageKey key = entry.getKey();
            List<ShortageEntry> group = entry.getValue();
            int missingQuantity = group.size();
            int missingLengthMm =
                    group.stream().mapToInt(s -> s.demand().cutLengthMm()).sum();

            ShortageRecord record = new ShortageRecord();
            record.setCuttingPlan(plan);
            record.setSalesOrder(orderIndex.get(new OrderKey(key.ycsx(), key.item())));
            record.setSlatMaterial(group.get(0).slatMaterial());
            record.setMissingQuantity(missingQuantity);
            record.setMissingLengthM(toMeters(missingLengthMm));
            shortageRecordRepository.save(record);
        }
    }

    /** Chuỗi hình học thuần túy (không chứa thông tin đơn hàng): "{nguồn}={dài}x{sl}+...+R{dư}", sắp giảm dần theo độ dài đoạn. */
    private static String buildPatternCode(CutRecord cut) {
        Map<Integer, Long> countsByLength = cut.pieces().stream()
                .collect(Collectors.groupingBy(CuttingDemand::cutLengthMm, LinkedHashMap::new, Collectors.counting()));
        String segments = countsByLength.entrySet().stream()
                .sorted(Map.Entry.<Integer, Long>comparingByKey().reversed())
                .map(e -> e.getKey() + "x" + e.getValue())
                .collect(Collectors.joining("+"));
        return cut.stockLengthMm() + "=" + segments + "+R" + cut.remainderMm();
    }

    /** patternCode + danh sách TUẦN TỰ (ycsx,item,cutLengthMm) — 2 CutRecord chỉ gộp khi giống hệt cả thứ tự (đơn gốc trùng nhau). */
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

    private record OrderKey(String ycsx, Integer item) {}

    private record ItemKey(String ycsx, Integer item, int cutLengthMm) {}

    private record ShortageKey(String ycsx, Integer item, Long slatMaterialId) {}

    /** 1 dòng inventory_batch — gom delta theo khóa này để mỗi dòng chỉ đọc/ghi đúng 1 lần mỗi lần chạy. */
    private record StockKey(Long slatMaterialId, int lengthMm) {}

    /** 1 CuttingPlanDetail đang gộp + các CuttingPlanDetailItem đã tạo cho nó, tra theo ItemKey để cộng dồn cutQuantity khi có stick giống hệt gộp thêm. */
    private record DetailGroup(CuttingPlanDetail detail, Map<ItemKey, CuttingPlanDetailItem> items) {}

    private record PieceGroups(Map<ItemKey, Integer> quantities, ItemKey originalKey) {}
}
