package com.slatcut.cutting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.slatcut.cutting.AbstractIntegrationTest;
import com.slatcut.cutting.config.ImportValidationException;
import com.slatcut.cutting.domain.BomItem;
import com.slatcut.cutting.domain.DoorProduct;
import com.slatcut.cutting.domain.SlatGroup;
import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.dto.BomImportResult;
import com.slatcut.cutting.dto.ImportRowError;
import com.slatcut.cutting.repository.BomItemRepository;
import com.slatcut.cutting.repository.DoorProductRepository;
import com.slatcut.cutting.repository.SlatMaterialRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
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

class BomImportServiceTest extends AbstractIntegrationTest {

    private static final Object[] HEADER = {
        "material",
        "door_material_name",
        "z_mau_sac",
        "slat_material",
        "slat_material_name",
        "slat_group",
        "dinh_muc_tb_m_per_bo_cua",
        "width_offset_m",
        "height_offset_m",
        "slat_count_slope",
        "slat_count_intercept"
    };

    private static final Path REAL_DATASET = Path.of("..", "dataset", "v_door_slats_norm.xlsx");

    @Autowired
    private BomImportService service;

    @Autowired
    private DoorProductRepository doorProductRepository;

    @Autowired
    private SlatMaterialRepository slatMaterialRepository;

    @Autowired
    private BomItemRepository bomItemRepository;

    /** Dựng file .xlsx trong bộ nhớ: mỗi Object[] là 1 dòng, phần tử null = ô trống. */
    private MultipartFile excel(Object[]... rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("bom");
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
                    "bom.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Object[] mainSlatRow(long material, String mauSac, long slatMaterial) {
        return new Object[] {
            material,
            "Cửa thép tấm liền CB trục 114",
            mauSac,
            slatMaterial,
            "Nan nhôm sơn A48iA #05",
            "Nan chính",
            4.778,
            0.035,
            null,
            0.0163,
            2.5
        };
    }

    private DoorProduct persistDoorProduct(long material, String mauSac, String name) {
        DoorProduct entity = new DoorProduct();
        entity.setMaterial(material);
        entity.setDoorMaterialName(name);
        entity.setMauSac(mauSac);
        return doorProductRepository.save(entity);
    }

    private SlatMaterial persistSlatMaterial(long code, String name, SlatGroup group) {
        SlatMaterial entity = new SlatMaterial();
        entity.setSlatMaterial(code);
        entity.setSlatMaterialName(name);
        entity.setSlatGroup(group);
        return slatMaterialRepository.save(entity);
    }

    private List<ImportRowError> errorsOf(MultipartFile file) {
        ImportValidationException exception =
                catchThrowableOfType(ImportValidationException.class, () -> service.importFromExcel(file));
        assertThat(exception).isNotNull();
        return exception.getErrors();
    }

    @Test
    void importFromExcel_createsMissingDoorProductSlatMaterialAndBomItem() {
        BomImportResult result = service.importFromExcel(excel(HEADER, mainSlatRow(21000059L, "#1", 21003867L)));

        assertThat(result.totalRowsImported()).isEqualTo(1);
        DoorProduct doorProduct =
                doorProductRepository.findByMaterialAndMauSac(21000059L, "#1").orElseThrow();
        SlatMaterial slatMaterial = slatMaterialRepository.findBySlatMaterial(21003867L).orElseThrow();
        assertThat(slatMaterial.getSlatGroup()).isEqualTo(SlatGroup.MAIN_SLAT);
        BomItem bomItem = bomItemRepository
                .findByDoorProduct_IdAndSlatMaterial_Id(doorProduct.getId(), slatMaterial.getId())
                .orElseThrow();
        assertThat(bomItem.getWidthOffsetM()).isEqualByComparingTo("0.035");
        assertThat(bomItem.getHeightOffsetM()).isNull();
        assertThat(bomItem.getSlatCountSlope()).isEqualByComparingTo("0.0163");
        assertThat(bomItem.getDinhMucTbMPerBoCua()).isEqualByComparingTo("4.778");
    }

    @Test
    void importFromExcel_neverOverwritesExistingDoorProductName() {
        persistDoorProduct(21000060L, "#2", "Tên mẫu cửa gốc");

        service.importFromExcel(excel(HEADER, mainSlatRow(21000060L, "#2", 21003868L)));

        assertThat(doorProductRepository
                        .findByMaterialAndMauSac(21000060L, "#2")
                        .orElseThrow()
                        .getDoorMaterialName())
                .isEqualTo("Tên mẫu cửa gốc");
    }

