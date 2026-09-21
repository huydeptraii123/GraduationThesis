package com.slatcut.cutting.domain;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Nhóm công năng của một loại thanh nan trong bộ cửa.
 *
 * <p>Nhãn tiếng Việt gắn liền với từng giá trị vì nó đi cả hai chiều: luồng nhập định mức đọc đúng
 * 5 chuỗi này từ file nguồn (xem docs/domain-model.md mục 3.3.2), còn file Excel gửi lại cho doanh
 * nghiệp phải in ra đúng chúng. Để nhãn ở một chỗ duy nhất thì hai chiều không thể lệch nhau —
 * chép sang lớp xuất báo cáo một bản thứ hai là cách chắc chắn để một ngày nào đó file nhập vào và
 * file xuất ra gọi cùng một nhóm bằng hai cái tên.
 */
public enum SlatGroup {
    MAIN_SLAT("Nan chính"),
    SUB_SLAT("Nan phụ"),
    BOTTOM_BAR("Thanh đáy"),
    RAIL("Ray"),
    OTHER("Khác");

    private static final Map<String, SlatGroup> BY_LABEL =
            Stream.of(values()).collect(Collectors.toMap(SlatGroup::label, Function.identity()));

    private final String label;

    SlatGroup(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt dùng trong file nguồn của doanh nghiệp và trong báo cáo xuất ra. */
    public String label() {
        return label;
    }

    /** Nhóm ứng với nhãn tiếng Việt, hoặc {@code null} nếu chuỗi không khớp nhãn nào. */
    public static SlatGroup fromLabel(String label) {
        return BY_LABEL.get(label);
    }
}
