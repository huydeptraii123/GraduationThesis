package com.slatcut.cutting.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Test tầng controller đầu tiên trong dự án — MockMvc thật (Spring Security bật), JWT tự sinh qua JwtService, không qua /auth/login. */
@AutoConfigureMockMvc
class CuttingPlanControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    /**
     * Tự dựng chứ không lấy từ context: bộ tuần tự hóa của ứng dụng thuộc nhánh Jackson khác,
     * còn ở đây chỉ cần đọc lại JSON đã trả về để so hai cây với nhau.
     */
    private static final ObjectMapper objectMapper = new ObjectMapper();

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

    /**
     * Chức năng tính mở cho ADMIN — nó chỉ đọc và không chốt quyết định sản xuất nào. Khẳng định
     * quan trọng ở đây không phải mã 200 mà là số phương án đã lưu không nhúc nhích: đây là endpoint
     * dễ bị hiểu nhầm là "chạy thuật toán thì phải lưu lại" nhất.
     */
    @Test
    void simulate_asAdmin_returnsDemandRowsWithoutSavingAnything() throws Exception {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 2000, 1);
        SalesOrder order = persistSalesOrder(doorProduct, customer, new BigDecimal("2.000"));
        long plansBefore = cuttingPlanRepository.count();

        mockMvc.perform(post("/api/v1/cutting-plans/simulate").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopeOrderCount").value(1))
                .andExpect(jsonPath("$.computedAt").isNotEmpty())
                .andExpect(jsonPath("$.demands.length()").value(1))
                .andExpect(jsonPath("$.demands[0].ycsx").value(order.getYcsx()))
                .andExpect(jsonPath("$.demands[0].quantityMissing").value(0))
                .andExpect(jsonPath("$.demands[0].statusText").value("✔Đủ"));

        assertThat(cuttingPlanRepository.count()).isEqualTo(plansBefore);
    }

    /**
     * Màn hình duyệt tái sử dụng nguyên các thành phần hiển thị của phương án đã lưu, nên phương án
     * chưa lưu phải trả về đúng hình dạng đó — chỉ khác ở chỗ mọi id còn rỗng vì chưa có bản ghi nào.
     */
    @Test
    void approvalPreview_asPlanner_returnsSavedPlanShapeWithNullIdsPlusFingerprint() throws Exception {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 2000, 1);
        SalesOrder order = persistSalesOrder(doorProduct, customer, new BigDecimal("2.000"));

        mockMvc.perform(get("/api/v1/cutting-plans/approval-preview")
                        .header("Authorization", "Bearer " + plannerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stateFingerprint").isNotEmpty())
                .andExpect(jsonPath("$.scopeCutoffDate").value(LocalDate.now().plusDays(3).toString()))
                .andExpect(jsonPath("$.details.length()").value(1))
                .andExpect(jsonPath("$.details[0].id").isEmpty())
                .andExpect(jsonPath("$.details[0].sourceLengthMm").value(2000))
                .andExpect(jsonPath("$.details[0].items[0].id").isEmpty())
                .andExpect(jsonPath("$.details[0].items[0].salesOrderId").value(order.getId()))
                .andExpect(jsonPath("$.details[0].items[0].customerName").value(customer.getCustomerName()))
                .andExpect(jsonPath("$.plan.demands.length()").value(1));
    }

    @Test
    void approve_withStaleFingerprint_returnsConflictAndSavesNothing() throws Exception {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 2000, 1);
        persistSalesOrder(doorProduct, customer, new BigDecimal("2.000"));
        long plansBefore = cuttingPlanRepository.count();

        mockMvc.perform(post("/api/v1/cutting-plans/approve")
                        .header("Authorization", "Bearer " + plannerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stateFingerprint\":\"da-cu\"}"))
                .andExpect(status().isConflict());

        assertThat(cuttingPlanRepository.count()).isEqualTo(plansBefore);
    }

    /** Đường đi trọn vẹn của màn hình duyệt: xem phương án, cầm dấu vân đi duyệt, nhận lại phương án đã lưu. */
    @Test
    void approve_withFingerprintFromPreview_savesPlanAndMarksOrderApproved() throws Exception {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 2000, 1);
        SalesOrder order = persistSalesOrder(doorProduct, customer, new BigDecimal("2.000"));

        String previewBody = mockMvc.perform(get("/api/v1/cutting-plans/approval-preview")
                        .header("Authorization", "Bearer " + plannerToken()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String fingerprint = JsonPath.read(previewBody, "$.stateFingerprint");

        mockMvc.perform(post("/api/v1/cutting-plans/approve")
                        .header("Authorization", "Bearer " + plannerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stateFingerprint\":\"" + fingerprint + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.scopeOrderCount").value(1))
                .andExpect(jsonPath("$.details[0].id").isNotEmpty());

        assertThat(salesOrderRepository.findById(order.getId()).orElseThrow().getApprovedPlan())
                .isNotNull();
    }
    /**
     * Bất biến của màn hình duyệt: phương án PLANNER nhìn thấy và phương án được ghi xuống phải
     * nhóm phôi y hệt nhau. Người duyệt chịu trách nhiệm trên đúng cái họ đã xem, nên hai bên lệch
     * nhau dù chỉ ở cách gộp phôi cũng là lệch trách nhiệm.
     *
     * <p>Dựng bằng nhóm ray (mỗi bộ cửa cần 2 thanh cùng độ dài) để phép gộp thật sự phải làm
     * việc: hai phôi giống hệt nhau phải thành MỘT dòng với số phôi bằng 2, chứ không phải hai
     * dòng. Một bộ dữ liệu không có gì để gộp sẽ cho hai bên trùng nhau kể cả khi phép gộp sai.
     */
    @Test
    void approvalPreviewAndApprovedPlan_groupSticksIdentically() throws Exception {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial rail = persistRailSlatMaterial();
        persistRailBomItem(doorProduct, rail);
        persistInventoryBatch(rail, 2500, 2);
        persistSalesOrder(doorProduct, customer, new BigDecimal("2.000"));

        String previewBody = mockMvc.perform(get("/api/v1/cutting-plans/approval-preview")
                        .header("Authorization", "Bearer " + plannerToken()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode previewDetails = objectMapper.readTree(previewBody).get("details");
        String fingerprint = JsonPath.read(previewBody, "$.stateFingerprint");

        String approvedBody = mockMvc.perform(post("/api/v1/cutting-plans/approve")
                        .header("Authorization", "Bearer " + plannerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stateFingerprint\":\"" + fingerprint + "\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode approvedDetails = objectMapper.readTree(approvedBody).get("details");

        assertThat(previewDetails).hasSize(1);
        assertThat(previewDetails.get(0).get("stickCount").asInt()).isEqualTo(2);
        assertThat(previewDetails.get(0).get("items").get(0).get("cutQuantity").asInt())
                .isEqualTo(2);
        assertThat(withoutIds(approvedDetails)).isEqualTo(withoutIds(previewDetails));
    }

    /** Bỏ mọi trường id trước khi so: phương án chưa lưu chưa có id, đó là khác biệt duy nhất được phép. */
    private static JsonNode withoutIds(JsonNode node) {
        if (node.isObject()) {
            ObjectNode copy = ((ObjectNode) node).deepCopy();
            copy.remove("id");
            copy.fieldNames().forEachRemaining(field -> copy.set(field, withoutIds(copy.get(field))));
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = JsonNodeFactory.instance.arrayNode();
            node.forEach(child -> copy.add(withoutIds(child)));
            return copy;
        }
        return node;
    }

    private SlatMaterial persistRailSlatMaterial() {
        SlatMaterial entity = new SlatMaterial();
        entity.setSlatMaterial(70_000_000L + ++counter);
        entity.setSlatMaterialName("Ray " + counter);
        entity.setSlatGroup(SlatGroup.RAIL);
        return slatMaterialRepository.save(entity);
    }

    private void persistRailBomItem(DoorProduct doorProduct, SlatMaterial slatMaterial) {
        BomItem entity = new BomItem();
        entity.setDoorProduct(doorProduct);
        entity.setSlatMaterial(slatMaterial);
        entity.setHeightOffsetM(BigDecimal.ZERO);
        bomItemRepository.save(entity);
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
    void generate_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/cutting-plans/generate")).andExpect(status().isUnauthorized());
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
        // Mẫu cửa phải có định mức, nếu không đơn bị loại khỏi phạm vi (xem findUnprocessedInScope).
        persistBomItem(doorProduct, persistSlatMaterial());
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
    void export_asPlanner_returnsXlsxFile() throws Exception {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 2000, 1);
        persistSalesOrder(doorProduct, customer, new BigDecimal("2.000"));
        CuttingPlan plan = service.generate();

        mockMvc.perform(get("/api/v1/cutting-plans/{id}/export", plan.getId())
                        .header("Authorization", "Bearer " + plannerToken()))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(
                        "Content-Disposition", "attachment; filename=\"phuong-an-cat-" + plan.getId() + ".xlsx\""));
    }

    @Test
    void export_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/cutting-plans/{id}/export", 1L)).andExpect(status().isUnauthorized());
    }

    @Test
    void shortageReport_asPlanner_returnsXlsxFile() throws Exception {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistSalesOrder(doorProduct, customer, new BigDecimal("5.000"));
        CuttingPlan plan = service.generate();

        mockMvc.perform(get("/api/v1/cutting-plans/{id}/shortage-report", plan.getId())
                        .header("Authorization", "Bearer " + plannerToken()))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"bao-cao-thieu-vat-tu-" + plan.getId() + ".xlsx\""));
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
                .andExpect(jsonPath("$.content[0].id").value(newer.getId()))
                .andExpect(jsonPath("$.content[1].id").value(older.getId()))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    void getAll_appliesPageSizeAndStatusFilter() throws Exception {
        newEmptyPlan(LocalDateTime.now().minusHours(2));
        CuttingPlan newer = newEmptyPlan(LocalDateTime.now().minusHours(1));

        mockMvc.perform(get("/api/v1/cutting-plans")
                        .param("size", "1")
                        .param("status", "COMPLETED")
                        .header("Authorization", "Bearer " + plannerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(newer.getId()))
                .andExpect(jsonPath("$.size").value(1));
    }

    @Test
    void getAll_filtersByPlanId() throws Exception {
        newEmptyPlan(LocalDateTime.now().minusHours(3));
        CuttingPlan target = newEmptyPlan(LocalDateTime.now());

        mockMvc.perform(get("/api/v1/cutting-plans")
                        .param("planId", String.valueOf(target.getId()))
                        .header("Authorization", "Bearer " + plannerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(target.getId()));
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
