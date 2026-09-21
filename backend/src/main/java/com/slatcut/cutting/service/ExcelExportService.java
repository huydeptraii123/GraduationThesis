package com.slatcut.cutting.service;

import com.slatcut.cutting.dto.CuttingPlanDemandView;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * Xuất Excel mức chi tiết theo đơn hàng — 1 dòng cho mỗi {@link CuttingPlanDemandView}, tức mỗi
 * loại vật tư của mỗi bộ cửa.
 *
 * <p>Bộ cột giữ <b>đúng tên và đúng thứ tự</b> khuôn mẫu doanh nghiệp đang dùng, kể cả chỗ lẫn
 * tiếng Việt với tiếng Anh và ba cột không có nguồn dữ liệu nên để trống: file sinh ra phải thay
 * thế trực tiếp được file họ tổng hợp thủ công, và các công cụ báo cáo sẵn có đọc file theo tên
 * cột. Đổi tên cột cho "nhất quán" là làm hỏng chúng (docs/requirements-functional.md).
 *
 * <p>Lớp này không tự đọc cơ sở dữ liệu. Nó nhận sẵn danh sách dòng báo cáo do
 * {@link CuttingPlanReportService} dựng, nên cùng một bộ xuất phục vụ được cả phương án đã duyệt
 * lẫn một lần tính chưa hề được lưu — thứ không có id để mà đọc lại.
 */
@Service
public class ExcelExportService {

    /** Tên sheet của khuôn mẫu doanh nghiệp — công cụ báo cáo của họ trỏ thẳng vào tên này. */
    private static final String SHEET_NAME = "Export";

    private static final String DATE_FORMAT = "dd/MM/yyyy";
    private static final String QUANTITY_FORMAT = "#,0";
    private static final String INTEGER_FORMAT = "0";

    private static final int COLUMN_WIDTH = 22 * 256;

    /**
     * Hai cột chỉ tồn tại trên hệ thống quản trị nguồn của doanh nghiệp — tồn kho ở một kho vật tư
     * khác, và các lệnh sản xuất thanh nan đang mở chưa nhập kho. Cột vẫn giữ để khuôn dạng không
     * lệch, nhưng không suy đoán giá trị.
     *
     * <p>Nhóm dữ liệu ngoài phạm vi thứ ba — số thanh đang bị giữ theo phiếu dự trữ — không có cột
     * riêng: ở khuôn mẫu doanh nghiệp nó là một biến thể của cột trạng thái đáp ứng, nên hệ thống
     * đơn giản là không bao giờ sinh ra biến thể đó.
     */
    private static final String OUT_OF_SCOPE = null;

    /** Đúng 21 cột của khuôn mẫu, đúng thứ tự. Thứ tự khai báo ở đây CHÍNH LÀ thứ tự cột trong file. */
    private static final List<Column> CUTTING_PLAN_COLUMNS = List.of(
            Column.integer("global_seq", null),
            Column.integer("TT ưu tiên", CuttingPlanDemandView::priorityRank),
            Column.text("ycsx", CuttingPlanDemandView::ycsx),
            Column.text("lenh_sx", view -> asText(view.lenhSx())),
            Column.text("so_number", view -> asText(view.soNumber())),
            Column.text("customer_name", CuttingPlanDemandView::customerName),
            Column.text("component_material_description", CuttingPlanDemandView::slatMaterialName),
            Column.text("material_group", CuttingPlanDemandView::materialGroup),
            Column.number("wsx", CuttingPlanDemandView::wsxM),
            Column.number("cut", view -> metres(view.cutLengthMm())),
            Column.quantity("SL thanh cần", CuttingPlanDemandView::quantityNeeded),
            Column.quantity("SL thanh thiếu", CuttingPlanDemandView::quantityMissing),
            Column.text("trang_thai_dap_ung", CuttingPlanDemandView::statusText),
            Column.text("chi_tiet_lo_su_dung", CuttingPlanDemandView::cutDetailText),
            Column.date("delivery_date", CuttingPlanDemandView::reqdDeliveryDate),
            Column.text("component_group", view -> view.slatGroup() == null ? null : view.slatGroup().label()),
            Column.text("component_material", view -> asText(view.slatMaterialCode())),
            Column.text("tp_nan_hien_co_da_tru_znan", CuttingPlanDemandView::stockSnapshotText),
            Column.text("kc04_nan_hien_co", view -> OUT_OF_SCOPE),
            Column.text("co_open_hien_co", view -> OUT_OF_SCOPE),
            Column.text("trang_thai_bo_cua", CuttingPlanDemandView::doorSetStatus));

