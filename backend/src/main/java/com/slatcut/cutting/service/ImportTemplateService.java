package com.slatcut.cutting.service;

import com.slatcut.cutting.domain.SlatGroup;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationConstraint.OperatorType;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * File Excel mẫu cho ba luồng nhập (định mức, tồn kho, đơn hàng): người dùng tải về, điền dữ liệu,
 * rồi nhập lại lên hệ thống.
 *
 * <p>Sheet đầu tiên ("Dữ liệu") là thứ các trình đọc nhập thật sự đọc: dòng 1 là tên cột, dữ liệu từ
 * dòng 2. Mỗi cột được mô tả ở đây (tên, ý nghĩa, kiểu), và hàm dựng đối chiếu tập cột đó với
 * {@code REQUIRED_COLUMNS} của từng trình đọc: phải phủ ĐÚNG danh sách đó. Trình đọc thêm hay đổi
 * tên một cột mà quên sửa file mẫu thì ứng dụng không khởi động được, thay vì phát cho doanh nghiệp
 * một file mẫu không nhập được. Sheet thứ hai ("Hướng dẫn") giải thích từng cột và các luật nhập; trình đọc bỏ qua nó.
 *
 * <p>Mỗi cột được định dạng sẵn theo kiểu dữ liệu, vì trình đọc đọc theo kiểu ô: cột chữ để kiểu
 * Text (Excel không tự đổi mã màu "05" thành số 5), cột ngày để kiểu ngày, cột nhóm thanh nan có
 * danh sách chọn đúng 5 nhãn mà trình đọc chấp nhận.
 *
 * <p>Dòng ví dụ dùng mã giả lập (đầu 99) và khớp chéo giữa ba file — nhập lần lượt ba file mẫu theo
 * thứ tự định mức → tồn kho → đơn hàng thì ra hai bộ cửa tính được phương án cắt. Chúng được tô màu
 * riêng và sheet hướng dẫn yêu cầu xóa trước khi nhập dữ liệu thật: với tồn kho, nhập nguyên file mẫu
 * sẽ đưa mọi lô khác về 0 thanh.
 */
@Service
public class ImportTemplateService {

    public static final String BOM_FILENAME = "mau-nhap-dinh-muc.xlsx";
    public static final String INVENTORY_FILENAME = "mau-nhap-ton-kho.xlsx";
    public static final String SALES_ORDER_FILENAME = "mau-nhap-don-hang.xlsx";

    static final String DATA_SHEET = "Dữ liệu";
    static final String GUIDE_SHEET = "Hướng dẫn";

    /** Số dòng dữ liệu được gắn ràng buộc kiểu — dư cho tệp thật lớn nhất (vài nghìn dòng). */
    static final int VALIDATED_ROWS = 10_000;

    private static final int GUIDE_COLUMNS = 5;

    /** Kiểu dữ liệu của một cột — quyết định định dạng ô, ràng buộc nhập và câu mô tả ở sheet hướng dẫn. */
    enum Kind {
        TEXT("Chữ"),
        SLAT_GROUP("Chọn 1 trong: " + String.join(", ", Stream.of(SlatGroup.values()).map(SlatGroup::label).toList())),
        CODE("Số nguyên ≥ 1"),
        DECIMAL("Số"),
        DECIMAL_POSITIVE("Số > 0"),
        DECIMAL_NON_NEGATIVE("Số ≥ 0"),
        DATE("Ngày (yyyy-mm-dd)");

        private final String description;

        Kind(String description) {
            this.description = description;
        }
    }

    /** Trạng thái bắt buộc của một cột: phải có cột lẫn có giá trị, chỉ phải có cột, hay tùy chọn hẳn. */
    enum Presence {
        REQUIRED("Bắt buộc"),
        COLUMN_ONLY("Cột bắt buộc, ô được để trống"),
        OPTIONAL("Tùy chọn");

        private final String label;

        Presence(String label) {
            this.label = label;
        }
    }

    record Column(String name, Presence presence, Kind kind, String meaning) {}

    /**
     * @param warnings lưu ý riêng của file này, in đỏ ngay sau lưu ý "xóa dòng ví dụ"
     * @param rules    lưu ý riêng còn lại, in sau các lưu ý chung
     */
    record Template(
            String title,
            List<Column> columns,
            List<Map<String, Object>> examples,
            List<String> warnings,
            List<String> rules) {}

