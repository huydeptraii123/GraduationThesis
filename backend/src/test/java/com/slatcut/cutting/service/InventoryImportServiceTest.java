package com.slatcut.cutting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.slatcut.cutting.AbstractIntegrationTest;
import com.slatcut.cutting.config.ImportValidationException;
import com.slatcut.cutting.domain.InventoryBatch;
import com.slatcut.cutting.domain.SlatGroup;
import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.dto.ImportRowError;
import com.slatcut.cutting.dto.InventoryImportResult;
import com.slatcut.cutting.repository.InventoryBatchRepository;
import com.slatcut.cutting.repository.SlatMaterialRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class InventoryImportServiceTest extends AbstractIntegrationTest {

    private static final Object[] HEADER = {"material", "material_description", "batch", "unrestricted"};

    private static final Path REAL_DATASET = Path.of("..", "dataset", "v_mchb_batch_stock.xlsx");

    @Autowired
    private InventoryImportService service;

    @Autowired
    private SlatMaterialRepository slatMaterialRepository;

    @Autowired
    private InventoryBatchRepository inventoryBatchRepository;

    /** Dựng file .xlsx trong bộ nhớ: mỗi Object[] là 1 dòng, phần tử null = ô trống. */
    private MultipartFile excel(Object[]... rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("stock");
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r);
                Object[] values = rows[r];
                for (int c = 0; c < values.length; c++) {
                    Object value = values[c];
                    if (value == null) {
                        continue;
                    }
                    Cell cell = row.createCell(c);
                    if (value instanceof Number number) {
                        cell.setCellValue(number.doubleValue());
                    } else {
                        cell.setCellValue(value.toString());
                    }
                }
            }
            workbook.write(out);
            return new MockMultipartFile(
                    "file",
                    "ton-kho.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private SlatMaterial persistMaterial(long code, String name, SlatGroup group) {
        SlatMaterial entity = new SlatMaterial();
        entity.setSlatMaterial(code);
        entity.setSlatMaterialName(name);
        entity.setSlatGroup(group);
        return slatMaterialRepository.save(entity);
    }

    private InventoryBatch persistBatch(SlatMaterial material, int lengthMm, int count) {
        InventoryBatch entity = new InventoryBatch();
        entity.setSlatMaterial(material);
        entity.setDoDaiThanhMm(lengthMm);
        entity.setSoThanh(count);
        return inventoryBatchRepository.save(entity);
    }

    private List<ImportRowError> errorsOf(MultipartFile file) {
        ImportValidationException exception =
                catchThrowableOfType(ImportValidationException.class, () -> service.importFromExcel(file));
        assertThat(exception).isNotNull();
        return exception.getErrors();
    }

    @Test
    void importFromExcel_createsMissingMaterialWithOtherGroupAndDerivesStickCount() {
        InventoryImportResult result =
                service.importFromExcel(excel(HEADER, new Object[] {74000001L, "Nan nhập mới", 2500, 15}));

        assertThat(result.totalRowsImported()).isEqualTo(1);
        assertThat(result.zeroedOutCount()).isZero();
        SlatMaterial created = slatMaterialRepository.findBySlatMaterial(74000001L).orElseThrow();
        assertThat(created.getSlatMaterialName()).isEqualTo("Nan nhập mới");
        assertThat(created.getSlatGroup()).isEqualTo(SlatGroup.OTHER);
        assertThat(inventoryBatchRepository
                        .findBySlatMaterial_IdAndDoDaiThanhMm(created.getId(), 2500)
                        .orElseThrow()
                        .getSoThanh())
                .isEqualTo(6);
    }

    @Test
    void importFromExcel_neverOverwritesNameOrGroupOfExistingMaterial() {
        persistMaterial(74000002L, "Tên gốc trong hệ thống", SlatGroup.MAIN_SLAT);

        service.importFromExcel(excel(HEADER, new Object[] {74000002L, "Tên khác trong file", 2500, 15}));

        SlatMaterial unchanged = slatMaterialRepository.findBySlatMaterial(74000002L).orElseThrow();
        assertThat(unchanged.getSlatMaterialName()).isEqualTo("Tên gốc trong hệ thống");
        assertThat(unchanged.getSlatGroup()).isEqualTo(SlatGroup.MAIN_SLAT);
    }

    @Test
    void importFromExcel_updatesExistingBatchInPlaceKeepingItsId() {
        SlatMaterial material = persistMaterial(74000003L, "Nan có sẵn lô", SlatGroup.MAIN_SLAT);
        InventoryBatch existing = persistBatch(material, 2500, 2);

        service.importFromExcel(excel(HEADER, new Object[] {74000003L, "Nan có sẵn lô", 2500, 15}));

        InventoryBatch updated = inventoryBatchRepository.findById(existing.getId()).orElseThrow();
        assertThat(updated.getSoThanh()).isEqualTo(6);
    }

    @Test
    void importFromExcel_zeroesOutBatchesMissingFromTheFile() {
        SlatMaterial material = persistMaterial(74000004L, "Nan hai độ dài", SlatGroup.MAIN_SLAT);
        persistBatch(material, 2500, 4);
        InventoryBatch disappearing = persistBatch(material, 3000, 7);

        InventoryImportResult result =
                service.importFromExcel(excel(HEADER, new Object[] {74000004L, "Nan hai độ dài", 2500, 15}));

        assertThat(result.zeroedOutCount()).isEqualTo(1);
        assertThat(inventoryBatchRepository
                        .findById(disappearing.getId())
                        .orElseThrow()
                        .getSoThanh())
                .isZero();
    }

    @Test
    void importFromExcel_doesNotCountBatchesAlreadyAtZero() {
        SlatMaterial material = persistMaterial(74000005L, "Nan đã hết", SlatGroup.MAIN_SLAT);
        persistBatch(material, 3000, 0);

        InventoryImportResult result =
                service.importFromExcel(excel(HEADER, new Object[] {74000005L, "Nan đã hết", 2500, 15}));

        assertThat(result.zeroedOutCount()).isZero();
    }

    @Test
    void importFromExcel_withHeaderOnlyZeroesOutEveryExistingBatch() {
        SlatMaterial material = persistMaterial(74000006L, "Nan bị quét sạch", SlatGroup.MAIN_SLAT);
        InventoryBatch batch = persistBatch(material, 2500, 9);

        InventoryImportResult result = service.importFromExcel(excel(HEADER));

        assertThat(result.totalRowsImported()).isZero();
        assertThat(result.zeroedOutCount()).isEqualTo(1);
        assertThat(inventoryBatchRepository.findById(batch.getId()).orElseThrow().getSoThanh())
                .isZero();
    }

    @Test
    void importFromExcel_skipsBlankRowsWithoutCountingThem() {
        InventoryImportResult result = service.importFromExcel(excel(
                HEADER,
                new Object[] {74000007L, "Nan dòng 2", 2500, 15},
                new Object[] {},
                new Object[] {74000008L, "Nan dòng 4", 2500, 15}));

        assertThat(result.totalRowsImported()).isEqualTo(2);
    }

    @Test
    void importFromExcel_acceptsHeaderWithPaddingAndExtraColumnsInAnyOrder() {
        InventoryImportResult result = service.importFromExcel(excel(
                new Object[] {"unrestricted", "ghi_chu", " material ", "batch", "material_description"},
                new Object[] {15, "cột thừa bị bỏ qua", 74000009L, 2500, "Nan đảo cột"}));

        assertThat(result.totalRowsImported()).isEqualTo(1);
        assertThat(slatMaterialRepository.findBySlatMaterial(74000009L)).isPresent();
    }

    @Test
    void importFromExcel_acceptsZeroQuantityAsValidRow() {
        InventoryImportResult result =
                service.importFromExcel(excel(HEADER, new Object[] {74000010L, "Nan tồn 0", 2500, 0}));

        assertThat(result.totalRowsImported()).isEqualTo(1);
        SlatMaterial material = slatMaterialRepository.findBySlatMaterial(74000010L).orElseThrow();
        assertThat(inventoryBatchRepository
                        .findBySlatMaterial_IdAndDoDaiThanhMm(material.getId(), 2500)
                        .orElseThrow()
                        .getSoThanh())
                .isZero();
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
    void importFromExcel_reportsEveryMissingRequiredColumn() {
        MultipartFile file = excel(new Object[] {"material", "batch"}, new Object[] {74000011L, 2500});

        assertThat(errorsOf(file))
                .hasSize(2)
                .allSatisfy(error -> assertThat(error.rowNumber()).isEqualTo(1))
                .extracting(ImportRowError::message)
                .anySatisfy(message -> assertThat(message).contains("material_description"))
                .anySatisfy(message -> assertThat(message).contains("unrestricted"));
    }

    @Test
    void importFromExcel_throwsWhenFileIsNotAnExcelWorkbook() {
        MultipartFile file = new MockMultipartFile(
                "file", "ton-kho.xlsx", "text/plain", "khong phai excel".getBytes(StandardCharsets.UTF_8));

        assertThat(errorsOf(file)).singleElement().satisfies(error -> assertThat(error.message())
                .contains("không đọc được"));
    }

    @Test
    void importFromExcel_reportsErrorsUsingRealExcelRowNumbers() {
        MultipartFile file = excel(
                HEADER,
                new Object[] {74000012L, "Nan hợp lệ", 2500, 15},
                new Object[] {null, "Nan thiếu mã", 2500, 15});

        assertThat(errorsOf(file)).singleElement().satisfies(error -> {
            assertThat(error.rowNumber()).isEqualTo(3);
            assertThat(error.message()).contains("Thiếu mã thanh nan");
        });
    }

    @Test
    void importFromExcel_rejectsUnparsableMaterialCode() {
        MultipartFile file = excel(HEADER, new Object[] {"ABC", "Nan mã chữ", 2500, 15});

        assertThat(errorsOf(file)).singleElement().satisfies(error -> assertThat(error.message())
                .contains("Mã thanh nan không hợp lệ"));
    }

    @Test
    void importFromExcel_rejectsBlankMaterialDescription() {
        MultipartFile file = excel(HEADER, new Object[] {74000013L, "   ", 2500, 15});

        assertThat(errorsOf(file)).singleElement().satisfies(error -> assertThat(error.message())
                .contains("Thiếu tên thanh nan"));
    }

    @Test
    void importFromExcel_rejectsNonPositiveOrFractionalLength() {
        assertThat(errorsOf(excel(HEADER, new Object[] {74000014L, "Nan dài 0", 0, 15})))
                .singleElement()
                .satisfies(error -> assertThat(error.message()).contains("số nguyên dương"));

        assertThat(errorsOf(excel(HEADER, new Object[] {74000015L, "Nan dài lẻ", 2500.5, 15})))
                .singleElement()
                .satisfies(error -> assertThat(error.message()).contains("số nguyên dương"));
    }

    @Test
    void importFromExcel_rejectsNegativeTotalMeters() {
        MultipartFile file = excel(HEADER, new Object[] {74000016L, "Nan tồn âm", 2500, -15});

        assertThat(errorsOf(file)).singleElement().satisfies(error -> assertThat(error.message())
                .contains("không được âm"));
    }

    @Test
    void importFromExcel_rejectsTotalMetersNotDivisibleByLength() {
        MultipartFile file = excel(HEADER, new Object[] {74000017L, "Nan lệch số", 2500, 16});

        assertThat(errorsOf(file)).singleElement().satisfies(error -> assertThat(error.message())
                .contains("không chia hết"));
    }

    @Test
    void importFromExcel_toleratesRoundingNoiseBelowThreshold() {
        InventoryImportResult result =
                service.importFromExcel(excel(HEADER, new Object[] {74000018L, "Nan lệch rất nhỏ", 2500, 15.00001}));

        assertThat(result.totalRowsImported()).isEqualTo(1);
        SlatMaterial material = slatMaterialRepository.findBySlatMaterial(74000018L).orElseThrow();
        assertThat(inventoryBatchRepository
                        .findBySlatMaterial_IdAndDoDaiThanhMm(material.getId(), 2500)
                        .orElseThrow()
                        .getSoThanh())
                .isEqualTo(6);
    }

    @Test
    void importFromExcel_rejectsDuplicateMaterialAndLengthWithinSameFile() {
        MultipartFile file = excel(
                HEADER,
                new Object[] {74000019L, "Nan trùng", 2500, 15},
                new Object[] {74000019L, "Nan trùng", 2500, 20});

        assertThat(errorsOf(file)).singleElement().satisfies(error -> {
            assertThat(error.rowNumber()).isEqualTo(3);
            assertThat(error.message()).contains("Trùng tổ hợp");
        });
    }

    @Test
    void importFromExcel_collectsEveryErrorOfEveryRowInsteadOfFailingFast() {
        MultipartFile file = excel(
                HEADER,
                new Object[] {null, null, 2500, 15},
                new Object[] {74000020L, "Nan lỗi độ dài", -1, 15});

        List<ImportRowError> errors = errorsOf(file);

        assertThat(errors).hasSize(3);
        assertThat(errors).filteredOn(error -> error.rowNumber() == 2).hasSize(2);
        assertThat(errors).filteredOn(error -> error.rowNumber() == 3).hasSize(1);
    }

    @Test
    void importFromExcel_writesNothingWhenAnyRowIsInvalid() {
        SlatMaterial material = persistMaterial(74000021L, "Nan không được đụng", SlatGroup.MAIN_SLAT);
        InventoryBatch batch = persistBatch(material, 2500, 4);
        MultipartFile file = excel(
                HEADER,
                new Object[] {74000021L, "Nan không được đụng", 2500, 25},
                new Object[] {74000022L, "Nan lỗi", 2500, 16});

        assertThatThrownBy(() -> service.importFromExcel(file)).isInstanceOf(ImportValidationException.class);

        assertThat(inventoryBatchRepository.findById(batch.getId()).orElseThrow().getSoThanh())
                .isEqualTo(4);
        assertThat(slatMaterialRepository.findBySlatMaterial(74000022L)).isEmpty();
    }

    @Test
    void importFromExcel_importsTheRealInventoryFileConsistently() throws IOException {
        Assumptions.assumeTrue(Files.exists(REAL_DATASET), "Không có file tồn kho thật trên máy này");

        MultipartFile file = new MockMultipartFile(
                "file",
                REAL_DATASET.getFileName().toString(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                Files.readAllBytes(REAL_DATASET));

        InventoryImportResult result = service.importFromExcel(file);

        assertThat(result.totalRowsImported()).isPositive();
        assertThat(inventoryBatchRepository.findAll())
                .hasSize(result.totalRowsImported())
                .allSatisfy(batch -> {
                    assertThat(batch.getDoDaiThanhMm()).isPositive();
                    assertThat(batch.getSoThanh()).isNotNegative();
                });
    }
}
