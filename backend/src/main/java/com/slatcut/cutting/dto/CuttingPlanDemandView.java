package com.slatcut.cutting.dto;

import com.slatcut.cutting.domain.SlatGroup;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Một dòng ở <b>mức chi tiết theo đơn hàng</b> (docs/requirements-functional.md Nhóm 3): một loại
 * vật tư thành phần của một bộ cửa. Đây là đơn vị dữ liệu chung của mọi đầu ra báo cáo — các khối
 * biểu đồ tổng quan, file Excel phương án cắt, và báo cáo thiếu vật tư đều gộp lên từ chính danh
 * sách này.
 *
 * <p>Dùng chung một nguồn là yêu cầu nghiệp vụ chứ không phải tiện tay: báo cáo thiếu vật tư là bản
 * lọc của mức chi tiết này, nên nếu hai bên tự tính lấy thì con số có thể lệch nhau mà không ai
 * phát hiện. Cùng lý do, danh sách này dựng được từ <b>cả</b> kết quả một lần tính còn nằm trong bộ
 * nhớ lẫn một phương án đã duyệt đọc lại từ cơ sở dữ liệu — hai đường vào, một định dạng ra.
 *
 * @param priorityRank hạng ưu tiên xử lý TRONG từng loại vật tư, đánh số từ 1 theo đúng thứ tự ưu
 *     tiên của thuật toán (ngày giao, rồi ycsx, rồi z_item). Cố ý không phải số thứ tự toàn file:
 *     thợ cắt làm việc theo từng loại thanh nan một, nên thứ hạng chỉ có nghĩa trong phạm vi đó
 * @param wsxM kích thước gốc của bộ cửa mà đoạn cắt được tính ra từ đó — chiều cao với nhóm ray,
 *     chiều rộng với các nhóm còn lại. Giữ lại bên cạnh {@code cutLengthMm} để người đọc thấy được
 *     phần hao đã trừ, thay vì phải tra ngược định mức
 * @param quantityMissing số thanh còn thiếu; bằng 0 nghĩa là đủ, bằng {@code quantityNeeded} nghĩa
 *     là thiếu toàn bộ
 * @param statusText trạng thái đáp ứng dạng câu chữ, khuôn mẫu ở docs/sequence-diagrams.md
 * @param doorSetStatus trạng thái chung của cả bộ cửa — một loại thanh thiếu là cả bộ tính thiếu,
 *     vì bộ cửa không lắp được khi còn thiếu bất kỳ thành phần nào
 * @param lenhSx lệnh sản xuất của bộ cửa — chỉ để in báo cáo cho khớp chứng từ xưởng. Rỗng với đơn
 *     tạo thủ công và với đơn đã duyệt từ trước khi hệ thống bắt đầu lưu trường này
 * @param materialGroup model cửa, trục của biểu đồ "theo model". Rỗng với mẫu cửa chỉ mới biết tới
 *     qua luồng nhập định mức, vì hồ sơ định mức không có cột này — ô trống được lấp ở lượt nhập
 *     đơn hàng kế tiếp có mặt mẫu cửa đó
 * @param cutDetailText mô tả cách cắt thực tế — mỗi nhóm phôi giống hệt nhau một mệnh đề. Rỗng khi
 *     bộ cửa thiếu toàn bộ loại thanh nan đó, vì khi ấy không có phôi nào để mô tả
 * @param stockSnapshotText ảnh chụp tồn kho của loại vật tư đó đầu lần chạy, không phải tồn kho
 *     hiện hành: tồn kho đổi hằng ngày nên đọc lại thì cùng một phương án xuất ra ở hai thời điểm
 *     cho hai con số khác nhau, không còn đối chiếu được với chứng từ đã phát hành
 */
public record CuttingPlanDemandView(
        int priorityRank,
        String ycsx,
        Integer item,
        Long lenhSx,
        Long soNumber,
        String customerName,
        LocalDate reqdDeliveryDate,
        String doorProductName,
        String materialGroup,
        Long slatMaterialCode,
        String slatMaterialName,
        SlatGroup slatGroup,
        BigDecimal wsxM,
        int cutLengthMm,
        int quantityNeeded,
        int quantityMissing,
        String statusText,
        String cutDetailText,
        String stockSnapshotText,
        String doorSetStatus) {

    /** Bộ cửa đủ mọi loại thanh nan — giữ đúng chữ của khuôn mẫu doanh nghiệp, kể cả hậu tố "TP". */
    public static final String DOOR_SET_SUFFICIENT = "Đủ nan TP";

    /** Bộ cửa thiếu ít nhất một loại thanh nan. */
    public static final String DOOR_SET_SHORT = "Thiếu nan";
}
