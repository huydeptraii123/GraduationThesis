package com.slatcut.cutting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.slatcut.cutting.AbstractIntegrationTest;
import com.slatcut.cutting.domain.InventoryBatch;
import com.slatcut.cutting.domain.SalesOrder;
import com.slatcut.cutting.domain.SlatGroup;
import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.dto.BomImportResult;
import com.slatcut.cutting.dto.InventoryImportResult;
import com.slatcut.cutting.dto.SalesOrderImportResult;
import com.slatcut.cutting.repository.DoorProductRepository;
import com.slatcut.cutting.repository.InventoryBatchRepository;
import com.slatcut.cutting.repository.SalesOrderRepository;
import com.slatcut.cutting.repository.SlatMaterialRepository;
import com.slatcut.cutting.service.optimizer.CutRecord;
import com.slatcut.cutting.service.optimizer.CuttingPlanResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFDataValidation;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.STDataValidationType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

class ImportTemplateServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ImportTemplateService templateService;

    @Autowired
    private BomImportService bomImportService;

    @Autowired
    private InventoryImportService inventoryImportService;

    @Autowired
    private SalesOrderImportService salesOrderImportService;

    @Autowired
    private CuttingPlanService cuttingPlanService;

    @Autowired
    private SlatMaterialRepository slatMaterialRepository;

    @Autowired
    private InventoryBatchRepository inventoryBatchRepository;

    @Autowired
    private DoorProductRepository doorProductRepository;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    private static XSSFWorkbook open(byte[] file) {
        try {
            return new XSSFWorkbook(new ByteArrayInputStream(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static MockMultipartFile upload(byte[] file, String name) {
        return new MockMultipartFile(
                "file", name, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", file);
    }

    private static List<String> header(Sheet sheet) {
        List<String> names = new ArrayList<>();
        for (Cell cell : sheet.getRow(0)) {
            names.add(cell.getStringCellValue());
        }
        return names;
    }

    private static int columnOf(Sheet sheet, String name) {
        return header(sheet).indexOf(name);
    }

    /** File mẫu sau khi người dùng xóa dòng ví dụ — chỉ còn dòng tiêu đề. */
    private static byte[] withoutExamples(byte[] template) {
        try (XSSFWorkbook workbook = open(template); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int r = sheet.getLastRowNum(); r >= 1; r--) {
                sheet.removeRow(sheet.getRow(r));
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void headerRow_isExactlyWhatEachReaderRequires() {
        try (XSSFWorkbook bom = open(templateService.bomTemplate());
                XSSFWorkbook inventory = open(templateService.inventoryTemplate());
                XSSFWorkbook salesOrder = open(templateService.salesOrderTemplate())) {
            assertThat(header(bom.getSheetAt(0))).containsExactlyElementsOf(BomImportService.REQUIRED_COLUMNS);
            assertThat(header(inventory.getSheetAt(0)))
                    .containsExactlyElementsOf(InventoryImportService.REQUIRED_COLUMNS);
            // Đơn hàng: đủ cột bắt buộc + đúng một cột tùy chọn mà trình đọc biết đọc (lệnh sản xuất).
            assertThat(header(salesOrder.getSheetAt(0)))
                    .containsExactlyInAnyOrderElementsOf(Stream.concat(
                                    SalesOrderImportService.REQUIRED_COLUMNS.stream(),
                                    Stream.of(SalesOrderImportService.OPTIONAL_PRODUCTION_ORDER_COLUMN))
                            .toList());

            for (XSSFWorkbook workbook : List.of(bom, inventory, salesOrder)) {
                assertThat(workbook.getSheetName(0)).isEqualTo(ImportTemplateService.DATA_SHEET);
                assertThat(workbook.getSheetName(1)).isEqualTo(ImportTemplateService.GUIDE_SHEET);
                // Mỗi ô tiêu đề có ghi chú giải nghĩa cột.
                for (Cell cell : workbook.getSheetAt(0).getRow(0)) {
                    assertThat(cell.getCellComment()).as(cell.getStringCellValue()).isNotNull();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void columns_arePreformattedSoExcelKeepsTheTypeTheReaderExpects() {
        try (XSSFWorkbook bom = open(templateService.bomTemplate());
                XSSFWorkbook salesOrder = open(templateService.salesOrderTemplate())) {
            Sheet bomSheet = bom.getSheetAt(0);
            Sheet orderSheet = salesOrder.getSheetAt(0);

            // Cột chữ để kiểu Text: gõ "05" hay "#01" không bị Excel đổi thành số.
            assertThat(bomSheet.getColumnStyle(columnOf(bomSheet, "z_mau_sac")).getDataFormatString())
                    .isEqualTo("@");
            assertThat(orderSheet.getColumnStyle(columnOf(orderSheet, "ycsx")).getDataFormatString())
                    .isEqualTo("@");
            assertThat(orderSheet.getColumnStyle(columnOf(orderSheet, "reqd_delivery_date")).getDataFormatString())
                    .isEqualTo("yyyy-mm-dd");
            assertThat(bomSheet.getColumnStyle(columnOf(bomSheet, "material")).getDataFormatString())
                    .isEqualTo("0");

            // Ô ví dụ ghi đúng kiểu: mã màu là chữ, ngày giao là ô ngày.
            Row firstExample = orderSheet.getRow(1);
            assertThat(firstExample.getCell(columnOf(orderSheet, "z_mau_sac")).getCellType())
                    .isEqualTo(CellType.STRING);
            assertThat(firstExample.getCell(columnOf(orderSheet, "reqd_delivery_date")).getLocalDateTimeCellValue()
                            .toLocalDate())
                    .isEqualTo(LocalDate.now().plusDays(7));

            // Cột nhóm thanh nan có danh sách chọn đúng 5 nhãn trình đọc chấp nhận.
            int groupColumn = columnOf(bomSheet, "slat_group");
            List<XSSFDataValidation> groupValidations = ((XSSFSheet) bomSheet).getDataValidations().stream()
                    .filter(v -> v.getRegions().getCellRangeAddress(0).getFirstColumn() == groupColumn)
                    .toList();
            assertThat(groupValidations).hasSize(1);
            assertThat(groupValidations.getFirst().getValidationConstraint().getExplicitListValues())
                    .containsExactlyElementsOf(Stream.of(SlatGroup.values()).map(SlatGroup::label).toList());
            // OOXML đặt tên ngược: showDropDown="1" là ẨN mũi tên. Khóa thẳng giá trị trong XML thay vì tin
            // vào cờ setSuppressDropDownArrow của POI (cũng ngược nghĩa và đổi theo phiên bản).
            assertThat(((XSSFSheet) bomSheet).getCTWorksheet().getDataValidations().getDataValidationList().stream()
                            .filter(v -> v.getType() == STDataValidationType.LIST)
                            .map(v -> v.getShowDropDown()))
                    .containsExactly(false);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Định dạng kỳ vọng của MỌI cột trong cả ba file mẫu, ghi độc lập với mã sinh file: cột chữ để Text
     * ("@"), cột ngày để kiểu ngày, cột số thập phân để General, còn lại (mã, số thứ tự, độ dài mm) là
     * số nguyên "0". Thêm cột mới vào trình đọc mà quên khai ở đây thì ca kiểm thử này đỏ.
     */
    private static final Set<String> TEXT_COLUMNS = Set.of("door_material_name", "z_mau_sac", "slat_material_name",
            "slat_group", "material_description", "ycsx", "customer_name", "item_description", "material_group");
    private static final Set<String> DECIMAL_COLUMNS = Set.of("dinh_muc_tb_m_per_bo_cua", "width_offset_m",
            "height_offset_m", "slat_count_slope", "slat_count_intercept", "unrestricted", "z_chieu_cao_dh",
            "z_chieu_rong_dh");
    private static final Set<String> INTEGER_COLUMNS = Set.of("material", "slat_material", "batch", "z_item",
            "sales_document", "sales_order_item", "order", "customer");

    private static String expectedFormat(String column) {
        if (TEXT_COLUMNS.contains(column)) {
            return "@";
        }
        if (DECIMAL_COLUMNS.contains(column)) {
            return "General";
        }
        if (INTEGER_COLUMNS.contains(column)) {
            return "0";
        }
        if (column.equals("reqd_delivery_date")) {
            return "yyyy-mm-dd";
        }
        throw new AssertionError("Cột chưa khai định dạng kỳ vọng trong ca kiểm thử: " + column);
    }

    @Test
    void everyColumnOfEveryTemplate_hasTheFormatOfItsType() {
        for (byte[] template : List.of(
                templateService.bomTemplate(), templateService.inventoryTemplate(), templateService.salesOrderTemplate())) {
            try (XSSFWorkbook workbook = open(template)) {
                Sheet sheet = workbook.getSheetAt(0);
                List<String> columns = header(sheet);
                for (int c = 0; c < columns.size(); c++) {
                    assertThat(sheet.getColumnStyle(c).getDataFormatString())
                            .as(columns.get(c))
                            .isEqualTo(expectedFormat(columns.get(c)));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /** Sheet hướng dẫn của cả ba file: mỗi cột của sheet dữ liệu có đúng một dòng giải thích, lưu ý số 1 là "xóa dòng ví dụ". */
    @Test
    void guideSheet_ofEveryTemplate_explainsEveryColumn_andStartsWithTheDeleteExamplesWarning() {
        for (byte[] template : List.of(
                templateService.bomTemplate(), templateService.inventoryTemplate(), templateService.salesOrderTemplate())) {
            try (XSSFWorkbook workbook = open(template)) {
                Sheet guide = workbook.getSheetAt(1);
                List<String> firstColumn = new ArrayList<>();
                String firstNote = null;
                for (Row row : guide) {
                    Cell first = row.getCell(0);
                    if (first == null) {
                        continue;
                    }
                    firstColumn.add(first.getStringCellValue());
                    if ("1.".equals(first.getStringCellValue())) {
                        firstNote = row.getCell(1).getStringCellValue();
                    }
                }
                for (String column : header(workbook.getSheetAt(0))) {
                    assertThat(firstColumn).as("dòng giải thích cột " + column).containsOnlyOnce(column);
                }
                assertThat(firstNote).contains("XÓA HẾT trước khi điền dữ liệu thật");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Test
    void guideSheet_putsTheDeleteExamplesWarningFirst_andInventoryWarnsAboutZeroOut() {
        try (XSSFWorkbook inventory = open(templateService.inventoryTemplate())) {
            List<String> texts = new ArrayList<>();
            for (Row row : inventory.getSheetAt(1)) {
                for (Cell cell : row) {
                    texts.add(cell.getStringCellValue());
                }
            }
            String joined = String.join("\n", texts);
            assertThat(joined).contains("XÓA HẾT trước khi điền dữ liệu thật");
            assertThat(joined).contains("ẢNH CHỤP TOÀN BỘ TỒN KHO");
            assertThat(joined.indexOf("XÓA HẾT")).isLessThan(joined.indexOf("ẢNH CHỤP"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Ba file mẫu nhập NGUYÊN TRẠNG theo đúng thứ tự định mức → tồn kho → đơn hàng phải đi qua cả ba
     * trình đọc, và dòng ví dụ khớp chéo đủ để tính ra một phương án cắt không thiếu vật tư — tức là
     * file mẫu "hợp lệ" theo chính các trình đọc, không theo một bản mô tả nằm ngoài chúng.
     */
    @Test
    void allThreeTemplates_importAsIsInOrder_andTheExamplesCutWithoutShortage() {
        BomImportResult bom = bomImportService.importFromExcel(upload(templateService.bomTemplate(), "bom.xlsx"));
        InventoryImportResult inventory =
                inventoryImportService.importFromExcel(upload(templateService.inventoryTemplate(), "ton-kho.xlsx"));
        SalesOrderImportResult orders = salesOrderImportService.importFromExcel(
                upload(templateService.salesOrderTemplate(), "don-hang.xlsx"));

        assertThat(bom.totalRowsImported()).isEqualTo(2);
        assertThat(inventory.totalRowsImported()).isEqualTo(2);
        assertThat(orders.totalRowsImported()).isEqualTo(2);
        assertThat(orders.skippedNonDoorRows()).isZero();

        // Thanh nan lấy nhóm từ định mức, không rơi về "Khác" như mã lạ đến từ tồn kho.
        SlatMaterial mainSlat = slatMaterialRepository.findBySlatMaterial(99100001L).orElseThrow();
        SlatMaterial rail = slatMaterialRepository.findBySlatMaterial(99100002L).orElseThrow();
        assertThat(mainSlat.getSlatGroup()).isEqualTo(SlatGroup.MAIN_SLAT);
        assertThat(rail.getSlatGroup()).isEqualTo(SlatGroup.RAIL);
        InventoryBatch mainStock = inventoryBatchRepository
                .findBySlatMaterial_IdAndDoDaiThanhMm(mainSlat.getId(), 4800)
                .orElseThrow();
        assertThat(mainStock.getSoThanh()).isEqualTo(100);

        // Mã màu đi qua cột Text nguyên vẹn; kích thước và ngày giao đúng như ô ví dụ.
        assertThat(doorProductRepository.findByMaterialAndMauSac(99000001L, "#01")).isPresent();
        SalesOrder first = salesOrderRepository.findByYcsxAndItem("YCSX-VD-01", 1).orElseThrow();
        assertThat(first.getChieuCaoDh()).isEqualByComparingTo(new BigDecimal("3.2"));
        assertThat(first.getChieuRongDh()).isEqualByComparingTo(new BigDecimal("4.75"));
        assertThat(first.getReqdDeliveryDate()).isEqualTo(LocalDate.now().plusDays(7));
        assertThat(first.getLenhSx()).isEqualTo(9900000101L);

        CuttingPlanResult result = cuttingPlanService.simulate().result();
        List<CutRecord> exampleCuts = result.cuts().stream()
                .filter(cut -> cut.pieces().stream().allMatch(piece -> "YCSX-VD-01".equals(piece.ycsx())))
                .toList();
        Map<Integer, Long> piecesByLength = exampleCuts.stream()
                .flatMap(cut -> cut.pieces().stream())
                .collect(Collectors.groupingBy(piece -> piece.cutLengthMm(), Collectors.counting()));
        // 2 bộ × 43 nan chính 4700mm, 2 bộ × 2 ray 3100mm — mỗi đoạn từ một thanh, dư 100mm bỏ đi.
        assertThat(piecesByLength).containsExactlyInAnyOrderEntriesOf(Map.of(4700, 86L, 3100, 4L));
        assertThat(exampleCuts).allSatisfy(cut -> assertThat(cut.remainderMm()).isEqualTo(100));
        assertThat(result.shortages().stream().filter(s -> "YCSX-VD-01".equals(s.demand().ycsx())))
                .isEmpty();
    }

    /**
     * Chốt chặn chống lệch: trình đọc thêm, bớt cột, hay đổi tập cột tùy chọn mà mô tả file mẫu không
     * đổi theo thì hàm kiểm từ chối — chính hàm mà hàm dựng gọi lúc khởi động, nên ứng dụng không lên được.
     */
    @Test
    void checked_rejectsATemplateThatDriftsFromItsReader() {
        List<String> reader = BomImportService.REQUIRED_COLUMNS;
        List<String> withExtra = Stream.concat(reader.stream(), Stream.of("cot_moi")).toList();
        List<String> withoutLast = reader.subList(0, reader.size() - 1);

        assertThat(ImportTemplateService.checked(ImportTemplateService.bomSpec(), reader, List.of())).isNotNull();
        for (List<String> drifted : List.of(withExtra, withoutLast)) {
            assertThatThrownBy(() -> ImportTemplateService.checked(ImportTemplateService.bomSpec(), drifted, List.of()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("lệch với trình đọc nhập");
        }
        assertThatThrownBy(() -> ImportTemplateService.checked(ImportTemplateService.bomSpec(), reader, List.of("order")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lệch với trình đọc nhập");
    }

    @Test
    void templatesWithExamplesDeleted_importAsEmptyFilesWithoutErrors() {
        BomImportResult bom =
                bomImportService.importFromExcel(upload(withoutExamples(templateService.bomTemplate()), "bom.xlsx"));
        SalesOrderImportResult orders = salesOrderImportService.importFromExcel(
                upload(withoutExamples(templateService.salesOrderTemplate()), "don-hang.xlsx"));

        assertThat(bom.totalRowsImported()).isZero();
        assertThat(orders.totalRowsImported()).isZero();
    }
}
