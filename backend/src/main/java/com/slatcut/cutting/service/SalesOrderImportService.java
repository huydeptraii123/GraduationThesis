package com.slatcut.cutting.service;

import com.slatcut.cutting.config.ImportValidationException;
import com.slatcut.cutting.domain.Customer;
import com.slatcut.cutting.domain.DoorProduct;
import com.slatcut.cutting.domain.SalesOrder;
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
        for (ParsedRow row : outcome.rows()) {
            Customer customer = customersByCode.get(row.customer());
            DoorProduct doorProduct = doorProductsByKey.get(new DoorProductKey(row.material(), row.mauSac()));
            upsertSalesOrder(row, customer, doorProduct);
        }

        return new SalesOrderImportResult(outcome.rows().size(), outcome.skippedNonDoorRows());
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

                parseRow(row, col, r + 1, evaluator, rows, errors, seenYcsxItemKeys, seenSalesDocItemKeys);
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

    private Map<DoorProductKey, DoorProduct> upsertDoorProducts(List<ParsedRow> rows) {
        Map<DoorProductKey, DoorProduct> result = new HashMap<>();
        for (ParsedRow row : rows) {
            DoorProductKey key = new DoorProductKey(row.material(), row.mauSac());
            result.computeIfAbsent(key, k -> doorProductRepository
                    .findByMaterialAndMauSac(k.material(), k.mauSac())
                    .orElseGet(() -> {
                        DoorProduct entity = new DoorProduct();
                        entity.setMaterial(k.material());
                        entity.setDoorMaterialName(row.itemDescription());
                        entity.setMauSac(k.mauSac());
                        return doorProductRepository.save(entity);
                    }));
        }
        return result;
    }

    private void upsertSalesOrder(ParsedRow row, Customer customer, DoorProduct doorProduct) {
        SalesOrder entity =
                salesOrderRepository.findByYcsxAndItem(row.ycsx(), row.item()).orElseGet(SalesOrder::new);
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
            BigDecimal chieuCaoDh,
            BigDecimal chieuRongDh,
            LocalDate reqdDeliveryDate) {}

    private record YcsxItemKey(String ycsx, Integer item) {}

    private record SalesDocItemKey(Long salesDocument, Integer salesOrderItem) {}

    private record DoorProductKey(Long material, String mauSac) {}
}