    private static final String DELETE_EXAMPLES_WARNING = "Các dòng tô vàng ở sheet \"" + DATA_SHEET
            + "\" là DÒNG VÍ DỤ với dữ liệu giả lập — XÓA HẾT trước khi điền dữ liệu thật và nhập lên hệ thống.";

    private static final List<String> COMMON_RULES = List.of(
            "Giữ nguyên tên cột ở dòng 1: hệ thống tìm cột theo đúng tên này (thứ tự cột không quan trọng; cột "
                    + "lạ được bỏ qua). Dữ liệu bắt đầu từ dòng 2.",
            "Hệ thống chỉ đọc sheet đầu tiên. Sheet hướng dẫn này để nguyên hay xóa đều được.",
            "Chỉ cần MỘT dòng không hợp lệ là cả lượt nhập bị hủy, không dòng nào được ghi; màn hình nhập liệt "
                    + "kê số dòng và lỗi cụ thể để sửa rồi nhập lại.",
            "Khi khởi tạo dữ liệu, nhập theo thứ tự: định mức → tồn kho → đơn hàng.",
            "File tối đa 10MB, định dạng .xlsx hoặc .xls.");

    private static final List<String> SALES_ORDER_OPTIONAL =
            List.of(SalesOrderImportService.OPTIONAL_PRODUCTION_ORDER_COLUMN);

    private final Template bom;
    private final Template inventory;

    public ImportTemplateService() {
        this.bom = checked(bomSpec(), BomImportService.REQUIRED_COLUMNS, List.of());
        this.inventory = checked(inventorySpec(), InventoryImportService.REQUIRED_COLUMNS, List.of());
        // Mẫu đơn hàng dựng lại mỗi lần tải (ngày ví dụ đổi theo ngày), nhưng vẫn kiểm một lần ở đây để
        // lệch cột với trình đọc làm ứng dụng dừng ngay lúc khởi động như hai mẫu kia.
        checked(salesOrderSpec(LocalDate.now()), SalesOrderImportService.REQUIRED_COLUMNS, SALES_ORDER_OPTIONAL);
    }

    public byte[] bomTemplate() {
        return write(bom);
    }

    public byte[] inventoryTemplate() {
        return write(inventory);
    }

    /** Dựng lại mỗi lần tải: ngày giao của dòng ví dụ luôn là một tuần sau hôm nay, kể cả khi ứng dụng chạy lâu. */
    public byte[] salesOrderTemplate() {
        return write(checked(
                salesOrderSpec(LocalDate.now().plusDays(7)),
                SalesOrderImportService.REQUIRED_COLUMNS,
                SALES_ORDER_OPTIONAL));
    }

    // ------------------------------------------------------------------ mô tả từng file mẫu

