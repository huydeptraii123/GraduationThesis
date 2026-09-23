package com.slatcut.cutting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 1 điểm trên biểu đồ xu hướng phế: một bộ cửa đã được cắt, đặt tại ngày giao yêu cầu của nó.
 *
 * <p>Trục thời gian ở đây là {@code reqdDeliveryDate} chứ không phải thời điểm chạy thuật toán —
 * mốc thời gian có ý nghĩa nghiệp vụ là hạn giao của bộ cửa, còn thời điểm bấm nút chạy chỉ phản
 * ánh thao tác của người dùng. Bộ cửa không mang giờ, chỉ có ngày.
 *
 * @param wasteM phần phế quy cho bộ cửa này; phôi bị nhiều bộ cửa dùng chung thì chia theo tỷ lệ
 *     độ dài mỗi bên đã cắt, nên cộng mọi bộ cửa lại vẫn đúng bằng tổng phế của phương án
 */
public record OrderWastePointResponse(
        Long salesOrderId,
        String ycsx,
        Integer item,
        LocalDate reqdDeliveryDate,
        BigDecimal wasteM,
        BigDecimal stockUsedM,
        BigDecimal wasteRatioPercent) {
}
