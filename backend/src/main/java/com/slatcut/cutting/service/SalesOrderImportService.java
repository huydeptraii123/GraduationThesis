package com.slatcut.cutting.service;

import com.slatcut.cutting.config.ImportValidationException;
import com.slatcut.cutting.domain.Customer;
import com.slatcut.cutting.domain.DoorProduct;
import com.slatcut.cutting.domain.SalesOrder;
import com.slatcut.cutting.dto.ApprovedOrderConflict;
import com.slatcut.cutting.dto.ImportRowError;
import com.slatcut.cutting.dto.SalesOrderImportResult;
import com.slatcut.cutting.repository.CustomerRepository;
import com.slatcut.cutting.repository.DoorProductRepository;
import com.slatcut.cutting.repository.SalesOrderRepository;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SalesOrderImportService {

    private static final String DOOR_MATERIAL_GROUP_PREFIX = "CA-";

    private static final List<String> REQUIRED_COLUMNS = List.of(
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
            "z_chieu_rong_dh");

    private final SalesOrderRepository salesOrderRepository;
    private final CustomerRepository customerRepository;
    private final DoorProductRepository doorProductRepository;

    public SalesOrderImportService(
            SalesOrderRepository salesOrderRepository,
            CustomerRepository customerRepository,
            DoorProductRepository doorProductRepository) {
        this.salesOrderRepository = salesOrderRepository;
        this.customerRepository = customerRepository;
        this.doorProductRepository = doorProductRepository;
    }

    @Transactional
    public SalesOrderImportResult importFromExcel(MultipartFile file) {
        ParseOutcome outcome = parseAndValidate(file);

        Map<Long, Customer> customersByCode = upsertCustomers(outcome.rows());
        Map<DoorProductKey, DoorProduct> doorProductsByKey = upsertDoorProducts(outcome.rows());
        List<ApprovedOrderConflict> conflicts = new ArrayList<>();
        for (ParsedRow row : outcome.rows()) {
            Customer customer = customersByCode.get(row.customer());
            DoorProduct doorProduct = doorProductsByKey.get(new DoorProductKey(row.material(), row.mauSac()));
            upsertSalesOrder(row, customer, doorProduct).ifPresent(conflicts::add);
        }

        return new SalesOrderImportResult(outcome.rows().size(), outcome.skippedNonDoorRows(), conflicts);
    }

    private ParseOutcome parseAndValidate(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream();
                Workbook workbook = WorkbookFactory.create(inputStream)) {
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Sheet sheet = workbook.getSheetAt(0);
            Map<String, Integer> col = readHeader(sheet.getRow(0));

            List<ParsedRow> rows = new ArrayList<>();
            List<ImportRowError> errors = new ArrayList<>();
            Set<YcsxItemKey> seenYcsxItemKeys = new HashSet<>();
            Set<SalesDocItemKey> seenSalesDocItemKeys = new HashSet<>();
            int skippedNonDoorRows = 0;

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlankRow(row)) {
                    continue;
                }

                // Hồ sơ nguồn trộn lẫn dòng "cửa" (material_group bắt đầu bằng "CA-") và dòng
                // vật tư khác trong cùng sheet (bộ tời/motor cuốn cửa...) — các dòng đó thừa
                // hưởng kích thước cửa của cùng lô sản xuất nên KHÔNG thể lọc bằng cột kích
                // thước. Dòng ngoài phạm vi bị bỏ qua lặng lẽ, không tính là lỗi.
                String materialGroup = readString(row, col.get("material_group"), evaluator);
                if (materialGroup == null || !materialGroup.startsWith(DOOR_MATERIAL_GROUP_PREFIX)) {
                    skippedNonDoorRows++;
                    continue;
                }

                parseRow(
                        row, col, r + 1, evaluator, materialGroup, rows, errors, seenYcsxItemKeys, seenSalesDocItemKeys);
            }

            if (!errors.isEmpty()) {
                throw new ImportValidationException(errors);
            }
            return new ParseOutcome(rows, skippedNonDoorRows);
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
            String materialGroup,
            List<ParsedRow> rows,
            List<ImportRowError> errors,
            Set<YcsxItemKey> seenYcsxItemKeys,
            Set<SalesDocItemKey> seenSalesDocItemKeys) {
        List<String> rowErrors = new ArrayList<>();

        String ycsx = readString(row, col.get("ycsx"), evaluator);
        if (ycsx == null || ycsx.isBlank()) {
            rowErrors.add("Thiếu mã lô sản xuất (ycsx)");
        }

        Double itemRaw = readNumeric(row, col.get("z_item"), evaluator);
        Integer item = null;
        if (itemRaw == null) {
            rowErrors.add("Thiếu số thứ tự bộ cửa (z_item)");
        } else if (itemRaw != Math.floor(itemRaw)) {
            rowErrors.add("Số thứ tự bộ cửa (z_item) phải là số nguyên: " + itemRaw);
        } else {
            item = itemRaw.intValue();
        }

        Double salesDocumentRaw = readNumeric(row, col.get("sales_document"), evaluator);
        Long salesDocument = null;
        if (salesDocumentRaw == null) {
            rowErrors.add("Thiếu số đơn hàng (sales_document)");
        } else if (salesDocumentRaw != Math.floor(salesDocumentRaw)) {
            rowErrors.add("Số đơn hàng (sales_document) phải là số nguyên: " + salesDocumentRaw);
        } else {
            salesDocument = salesDocumentRaw.longValue();
        }

        Double salesOrderItemRaw = readNumeric(row, col.get("sales_order_item"), evaluator);
        Integer salesOrderItem = null;
        if (salesOrderItemRaw == null) {
            rowErrors.add("Thiếu số dòng đơn hàng (sales_order_item)");
        } else if (salesOrderItemRaw != Math.floor(salesOrderItemRaw)) {
            rowErrors.add("Số dòng đơn hàng (sales_order_item) phải là số nguyên: " + salesOrderItemRaw);
        } else {
            salesOrderItem = salesOrderItemRaw.intValue();
        }

        Double customerRaw = readNumeric(row, col.get("customer"), evaluator);
        Long customer = null;
        if (customerRaw == null) {
            rowErrors.add("Thiếu mã khách hàng (customer)");
        } else if (customerRaw != Math.floor(customerRaw)) {
            rowErrors.add("Mã khách hàng (customer) phải là số nguyên: " + customerRaw);
        } else {
            customer = customerRaw.longValue();
        }

        String customerName = readString(row, col.get("customer_name"), evaluator);
        if (customerName == null || customerName.isBlank()) {
            rowErrors.add("Thiếu tên khách hàng (customer_name)");
        }

        Double materialRaw = readNumeric(row, col.get("material"), evaluator);
        Long material = null;
        if (materialRaw == null) {
            rowErrors.add("Thiếu mã mẫu cửa (material)");
        } else if (materialRaw != Math.floor(materialRaw)) {
            rowErrors.add("Mã mẫu cửa (material) phải là số nguyên: " + materialRaw);
        } else {
            material = materialRaw.longValue();
        }

        String itemDescription = readString(row, col.get("item_description"), evaluator);
        if (itemDescription == null || itemDescription.isBlank()) {
            rowErrors.add("Thiếu tên mẫu cửa (item_description)");
        }

        String mauSac = readString(row, col.get("z_mau_sac"), evaluator);
        if (mauSac == null || mauSac.isBlank()) {
            rowErrors.add("Thiếu màu cửa (z_mau_sac)");
        }

        Double chieuCaoRaw = readNumeric(row, col.get("z_chieu_cao_dh"), evaluator);
        BigDecimal chieuCaoDh = null;
        if (chieuCaoRaw == null) {
            rowErrors.add("Thiếu chiều cao cửa (z_chieu_cao_dh)");
        } else if (chieuCaoRaw <= 0) {
            rowErrors.add("Chiều cao cửa (z_chieu_cao_dh) phải lớn hơn 0: " + chieuCaoRaw);
        } else {
            chieuCaoDh = BigDecimal.valueOf(chieuCaoRaw);
        }

        Double chieuRongRaw = readNumeric(row, col.get("z_chieu_rong_dh"), evaluator);
        BigDecimal chieuRongDh = null;
        if (chieuRongRaw == null) {
            rowErrors.add("Thiếu chiều rộng cửa (z_chieu_rong_dh)");
        } else if (chieuRongRaw <= 0) {
            rowErrors.add("Chiều rộng cửa (z_chieu_rong_dh) phải lớn hơn 0: " + chieuRongRaw);
        } else {
            chieuRongDh = BigDecimal.valueOf(chieuRongRaw);
        }

        LocalDate reqdDeliveryDate = readDate(row, col.get("reqd_delivery_date"), evaluator);
        if (reqdDeliveryDate == null) {
            rowErrors.add("Thiếu hoặc sai định dạng ngày giao yêu cầu (reqd_delivery_date)");
        }

        if (ycsx != null && !ycsx.isBlank() && item != null) {
            YcsxItemKey key = new YcsxItemKey(ycsx, item);
            if (!seenYcsxItemKeys.add(key)) {
                rowErrors.add("Trùng tổ hợp (ycsx, z_item) với 1 dòng khác trong cùng file: " + ycsx + " / " + item);
            }
        }
        if (salesDocument != null && salesOrderItem != null) {
            SalesDocItemKey key = new SalesDocItemKey(salesDocument, salesOrderItem);
            if (!seenSalesDocItemKeys.add(key)) {
                rowErrors.add("Trùng tổ hợp (sales_document, sales_order_item) với 1 dòng khác trong cùng file: "
                        + salesDocument + " / " + salesOrderItem);
            }
        }

        if (!rowErrors.isEmpty()) {
            rowErrors.forEach(msg -> errors.add(new ImportRowError(excelRowNumber, msg)));
            return;
        }

        rows.add(new ParsedRow(
                ycsx,
                item,
                salesDocument,
                salesOrderItem,
                customer,
                customerName,
                material,
                itemDescription,
                mauSac,
                materialGroup,
                chieuCaoDh,
                chieuRongDh,
                reqdDeliveryDate));
    }

    private Map<Long, Customer> upsertCustomers(List<ParsedRow> rows) {
        Map<Long, Customer> result = new HashMap<>();
        for (ParsedRow row : rows) {
            result.computeIfAbsent(row.customer(), code -> customerRepository
                    .findByCustomer(code)
                    .orElseGet(() -> {
                        Customer entity = new Customer();
                        entity.setCustomer(code);
                        entity.setCustomerName(row.customerName());
                        return customerRepository.save(entity);
                    }));
        }
        return result;
    }

    /**
     * Mẫu cửa mới thì tạo, mẫu cửa đã có thì dùng lại — và nhân thể điền model cửa nếu đang rỗng.
     *
     * <p>Điền-khi-rỗng KHÔNG phải là ghi đè: mẫu cửa được tạo ra từ cả luồng nhập định mức, mà hồ sơ
     * định mức không có cột model, nên những mẫu cửa biết tới qua đường đó vốn bỏ trống ô này. Lượt
     * nhập đơn hàng là nơi duy nhất biết model, và nó chỉ đang lấp chỗ trống. Giá trị đã có thì giữ
     * nguyên, đúng nguyên tắc chung của mọi luồng nhập là không phá bản ghi sẵn có
     * (docs/domain-model.md).
     */
    private Map<DoorProductKey, DoorProduct> upsertDoorProducts(List<ParsedRow> rows) {
        Map<DoorProductKey, DoorProduct> result = new HashMap<>();
        for (ParsedRow row : rows) {
            DoorProductKey key = new DoorProductKey(row.material(), row.mauSac());
            result.computeIfAbsent(key, k -> doorProductRepository
                    .findByMaterialAndMauSac(k.material(), k.mauSac())
                    .map(existing -> fillMissingMaterialGroup(existing, row.materialGroup()))
                    .orElseGet(() -> {
                        DoorProduct entity = new DoorProduct();
                        entity.setMaterial(k.material());
                        entity.setDoorMaterialName(row.itemDescription());
                        entity.setMauSac(k.mauSac());
                        entity.setMaterialGroup(row.materialGroup());
                        return doorProductRepository.save(entity);
                    }));
        }
        return result;
    }

    private DoorProduct fillMissingMaterialGroup(DoorProduct entity, String materialGroup) {
        if (entity.getMaterialGroup() != null || materialGroup == null) {
            return entity;
        }
        entity.setMaterialGroup(materialGroup);
        return doorProductRepository.save(entity);
    }

    /**
     * Upsert 1 bộ cửa theo khóa nghiệp vụ (ycsx, z_item) — trừ khi bộ cửa đó đã thuộc một phương án
     * cắt được duyệt, khi đó bản ghi cũ được giữ nguyên.
     *
     * <p>Lý do không ghi đè: nan của bộ cửa đã cắt theo đúng kích thước đang lưu và đã ra khỏi kho.
     * Ghi đè kích thước mới chỉ làm hồ sơ lệch với vật tư thực tế mà không khiến bộ cửa được cắt
     * lại, vì trạng thái đã duyệt giữ nó ngoài mọi lần chạy sau. Thực tế kích thước cửa của khách
     * là cố định nên tình huống này gần như chỉ xảy ra khi nhân viên đo sai rồi số liệu được đính
     * chính; quyết định tạo một bộ cửa mới để cắt lại thuộc về PLANNER, không phải về lượt nhập.
     *
     * @return dòng cảnh báo khi bộ cửa đã duyệt VÀ dữ liệu nguồn khác dữ liệu đã lưu; rỗng khi
     *     không có gì bất thường — bộ cửa đã duyệt mà dữ liệu y hệt thì bỏ qua im lặng, vì file
     *     nguồn xuất lại toàn bộ tồn đọng mỗi ngày nên phần lớn dòng đã duyệt đều trùng khớp và
     *     cảnh báo cho chúng chỉ tạo nhiễu.
     */
    private Optional<ApprovedOrderConflict> upsertSalesOrder(
            ParsedRow row, Customer customer, DoorProduct doorProduct) {
        SalesOrder entity =
                salesOrderRepository.findByYcsxAndItem(row.ycsx(), row.item()).orElseGet(SalesOrder::new);
        if (entity.getApprovedPlan() != null) {
            List<String> changed = describeChanges(entity, row, customer, doorProduct);
            return changed.isEmpty()
                    ? Optional.empty()
                    : Optional.of(new ApprovedOrderConflict(
                            entity.getYcsx(), entity.getItem(), entity.getApprovedPlan().getId(), changed));
        }
        entity.setYcsx(row.ycsx());
        entity.setItem(row.item());
        entity.setSalesDocument(row.salesDocument());
        entity.setSalesOrderItem(row.salesOrderItem());
        entity.setCustomer(customer);
        entity.setDoorProduct(doorProduct);
        entity.setChieuCaoDh(row.chieuCaoDh());
        entity.setChieuRongDh(row.chieuRongDh());
        entity.setReqdDeliveryDate(row.reqdDeliveryDate());
        salesOrderRepository.save(entity);
        return Optional.empty();
    }

    /**
     * Liệt kê từng trường lệch giữa bản ghi đã lưu và dòng trong file nguồn, dạng "tên trường: cũ →
     * mới". So khớp cả trường không ảnh hưởng tới việc cắt (khách hàng, ngày giao, số chứng từ) vì
     * PLANNER cần thấy đủ mới quyết được: một bộ cửa đổi khách hàng là chuyện khác hẳn với một bộ
     * cửa đổi kích thước, dù cả hai đều không được phép ghi đè.
     *
     * <p>So sánh kích thước bằng {@code compareTo} chứ không {@code equals}: BigDecimal đọc từ
     * Excel và BigDecimal đọc từ cột DECIMAL(6,3) thường khác scale (2.5 với 2.500), equals sẽ báo
     * lệch ở mọi dòng.
     */
    private List<String> describeChanges(
            SalesOrder entity, ParsedRow row, Customer customer, DoorProduct doorProduct) {
        List<String> changed = new ArrayList<>();
        addIfChanged(changed, "chiều cao", entity.getChieuCaoDh(), row.chieuCaoDh());
        addIfChanged(changed, "chiều rộng", entity.getChieuRongDh(), row.chieuRongDh());
        if (!Objects.equals(entity.getDoorProduct().getId(), doorProduct.getId())) {
            changed.add("mẫu cửa: %d → %d".formatted(entity.getDoorProduct().getId(), doorProduct.getId()));
        }
        if (!Objects.equals(entity.getCustomer().getId(), customer.getId())) {
            changed.add("khách hàng: %d → %d".formatted(entity.getCustomer().getId(), customer.getId()));
        }
        if (!Objects.equals(entity.getReqdDeliveryDate(), row.reqdDeliveryDate())) {
            changed.add("ngày giao: %s → %s".formatted(entity.getReqdDeliveryDate(), row.reqdDeliveryDate()));
        }
        if (!Objects.equals(entity.getSalesDocument(), row.salesDocument())) {
            changed.add("số chứng từ: %s → %s".formatted(entity.getSalesDocument(), row.salesDocument()));
        }
        return changed;
    }

    private static void addIfChanged(List<String> changed, String label, BigDecimal stored, BigDecimal incoming) {
        if (stored == null ? incoming != null : incoming == null || stored.compareTo(incoming) != 0) {
            changed.add("%s: %s → %s".formatted(label, stored, incoming));
        }
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

    private LocalDate readDate(Row row, int colIndex, FormulaEvaluator evaluator) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case NUMERIC ->
                DateUtil.isCellDateFormatted(cell) ? cell.getLocalDateTimeCellValue().toLocalDate() : null;
            case STRING -> parseDateOrNull(cell.getStringCellValue());
            case FORMULA -> dateFromFormula(cell, evaluator);
            default -> null;
        };
    }

    private LocalDate dateFromFormula(Cell cell, FormulaEvaluator evaluator) {
        CellValue value = evaluator.evaluate(cell);
        if (value.getCellType() != CellType.NUMERIC || !DateUtil.isCellDateFormatted(cell)) {
            return null;
        }
        return DateUtil.getLocalDateTime(value.getNumberValue()).toLocalDate();
    }

    private LocalDate parseDateOrNull(String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
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

    private record ParseOutcome(List<ParsedRow> rows, int skippedNonDoorRows) {}

    private record ParsedRow(
            String ycsx,
            Integer item,
            Long salesDocument,
            Integer salesOrderItem,
            Long customer,
            String customerName,
            Long material,
            String itemDescription,
            String mauSac,
            String materialGroup,
            BigDecimal chieuCaoDh,
            BigDecimal chieuRongDh,
            LocalDate reqdDeliveryDate) {}

    private record YcsxItemKey(String ycsx, Integer item) {}

    private record SalesDocItemKey(Long salesDocument, Integer salesOrderItem) {}

    private record DoorProductKey(Long material, String mauSac) {}
}