    @Test
    void importFromExcel_neverOverwritesExistingSlatMaterialNameOrGroup() {
        // Đúng tình huống thật: InventoryImportService (5.2) tạo SlatMaterial với slatGroup=OTHER
        // khi gặp mã lạ trong file tồn kho, trước khi BOM import biết nhóm thật. Giữ nguyên bản ghi
        // cũ, không tự sửa lại — khớp nguyên tắc đã chốt "không bao giờ ghi đè bản ghi đã có".
        persistSlatMaterial(21003869L, "Tên thanh nan gốc", SlatGroup.OTHER);

        service.importFromExcel(excel(HEADER, mainSlatRow(21000061L, "#3", 21003869L)));

        SlatMaterial existing = slatMaterialRepository.findBySlatMaterial(21003869L).orElseThrow();
        assertThat(existing.getSlatMaterialName()).isEqualTo("Tên thanh nan gốc");
        assertThat(existing.getSlatGroup()).isEqualTo(SlatGroup.OTHER);
    }

    @Test
    void importFromExcel_upsertsExistingBomItemByDoorProductAndSlatMaterialKeepingItsId() {
        DoorProduct doorProduct = persistDoorProduct(21000062L, "#4", "Cửa cũ");
        SlatMaterial slatMaterial = persistSlatMaterial(21003870L, "Nan cũ", SlatGroup.MAIN_SLAT);
        BomItem existing = new BomItem();
        existing.setDoorProduct(doorProduct);
        existing.setSlatMaterial(slatMaterial);
        existing.setWidthOffsetM(new BigDecimal("0.010"));
        existing = bomItemRepository.save(existing);

        service.importFromExcel(excel(HEADER, mainSlatRow(21000062L, "#4", 21003870L)));

        BomItem updated = bomItemRepository.findById(existing.getId()).orElseThrow();
        assertThat(updated.getWidthOffsetM()).isEqualByComparingTo("0.035");
    }

    @Test
    void importFromExcel_rejectsUnknownSlatGroupLabel() {
        Object[] row = mainSlatRow(21000063L, "#5", 21003871L);
        row[5] = "Nhóm không rõ";

        assertThat(errorsOf(excel(HEADER, row)))
                .singleElement()
                .satisfies(error -> assertThat(error.message()).contains("slat_group"));
    }

    @Test
    void importFromExcel_rejectsDuplicateComboWithinSameFile() {
        assertThat(errorsOf(excel(
                        HEADER,
                        mainSlatRow(21000064L, "#6", 21003872L),
                        mainSlatRow(21000064L, "#6", 21003872L))))
                .singleElement()
                .satisfies(error -> assertThat(error.message()).contains("Trùng tổ hợp"));
    }

    @Test
    void importFromExcel_reportsEveryMissingRequiredColumn() {
        MultipartFile file = excel(new Object[] {"material", "door_material_name"}, new Object[] {21000065L, "X"});

        assertThat(errorsOf(file))
                .hasSize(9)
                .allSatisfy(error -> assertThat(error.rowNumber()).isEqualTo(1))
                .extracting(ImportRowError::message)
                .anySatisfy(message -> assertThat(message).contains("slat_group"))
                .anySatisfy(message -> assertThat(message).contains("slat_material_name"));
    }

    @Test
    void importFromExcel_throwsWhenHeaderRowIsMissing() {
        assertThat(errorsOf(excel()))
                .singleElement()
                .satisfies(error -> assertThat(error.message()).contains("dòng tiêu đề"));
    }

    @Test
    void importFromExcel_skipsBlankRowsWithoutError() {
        BomImportResult result = service.importFromExcel(
                excel(HEADER, mainSlatRow(21000066L, "#7", 21003873L), new Object[] {}));

        assertThat(result.totalRowsImported()).isEqualTo(1);
    }

    @Test
    void importFromExcel_importsTheRealBomFileConsistently() throws IOException {
        Assumptions.assumeTrue(Files.exists(REAL_DATASET), "Không có file BOM thật trên máy này");

        MultipartFile file = new MockMultipartFile(
                "file",
                REAL_DATASET.getFileName().toString(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                Files.readAllBytes(REAL_DATASET));

        BomImportResult result = service.importFromExcel(file);

        assertThat(result.totalRowsImported()).isGreaterThan(0);
    }
}
