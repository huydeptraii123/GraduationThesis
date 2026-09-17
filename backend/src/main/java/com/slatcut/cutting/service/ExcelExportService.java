package com.slatcut.cutting.service;

import com.slatcut.cutting.domain.RemainderType;
import com.slatcut.cutting.dto.CuttingPlanDetailItemResponse;
import com.slatcut.cutting.dto.CuttingPlanDetailResponse;
import com.slatcut.cutting.dto.CuttingPlanResponse;
import com.slatcut.cutting.dto.ShortageRecordResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * Xuất Excel cho 1 lần chạy đã lưu — đọc lại qua {@link CuttingPlanService#getById(Long)} (đã ráp
 * đầy đủ details/shortages, chống N+1 sẵn), không tự query CSDL. "Đợt cắt" tính lại bằng
 * {@link #buildBatchNumbers(CuttingPlanResponse)} — cố ý port riêng từ thuật toán
 * {@code frontend/.../cuttingBatches.ts} (đã hỏi và chốt: chấp nhận tồn tại ở 2 nơi thay vì đụng
 * lại code 9.4 đã xong/đã test), cùng quy tắc: nhóm theo doorProductId, chunk tối đa 7 bộ/đợt lấp
 * đầy theo (reqdDeliveryDate, ycsx, item), sắp đợt theo ngày giao sớm nhất rồi số bộ trùng ngày đó.
 */
@Service
public class ExcelExportService {

    private static final int MAX_ORDERS_PER_BATCH = 7;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Map<RemainderType, String> REMAINDER_LABEL = Map.of(
            RemainderType.DISCARDED, "Bỏ",
            RemainderType.WASTE, "Lãng phí",
            RemainderType.RESTOCK, "Nhập kho");

    private final CuttingPlanService cuttingPlanService;

    public ExcelExportService(CuttingPlanService cuttingPlanService) {
        this.cuttingPlanService = cuttingPlanService;
    }

    private record OrderRow(Long salesOrderId, Long doorProductId, LocalDate reqdDeliveryDate, String ycsx, Integer item) {
    }

    private record Batch(LocalDate earliestDeliveryDate, List<OrderRow> orders) {
    }

    /** 1 dòng/phôi — đúng docs/requirements-functional.md dòng 15 (mức chi tiết nhất, cho xưởng cắt). */
    public byte[] exportCuttingPlan(Long id) {
        CuttingPlanResponse plan = cuttingPlanService.getById(id);
        Map<Long, Integer> batchNumberByOrderId = buildBatchNumbers(plan);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Kết quả cắt");
            CellStyle headerStyle = headerStyle(workbook);
            String[] headers = {
                "Đợt cắt", "Lệnh SX gốc", "Bộ cửa", "Khách hàng", "Kích thước (H×R, m)",
                "Mã vật tư", "Mô tả vật tư", "Độ dài phôi (m)", "SL phôi", "Mã Pattern",
                "SL đoạn (đơn gốc)", "Đơn hàng ghép thêm", "Phần dư (m)", "Loại phần dư"
            };
            writeHeader(sheet, headerStyle, headers);

            int rowIndex = 1;
            for (CuttingPlanDetailResponse detail : plan.details()) {
                CuttingPlanDetailItemResponse original = detail.items().stream()
                        .filter(CuttingPlanDetailItemResponse::originalOrder)
                        .findFirst()
                        .orElse(detail.items().get(0));
                String mergedOrders = detail.items().stream()
                        .filter(item -> !item.equals(original))
                        .map(item -> "%s/%d (%d đoạn)".formatted(item.ycsx(), item.item(), item.cutQuantity()))
                        .collect(Collectors.joining(", "));

                Row row = sheet.createRow(rowIndex++);
                int col = 0;
                setCell(row, col++, batchNumberByOrderId.get(original.salesOrderId()));
                setCell(row, col++, original.ycsx());
                setCell(row, col++, original.item());
                setCell(row, col++, original.customerName());
                setCell(row, col++, "%.3f × %.3f".formatted(original.chieuCaoDh(), original.chieuRongDh()));
                setCell(row, col++, detail.slatMaterialCode());
                setCell(row, col++, detail.slatMaterialName());
                setCell(row, col++, detail.sourceLengthMm() / 1000.0);
                setCell(row, col++, detail.stickCount());
                setCell(row, col++, detail.patternCode());
                setCell(row, col++, original.cutQuantity());
                setCell(row, col++, mergedOrders);
                setCell(row, col++, detail.remainderMm() / 1000.0);
                setCell(row, col, REMAINDER_LABEL.get(detail.remainderType()));
            }

            autoSizeColumns(sheet, headers.length);
            return toBytes(workbook);
        } catch (IOException e) {
            throw new UncheckedIOException("Không tạo được file Excel kết quả cắt", e);
        }
    }

    /**
     * 2 sheet — đúng docs/requirements-functional.md dòng 17. Không cần "đợt cắt"/chi tiết cách cắt
     * (docs/sequence-diagrams.md dòng 143: chỉ cần ShortageRecord kèm ngày giao của đơn).
     */
    public byte[] exportShortageReport(Long id) {
        CuttingPlanResponse plan = cuttingPlanService.getById(id);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = headerStyle(workbook);
            writeSummarySheet(workbook, headerStyle, plan.shortages());
            writeDetailSheet(workbook, headerStyle, plan.shortages());
            return toBytes(workbook);
        } catch (IOException e) {
            throw new UncheckedIOException("Không tạo được file Excel báo cáo thiếu vật tư", e);
        }
    }

    private void writeSummarySheet(XSSFWorkbook workbook, CellStyle headerStyle, List<ShortageRecordResponse> shortages) {
        Sheet sheet = workbook.createSheet("Tổng hợp theo vật tư");
        String[] headers = {"Mã vật tư", "Mô tả vật tư", "Tổng SL đoạn thiếu", "Tổng độ dài thiếu (m)"};
        writeHeader(sheet, headerStyle, headers);

        record MaterialTotal(Long code, String name, int quantity, java.math.BigDecimal length) {
        }
        Map<Long, MaterialTotal> totals = new LinkedHashMap<>();
        for (ShortageRecordResponse shortage : shortages) {
            MaterialTotal current = totals.get(shortage.slatMaterialId());
            int quantity = shortage.missingQuantity() + (current == null ? 0 : current.quantity());
            java.math.BigDecimal length =
                    shortage.missingLengthM().add(current == null ? java.math.BigDecimal.ZERO : current.length());
            totals.put(shortage.slatMaterialId(),
                    new MaterialTotal(shortage.slatMaterialCode(), shortage.slatMaterialName(), quantity, length));
        }

        int rowIndex = 1;
        for (MaterialTotal total : totals.values()) {
            Row row = sheet.createRow(rowIndex++);
            setCell(row, 0, total.code());
            setCell(row, 1, total.name());
            setCell(row, 2, total.quantity());
            setCell(row, 3, total.length().doubleValue());
        }
        autoSizeColumns(sheet, headers.length);
    }

    private void writeDetailSheet(XSSFWorkbook workbook, CellStyle headerStyle, List<ShortageRecordResponse> shortages) {
        Sheet sheet = workbook.createSheet("Chi tiết theo đơn hàng");
        String[] headers = {
            "Lệnh SX", "Bộ cửa", "Khách hàng", "Mã vật tư", "Mô tả vật tư",
            "SL thiếu", "Độ dài thiếu (m)", "Ngày giao yêu cầu"
        };
        writeHeader(sheet, headerStyle, headers);

        int rowIndex = 1;
        for (ShortageRecordResponse shortage : shortages) {
            Row row = sheet.createRow(rowIndex++);
            setCell(row, 0, shortage.ycsx());
            setCell(row, 1, shortage.item());
            setCell(row, 2, shortage.customerName());
            setCell(row, 3, shortage.slatMaterialCode());
            setCell(row, 4, shortage.slatMaterialName());
            setCell(row, 5, shortage.missingQuantity());
            setCell(row, 6, shortage.missingLengthM().doubleValue());
            // Ngày giao thật, không quy đổi — file dùng trực tiếp lập lệnh sản xuất bù (dòng 17).
            setCell(row, 7, shortage.reqdDeliveryDate().format(DATE_FORMAT));
        }
        autoSizeColumns(sheet, headers.length);
    }

    /** Port từ cuttingBatches.ts — xem javadoc lớp. */
    private Map<Long, Integer> buildBatchNumbers(CuttingPlanResponse plan) {
        Map<Long, OrderRow> rows = new LinkedHashMap<>();
        for (CuttingPlanDetailResponse detail : plan.details()) {
            for (CuttingPlanDetailItemResponse item : detail.items()) {
                rows.putIfAbsent(item.salesOrderId(),
                        new OrderRow(item.salesOrderId(), item.doorProductId(), item.reqdDeliveryDate(), item.ycsx(), item.item()));
            }
        }
        for (ShortageRecordResponse shortage : plan.shortages()) {
            rows.putIfAbsent(shortage.salesOrderId(), new OrderRow(
                    shortage.salesOrderId(), shortage.doorProductId(), shortage.reqdDeliveryDate(), shortage.ycsx(), shortage.item()));
        }

        List<OrderRow> sorted = rows.values().stream()
                .sorted(Comparator.comparing(OrderRow::reqdDeliveryDate)
                        .thenComparing(OrderRow::ycsx)
                        .thenComparing(OrderRow::item))
                .toList();

        Map<Long, List<OrderRow>> byDoorProduct = new LinkedHashMap<>();
        for (OrderRow row : sorted) {
            byDoorProduct.computeIfAbsent(row.doorProductId(), key -> new ArrayList<>()).add(row);
        }

        List<Batch> batches = new ArrayList<>();
        for (List<OrderRow> group : byDoorProduct.values()) {
            for (int i = 0; i < group.size(); i += MAX_ORDERS_PER_BATCH) {
                List<OrderRow> chunk = group.subList(i, Math.min(i + MAX_ORDERS_PER_BATCH, group.size()));
                LocalDate earliest =
                        chunk.stream().map(OrderRow::reqdDeliveryDate).min(LocalDate::compareTo).orElseThrow();
                batches.add(new Batch(earliest, new ArrayList<>(chunk)));
            }
        }

        batches.sort((a, b) -> {
            int dateCompare = a.earliestDeliveryDate().compareTo(b.earliestDeliveryDate());
            if (dateCompare != 0) {
                return dateCompare;
            }
            long countA = a.orders().stream().filter(o -> o.reqdDeliveryDate().equals(a.earliestDeliveryDate())).count();
            long countB = b.orders().stream().filter(o -> o.reqdDeliveryDate().equals(b.earliestDeliveryDate())).count();
            return Long.compare(countB, countA);
        });

        Map<Long, Integer> batchNumberByOrderId = new HashMap<>();
        for (int i = 0; i < batches.size(); i++) {
            int batchNumber = i + 1;
            for (OrderRow order : batches.get(i).orders()) {
                batchNumberByOrderId.put(order.salesOrderId(), batchNumber);
            }
        }
        return batchNumberByOrderId;
    }

    private CellStyle headerStyle(XSSFWorkbook workbook) {
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(boldFont);
        return style;
    }

    private void writeHeader(Sheet sheet, CellStyle headerStyle, String[] headers) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    /**
     * Đặt độ rộng cột cố định (đơn vị 1/256 ký tự), KHÔNG dùng {@code Sheet.autoSizeColumn()} —
     * hàm đó cần AWT tính font metrics thật, dễ ném lỗi hoặc ra kết quả sai trên image chạy
     * production (Dockerfile dùng eclipse-temurin:21-jre, không cài fontconfig).
     */
    private void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.setColumnWidth(i, 22 * 256);
        }
    }

    private void setCell(Row row, int column, String value) {
        row.createCell(column).setCellValue(value == null ? "" : value);
    }

    private void setCell(Row row, int column, double value) {
        row.createCell(column).setCellValue(value);
    }

    private void setCell(Row row, int column, Integer value) {
        if (value != null) {
            row.createCell(column).setCellValue(value);
        }
    }

    private void setCell(Row row, int column, Long value) {
        if (value != null) {
            row.createCell(column).setCellValue(value);
        }
    }

    private byte[] toBytes(XSSFWorkbook workbook) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
