package com.slatcut.cutting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.slatcut.cutting.AbstractIntegrationTest;
import com.slatcut.cutting.config.ImportValidationException;
import com.slatcut.cutting.domain.Customer;
import com.slatcut.cutting.domain.CuttingPlan;
import com.slatcut.cutting.domain.CuttingPlanStatus;
import com.slatcut.cutting.domain.DoorProduct;
import com.slatcut.cutting.domain.SalesOrder;
import com.slatcut.cutting.dto.ImportRowError;
import com.slatcut.cutting.dto.SalesOrderImportResult;
import com.slatcut.cutting.repository.CustomerRepository;
import com.slatcut.cutting.repository.CuttingPlanRepository;
import com.slatcut.cutting.repository.DoorProductRepository;
import com.slatcut.cutting.repository.SalesOrderRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class SalesOrderImportServiceTest extends AbstractIntegrationTest {

    private static final Object[] HEADER = {
        "ycsx",
        "z_item",
        "sales_document",
        "sales_order_item",
        "customer",
        "customer_name",
        "material",
        "item_description",
        "material_group",
        "z_mau_sac",
        "reqd_delivery_date",
        "z_chieu_cao_dh",
        "z_chieu_rong_dh"
    };

    private static final Path REAL_DATASET = Path.of("..", "dataset", "v_ztb_ycsx.xlsx");

    @Autowired
    private SalesOrderImportService service;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CuttingPlanRepository cuttingPlanRepository;

    @Autowired
    private DoorProductRepository doorProductRepository;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    /** Dựng file .xlsx trong bộ nhớ: mỗi Object[] là 1 dòng, phần tử null = ô trống, LocalDate = ô ngày. */
    private MultipartFile excel(Object[]... rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
            Sheet sheet = workbook.createSheet("orders");
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r);
                Object[] values = rows[r];
                for (int c = 0; c < values.length; c++) {
                    Object value = values[c];
                    if (value == null) {
                        continue;
                    }
                    Cell cell = row.createCell(c);
                    if (value instanceof LocalDate date) {
                        cell.setCellValue(date);
                        cell.setCellStyle(dateStyle);
                    } else if (value instanceof Number number) {
                        cell.setCellValue(number.doubleValue());
                    } else {
                        cell.setCellValue(value.toString());
                    }
                }
            }
            workbook.write(out);
            return new MockMultipartFile(
                    "file",
                    "don-hang.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Object[] doorRow(String ycsx, int item, long salesDocument, int salesOrderItem, long customer) {
        return new Object[] {
            ycsx,
            item,
            salesDocument,
            salesOrderItem,
            customer,
            "Khách hàng " + customer,
            85000001L,
            "Cửa A48i dày 1.1-1.2mm (#05)",
            "CA-A48I",
            "#05",
            LocalDate.of(2026, 9, 28),
            2.500,
            3.500
        };
    }

    /** Dòng phụ kiện khác (bộ tời/motor...) — cùng lô sản xuất, thừa hưởng kích thước cửa nhưng
     *  không phải bản thân cửa (material_group không bắt đầu bằng "CA-") — phải bị bỏ qua khỏi
     *  phạm vi nhập, kể cả khi có đủ z_chieu_cao_dh/z_chieu_rong_dh, không phải mọi trường khác. */
    private Object[] nonDoorRow(String ycsx, long customer) {
        return new Object[] {
            ycsx, 1, 1000000000L + customer, 1, customer, "Khách hàng " + customer, 70000001L, "Bộ tời AH500A",
            "AH500A", null, LocalDate.of(2026, 9, 28), 2.500, 3.500
        };
    }

    private Customer persistCustomer(long code, String name) {
        Customer entity = new Customer();
        entity.setCustomer(code);
        entity.setCustomerName(name);
        return customerRepository.save(entity);
    }

    private DoorProduct persistDoorProduct(long material, String mauSac, String name) {
        DoorProduct entity = new DoorProduct();
        entity.setMaterial(material);
        entity.setDoorMaterialName(name);
        entity.setMauSac(mauSac);
        return doorProductRepository.save(entity);
    }

    private List<ImportRowError> errorsOf(MultipartFile file) {
        ImportValidationException exception =
                catchThrowableOfType(ImportValidationException.class, () -> service.importFromExcel(file));
        assertThat(exception).isNotNull();
        return exception.getErrors();
    }

    @Test
    void importFromExcel_createsMissingCustomerAndDoorProductAndPersistsOrder() {
        SalesOrderImportResult result =
                service.importFromExcel(excel(HEADER, doorRow("HY10001", 1, 1000100001L, 1, 92000001L)));

        assertThat(result.totalRowsImported()).isEqualTo(1);
        assertThat(customerRepository.findByCustomer(92000001L)).isPresent();
        DoorProduct createdProduct =
                doorProductRepository.findByMaterialAndMauSac(85000001L, "#05").orElseThrow();
        assertThat(createdProduct.getDoorMaterialName()).isEqualTo("Cửa A48i dày 1.1-1.2mm (#05)");
        SalesOrder createdOrder = salesOrderRepository.findByYcsxAndItem("HY10001", 1).orElseThrow();
        assertThat(createdOrder.getSalesDocument()).isEqualTo(1000100001L);
        assertThat(createdOrder.getReqdDeliveryDate()).isEqualTo(LocalDate.of(2026, 9, 28));
    }

    @Test
    void importFromExcel_neverOverwritesExistingCustomerName() {
        persistCustomer(92000002L, "Tên gốc trong hệ thống");

        service.importFromExcel(excel(HEADER, doorRow("HY10002", 1, 1000100002L, 1, 92000002L)));

        assertThat(customerRepository.findByCustomer(92000002L).orElseThrow().getCustomerName())
                .isEqualTo("Tên gốc trong hệ thống");
    }

    @Test
    void importFromExcel_neverOverwritesExistingDoorProductName() {
        persistDoorProduct(85000001L, "#05", "Tên mẫu cửa gốc");

        service.importFromExcel(excel(HEADER, doorRow("HY10003", 1, 1000100003L, 1, 92000003L)));

        assertThat(doorProductRepository
                        .findByMaterialAndMauSac(85000001L, "#05")
                        .orElseThrow()
                        .getDoorMaterialName())
                .isEqualTo("Tên mẫu cửa gốc");
    }

    @Test
    void importFromExcel_upsertsExistingOrderByYcsxItemKeepingItsId() {
        Customer customer = persistCustomer(92000004L, "Khách hàng cũ");
        DoorProduct doorProduct = persistDoorProduct(85000001L, "#05", "Cửa cũ");
        SalesOrder existing = new SalesOrder();
        existing.setYcsx("HY10004");
        existing.setItem(1);
        existing.setSalesDocument(1L);
        existing.setSalesOrderItem(1);
        existing.setCustomer(customer);
        existing.setDoorProduct(doorProduct);
        existing.setChieuCaoDh(new BigDecimal("1.000"));
        existing.setChieuRongDh(new BigDecimal("1.000"));
        existing.setReqdDeliveryDate(LocalDate.of(2020, 1, 1));
        existing = salesOrderRepository.save(existing);

        service.importFromExcel(excel(HEADER, doorRow("HY10004", 1, 1000100004L, 9, 92000004L)));

        SalesOrder updated = salesOrderRepository.findById(existing.getId()).orElseThrow();
        assertThat(updated.getSalesDocument()).isEqualTo(1000100004L);
        assertThat(updated.getReqdDeliveryDate()).isEqualTo(LocalDate.of(2026, 9, 28));
    }

    /**
     * Đơn hàng được nhập lại từ hệ thống nguồn mỗi ngày. Nếu lượt nhập ghi đè trạng thái đã duyệt,
     * toàn bộ đơn đã chốt sẽ quay lại hàng chờ sau đúng một lần nhập định kỳ và bị cắt lần hai
     * trên tồn kho đã bị trừ. Bất biến này không có ràng buộc CSDL nào bảo vệ nên phải khóa bằng
     * test — kể cả khi cách cài đặt thay đổi (nay lượt nhập bỏ qua hẳn đơn đã duyệt, nhưng khẳng
     * định cần giữ vẫn là "trạng thái đã duyệt sống sót qua lượt nhập").
     */
    @Test
    void importFromExcel_doesNotResetApprovedPlanOfAlreadyApprovedOrder() {
        Customer customer = persistCustomer(92000014L, "Khách hàng đã duyệt");
        DoorProduct doorProduct = persistDoorProduct(85000011L, "#05", "Cửa đã duyệt");
        SalesOrder existing = new SalesOrder();
        existing.setYcsx("HY10014");
        existing.setItem(1);
        existing.setSalesDocument(1L);
        existing.setSalesOrderItem(1);
        existing.setCustomer(customer);
        existing.setDoorProduct(doorProduct);
        existing.setChieuCaoDh(new BigDecimal("1.000"));
        existing.setChieuRongDh(new BigDecimal("1.000"));
        existing.setReqdDeliveryDate(LocalDate.of(2020, 1, 1));
        existing.setApprovedPlan(persistCuttingPlan());
        existing = salesOrderRepository.save(existing);
        Long approvedPlanId = existing.getApprovedPlan().getId();

        service.importFromExcel(excel(HEADER, doorRow("HY10014", 1, 1000100014L, 9, 92000014L)));

        SalesOrder updated = salesOrderRepository.findById(existing.getId()).orElseThrow();
        assertThat(updated.getApprovedPlan()).isNotNull();
        assertThat(updated.getApprovedPlan().getId()).isEqualTo(approvedPlanId);
        // Bản ghi giữ nguyên hoàn toàn, không riêng gì cột trạng thái.
        assertThat(updated.getSalesDocument()).isEqualTo(1L);
        assertThat(updated.getChieuRongDh()).isEqualByComparingTo(new BigDecimal("1.000"));
    }

    private CuttingPlan persistCuttingPlan() {
        CuttingPlan plan = new CuttingPlan();
        plan.setRunAt(java.time.LocalDateTime.now());
        plan.setStatus(CuttingPlanStatus.COMPLETED);
        plan.setScopeCutoffDate(LocalDate.now().plusDays(3));
        plan.setScopeOrderCount(1);
        plan.setTotalWasteM(BigDecimal.ZERO);
        plan.setTotalStockUsedM(BigDecimal.ZERO);
        return cuttingPlanRepository.save(plan);
    }

    /**
     * Nan của bộ cửa đã duyệt đã cắt theo đúng kích thước đang lưu và đã ra khỏi kho. Ghi đè kích
     * thước mới chỉ làm hồ sơ lệch với vật tư thực tế mà không khiến bộ cửa được cắt lại, vì trạng
     * thái đã duyệt giữ nó ngoài mọi lần chạy sau.
     */
    @Test
    void importFromExcel_doesNotOverwriteApprovedOrderAndReportsConflict() {
        Customer customer = persistCustomer(92000015L, "Khách hàng đã cắt");
        DoorProduct doorProduct = persistDoorProduct(85000012L, "#05", "Cửa đã cắt");
        SalesOrder existing = new SalesOrder();
        existing.setYcsx("HY10015");
        existing.setItem(1);
        existing.setSalesDocument(1000100015L);
        existing.setSalesOrderItem(9);
        existing.setCustomer(customer);
        existing.setDoorProduct(doorProduct);
        existing.setChieuCaoDh(new BigDecimal("2.500"));
        existing.setChieuRongDh(new BigDecimal("4.870"));
        existing.setReqdDeliveryDate(LocalDate.of(2026, 9, 28));
        existing.setApprovedPlan(persistCuttingPlan());
        existing = salesOrderRepository.save(existing);

        // Cùng bộ cửa, nhưng chiều rộng trong file nguồn đã được đính chính 4.870 -> 3.500.
        SalesOrderImportResult result =
                service.importFromExcel(excel(HEADER, doorRow("HY10015", 1, 1000100015L, 9, 92000015L)));

        SalesOrder unchanged = salesOrderRepository.findById(existing.getId()).orElseThrow();
        assertThat(unchanged.getChieuRongDh()).isEqualByComparingTo(new BigDecimal("4.870"));
        assertThat(result.approvedOrderConflicts()).hasSize(1);
        assertThat(result.approvedOrderConflicts().get(0).ycsx()).isEqualTo("HY10015");
        assertThat(result.approvedOrderConflicts().get(0).changedFields())
                .anyMatch(field -> field.startsWith("chiều rộng"));
    }

    /**
     * File nguồn xuất lại toàn bộ tồn đọng mỗi ngày nên phần lớn dòng đã duyệt đều trùng khớp —
     * cảnh báo cho chúng chỉ tạo nhiễu và làm PLANNER bỏ qua cả những cảnh báo thật.
     */
    @Test
    void importFromExcel_reportsNoConflictWhenApprovedOrderDataIsUnchanged() {
        Customer customer = persistCustomer(92000016L, "Khách hàng 92000016");
        DoorProduct doorProduct = persistDoorProduct(85000001L, "#05", "Cửa A48i dày 1.1-1.2mm (#05)");
        SalesOrder existing = new SalesOrder();
        existing.setYcsx("HY10016");
        existing.setItem(1);
        existing.setSalesDocument(1000100016L);
        existing.setSalesOrderItem(9);
        existing.setCustomer(customer);
        existing.setDoorProduct(doorProduct);
        existing.setChieuCaoDh(new BigDecimal("2.500"));
        existing.setChieuRongDh(new BigDecimal("3.500"));
        existing.setReqdDeliveryDate(LocalDate.of(2026, 9, 28));
        existing.setApprovedPlan(persistCuttingPlan());
        salesOrderRepository.save(existing);

        SalesOrderImportResult result =
                service.importFromExcel(excel(HEADER, doorRow("HY10016", 1, 1000100016L, 9, 92000016L)));

        assertThat(result.approvedOrderConflicts()).isEmpty();
    }

    @Test
    void importFromExcel_reportsNoConflictForOrdersNotYetApproved() {
        SalesOrderImportResult result =
                service.importFromExcel(excel(HEADER, doorRow("HY10017", 1, 1000100017L, 9, 92000017L)));

        assertThat(result.totalRowsImported()).isEqualTo(1);
        assertThat(result.approvedOrderConflicts()).isEmpty();
    }

    @Test
    void importFromExcel_skipsRowsWhoseMaterialGroupIsNotADoorWithoutError() {
        SalesOrderImportResult result =
                service.importFromExcel(excel(HEADER, nonDoorRow("HY10005", 92000005L)));

        assertThat(result.totalRowsImported()).isZero();
        assertThat(result.skippedNonDoorRows()).isEqualTo(1);
    }

    @Test
    void importFromExcel_skipsRowsWithBlankMaterialGroup() {
        Object[] blankGroupRow = new Object[] {
            "HY10006", 1, 1000100006L, 1, 92000006L, "Khách hàng", 70000001L, "Không rõ loại", null, null,
            LocalDate.of(2026, 9, 28), 2.5, 3.5
        };

        SalesOrderImportResult result = service.importFromExcel(excel(HEADER, blankGroupRow));

        assertThat(result.totalRowsImported()).isZero();
        assertThat(result.skippedNonDoorRows()).isEqualTo(1);
    }

    @Test
    void importFromExcel_countsSkippedNonDoorRowsSeparatelyFromImportedRows() {
        SalesOrderImportResult result = service.importFromExcel(excel(
                HEADER,
                doorRow("HY10008", 1, 1000100008L, 1, 92000008L),
                nonDoorRow("HY10007", 92000007L)));

        assertThat(result.totalRowsImported()).isEqualTo(1);
        assertThat(result.skippedNonDoorRows()).isEqualTo(1);
    }

    @Test
    void importFromExcel_rejectsInScopeDoorRowMissingDimension() {
        Object[] doorRowMissingWidth = new Object[] {
            "HY10020", 1, 1000100021L, 1, 92000020L, "Khách hàng", 85000001L, "Cửa thiếu chiều rộng", "CA-A48I",
            "#05", LocalDate.of(2026, 9, 28), 2.5, null
        };

        assertThat(errorsOf(excel(HEADER, doorRowMissingWidth)))
                .singleElement()
                .satisfies(error -> assertThat(error.message()).contains("chiều rộng cửa"));
    }

    @Test
    void importFromExcel_skipsBlankRowsWithoutCountingThemAsNonDoorRows() {
        SalesOrderImportResult result = service.importFromExcel(excel(
                HEADER, doorRow("HY10009", 1, 1000100009L, 1, 92000009L), new Object[] {}));

        assertThat(result.totalRowsImported()).isEqualTo(1);
        assertThat(result.skippedNonDoorRows()).isZero();
    }

    @Test
    void importFromExcel_reportsEveryMissingRequiredColumn() {
        MultipartFile file = excel(new Object[] {"ycsx", "z_item"}, new Object[] {"HY10010", 1});

        assertThat(errorsOf(file))
                .hasSize(11)
                .allSatisfy(error -> assertThat(error.rowNumber()).isEqualTo(1))
                .extracting(ImportRowError::message)
                .anySatisfy(message -> assertThat(message).contains("sales_document"))
                .anySatisfy(message -> assertThat(message).contains("z_chieu_cao_dh"));
    }

    @Test
    void importFromExcel_throwsWhenHeaderRowIsMissing() {
        assertThat(errorsOf(excel()))
                .singleElement()
                .satisfies(error -> {
                    assertThat(error.rowNumber()).isEqualTo(1);
                    assertThat(error.message()).contains("dòng tiêu đề");
                });
    }

    @Test
    void importFromExcel_throwsWhenFileIsNotAnExcelWorkbook() {
        MultipartFile file = new MockMultipartFile(
                "file", "don-hang.xlsx", "text/plain", "khong phai excel".getBytes(StandardCharsets.UTF_8));

        assertThat(errorsOf(file)).singleElement().satisfies(error -> assertThat(error.message())
                .contains("không đọc được"));
    }

    @Test
    void importFromExcel_rejectsDuplicateYcsxItemWithinSameFile() {
        MultipartFile file = excel(
                HEADER,
                doorRow("HY10011", 1, 1000100011L, 1, 92000011L),
                doorRow("HY10011", 1, 1000100012L, 2, 92000011L));

        assertThat(errorsOf(file)).singleElement().satisfies(error -> assertThat(error.message())
                .contains("Trùng tổ hợp (ycsx, z_item)"));
    }

    @Test
    void importFromExcel_rejectsDuplicateSalesDocumentItemWithinSameFile() {
        MultipartFile file = excel(
                HEADER,
                doorRow("HY10012", 1, 1000100013L, 1, 92000012L),
                doorRow("HY10013", 1, 1000100013L, 1, 92000012L));

        assertThat(errorsOf(file)).singleElement().satisfies(error -> assertThat(error.message())
                .contains("Trùng tổ hợp (sales_document, sales_order_item)"));
    }

    @Test
    void importFromExcel_collectsEveryErrorOfEveryRowInsteadOfFailingFast() {
        Object[] rowMissingYcsxAndCustomerName = new Object[] {
            null, 1, 1000100014L, 1, 92000013L, null, 85000001L, "Cửa lỗi", "CA-A48I", "#05",
            LocalDate.of(2026, 9, 28), 2.5, 3.5
        };
        Object[] rowWithInvalidDate = new Object[] {
            "HY10015",
            1,
            1000100015L,
            1,
            92000014L,
            "Khách hàng",
            85000001L,
            "Cửa lỗi ngày",
            "CA-A48I",
            "#05",
            "khong-phai-ngay",
            2.5,
            3.5
        };
        MultipartFile file = excel(HEADER, rowMissingYcsxAndCustomerName, rowWithInvalidDate);

        List<ImportRowError> errors = errorsOf(file);

        assertThat(errors).hasSize(3);
        assertThat(errors).filteredOn(error -> error.rowNumber() == 2).hasSize(2);
        assertThat(errors).filteredOn(error -> error.rowNumber() == 3).hasSize(1);
    }

    @Test
    void importFromExcel_writesNothingWhenAnyInScopeRowIsInvalid() {
        MultipartFile file = excel(
                HEADER,
                doorRow("HY10016", 1, 1000100016L, 1, 92000015L),
                new Object[] {
                    null, 1, 1000100017L, 1, 92000016L, "Khách hàng", 85000001L, "Cửa lỗi", "CA-A48I", "#05",
                    LocalDate.of(2026, 9, 28), 2.5, 3.5
                });

        assertThatThrownBy(() -> service.importFromExcel(file)).isInstanceOf(ImportValidationException.class);

        assertThat(salesOrderRepository.findByYcsxAndItem("HY10016", 1)).isEmpty();
        assertThat(customerRepository.findByCustomer(92000015L)).isEmpty();
    }

    @Test
    void importFromExcel_reportsErrorsUsingRealExcelRowNumbers() {
        MultipartFile file = excel(
                HEADER,
                doorRow("HY10017", 1, 1000100018L, 1, 92000017L),
                new Object[] {
                    null, 1, 1000100019L, 1, 92000018L, "Khách hàng", 85000001L, "Cửa lỗi", "CA-A48I", "#05",
                    LocalDate.of(2026, 9, 28), 2.5, 3.5
                });

        assertThat(errorsOf(file)).singleElement().satisfies(error -> {
            assertThat(error.rowNumber()).isEqualTo(3);
            assertThat(error.message()).contains("Thiếu mã lô sản xuất");
        });
    }

    @Test
    void importFromExcel_rejectsUnparsableOrMissingDate() {
        Object[] row = new Object[] {
            "HY10018",
            1,
            1000100020L,
            1,
            92000019L,
            "Khách hàng",
            85000001L,
            "Cửa ngày sai",
            "CA-A48I",
            "#05",
            "khong-phai-ngay",
            2.5,
            3.5
        };

        assertThat(errorsOf(excel(HEADER, row))).singleElement().satisfies(error -> assertThat(error.message())
                .contains("ngày giao yêu cầu"));
    }

    @Test
    void importFromExcel_importsTheRealSalesOrderFileConsistently() throws IOException {
        Assumptions.assumeTrue(Files.exists(REAL_DATASET), "Không có file đơn hàng thật trên máy này");

        MultipartFile file = new MockMultipartFile(
                "file",
                REAL_DATASET.getFileName().toString(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                Files.readAllBytes(REAL_DATASET));

        // Dữ liệu SAP thật hiện có một số dòng cửa (material_group bắt đầu bằng "CA-") thiếu
        // customer_name — lỗi dữ liệu nguồn có thật tại thời điểm viết test này, không phải lỗi
        // hệ thống. Theo đúng NFR "toàn bộ lượt nhập bị hủy khi có dòng lỗi", file bị từ chối
        // trọn vẹn trong trường hợp đó. Test xác nhận MỌI lỗi phát sinh đều thuộc đúng loại đã
        // biết (customer_name) — nếu có loại lỗi khác lọt qua, coi là lỗi logic parse thật.
        SalesOrderImportResult[] resultHolder = new SalesOrderImportResult[1];
        ImportValidationException exception = catchThrowableOfType(
                ImportValidationException.class, () -> resultHolder[0] = service.importFromExcel(file));

        if (exception != null) {
            assertThat(exception.getErrors())
                    .isNotEmpty()
                    .allSatisfy(error -> assertThat(error.message()).contains("customer_name"));
            return;
        }

        SalesOrderImportResult result = resultHolder[0];
        assertThat(result.totalRowsImported()).isPositive();
        assertThat(salesOrderRepository.findAll())
                .hasSize(result.totalRowsImported())
                .allSatisfy(order -> {
                    assertThat(order.getChieuCaoDh()).isPositive();
                    assertThat(order.getChieuRongDh()).isPositive();
                    assertThat(order.getReqdDeliveryDate()).isNotNull();
                });
    }
}
