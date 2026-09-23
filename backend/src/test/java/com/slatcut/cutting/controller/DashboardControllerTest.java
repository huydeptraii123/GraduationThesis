package com.slatcut.cutting.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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

    /**
     * Duyệt phương án cho phạm vi hiện tại — thay cho đường ghi một bước đã gỡ. Đi qua đúng luồng
     * thật: xem trước để lấy dấu vân trạng thái, rồi duyệt bằng chính dấu vân đó.
     */
    private CuttingPlan approvePlan() {
        return cuttingPlanService.approve(cuttingPlanService.approvalPreview().stateFingerprint());
    }

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

    /** 1 bộ cửa cắt từ phôi 2.5m để lại 0.25m bỏ đi, đặt ở ngày giao chỉ định. */
    private SalesOrder persistDiscardingOrder(LocalDate reqdDeliveryDate) {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial(SlatGroup.BOTTOM_BAR);
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 2500, 1);
        SalesOrder order = persistSalesOrder(doorProduct, customer, new BigDecimal("2.250"));
        order.setReqdDeliveryDate(reqdDeliveryDate);
        return salesOrderRepository.save(order);
    }

    private void persistInventoryBatch(SlatMaterial slatMaterial, int lengthMm, int count) {
        InventoryBatch entity = new InventoryBatch();
        entity.setSlatMaterial(slatMaterial);
        entity.setDoDaiThanhMm(lengthMm);
        entity.setSoThanh(count);
        inventoryBatchRepository.save(entity);
    }

    /** 1 đơn 2m cắt từ phôi 2.5m, dư 0.5m thuộc khoảng 30cm-3m, tức "lãng phí" tính vào tỷ lệ phế. */
    /**
     * Thanh 2500mm cắt đoạn 2250mm -> dư 250mm, dưới ngưỡng 30cm nên bị bỏ đi và tính vào phế liệu.
     * Phải dùng phần dư loại BỎ ĐI chứ không phải loại lãng phí: thuật toán không còn sinh ra phần
     * dư 30cm-3m nữa, nên kịch bản cũ (dư 500mm) giờ chỉ cho ra một đơn thiếu vật tư và không có
     * dòng phế liệu nào để đo.
     */
    private CuttingPlan runWithQuarterMetreDiscardedRemainder() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial(SlatGroup.BOTTOM_BAR);
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 2500, 1);
        persistSalesOrder(doorProduct, customer, new BigDecimal("2.250"));
        return approvePlan();
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
        // Mẫu cửa phải có định mức, nếu không đơn bị loại khỏi phạm vi (xem findUnprocessedInScope).
        persistBomItem(doorProduct, persistSlatMaterial(SlatGroup.BOTTOM_BAR));
        for (int i = 0; i < 71; i++) {
            persistSalesOrder(doorProduct, customer, new BigDecimal("2.000"));
        }

        mockMvc.perform(get("/api/v1/dashboard").header("Authorization", "Bearer " + plannerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingOrderCount").value(71));
    }

    @Test
    void getDashboard_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard")).andExpect(status().isUnauthorized());
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
                .andExpect(jsonPath("$.orderWasteTrend.length()").value(0))
                .andExpect(jsonPath("$.wasteByGroup.length()").value(0))
                .andExpect(jsonPath("$.cumulativeWasteM").value(0.00))
                .andExpect(jsonPath("$.cumulativeWasteRatioPercent").value(0.0))
                // 3 ngưỡng luôn có mặt kể cả khi chưa phát sinh, để nhãn/màu biểu đồ tròn không đổi.
                .andExpect(jsonPath("$.remainderBreakdown.length()").value(3));
    }

    @Test
    void getDashboard_afterTwoRuns_returnsTrendOldestFirstAndCumulativeTotals() throws Exception {
        CuttingPlan first = runWithQuarterMetreDiscardedRemainder();
        CuttingPlan second = runWithQuarterMetreDiscardedRemainder();

        // Lô của vật tư chưa có định mức nào dùng tới, không bị tiêu thụ, dùng để khoá KPI tồn kho.
        persistInventoryBatch(persistSlatMaterial(SlatGroup.MAIN_SLAT), 6000, 5);

        mockMvc.perform(get("/api/v1/dashboard").header("Authorization", "Bearer " + plannerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wasteTrend.length()").value(2))
                .andExpect(jsonPath("$.wasteTrend[0].planId").value(first.getId()))
                .andExpect(jsonPath("$.wasteTrend[1].planId").value(second.getId()))
                .andExpect(jsonPath("$.wasteTrend[0].totalStockUsedM").value(2.50))
                .andExpect(jsonPath("$.wasteTrend[0].totalWasteM").value(0.25))
                .andExpect(jsonPath("$.wasteTrend[0].wasteRatioPercent").value(10.0))
                .andExpect(jsonPath("$.latestPlan.planId").value(second.getId()))
                .andExpect(jsonPath("$.cumulativeWasteM").value(0.50))
                .andExpect(jsonPath("$.cumulativeStockUsedM").value(5.00))
                .andExpect(jsonPath("$.cumulativeWasteRatioPercent").value(10.0))
                // 2 lô đã bị cắt hết còn 0 thanh, chỉ lô chưa đụng tới mới được tính là "sẵn sàng".
                .andExpect(jsonPath("$.readyBatchCount").value(1))
                .andExpect(jsonPath("$.readyStickCount").value(5))
                .andExpect(jsonPath("$.pendingOrderCount").value(0));
    }

    /**
     * {@code remainder_mm} là phần dư của MỖI phôi, mà 2 phôi giống hệt nhau bị gộp vào 1 dòng
     * detail ({@code stickCount=2}). Kịch bản RAIL dưới đây là case duy nhất phân biệt được "cộng
     * theo số phôi" (đúng) với "cộng theo số dòng detail" (sai): bỏ trọng số stickCount sẽ ra 0.20m
     * thay vì 0.40m. Đồng thời đối chiếu chéo với tổng phế cộng dồn, con số được tính từ CutRecord
     * trước khi gộp dòng, tức bằng một đường đi hoàn toàn khác.
     */
    @Test
    void getDashboard_remainderBreakdown_weightsRemainderByStickCount() throws Exception {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial rail = persistSlatMaterial(SlatGroup.RAIL);
        persistRailBomItem(doorProduct, rail);
        // 2200mm cho đoạn 2000mm -> dư 200mm mỗi phôi, dưới ngưỡng 30cm nên được cắt và bỏ đi.
        persistInventoryBatch(rail, 2200, 2);
        persistRailSalesOrder(doorProduct, customer, new BigDecimal("2.000"));

        approvePlan();

        mockMvc.perform(get("/api/v1/dashboard").header("Authorization", "Bearer " + plannerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainderBreakdown[?(@.remainderType == 'DISCARDED')].totalM").value(0.40))
                .andExpect(jsonPath("$.remainderBreakdown[?(@.remainderType == 'WASTE')].totalM").value(0.00))
                .andExpect(jsonPath("$.remainderBreakdown[?(@.remainderType == 'RESTOCK')].totalM").value(0.00))
                .andExpect(jsonPath("$.wasteByGroup.length()").value(1))
                .andExpect(jsonPath("$.wasteByGroup[0].slatGroup").value("RAIL"))
                .andExpect(jsonPath("$.wasteByGroup[0].totalM").value(0.40))
                // Mẫu số của nhóm phải bằng đúng tồn kho thực tiêu hao, tức 2 phôi 2.2m.
                .andExpect(jsonPath("$.wasteByGroup[0].stockUsedM").value(4.40))
                .andExpect(jsonPath("$.wasteByGroup[0].wasteRatioPercent").value(9.1))
                // Chỉ 1 bộ cửa nên toàn bộ phế quy về nó, không có gì để chia tỷ lệ.
                .andExpect(jsonPath("$.orderWasteTrend.length()").value(1))
                .andExpect(jsonPath("$.orderWasteTrend[0].wasteM").value(0.40))
                .andExpect(jsonPath("$.orderWasteTrend[0].stockUsedM").value(4.40))
                .andExpect(jsonPath("$.orderWasteTrend[0].wasteRatioPercent").value(9.1))
                .andExpect(jsonPath("$.cumulativeWasteM").value(0.40))
                .andExpect(jsonPath("$.cumulativeStockUsedM").value(4.40));
    }

    /**
     * Ca duy nhất phân biệt được phép chia tỷ lệ với phép quy phế cho một bộ cửa duy nhất: Mức 3
     * ghép đoạn của HAI bộ cửa khác nhau lên cùng một phôi, nên phần dư của phôi đó không thuộc
     * hẳn về bên nào.
     *
     * <p>Hai bộ cửa cố ý lấy độ dài lệch nhau (4.000m và 1.900m) chứ không chia đôi phôi: chia đều
     * hay quy hết cho một bên đều lọt nếu hai đoạn bằng nhau. Với tỷ lệ 8000:3800 thì phần phế
     * phải rơi về 0.14m / 0.06m.
     *
     * <p><b>Hai bộ cửa phải ra CÙNG một tỷ lệ, và đó là điều bắt buộc về mặt toán học</b>: phép
     * chia tỷ lệ nhân cả tử lẫn mẫu với cùng một hệ số nên hệ số triệt tiêu, để lại đúng tỷ lệ của
     * chính cái phôi (200/12000 = 1,7%). Trước khi sửa, hai bộ cửa hiện 1,6% và 1,7% — chênh lệch
     * đó KHÔNG phải tín hiệu nghiệp vụ mà là tạo tác của việc làm tròn về mét trước khi chia; nay
     * tỷ lệ được chia trên milimet gốc. Khẳng định hai con số bằng nhau ở đây chính là chốt chặn
     * không cho phép làm tròn sớm quay lại.
     *
     * <p>Phép cộng ngược ở cuối đọc thẳng từ thân phản hồi chứ không so với
     * {@code cumulativeWasteM}: hai con số cộng dồn kia lấy từ cột đã lưu của
     * {@code cutting_plan}, một đường đi không hề chạm {@code orderWasteTrend()} nên vẫn xanh kể
     * cả khi mảng đó rỗng. Cộng thẳng các phần tử mới thật sự khóa được tính bảo toàn.
     */
    @Test
    void getDashboard_orderWasteTrend_splitsOneSharedStickBetweenTwoDoorSetsByCutLength() throws Exception {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial rail = persistSlatMaterial(SlatGroup.RAIL);
        persistRailBomItem(doorProduct, rail);
        // 2 phôi 6m: mỗi phôi nhận 1 đoạn 4m của bộ cửa này và 1 đoạn 1.9m của bộ cửa kia -> dư 0.1m.
        persistInventoryBatch(rail, 6000, 2);
        SalesOrder wide = persistRailSalesOrder(doorProduct, customer, new BigDecimal("4.000"));
        SalesOrder narrow = persistRailSalesOrder(doorProduct, customer, new BigDecimal("1.900"));

        approvePlan();

        MvcResult response = mockMvc
                .perform(get("/api/v1/dashboard").header("Authorization", "Bearer " + plannerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderWasteTrend.length()").value(2))
                .andExpect(jsonPath("$.orderWasteTrend[?(@.salesOrderId == %d)].wasteM".formatted(wide.getId()))
                        .value(0.14))
                .andExpect(jsonPath("$.orderWasteTrend[?(@.salesOrderId == %d)].stockUsedM".formatted(wide.getId()))
                        .value(8.14))
                .andExpect(jsonPath("$.orderWasteTrend[?(@.salesOrderId == %d)].wasteRatioPercent"
                                .formatted(wide.getId()))
                        .value(1.7))
                .andExpect(jsonPath("$.orderWasteTrend[?(@.salesOrderId == %d)].wasteM".formatted(narrow.getId()))
                        .value(0.06))
                .andExpect(jsonPath("$.orderWasteTrend[?(@.salesOrderId == %d)].stockUsedM".formatted(narrow.getId()))
                        .value(3.86))
                .andExpect(jsonPath("$.orderWasteTrend[?(@.salesOrderId == %d)].wasteRatioPercent"
                                .formatted(narrow.getId()))
                        .value(1.7))
                .andReturn();

        // Cộng ngược trên chính mảng vừa trả về: 0.14 + 0.06 = 0.20 và 8.14 + 3.86 = 12.00.
        assertOrderWasteTrendAddsUpToPlanTotals(response);
    }

    /**
     * Tổng phế (và tổng tiêu hao) quy về từng bộ cửa phải bằng đúng tổng của phương án. Đọc cả hai
     * vế từ CÙNG một thân phản hồi nên phép so này thật sự chạm vào {@code orderWasteTrend()} —
     * mảng rỗng hoặc phép chia làm mất/nhân đôi vật liệu đều bị bắt ngay.
     */
    private void assertOrderWasteTrendAddsUpToPlanTotals(MvcResult response) throws Exception {
        DocumentContext json = JsonPath.parse(response.getResponse().getContentAsString());
        assertThat(json.<List<Number>>read("$.orderWasteTrend[*].wasteM")).isNotEmpty();

        assertThat(sum(json, "$.orderWasteTrend[*].wasteM"))
                .isEqualByComparingTo(toBigDecimal(json.read("$.cumulativeWasteM")));
        assertThat(sum(json, "$.orderWasteTrend[*].stockUsedM"))
                .isEqualByComparingTo(toBigDecimal(json.read("$.cumulativeStockUsedM")));
    }

    private static BigDecimal sum(DocumentContext json, String path) {
        return json.<List<Number>>read(path).stream()
                .map(DashboardControllerTest::toBigDecimal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** JsonPath trả số JSON về dưới dạng {@code Double}; đi vòng qua chuỗi để không rước sai số nhị phân. */
    private static BigDecimal toBigDecimal(Number value) {
        return new BigDecimal(String.valueOf(value));
    }

    /**
     * Trục hoành của biểu đồ xu hướng là ngày giao, nên thứ tự trả về phải theo ngày giao.
     *
     * <p><b>Hai bộ cửa bắt buộc được duyệt ở HAI ĐỢT khác nhau, đợt trước duyệt bộ giao MUỘN
     * hơn</b> — đây là điểm sống còn của ca này. Trong phạm vi một đợt duyệt, thuật toán vốn đã xử
     * lý theo đúng ngày giao và ghi dòng phôi theo thứ tự đó, nên thứ tự tự nhiên của dữ liệu trùng
     * luôn với thứ tự cần kiểm và bỏ hẳn phép sắp xếp thì test vẫn xanh: hai bản trước của ca này
     * (một bản đổi thứ tự tạo bản ghi, một bản đổi loại thanh nan) đều mắc đúng lỗi đó và bị kiểm
     * chứng ngược bắt được. Nguyên nhân là {@code groupBySlatMaterial} xếp nhóm theo thứ tự XUẤT
     * HIỆN ĐẦU TIÊN trong danh sách nhu cầu, mà danh sách đó vốn đã theo ngày giao — đổi loại thanh
     * nan không tách được hai thứ tự. Cắt qua ranh giới hai đợt duyệt thì mới tách được: dòng phôi
     * của bộ giao muộn mang id nhỏ hơn vì được ghi trước.
     */
    @Test
    void getDashboard_orderWasteTrend_isSortedByDeliveryDateNotByApprovalOrder() throws Exception {
        SalesOrder later = persistDiscardingOrder(LocalDate.now().plusDays(2));
        approvePlan();

        SalesOrder sooner = persistDiscardingOrder(LocalDate.now());
        approvePlan();

        mockMvc.perform(get("/api/v1/dashboard").header("Authorization", "Bearer " + plannerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderWasteTrend.length()").value(2))
                .andExpect(jsonPath("$.orderWasteTrend[0].salesOrderId").value(sooner.getId()))
                .andExpect(jsonPath("$.orderWasteTrend[1].salesOrderId").value(later.getId()));
    }

    /**
     * Nhánh phần dư NHẬP LẠI KHO là thứ duy nhất phân biệt công thức mới với một phép cộng ngây
     * thơ, mà trước ca này thì <b>không test nào sinh ra nổi một dòng phôi kiểu đó</b> — thay cả
     * hai mệnh đề {@code CASE WHEN} bằng tổng trần thì 430 test vẫn xanh.
     *
     * <p>Phôi 8m cắt một đoạn 4m: phần dư 4m vượt 3m nên Mức 4 nhận, thanh dư quay lại kho. Hai
     * hệ quả phải đúng cùng lúc: phần dư đó <b>không</b> vào tử số (0 mét phế), và nó <b>bị trừ
     * khỏi</b> mẫu số (tiêu hao 4m chứ không phải 8m) — đúng định nghĩa tồn kho thực tiêu hao của
     * {@code CuttingPlanService#totalStockUsedM}. Dùng tổng trần cho mẫu số sẽ ra 8m.
     *
     * <p>Dùng nhóm THANH ĐÁY chứ không dùng ray dẫn hướng như các ca khác trong lớp này: mỗi bộ
     * cửa cần 2 ray nhưng chỉ cần 1 thanh đáy. Với 2 đoạn 4m thì Mức 2 gói vừa khít phôi 8m và
     * phần dư ra 0 — bản đầu của ca này đã dựng bằng ray và không sinh nổi dòng nhập lại kho nào.
     *
     * <p>Nhóm cũng phải HIỆN LÊN với tỷ lệ 0% chứ không biến mất: bộ lọc là {@code stockUsedM > 0}
     * chứ không phải {@code totalM > 0}. Giữ nguyên bộ lọc cũ thì {@code wasteByGroup} rỗng.
     */
    @Test
    void getDashboard_restockedRemainder_addsNoWasteAndIsRemovedFromTheDenominator() throws Exception {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial bottomBar = persistSlatMaterial(SlatGroup.BOTTOM_BAR);
        persistBomItem(doorProduct, bottomBar);
        persistInventoryBatch(bottomBar, 8000, 1);
        persistSalesOrder(doorProduct, customer, new BigDecimal("4.000"));

        approvePlan();

        mockMvc.perform(get("/api/v1/dashboard").header("Authorization", "Bearer " + plannerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainderBreakdown[?(@.remainderType == 'RESTOCK')].totalM").value(4.00))
                // Nhóm vẫn hiện dù không phát sinh mét phế nào.
                .andExpect(jsonPath("$.wasteByGroup.length()").value(1))
                .andExpect(jsonPath("$.wasteByGroup[0].totalM").value(0.00))
                .andExpect(jsonPath("$.wasteByGroup[0].stockUsedM").value(4.00))
                .andExpect(jsonPath("$.wasteByGroup[0].wasteRatioPercent").value(0.0))
                .andExpect(jsonPath("$.orderWasteTrend.length()").value(1))
                .andExpect(jsonPath("$.orderWasteTrend[0].wasteM").value(0.00))
                .andExpect(jsonPath("$.orderWasteTrend[0].stockUsedM").value(4.00))
                .andExpect(jsonPath("$.cumulativeStockUsedM").value(4.00));
    }
}
