package com.slatcut.cutting.service;

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
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Import định mức BOM từ file Excel nguồn (view SAP "v_door_slats_norm") — KHÔNG zero-out: khác
 * tồn kho (5.2, file là snapshot toàn tập), file BOM này không chắc chắn là bản đầy đủ mọi tổ hợp
 * door+slat đang dùng nên chỉ upsert theo khóa nghiệp vụ, không bao giờ xóa/vô hiệu dòng cũ vắng
 * mặt trong lần import.
 */
@Service
public class BomImportService {

    private static final List<String> REQUIRED_COLUMNS = List.of(
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
            "slat_count_intercept");

    // Khớp đúng 5 giá trị tiếng Việt thật trong file nguồn — xem docs/domain-model.md mục 3.3.2.
    private static final Map<String, SlatGroup> SLAT_GROUP_BY_LABEL = Map.of(
            "Nan chính", SlatGroup.MAIN_SLAT,
            "Nan phụ", SlatGroup.SUB_SLAT,
            "Thanh đáy", SlatGroup.BOTTOM_BAR,
            "Ray", SlatGroup.RAIL,
            "Khác", SlatGroup.OTHER);

    private final BomItemRepository bomItemRepository;
    private final DoorProductRepository doorProductRepository;
    private final SlatMaterialRepository slatMaterialRepository;

    public BomImportService(
            BomItemRepository bomItemRepository,
            DoorProductRepository doorProductRepository,
            SlatMaterialRepository slatMaterialRepository) {
        this.bomItemRepository = bomItemRepository;
        this.doorProductRepository = doorProductRepository;
        this.slatMaterialRepository = slatMaterialRepository;
    }

    @Transactional
    public BomImportResult importFromExcel(MultipartFile file) {
        List<ParsedRow> rows = parseAndValidate(file);

        Map<DoorProductKey, DoorProduct> doorProductsByKey = upsertDoorProducts(rows);
        Map<Long, SlatMaterial> slatMaterialsByCode = upsertSlatMaterials(rows);
        for (ParsedRow row : rows) {
            DoorProduct doorProduct = doorProductsByKey.get(new DoorProductKey(row.material(), row.mauSac()));
            SlatMaterial slatMaterial = slatMaterialsByCode.get(row.slatMaterial());
            upsertBomItem(row, doorProduct, slatMaterial);
        }

        return new BomImportResult(rows.size());
    }