    /**
     * Báo cáo thiếu vật tư là <b>bản lọc</b> của bộ cột trên, không phải một báo cáo tính riêng —
     * nhờ vậy hai file không thể lệch số với nhau. Giữ lại các cột cần để lập lệnh sản xuất bù
     * thanh nan, bỏ phần mô tả cách cắt (dòng còn thiếu thì chưa cắt được gì để mà mô tả).
     *
     * <p>Đây là tập CÓ/KHÔNG, không phải thứ tự cột: thứ tự lấy theo khuôn mẫu 21 cột ở trên, để
     * hai file đọc giống nhau. Đảo thứ tự các dòng dưới đây sẽ không đổi được gì trong file xuất ra.
     */
    private static final Set<String> SHORTAGE_COLUMNS = Set.of(
            "TT ưu tiên",
            "ycsx",
            "lenh_sx",
            "customer_name",
            "component_material",
            "component_material_description",
            "material_group",
            "component_group",
            "cut",
            "SL thanh cần",
            "SL thanh thiếu",
            "trang_thai_dap_ung",
            "delivery_date");

    /** Phương án cắt đầy đủ — mọi nhu cầu cắt, đủ hay thiếu. */
    public byte[] exportCuttingPlan(List<CuttingPlanDemandView> rows) {
        return write(rows, CUTTING_PLAN_COLUMNS, "Không tạo được file Excel phương án cắt");
    }

    /** Chỉ những nhu cầu còn thiếu thanh — căn cứ để sản xuất bù. */
    public byte[] exportShortageReport(List<CuttingPlanDemandView> rows) {
        List<CuttingPlanDemandView> shortages =
                rows.stream().filter(view -> view.quantityMissing() > 0).toList();
        List<Column> columns = CUTTING_PLAN_COLUMNS.stream()
                .filter(column -> SHORTAGE_COLUMNS.contains(column.header()))
                .toList();
        return write(shortages, columns, "Không tạo được file Excel báo cáo thiếu vật tư");
    }