    static Template bomSpec() {
        List<Column> columns = List.of(
                new Column("material", Presence.REQUIRED, Kind.CODE,
                        "Mã mẫu cửa. Đơn hàng tham chiếu định mức qua cặp (mã mẫu cửa, màu cửa)."),
                new Column("door_material_name", Presence.REQUIRED, Kind.TEXT, "Tên mẫu cửa."),
                new Column("z_mau_sac", Presence.REQUIRED, Kind.TEXT,
                        "Màu cửa, ghi đúng như cột z_mau_sac của file đơn hàng (đơn chỉ lấy định mức cùng màu)."),
                new Column("slat_material", Presence.REQUIRED, Kind.CODE,
                        "Mã thanh nan (vật tư) cần cho bộ cửa — trùng với cột material của file tồn kho."),
                new Column("slat_material_name", Presence.REQUIRED, Kind.TEXT, "Tên thanh nan."),
                new Column("slat_group", Presence.REQUIRED, Kind.SLAT_GROUP,
                        "Nhóm thanh nan, quyết định công thức cắt: Nan chính dài = rộng − width_offset_m, số nan = "
                                + "slat_count_slope × cao + slat_count_intercept; Ray dài = cao − height_offset_m, 2 "
                                + "thanh/bộ; Nan phụ và Thanh đáy dài = rộng, 1 thanh/bộ; Khác không cắt từ tồn kho."),
                new Column("dinh_muc_tb_m_per_bo_cua", Presence.COLUMN_ONLY, Kind.DECIMAL_NON_NEGATIVE,
                        "Định mức trung bình (mét/bộ cửa) — chỉ để tham khảo, không dùng khi tính phương án cắt."),
                new Column("width_offset_m", Presence.COLUMN_ONLY, Kind.DECIMAL,
                        "Độ trừ chiều rộng (mét) của Nan chính. Để trống thì hệ thống lấy rộng × 0,976."),
                new Column("height_offset_m", Presence.COLUMN_ONLY, Kind.DECIMAL,
                        "Độ trừ chiều cao (mét) của Ray. BẮT BUỘC với dòng Ray — thiếu thì bộ cửa bị chặn, không tính."),
                new Column("slat_count_slope", Presence.COLUMN_ONLY, Kind.DECIMAL,
                        "Hệ số tính số nan theo chiều cao (Nan chính)."),
                new Column("slat_count_intercept", Presence.COLUMN_ONLY, Kind.DECIMAL,
                        "Hằng số cộng thêm khi tính số nan (Nan chính)."));
        // Số ví dụ khớp chéo với hai file kia: cửa 3,2 × 4,75m → nan chính 4,75 − 0,05 = 4,7m, 12,5 × 3,2 + 3 =
        // 43 nan (cắt từ thanh 4,8m, dư 0,1m bỏ đi); ray 3,2 − 0,1 = 3,1m, 2 thanh (từ thanh 3,2m).
        List<Map<String, Object>> examples = List.of(
                row("material", 99000001L, "door_material_name", "Cửa cuốn mẫu (ví dụ)", "z_mau_sac", "#01",
                        "slat_material", 99100001L, "slat_material_name", "Nan chính mẫu (ví dụ)",
                        "slat_group", SlatGroup.MAIN_SLAT.label(), "dinh_muc_tb_m_per_bo_cua", new BigDecimal("202.1"),
                        "width_offset_m", new BigDecimal("0.05"), "slat_count_slope", new BigDecimal("12.5"),
                        "slat_count_intercept", new BigDecimal("3")),
                row("material", 99000001L, "door_material_name", "Cửa cuốn mẫu (ví dụ)", "z_mau_sac", "#01",
                        "slat_material", 99100002L, "slat_material_name", "Ray mẫu (ví dụ)",
                        "slat_group", SlatGroup.RAIL.label(), "dinh_muc_tb_m_per_bo_cua", new BigDecimal("6.2"),
                        "height_offset_m", new BigDecimal("0.1")));
        List<String> rules = List.of(
                "Mỗi dòng là một loại thanh nan của một mẫu cửa cùng màu; không trùng tổ hợp (material, z_mau_sac, "
                        + "slat_material) trong cùng file.",
                "Nhập lại tổ hợp đã có thì cập nhật dòng cũ; dòng có trên hệ thống mà vắng mặt trong file KHÔNG bị xóa.",
                "Bộ cửa bị chặn (không tính phương án cắt) khi: mẫu cửa không có dòng nào ngoài nhóm Khác; hoặc có "
                        + "dòng Ray thiếu height_offset_m; hoặc có Nan chính nhưng không dòng nào đủ cả "
                        + "slat_count_slope lẫn slat_count_intercept.",
                "Dòng Nan chính thiếu hệ số, đứng cạnh một dòng Nan chính đủ hệ số, được bỏ riêng khi tính.");
        return new Template("định mức vật tư (BOM)", columns, examples, List.of(), rules);
    }

