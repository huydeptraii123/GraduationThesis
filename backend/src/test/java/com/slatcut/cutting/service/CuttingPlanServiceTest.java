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
import com.slatcut.cutting.repository.BomItemRepository;
import com.slatcut.cutting.repository.CustomerRepository;
import com.slatcut.cutting.repository.CuttingPlanDetailItemRepository;
import com.slatcut.cutting.repository.CuttingPlanDetailRepository;
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
    }
}
