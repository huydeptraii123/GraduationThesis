package com.slatcut.cutting.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.slatcut.cutting.AbstractIntegrationTest;
import com.slatcut.cutting.domain.BomItem;
import com.slatcut.cutting.domain.Customer;
import com.slatcut.cutting.domain.CuttingPlan;
import com.slatcut.cutting.domain.DoorProduct;
import com.slatcut.cutting.domain.InventoryBatch;
import com.slatcut.cutting.domain.SalesOrder;
import com.slatcut.cutting.domain.SlatGroup;
import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.repository.BomItemRepository;
import com.slatcut.cutting.repository.CustomerRepository;
import com.slatcut.cutting.repository.DoorProductRepository;
import com.slatcut.cutting.repository.InventoryBatchRepository;
import com.slatcut.cutting.repository.SalesOrderRepository;
import com.slatcut.cutting.repository.SlatMaterialRepository;
import com.slatcut.cutting.security.JwtService;
import com.slatcut.cutting.service.CuttingPlanService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Dashboard chỉ tổng hợp lại kết quả đã lưu, nên test tập trung vào việc số liệu KHỚP với các lần chạy thật. */
@AutoConfigureMockMvc
class DashboardControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CuttingPlanService cuttingPlanService;

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

    private SlatMaterial persistSlatMaterial(SlatGroup slatGroup) {
        SlatMaterial entity = new SlatMaterial();
        entity.setSlatMaterial(70_000_000L + ++counter);
        entity.setSlatMaterialName("Thanh nan " + counter);
        entity.setSlatGroup(slatGroup);
        return slatMaterialRepository.save(entity);
    }

    private void persistBomItem(DoorProduct doorProduct, SlatMaterial slatMaterial) {
        BomItem entity = new BomItem();
        entity.setDoorProduct(doorProduct);
        entity.setSlatMaterial(slatMaterial);
        bomItemRepository.save(entity);
    }

    /** RAIL cần heightOffsetM để CuttingDemandService tính được cutLength từ chiều cao đơn hàng. */
    private void persistRailBomItem(DoorProduct doorProduct, SlatMaterial slatMaterial) {
        BomItem entity = new BomItem();
        entity.setDoorProduct(doorProduct);
        entity.setSlatMaterial(slatMaterial);
        entity.setHeightOffsetM(BigDecimal.ZERO);
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

    private SalesOrder persistRailSalesOrder(DoorProduct doorProduct, Customer customer, BigDecimal chieuCaoDh) {
        SalesOrder entity = new SalesOrder();
        entity.setYcsx("HY9" + ++counter);
        entity.setItem((int) counter);
        entity.setCustomer(customer);
        entity.setDoorProduct(doorProduct);
        entity.setChieuCaoDh(chieuCaoDh);
        entity.setChieuRongDh(new BigDecimal("1.500"));
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

    /** 1 đơn 2m cắt từ phôi 2.5m, dư 0.5m thuộc khoảng 30cm-3m, tức "lãng phí" tính vào tỷ lệ phế. */
    private CuttingPlan runWithHalfMeterWaste() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial(SlatGroup.BOTTOM_BAR);
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 2500, 1);
        persistSalesOrder(doorProduct, customer, new BigDecimal("2.000"));
        return cuttingPlanService.generate();
    }

    /**
     * KPI tồn đọng phải đếm hết số đơn đang chờ, không dừng ở hạn mức 70 đơn/lần chạy của thuật
     * toán — nếu dùng lại query có LIMIT 70 thì con số đứng im ở 70 và PLANNER không thấy được
     * lượng việc thật sự dồn lại.
     */
    @Test
    void getDashboard_pendingOrderCount_isNotCappedByPerRunOrderLimit() throws Exception {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        for (int i = 0; i < 71; i++) {
            persistSalesOrder(doorProduct, customer, new BigDecimal("2.000"));
        }

        mockMvc.perform(get("/api/v1/dashboard").header("Authorization", "Bearer " + plannerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingOrderCount").value(71));
    }

    @Test
    void getDashboard_withoutToken_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    void getDashboard_asAdmin_isAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }

    @Test
    void getDashboard_withoutAnyRun_returnsZeroedStatsAndFullRemainderLegend() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard").header("Authorization", "Bearer " + plannerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingOrderCount").value(0))
                .andExpect(jsonPath("$.latestPlan").doesNotExist())
                .andExpect(jsonPath("$.wasteTrend.length()").value(0))
                .andExpect(jsonPath("$.wasteByGroup.length()").value(0))
                .andExpect(jsonPath("$.cumulativeWasteM").value(0.00))
                .andExpect(jsonPath("$.cumulativeWasteRatioPercent").value(0.0))
                // 3 ngưỡng luôn có mặt kể cả khi chưa phát sinh, để nhãn/màu biểu đồ tròn không đổi.
                .andExpect(jsonPath("$.remainderBreakdown.length()").value(3));
    }

    @Test
    void getDashboard_afterTwoRuns_returnsTrendOldestFirstAndCumulativeTotals() throws Exception {
        CuttingPlan first = runWithHalfMeterWaste();
        CuttingPlan second = runWithHalfMeterWaste();

        // Lô của vật tư chưa có định mức nào dùng tới, không bị tiêu thụ, dùng để khoá KPI tồn kho.
        persistInventoryBatch(persistSlatMaterial(SlatGroup.MAIN_SLAT), 6000, 5);

        mockMvc.perform(get("/api/v1/dashboard").header("Authorization", "Bearer " + plannerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wasteTrend.length()").value(2))
                .andExpect(jsonPath("$.wasteTrend[0].planId").value(first.getId()))
                .andExpect(jsonPath("$.wasteTrend[1].planId").value(second.getId()))
                .andExpect(jsonPath("$.wasteTrend[0].totalStockUsedM").value(2.50))
                .andExpect(jsonPath("$.wasteTrend[0].totalWasteM").value(0.50))
                .andExpect(jsonPath("$.wasteTrend[0].wasteRatioPercent").value(20.0))
                .andExpect(jsonPath("$.latestPlan.planId").value(second.getId()))
                .andExpect(jsonPath("$.cumulativeWasteM").value(1.00))
                .andExpect(jsonPath("$.cumulativeStockUsedM").value(5.00))
                .andExpect(jsonPath("$.cumulativeWasteRatioPercent").value(20.0))
                // 2 lô đã bị cắt hết còn 0 thanh, chỉ lô chưa đụng tới mới được tính là "sẵn sàng".
                .andExpect(jsonPath("$.readyBatchCount").value(1))
                .andExpect(jsonPath("$.readyStickCount").value(5))
                .andExpect(jsonPath("$.pendingOrderCount").value(0));
    }

    /**
     * {@code remainder_mm} là phần dư của MỖI phôi, mà 2 phôi giống hệt nhau bị gộp vào 1 dòng
     * detail ({@code stickCount=2}). Kịch bản RAIL dưới đây là case duy nhất phân biệt được "cộng
     * theo số phôi" (đúng) với "cộng theo số dòng detail" (sai): bỏ trọng số stickCount sẽ ra 0.50m
     * thay vì 1.00m. Đồng thời đối chiếu chéo với tổng phế cộng dồn, con số được tính từ CutRecord
     * trước khi gộp dòng, tức bằng một đường đi hoàn toàn khác.
     */
    @Test
    void getDashboard_remainderBreakdown_weightsRemainderByStickCount() throws Exception {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial rail = persistSlatMaterial(SlatGroup.RAIL);
        persistRailBomItem(doorProduct, rail);
        persistInventoryBatch(rail, 2500, 2);
        persistRailSalesOrder(doorProduct, customer, new BigDecimal("2.000"));

        cuttingPlanService.generate();

        mockMvc.perform(get("/api/v1/dashboard").header("Authorization", "Bearer " + plannerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainderBreakdown[?(@.remainderType == 'WASTE')].totalM").value(1.00))
                .andExpect(jsonPath("$.remainderBreakdown[?(@.remainderType == 'RESTOCK')].totalM").value(0.00))
                .andExpect(jsonPath("$.wasteByGroup.length()").value(1))
                .andExpect(jsonPath("$.wasteByGroup[0].slatGroup").value("RAIL"))
                .andExpect(jsonPath("$.wasteByGroup[0].totalM").value(1.00))
                .andExpect(jsonPath("$.cumulativeWasteM").value(1.00))
                .andExpect(jsonPath("$.cumulativeStockUsedM").value(5.00));
    }
}
