package com.slatcut.cutting.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.slatcut.cutting.AbstractIntegrationTest;
import com.slatcut.cutting.domain.BomItem;
import com.slatcut.cutting.domain.Customer;
import com.slatcut.cutting.domain.CuttingPlan;
import com.slatcut.cutting.domain.CuttingPlanStatus;
import com.slatcut.cutting.domain.DoorProduct;
import com.slatcut.cutting.domain.InventoryBatch;
import com.slatcut.cutting.domain.SalesOrder;
import com.slatcut.cutting.domain.SlatGroup;
import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.repository.BomItemRepository;
import com.slatcut.cutting.repository.CustomerRepository;
import com.slatcut.cutting.repository.CuttingPlanRepository;
import com.slatcut.cutting.repository.DoorProductRepository;
import com.slatcut.cutting.repository.InventoryBatchRepository;
import com.slatcut.cutting.repository.SalesOrderRepository;
import com.slatcut.cutting.repository.SlatMaterialRepository;
import com.slatcut.cutting.security.JwtService;
import com.slatcut.cutting.service.CuttingPlanService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Test tầng controller đầu tiên trong dự án — MockMvc thật (Spring Security bật), JWT tự sinh qua JwtService, không qua /auth/login. */
@AutoConfigureMockMvc
class CuttingPlanControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CuttingPlanService service;

    @Autowired
    private CuttingPlanRepository cuttingPlanRepository;

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

    private long counter = 0;

    private String plannerToken() {
        return jwtService.generateToken("planner", "PLANNER");
    }

    private String adminToken() {
        return jwtService.generateToken("admin", "ADMIN");
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

    private void persistBomItem(DoorProduct doorProduct, SlatMaterial slatMaterial) {
        BomItem entity = new BomItem();
        entity.setDoorProduct(doorProduct);
        entity.setSlatMaterial(slatMaterial);
        bomItemRepository.save(entity);
    }

    private SalesOrder persistSalesOrder(DoorProduct doorProduct, Customer customer, BigDecimal chieuRongDh) {
        SalesOrder entity = new SalesOrder();
        entity.setYcsx("HY9" + ++counter);
        entity.setItem((int) counter);
        entity.setCustomer(customer);
        entity.setDoorProduct(doorProduct);
        entity.setChieuCaoDh(new BigDecimal("2.500"));
        entity.setChieuRongDh(chieuRongDh);
        entity.setReqdDeliveryDate(LocalDate.now());
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
    void generate_asPlanner_returnsNestedDetailsAndShortages() throws Exception {
        Customer customer = persistCustomer();

        DoorProduct sufficientProduct = persistDoorProduct();
        SlatMaterial sufficientMaterial = persistSlatMaterial();
        persistBomItem(sufficientProduct, sufficientMaterial);
        persistInventoryBatch(sufficientMaterial, 2000, 1);
        SalesOrder sufficientOrder = persistSalesOrder(sufficientProduct, customer, new BigDecimal("2.000"));

        DoorProduct shortageProduct = persistDoorProduct();
        SlatMaterial shortageMaterial = persistSlatMaterial();
        persistBomItem(shortageProduct, shortageMaterial);
        SalesOrder shortageOrder = persistSalesOrder(shortageProduct, customer, new BigDecimal("5.000"));

        mockMvc.perform(post("/api/v1/cutting-plans/generate")
                        .header("Authorization", "Bearer " + plannerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopeOrderCount").value(2))
                .andExpect(jsonPath("$.details.length()").value(1))
                .andExpect(jsonPath("$.details[0].sourceLengthMm").value(2000))
                .andExpect(jsonPath("$.details[0].items[0].salesOrderId").value(sufficientOrder.getId()))
                .andExpect(jsonPath("$.details[0].items[0].customerName").value(customer.getCustomerName()))
                .andExpect(jsonPath("$.details[0].items[0].doorProductName").value(sufficientProduct.getDoorMaterialName()))
                .andExpect(jsonPath("$.shortages.length()").value(1))
                .andExpect(jsonPath("$.shortages[0].salesOrderId").value(shortageOrder.getId()))
                .andExpect(jsonPath("$.shortages[0].missingLengthM").value(5.00))
                .andExpect(jsonPath("$.shortages[0].doorProductName").value(shortageProduct.getDoorMaterialName()));
    }

    @Test
    void generate_asAdmin_isForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/cutting-plans/generate").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void generate_withoutToken_isForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/cutting-plans/generate")).andExpect(status().isForbidden());
    }

    @Test
    void getById_returnsPersistedPlanWithNestedStructure() throws Exception {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 2000, 1);
        persistSalesOrder(doorProduct, customer, new BigDecimal("2.000"));

        CuttingPlan plan = service.generate();

        mockMvc.perform(get("/api/v1/cutting-plans/{id}", plan.getId())
                        .header("Authorization", "Bearer " + plannerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(plan.getId()))
                .andExpect(jsonPath("$.scopeOrderCount").value(1))
                .andExpect(jsonPath("$.totalStockUsedM").value(2.00))
                .andExpect(jsonPath("$.details.length()").value(1))
                .andExpect(jsonPath("$.details[0].items.length()").value(1));
    }

    @Test
    void getScopePreview_asPlanner_returnsEligibleCountAndCutoffDate() throws Exception {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        persistSalesOrder(doorProduct, customer, new BigDecimal("2.000"));

        mockMvc.perform(get("/api/v1/cutting-plans/scope-preview")
                        .header("Authorization", "Bearer " + plannerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligibleOrderCount").value(1))
                .andExpect(jsonPath("$.scopeCutoffDate").value(LocalDate.now().plusDays(3).toString()));
    }

    @Test
    void getScopePreview_asAdmin_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/cutting-plans/scope-preview")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getById_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/cutting-plans/{id}", 999_999L)
                        .header("Authorization", "Bearer " + plannerToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAll_returnsSummariesOrderedByRunAtDescending() throws Exception {
        CuttingPlan older = newEmptyPlan(LocalDateTime.now().minusHours(1));
        CuttingPlan newer = newEmptyPlan(LocalDateTime.now());

        mockMvc.perform(get("/api/v1/cutting-plans").header("Authorization", "Bearer " + plannerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(newer.getId()))
                .andExpect(jsonPath("$[1].id").value(older.getId()));
    }

    private CuttingPlan newEmptyPlan(LocalDateTime runAt) {
        CuttingPlan plan = new CuttingPlan();
        plan.setRunAt(runAt);
        plan.setStatus(CuttingPlanStatus.COMPLETED);
        plan.setScopeCutoffDate(LocalDate.now().plusDays(3));
        plan.setScopeOrderCount(0);
        plan.setTotalWasteM(BigDecimal.ZERO);
        plan.setTotalStockUsedM(BigDecimal.ZERO);
        return cuttingPlanRepository.save(plan);
    }
}
