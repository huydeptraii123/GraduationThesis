package com.slatcut.cutting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.slatcut.cutting.AbstractIntegrationTest;
import com.slatcut.cutting.config.ConflictException;
import com.slatcut.cutting.config.UnprocessableRequestException;
import com.slatcut.cutting.domain.BomItem;
import com.slatcut.cutting.domain.Customer;
import com.slatcut.cutting.domain.CutLevel;
import com.slatcut.cutting.domain.CuttingPlan;
import com.slatcut.cutting.domain.CuttingPlanDetail;
import com.slatcut.cutting.domain.CuttingPlanDetailItem;
import com.slatcut.cutting.domain.CuttingPlanStatus;
import com.slatcut.cutting.domain.CuttingPlanStockSnapshot;
import com.slatcut.cutting.domain.DoorProduct;
import com.slatcut.cutting.domain.InventoryBatch;
import com.slatcut.cutting.domain.RemainderType;
import com.slatcut.cutting.domain.SalesOrder;
import com.slatcut.cutting.domain.ShortageRecord;
import com.slatcut.cutting.domain.SlatGroup;
import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.repository.BomItemRepository;
import com.slatcut.cutting.repository.CustomerRepository;
import com.slatcut.cutting.repository.CuttingPlanDetailItemRepository;
import com.slatcut.cutting.repository.CuttingPlanDetailRepository;
import com.slatcut.cutting.repository.CuttingPlanRepository;
import com.slatcut.cutting.repository.CuttingPlanStockSnapshotRepository;
import com.slatcut.cutting.repository.DoorProductRepository;
import com.slatcut.cutting.repository.InventoryBatchRepository;
import com.slatcut.cutting.repository.SalesOrderRepository;
import com.slatcut.cutting.repository.ShortageRecordRepository;
import com.slatcut.cutting.repository.SlatMaterialRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CuttingPlanServiceTest extends AbstractIntegrationTest {

    @Autowired
    private CuttingPlanService service;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private DoorProductRepository doorProductRepository;

    @Autowired
    private SlatMaterialRepository slatMaterialRepository;

    @Autowired
    private BomItemRepository bomItemRepository;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private InventoryBatchRepository inventoryBatchRepository;

    @Autowired
    private CuttingPlanRepository cuttingPlanRepository;

    @Autowired
    private CuttingPlanDetailRepository cuttingPlanDetailRepository;

    @Autowired
    private CuttingPlanDetailItemRepository cuttingPlanDetailItemRepository;

    @Autowired
    private ShortageRecordRepository shortageRecordRepository;

    @Autowired
    private CuttingPlanStockSnapshotRepository cuttingPlanStockSnapshotRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private long counter = 0;

    /**
     * Duyệt phương án cho phạm vi hiện tại — thay cho đường ghi một bước đã gỡ. Đi qua đúng luồng
     * thật: xem trước để lấy dấu vân trạng thái, rồi duyệt bằng chính dấu vân đó.
     */
    private CuttingPlan approvePlan() {
        return service.approve(service.approvalPreview().stateFingerprint());
    }

    private Customer persistCustomer() {
        Customer entity = new Customer();
        entity.setCustomer(90_000_000L + ++counter);
        entity.setCustomerName("Khách hàng " + counter);
        return customerRepository.save(entity);
    }

    private DoorProduct persistDoorProduct() {
        DoorProduct entity = new DoorProduct();
        entity.setMaterial(80_000_000L + ++counter);
        entity.setDoorMaterialName("Cửa cuốn " + counter);
        entity.setMauSac("#01");
        return doorProductRepository.save(entity);
    }

    private SlatMaterial persistSlatMaterial() {
        SlatMaterial entity = new SlatMaterial();
        entity.setSlatMaterial(70_000_000L + ++counter);
        entity.setSlatMaterialName("Thanh nan " + counter);
        entity.setSlatGroup(SlatGroup.BOTTOM_BAR);
        return slatMaterialRepository.save(entity);
    }

    /** BOTTOM_BAR dùng thẳng chiều rộng cửa làm cutLengthMm, quantity=1 — công thức đơn giản nhất để kiểm soát độ dài đoạn cắt trong test. */
    private BomItem persistBomItem(DoorProduct doorProduct, SlatMaterial slatMaterial) {
        BomItem entity = new BomItem();
        entity.setDoorProduct(doorProduct);
        entity.setSlatMaterial(slatMaterial);
        return bomItemRepository.save(entity);
    }

    private SlatMaterial persistSlatMaterial(SlatGroup slatGroup) {
        SlatMaterial entity = new SlatMaterial();
        entity.setSlatMaterial(70_000_000L + ++counter);
        entity.setSlatMaterialName("Thanh nan " + counter);
        entity.setSlatGroup(slatGroup);
        return slatMaterialRepository.save(entity);
    }

    /** RAIL luôn sinh quantity=2 (CuttingDemandService) — dùng để tái hiện đúng bug đã phát hiện qua review (gộp stickCount làm mất cutQuantity). */
    private SlatMaterial persistRailSlatMaterial() {
        SlatMaterial entity = new SlatMaterial();
        entity.setSlatMaterial(70_000_000L + ++counter);
        entity.setSlatMaterialName("Ray " + counter);
        entity.setSlatGroup(SlatGroup.RAIL);
        return slatMaterialRepository.save(entity);
    }

    private BomItem persistRailBomItem(DoorProduct doorProduct, SlatMaterial slatMaterial) {
        BomItem entity = new BomItem();
        entity.setDoorProduct(doorProduct);
        entity.setSlatMaterial(slatMaterial);
        entity.setHeightOffsetM(BigDecimal.ZERO);
        return bomItemRepository.save(entity);
    }

    private SalesOrder persistSalesOrder(String ycsx, DoorProduct doorProduct, Customer customer, BigDecimal chieuRongDh, LocalDate reqdDeliveryDate) {
        SalesOrder entity = new SalesOrder();
        entity.setYcsx(ycsx);
        entity.setItem((int) ++counter);
        entity.setCustomer(customer);
        entity.setDoorProduct(doorProduct);
        entity.setChieuCaoDh(new BigDecimal("2.500"));
        entity.setChieuRongDh(chieuRongDh);
        entity.setReqdDeliveryDate(reqdDeliveryDate);
        return salesOrderRepository.save(entity);
    }

    private SalesOrder persistRailSalesOrder(String ycsx, DoorProduct doorProduct, Customer customer, BigDecimal chieuCaoDh, LocalDate reqdDeliveryDate) {
        SalesOrder entity = new SalesOrder();
        entity.setYcsx(ycsx);
        entity.setItem((int) ++counter);
        entity.setCustomer(customer);
        entity.setDoorProduct(doorProduct);
        entity.setChieuCaoDh(chieuCaoDh);
        entity.setChieuRongDh(new BigDecimal("1.500"));
        entity.setReqdDeliveryDate(reqdDeliveryDate);
        return salesOrderRepository.save(entity);
    }

    /** Phương án rỗng chỉ để làm mốc "đã duyệt" cho đơn hàng — không kiểm nội dung của nó. */
    private CuttingPlan persistCuttingPlan() {
        CuttingPlan entity = new CuttingPlan();
        entity.setRunAt(java.time.LocalDateTime.now());
        entity.setStatus(CuttingPlanStatus.COMPLETED);
        entity.setScopeCutoffDate(LocalDate.now());
        entity.setScopeOrderCount(0);
        entity.setTotalWasteM(BigDecimal.ZERO);
        entity.setTotalStockUsedM(BigDecimal.ZERO);
        return cuttingPlanRepository.save(entity);
    }

    private void persistInventoryBatch(SlatMaterial slatMaterial, int lengthMm, int count) {
        InventoryBatch entity = new InventoryBatch();
        entity.setSlatMaterial(slatMaterial);
        entity.setDoDaiThanhMm(lengthMm);
        entity.setSoThanh(count);
        inventoryBatchRepository.save(entity);
    }

    // =============================================== Tách "tính" khỏi "duyệt" (Nhóm 3)

    /**
     * Bất biến quan trọng nhất của chức năng tính: bấm bao nhiêu lần cũng không làm đổi một dòng
     * nào. Đo bằng chênh lệch trước/sau thay vì con số tuyệt đối, vì cả bộ test dùng chung một cơ
     * sở dữ liệu.
     *
     * <p>Kiểm cả tồn kho lẫn trạng thái đơn: thuật toán bên trong vẫn "tiêu thụ" phôi và vẫn phân
     * loại đơn y như lúc duyệt, chỉ khác ở chỗ kết quả không được ghi xuống — nên hai chỗ này đúng
     * là nơi một thao tác ghi lọt lưới sẽ hiện ra.
     */

    @Test
    void simulate_doesNotWriteAnythingToDatabase() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 2000, 5);
        SalesOrder order = persistSalesOrder(
                "HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());

        long plansBefore = cuttingPlanRepository.count();
        long detailsBefore = cuttingPlanDetailRepository.count();
        long itemsBefore = cuttingPlanDetailItemRepository.count();
        long shortagesBefore = shortageRecordRepository.count();
        long snapshotsBefore = cuttingPlanStockSnapshotRepository.count();
        long sticksBefore = inventoryBatchRepository.sumAvailableSticks();

        CuttingPlanPreview preview = service.simulate();

        assertThat(preview.result().cuts()).isNotEmpty();
        assertThat(cuttingPlanRepository.count()).isEqualTo(plansBefore);
        assertThat(cuttingPlanDetailRepository.count()).isEqualTo(detailsBefore);
        assertThat(cuttingPlanDetailItemRepository.count()).isEqualTo(itemsBefore);
        assertThat(shortageRecordRepository.count()).isEqualTo(shortagesBefore);
        assertThat(cuttingPlanStockSnapshotRepository.count()).isEqualTo(snapshotsBefore);
        assertThat(inventoryBatchRepository.sumAvailableSticks()).isEqualTo(sticksBefore);
        assertThat(salesOrderRepository.findById(order.getId()).orElseThrow().getApprovedPlan())
                .isNull();
    }

    /**
     * Chức năng tính cố ý bỏ cả mốc t+3 lẫn hạn mức 70 đơn — đó chính là thứ làm nó trả lời được
     * câu hỏi "toàn bộ đơn đang có thì còn thiếu vật tư gì", nên phải khóa bằng test chứ không để
     * một lần tối ưu phạm vi sau này lặng lẽ lấy lại.
     */
    @Test
    void simulate_coversOrdersBeyondDeliveryCutoffAndSeventyOrderLimit() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        for (int i = 0; i < 71; i++) {
            persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());
        }
        SalesOrder farFuture = persistSalesOrder(
                "HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now().plusDays(90));

        CuttingPlanPreview preview = service.simulate();

        assertThat(preview.scopeOrders()).hasSize(72);
        assertThat(preview.scopeOrders()).extracting(SalesOrder::getId).contains(farFuture.getId());
    }

    /** Phạm vi của đợt duyệt thì ngược lại — vẫn bó đúng t+3 và 70 đơn như đặc tả cũ. */
    @Test
    void approvalPreview_keepsDeliveryCutoffAndSeventyOrderLimit() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        for (int i = 0; i < 71; i++) {
            persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());
        }
        SalesOrder farFuture = persistSalesOrder(
                "HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now().plusDays(90));

        CuttingPlanApprovalPreview preview = service.approvalPreview();

        assertThat(preview.scopeCutoffDate()).isEqualTo(LocalDate.now().plusDays(3));
        assertThat(preview.plan().scopeOrders()).hasSize(70);
        assertThat(preview.plan().scopeOrders())
                .extracting(SalesOrder::getId)
                .doesNotContain(farFuture.getId());
    }

    /** Đơn có mẫu cửa không sinh được nhu cầu cắt nào được đếm tách ra, không lẫn vào kết quả tính. */
    @Test
    void simulate_countsOrdersBlockedByMissingBomSeparately() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 2000, 1);
        persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());
        SalesOrder blocked = persistSalesOrder(
                "HY9" + (counter + 1), persistDoorProduct(), customer, new BigDecimal("2.000"), LocalDate.now());

        CuttingPlanPreview preview = service.simulate();

        assertThat(preview.scopeOrders()).extracting(SalesOrder::getId).doesNotContain(blocked.getId());
        assertThat(preview.blockedOrderCount()).isEqualTo(1);
    }

    /**
     * Tồn kho đổi trong lúc PLANNER xem xét: ghi xuống lúc này sẽ trừ những phôi mà phương án tưởng
     * là còn. Dấu vân bắt đúng tình huống đó và chặn trước khi có dòng nào được ghi.
     */
    @Test
    void approve_rejectsWhenInventoryChangedSincePreview() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 2000, 1);
        persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());
        CuttingPlanApprovalPreview preview = service.approvalPreview();

        persistInventoryBatch(slatMaterial, 3000, 4);
        long plansBefore = cuttingPlanRepository.count();

        assertThatThrownBy(() -> service.approve(preview.stateFingerprint()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("đã thay đổi");
        assertThat(cuttingPlanRepository.count()).isEqualTo(plansBefore);
    }

    /**
     * Nửa còn lại của dấu vân: một đơn giao gấp vừa được nhập sẽ CHEN vào phạm vi chứ không nằm yên
     * ngoài nó, nên duyệt tiếp phương án cũ là bỏ sót đúng đơn gấp nhất.
     */
    @Test
    void approve_rejectsWhenNewOrderArrivedSincePreview() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 2000, 2);
        persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());
        CuttingPlanApprovalPreview preview = service.approvalPreview();

        persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());

        assertThatThrownBy(() -> service.approve(preview.stateFingerprint()))
                .isInstanceOf(ConflictException.class);
    }

    /** Dấu vân còn khớp thì duyệt ghi đủ: phương án, tồn kho bị trừ, và trạng thái đã duyệt của đơn. */
    @Test
    void approve_writesPlanWhenFingerprintStillMatches() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 2000, 3);
        SalesOrder order = persistSalesOrder(
                "HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());
        CuttingPlanApprovalPreview preview = service.approvalPreview();
        long sticksBefore = inventoryBatchRepository.sumAvailableSticks();

        CuttingPlan plan = service.approve(preview.stateFingerprint());

        assertThat(plan.getId()).isNotNull();
        assertThat(plan.getScopeOrderCount()).isEqualTo(1);
        assertThat(inventoryBatchRepository.sumAvailableSticks()).isEqualTo(sticksBefore - 1);
        assertThat(salesOrderRepository.findById(order.getId()).orElseThrow().getApprovedPlan())
                .isNotNull();
    }

    /**
     * Phạm vi rỗng: dấu vân vẫn khớp (không có gì thay đổi để mà lệch), nên nếu không có chốt riêng
     * thì lượt duyệt này ghi xuống một phương án trắng — và vì nó cũng không làm đổi dữ liệu nào,
     * dấu vân tiếp tục khớp ở lần bấm sau, lặp lại được vô hạn.
     */
    @Test
    void approve_rejectsWhenScopeHasNoOrder() {
        // Không tạo đơn hàng nào: mỗi test chạy trong transaction riêng và quay lui sau khi xong,
        // nên phạm vi ở đây rỗng thật.
        CuttingPlanApprovalPreview preview = service.approvalPreview();
        assertThat(preview.plan().scopeOrders()).isEmpty();
        long plansBefore = cuttingPlanRepository.count();
        long detailsBefore = cuttingPlanDetailRepository.count();
        long shortagesBefore = shortageRecordRepository.count();

        assertThatThrownBy(() -> service.approve(preview.stateFingerprint()))
                .isInstanceOf(UnprocessableRequestException.class)
                .hasMessageContaining("Không có đơn hàng nào trong phạm vi");

        // Đo bằng phần chênh lệch, không dùng số tuyệt đối: CSDL test dùng chung cho cả bộ.
        assertThat(cuttingPlanRepository.count()).isEqualTo(plansBefore);
        assertThat(cuttingPlanDetailRepository.count()).isEqualTo(detailsBefore);
        assertThat(shortageRecordRepository.count()).isEqualTo(shortagesBefore);
    }

    /**
     * Định mức là đầu vào của thuật toán ngang hàng với đơn hàng và tồn kho: thêm một dòng định mức
     * kéo cả một mẫu cửa đang bị chặn vào phạm vi, nên phương án ghi xuống sẽ khác hẳn phương án
     * vừa được duyệt nếu dấu vân bỏ qua phần này.
     */
    @Test
    void approve_rejectsWhenBomChangedSincePreview() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 2000, 2);
        persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());
        CuttingPlanApprovalPreview preview = service.approvalPreview();

        persistBomItem(doorProduct, persistSlatMaterial());

        assertThatThrownBy(() -> service.approve(preview.stateFingerprint()))
                .isInstanceOf(ConflictException.class);
    }

    /**
     * Nhóm vật tư của thanh nan quyết định công thức cắt áp cho từng dòng định mức, nên danh mục
     * thanh nan cũng là đầu vào — thành phần cuối cùng của dấu vân.
     */
    @Test
    void approve_rejectsWhenSlatMaterialCatalogChangedSincePreview() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 2000, 2);
        persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());
        CuttingPlanApprovalPreview preview = service.approvalPreview();

        persistSlatMaterial();

        assertThatThrownBy(() -> service.approve(preview.stateFingerprint()))
                .isInstanceOf(ConflictException.class);
    }

    /**
     * Ngược lại, một đơn có ngày giao ngoài hạn t+3 KHÔNG được làm hỏng dấu vân: đơn như vậy không
     * cách nào lọt vào đợt duyệt này, mà doanh nghiệp thì nhập đơn liên tục trong ngày — để chúng
     * làm lệch dấu vân thì thao tác duyệt không bao giờ hoàn tất được.
     */
    @Test
    void approve_toleratesNewOrderBeyondDeliveryCutoff() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 2000, 2);
        persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());
        CuttingPlanApprovalPreview preview = service.approvalPreview();

        persistSalesOrder(
                "HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now().plusDays(90));

        assertThat(service.approve(preview.stateFingerprint()).getId()).isNotNull();
    }

    /**
     * Phương án trình cho PLANNER được dựng thành báo cáo sau khi transaction đọc đã đóng, nên đơn
     * hàng phải mang theo khách hàng và mẫu cửa chứ không phải proxy đã mất phiên.
     *
     * <p>Xóa persistence context trước khi gọi là bắt buộc để test có nghĩa: không xóa thì truy vấn
     * trả về chính những entity vừa lưu trong test, vốn đã cầm sẵn đối tượng thật, và khẳng định
     * dưới đây xanh kể cả khi truy vấn không hề nạp kèm. Chỉ kiểm khách hàng — mẫu cửa được thuật
     * toán đọc ngay trong lúc dựng nhu cầu cắt nên khởi tạo dù có nạp kèm hay không, còn khách hàng
     * thì chỉ được đọc lúc dựng báo cáo.
     */
    @Test
    void approvalPreview_loadsCustomerTogetherWithTheOrder() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 2000, 1);
        persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());
        entityManager.flush();
        entityManager.clear();

        CuttingPlanApprovalPreview preview = service.approvalPreview();

        SalesOrder loaded = preview.plan().scopeOrders().get(0);
        assertThat(Hibernate.isInitialized(loaded.getCustomer())).isTrue();
    }
    /**
     * Chốt chặn chống hai lượt duyệt chồng nhau, kiểm ở đúng tầng mà nó hoạt động. Dấu vân trạng
     * thái không đủ: nó chỉ là một lần đọc thường nên hai lượt chạy song song — hoặc một cú nhấp
     * đúp — đều đọc được dấu vân cũ và cùng vượt qua. Câu UPDATE này khóa dòng ở CSDL nên lượt sau
     * không sửa được đơn mà lượt trước đã chốt, và số dòng nó trả về chính là thứ lớp dịch vụ dùng
     * để phát hiện rồi hủy cả giao dịch.
     *
     * <p>Bản thân tình huống hai giao dịch chạy song song không dựng được trong bộ test này (mọi
     * test chạy trong một giao dịch duy nhất rồi quay lui), nên phần kiểm được là cơ chế mà chốt
     * chặn đó dựa vào: đơn đã thuộc phương án khác thì không bị sửa, và không bị đếm.
     */
    @Test
    void markApproved_leavesOrdersAlreadyBelongingToAnotherPlanUntouched() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SalesOrder pending =
                persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());
        SalesOrder alreadyApproved =
                persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());
        CuttingPlan earlierPlan = persistCuttingPlan();
        alreadyApproved.setApprovedPlan(earlierPlan);
        salesOrderRepository.saveAndFlush(alreadyApproved);
        CuttingPlan laterPlan = persistCuttingPlan();

        int marked = salesOrderRepository.markApproved(laterPlan, List.of(pending.getId(), alreadyApproved.getId()));

        assertThat(marked).isEqualTo(1);
        assertThat(salesOrderRepository.findById(pending.getId()).orElseThrow().getApprovedPlan().getId())
                .isEqualTo(laterPlan.getId());
        assertThat(salesOrderRepository
                        .findById(alreadyApproved.getId())
                        .orElseThrow()
                        .getApprovedPlan()
                        .getId())
                .isEqualTo(earlierPlan.getId());
    }

    /**
     * Mức 3 ghép hai đoạn của hai đơn khác nhau lên cùng một phôi. Phạm vi được phép ghép là tập
     * đơn của CHÍNH lần chạy đó, nên hai chức năng cho kết quả khác nhau trên cùng một bộ dữ liệu:
     * chức năng tính ghép được đơn giao gấp với đơn giao xa, còn đợt duyệt thì không — đơn giao xa
     * không nằm trong phạm vi, và một phương án đưa xuống xưởng không được phép cắt cho một bộ cửa
     * chưa tới lượt sản xuất.
     *
     * <p>Bộ dữ liệu dựng sao cho ghép hay không ghép cho hai kết quả nhìn thấy được: phôi 7000mm
     * vừa khít 3000 + 4000 (phần dư 0), còn nếu chỉ cắt đoạn 3000 thì để lại 4000mm nhập lại kho.
     * Mức 1 không khớp (dư 4000mm quá xa ngưỡng 30cm) và Mức 2 cũng không (7000 không chia hết cho
     * 3000), nên mức quyết định kết quả đúng là Mức 3.
     */
    @Test
    void simulate_pairsAcrossAllOrdersAtLevelThree() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 7000, 1);
        persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("3.000"), LocalDate.now());
        persistSalesOrder(
                "HY9" + (counter + 1), doorProduct, customer, new BigDecimal("4.000"), LocalDate.now().plusDays(90));

        CuttingPlanPreview preview = service.simulate();

        assertThat(preview.result().cuts()).hasSize(1);
        assertThat(preview.result().cuts().get(0).pieces())
                .extracting(CuttingDemand::cutLengthMm)
                .containsExactlyInAnyOrder(3000, 4000);
        assertThat(preview.result().cuts().get(0).remainderMm()).isZero();
        assertThat(preview.result().shortages()).isEmpty();
    }

    /** Cùng bộ dữ liệu, nhưng đợt duyệt chỉ thấy đơn trong hạn giao nên không có đối tác để ghép. */
    @Test
    void approve_doesNotPairWithOrderOutsideTheApprovalScope() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 7000, 1);
        SalesOrder urgent =
                persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("3.000"), LocalDate.now());
        SalesOrder farFuture = persistSalesOrder(
                "HY9" + (counter + 1), doorProduct, customer, new BigDecimal("4.000"), LocalDate.now().plusDays(90));

        CuttingPlan plan = approvePlan();

        List<CuttingPlanDetail> details = cuttingPlanDetailRepository.findByCuttingPlan_Id(plan.getId());
        assertThat(details).hasSize(1);
        // Không ghép được nên rơi xuống Mức 4: cắt đoạn 3000 và để lại 4000mm nhập lại kho.
        assertThat(details.get(0).getRemainderMm()).isEqualTo(4000);
        assertThat(details.get(0).getRemainderType()).isEqualTo(RemainderType.RESTOCK);
        assertThat(cuttingPlanDetailItemRepository.findByCuttingPlanDetail_IdIn(List.of(details.get(0).getId())))
                .extracting(item -> item.getSalesOrder().getId())
                .containsExactly(urgent.getId());
        // Đơn ngoài phạm vi không bị đụng tới: không có lát cắt nào, và vẫn ở lại hàng chờ.
        assertThat(salesOrderRepository.findById(farFuture.getId()).orElseThrow().getApprovedPlan())
                .isNull();
    }

    // =================================== Dữ liệu chỉ phục vụ báo cáo (mức cắt, tồn kho)

    /**
     * Mức ưu tiên phải được ghi lại đúng nhánh đã cắt. Bốn ca dựng riêng vì không suy ngược được từ
     * kết quả — đó chính là lý do cột này tồn tại.
     */
    @Test
    void approve_recordsTheCutLevelActuallyUsed_nearFit() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 2000, 1);
        persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());

        CuttingPlan plan = approvePlan();

        assertThat(cuttingPlanDetailRepository.findByCuttingPlan_Id(plan.getId()))
                .extracting(CuttingPlanDetail::getCutLevel)
                .containsExactly(CutLevel.PA1);
    }

    @Test
    void approve_recordsTheCutLevelActuallyUsed_multiple() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 6000, 1);
        persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("3.000"), LocalDate.now());
        persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("3.000"), LocalDate.now());

        CuttingPlan plan = approvePlan();

        assertThat(cuttingPlanDetailRepository.findByCuttingPlan_Id(plan.getId()))
                .extracting(CuttingPlanDetail::getCutLevel)
                .containsExactly(CutLevel.PA2);
    }

    @Test
    void approve_recordsTheCutLevelActuallyUsed_combination() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 7000, 1);
        persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("3.000"), LocalDate.now());
        persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("4.000"), LocalDate.now());

        CuttingPlan plan = approvePlan();

        assertThat(cuttingPlanDetailRepository.findByCuttingPlan_Id(plan.getId()))
                .extracting(CuttingPlanDetail::getCutLevel)
                .containsExactly(CutLevel.PA3);
    }

    /**
     * Mức 4 cũng là ca kiểm số phôi còn lại rõ nhất: phôi 7000mm bị tiêu thụ hết, còn phần dư 4000mm
     * quay lại kho thành một thanh mới — con số "còn lại" phải đọc từ trạng thái kho SAU lần chạy,
     * kể cả phần vừa nhập lại giữa chừng.
     */
    @Test
    void approve_recordsCutLevelFourAndRemainingSticksAfterTheRun() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 7000, 2);
        persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("3.000"), LocalDate.now());

        CuttingPlan plan = approvePlan();

        List<CuttingPlanDetail> details = cuttingPlanDetailRepository.findByCuttingPlan_Id(plan.getId());
        assertThat(details).hasSize(1);
        assertThat(details.get(0).getCutLevel()).isEqualTo(CutLevel.PA4);
        assertThat(details.get(0).getSourceLengthMm()).isEqualTo(7000);
        // Kho có 2 thanh 7000mm, lần chạy tiêu 1 nên còn 1.
        assertThat(details.get(0).getRemainingSticksAfter()).isEqualTo(1);
    }

    /**
     * Ảnh chụp tồn kho phải là trạng thái TRƯỚC khi thuật toán tiêu thụ, và chỉ gồm những loại thanh
     * nan có mặt trong lần chạy.
     *
     * <p>Chụp sau khi trừ kho thì báo cáo hiển thị một kho đã bị chính phương án đó làm thay đổi —
     * người đọc không còn biết lúc lập phương án trong kho có gì. Chụp cả kho thì mỗi lần duyệt
     * chép thừa hàng nghìn dòng không ai hỏi tới.
     */
    @Test
    void approve_snapshotsStockAsItWasBeforeTheRunAndOnlyForMaterialsInvolved() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial used = persistSlatMaterial();
        SlatMaterial untouched = persistSlatMaterial();
        persistBomItem(doorProduct, used);
        persistInventoryBatch(used, 2000, 3);
        persistInventoryBatch(used, 5000, 1);
        persistInventoryBatch(untouched, 4000, 9);
        persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());

        CuttingPlan plan = approvePlan();

        assertThat(cuttingPlanStockSnapshotRepository.findByCuttingPlan_Id(plan.getId()))
                .extracting(
                        snapshot -> snapshot.getSlatMaterial().getId(),
                        CuttingPlanStockSnapshot::getDoDaiThanhMm,
                        CuttingPlanStockSnapshot::getSoThanh)
                .containsExactlyInAnyOrder(tuple(used.getId(), 2000, 3), tuple(used.getId(), 5000, 1));
        // Kho thật đã bị trừ 1 thanh 2000mm, nhưng ảnh chụp vẫn giữ nguyên con số trước lần chạy.
        assertThat(inventoryBatchRepository
                        .findBySlatMaterial_IdAndDoDaiThanhMm(used.getId(), 2000)
                        .orElseThrow()
                        .getSoThanh())
                .isEqualTo(2);
    }

    /** Dấu vân rỗng (client cũ, hoặc bấm duyệt mà chưa hề xem phương án) cũng bị từ chối, không rơi vào NPE. */
    @Test
    void approve_rejectsMissingFingerprint() {
        assertThatThrownBy(() -> service.approve(null)).isInstanceOf(ConflictException.class);
    }
    @Test
    void generate_sufficientInventory_persistsCuttingPlanDetailAndItem() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 2000, 1);
        SalesOrder order = persistSalesOrder(
                "HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());

        CuttingPlan plan = approvePlan();

        assertThat(plan.getId()).isNotNull();
        assertThat(plan.getStatus()).isEqualTo(CuttingPlanStatus.COMPLETED);
        assertThat(plan.getScopeOrderCount()).isEqualTo(1);
        assertThat(plan.getTotalWasteM()).isEqualByComparingTo("0.00");
        assertThat(plan.getTotalStockUsedM()).isEqualByComparingTo("2.00");

        List<CuttingPlanDetail> details = cuttingPlanDetailRepository.findAll();
        assertThat(details).hasSize(1);
        CuttingPlanDetail detail = details.get(0);
        assertThat(detail.getSourceLengthMm()).isEqualTo(2000);
        assertThat(detail.getRemainderMm()).isEqualTo(0);
        assertThat(detail.getRemainderType()).isEqualTo(RemainderType.DISCARDED);
        assertThat(detail.getStickCount()).isEqualTo(1);

        List<CuttingPlanDetailItem> items = cuttingPlanDetailItemRepository.findAll();
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getSalesOrder().getId()).isEqualTo(order.getId());
        assertThat(items.get(0).getCutQuantity()).isEqualTo(1);
        assertThat(items.get(0).isOriginalOrder()).isTrue();
    }

    @Test
    void generate_noInventory_persistsShortageRecord() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        SalesOrder order = persistSalesOrder(
                "HY9" + (counter + 1), doorProduct, customer, new BigDecimal("5.000"), LocalDate.now());

        approvePlan();

        assertThat(cuttingPlanDetailRepository.findAll()).isEmpty();
        List<ShortageRecord> shortages = shortageRecordRepository.findAll();
        assertThat(shortages).hasSize(1);
        assertThat(shortages.get(0).getSalesOrder().getId()).isEqualTo(order.getId());
        assertThat(shortages.get(0).getSlatMaterial().getId()).isEqualTo(slatMaterial.getId());
        assertThat(shortages.get(0).getMissingQuantity()).isEqualTo(1);
        assertThat(shortages.get(0).getMissingLengthM()).isEqualByComparingTo("5.00");
    }

    @Test
    void generate_excludesOrdersBeyondCutoffDate() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistSalesOrder(
                "HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now().plusDays(10));

        // Phạm vi rỗng nay bị từ chối thay vì ghi xuống một phương án trắng — lời từ chối đó chính
        // là bằng chứng đơn giao xa đã bị loại khỏi phạm vi.
        assertThatThrownBy(this::approvePlan)
                .as("đơn có ngày giao sau mốc t+3 không được đưa vào phạm vi duyệt")
                .isInstanceOf(UnprocessableRequestException.class);

        assertThat(cuttingPlanDetailItemRepository.findAll()).isEmpty();
        assertThat(shortageRecordRepository.findAll()).isEmpty();
    }

    @Test
    void generate_ordersAlreadyProcessed_areExcludedFromLaterRuns() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 2000, 1);
        persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());

        approvePlan();

        assertThatThrownBy(this::approvePlan)
                .as("đơn đã duyệt không quay lại hàng chờ nên lượt duyệt sau không còn đơn nào")
                .isInstanceOf(UnprocessableRequestException.class);
        assertThat(cuttingPlanDetailItemRepository.findAll()).hasSize(1);
    }

    @Test
    void generate_marksEveryOrderInScopeAsApproved() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 2000, 1);
        SalesOrder order =
                persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());

        CuttingPlan plan = approvePlan();

        assertThat(salesOrderRepository.findById(order.getId()))
                .get()
                .extracting(saved -> saved.getApprovedPlan().getId())
                .isEqualTo(plan.getId());
    }

    /**
     * Đơn không cắt được thanh nào vẫn phải bị đánh dấu đã duyệt. Nếu chỉ đánh dấu những đơn có
     * kết quả cắt, đơn thiếu vật tư sẽ quay lại hàng chờ và bị đưa vào lần duyệt kế tiếp trong khi
     * tồn kho đã bị trừ cho các đơn khác ở lần này.
     */
    @Test
    void generate_marksShortageOnlyOrderAsApprovedToo() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        SalesOrder order =
                persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());

        CuttingPlan plan = approvePlan();

        assertThat(shortageRecordRepository.findByCuttingPlan_Id(plan.getId())).isNotEmpty();
        assertThat(cuttingPlanDetailItemRepository.findAll()).isEmpty();
        assertThat(salesOrderRepository.findById(order.getId()))
                .get()
                .extracting(saved -> saved.getApprovedPlan().getId())
                .isEqualTo(plan.getId());
    }

    /**
     * Điều kiện lọc định mức ở truy vấn phạm vi viết bằng SQL nên chỉ kiểm được rằng hệ số tính số
     * nan có tồn tại, không kiểm được giá trị tính ra. Hệ số cho ra 0 nan vẫn lọt vào phạm vi, rồi
     * không sinh ra lát cắt lẫn dòng thiếu vật tư nào. Nếu đơn đó vẫn bị đánh dấu đã duyệt, nó biến
     * mất khỏi mọi hàng chờ và mọi báo cáo mà không ai biết — phải giữ lại để còn nhìn thấy.
     */
    @Test
    void generate_doesNotApproveOrderThatProducedNothing() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial mainSlat = persistSlatMaterial(SlatGroup.MAIN_SLAT);
        BomItem bomItem = new BomItem();
        bomItem.setDoorProduct(doorProduct);
        bomItem.setSlatMaterial(mainSlat);
        bomItem.setWidthOffsetM(BigDecimal.ZERO);
        bomItem.setSlatCountSlope(BigDecimal.ZERO);
        bomItem.setSlatCountIntercept(BigDecimal.ZERO);
        bomItemRepository.save(bomItem);
        persistInventoryBatch(mainSlat, 2000, 1);
        SalesOrder order =
                persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());

        CuttingPlan plan = approvePlan();

        assertThat(plan.getScopeOrderCount()).isEqualTo(1);
        assertThat(cuttingPlanDetailItemRepository.findAll()).isEmpty();
        assertThat(shortageRecordRepository.findByCuttingPlan_Id(plan.getId())).isEmpty();
        assertThat(salesOrderRepository.findById(order.getId()))
                .get()
                .extracting(SalesOrder::getApprovedPlan)
                .isNull();
    }

    /**
     * Phạm vi của chức năng TÍNH: đúng hai giới hạn của chức năng duyệt bị bỏ. Đơn giao xa hơn
     * t+3 và đơn vượt hạn mức 70 đều phải có mặt — đây chính là điều làm bức tranh thiếu hụt vật
     * tư của toàn bộ đơn tồn nhìn thấy được.
     */
    @Test
    void findUnapproved_ignoresDeliveryCutoffAndSeventyOrderLimit() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        for (int i = 0; i < 71; i++) {
            persistSalesOrder(
                    "HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());
        }
        SalesOrder farFuture = persistSalesOrder(
                "HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now().plusDays(90));

        List<SalesOrder> unapproved = salesOrderRepository.findUnapproved();

        assertThat(unapproved).hasSize(72);
        assertThat(unapproved).extracting(SalesOrder::getId).contains(farFuture.getId());
        assertThat(salesOrderRepository.findUnprocessedInScope(
                        LocalDate.now().plusDays(3), org.springframework.data.domain.PageRequest.of(0, 70)))
                .hasSize(70);
    }

    @Test
    void findUnapproved_excludesOrdersAlreadyApproved() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 2000, 1);
        SalesOrder order =
                persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());

        assertThat(salesOrderRepository.findUnapproved()).extracting(SalesOrder::getId).contains(order.getId());
        approvePlan();

        assertThat(salesOrderRepository.findUnapproved()).extracting(SalesOrder::getId).doesNotContain(order.getId());
    }

    /**
     * Đơn có mẫu cửa không sinh được nhu cầu cắt nào bị loại khỏi phạm vi của CẢ hai chức năng —
     * đó là đơn đang bị chặn chờ khai báo định mức, không phải đơn chờ tới lượt. Nếu lọt vào phạm
     * vi, nó sẽ bị gán approvedPlan dù không sản xuất được gì.
     */
    @Test
    void findUnapproved_excludesOrdersBlockedByMissingBom() {
        Customer customer = persistCustomer();
        DoorProduct withoutBom = persistDoorProduct();
        SalesOrder blocked =
                persistSalesOrder("HY9" + (counter + 1), withoutBom, customer, new BigDecimal("2.000"), LocalDate.now());

        assertThat(salesOrderRepository.findUnapproved())
                .extracting(SalesOrder::getId)
                .doesNotContain(blocked.getId());
    }

    @Test
    void generate_limitsScopeToSeventyOrders_excludesLowestPriorityOrder() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        // Không có tồn kho -> mọi đơn trong phạm vi đều thành ShortageRecord, đơn giản hóa việc
        // xác nhận CHÍNH XÁC đơn nào bị loại (chỉ cần đối chiếu danh sách ShortageRecord).
        LocalDate sameDate = LocalDate.now();
        SalesOrder excludedOrder = null;
        for (int i = 1; i <= 71; i++) {
            String ycsx = String.format("A%03d", i);
            SalesOrder order = persistSalesOrder(ycsx, doorProduct, customer, new BigDecimal("1.000"), sameDate);
            if (i == 71) {
                excludedOrder = order;
            }
        }

        CuttingPlan plan = approvePlan();

        assertThat(plan.getScopeOrderCount()).isEqualTo(70);
        assertThat(shortageRecordRepository.findAll()).hasSize(70);
        Long excludedOrderId = excludedOrder.getId();
        assertThat(shortageRecordRepository.findAll())
                .extracting(sr -> sr.getSalesOrder().getId())
                .doesNotContain(excludedOrderId);
    }

    @Test
    void generate_multipleOfSameLength_mergesTwoOrdersOntoOneStickAsSingleDetail() {
        Customer customerA = persistCustomer();
        Customer customerB = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 6000, 1);
        SalesOrder orderA = persistSalesOrder(
                "A" + (counter + 1), doorProduct, customerA, new BigDecimal("3.000"), LocalDate.now());
        SalesOrder orderB = persistSalesOrder(
                "B" + (counter + 1), doorProduct, customerB, new BigDecimal("3.000"), LocalDate.now());

        approvePlan();

        List<CuttingPlanDetail> details = cuttingPlanDetailRepository.findAll();
        assertThat(details).hasSize(1);
        CuttingPlanDetail detail = details.get(0);
        assertThat(detail.getSourceLengthMm()).isEqualTo(6000);
        assertThat(detail.getRemainderMm()).isEqualTo(0);
        assertThat(detail.getStickCount()).isEqualTo(1);

        List<CuttingPlanDetailItem> items = cuttingPlanDetailItemRepository.findAll();
        assertThat(items).hasSize(2);
        assertThat(items)
                .extracting(item -> item.getSalesOrder().getId(), CuttingPlanDetailItem::isOriginalOrder)
                .containsExactlyInAnyOrder(tuple(orderA.getId(), true), tuple(orderB.getId(), false));
    }

    @Test
    void generate_railQuantityTwoMatchedAsTwoIdenticalSticks_accumulatesCutQuantityWhenMerged() {
        // Bug đã phát hiện qua review (đã sửa): khi 2 CutRecord giống hệt nhau gộp vào 1
        // CuttingPlanDetail (stickCount tăng), cutQuantity của CuttingPlanDetailItem từng bị đứng
        // yên ở giá trị của stick đầu tiên thay vì cộng dồn. RAIL luôn cần quantity=2 cho 1 đơn
        // (CuttingDemandService) nên đây là case thật, không phải giả định — với đủ 2 thanh khớp
        // gần đúng cùng độ dài, cả 2 đơn vị đều qua Mức 1 độc lập, sinh 2 CutRecord y hệt.
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial rail = persistRailSlatMaterial();
        persistRailBomItem(doorProduct, rail);
        persistInventoryBatch(rail, 2000, 2);
        SalesOrder order = persistRailSalesOrder(
                "HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());

        approvePlan();

        List<CuttingPlanDetail> details = cuttingPlanDetailRepository.findAll();
        assertThat(details).hasSize(1);
        assertThat(details.get(0).getStickCount()).isEqualTo(2);

        List<CuttingPlanDetailItem> items = cuttingPlanDetailItemRepository.findAll();
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getSalesOrder().getId()).isEqualTo(order.getId());
        assertThat(items.get(0).getCutQuantity()).isEqualTo(2);
        assertThat(items.get(0).isOriginalOrder()).isTrue();

        // Cùng lúc khóa luôn ngữ nghĩa trừ tồn kho: 2 phôi bị gộp thành 1 dòng detail (stickCount=2)
        // vẫn phải trừ đủ 2 thanh. Đây là kịch bản duy nhất phân biệt được "đếm theo CutRecord"
        // (đúng) với "đếm theo số dòng detail" (sai) — thiếu assert này thì đổi nhầm vẫn xanh test.
        assertThat(inventoryBatchRepository
                        .findBySlatMaterial_IdAndDoDaiThanhMm(rail.getId(), 2000)
                        .orElseThrow()
                        .getSoThanh())
                .isZero();
    }


    @Test
    void generate_decrementsConsumedInventory() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 2000, 3);
        persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());

        approvePlan();

        InventoryBatch batch = inventoryBatchRepository
                .findBySlatMaterial_IdAndDoDaiThanhMm(slatMaterial.getId(), 2000)
                .orElseThrow();
        assertThat(batch.getSoThanh()).isEqualTo(2);
    }

    @Test
    void generate_restocksRemainderOverThreeMetersAsNewInventoryRow() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        // 1 phôi 6m cắt 1 đoạn 2m: Mức 2 không áp dụng (chỉ 1 đoạn chờ, bội số 3 > số đoạn có),
        // Mức 1/3 không khớp → Mức 4 best-fit, dư 4m > 3m nên phải nhập lại kho thành lô mới.
        persistInventoryBatch(slatMaterial, 6000, 1);
        persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());

        approvePlan();

        assertThat(inventoryBatchRepository
                        .findBySlatMaterial_IdAndDoDaiThanhMm(slatMaterial.getId(), 6000)
                        .orElseThrow()
                        .getSoThanh())
                .isZero();
        assertThat(inventoryBatchRepository
                        .findBySlatMaterial_IdAndDoDaiThanhMm(slatMaterial.getId(), 4000)
                        .orElseThrow()
                        .getSoThanh())
                .isEqualTo(1);
    }

    /**
     * Phần dư >3m được nhập lại kho giữa lượt chạy rồi bị chính lượt đó cắt tiếp: thanh 8m cắt 2m
     * (dư 6m, nhập lại) → 6m lại được dùng cho đơn thứ 2, cắt 2m (dư 4m, nhập lại). Về mặt vật lý
     * chỉ đúng 1 thanh 8m rời kho và 1 thanh 4m nằm lại, còn 6m chỉ là trạng thái trung gian —
     * delta của nó phải triệt tiêu, KHÔNG được để lại dòng tồn kho 6m nào.
     */
    @Test
    void generate_remainderRestockedThenReusedInSameRunNetsOut() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 8000, 1);
        persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());
        persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());

        approvePlan();

        assertThat(inventoryBatchRepository
                        .findBySlatMaterial_IdAndDoDaiThanhMm(slatMaterial.getId(), 8000)
                        .orElseThrow()
                        .getSoThanh())
                .isZero();
        assertThat(inventoryBatchRepository.findBySlatMaterial_IdAndDoDaiThanhMm(slatMaterial.getId(), 6000))
                .as("6m chỉ là phần dư trung gian, không được tạo thành lô tồn kho")
                .isEmpty();
        assertThat(inventoryBatchRepository
                        .findBySlatMaterial_IdAndDoDaiThanhMm(slatMaterial.getId(), 4000)
                        .orElseThrow()
                        .getSoThanh())
                .isEqualTo(1);
    }

    /**
     * Kịch bản thật của dataset ~190 đơn với cap 70 đơn/lần chạy: tồn kho lần 1 đã tiêu thụ KHÔNG
     * được cấp lại cho lần chạy sau. Nếu bỏ phần ghi lại tồn kho, đơn thứ 2 sẽ nhận được phương án
     * cắt từ đúng thanh mà đơn thứ 1 đã dùng thay vì bị đánh dấu thiếu vật tư.
     */
    @Test
    void generate_secondRunDoesNotReuseInventoryConsumedByFirstRun() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 2000, 1);
        persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());

        approvePlan();
        assertThat(cuttingPlanDetailRepository.findAll()).hasSize(1);

        SalesOrder secondOrder = persistSalesOrder(
                "HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());

        approvePlan();

        assertThat(cuttingPlanDetailRepository.findAll()).hasSize(1);
        List<ShortageRecord> shortages = shortageRecordRepository.findAll();
        assertThat(shortages).hasSize(1);
        assertThat(shortages.get(0).getSalesOrder().getId()).isEqualTo(secondOrder.getId());
    }

    /**
     * Mẫu cửa chưa có dòng định mức nào sinh ra 0 nhu cầu cắt, nên đơn của nó không để lại
     * CuttingPlanDetailItem lẫn ShortageRecord — trước đây điều đó khiến nó mãi mãi bị coi là "chưa
     * xử lý" và chiếm chỗ trong hạn mức 70 đơn của MỌI lần chạy sau. Nay bị loại khỏi phạm vi ngay
     * từ truy vấn, nhưng phải đếm được để còn cảnh báo, không phải biến mất im lặng.
     */
    @Test
    void generate_excludesOrdersWhoseDoorProductHasNoBom() {
        Customer customer = persistCustomer();

        DoorProduct configured = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(configured, slatMaterial);
        persistInventoryBatch(slatMaterial, 2000, 1);
        SalesOrder processableOrder =
                persistSalesOrder("HY9" + (counter + 1), configured, customer, new BigDecimal("2.000"), LocalDate.now());

        DoorProduct withoutBom = persistDoorProduct();
        persistSalesOrder("HY9" + (counter + 1), withoutBom, customer, new BigDecimal("2.000"), LocalDate.now());

        assertThat(service.countPendingMissingBom())
                .as("đơn của mẫu cửa chưa có định mức phải đếm được riêng")
                .isEqualTo(1);

        CuttingPlan plan = approvePlan();

        assertThat(plan.getScopeOrderCount())
                .as("chỉ đơn của mẫu cửa đã có định mức mới vào phạm vi xử lý")
                .isEqualTo(1);
        List<CuttingPlanDetailItem> items = cuttingPlanDetailItemRepository.findAll();
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getSalesOrder().getId()).isEqualTo(processableOrder.getId());
        assertThat(shortageRecordRepository.findAll()).isEmpty();

        // Vẫn còn đó chờ ADMIN cấu hình định mức — không bị đánh dấu là đã xử lý.
        assertThat(service.countPendingMissingBom()).isEqualTo(1);
    }

    /**
     * Khoá điều kiện lọc phạm vi khớp với các quy tắc bỏ qua của CuttingDemandService.
     *
     * <p>Chỉ hỏi "mẫu cửa có dòng định mức nào không" là chưa đủ: CuttingDemandService còn bỏ qua
     * TỪNG DÒNG một, nên mẫu cửa có định mức mà mọi dòng đều bị bỏ qua vẫn sinh 0 nhu cầu cắt và
     * kẹt vòng lặp y hệt trường hợp không có dòng nào. Mỗi kịch bản dưới đây ứng với đúng 1 nhánh
     * {@code return Optional.empty()} trong CuttingDemandService.buildDemand — thêm nhánh mới ở đó
     * mà quên sửa truy vấn phạm vi thì test này là nơi phát hiện ra.
     */
    @Test
    void generate_excludesOrdersWhoseEveryBomRowIsSkippedByDemandRules() {
        Customer customer = persistCustomer();

        // (1) MAIN_SLAT thiếu hệ số tính số nan — đúng 69/527 dòng trong dữ liệu thật của doanh nghiệp.
        DoorProduct missingCoefficients = persistDoorProduct();
        persistBomItem(missingCoefficients, persistSlatMaterial(SlatGroup.MAIN_SLAT));
        persistSalesOrder(
                "HY9" + (counter + 1), missingCoefficients, customer, new BigDecimal("2.000"), LocalDate.now());

        // (2) RAIL thiếu heightOffsetM.
        DoorProduct missingHeightOffset = persistDoorProduct();
        persistBomItem(missingHeightOffset, persistSlatMaterial(SlatGroup.RAIL));
        persistSalesOrder(
                "HY9" + (counter + 1), missingHeightOffset, customer, new BigDecimal("2.000"), LocalDate.now());

        // (3) Nhóm OTHER không có công thức cắt.
        DoorProduct onlyOtherGroup = persistDoorProduct();
        persistBomItem(onlyOtherGroup, persistSlatMaterial(SlatGroup.OTHER));
        persistSalesOrder("HY9" + (counter + 1), onlyOtherGroup, customer, new BigDecimal("2.000"), LocalDate.now());

        assertThat(service.countPendingMissingBom())
                .as("cả 3 đơn đều không sinh được nhu cầu cắt nên phải đếm là đang bị chặn")
                .isEqualTo(3);

        assertThatThrownBy(this::approvePlan)
                .as("không đơn nào trong 3 kịch bản được đưa vào phạm vi xử lý")
                .isInstanceOf(UnprocessableRequestException.class);

        assertThat(cuttingPlanDetailItemRepository.findAll()).isEmpty();
        assertThat(shortageRecordRepository.findAll()).isEmpty();
        assertThat(service.countPendingMissingBom()).isEqualTo(3);
    }

    /**
     * Tồn kho nay trừ bằng {@code UPDATE ... SET so_thanh = so_thanh + delta} nguyên tử thay vì
     * đọc-sửa-ghi. Query {@code @Modifying} không đi qua persistence context, nên nếu thiếu
     * {@code clearAutomatically}/{@code flushAutomatically} thì lượt chạy thứ hai sẽ đọc lại số
     * thanh cũ và trừ sai. Ba thanh, hai lượt mỗi lượt tiêu 1 thanh, phải còn đúng 1.
     */
    @Test
    void generate_twoRuns_accumulateInventoryDeltasCorrectly() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 2000, 3);

        persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());
        approvePlan();
        assertThat(inventoryBatchRepository
                        .findBySlatMaterial_IdAndDoDaiThanhMm(slatMaterial.getId(), 2000)
                        .orElseThrow()
                        .getSoThanh())
                .isEqualTo(2);

        persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());
        approvePlan();
        assertThat(inventoryBatchRepository
                        .findBySlatMaterial_IdAndDoDaiThanhMm(slatMaterial.getId(), 2000)
                        .orElseThrow()
                        .getSoThanh())
                .as("lượt sau phải trừ tiếp trên kết quả của lượt trước, không đọc lại số cũ")
                .isEqualTo(1);
    }

    /**
     * Mẫu số của "tỷ lệ phế" là tồn kho THỰC TIÊU HAO, không phải tổng độ dài mọi phôi đã qua máy
     * cắt. Cùng kịch bản 8m → dư 6m nhập lại kho → 6m lại được cắt tiếp trong chính lượt đó: chỉ
     * đúng 4m rời kho vĩnh viễn (8m ra, 4m nằm lại), nên mẫu số phải là 4.00m. Công thức cũ cộng
     * thẳng 8000 + 6000 = 14.00m, đếm hai lần phần 6m trung gian và làm tỷ lệ phế thấp giả tạo.
     */
    @Test
    void generate_totalStockUsedM_excludesRemainderRestockedToInventory() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 8000, 1);
        persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());
        persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());

        CuttingPlan plan = approvePlan();

        assertThat(plan.getTotalStockUsedM())
                .as("8m ra kho, 4m nhập lại → chỉ 4m thực tiêu hao")
                .isEqualByComparingTo(new BigDecimal("4.00"));
        assertThat(plan.getTotalWasteM()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /**
     * Khoá chung một định nghĩa mẫu số cho cả tầng service lẫn migration tính lại dữ liệu cũ:
     * {@code Σ stickCount × (sourceLengthMm − remainderMm nếu RESTOCK)}. Kịch bản có đủ cả hai
     * nhánh — 1 loại thanh dư 0.2m (bỏ đi, tính trọn vào mẫu số) và 1 loại thanh dư 4m (nhập lại
     * kho, KHÔNG tính vào mẫu số) — nên nếu bỏ nhánh RESTOCK thì assertion sai ngay.
     */
    @Test
    void generate_totalStockUsedM_matchesRecomputeFormulaOverDetailRows() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial discardMaterial = persistSlatMaterial();
        SlatMaterial restockMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, discardMaterial);
        persistBomItem(doorProduct, restockMaterial);
        // 2200 − 2000 = 200mm: dưới 30cm nên cắt được và bỏ đi, tiêu hao trọn thanh.
        persistInventoryBatch(discardMaterial, 2200, 1);
        persistInventoryBatch(restockMaterial, 6000, 1);
        persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());

        CuttingPlan plan = approvePlan();

        int recomputedMm = cuttingPlanDetailRepository.findByCuttingPlan_Id(plan.getId()).stream()
                .mapToInt(detail -> detail.getStickCount()
                        * (detail.getSourceLengthMm()
                                - (detail.getRemainderType() == RemainderType.RESTOCK ? detail.getRemainderMm() : 0)))
                .sum();
        assertThat(recomputedMm).isEqualTo(4200);
        assertThat(plan.getTotalStockUsedM())
                .as("2.2m tiêu hao trọn + (6m − 4m nhập lại kho)")
                .isEqualByComparingTo(new BigDecimal("4.20"));
        assertThat(plan.getTotalWasteM()).isEqualByComparingTo(new BigDecimal("0.20"));
    }
}