    private byte[] write(List<CuttingPlanDemandView> rows, List<Column> columns, String errorMessage) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);
            Styles styles = new Styles(workbook);
            writeHeader(sheet, styles.header, columns);

            int rowIndex = 1;
            for (CuttingPlanDemandView view : rows) {
                Row row = sheet.createRow(rowIndex);
                for (int column = 0; column < columns.size(); column++) {
                    // global_seq không có getter nào để đọc — nó là vị trí của dòng trong chính file này.
                    Object value = columns.get(column).value(view, rowIndex);
                    writeCell(row.createCell(column), value, styles.styleFor(columns.get(column).type()));
                }
                rowIndex++;
            }

            setColumnWidths(sheet, columns.size());
            return toBytes(workbook);
        } catch (IOException e) {
            throw new UncheckedIOException(errorMessage, e);
        }
    }

    private void writeHeader(Sheet sheet, CellStyle headerStyle, List<Column> columns) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < columns.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns.get(i).header());
            cell.setCellStyle(headerStyle);
        }
    }

    private void writeCell(Cell cell, Object value, CellStyle style) {
        if (style != null) {
            cell.setCellStyle(style);
        }
        switch (value) {
            case null -> cell.setBlank();
            case String text -> cell.setCellValue(text);
            case BigDecimal number -> cell.setCellValue(number.doubleValue());
            case Number number -> cell.setCellValue(number.doubleValue());
            case java.time.LocalDate date -> cell.setCellValue(date);
            default -> cell.setCellValue(value.toString());
        }
    }

    /**
     * Độ rộng cột cố định (đơn vị 1/256 ký tự). KHÔNG dùng {@code Sheet.autoSizeColumn()} — hàm đó
     * cần AWT tính font metrics thật, dễ ném lỗi hoặc ra kết quả sai trên image chạy production
     * (Dockerfile dùng eclipse-temurin:21-jre, không cài fontconfig).
     */
    private void setColumnWidths(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.setColumnWidth(i, COLUMN_WIDTH);
        }
    }

    private byte[] toBytes(XSSFWorkbook workbook) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Mã lệnh sản xuất, số chứng từ bán hàng và mã vật tư ghi thành CHỮ dù nội dung toàn chữ số —
     * khuôn mẫu doanh nghiệp lưu chúng như vậy và công cụ báo cáo của họ nối bảng theo ba cột này.
     * Ghi thành số thì phép nối bên đó lệch kiểu và mọi số 0 đứng đầu biến mất.
     */
    private static String asText(Long value) {
        return value == null ? null : String.valueOf(value);
    }

    private static BigDecimal metres(int lengthMm) {
        return BigDecimal.valueOf(lengthMm).movePointLeft(3);
    }

    /** Kiểu ô quyết định định dạng số của cột — khớp đúng định dạng file mẫu. */
    private enum CellType {
        TEXT,
        NUMBER,
        QUANTITY,
        INTEGER,
        DATE
    }

    /**
     * Một cột của file: tên in ra ở dòng đầu, cách lấy giá trị từ một dòng báo cáo, và kiểu ô.
     *
     * @param reader {@code null} với cột tự sinh từ vị trí dòng (global_seq), vì không có gì để đọc
     */
    private record Column(String header, CellType type, Function<CuttingPlanDemandView, Object> reader) {

        Object value(CuttingPlanDemandView view, int rowIndex) {
            return reader == null ? rowIndex : reader.apply(view);
        }

        static Column text(String header, Function<CuttingPlanDemandView, String> reader) {
            return new Column(header, CellType.TEXT, reader::apply);
        }

        static Column number(String header, Function<CuttingPlanDemandView, BigDecimal> reader) {
            return new Column(header, CellType.NUMBER, reader::apply);
        }

        static Column quantity(String header, Function<CuttingPlanDemandView, Integer> reader) {
            return new Column(header, CellType.QUANTITY, reader::apply);
        }

        static Column integer(String header, Function<CuttingPlanDemandView, Integer> reader) {
            return new Column(header, CellType.INTEGER, reader == null ? null : reader::apply);
        }

        static Column date(String header, Function<CuttingPlanDemandView, java.time.LocalDate> reader) {
            return new Column(header, CellType.DATE, reader::apply);
        }
    }

    /** Các {@link CellStyle} dùng lại cho cả sheet — POI giới hạn số style mỗi workbook. */
    private static final class Styles {

        private final CellStyle header;
        private final CellStyle quantity;
        private final CellStyle integer;
        private final CellStyle date;

        private Styles(Workbook workbook) {
            CreationHelper helper = workbook.getCreationHelper();
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            this.header = workbook.createCellStyle();
            this.header.setFont(boldFont);
            this.quantity = format(workbook, helper, QUANTITY_FORMAT);
            this.integer = format(workbook, helper, INTEGER_FORMAT);
            this.date = format(workbook, helper, DATE_FORMAT);
        }

        private static CellStyle format(Workbook workbook, CreationHelper helper, String pattern) {
            CellStyle style = workbook.createCellStyle();
            style.setDataFormat(helper.createDataFormat().getFormat(pattern));
            return style;
        }

        private CellStyle styleFor(CellType type) {
            return switch (type) {
                case QUANTITY -> quantity;
                case INTEGER -> integer;
                case DATE -> date;
                case TEXT, NUMBER -> null;
            };
        }
    }
}
