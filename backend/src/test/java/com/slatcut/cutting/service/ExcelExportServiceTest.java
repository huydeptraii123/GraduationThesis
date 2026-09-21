package com.slatcut.cutting.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.slatcut.cutting.AbstractIntegrationTest;
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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ExcelExportServiceTest extends AbstractIntegrationTest {

    /**
     * Đúng 21 tên cột và đúng thứ tự của khuôn mẫu doanh nghiệp (file "Chỉ dẫn tối ưu nan theo đơn
     * hàng"). Danh sách này cố ý viết tay lại chứ không đọc từ hằng số của lớp đang kiểm thử: nếu
     * lấy chung nguồn thì một lần đổi tên cột sẽ đổi luôn cả kỳ vọng và bài kiểm thử im lặng đồng
     * ý — trong khi hỏng khuôn cột chính là thứ làm công cụ báo cáo của doanh nghiệp đọc không ra.
     */
    private static final List<String> TEMPLATE_COLUMNS = List.of(
            "global_seq",
            "TT ưu tiên",
            "ycsx",
            "lenh_sx",
            "so_number",
            "customer_name",
            "component_material_description",
            "material_group",
            "wsx",
            "cut",
            "SL thanh cần",
            "SL thanh thiếu",
            "trang_thai_dap_ung",
            "chi_tiet_lo_su_dung",
            "delivery_date",
            "component_group",
            "component_material",
            "tp_nan_hien_co_da_tru_znan",
            "kc04_nan_hien_co",
            "co_open_hien_co",
            "trang_thai_bo_cua");

    private static final int COL_GLOBAL_SEQ = 0;
    private static final int COL_YCSX = 2;
    private static final int COL_LENH_SX = 3;
    private static final int COL_SO_NUMBER = 4;
    private static final int COL_CUT = 9;
    private static final int COL_NEEDED = 10;
    private static final int COL_MISSING = 11;
    private static final int COL_STATUS = 12;
    private static final int COL_CUT_DETAIL = 13;
    private static final int COL_DELIVERY_DATE = 14;
    private static final int COL_COMPONENT_GROUP = 15;
    private static final int COL_COMPONENT_MATERIAL = 16;
    private static final int COL_STOCK_SNAPSHOT = 17;

    @Autowired
    private ExcelExportService excelExportService;

    @Autowired
    private CuttingPlanService cuttingPlanService;

    @Autowired
    private CuttingPlanReportService reportService;

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
        entity.setMaterialGroup("CA-CA10");
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

    private SalesOrder persistSalesOrder(
            DoorProduct doorProduct, Customer customer, BigDecimal chieuRongDh, LocalDate reqdDeliveryDate) {
        SalesOrder entity = new SalesOrder();
        entity.setYcsx("HY8" + (++counter));
        entity.setItem(1);
        entity.setLenhSx(100_600_000_000L + counter);
        entity.setSalesDocument(1_000_000_000L + counter);
        entity.setCustomer(customer);
        entity.setDoorProduct(doorProduct);
        entity.setChieuCaoDh(new BigDecimal("2.500"));
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

    private Workbook toWorkbook(byte[] bytes) throws IOException {
        return WorkbookFactory.create(new ByteArrayInputStream(bytes));
    }

    private static List<String> headersOf(Sheet sheet) {
        List<String> headers = new ArrayList<>();
        for (Cell cell : sheet.getRow(0)) {
            headers.add(cell.getStringCellValue());
        }
        return headers;
    }

    /** Duyệt phương án cho phạm vi hiện tại — xem trước để lấy dấu vân, rồi duyệt bằng chính nó. */
    private CuttingPlan approvePlan() {
        return cuttingPlanService.approve(cuttingPlanService.approvalPreview().stateFingerprint());
    }

    private List<CuttingPlanDemandView> approvedRows() {
        return reportService.buildFromApprovedPlan(approvePlan().getId());
    }

    @Test
    void exportCuttingPlan_usesTheExactColumnNamesAndOrderOfTheCompanyTemplate() throws IOException {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 2200, 1);
        persistSalesOrder(doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());

        byte[] file = excelExportService.exportCuttingPlan(approvedRows());

        try (Workbook workbook = toWorkbook(file)) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            Sheet sheet = workbook.getSheet("Export");
            assertThat(sheet).isNotNull();
            assertThat(headersOf(sheet)).containsExactlyElementsOf(TEMPLATE_COLUMNS);
        }
    }

    @Test
    void exportCuttingPlan_writesOneRowPerMaterialOfEachDoorSet_withTheReportValues() throws IOException {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 2200, 1);
        LocalDate deliveryDate = LocalDate.now();
        SalesOrder order = persistSalesOrder(doorProduct, customer, new BigDecimal("2.000"), deliveryDate);

        byte[] file = excelExportService.exportCuttingPlan(approvedRows());

        try (Workbook workbook = toWorkbook(file)) {
            Sheet sheet = workbook.getSheet("Export");
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(2); // tiêu đề + 1 nhu cầu cắt
            Row row = sheet.getRow(1);

            assertThat((int) row.getCell(COL_GLOBAL_SEQ).getNumericCellValue()).isEqualTo(1);
            assertThat(row.getCell(COL_YCSX).getStringCellValue()).isEqualTo(order.getYcsx());
            assertThat(row.getCell(COL_CUT).getNumericCellValue()).isEqualTo(2.0);
            assertThat((int) row.getCell(COL_NEEDED).getNumericCellValue()).isEqualTo(1);
            assertThat((int) row.getCell(COL_MISSING).getNumericCellValue()).isZero();
            assertThat(row.getCell(COL_STATUS).getStringCellValue()).isEqualTo("✔Đủ");
            assertThat(row.getCell(COL_CUT_DETAIL).getStringCellValue())
                    .isEqualTo("[TP] 2200mm: 1 phôi → 1 nan [Cắt phế 0.20m, PA1] (còn lại 0 phôi)");
            assertThat(row.getCell(COL_DELIVERY_DATE).getLocalDateTimeCellValue().toLocalDate())
                    .isEqualTo(deliveryDate);
            assertThat(row.getCell(COL_COMPONENT_GROUP).getStringCellValue()).isEqualTo("Thanh đáy");
            assertThat(row.getCell(COL_STOCK_SNAPSHOT).getStringCellValue()).isEqualTo("2.20m 1 thanh");
            assertThat(row.getCell(COL_DELIVERY_DATE).getCellStyle().getDataFormatString())
                    .isEqualTo("dd/MM/yyyy");
            assertThat(row.getCell(COL_NEEDED).getCellStyle().getDataFormatString()).isEqualTo("#,0");
        }
    }

    /**
     * Ba cột định danh ghi thành CHỮ dù nội dung toàn chữ số — công cụ báo cáo của doanh nghiệp nối
     * bảng theo chúng, ghi thành số thì phép nối lệch kiểu và số 0 đứng đầu biến mất.
     */
    @Test
    void exportCuttingPlan_writesIdentifierColumnsAsTextNotNumbers() throws IOException {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 2200, 1);
        SalesOrder order = persistSalesOrder(doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());

        byte[] file = excelExportService.exportCuttingPlan(approvedRows());

        try (Workbook workbook = toWorkbook(file)) {
            Row row = workbook.getSheet("Export").getRow(1);
            assertThat(row.getCell(COL_LENH_SX).getCellType()).isEqualTo(CellType.STRING);
            assertThat(row.getCell(COL_LENH_SX).getStringCellValue())
                    .isEqualTo(String.valueOf(order.getLenhSx()));
            assertThat(row.getCell(COL_SO_NUMBER).getCellType()).isEqualTo(CellType.STRING);
            assertThat(row.getCell(COL_SO_NUMBER).getStringCellValue())
                    .isEqualTo(String.valueOf(order.getSalesDocument()));
            assertThat(row.getCell(COL_COMPONENT_MATERIAL).getCellType()).isEqualTo(CellType.STRING);
            assertThat(row.getCell(COL_COMPONENT_MATERIAL).getStringCellValue())
                    .isEqualTo(String.valueOf(slatMaterial.getSlatMaterial()));
        }
    }

    /** Hai cột chỉ có nguồn ở hệ thống quản trị của doanh nghiệp: giữ cột, để trống, không suy đoán. */
    @Test
    void exportCuttingPlan_leavesTheOutOfScopeColumnsBlank() throws IOException {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 2200, 1);
        persistSalesOrder(doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());

        byte[] file = excelExportService.exportCuttingPlan(approvedRows());

        try (Workbook workbook = toWorkbook(file)) {
            Row row = workbook.getSheet("Export").getRow(1);
            assertThat(row.getCell(TEMPLATE_COLUMNS.indexOf("kc04_nan_hien_co")).getCellType())
                    .isEqualTo(CellType.BLANK);
            assertThat(row.getCell(TEMPLATE_COLUMNS.indexOf("co_open_hien_co")).getCellType())
                    .isEqualTo(CellType.BLANK);
        }
    }

    @Test
    void exportShortageReport_keepsOnlyRowsStillMissingSticks_withTheReducedColumnSet() throws IOException {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 2200, 1);
        LocalDate shortDeliveryDate = LocalDate.now().plusDays(1);
        persistSalesOrder(doorProduct, customer, new BigDecimal("2.000"), LocalDate.now());
        SalesOrder shortOrder = persistSalesOrder(doorProduct, customer, new BigDecimal("4.000"), shortDeliveryDate);

        List<CuttingPlanDemandView> rows = approvedRows();
        byte[] file = excelExportService.exportShortageReport(rows);

        try (Workbook workbook = toWorkbook(file)) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            Sheet sheet = workbook.getSheet("Export");
            assertThat(headersOf(sheet))
                    .containsExactly(
                            "TT ưu tiên",
                            "ycsx",
                            "lenh_sx",
                            "customer_name",
                            "component_material_description",
                            "material_group",
                            "cut",
                            "SL thanh cần",
                            "SL thanh thiếu",
                            "trang_thai_dap_ung",
                            "delivery_date",
                            "component_group",
                            "component_material");

            // 2 bộ cửa đi vào phương án, chỉ bộ thiếu vật tư còn lại trong bản lọc.
            assertThat(rows).hasSize(2);
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(2);
            Row row = sheet.getRow(1);
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo(shortOrder.getYcsx());
            assertThat(row.getCell(9).getStringCellValue()).startsWith("Thiếu toàn bộ");
        }
    }
}