    private static Template inventorySpec() {
        List<Column> columns = List.of(
                new Column("material", Presence.REQUIRED, Kind.CODE,
                        "Mã thanh nan — trùng với cột slat_material của file định mức."),
                new Column("material_description", Presence.REQUIRED, Kind.TEXT, "Tên thanh nan."),
                new Column("batch", Presence.REQUIRED, Kind.CODE, "Độ dài một thanh (milimet, số nguyên)."),
                new Column("unrestricted", Presence.REQUIRED, Kind.DECIMAL_NON_NEGATIVE,
                        "Tổng mét tồn của lô = số thanh × độ dài một thanh (mét). Phải chia hết cho độ dài."));
        List<Map<String, Object>> examples = List.of(
                row("material", 99100001L, "material_description", "Nan chính mẫu (ví dụ)", "batch", 4800L,
                        "unrestricted", new BigDecimal("480")),
                row("material", 99100002L, "material_description", "Ray mẫu (ví dụ)", "batch", 3200L,
                        "unrestricted", new BigDecimal("64")));
        List<String> warnings = List.of(
                "MỖI LẦN NHẬP LÀ ẢNH CHỤP TOÀN BỘ TỒN KHO: tổ hợp (mã thanh nan, độ dài) đã có trên hệ thống mà "
                        + "không có trong file sẽ bị đưa về 0 thanh. File phải chứa đủ mọi lô đang tồn.");
        List<String> rules = List.of(
                "Mỗi dòng là một lô (mã thanh nan, độ dài); không trùng tổ hợp này trong cùng file. Ví dụ: 100 "
                        + "thanh dài 4800mm thì batch = 4800, unrestricted = 480.",
                "Mã thanh nan chưa có trên hệ thống được tạo mới ở nhóm Khác — nên nhập định mức trước để thanh nan "
                        + "có đúng nhóm.");
        return new Template("tồn kho thanh nan", columns, examples, warnings, rules);
    }

    private static Template salesOrderSpec(LocalDate exampleDelivery) {
        String prefix = SalesOrderImportService.DOOR_MATERIAL_GROUP_PREFIX;
        List<Column> columns = List.of(
                new Column("ycsx", Presence.REQUIRED, Kind.TEXT, "Mã lô sản xuất (yêu cầu sản xuất)."),
                new Column("z_item", Presence.REQUIRED, Kind.CODE,
                        "Số thứ tự bộ cửa trong lô. Mỗi dòng là đúng 1 bộ cửa; (ycsx, z_item) không được trùng."),
                new Column("sales_document", Presence.REQUIRED, Kind.CODE, "Số đơn hàng."),
                new Column("sales_order_item", Presence.REQUIRED, Kind.CODE,
                        "Số dòng trong đơn hàng; (sales_document, sales_order_item) không được trùng."),
                new Column(SalesOrderImportService.OPTIONAL_PRODUCTION_ORDER_COLUMN, Presence.OPTIONAL, Kind.CODE,
                        "Lệnh sản xuất — chỉ để in trên báo cáo. Có thể bỏ cả cột."),
                new Column("customer", Presence.REQUIRED, Kind.CODE, "Mã khách hàng."),
                new Column("customer_name", Presence.REQUIRED, Kind.TEXT, "Tên khách hàng."),
                new Column("material", Presence.REQUIRED, Kind.CODE,
                        "Mã mẫu cửa — cùng với z_mau_sac phải khớp một mẫu cửa đã có định mức."),
                new Column("item_description", Presence.REQUIRED, Kind.TEXT, "Tên mẫu cửa."),
                new Column("material_group", Presence.REQUIRED, Kind.TEXT,
                        "Nhóm vật tư. Chỉ dòng bắt đầu bằng \"" + prefix + "\" là cửa và được nhập; dòng khác bị bỏ qua."),
                new Column("z_mau_sac", Presence.REQUIRED, Kind.TEXT, "Màu cửa, ghi đúng như cột z_mau_sac của định mức."),
                new Column("reqd_delivery_date", Presence.REQUIRED, Kind.DATE,
                        "Ngày giao yêu cầu — đơn giao sớm hơn được ưu tiên cắt trước."),
                new Column("z_chieu_cao_dh", Presence.REQUIRED, Kind.DECIMAL_POSITIVE, "Chiều cao cửa (mét)."),
                new Column("z_chieu_rong_dh", Presence.REQUIRED, Kind.DECIMAL_POSITIVE, "Chiều rộng cửa (mét)."));
        Map<String, Object> first = row("ycsx", "YCSX-VD-01", "z_item", 1L, "sales_document", 9900000001L,
                "sales_order_item", 10L, "order", 9900000101L, "customer", 9900001L,
                "customer_name", "Khách hàng mẫu (ví dụ)", "material", 99000001L,
                "item_description", "Cửa cuốn mẫu (ví dụ)", "material_group", prefix + "MAU", "z_mau_sac", "#01",
                "reqd_delivery_date", exampleDelivery, "z_chieu_cao_dh", new BigDecimal("3.2"),
                "z_chieu_rong_dh", new BigDecimal("4.75"));
        Map<String, Object> second = new LinkedHashMap<>(first);
        second.putAll(row("z_item", 2L, "sales_order_item", 20L));
        List<String> rules = List.of(
                "Kích thước cửa ghi bằng MÉT (3.2 nghĩa là 3,2m).",
                "Nhập lại cùng (ycsx, z_item) thì cập nhật đơn cũ; đơn đã được duyệt phương án cắt thì giữ nguyên "
                        + "và hiện cảnh báo.",
                "Dòng có material_group không bắt đầu bằng \"" + prefix + "\" (vd bộ tời, motor) được bỏ qua, "
                        + "không tính là lỗi.",
                "Đơn có cặp (material, z_mau_sac) chưa có định mức đầy đủ vẫn nhập được nhưng hiện trạng thái "
                        + "\"Đang bị chặn\" và không được tính phương án cắt.");
        return new Template("đơn hàng", columns, List.of(first, second), List.of(), rules);
    }

