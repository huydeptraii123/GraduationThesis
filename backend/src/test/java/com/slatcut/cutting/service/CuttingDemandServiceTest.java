package com.slatcut.cutting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.slatcut.cutting.AbstractIntegrationTest;
import com.slatcut.cutting.domain.BomItem;
import com.slatcut.cutting.domain.Customer;
import com.slatcut.cutting.domain.DoorProduct;
import com.slatcut.cutting.domain.SalesOrder;
import com.slatcut.cutting.domain.SlatGroup;
import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.repository.BomItemRepository;
import com.slatcut.cutting.repository.CustomerRepository;
import com.slatcut.cutting.repository.DoorProductRepository;
import com.slatcut.cutting.repository.SalesOrderRepository;
import com.slatcut.cutting.repository.SlatMaterialRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CuttingDemandServiceTest extends AbstractIntegrationTest {

    @Autowired
    private CuttingDemandService service;

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
        entity.setMauSac("#0" + (counter % 9 + 1));
        return doorProductRepository.save(entity);
    }

    private SlatMaterial persistSlatMaterial(SlatGroup group) {
        SlatMaterial entity = new SlatMaterial();
        entity.setSlatMaterial(70_000_000L + ++counter);
        entity.setSlatMaterialName("Thanh nan " + counter);
        entity.setSlatGroup(group);
        return slatMaterialRepository.save(entity);
    }

    private BomItem persistBomItem(
            DoorProduct doorProduct,
            SlatMaterial slatMaterial,
            BigDecimal widthOffsetM,
            BigDecimal heightOffsetM,
            BigDecimal slatCountSlope,
            BigDecimal slatCountIntercept) {
        BomItem entity = new BomItem();
        entity.setDoorProduct(doorProduct);
        entity.setSlatMaterial(slatMaterial);
        entity.setWidthOffsetM(widthOffsetM);
        entity.setHeightOffsetM(heightOffsetM);
        entity.setSlatCountSlope(slatCountSlope);
        entity.setSlatCountIntercept(slatCountIntercept);
        return bomItemRepository.save(entity);
    }

    private SalesOrder persistSalesOrder(DoorProduct doorProduct, Customer customer, BigDecimal chieuCaoDh, BigDecimal chieuRongDh) {
        SalesOrder entity = new SalesOrder();
        entity.setYcsx("HY9" + ++counter);
        entity.setItem((int) counter);
        entity.setCustomer(customer);
        entity.setDoorProduct(doorProduct);
        entity.setChieuCaoDh(chieuCaoDh);
        entity.setChieuRongDh(chieuRongDh);
        entity.setReqdDeliveryDate(LocalDate.of(2026, 9, 28));
        return salesOrderRepository.save(entity);
    }

    @Test
    void buildDemands_mainSlatWithWidthOffset_subtractsOffsetAndComputesQuantity() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial(SlatGroup.MAIN_SLAT);
        persistBomItem(
                doorProduct,
                slatMaterial,
                new BigDecimal("0.024"),
                null,
                new BigDecimal("2.000000"),
                new BigDecimal("1.0000"));
        SalesOrder order = persistSalesOrder(doorProduct, customer, new BigDecimal("2.500"), new BigDecimal("3.150"));

        List<CuttingDemand> demands = service.buildDemands(List.of(order));

        assertThat(demands).hasSize(1);
        CuttingDemand demand = demands.get(0);
        assertThat(demand.cutLengthMm()).isEqualTo(3126); // 3.150 - 0.024 = 3.126m
        assertThat(demand.quantity()).isEqualTo(6); // 2*2.500 + 1 = 6
        assertThat(demand.slatMaterial().getId()).isEqualTo(slatMaterial.getId());
        assertThat(demand.ycsx()).isEqualTo(order.getYcsx());
        assertThat(demand.item()).isEqualTo(order.getItem());
        assertThat(demand.reqdDeliveryDate()).isEqualTo(order.getReqdDeliveryDate());
    }

    @Test
    void buildDemands_mainSlatMissingWidthOffset_usesFallbackRatio() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial(SlatGroup.MAIN_SLAT);
        persistBomItem(doorProduct, slatMaterial, null, null, new BigDecimal("1.000000"), new BigDecimal("0.0000"));
        SalesOrder order = persistSalesOrder(doorProduct, customer, new BigDecimal("4.000"), new BigDecimal("3.000"));

        List<CuttingDemand> demands = service.buildDemands(List.of(order));

        assertThat(demands).hasSize(1);
        CuttingDemand demand = demands.get(0);
        assertThat(demand.cutLengthMm()).isEqualTo(2928); // 3.000 * 0.976 = 2.928m
        assertThat(demand.quantity()).isEqualTo(4); // 1*4.000 + 0 = 4
    }

    @Test
    void buildDemands_mainSlatMissingSlopeOrIntercept_skipsWithoutError() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial(SlatGroup.MAIN_SLAT);
        persistBomItem(doorProduct, slatMaterial, new BigDecimal("0.024"), null, null, new BigDecimal("1.0000"));
        SalesOrder order = persistSalesOrder(doorProduct, customer, new BigDecimal("2.500"), new BigDecimal("3.150"));

        List<CuttingDemand> demands = service.buildDemands(List.of(order));

        assertThat(demands).isEmpty();
    }

    @Test
    void buildDemands_bottomBarAndSubSlat_useDoorWidthDirectlyWithQuantityOne() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial bottomBar = persistSlatMaterial(SlatGroup.BOTTOM_BAR);
        SlatMaterial subSlat = persistSlatMaterial(SlatGroup.SUB_SLAT);
        persistBomItem(doorProduct, bottomBar, null, null, null, null);
        persistBomItem(doorProduct, subSlat, null, null, null, null);
        SalesOrder order = persistSalesOrder(doorProduct, customer, new BigDecimal("2.500"), new BigDecimal("2.800"));

        List<CuttingDemand> demands = service.buildDemands(List.of(order));

        assertThat(demands)
                .hasSize(2)
                .extracting(CuttingDemand::cutLengthMm, CuttingDemand::quantity)
                .containsOnly(tuple(2800, 1));
    }

    @Test
    void buildDemands_railWithHeightOffset_subtractsOffsetWithQuantityTwo() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial(SlatGroup.RAIL);
        persistBomItem(doorProduct, slatMaterial, null, new BigDecimal("0.050"), null, null);
        SalesOrder order = persistSalesOrder(doorProduct, customer, new BigDecimal("2.500"), new BigDecimal("3.150"));

        List<CuttingDemand> demands = service.buildDemands(List.of(order));

        assertThat(demands).hasSize(1);
        CuttingDemand demand = demands.get(0);
        assertThat(demand.cutLengthMm()).isEqualTo(2450); // 2.500 - 0.050 = 2.450m
        assertThat(demand.quantity()).isEqualTo(2);
    }

    @Test
    void buildDemands_railMissingHeightOffset_skipsWithoutError() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial(SlatGroup.RAIL);
        persistBomItem(doorProduct, slatMaterial, null, null, null, null);
        SalesOrder order = persistSalesOrder(doorProduct, customer, new BigDecimal("2.500"), new BigDecimal("3.150"));

        List<CuttingDemand> demands = service.buildDemands(List.of(order));

        assertThat(demands).isEmpty();
    }

    @Test
    void buildDemands_otherGroup_alwaysSkipped() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial(SlatGroup.OTHER);
        persistBomItem(
                doorProduct,
                slatMaterial,
                new BigDecimal("0.024"),
                new BigDecimal("0.050"),
                new BigDecimal("2.000000"),
                new BigDecimal("1.0000"));
        SalesOrder order = persistSalesOrder(doorProduct, customer, new BigDecimal("2.500"), new BigDecimal("3.150"));

        List<CuttingDemand> demands = service.buildDemands(List.of(order));

        assertThat(demands).isEmpty();
    }

    @Test
    void buildDemands_doorProductWithMultipleBomItems_returnsOneDemandPerBomItem() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial mainSlat = persistSlatMaterial(SlatGroup.MAIN_SLAT);
        SlatMaterial rail = persistSlatMaterial(SlatGroup.RAIL);
        SlatMaterial bottomBar = persistSlatMaterial(SlatGroup.BOTTOM_BAR);
        persistBomItem(
                doorProduct, mainSlat, new BigDecimal("0.024"), null, new BigDecimal("2.000000"), new BigDecimal("1.0000"));
        persistBomItem(doorProduct, rail, null, new BigDecimal("0.050"), null, null);
        persistBomItem(doorProduct, bottomBar, null, null, null, null);
        SalesOrder order = persistSalesOrder(doorProduct, customer, new BigDecimal("2.500"), new BigDecimal("3.150"));

        List<CuttingDemand> demands = service.buildDemands(List.of(order));

        assertThat(demands).hasSize(3);
        assertThat(demands).extracting(d -> d.slatMaterial().getId())
                .containsExactlyInAnyOrder(mainSlat.getId(), rail.getId(), bottomBar.getId());
    }

    @Test
    void buildDemands_doorProductWithoutAnyBomItem_producesNoDemandsWithoutError() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SalesOrder order = persistSalesOrder(doorProduct, customer, new BigDecimal("2.500"), new BigDecimal("3.150"));

        List<CuttingDemand> demands = service.buildDemands(List.of(order));

        assertThat(demands).isEmpty();
    }

    @Test
    void buildDemands_multipleOrdersWithDifferentDoorProducts_joinsCorrectlyWithoutCrossContamination() {
        Customer customer = persistCustomer();

        DoorProduct doorProductA = persistDoorProduct();
        SlatMaterial slatA = persistSlatMaterial(SlatGroup.BOTTOM_BAR);
        persistBomItem(doorProductA, slatA, null, null, null, null);
        SalesOrder orderA = persistSalesOrder(doorProductA, customer, new BigDecimal("2.500"), new BigDecimal("2.000"));

        DoorProduct doorProductB = persistDoorProduct();
        SlatMaterial slatB = persistSlatMaterial(SlatGroup.SUB_SLAT);
        persistBomItem(doorProductB, slatB, null, null, null, null);
        SalesOrder orderB = persistSalesOrder(doorProductB, customer, new BigDecimal("2.500"), new BigDecimal("4.000"));

        List<CuttingDemand> demands = service.buildDemands(List.of(orderA, orderB));

        assertThat(demands)
                .hasSize(2)
                .extracting(d -> d.slatMaterial().getId(), CuttingDemand::cutLengthMm)
                .containsExactlyInAnyOrder(tuple(slatA.getId(), 2000), tuple(slatB.getId(), 4000));
    }

    @Test
    void buildDemands_roundsQuantityHalfUpAtBoundary() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial(SlatGroup.MAIN_SLAT);
        // 1 * 2.500 + 1.000 = 3.5 -> HALF_UP phải làm tròn lên 4, không phải 3.
        persistBomItem(
                doorProduct,
                slatMaterial,
                new BigDecimal("0.000"),
                null,
                new BigDecimal("1.000000"),
                new BigDecimal("1.0000"));
        SalesOrder order = persistSalesOrder(doorProduct, customer, new BigDecimal("2.500"), new BigDecimal("3.000"));

        List<CuttingDemand> demands = service.buildDemands(List.of(order));

        assertThat(demands).hasSize(1);
        assertThat(demands.get(0).quantity()).isEqualTo(4);
    }

    @Test
    void buildDemands_emptyOrderList_returnsEmptyListWithoutQuerying() {
        assertThat(service.buildDemands(List.of())).isEmpty();
    }
}
