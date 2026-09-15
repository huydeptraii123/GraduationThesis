package com.slatcut.cutting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.slatcut.cutting.AbstractIntegrationTest;
import com.slatcut.cutting.config.ConflictException;
import com.slatcut.cutting.config.ResourceNotFoundException;
import com.slatcut.cutting.domain.Customer;
import com.slatcut.cutting.domain.DoorProduct;
import com.slatcut.cutting.domain.SalesOrder;
import com.slatcut.cutting.dto.SalesOrderRequest;
import com.slatcut.cutting.dto.SalesOrderResponse;
import com.slatcut.cutting.repository.CustomerRepository;
import com.slatcut.cutting.repository.DoorProductRepository;
import com.slatcut.cutting.repository.SalesOrderRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SalesOrderServiceTest extends AbstractIntegrationTest {

    @Autowired
    private SalesOrderService service;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private DoorProductRepository doorProductRepository;

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
        SalesOrder entity = new SalesOrder();
        entity.setYcsx(ycsx);
        entity.setItem(item);
        entity.setSalesDocument(salesDocument);
        entity.setSalesOrderItem(salesOrderItem);
        entity.setCustomer(customer);
        entity.setDoorProduct(doorProduct);
        entity.setChieuCaoDh(new BigDecimal("2.500"));
        entity.setChieuRongDh(new BigDecimal("3.500"));
        entity.setReqdDeliveryDate(LocalDate.of(2026, 9, 28));
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
    void getAll_returnsEveryPersistedOrder() {
        Customer customer = persistCustomer(91000007L, "Khách hàng F");
        DoorProduct doorProduct = persistDoorProduct(83000006L, "#02");
        persistOrder("HY90007", 1, 1000900008L, 1, customer, doorProduct);
        persistOrder("HY90007", 2, 1000900009L, 1, customer, doorProduct);

        assertThat(service.getAll()).extracting(SalesOrderResponse::ycsx).contains("HY90007");
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
    void delete_removesOrderWithoutGuard() {
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
}