    private List<ParsedRow> parseAndValidate(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream();
                Workbook workbook = WorkbookFactory.create(inputStream)) {
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> col = readHeader(sheet.getRow(0));

            List<ParsedRow> rows = new ArrayList<>();
            List<ImportRowError> errors = new ArrayList<>();
            Set<RowKey> seenKeys = new HashSet<>();

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlankRow(row)) {
                    continue;
                }
                parseRow(row, col, r + 1, evaluator, rows, errors, seenKeys);
            }

            if (!errors.isEmpty()) {
                throw new ImportValidationException(errors);
            }
            return rows;
        } catch (ImportValidationException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new ImportValidationException(
                    List.of(new ImportRowError(1, "File Excel không đọc được hoặc không hợp lệ: " + e.getMessage())));
        }
    }

    private void parseRow(
            Row row,
            Map<String, Integer> col,
            int excelRowNumber,
            FormulaEvaluator evaluator,
            List<ParsedRow> rows,
            List<ImportRowError> errors,
            Set<RowKey> seenKeys) {
        List<String> rowErrors = new ArrayList<>();

        Double materialRaw = readNumeric(row, col.get("material"), evaluator);
        Long material = null;
        if (materialRaw == null) {
            rowErrors.add("Thiếu mã mẫu cửa (material)");
        } else if (materialRaw != Math.floor(materialRaw)) {
            rowErrors.add("Mã mẫu cửa (material) phải là số nguyên: " + materialRaw);
        } else {
            material = materialRaw.longValue();
        }

        String doorMaterialName = readString(row, col.get("door_material_name"), evaluator);
        if (doorMaterialName == null || doorMaterialName.isBlank()) {
            rowErrors.add("Thiếu tên mẫu cửa (door_material_name)");
        }

        String mauSac = readString(row, col.get("z_mau_sac"), evaluator);
        if (mauSac == null || mauSac.isBlank()) {
            rowErrors.add("Thiếu màu cửa (z_mau_sac)");
        }

        Double slatMaterialRaw = readNumeric(row, col.get("slat_material"), evaluator);
        Long slatMaterial = null;
        if (slatMaterialRaw == null) {
            rowErrors.add("Thiếu mã thanh nan (slat_material)");
        } else if (slatMaterialRaw != Math.floor(slatMaterialRaw)) {
            rowErrors.add("Mã thanh nan (slat_material) phải là số nguyên: " + slatMaterialRaw);
        } else {
            slatMaterial = slatMaterialRaw.longValue();
        }

        String slatMaterialName = readString(row, col.get("slat_material_name"), evaluator);
        if (slatMaterialName == null || slatMaterialName.isBlank()) {
            rowErrors.add("Thiếu tên thanh nan (slat_material_name)");
        }

        String slatGroupRaw = readString(row, col.get("slat_group"), evaluator);
        SlatGroup slatGroup = null;
        if (slatGroupRaw == null || slatGroupRaw.isBlank()) {
            rowErrors.add("Thiếu nhóm thanh nan (slat_group)");
        } else {
            slatGroup = SLAT_GROUP_BY_LABEL.get(slatGroupRaw);
            if (slatGroup == null) {
                rowErrors.add("Nhóm thanh nan (slat_group) không hợp lệ: " + slatGroupRaw);
            }
        }

        BigDecimal widthOffsetM = readDecimal(row, col.get("width_offset_m"), evaluator);
        BigDecimal heightOffsetM = readDecimal(row, col.get("height_offset_m"), evaluator);
        BigDecimal slatCountSlope = readDecimal(row, col.get("slat_count_slope"), evaluator);
        BigDecimal slatCountIntercept = readDecimal(row, col.get("slat_count_intercept"), evaluator);
        BigDecimal dinhMucTbMPerBoCua = readDecimal(row, col.get("dinh_muc_tb_m_per_bo_cua"), evaluator);

        if (material != null && mauSac != null && !mauSac.isBlank() && slatMaterial != null) {
            RowKey key = new RowKey(material, mauSac, slatMaterial);
            if (!seenKeys.add(key)) {
                rowErrors.add("Trùng tổ hợp (mẫu cửa, màu, mã thanh nan) với 1 dòng khác trong cùng file: " + material
                        + " / " + mauSac + " / " + slatMaterial);
            }
        }

        if (!rowErrors.isEmpty()) {
            rowErrors.forEach(msg -> errors.add(new ImportRowError(excelRowNumber, msg)));
            return;
        }

        rows.add(new ParsedRow(
                material,
                doorMaterialName,
                mauSac,
                slatMaterial,
                slatMaterialName,
                slatGroup,
                widthOffsetM,
                heightOffsetM,
                slatCountSlope,
                slatCountIntercept,
                dinhMucTbMPerBoCua));
    }

    private Map<DoorProductKey, DoorProduct> upsertDoorProducts(List<ParsedRow> rows) {
        Map<DoorProductKey, DoorProduct> result = new HashMap<>();
        for (ParsedRow row : rows) {
            DoorProductKey key = new DoorProductKey(row.material(), row.mauSac());
            result.computeIfAbsent(key, k -> doorProductRepository
                    .findByMaterialAndMauSac(k.material(), k.mauSac())
                    .orElseGet(() -> {
                        DoorProduct entity = new DoorProduct();
                        entity.setMaterial(k.material());
                        entity.setDoorMaterialName(row.doorMaterialName());
                        entity.setMauSac(k.mauSac());
                        return doorProductRepository.save(entity);
                    }));
        }
        return result;
    }

    private Map<Long, SlatMaterial> upsertSlatMaterials(List<ParsedRow> rows) {
        Map<Long, SlatMaterial> result = new HashMap<>();
        for (ParsedRow row : rows) {
            result.computeIfAbsent(row.slatMaterial(), code -> slatMaterialRepository
                    .findBySlatMaterial(code)
                    .orElseGet(() -> {
                        SlatMaterial entity = new SlatMaterial();
                        entity.setSlatMaterial(code);
                        entity.setSlatMaterialName(row.slatMaterialName());
                        entity.setSlatGroup(row.slatGroup());
                        return slatMaterialRepository.save(entity);
                    }));
        }
        return result;
    }

    private void upsertBomItem(ParsedRow row, DoorProduct doorProduct, SlatMaterial slatMaterial) {
        BomItem entity = bomItemRepository
                .findByDoorProduct_IdAndSlatMaterial_Id(doorProduct.getId(), slatMaterial.getId())
                .orElseGet(BomItem::new);
        entity.setDoorProduct(doorProduct);
        entity.setSlatMaterial(slatMaterial);
        entity.setWidthOffsetM(row.widthOffsetM());
        entity.setHeightOffsetM(row.heightOffsetM());
        entity.setSlatCountSlope(row.slatCountSlope());
        entity.setSlatCountIntercept(row.slatCountIntercept());
        entity.setDinhMucTbMPerBoCua(row.dinhMucTbMPerBoCua());
        bomItemRepository.save(entity);
    }

    private Map<String, Integer> readHeader(Row headerRow) {
        if (headerRow == null) {
            throw new ImportValidationException(List.of(new ImportRowError(1, "File không có dòng tiêu đề")));
        }

        Map<String, Integer> index = new HashMap<>();
        for (Cell cell : headerRow) {
            if (cell.getCellType() == CellType.STRING) {
                index.put(cell.getStringCellValue().trim(), cell.getColumnIndex());
            }
        }

        List<ImportRowError> missing = REQUIRED_COLUMNS.stream()
                .filter(name -> !index.containsKey(name))
                .map(name -> new ImportRowError(1, "Thiếu cột bắt buộc: " + name))
                .toList();
        if (!missing.isEmpty()) {
            throw new ImportValidationException(missing);
        }
        return index;
    }

    private boolean isBlankRow(Row row) {
        for (Cell cell : row) {
            if (cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }

    private String readString(Row row, int colIndex, FormulaEvaluator evaluator) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case FORMULA -> stringFromValue(evaluator.evaluate(cell));
            default -> null;
        };
    }

    private Double readNumeric(Row row, int colIndex, FormulaEvaluator evaluator) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case NUMERIC -> cell.getNumericCellValue();
            case STRING -> parseDoubleOrNull(cell.getStringCellValue());
            case FORMULA -> numericFromValue(evaluator.evaluate(cell));
            default -> null;
        };
    }

    /** Cột hệ số kỹ thuật — nullable theo nhóm thanh nan (xem docs/domain-model.md 3.3.2), không
     *  báo lỗi khi trống. */
    private BigDecimal readDecimal(Row row, int colIndex, FormulaEvaluator evaluator) {
        Double value = readNumeric(row, colIndex, evaluator);
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private String stringFromValue(CellValue value) {
        return switch (value.getCellType()) {
            case STRING -> value.getStringValue().trim();
            case NUMERIC -> String.valueOf((long) value.getNumberValue());
            default -> null;
        };
    }

    private Double numericFromValue(CellValue value) {
        return value.getCellType() == CellType.NUMERIC ? value.getNumberValue() : null;
    }

    private Double parseDoubleOrNull(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record ParsedRow(
            Long material,
            String doorMaterialName,
            String mauSac,
            Long slatMaterial,
            String slatMaterialName,
            SlatGroup slatGroup,
            BigDecimal widthOffsetM,
            BigDecimal heightOffsetM,
            BigDecimal slatCountSlope,
            BigDecimal slatCountIntercept,
            BigDecimal dinhMucTbMPerBoCua) {}

    private record RowKey(Long material, String mauSac, Long slatMaterial) {}

    private record DoorProductKey(Long material, String mauSac) {}
}
