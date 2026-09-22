package com.slatcut.cutting.service;

import com.slatcut.cutting.config.ConflictException;
import com.slatcut.cutting.config.ResourceNotFoundException;
import com.slatcut.cutting.config.UnprocessableRequestException;
import com.slatcut.cutting.domain.CutLevel;
import com.slatcut.cutting.domain.CuttingPlan;
import com.slatcut.cutting.domain.CuttingPlanDetail;
import com.slatcut.cutting.domain.CuttingPlanDetailItem;
import com.slatcut.cutting.domain.CuttingPlanStatus;
import com.slatcut.cutting.domain.CuttingPlanStockSnapshot;
import com.slatcut.cutting.domain.InventoryBatch;
import com.slatcut.cutting.domain.RemainderType;
import com.slatcut.cutting.domain.SalesOrder;
import com.slatcut.cutting.domain.ShortageRecord;
import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.dto.CuttingPlanDetailItemResponse;
import com.slatcut.cutting.dto.CuttingPlanDetailResponse;
import com.slatcut.cutting.dto.CuttingPlanResponse;
import com.slatcut.cutting.dto.CuttingPlanSummaryResponse;
import com.slatcut.cutting.dto.PageResponse;
import com.slatcut.cutting.dto.ShortageRecordResponse;
import com.slatcut.cutting.mapper.CuttingPlanMapper;
import com.slatcut.cutting.repository.BomItemRepository;
import com.slatcut.cutting.repository.CuttingPlanDetailItemRepository;
import com.slatcut.cutting.repository.CuttingPlanDetailRepository;
import com.slatcut.cutting.repository.CuttingPlanRepository;
import com.slatcut.cutting.repository.CuttingPlanStockSnapshotRepository;
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
import com.slatcut.cutting.service.optimizer.StockLine;
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
    private final CuttingPlanStockSnapshotRepository cuttingPlanStockSnapshotRepository;
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
            CuttingPlanStockSnapshotRepository cuttingPlanStockSnapshotRepository,
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
        this.cuttingPlanStockSnapshotRepository = cuttingPlanStockSnapshotRepository;
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
        AlgorithmRun run = runAlgorithm(scopeOrders);
        long blockedOrderCount = salesOrderRepository.countUnapprovedIgnoringBom() - scopeOrders.size();
        return buildPreview(scopeOrders, run, blockedOrderCount);
    }

    /**
     * Phương án đề xuất cho một đợt duyệt, kèm dấu vân trạng thái để {@link #approve(String)} nhận
     * lại. Không ghi gì — PLANNER còn phải xem xét trước đã.
     */
    @Transactional(readOnly = true)
    public CuttingPlanApprovalPreview approvalPreview() {
        ScopeSnapshot snapshot = readApprovalScope();
        AlgorithmRun run = runAlgorithm(snapshot.orders());
        long blockedOrderCount = countPendingMissingBom(snapshot.cutoffDate());
        return new CuttingPlanApprovalPreview(
                buildPreview(snapshot.orders(), run, blockedOrderCount),
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
     * @throws UnprocessableRequestException khi phạm vi không còn đơn nào để duyệt
     */
    @Transactional
    public CuttingPlan approve(String expectedStateFingerprint) {
        ScopeSnapshot snapshot = readApprovalScope();
        if (!snapshot.fingerprint().equals(expectedStateFingerprint)) {
            throw new ConflictException("Đơn hàng hoặc tồn kho đã thay đổi kể từ lúc phương án này được tính."
                    + " Hãy xem lại phương án tính trên trạng thái mới rồi duyệt lại.");
        }
        // Kiểm SAU phép so dấu vân, không phải trước: dấu vân lệch nghĩa là dữ liệu nền đã đổi, nên
        // chính con số "phạm vi có bao nhiêu đơn" vừa đọc được cũng không còn là con số PLANNER đã
        // nhìn thấy — báo "dữ liệu đã thay đổi" mới là mô tả đúng chuyện vừa xảy ra.
        //
        // Không có chốt này thì một lần bấm duyệt khi phạm vi đã rỗng vẫn ghi xuống một CuttingPlan
        // trắng: không đoạn cắt, không dòng thiếu vật tư, không đơn nào được đánh dấu. Tệ hơn, vì
        // bản thân nó không làm đổi dữ liệu nào nên dấu vân vẫn khớp ở lần bấm kế tiếp và thao tác
        // đó lặp lại được vô hạn (docs/requirements-functional.md, mục chức năng duyệt).
        if (snapshot.orders().isEmpty()) {
            throw new UnprocessableRequestException("Không có đơn hàng nào trong phạm vi để duyệt."
                    + " Phương án chỉ được ghi lại khi có ít nhất một đơn hàng được chốt.");
        }
        return persistApprovedPlan(snapshot);
    }

    /**
     * Phần thuần tính toán dùng chung cho cả ba hàm trên: dựng nhu cầu cắt, nạp ảnh chụp tồn kho,
     * chạy 4 mức ưu tiên. Không chạm tới một repository ghi nào — đó là lý do chức năng tính có thể
     * dùng lại y nguyên thuật toán của chức năng duyệt mà không có rủi ro làm đổi dữ liệu.
     */
    private AlgorithmRun runAlgorithm(List<SalesOrder> orders) {
        List<CuttingDemand> demands = cuttingDemandService.buildDemands(orders);
        List<InventoryBatch> batches = inventoryBatchRepository.findAll();
        InventoryPool pool = new InventoryPool(batches);
        CuttingPlanResult result = cuttingStrategy.computePlan(demands, pool);

        // Chỉ giữ lại tồn kho của những loại thanh nan thực sự có mặt trong lần chạy: loại không
        // liên quan thì báo cáo không bao giờ hỏi tới, mà ảnh chụp lại được ghi xuống CSDL mỗi lần
        // duyệt nên không có lý do gì chép cả kho vào đó.
        Set<Long> materialIds =
                demands.stream().map(demand -> demand.slatMaterial().getId()).collect(Collectors.toSet());
        return new AlgorithmRun(result, startingStock(batches, materialIds), pool.remainingLines(materialIds));
    }

    /**
     * Tồn kho ngay TRƯỚC khi thuật toán tiêu thụ. Bỏ lô đã hết thanh đúng như {@link InventoryPool}
     * bỏ khi nạp — lô có 0 thanh không phải là tồn kho, và để lại thì ảnh chụp mô tả một kho khác
     * với kho mà thuật toán thực sự nhìn thấy.
     */
    private static List<StockLine> startingStock(List<InventoryBatch> batches, Set<Long> slatMaterialIds) {
        return batches.stream()
                .filter(batch -> batch.getSoThanh() != null && batch.getSoThanh() > 0)
                .filter(batch -> slatMaterialIds.contains(batch.getSlatMaterial().getId()))
                .map(batch -> new StockLine(
                        batch.getSlatMaterial().getId(), batch.getDoDaiThanhMm(), batch.getSoThanh()))
                .toList();
    }

    private CuttingPlanPreview buildPreview(
            List<SalesOrder> scopeOrders, AlgorithmRun run, long blockedOrderCount) {
        return new CuttingPlanPreview(
                LocalDateTime.now(),
                scopeOrders,
                run.result(),
                blockedOrderCount,
                totalWasteM(run.result().cuts()),
                totalStockUsedM(run.result().cuts()),
                run.stockAtStart(),
                run.stockAfterRun());
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
        AlgorithmRun run = runAlgorithm(scopeOrders);
        CuttingPlanResult result = run.result();

        CuttingPlan plan = new CuttingPlan();
        plan.setRunAt(LocalDateTime.now());
        plan.setStatus(CuttingPlanStatus.COMPLETED);
        plan.setScopeCutoffDate(snapshot.cutoffDate());
        plan.setScopeOrderCount(scopeOrders.size());
        plan.setTotalWasteM(totalWasteM(result.cuts()));
        plan.setTotalStockUsedM(totalStockUsedM(result.cuts()));
        cuttingPlanRepository.save(plan);

        persistCuts(plan, result.cuts(), orderIndex, run.stockAfterRun());
        persistShortages(plan, result.shortages(), orderIndex);
        persistStockSnapshot(plan, run.stockAtStart());
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
     * chừng. Vị trí cuối cùng cũng là nơi hợp lý để đặt chốt chặn chống hai lượt duyệt chồng nhau:
     * đây là thao tác ghi duy nhất khóa được dòng trên chính tập đơn đang tranh chấp.
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
        int marked = salesOrderRepository.markApproved(plan, approvedIds);
        if (marked != approvedIds.size()) {
            // Chỉ xảy ra khi một lượt duyệt khác vừa chốt chính những đơn này trong lúc lượt này
            // đang chạy thuật toán — kể cả khi "lượt khác" chỉ là cú nhấp thứ hai của cùng một
            // người. Ném lỗi để cả giao dịch quay lui: tồn kho đã trừ ở trên cũng được hoàn lại,
            // thay vì trừ hai lần cho một phương án duy nhất.
            throw new ConflictException("Đơn hàng trong phạm vi vừa được duyệt bởi một lượt khác."
                    + " Hãy xem lại phương án tính trên trạng thái mới rồi duyệt lại.");
        }
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

    /**
     * Số đơn đang tồn đọng trong hạn giao, KHÔNG cắt ở hạn mức {@value #SCOPE_MAX_ORDERS} đơn mỗi
     * lần chạy — khác phạm vi của một đợt duyệt ở đúng điểm đó: đợt duyệt cần biết lần này lấy được
     * bao nhiêu đơn, còn KPI trang chủ cần biết còn bao nhiêu đơn phải xử lý.
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
     * Ghi phương án cắt xuống hai bảng con. Phép gộp phôi nằm ở {@link CuttingResultGrouping} vì
     * màn hình duyệt phải trình bày đúng cách nhóm đó trước khi có dòng nào được ghi — xem javadoc
     * của lớp kia.
     */
    private void persistCuts(
            CuttingPlan plan,
            List<CutRecord> cuts,
            Map<OrderKey, SalesOrder> orderIndex,
            List<StockLine> stockAfterRun) {
        Map<StockKey, Integer> remainingByStock = stockAfterRun.stream()
                .collect(Collectors.toMap(
                        line -> new StockKey(line.slatMaterialId(), line.lengthMm()), StockLine::stickCount));

        for (CuttingResultGrouping.CutGroup group : CuttingResultGrouping.groupCuts(cuts)) {
            CuttingPlanDetail detail = new CuttingPlanDetail();
            detail.setCuttingPlan(plan);
            detail.setSlatMaterial(group.slatMaterial());
            detail.setSourceLengthMm(group.sourceLengthMm());
            detail.setPatternCode(group.patternCode());
            detail.setRemainderMm(group.remainderMm());
            detail.setRemainderType(RemainderType.valueOf(group.remainderCategory().name()));
            detail.setCutLevel(CutLevel.valueOf(group.cutLevel().name()));
            detail.setStickCount(group.stickCount());
            // Vắng mặt nghĩa là độ dài đó đã bị cắt hết sạch trong chính lần chạy này, tức còn 0.
            detail.setRemainingSticksAfter(remainingByStock.getOrDefault(
                    new StockKey(group.slatMaterial().getId(), group.sourceLengthMm()), 0));
            cuttingPlanDetailRepository.save(detail);

            for (CuttingResultGrouping.CutItem cutItem : group.items()) {
                CuttingPlanDetailItem item = new CuttingPlanDetailItem();
                item.setCuttingPlanDetail(detail);
                item.setSalesOrder(orderIndex.get(new OrderKey(cutItem.ycsx(), cutItem.item())));
                item.setCutLengthMm(cutItem.cutLengthMm());
                item.setCutQuantity(cutItem.cutQuantity());
                item.setOriginalOrder(cutItem.originalOrder());
                cuttingPlanDetailItemRepository.save(item);
            }
        }
    }

    /**
     * Chép tồn kho đầu lần chạy vào bảng ảnh chụp của phương án.
     *
     * <p>Chạy TRƯỚC {@link #applyInventoryChanges}: sau khi trừ kho thì ảnh chụp không còn chụp
     * được trạng thái ban đầu nữa. Dùng tham chiếu lười tới loại thanh nan thay vì nạp entity —
     * chỉ cần khóa ngoại để ghi, không đọc trường nào của nó.
     */
    private void persistStockSnapshot(CuttingPlan plan, List<StockLine> stockAtStart) {
        for (StockLine line : stockAtStart) {
            CuttingPlanStockSnapshot snapshot = new CuttingPlanStockSnapshot();
            snapshot.setCuttingPlan(plan);
            snapshot.setSlatMaterial(slatMaterialRepository.getReferenceById(line.slatMaterialId()));
            snapshot.setDoDaiThanhMm(line.lengthMm());
            snapshot.setSoThanh(line.stickCount());
            cuttingPlanStockSnapshotRepository.save(snapshot);
        }
    }

    private void persistShortages(
            CuttingPlan plan, List<ShortageEntry> shortages, Map<OrderKey, SalesOrder> orderIndex) {
        for (CuttingResultGrouping.ShortageGroup group : CuttingResultGrouping.groupShortages(shortages)) {
            ShortageRecord record = new ShortageRecord();
            record.setCuttingPlan(plan);
            record.setSalesOrder(orderIndex.get(new OrderKey(group.ycsx(), group.item())));
            record.setSlatMaterial(group.slatMaterial());
            record.setMissingQuantity(group.missingQuantity());
            record.setMissingLengthM(group.missingLengthM());
            shortageRecordRepository.save(record);
        }
    }

    private static BigDecimal toMeters(int lengthMm) {
        return BigDecimal.valueOf(lengthMm).divide(MM_PER_M, 2, RoundingMode.HALF_UP);
    }

    /**
     * Một lần chạy thuật toán: kết quả cắt, cộng trạng thái kho ở hai đầu.
     *
     * <p>Hai danh sách tồn kho đi kèm kết quả chứ không đọc lại sau, vì chỉ lúc này mới biết được
     * chúng: ảnh chụp đầu lần chạy sẽ sai ngay khi tồn kho bị trừ, còn số thanh còn lại thì kho tạm
     * của thuật toán là nơi duy nhất biết cả phần dư trên 3m vừa nhập lại giữa chừng lẫn những độ
     * dài đã cắt hết sạch.
     */
    private record AlgorithmRun(CuttingPlanResult result, List<StockLine> stockAtStart, List<StockLine> stockAfterRun) {}

    /**
     * Một lượt đọc trạng thái hệ thống: danh sách đơn trong phạm vi duyệt, mốc ngày giao đã dùng để
     * lấy chúng, và dấu vân của đúng lượt đọc đó. Gói chung làm một để dấu vân không thể bị tách ra
     * khỏi danh sách đơn mà nó mô tả.
     */
    private record ScopeSnapshot(List<SalesOrder> orders, LocalDate cutoffDate, String fingerprint) {}

    private record OrderKey(String ycsx, Integer item) {}

    /** 1 dòng inventory_batch — gom delta theo khóa này để mỗi dòng chỉ đọc/ghi đúng 1 lần mỗi lần chạy. */
    private record StockKey(Long slatMaterialId, int lengthMm) {}
}