    private static Map<String, Object> row(Object... keyValues) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            values.put((String) keyValues[i], keyValues[i + 1]);
        }
        return values;
    }

    /**
     * Mô tả cột của file mẫu phải phủ ĐÚNG danh sách cột trình đọc yêu cầu, cộng các cột tùy chọn nó
     * biết đọc — lệch là lỗi lập trình, nên dừng ngay lúc khởi động.
     */
    static Template checked(Template template, List<String> requiredByReader, List<String> optionalByReader) {
        Set<String> names = new HashSet<>();
        for (Column column : template.columns()) {
            if (!names.add(column.name())) {
                throw new IllegalStateException("File mẫu " + template.title() + " lặp cột " + column.name());
            }
        }
        Set<String> required = new HashSet<>();
        Set<String> optional = new HashSet<>();
        for (Column column : template.columns()) {
            (column.presence() == Presence.OPTIONAL ? optional : required).add(column.name());
        }
        if (!required.equals(Set.copyOf(requiredByReader)) || !optional.equals(Set.copyOf(optionalByReader))) {
            throw new IllegalStateException("File mẫu " + template.title() + " lệch với trình đọc nhập: mẫu có "
                    + required + " + tùy chọn " + optional + ", trình đọc đòi " + requiredByReader
                    + " + tùy chọn " + optionalByReader);
        }
        for (Map<String, Object> example : template.examples()) {
            if (!names.containsAll(example.keySet())) {
                throw new IllegalStateException("Dòng ví dụ của file mẫu " + template.title() + " có cột lạ");
            }
        }
        return template;
    }

    // ------------------------------------------------------------------ ghi workbook

    private byte[] write(Template template) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            writeDataSheet(workbook.createSheet(DATA_SHEET), workbook.getCreationHelper(), styles, template);
            writeGuideSheet(workbook.createSheet(GUIDE_SHEET), styles, template);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Không tạo được file Excel mẫu " + template.title(), e);
        }
    }

    private void writeDataSheet(Sheet sheet, CreationHelper helper, Styles styles, Template template) {
        List<Column> columns = template.columns();
        Row header = sheet.createRow(0);
        Drawing<?> drawing = sheet.createDrawingPatriarch();
        DataValidationHelper validations = sheet.getDataValidationHelper();
        for (int c = 0; c < columns.size(); c++) {
            Column column = columns.get(c);
            Cell cell = header.createCell(c);
            cell.setCellValue(column.name());
            cell.setCellStyle(column.presence() == Presence.OPTIONAL ? styles.optionalHeader : styles.header);
            cell.setCellComment(comment(drawing, helper, c, column));
            // Ô người dùng gõ thêm ở các dòng dưới nhận định dạng của cột (Text, ngày, số nguyên...).
            sheet.setDefaultColumnStyle(c, styles.column.get(column.kind()));
            // Độ rộng cố định — KHÔNG autoSizeColumn(): image chạy thật không có fontconfig (xem ExcelExportService).
            sheet.setColumnWidth(c, Math.max(column.name().length() + 4, 16) * 256);
            if (column.kind() != Kind.TEXT) {
                sheet.addValidationData(validation(validations, c, column));
            }
        }
        for (int r = 0; r < template.examples().size(); r++) {
            Row row = sheet.createRow(r + 1);
            Map<String, Object> example = template.examples().get(r);
            for (int c = 0; c < columns.size(); c++) {
                Column column = columns.get(c);
                Cell cell = row.createCell(c);
                cell.setCellStyle(styles.example.get(column.kind()));
                Object value = example.get(column.name());
                switch (value) {
                    case null -> cell.setBlank();
                    case String text -> cell.setCellValue(text);
                    case BigDecimal number -> cell.setCellValue(number.doubleValue());
                    case Long number -> cell.setCellValue(number.doubleValue());
                    case LocalDate date -> cell.setCellValue(date);
                    default -> throw new IllegalStateException("Kiểu giá trị ví dụ chưa hỗ trợ: " + value.getClass());
                }
            }
        }
        sheet.createFreezePane(0, 1);
    }

    private static Comment comment(Drawing<?> drawing, CreationHelper helper, int columnIndex, Column column) {
        ClientAnchor anchor = helper.createClientAnchor();
        anchor.setCol1(columnIndex);
        anchor.setCol2(columnIndex + 4);
        anchor.setRow1(0);
        anchor.setRow2(6);
        Comment comment = drawing.createCellComment(anchor);
        comment.setString(helper.createRichTextString(
                column.presence().label + " · " + column.kind().description + "\n" + column.meaning()));
        comment.setAuthor("Hệ thống");
        return comment;
    }

    private static DataValidation validation(DataValidationHelper helper, int columnIndex, Column column) {
        DataValidationConstraint constraint = switch (column.kind()) {
            case TEXT -> throw new IllegalArgumentException("Cột chữ không gắn ràng buộc nhập");
            case SLAT_GROUP -> helper.createExplicitListConstraint(
                    Stream.of(SlatGroup.values()).map(SlatGroup::label).toArray(String[]::new));
            case CODE -> helper.createIntegerConstraint(OperatorType.GREATER_OR_EQUAL, "1", null);
            case DECIMAL -> helper.createDecimalConstraint(OperatorType.BETWEEN, "-1000000", "1000000");
            case DECIMAL_POSITIVE -> helper.createDecimalConstraint(OperatorType.GREATER_THAN, "0", null);
            case DECIMAL_NON_NEGATIVE -> helper.createDecimalConstraint(OperatorType.GREATER_OR_EQUAL, "0", null);
            case DATE -> helper.createDateConstraint(
                    OperatorType.BETWEEN, "DATE(2000,1,1)", "DATE(2100,12,31)", Styles.DATE_FORMAT);
        };
        DataValidation validation = helper.createValidation(
                constraint, new CellRangeAddressList(1, VALIDATED_ROWS, columnIndex, columnIndex));
        validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
        validation.setShowErrorBox(true);
        validation.createErrorBox("Giá trị không hợp lệ", "Cột " + column.name() + ": " + column.kind().description);
        // XSSF đảo nghĩa cờ này: true mới HIỆN mũi tên danh sách chọn.
        validation.setSuppressDropDownArrow(column.kind() == Kind.SLAT_GROUP);
        return validation;
    }

    private void writeGuideSheet(Sheet sheet, Styles styles, Template template) {
        int r = 0;
        text(sheet.createRow(r++), 0, "Hướng dẫn điền file mẫu nhập " + template.title(), styles.title);
        r++;
        Row header = sheet.createRow(r++);
        String[] headings = {"Cột", "Bắt buộc", "Ý nghĩa", "Kiểu dữ liệu", "Ví dụ"};
        for (int c = 0; c < headings.length; c++) {
            text(header, c, headings[c], styles.header);
        }
        for (Column column : template.columns()) {
            Row row = sheet.createRow(r++);
            text(row, 0, column.name(), styles.wrap);
            text(row, 1, column.presence().label, styles.wrap);
            text(row, 2, column.meaning(), styles.wrap);
            text(row, 3, column.kind().description, styles.wrap);
            // Giá trị có thật đầu tiên trong các dòng ví dụ: height_offset_m trống ở dòng Nan chính nhưng
            // có ở dòng Ray — lấy riêng dòng đầu thì hướng dẫn ghi "(để trống)" cho đúng cột bắt buộc với Ray.
            Object example = template.examples().stream()
                    .map(values -> values.get(column.name()))
                    .filter(value -> value != null)
                    .findFirst()
                    .orElse(null);
            text(row, 4, display(example), styles.wrap);
        }
        r++;
        text(sheet.createRow(r++), 0, "Lưu ý khi nhập", styles.title);
        Set<String> highlighted = new HashSet<>(template.warnings());
        highlighted.add(DELETE_EXAMPLES_WARNING);
        List<String> notes = Stream.of(
                        List.of(DELETE_EXAMPLES_WARNING), template.warnings(), COMMON_RULES, template.rules())
                .flatMap(List::stream)
                .toList();
        for (int i = 0; i < notes.size(); i++) {
            Row row = sheet.createRow(r++);
            text(row, 0, (i + 1) + ".", styles.wrap);
            text(row, 1, notes.get(i), highlighted.contains(notes.get(i)) ? styles.warning : styles.wrap);
            sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 1, GUIDE_COLUMNS - 1));
            row.setHeightInPoints(32);
        }
        int[] widths = {26, 22, 70, 30, 24};
        for (int c = 0; c < GUIDE_COLUMNS; c++) {
            sheet.setColumnWidth(c, widths[c] * 256);
        }
    }

    private static void text(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static String display(Object value) {
        return switch (value) {
            case null -> "(để trống)";
            case BigDecimal number -> number.toPlainString();
            default -> value.toString();
        };
    }

    /** Style dùng lại cho cả workbook — POI giới hạn số style mỗi workbook. */
    private static final class Styles {

        static final String DATE_FORMAT = "yyyy-mm-dd";

        private final CellStyle header;
        private final CellStyle optionalHeader;
        private final CellStyle title;
        private final CellStyle wrap;
        private final CellStyle warning;
        private final Map<Kind, CellStyle> column = new EnumMap<>(Kind.class);
        private final Map<Kind, CellStyle> example = new EnumMap<>(Kind.class);

        private Styles(XSSFWorkbook workbook) {
            Font bold = workbook.createFont();
            bold.setBold(true);
            Font boldItalic = workbook.createFont();
            boldItalic.setBold(true);
            boldItalic.setItalic(true);
            Font italic = workbook.createFont();
            italic.setItalic(true);
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 13);
            Font warningFont = workbook.createFont();
            warningFont.setBold(true);
            warningFont.setColor(IndexedColors.DARK_RED.getIndex());

            header = filled(workbook, IndexedColors.GREY_25_PERCENT);
            header.setFont(bold);
            optionalHeader = workbook.createCellStyle();
            optionalHeader.setFont(boldItalic);
            title = workbook.createCellStyle();
            title.setFont(titleFont);
            wrap = workbook.createCellStyle();
            wrap.setWrapText(true);
            wrap.setVerticalAlignment(VerticalAlignment.TOP);
            warning = workbook.createCellStyle();
            warning.cloneStyleFrom(wrap);
            warning.setFont(warningFont);

            CreationHelper helper = workbook.getCreationHelper();
            for (Kind kind : Kind.values()) {
                short format = helper.createDataFormat().getFormat(formatOf(kind));
                CellStyle plain = workbook.createCellStyle();
                plain.setDataFormat(format);
                column.put(kind, plain);
                CellStyle sample = filled(workbook, IndexedColors.LIGHT_YELLOW);
                sample.setDataFormat(format);
                sample.setFont(italic);
                example.put(kind, sample);
            }
        }

        private static String formatOf(Kind kind) {
            return switch (kind) {
                case TEXT, SLAT_GROUP -> "@";
                case CODE -> "0";
                case DECIMAL, DECIMAL_POSITIVE, DECIMAL_NON_NEGATIVE -> "General";
                case DATE -> DATE_FORMAT;
            };
        }

        private static CellStyle filled(XSSFWorkbook workbook, IndexedColors color) {
            CellStyle style = workbook.createCellStyle();
            style.setFillForegroundColor(color.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            return style;
        }
    }
}
