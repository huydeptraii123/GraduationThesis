package com.slatcut.cutting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.slatcut.cutting.AbstractIntegrationTest;
import com.slatcut.cutting.domain.BomItem;
import com.slatcut.cutting.domain.Customer;
import com.slatcut.cutting.domain.CuttingPlan;
import com.slatcut.cutting.domain.CuttingPlanDetail;
import com.slatcut.cutting.domain.CuttingPlanDetailItem;
import com.slatcut.cutting.domain.CuttingPlanStatus;
import com.slatcut.cutting.domain.DoorProduct;
import com.slatcut.cutting.domain.InventoryBatch;
import com.slatcut.cutting.domain.RemainderType;
import com.slatcut.cutting.domain.SalesOrder;
import com.slatcut.cutting.domain.ShortageRecord;
import com.slatcut.cutting.domain.SlatGroup;
import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.dto.CuttingPlanScopePreviewResponse;
import com.slatcut.cutting.repository.BomItemRepository;
import com.slatcut.cutting.repository.CustomerRepository;
import com.slatcut.cutting.repository.CuttingPlanDetailItemRepository;
import com.slatcut.cutting.repository.CuttingPlanDetailRepository;
import com.slatcut.cutting.repository.CuttingPlanRepository;
import com.slatcut.cutting.repository.DoorProductRepository;
import com.slatcut.cutting.repository.InventoryBatchRepository;
import com.slatcut.cutting.repository.SalesOrderRepository;
import com.slatcut.cutting.repository.ShortageRecordRepository;
import com.slatcut.cutting.repository.SlatMaterialRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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

    private long counter = 0;

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

    private void persistInventoryBatch(SlatMaterial slatMaterial, int lengthMm, int count) {
        InventoryBatch entity = new InventoryBatch();
        entity.setSlatMaterial(slatMaterial);
        entity.setDoDaiThanhMm(lengthMm);
        entity.setSoThanh(count);
        inventoryBatchRepository.save(entity);
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

        CuttingPlan plan = service.generate();

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

        service.generate();

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

        CuttingPlan plan = service.generate();

        assertThat(plan.getScopeOrderCount()).isEqualTo(0);
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

        service.generate();
        CuttingPlan second = service.generate();

        assertThat(second.getScopeOrderCount()).isEqualTo(0);
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

        CuttingPlan plan = service.generate();

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

        CuttingPlan plan = service.generate();

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

        CuttingPlan plan = service.generate();

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
        service.generate();

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

        CuttingPlan plan = service.generate();

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

        service.generate();

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

        service.generate();

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
    void getScopePreview_countsEligibleOrdersWithoutPersistingAnything() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());
        persistSalesOrder(
                "HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now().plusDays(10));

        CuttingPlanScopePreviewResponse preview = service.getScopePreview();

        assertThat(preview.eligibleOrderCount()).isEqualTo(1);
        assertThat(preview.scopeCutoffDate()).isEqualTo(LocalDate.now().plusDays(3));
        assertThat(cuttingPlanRepository.findAll()).isEmpty();
        assertThat(cuttingPlanDetailItemRepository.findAll()).isEmpty();
        assertThat(shortageRecordRepository.findAll()).isEmpty();
    }

    @Test
    void generate_decrementsConsumedInventory() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 2000, 3);
        persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());

        service.generate();

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

        service.generate();

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

        service.generate();

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

        service.generate();
        assertThat(cuttingPlanDetailRepository.findAll()).hasSize(1);

        SalesOrder secondOrder = persistSalesOrder(
                "HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());

        service.generate();

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

        CuttingPlan plan = service.generate();

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

        CuttingPlan plan = service.generate();

        assertThat(plan.getScopeOrderCount())
                .as("không đơn nào trong 3 kịch bản được đưa vào phạm vi xử lý")
                .isZero();
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
        service.generate();
        assertThat(inventoryBatchRepository
                        .findBySlatMaterial_IdAndDoDaiThanhMm(slatMaterial.getId(), 2000)
                        .orElseThrow()
                        .getSoThanh())
                .isEqualTo(2);

        persistSalesOrder("HY9" + (counter + 1), doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());
        service.generate();
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

        CuttingPlan plan = service.generate();

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

        CuttingPlan plan = service.generate();

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
