package com.slatcut.cutting.service;

import com.slatcut.cutting.config.ImportValidationException;
import com.slatcut.cutting.domain.InventoryBatch;
import com.slatcut.cutting.domain.SlatGroup;
import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.dto.ImportRowError;
import com.slatcut.cutting.dto.InventoryImportResult;
import com.slatcut.cutting.repository.InventoryBatchRepository;
import com.slatcut.cutting.repository.SlatMaterialRepository;
import java.io.IOException;
import java.io.InputStream;
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

@Service
public class InventoryImportService {

    private static final List<String> REQUIRED_COLUMNS =
            List.of("material", "material_description", "batch", "unrestricted");

    private final SlatMaterialRepository slatMaterialRepository;
    private final InventoryBatchRepository inventoryBatchRepository;

    public InventoryImportService(
            SlatMaterialRepository slatMaterialRepository, InventoryBatchRepository inventoryBatchRepository) {
        this.slatMaterialRepository = slatMaterialRepository;
        this.inventoryBatchRepository = inventoryBatchRepository;
    }

    @Transactional
    public InventoryImportResult importFromExcel(MultipartFile file) {
        List<ParsedRow> rows = parseAndValidate(file);

        Map<Long, SlatMaterial> materialsByCode = upsertSlatMaterials(rows);
        Set<BatchKey> fileKeys = new HashSet<>();
        for (ParsedRow row : rows) {
            SlatMaterial material = materialsByCode.get(row.material());
            upsertInventoryBatch(row, material);
            fileKeys.add(new BatchKey(material.getId(), row.doDaiThanhMm()));
        }

        int zeroedOut = zeroOutMissingBatches(fileKeys);
        return new InventoryImportResult(rows.size(), zeroedOut);
    }

    private List<ParsedRow> parseAndValidate(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream();
                Workbook workbook = WorkbookFactory.create(inputStream)) {
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> columnIndex = readHeader(sheet.getRow(0));

            List<ParsedRow> rows = new ArrayList<>();
            List<ImportRowError> errors = new ArrayList<>();
            Set<RawKey> seenKeys = new HashSet<>();

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlankRow(row)) {
                    continue;
                }
                parseRow(row, columnIndex, r + 1, evaluator, rows, errors, seenKeys);
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
            Set<RawKey> seenKeys) {
        List<String> rowErrors = new ArrayList<>();

        String materialRaw = readString(row, col.get("material"), evaluator);
        Long material = null;
        if (materialRaw == null || materialRaw.isBlank()) {
            rowErrors.add("Thiếu mã thanh nan (material)");
        } else {
            try {
                material = Long.parseLong(materialRaw);
            } catch (NumberFormatException e) {
                rowErrors.add("Mã thanh nan không hợp lệ: " + materialRaw);
            }
        }

        String materialName = readString(row, col.get("material_description"), evaluator);
        if (materialName == null || materialName.isBlank()) {
            rowErrors.add("Thiếu tên thanh nan (material_description)");
        }

        // Cột nguồn thật (SAP) là "batch" (độ dài, mm) và "unrestricted" (tổng mét tồn) —
        // không phải "do_dai_thanh_mm"/"so_thanh" như file CSV đã qua xử lý dùng để phân tích.
        // so_thanh = unrestricted (mét) * 1000 / batch (mm), luôn là số nguyên theo dữ liệu thật.
        Double lengthRaw = readNumeric(row, col.get("batch"), evaluator);
        Integer doDaiThanhMm = null;
        if (lengthRaw == null) {
            rowErrors.add("Thiếu hoặc sai định dạng độ dài (batch)");
        } else if (lengthRaw <= 0 || lengthRaw != Math.floor(lengthRaw)) {
            rowErrors.add("Độ dài phải là số nguyên dương: " + lengthRaw);
        } else {
            doDaiThanhMm = lengthRaw.intValue();
        }

        Double totalMetersRaw = readNumeric(row, col.get("unrestricted"), evaluator);
        Integer soThanh = null;
        if (totalMetersRaw == null) {
            rowErrors.add("Thiếu hoặc sai định dạng tổng mét tồn (unrestricted)");
        } else if (totalMetersRaw < 0) {
            rowErrors.add("Tổng mét tồn không được âm: " + totalMetersRaw);
        } else if (doDaiThanhMm != null) {
            double computed = totalMetersRaw * 1000 / doDaiThanhMm;
            if (Math.abs(computed - Math.round(computed)) > 0.01) {
                rowErrors.add("Tổng mét tồn (" + totalMetersRaw + ") không chia hết cho độ dài (" + doDaiThanhMm
                        + "mm), dữ liệu không nhất quán");
            } else {
                soThanh = (int) Math.round(computed);
            }
        }

        if (material != null && doDaiThanhMm != null) {
            RawKey key = new RawKey(material, doDaiThanhMm);
            if (!seenKeys.add(key)) {
                rowErrors.add("Trùng tổ hợp (mã thanh nan, độ dài) với 1 dòng khác trong cùng file: " + material + " / "
                        + doDaiThanhMm + "mm");
            }
        }

        if (!rowErrors.isEmpty()) {
            rowErrors.forEach(msg -> errors.add(new ImportRowError(excelRowNumber, msg)));
            return;
        }

        rows.add(new ParsedRow(material, materialName, doDaiThanhMm, soThanh));
    }

    private Map<Long, SlatMaterial> upsertSlatMaterials(List<ParsedRow> rows) {
        Map<Long, SlatMaterial> result = new HashMap<>();
        for (ParsedRow row : rows) {
            result.computeIfAbsent(row.material(), code -> slatMaterialRepository
                    .findBySlatMaterial(code)
                    .orElseGet(() -> {
                        SlatMaterial entity = new SlatMaterial();
                        entity.setSlatMaterial(code);
                        entity.setSlatMaterialName(row.materialName());
                        entity.setSlatGroup(SlatGroup.OTHER);
                        return slatMaterialRepository.save(entity);
                    }));
        }
        return result;
    }

    private void upsertInventoryBatch(ParsedRow row, SlatMaterial material) {
        InventoryBatch entity = inventoryBatchRepository
                .findBySlatMaterial_IdAndDoDaiThanhMm(material.getId(), row.doDaiThanhMm())
                .orElseGet(InventoryBatch::new);
        entity.setSlatMaterial(material);
        entity.setDoDaiThanhMm(row.doDaiThanhMm());
        entity.setSoThanh(row.soThanh());
        inventoryBatchRepository.save(entity);
    }

    private int zeroOutMissingBatches(Set<BatchKey> fileKeys) {
        int zeroedOut = 0;
        for (InventoryBatch batch : inventoryBatchRepository.findAll()) {
            BatchKey key = new BatchKey(batch.getSlatMaterial().getId(), batch.getDoDaiThanhMm());
            if (!fileKeys.contains(key) && batch.getSoThanh() != 0) {
                batch.setSoThanh(0);
                inventoryBatchRepository.save(batch);
                zeroedOut++;
            }
        }
        return zeroedOut;
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

    private record ParsedRow(Long material, String materialName, Integer doDaiThanhMm, Integer soThanh) {}

    private record BatchKey(Long slatMaterialId, Integer doDaiThanhMm) {}

    private record RawKey(Long material, Integer doDaiThanhMm) {}
}
