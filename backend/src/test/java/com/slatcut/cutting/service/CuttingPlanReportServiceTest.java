package com.slatcut.cutting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.slatcut.cutting.AbstractIntegrationTest;
import com.slatcut.cutting.config.ResourceNotFoundException;
import com.slatcut.cutting.domain.BomItem;
import com.slatcut.cutting.domain.Customer;
import com.slatcut.cutting.domain.CuttingPlan;
import com.slatcut.cutting.domain.DoorProduct;
import com.slatcut.cutting.domain.InventoryBatch;
import com.slatcut.cutting.domain.SalesOrder;
import com.slatcut.cutting.domain.SlatGroup;
import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.dto.CuttingPlanDemandView;
import com.slatcut.cutting.repository.BomItemRepository;
import com.slatcut.cutting.repository.CustomerRepository;
import com.slatcut.cutting.repository.DoorProductRepository;
import com.slatcut.cutting.repository.InventoryBatchRepository;
import com.slatcut.cutting.repository.SalesOrderRepository;
import com.slatcut.cutting.repository.SlatMaterialRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CuttingPlanReportServiceTest extends AbstractIntegrationTest {

    @Autowired
    private CuttingPlanReportService reportService;

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

    /** BOTTOM_BAR: đoạn cắt bằng đúng chiều rộng cửa, mỗi bộ cần 1 thanh — công thức đơn giản nhất. */
    private void persistBomItem(DoorProduct doorProduct, SlatMaterial slatMaterial) {
        BomItem entity = new BomItem();
        entity.setDoorProduct(doorProduct);
        entity.setSlatMaterial(slatMaterial);
        bomItemRepository.save(entity);
    }

    /** RAIL: đoạn cắt bằng chiều CAO cửa trừ hao, mỗi bộ cần 2 thanh — dùng để dựng ca thiếu một phần. */
    private void persistRailBomItem(DoorProduct doorProduct, SlatMaterial slatMaterial) {
        BomItem entity = new BomItem();
        entity.setDoorProduct(doorProduct);
        entity.setSlatMaterial(slatMaterial);
        entity.setHeightOffsetM(BigDecimal.ZERO);
        bomItemRepository.save(entity);
    }

    private SalesOrder persistSalesOrder(
            DoorProduct doorProduct, Customer customer, BigDecimal chieuCaoDh, BigDecimal chieuRongDh, LocalDate reqdDeliveryDate) {
        SalesOrder entity = new SalesOrder();
        entity.setYcsx("HY8" + (++counter));
        entity.setItem(1);
        entity.setSalesDocument(1_000_000_000L + counter);
        entity.setCustomer(customer);
        entity.setDoorProduct(doorProduct);
        entity.setChieuCaoDh(chieuCaoDh);
        entity.setChieuRongDh(chieuRongDh);
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
    void buildFromPreview_writesOneRowPerMaterialOfEachDoorSet() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial bottomBar = persistSlatMaterial(SlatGroup.BOTTOM_BAR);
        SlatMaterial rail = persistSlatMaterial(SlatGroup.RAIL);
        persistBomItem(doorProduct, bottomBar);
        persistRailBomItem(doorProduct, rail);
        persistInventoryBatch(bottomBar, 2000, 1);
        persistInventoryBatch(rail, 2500, 2);
        SalesOrder order = persistSalesOrder(
                doorProduct, customer, new BigDecimal("2.500"), new BigDecimal("2.000"), LocalDate.now());

        List<CuttingPlanDemandView> rows = reportService.buildFromPreview(cuttingPlanService.simulate());

        assertThat(rows).hasSize(2);
        assertThat(rows)
                .extracting(
                        CuttingPlanDemandView::ycsx,
                        CuttingPlanDemandView::slatMaterialCode,
                        CuttingPlanDemandView::cutLengthMm,
                        CuttingPlanDemandView::quantityNeeded,
                        CuttingPlanDemandView::quantityMissing)
                .containsExactlyInAnyOrder(
                        tuple(order.getYcsx(), bottomBar.getSlatMaterial(), 2000, 1, 0),
                        tuple(order.getYcsx(), rail.getSlatMaterial(), 2500, 2, 0));
        assertThat(rows)
                .allSatisfy(row -> assertThat(row.doorSetStatus()).isEqualTo(CuttingPlanDemandView.DOOR_SET_SUFFICIENT));
    }

    /**
     * Ba câu trạng thái của khuôn mẫu doanh nghiệp, mỗi câu một tình huống. Ca "thiếu một phần" chỉ
     * dựng được với nhóm ray (mỗi bộ cần 2 thanh): kho còn đúng 1 thanh thì một đoạn cắt được, đoạn
     * còn lại báo thiếu.
     */
    @Test
    void buildFromPreview_writesTheThreeStatusSentences() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial sufficient = persistSlatMaterial(SlatGroup.BOTTOM_BAR);
        SlatMaterial missingAll = persistSlatMaterial(SlatGroup.SUB_SLAT);
        SlatMaterial rail = persistSlatMaterial(SlatGroup.RAIL);
        persistBomItem(doorProduct, sufficient);
        persistBomItem(doorProduct, missingAll);
        persistRailBomItem(doorProduct, rail);
        persistInventoryBatch(sufficient, 2000, 1);
        persistInventoryBatch(rail, 2500, 1);
        persistSalesOrder(doorProduct, customer, new BigDecimal("2.500"), new BigDecimal("2.000"), LocalDate.now());

        List<CuttingPlanDemandView> rows = reportService.buildFromPreview(cuttingPlanService.simulate());

        assertThat(rows)
                .extracting(CuttingPlanDemandView::slatMaterialCode, CuttingPlanDemandView::statusText)
                .containsExactlyInAnyOrder(
                        tuple(sufficient.getSlatMaterial(), "✔Đủ"),
                        tuple(missingAll.getSlatMaterial(), "Thiếu toàn bộ 1 nan 2.00m (2.0m)"),
                        tuple(rail.getSlatMaterial(), "Thiếu 1 nan 2.50m (2.5m)"));
    }

    /** Một loại thanh thiếu là cả bộ cửa tính thiếu — bộ cửa không lắp được khi còn thiếu thành phần nào. */
    @Test
    void buildFromPreview_marksEveryRowOfADoorSetShortWhenOneMaterialIsMissing() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial sufficient = persistSlatMaterial(SlatGroup.BOTTOM_BAR);
        SlatMaterial missing = persistSlatMaterial(SlatGroup.SUB_SLAT);
        persistBomItem(doorProduct, sufficient);
        persistBomItem(doorProduct, missing);
        persistInventoryBatch(sufficient, 2000, 1);
        persistSalesOrder(doorProduct, customer, new BigDecimal("2.500"), new BigDecimal("2.000"), LocalDate.now());

        List<CuttingPlanDemandView> rows = reportService.buildFromPreview(cuttingPlanService.simulate());

        assertThat(rows).hasSize(2);
        assertThat(rows)
                .allSatisfy(row -> assertThat(row.doorSetStatus()).isEqualTo(CuttingPlanDemandView.DOOR_SET_SHORT));
    }

    /**
     * Hạng ưu tiên đánh số lại từ 1 trong TỪNG loại vật tư, không chạy dồn trên cả file: thợ cắt
     * làm hết một loại thanh nan rồi mới sang loại khác, nên một cột đánh số toàn cục không nói lên
     * điều gì với họ.
     */
    @Test
    void buildFromPreview_ranksPriorityWithinEachMaterialNotAcrossTheWholeFile() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial bottomBar = persistSlatMaterial(SlatGroup.BOTTOM_BAR);
        SlatMaterial subSlat = persistSlatMaterial(SlatGroup.SUB_SLAT);
        persistBomItem(doorProduct, bottomBar);
        persistBomItem(doorProduct, subSlat);
        persistInventoryBatch(bottomBar, 2000, 2);
        persistInventoryBatch(subSlat, 2000, 2);
        SalesOrder urgent = persistSalesOrder(
                doorProduct, customer, new BigDecimal("2.500"), new BigDecimal("2.000"), LocalDate.now());
        SalesOrder later = persistSalesOrder(
                doorProduct, customer, new BigDecimal("2.500"), new BigDecimal("2.000"), LocalDate.now().plusDays(5));

        List<CuttingPlanDemandView> rows = reportService.buildFromPreview(cuttingPlanService.simulate());

        assertThat(rows).hasSize(4);
        assertThat(rows)
                .extracting(
                        CuttingPlanDemandView::slatMaterialCode,
                        CuttingPlanDemandView::ycsx,
                        CuttingPlanDemandView::priorityRank)
                .containsExactlyInAnyOrder(
                        tuple(bottomBar.getSlatMaterial(), urgent.getYcsx(), 1),
                        tuple(bottomBar.getSlatMaterial(), later.getYcsx(), 2),
                        tuple(subSlat.getSlatMaterial(), urgent.getYcsx(), 1),
                        tuple(subSlat.getSlatMaterial(), later.getYcsx(), 2));
    }

    /** Ray cắt theo chiều CAO cửa, các nhóm còn lại cắt theo chiều rộng — cột kích thước gốc phải nói đúng nguồn. */
    @Test
    void buildFromPreview_usesDoorHeightAsSourceDimensionForRailOnly() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial bottomBar = persistSlatMaterial(SlatGroup.BOTTOM_BAR);
        SlatMaterial rail = persistSlatMaterial(SlatGroup.RAIL);
        persistBomItem(doorProduct, bottomBar);
        persistRailBomItem(doorProduct, rail);
        persistInventoryBatch(bottomBar, 2000, 1);
        persistInventoryBatch(rail, 2500, 2);
        persistSalesOrder(doorProduct, customer, new BigDecimal("2.500"), new BigDecimal("2.000"), LocalDate.now());

        List<CuttingPlanDemandView> rows = reportService.buildFromPreview(cuttingPlanService.simulate());

        assertThat(rows)
                .extracting(CuttingPlanDemandView::slatGroup, CuttingPlanDemandView::wsxM)
                .containsExactlyInAnyOrder(
                        tuple(SlatGroup.BOTTOM_BAR, new BigDecimal("2.000")),
                        tuple(SlatGroup.RAIL, new BigDecimal("2.500")));
    }

    /**
     * Bất biến quan trọng nhất của lớp này: cùng một phương án, xem trước khi duyệt và đọc lại sau
     * khi duyệt phải ra đúng những con số như nhau. Hai đường vào khác hẳn nhau — một bên đọc kết
     * quả thuật toán còn trong bộ nhớ, một bên đọc ba bảng đã lưu — nên nếu mỗi bên tự gộp lấy thì
     * báo cáo trình PLANNER duyệt và file Excel xuất sau đó có thể lệch nhau mà không ai phát hiện.
     *
     * <p>Chiều cao 2.345m cố ý không tròn tới centimet: bảng thiếu vật tư lưu tổng độ dài ở đơn vị
     * centimet nên độ dài đoạn suy ngược từ đó là 2350mm chứ không phải 2345mm. Một kích thước
     * tròn như 2.500m che mất hẳn sai lệch này, mà kích thước thật của khách hàng thì gần như
     * không bao giờ tròn.
     */
    @Test
    void approvedPlanAndPreview_produceTheSameNumbers() {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial sufficient = persistSlatMaterial(SlatGroup.BOTTOM_BAR);
        SlatMaterial rail = persistSlatMaterial(SlatGroup.RAIL);
        persistBomItem(doorProduct, sufficient);
        persistRailBomItem(doorProduct, rail);
        persistInventoryBatch(sufficient, 2000, 1);
        persistInventoryBatch(rail, 2500, 1);
        persistSalesOrder(doorProduct, customer, new BigDecimal("2.345"), new BigDecimal("2.000"), LocalDate.now());

        CuttingPlanApprovalPreview preview = cuttingPlanService.approvalPreview();
        List<CuttingPlanDemandView> beforeApproval = reportService.buildFromPreview(preview.plan());
        CuttingPlan plan = cuttingPlanService.approve(preview.stateFingerprint());
        List<CuttingPlanDemandView> afterApproval = reportService.buildFromApprovedPlan(plan.getId());

        assertThat(afterApproval)
                .extracting(
                        CuttingPlanDemandView::priorityRank,
                        CuttingPlanDemandView::ycsx,
                        CuttingPlanDemandView::slatMaterialCode,
                        CuttingPlanDemandView::cutLengthMm,
                        CuttingPlanDemandView::quantityNeeded,
                        CuttingPlanDemandView::quantityMissing,
                        CuttingPlanDemandView::statusText,
                        CuttingPlanDemandView::doorSetStatus)
                .containsExactlyElementsOf(beforeApproval.stream()
                        .map(row -> tuple(
                                row.priorityRank(),
                                row.ycsx(),
                                row.slatMaterialCode(),
                                row.cutLengthMm(),
                                row.quantityNeeded(),
                                row.quantityMissing(),
                                row.statusText(),
                                row.doorSetStatus()))
                        .toList());
        assertThat(beforeApproval)
                .extracting(CuttingPlanDemandView::cutLengthMm, CuttingPlanDemandView::statusText)
                .containsExactlyInAnyOrder(tuple(2000, "✔Đủ"), tuple(2345, "Thiếu 1 nan 2.35m (2.3m)"));
    }

    @Test
    void buildFromApprovedPlan_throwsWhenPlanDoesNotExist() {
        assertThatThrownBy(() -> reportService.buildFromApprovedPlan(-1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
