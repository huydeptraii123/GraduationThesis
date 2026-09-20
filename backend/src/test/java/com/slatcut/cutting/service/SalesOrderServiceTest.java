package com.slatcut.cutting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.slatcut.cutting.AbstractIntegrationTest;
import com.slatcut.cutting.config.ConflictException;
import com.slatcut.cutting.config.ResourceNotFoundException;
import com.slatcut.cutting.domain.Customer;
import com.slatcut.cutting.domain.CuttingPlan;
import com.slatcut.cutting.domain.CuttingPlanDetail;
import com.slatcut.cutting.domain.CuttingPlanDetailItem;
import com.slatcut.cutting.domain.CuttingPlanStatus;
import com.slatcut.cutting.domain.DoorProduct;
import com.slatcut.cutting.domain.RemainderType;
import com.slatcut.cutting.domain.SalesOrder;
import com.slatcut.cutting.domain.ShortageRecord;
import com.slatcut.cutting.domain.SlatGroup;
import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.dto.PageResponse;
import com.slatcut.cutting.dto.SalesOrderRequest;
import com.slatcut.cutting.dto.SalesOrderResponse;
import com.slatcut.cutting.repository.CustomerRepository;
import com.slatcut.cutting.repository.CuttingPlanDetailItemRepository;
import com.slatcut.cutting.repository.CuttingPlanDetailRepository;
import com.slatcut.cutting.repository.CuttingPlanRepository;
import com.slatcut.cutting.repository.DoorProductRepository;
import com.slatcut.cutting.repository.SalesOrderRepository;
import com.slatcut.cutting.repository.ShortageRecordRepository;
import com.slatcut.cutting.repository.SlatMaterialRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class SalesOrderServiceTest extends AbstractIntegrationTest {

    @Autowired
    private SalesOrderService service;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private DoorProductRepository doorProductRepository;

    @Autowired
    private SlatMaterialRepository slatMaterialRepository;

    @Autowired
    private CuttingPlanRepository cuttingPlanRepository;

    @Autowired
    private CuttingPlanDetailRepository cuttingPlanDetailRepository;

    @Autowired
    private CuttingPlanDetailItemRepository cuttingPlanDetailItemRepository;

    @Autowired
    private ShortageRecordRepository shortageRecordRepository;

    private Customer persistCustomer(long code, String name) {
        Customer entity = new Customer();
        entity.setCustomer(code);
        entity.setCustomerName(name);
        return customerRepository.save(entity);
    }

    private DoorProduct persistDoorProduct(long material, String mauSac) {
        DoorProduct entity = new DoorProduct();
        entity.setMaterial(material);
        entity.setDoorMaterialName("Cửa cuốn " + material);
        entity.setMauSac(mauSac);
        return doorProductRepository.save(entity);
    }

    private SalesOrder persistOrder(
            String ycsx, int item, long salesDocument, int salesOrderItem, Customer customer, DoorProduct doorProduct) {
        return persistOrder(
                ycsx, item, salesDocument, salesOrderItem, customer, doorProduct, LocalDate.of(2026, 9, 28));
    }

    private SalesOrder persistOrder(
            String ycsx,
            int item,
            long salesDocument,
            int salesOrderItem,
            Customer customer,
            DoorProduct doorProduct,
            LocalDate reqdDeliveryDate) {
        SalesOrder entity = new SalesOrder();
        entity.setYcsx(ycsx);
        entity.setItem(item);
        entity.setSalesDocument(salesDocument);
        entity.setSalesOrderItem(salesOrderItem);
        entity.setCustomer(customer);
        entity.setDoorProduct(doorProduct);
        entity.setChieuCaoDh(new BigDecimal("2.500"));
        entity.setChieuRongDh(new BigDecimal("3.500"));
        entity.setReqdDeliveryDate(reqdDeliveryDate);
        return salesOrderRepository.save(entity);
    }

    private SalesOrderRequest request(
            String ycsx, int item, long salesDocument, int salesOrderItem, Long customerId, Long doorProductId) {
        SalesOrderRequest request = new SalesOrderRequest();
        request.setYcsx(ycsx);
        request.setItem(item);
        request.setSalesDocument(salesDocument);
        request.setSalesOrderItem(salesOrderItem);
        request.setCustomerId(customerId);
        request.setDoorProductId(doorProductId);
        request.setChieuCaoDh(new BigDecimal("2.500"));
        request.setChieuRongDh(new BigDecimal("3.500"));
        request.setReqdDeliveryDate(LocalDate.of(2026, 9, 28));
        return request;
    }

    @Test
    void create_persistsAllFieldsAndFlattensBothRelations() {
        Customer customer = persistCustomer(91000001L, "Khách hàng A");
        DoorProduct doorProduct = persistDoorProduct(83000001L, "#02");

        SalesOrderResponse response =
                service.create(request("HY90001", 10, 1000900001L, 1, customer.getId(), doorProduct.getId()));

        assertThat(response.id()).isNotNull();
        assertThat(response.ycsx()).isEqualTo("HY90001");
        assertThat(response.item()).isEqualTo(10);
        assertThat(response.salesDocument()).isEqualTo(1000900001L);
        assertThat(response.salesOrderItem()).isEqualTo(1);
        assertThat(response.customerId()).isEqualTo(customer.getId());
        assertThat(response.customerName()).isEqualTo("Khách hàng A");
        assertThat(response.doorProductId()).isEqualTo(doorProduct.getId());
        assertThat(response.doorProductMauSac()).isEqualTo("#02");
        assertThat(response.chieuCaoDh()).isEqualByComparingTo("2.500");
        assertThat(response.reqdDeliveryDate()).isEqualTo(LocalDate.of(2026, 9, 28));
    }

    @Test
    void create_throwsConflictWhenYcsxItemAlreadyExists() {
        Customer customer = persistCustomer(91000002L, "Khách hàng B");
        DoorProduct doorProduct = persistDoorProduct(83000002L, "#02");
        persistOrder("HY90002", 20, 1000900002L, 1, customer, doorProduct);

        assertThatThrownBy(() -> service.create(
                        request("HY90002", 20, 1000900099L, 9, customer.getId(), doorProduct.getId())))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ycsx");
    }

    @Test
    void create_throwsConflictWhenSalesDocumentItemAlreadyExists() {
        Customer customer = persistCustomer(91000003L, "Khách hàng C");
        DoorProduct doorProduct = persistDoorProduct(83000003L, "#02");
        persistOrder("HY90003", 30, 1000900003L, 1, customer, doorProduct);

        assertThatThrownBy(() -> service.create(
                        request("HY90099", 99, 1000900003L, 1, customer.getId(), doorProduct.getId())))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("sales_document");
    }

    @Test
    void create_allowsSameYcsxWithDifferentItem() {
        Customer customer = persistCustomer(91000004L, "Khách hàng D");
        DoorProduct doorProduct = persistDoorProduct(83000004L, "#02");
        persistOrder("HY90004", 40, 1000900004L, 1, customer, doorProduct);

        SalesOrderResponse response = service.create(
                request("HY90004", 41, 1000900005L, 2, customer.getId(), doorProduct.getId()));

        assertThat(response.item()).isEqualTo(41);
    }

    @Test
    void create_allowsTwoManualOrdersWithoutSapFields() {
        // Đơn tạo thủ công qua UI (không qua import Excel) không có sales_document/sales_order_item —
        // 2 đơn khác nhau cùng để trống 2 field này không được coi là trùng (JPA tự chuyển null
        // thành "IS NULL" trong query derivation, dễ báo trùng nhầm nếu service không tự chặn trước).
        Customer customer = persistCustomer(91000020L, "Khách hàng thủ công");
        DoorProduct doorProduct = persistDoorProduct(83000020L, "#02");

        SalesOrderRequest first = request("HY90020", 1, 0, 0, customer.getId(), doorProduct.getId());
        first.setSalesDocument(null);
        first.setSalesOrderItem(null);
        SalesOrderRequest second = request("HY90020", 2, 0, 0, customer.getId(), doorProduct.getId());
        second.setSalesDocument(null);
        second.setSalesOrderItem(null);

        service.create(first);
        SalesOrderResponse response = service.create(second);

        assertThat(response.salesDocument()).isNull();
        assertThat(response.salesOrderItem()).isNull();
    }

    @Test
    void create_throwsNotFoundNamingCustomerWhenItDoesNotExist() {
        DoorProduct doorProduct = persistDoorProduct(83000005L, "#02");

        assertThatThrownBy(() ->
                        service.create(request("HY90005", 1, 1000900006L, 1, 999_999L, doorProduct.getId())))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("khách hàng");
    }

    @Test
    void create_throwsNotFoundNamingDoorProductWhenItDoesNotExist() {
        Customer customer = persistCustomer(91000006L, "Khách hàng E");

        assertThatThrownBy(() ->
                        service.create(request("HY90006", 1, 1000900007L, 1, customer.getId(), 999_999L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("mẫu cửa");
    }

    @Test
    void getById_throwsNotFoundForUnknownId() {
        assertThatThrownBy(() -> service.getById(999_999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("đơn hàng");
    }

    @Test
    void getPage_returnsEveryPersistedOrder() {
        Customer customer = persistCustomer(91000007L, "Khách hàng F");
        DoorProduct doorProduct = persistDoorProduct(83000006L, "#02");
        persistOrder("HY90007", 1, 1000900008L, 1, customer, doorProduct);
        persistOrder("HY90007", 2, 1000900009L, 1, customer, doorProduct);

        assertThat(service.getPage("HY90007", null, null, null, PageRequest.of(0, 20)).content())
                .extracting(SalesOrderResponse::ycsx)
                .contains("HY90007");
    }

    @Test
    void getPage_splitsResultAcrossPagesInDeliveryPriorityOrder() {
        Customer customer = persistCustomer(91100000L, "Khách phân trang");
        DoorProduct doorProduct = persistDoorProduct(83100000L, "#09");
        // Chèn ngày giao GIẢM DẦN để nếu mất sắp xếp thì thứ tự trả về sẽ khác hẳn kỳ vọng.
        for (int index = 0; index < 25; index++) {
            persistOrder(
                    "PT%05d".formatted(index),
                    1,
                    1001000000L + index,
                    1,
                    customer,
                    doorProduct,
                    LocalDate.of(2026, 10, 1).plusDays(24 - index));
        }
        Pageable byPriority = PageRequest.of(0, 10, Sort.by("reqdDeliveryDate", "ycsx", "item"));

        PageResponse<SalesOrderResponse> first = service.getPage("Khách phân trang", null, null, null, byPriority);
        PageResponse<SalesOrderResponse> second =
                service.getPage("Khách phân trang", null, null, null, byPriority.withPage(1));
        PageResponse<SalesOrderResponse> last =
                service.getPage("Khách phân trang", null, null, null, byPriority.withPage(2));

        assertThat(first.totalElements()).isEqualTo(25);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.content()).hasSize(10);
        assertThat(last.content()).hasSize(5);
        assertThat(first.content().getFirst().reqdDeliveryDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(first.content().getLast().reqdDeliveryDate()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(second.content().getFirst().reqdDeliveryDate()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(last.content().getLast().reqdDeliveryDate()).isEqualTo(LocalDate.of(2026, 10, 25));
    }

    @Test
    void getPage_filtersByCustomerAndDeliveryRange() {
        Customer target = persistCustomer(91200001L, "Khách lọc đích");
        Customer other = persistCustomer(91200002L, "Khách lọc khác");
        DoorProduct doorProduct = persistDoorProduct(83200000L, "#10");
        persistOrder("LOC0001", 1, 1001100001L, 1, target, doorProduct, LocalDate.of(2026, 11, 5));
        persistOrder("LOC0002", 1, 1001100002L, 1, target, doorProduct, LocalDate.of(2026, 11, 20));
        persistOrder("LOC0003", 1, 1001100003L, 1, other, doorProduct, LocalDate.of(2026, 11, 5));
        Pageable firstPage = PageRequest.of(0, 20);

        PageResponse<SalesOrderResponse> byCustomer =
                service.getPage(null, target.getId(), null, null, firstPage);
        PageResponse<SalesOrderResponse> byRange = service.getPage(
                null, target.getId(), LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 30), firstPage);

        assertThat(byCustomer.totalElements()).isEqualTo(2);
        assertThat(byRange.totalElements()).isEqualTo(1);
        assertThat(byRange.content().getFirst().ycsx()).isEqualTo("LOC0002");
    }

    @Test
    void update_movesOrderToAnotherCustomerAndDoorProduct() {
        Customer source = persistCustomer(91000008L, "Khách nguồn");
        Customer target = persistCustomer(91000009L, "Khách đích");
        DoorProduct sourceProduct = persistDoorProduct(83000007L, "#02");
        DoorProduct targetProduct = persistDoorProduct(83000008L, "#03");
        SalesOrder existing = persistOrder("HY90008", 1, 1000900010L, 1, source, sourceProduct);

        SalesOrderResponse response = service.update(
                existing.getId(),
                request("HY90008", 1, 1000900010L, 1, target.getId(), targetProduct.getId()));

        assertThat(response.customerId()).isEqualTo(target.getId());
        assertThat(response.doorProductId()).isEqualTo(targetProduct.getId());
    }

    @Test
    void update_keepingOwnYcsxItemIsNotTreatedAsDuplicate() {
        Customer customer = persistCustomer(91000010L, "Khách hàng G");
        DoorProduct doorProduct = persistDoorProduct(83000009L, "#02");
        SalesOrder existing = persistOrder("HY90009", 1, 1000900011L, 1, customer, doorProduct);

        SalesOrderResponse response = service.update(
                existing.getId(),
                request("HY90009", 1, 1000900011L, 1, customer.getId(), doorProduct.getId()));

        assertThat(response.ycsx()).isEqualTo("HY90009");
    }

    @Test
    void update_keepingOwnSalesDocumentItemIsNotTreatedAsDuplicate() {
        Customer customer = persistCustomer(91000011L, "Khách hàng H");
        DoorProduct doorProduct = persistDoorProduct(83000010L, "#02");
        SalesOrder existing = persistOrder("HY90010", 1, 1000900012L, 1, customer, doorProduct);

        SalesOrderResponse response = service.update(
                existing.getId(),
                request("HY90010", 1, 1000900012L, 1, customer.getId(), doorProduct.getId()));

        assertThat(response.salesDocument()).isEqualTo(1000900012L);
    }

    @Test
    void update_throwsConflictWhenYcsxItemBelongsToAnotherOrder() {
        Customer customer = persistCustomer(91000012L, "Khách hàng I");
        DoorProduct doorProduct = persistDoorProduct(83000011L, "#02");
        SalesOrder first = persistOrder("HY90011", 1, 1000900013L, 1, customer, doorProduct);
        persistOrder("HY90011", 2, 1000900014L, 1, customer, doorProduct);

        assertThatThrownBy(() -> service.update(
                        first.getId(),
                        request("HY90011", 2, 1000900099L, 9, customer.getId(), doorProduct.getId())))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void update_throwsConflictWhenSalesDocumentItemBelongsToAnotherOrder() {
        Customer customer = persistCustomer(91000013L, "Khách hàng K");
        DoorProduct doorProduct = persistDoorProduct(83000012L, "#02");
        SalesOrder first = persistOrder("HY90012", 1, 1000900015L, 1, customer, doorProduct);
        persistOrder("HY90013", 1, 1000900016L, 1, customer, doorProduct);

        assertThatThrownBy(() -> service.update(
                        first.getId(),
                        request("HY90099", 99, 1000900016L, 1, customer.getId(), doorProduct.getId())))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void update_throwsNotFoundForUnknownId() {
        Customer customer = persistCustomer(91000014L, "Khách hàng L");
        DoorProduct doorProduct = persistDoorProduct(83000013L, "#02");

        assertThatThrownBy(() -> service.update(
                        999_999L, request("HY90014", 1, 1000900017L, 1, customer.getId(), doorProduct.getId())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_throwsConflictWhenOrderBelongsToApprovedPlan() {
        Customer customer = persistCustomer(91000018L, "Khách hàng P");
        DoorProduct doorProduct = persistDoorProduct(83000017L, "#02");
        SalesOrder existing = persistOrder("HY90018", 1, 1000900021L, 1, customer, doorProduct);
        existing.setApprovedPlan(persistCuttingPlan());
        salesOrderRepository.saveAndFlush(existing);

        assertThatThrownBy(() -> service.update(
                        existing.getId(),
                        request("HY90018", 1, 1000900021L, 1, customer.getId(), doorProduct.getId())))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("sửa");
    }

    @Test
    void delete_removesOrderWhenNotYetApproved() {
        Customer customer = persistCustomer(91000015L, "Khách hàng M");
        DoorProduct doorProduct = persistDoorProduct(83000014L, "#02");
        SalesOrder existing = persistOrder("HY90015", 1, 1000900018L, 1, customer, doorProduct);

        service.delete(existing.getId());

        assertThat(salesOrderRepository.findById(existing.getId())).isEmpty();
    }

    @Test
    void delete_throwsNotFoundForUnknownId() {
        assertThatThrownBy(() -> service.delete(999_999L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_throwsConflictWhenOrderBelongsToApprovedPlan() {
        Customer customer = persistCustomer(91000016L, "Khách hàng N");
        DoorProduct doorProduct = persistDoorProduct(83000015L, "#02");
        SalesOrder existing = persistOrder("HY90016", 1, 1000900019L, 1, customer, doorProduct);
        CuttingPlan plan = persistCuttingPlan();
        CuttingPlanDetail detail = persistCuttingPlanDetail(plan, persistSlatMaterial(70000015L));
        persistCuttingPlanDetailItem(detail, existing);
        existing.setApprovedPlan(plan);
        salesOrderRepository.saveAndFlush(existing);

        assertThatThrownBy(() -> service.delete(existing.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("#" + plan.getId());
        assertThat(salesOrderRepository.findById(existing.getId())).isPresent();
    }

    /**
     * Guard đọc approvedPlan chứ không dò hai bảng con — ca này chứng minh sự khác biệt đó là có
     * thật, không phải cách viết lại cùng một phép kiểm: đơn chỉ bị báo thiếu vật tư (không có
     * CuttingPlanDetailItem nào) vẫn bị chặn xóa, vì nó vẫn nằm trong một phương án đã duyệt.
     */
    @Test
    void delete_throwsConflictWhenApprovedOrderOnlyProducedShortage() {
        Customer customer = persistCustomer(91000017L, "Khách hàng O");
        DoorProduct doorProduct = persistDoorProduct(83000016L, "#02");
        SalesOrder existing = persistOrder("HY90017", 1, 1000900020L, 1, customer, doorProduct);
        CuttingPlan plan = persistCuttingPlan();
        persistShortageRecord(plan, existing, persistSlatMaterial(70000016L));
        existing.setApprovedPlan(plan);
        salesOrderRepository.saveAndFlush(existing);

        assertThatThrownBy(() -> service.delete(existing.getId())).isInstanceOf(ConflictException.class);
        assertThat(salesOrderRepository.findById(existing.getId())).isPresent();
    }

    private SlatMaterial persistSlatMaterial(long code) {
        SlatMaterial entity = new SlatMaterial();
        entity.setSlatMaterial(code);
        entity.setSlatMaterialName("Thanh nan " + code);
        entity.setSlatGroup(SlatGroup.BOTTOM_BAR);
        return slatMaterialRepository.save(entity);
    }

    private CuttingPlan persistCuttingPlan() {
        CuttingPlan plan = new CuttingPlan();
        plan.setRunAt(LocalDateTime.now());
        plan.setStatus(CuttingPlanStatus.COMPLETED);
        plan.setScopeCutoffDate(LocalDate.now().plusDays(3));
        plan.setScopeOrderCount(1);
        plan.setTotalWasteM(BigDecimal.ZERO);
        plan.setTotalStockUsedM(BigDecimal.ZERO);
        return cuttingPlanRepository.save(plan);
    }

    private CuttingPlanDetail persistCuttingPlanDetail(CuttingPlan plan, SlatMaterial slatMaterial) {
        CuttingPlanDetail detail = new CuttingPlanDetail();
        detail.setCuttingPlan(plan);
        detail.setSlatMaterial(slatMaterial);
        detail.setSourceLengthMm(2000);
        detail.setPatternCode("2000=2000x1+R0");
        detail.setRemainderMm(0);
        detail.setRemainderType(RemainderType.DISCARDED);
        detail.setStickCount(1);
        return cuttingPlanDetailRepository.save(detail);
    }

    private void persistCuttingPlanDetailItem(CuttingPlanDetail detail, SalesOrder salesOrder) {
        CuttingPlanDetailItem item = new CuttingPlanDetailItem();
        item.setCuttingPlanDetail(detail);
        item.setSalesOrder(salesOrder);
        item.setCutLengthMm(2000);
        item.setCutQuantity(1);
        item.setOriginalOrder(true);
        cuttingPlanDetailItemRepository.save(item);
    }

    private void persistShortageRecord(CuttingPlan plan, SalesOrder salesOrder, SlatMaterial slatMaterial) {
        ShortageRecord record = new ShortageRecord();
        record.setCuttingPlan(plan);
        record.setSalesOrder(salesOrder);
        record.setSlatMaterial(slatMaterial);
        record.setMissingQuantity(1);
        record.setMissingLengthM(new BigDecimal("2.00"));
        shortageRecordRepository.save(record);
    }
}
