package com.slatcut.cutting.dto;

import com.slatcut.cutting.domain.SlatGroup;
import java.math.BigDecimal;

/**
 * Mức hao phí của 1 nhóm thanh nan, cộng dồn qua mọi lần chạy.
 *
 * @param totalM tổng độ dài phế thật (bỏ + lãng phí), KHÔNG tính phần dư nhập lại kho
 * @param stockUsedM tồn kho thực tiêu hao của nhóm — mẫu số của tỷ lệ, đã trừ phần dư nhập lại kho
 * @param wasteRatioPercent {@code totalM / stockUsedM}, con số dùng để so sánh giữa các nhóm; so
 *     bằng mét thì nhóm nào chiếm nhiều vật tư nhất cũng luôn đứng đầu bất kể cắt tốt hay xấu
 */
public record SlatGroupWasteResponse(
        SlatGroup slatGroup, BigDecimal totalM, BigDecimal stockUsedM, BigDecimal wasteRatioPercent) {
}
