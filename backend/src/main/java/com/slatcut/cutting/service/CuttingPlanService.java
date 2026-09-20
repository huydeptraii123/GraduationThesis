package com.slatcut.cutting.service;

import com.slatcut.cutting.config.ConflictException;
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
import com.slatcut.cutting.dto.PageResponse;
import com.slatcut.cutting.dto.ShortageRecordResponse;
import com.slatcut.cutting.mapper.CuttingPlanMapper;
import com.slatcut.cutting.repository.BomItemRepository;
import com.slatcut.cutting.repository.CuttingPlanDetailItemRepository;
import com.slatcut.cutting.repository.CuttingPlanDetailRepository;
import com.slatcut.cutting.repository.CuttingPlanRepository;
import com.slatcut.cutting.repository.InventoryBatchRepository;
import com.slatcut.cutting.repository.SalesOrderRepository;
import com.slatcut.cutting.repository.ShortageRecordRepository;
import com.slatcut.cutting.repository.SlatMaterialRepository;
import com.slatcut.cutting.repository.TableState;
import com.slatcut.cutting.repository.spec.CuttingPlanSpecifications;
import com.slatcut.cutting.service.optimizer.CutRecord;
import com.slatcut.cutting.service.optimizer.CuttingPlanResult;
import com.slatcut.cutting.service.optimizer.CuttingStrategy;
import com.slatcut.cutting.service.optimizer.InventoryPool;
import com.slatcut.cutting.service.optimizer.RemainderCategory;
import com.slatcut.cutting.service.optimizer.ShortageEntry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nối CuttingDemandService (7.3) và CuttingStrategy (8.1-8.4) thành hai chức năng tách bạch của
 * docs/requirements-functional.md Nhóm 3, khác nhau ở đúng hai điểm — phạm vi lấy đơn, và có ghi
 * dữ liệu hay không:
 *
 * <ul>
 *   <li><b>Tính</b> ({@link #simulate()}) — toàn bộ đơn chưa duyệt đang có, không giới hạn ngày
 *       giao, không giới hạn số đơn, và KHÔNG ghi một dòng nào. Bỏ cả hai giới hạn chính là giá
 *       trị của chức năng: nó trả lời được "với toàn bộ đơn và toàn bộ tồn kho hiện tại thì còn
 *       thiếu loại thanh nan nào", thứ mà một đợt duyệt tối đa 70 đơn không bao giờ trả lời được.
 *   <li><b>Duyệt</b> ({@link #approvalPreview()} rồi {@link #approve(String)}) — phạm vi hẹp theo
 *       đúng luật đợt cắt: đơn chưa duyệt sinh được ít nhất 1 nhu cầu cắt, reqdDeliveryDate
 *       &lt;= t+3, tối đa 70 đơn (đơn ngoài phạm vi thuộc "nhóm 99", vẫn suy ra động nên không cần
 *       cột riêng). Đây là đường ghi dữ liệu duy nhất của lớp này.
 * </ul>
 *
 * <p>Ranh giới đó được giữ bằng cấu trúc chứ không bằng kỷ luật: {@link #runAlgorithm} là phần
 * dùng chung và thuần tính toán, còn mọi thao tác ghi đều nằm trong {@link #persistApprovedPlan},
 * chỉ có một đường gọi tới. Trạng thái "đã duyệt" của đơn ghi ở {@link #markScopeApproved}.
 */
@Service
public class CuttingPlanService {

    private static final Logger log = LoggerFactory.getLogger(CuttingPlanService.class);

    private static final int SCOPE_CUTOFF_DAYS = 3;
    private static final int SCOPE_MAX_ORDERS = 70;
    private static final BigDecimal MM_PER_M = new BigDecimal(1000);

    private final SalesOrderRepository salesOrderRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final CuttingDemandService cuttingDemandService;
    private final CuttingStrategy cuttingStrategy;
    private final BomItemRepository bomItemRepository;
    private final SlatMaterialRepository slatMaterialRepository;
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
            BomItemRepository bomItemRepository,
            SlatMaterialRepository slatMaterialRepository,
            CuttingPlanRepository cuttingPlanRepository,
            CuttingPlanDetailRepository cuttingPlanDetailRepository,
            CuttingPlanDetailItemRepository cuttingPlanDetailItemRepository,
            ShortageRecordRepository shortageRecordRepository,
            CuttingPlanMapper mapper) {
        this.salesOrderRepository = salesOrderRepository;
        this.inventoryBatchRepository = inventoryBatchRepository;
        this.cuttingDemandService = cuttingDemandService;
        this.cuttingStrategy = cuttingStrategy;
        this.bomItemRepository = bomItemRepository;
        this.slatMaterialRepository = slatMaterialRepository;
        this.cuttingPlanRepository = cuttingPlanRepository;
        this.cuttingPlanDetailRepository = cuttingPlanDetailRepository;
        this.cuttingPlanDetailItemRepository = cuttingPlanDetailItemRepository;
        this.shortageRecordRepository = shortageRecordRepository;
        this.mapper = mapper;
    }

    /**
     * Tính phương án cắt cho TOÀN BỘ đơn chưa duyệt trên hệ thống và tồn kho tại thời điểm gọi,
     * không ghi bất cứ thứ gì xuống cơ sở dữ liệu.
     *
     * <p>Không lưu lịch sử là quyết định nghiệp vụ, không phải thiếu sót: đơn hàng và tồn kho được
     * cập nhật liên tục trong ngày nên mỗi lần bấm là một lần tính trên trạng thái tại đúng thời
     * điểm đó, và một lịch sử các lần tính chỉ là một chồng số liệu đã lỗi thời. Đổi lại, người
     * dùng chạy thử bao nhiêu lần tùy ý mà không làm lệch tồn kho.
     *
     * <p>{@code readOnly = true} ở đây không chỉ là gợi ý tối ưu mà là hàng rào cuối cùng: nếu có
     * thao tác ghi nào lọt vào nhánh này, transaction sẽ ném lỗi thay vì âm thầm trừ tồn kho.
     * {@link InventoryPool} cũng sao chép tồn kho sang cấu trúc riêng chứ không sửa entity, nên
     * việc "cắt" trong lúc tính không để lại dirty state nào để Hibernate flush lúc commit.
     */
    @Transactional(readOnly = true)
    public CuttingPlanPreview simulate() {
        List<SalesOrder> scopeOrders = salesOrderRepository.findUnapproved();
        CuttingPlanResult result = runAlgorithm(scopeOrders);
        long blockedOrderCount = salesOrderRepository.countUnapprovedIgnoringBom() - scopeOrders.size();
        return buildPreview(scopeOrders, result, blockedOrderCount);
    }

    /**
     * Phương án đề xuất cho một đợt duyệt, kèm dấu vân trạng thái để {@link #approve(String)} nhận
     * lại. Không ghi gì — PLANNER còn phải xem xét trước đã.
     */
    @Transactional(readOnly = true)
    public CuttingPlanApprovalPreview approvalPreview() {
        ScopeSnapshot snapshot = readApprovalScope();
        CuttingPlanResult result = runAlgorithm(snapshot.orders());
        long blockedOrderCount = countPendingMissingBom(snapshot.cutoffDate());
        return new CuttingPlanApprovalPreview(
                buildPreview(snapshot.orders(), result, blockedOrderCount),
                snapshot.cutoffDate(),
                snapshot.fingerprint());
    }

    /**
     * Duyệt phương án cắt — đường ghi dữ liệu duy nhất của lớp này ngoài các thao tác nhập/sửa dữ
     * liệu gốc.
     *
     * <p>Thuật toán chạy LẠI ở đây thay vì nhận lại kết quả đã trình cho PLANNER xem. Nghe như
     * lãng phí nhưng đó là điều kiện để phương án ghi xuống khớp dữ liệu thật: nhận một kết quả
     * tính sẵn do phía client gửi lên là tin vào đúng thứ đang phải kiểm. Chạy lại ở đây thì phương
     * án được tính từ chính dữ liệu mà transaction này đọc được, còn dấu vân đảm bảo dữ liệu đó
     * vẫn là dữ liệu PLANNER đã nhìn thấy.
     *
     * <p><b>Giới hạn đã biết:</b> phép so dấu vân là một lần đọc thường, không khóa dòng — hai lượt
     * duyệt chạy song song đều đọc được dấu vân cũ sẽ cùng vượt qua cửa này và cùng trừ tồn kho.
     * Đây đúng là giới hạn đã ghi ở {@link InventoryBatchRepository#applyDelta}: hệ thống hiện có
     * một PLANNER thao tác tuần tự, và chặn triệt để cần khóa dòng khi đọc chứ không phải chạy lại
     * thuật toán.
     *
     * @param expectedStateFingerprint dấu vân mà {@link #approvalPreview()} đã trả về cùng phương
     *     án đang hiển thị
     * @throws ConflictException khi dấu vân lệch — đơn hàng hoặc tồn kho đã thay đổi, phương án
     *     đang hiển thị đã lỗi thời và ghi xuống sẽ trừ tồn kho những phôi thực tế không còn, hoặc
     *     bỏ sót đơn vừa được bổ sung vào phạm vi
     */
    @Transactional
    public CuttingPlan approve(String expectedStateFingerprint) {
        ScopeSnapshot snapshot = readApprovalScope();
        if (!snapshot.fingerprint().equals(expectedStateFingerprint)) {
            throw new ConflictException("Đơn hàng hoặc tồn kho đã thay đổi kể từ lúc phương án này được tính."
                    + " Hãy xem lại phương án tính trên trạng thái mới rồi duyệt lại.");
        }
        return persistApprovedPlan(snapshot);
    }

    /**
     * Đường ghi cũ: duyệt ngay phương án vừa tính, không qua bước PLANNER xem xét nên cũng không có
     * dấu vân trạng thái để kiểm. Giữ lại cho endpoint {@code POST /cutting-plans/generate} chạy
     * được cho tới khi màn hình duyệt thay thế nó; mọi thao tác ghi vẫn đi qua đúng
     * {@link #persistApprovedPlan} như {@link #approve(String)}.
     */
    @Transactional
    public CuttingPlan generate() {
        return persistApprovedPlan(readApprovalScope());
    }

    /**
     * Phần thuần tính toán dùng chung cho cả ba hàm trên: dựng nhu cầu cắt, nạp ảnh chụp tồn kho,
     * chạy 4 mức ưu tiên. Không chạm tới một repository ghi nào — đó là lý do chức năng tính có thể
     * dùng lại y nguyên thuật toán của chức năng duyệt mà không có rủi ro làm đổi dữ liệu.
     */
    private CuttingPlanResult runAlgorithm(List<SalesOrder> orders) {
        List<CuttingDemand> demands = cuttingDemandService.buildDemands(orders);
        InventoryPool pool = new InventoryPool(inventoryBatchRepository.findAll());
        return cuttingStrategy.computePlan(demands, pool);
    }

    private CuttingPlanPreview buildPreview(
            List<SalesOrder> scopeOrders, CuttingPlanResult result, long blockedOrderCount) {
        return new CuttingPlanPreview(
                LocalDateTime.now(),
                scopeOrders,
                result,
                blockedOrderCount,
                totalWasteM(result.cuts()),
                totalStockUsedM(result.cuts()));
    }

    /**
     * Lấy phạm vi đợt duyệt và dấu vân trạng thái TRONG CÙNG một lượt đọc.
     *
     * <p>Thứ tự này là bắt buộc chứ không tùy tiện: dấu vân phải mô tả đúng trạng thái đã dựng nên
     * danh sách đơn, nên nó được đọc ngay sau truy vấn phạm vi và trước khi thuật toán chạy. Chụp
     * sau khi thuật toán chạy xong thì một thay đổi xảy ra trong lúc thuật toán đang chạy sẽ được
     * ghi vào dấu vân như thể không có gì xảy ra, và cơ chế này mất tác dụng đúng ở tình huống nó
     * sinh ra để chặn. Hai truy vấn nằm trong cùng một transaction nên cùng đọc trên một ảnh chụp
     * nhất quán của cơ sở dữ liệu.
     */
    private ScopeSnapshot readApprovalScope() {
        LocalDate cutoffDate = scopeCutoffDate();
        List<SalesOrder> scopeOrders = findScopeOrders(cutoffDate);
        return new ScopeSnapshot(scopeOrders, cutoffDate, readStateFingerprint(cutoffDate));
    }

    /**
     * Dấu vân trạng thái: băm của mốc ngày giao đã dùng để lấy phạm vi, cộng tóm tắt của cả BỐN
     * bảng mà thuật toán đọc — đơn hàng trong hạn giao, tồn kho, định mức, danh mục thanh nan. Vì
     * sao mỗi thành phần cần có mặt thì nằm ở javadoc của chính truy vấn nguồn.
     *
     * <p>Nguyên tắc chọn thành phần: dấu vân phải phủ đúng tập đầu vào của thuật toán, không hơn
     * không kém. Thiếu một bảng thì một thay đổi ở đó đi lọt và phương án ghi xuống khác phương án
     * vừa được duyệt; thừa một bảng thì PLANNER bị từ chối bởi những thay đổi không thể ảnh hưởng
     * tới đợt duyệt này.
     *
     * <p>Mốc ngày giao nằm trong dấu vân vì chính nó cũng là một đầu vào, và là đầu vào duy nhất
     * không đến từ cơ sở dữ liệu: nó được tính từ ngày hiện tại. Xem phương án lúc 23h59 rồi bấm
     * duyệt sau nửa đêm thì phạm vi đã rộng thêm một ngày so với thứ PLANNER vừa xem, và không có
     * dòng dữ liệu nào đổi để báo điều đó.
     *
     * <p>Băm thay vì trả thẳng chuỗi các con số: giá trị này đi qua trình duyệt rồi quay lại, không
     * có lý do gì để nó tiết lộ quy mô dữ liệu, và băm cho chuỗi dài cố định dù sau này có thêm
     * thành phần.
     */
    private String readStateFingerprint(LocalDate cutoffDate) {
        TableState orders = salesOrderRepository.readScopeState(cutoffDate);
        InventoryBatchRepository.InventoryState stock = inventoryBatchRepository.readInventoryState();
        TableState bom = bomItemRepository.readState();
        TableState materials = slatMaterialRepository.readState();
        String canonical = "cutoff=%s|orders=%s|stock=%s/%d|bom=%s|materials=%s"
                .formatted(
                        cutoffDate,
                        describe(orders),
                        describe(stock),
                        stock.getTotalSticks(),
                        describe(bom),
                        describe(materials));
        return sha256Hex(canonical);
    }

    private static String describe(TableState state) {
        return state.getRowCount() + "@" + state.getLastUpdatedAt();
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 là thuật toán bắt buộc có của mọi JVM — nhánh này không đạt tới được.
            throw new IllegalStateException(e);
        }
    }

    /**
     * Ghi xuống toàn bộ kết quả của một đợt duyệt trong đúng 1 transaction: phương án và 3 bảng con,
     * tồn kho bị trừ cùng phần dư nhập lại, và trạng thái đã duyệt của mọi đơn trong phạm vi. Tách
     * ra được thì hệ thống có thể rơi vào trạng thái nửa vời nguy hiểm — ví dụ tồn kho đã trừ nhưng
     * đơn vẫn nằm trong hàng chờ, khiến lần duyệt sau cắt lại chính những đơn đó trên một kho đã
     * cạn.
     */
    private CuttingPlan persistApprovedPlan(ScopeSnapshot snapshot) {
        List<SalesOrder> scopeOrders = snapshot.orders();
        Map<OrderKey, SalesOrder> orderIndex = scopeOrders.stream()
                .collect(Collectors.toMap(so -> new OrderKey(so.getYcsx(), so.getItem()), so -> so));
        CuttingPlanResult result = runAlgorithm(scopeOrders);

        CuttingPlan plan = new CuttingPlan();
        plan.setRunAt(LocalDateTime.now());
        plan.setStatus(CuttingPlanStatus.COMPLETED);
        plan.setScopeCutoffDate(snapshot.cutoffDate());
        plan.setScopeOrderCount(scopeOrders.size());
        plan.setTotalWasteM(totalWasteM(result.cuts()));
        plan.setTotalStockUsedM(totalStockUsedM(result.cuts()));
        cuttingPlanRepository.save(plan);

        persistCuts(plan, result.cuts(), orderIndex);
        persistShortages(plan, result.shortages(), orderIndex);
        applyInventoryChanges(result.cuts());
        markScopeApproved(plan, scopeOrders, result);
        return plan;
    }

    /**
     * Đánh dấu đơn trong phạm vi là đã thuộc phương án này. Bao gồm cả đơn CHỈ sinh ra
     * ShortageRecord: gán thiếu đơn đó thì nó quay lại hàng chờ và bị đưa vào lần chạy kế tiếp
     * trong khi tồn kho đã bị trừ cho các đơn khác từ lần này.
     *
     * <p>Nhưng KHÔNG đánh dấu đơn không để lại bất kỳ kết quả nào — không một lát cắt, không một
     * dòng thiếu vật tư. Đơn như vậy về lý thuyết không lọt được vào phạm vi (điều kiện lọc định
     * mức ở {@link SalesOrderRepository#findUnprocessedInScope} đã loại), nhưng điều kiện đó viết
     * bằng SQL nên chỉ kiểm được sự tồn tại của tham số định mức, không kiểm được giá trị tính ra
     * — ví dụ hệ số cho ra số nan bằng 0 vẫn qua được cửa. Nếu vẫn đánh dấu, đơn đó biến mất khỏi
     * mọi hàng chờ và mọi báo cáo mà không ai biết; để nguyên thì nó ở lại hàng chờ y như trước,
     * nhìn thấy được, và log dưới đây chỉ ra ngay đơn nào cần xem lại định mức.
     *
     * <p>Gọi sau cùng vì {@code markApproved} xóa persistence context (xem javadoc của nó): mọi
     * thao tác ghi khác phải xong trước, nếu không các entity đang dở sẽ bị gỡ khỏi context giữa
     * chừng.
     */
    private void markScopeApproved(CuttingPlan plan, List<SalesOrder> scopeOrders, CuttingPlanResult result) {
        Set<OrderKey> produced = new HashSet<>();
        for (CutRecord cut : result.cuts()) {
            for (CuttingDemand piece : cut.pieces()) {
                produced.add(new OrderKey(piece.ycsx(), piece.item()));
            }
        }
        for (ShortageEntry shortage : result.shortages()) {
            produced.add(new OrderKey(shortage.demand().ycsx(), shortage.demand().item()));
        }

        List<Long> approvedIds = new ArrayList<>();
        for (SalesOrder order : scopeOrders) {
            if (produced.contains(new OrderKey(order.getYcsx(), order.getItem()))) {
                approvedIds.add(order.getId());
            } else {
                log.warn(
                        "Đơn ycsx={} item={} nằm trong phạm vi nhưng không sinh ra kết quả nào — giữ lại ở"
                                + " hàng chờ, cần kiểm tra lại định mức của mẫu cửa id={}",
                        order.getYcsx(),
                        order.getItem(),
                        order.getDoorProduct().getId());
            }
        }
        if (approvedIds.isEmpty()) {
            return;
        }
        salesOrderRepository.markApproved(plan, approvedIds);
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
     *
     * <p>Mỗi delta ghi xuống bằng 1 câu {@code UPDATE ... SET so_thanh = so_thanh + :delta} nguyên
     * tử, KHÔNG phải đọc entity ra rồi set lại — xem phạm vi bảo vệ (và phần KHÔNG bảo vệ) ở
     * {@link InventoryBatchRepository#applyDelta}.
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
            if (inventoryBatchRepository.applyDelta(key.slatMaterialId(), key.lengthMm(), delta) > 0) {
                continue;
            }
            if (delta < 0) {
                throw new IllegalStateException("Không tìm thấy lô tồn kho để trừ: slatMaterialId=" + key.slatMaterialId()
                        + ", doDaiThanhMm=" + key.lengthMm());
            }
            inventoryBatchRepository.save(newBatchFor(materialsById.get(key.slatMaterialId()), key.lengthMm(), delta));
        }
    }

    /** Độ dài phần dư nhập lại kho có thể chưa từng tồn tại thành lô riêng — khi đó tạo dòng mới với đúng số thanh nhập vào. */
    private static InventoryBatch newBatchFor(SlatMaterial slatMaterial, int lengthMm, int soThanh) {
        InventoryBatch batch = new InventoryBatch();
        batch.setSlatMaterial(slatMaterial);
        batch.setDoDaiThanhMm(lengthMm);
        batch.setSoThanh(soThanh);
        return batch;
    }

    /** Không lưu gì — chỉ đếm trước theo đúng quy tắc phạm vi của {@link #generate()}, phục vụ modal xác nhận ở FE. */
    @Transactional(readOnly = true)
    public CuttingPlanScopePreviewResponse getScopePreview() {
        LocalDate cutoffDate = scopeCutoffDate();
        int eligibleOrderCount = findScopeOrders(cutoffDate).size();
        return new CuttingPlanScopePreviewResponse(eligibleOrderCount, cutoffDate);
    }

    /**
     * Số đơn đang tồn đọng trong hạn giao, KHÔNG cắt ở hạn mức {@value #SCOPE_MAX_ORDERS} đơn mỗi
     * lần chạy — khác {@link #getScopePreview()} ở đúng điểm đó: modal xác nhận cần biết lần chạy
     * này lấy được bao nhiêu đơn, còn KPI trang chủ cần biết còn bao nhiêu đơn phải xử lý.
     */
    @Transactional(readOnly = true)
    public long countPendingInScope() {
        return salesOrderRepository.countUnprocessedInScope(scopeCutoffDate());
    }

    /**
     * Số đơn trong hạn giao bị thuật toán bỏ qua vì mẫu cửa không có dòng định mức nào dùng được.
     * Không phải "tồn đọng chờ tới lượt" mà là "đang bị chặn, cần ADMIN cấu hình" — nên đếm và hiển
     * thị tách khỏi {@link #countPendingInScope()}.
     *
     * <p>Suy ra bằng hiệu của hai phép đếm thay vì một truy vấn phủ định riêng: hai vế luôn cộng
     * lại đúng bằng tổng đơn chưa xử lý trong hạn giao, không thể lệch nhau.
     */
    @Transactional(readOnly = true)
    public long countPendingMissingBom() {
        return countPendingMissingBom(scopeCutoffDate());
    }

    /** Bản nhận sẵn mốc ngày giao, để luồng duyệt khỏi tính lại {@link #scopeCutoffDate()} lần hai. */
    private long countPendingMissingBom(LocalDate cutoffDate) {
        return salesOrderRepository.countUnprocessedInScopeIgnoringBom(cutoffDate)
                - salesOrderRepository.countUnprocessedInScope(cutoffDate);
    }

    /** Ngày giao xa nhất còn nằm trong phạm vi xử lý — công khai để màn hình khác hiển thị mà không tự tính lại. */
    public LocalDate currentScopeCutoffDate() {
        return scopeCutoffDate();
    }

    private LocalDate scopeCutoffDate() {
        return LocalDate.now().plusDays(SCOPE_CUTOFF_DAYS);
    }

    private List<SalesOrder> findScopeOrders(LocalDate cutoffDate) {
        return salesOrderRepository.findUnprocessedInScope(cutoffDate, PageRequest.of(0, SCOPE_MAX_ORDERS));
    }

    @Transactional(readOnly = true)
    public PageResponse<CuttingPlanSummaryResponse> getSummaryPage(
            Long planId, CuttingPlanStatus status, LocalDate runFrom, LocalDate runTo, Pageable pageable) {
        return PageResponse.of(
                cuttingPlanRepository.findAll(
                        CuttingPlanSpecifications.filter(planId, status, runFrom, runTo), pageable),
                mapper::toSummaryResponse);
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

    /**
     * Một lượt đọc trạng thái hệ thống: danh sách đơn trong phạm vi duyệt, mốc ngày giao đã dùng để
     * lấy chúng, và dấu vân của đúng lượt đọc đó. Gói chung làm một để dấu vân không thể bị tách ra
     * khỏi danh sách đơn mà nó mô tả.
     */
    private record ScopeSnapshot(List<SalesOrder> orders, LocalDate cutoffDate, String fingerprint) {}

    private record OrderKey(String ycsx, Integer item) {}

    private record ItemKey(String ycsx, Integer item, int cutLengthMm) {}

    private record ShortageKey(String ycsx, Integer item, Long slatMaterialId) {}

    /** 1 dòng inventory_batch — gom delta theo khóa này để mỗi dòng chỉ đọc/ghi đúng 1 lần mỗi lần chạy. */
    private record StockKey(Long slatMaterialId, int lengthMm) {}

    /** 1 CuttingPlanDetail đang gộp + các CuttingPlanDetailItem đã tạo cho nó, tra theo ItemKey để cộng dồn cutQuantity khi có stick giống hệt gộp thêm. */
    private record DetailGroup(CuttingPlanDetail detail, Map<ItemKey, CuttingPlanDetailItem> items) {}

    private record PieceGroups(Map<ItemKey, Integer> quantities, ItemKey originalKey) {}
}
