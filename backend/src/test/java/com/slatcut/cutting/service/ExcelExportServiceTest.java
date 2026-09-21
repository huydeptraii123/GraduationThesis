package com.slatcut.cutting.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.slatcut.cutting.AbstractIntegrationTest;
import com.slatcut.cutting.domain.CuttingPlan;
import com.slatcut.cutting.domain.BomItem;
import com.slatcut.cutting.domain.Customer;
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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ExcelExportServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ExcelExportService excelExportService;

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

    private SlatMaterial persistSlatMaterial() {
        SlatMaterial entity = new SlatMaterial();
        entity.setSlatMaterial(70_000_000L + ++counter);
        entity.setSlatMaterialName("Thanh nan " + counter);
        entity.setSlatGroup(SlatGroup.BOTTOM_BAR);
        return slatMaterialRepository.save(entity);
    }

    private BomItem persistBomItem(DoorProduct doorProduct, SlatMaterial slatMaterial) {
        BomItem entity = new BomItem();
        entity.setDoorProduct(doorProduct);
        entity.setSlatMaterial(slatMaterial);
        return bomItemRepository.save(entity);
    }

    private SalesOrder persistSalesOrder(
            String ycsx, DoorProduct doorProduct, Customer customer, BigDecimal chieuRongDh, LocalDate reqdDeliveryDate) {
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

    /**
     * Đọc ô về String dễ so sánh trong assertion — Cell.toString() mặc định trả số nguyên dạng
     * "1.0" (kiểu double bên trong POI), không dùng trực tiếp được để so khớp "1".
     */
    private static String cellText(Row row, int column) {
        Cell cell = row.getCell(column);
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double value = cell.getNumericCellValue();
                yield value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
            }
            default -> cell.toString();
        };
    }

    /**
     * Duyệt phương án cho phạm vi hiện tại — thay cho đường ghi một bước đã gỡ. Đi qua đúng luồng
     * thật: xem trước để lấy dấu vân trạng thái, rồi duyệt bằng chính dấu vân đó.
     */
    private CuttingPlan approvePlan() {
        return cuttingPlanService.approve(cuttingPlanService.approvalPreview().stateFingerprint());
    }

    @Test
    void exportCuttingPlan_mergedOrdersOnOneStick_showsOriginalAndMergedColumns() throws IOException {
        Customer customerA = persistCustomer();
        Customer customerB = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        persistInventoryBatch(slatMaterial, 6000, 1);
        SalesOrder orderA =
                persistSalesOrder("A" + (counter + 1), doorProduct, customerA, new BigDecimal("3.000"), LocalDate.now());
        persistSalesOrder("B" + (counter + 1), doorProduct, customerB, new BigDecimal("3.000"), LocalDate.now());

        var plan = approvePlan();
        byte[] file = excelExportService.exportCuttingPlan(plan.getId());

        try (Workbook workbook = toWorkbook(file)) {
            Sheet sheet = workbook.getSheet("Kết quả cắt");
            assertThat(sheet).isNotNull();
            Row header = sheet.getRow(0);
            // getLastCellNum() trả short — AssertJ tự động box thành Short, so isEqualTo(14) autobox
            // thành Integer thì luôn fail dù giá trị khớp (Short.equals(Integer) luôn false).
            assertThat((int) header.getLastCellNum()).isEqualTo(14);
            assertThat(cellText(header, 0)).isEqualTo("Đợt cắt");

            Row dataRow = sheet.getRow(1);
            assertThat(dataRow).isNotNull();
            assertThat(cellText(dataRow, 0)).isEqualTo("1"); // 1 mẫu cửa -> đúng 1 đợt cắt
            assertThat(cellText(dataRow, 1)).isEqualTo(orderA.getYcsx());
            assertThat(cellText(dataRow, 11)).contains("(1 đoạn)");
        }
    }

    @Test
    void exportCuttingPlan_eightOrdersSameDoorProduct_splitsIntoTwoBatches() throws IOException {
        Customer customer = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        LocalDate sameDate = LocalDate.now();
        List<SalesOrder> orders = new ArrayList<>();
        // Mỗi đơn 1 độ dài cắt riêng (1001..1008mm) + 1 thanh tồn kho dư đúng 100mm khớp riêng nó
        // (Mức 1) — tránh rủi ro Mức 2/3 ghép nhầm giữa các đơn khi test chỉ quan tâm số đợt cắt.
        for (int i = 1; i <= 8; i++) {
            SalesOrder order = persistSalesOrder(
                    String.format("A%03d", i), doorProduct, customer, new BigDecimal(String.format("1.%03d", i)), sameDate);
            int cutLengthMm = 1000 + i;
            persistInventoryBatch(slatMaterial, cutLengthMm + 100, 1);
            orders.add(order);
        }

        var plan = approvePlan();
        byte[] file = excelExportService.exportCuttingPlan(plan.getId());

        try (Workbook workbook = toWorkbook(file)) {
            Sheet sheet = workbook.getSheet("Kết quả cắt");
            List<String> batchNumberByYcsx = new ArrayList<>();
            for (int r = 1; r <= 8; r++) {
                Row row = sheet.getRow(r);
                batchNumberByYcsx.add(cellText(row, 0) + ":" + cellText(row, 1));
            }
            long batch1Count = batchNumberByYcsx.stream().filter(s -> s.startsWith("1:")).count();
            long batch2Count = batchNumberByYcsx.stream().filter(s -> s.startsWith("2:")).count();
            assertThat(batch1Count).isEqualTo(7);
            assertThat(batch2Count).isEqualTo(1);
            assertThat(batchNumberByYcsx).anyMatch(s -> s.equals("2:" + orders.get(7).getYcsx()));
        }
    }

    @Test
    void exportShortageReport_summarySheetGroupsByMaterial_detailSheetKeepsRealDeliveryDate() throws IOException {
        Customer customerA = persistCustomer();
        Customer customerB = persistCustomer();
        DoorProduct doorProduct = persistDoorProduct();
        SlatMaterial slatMaterial = persistSlatMaterial();
        persistBomItem(doorProduct, slatMaterial);
        LocalDate deliveryDateA = LocalDate.now();
        LocalDate deliveryDateB = LocalDate.now().plusDays(1);
        persistSalesOrder("A" + (counter + 1), doorProduct, customerA, new BigDecimal("5.000"), deliveryDateA);
        persistSalesOrder("B" + (counter + 1), doorProduct, customerB, new BigDecimal("6.000"), deliveryDateB);

        var plan = approvePlan();
        byte[] file = excelExportService.exportShortageReport(plan.getId());

        try (Workbook workbook = toWorkbook(file)) {
            Sheet summary = workbook.getSheet("Tổng hợp theo vật tư");
            assertThat(summary.getPhysicalNumberOfRows()).isEqualTo(2); // header + 1 vật tư gộp
            Row summaryRow = summary.getRow(1);
            assertThat(cellText(summaryRow, 2)).isEqualTo("2"); // tổng SL đoạn thiếu = 2 đơn

            Sheet detail = workbook.getSheet("Chi tiết theo đơn hàng");
            assertThat(detail.getPhysicalNumberOfRows()).isEqualTo(3); // header + 2 đơn thiếu
            DateTimeFormatter format = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            List<String> deliveryDates = List.of(cellText(detail.getRow(1), 7), cellText(detail.getRow(2), 7));
            assertThat(deliveryDates).containsExactlyInAnyOrder(deliveryDateA.format(format), deliveryDateB.format(format));
        }
    }
}
