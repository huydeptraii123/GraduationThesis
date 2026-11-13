package com.slatcut.cutting.dto;

import com.slatcut.cutting.domain.SlatGroup;
import java.math.BigDecimal;

/**
 * Mức hao phí của 1 nhóm thanh nan, cộng dồn qua mọi lần chạy.
 *
 * @param totalM tổng độ dài phần dư bị bỏ đi (dưới 30cm) — tử số của tỷ lệ phế
 * @param stockUsedM tổng độ dài các thanh đã cắt của nhóm — mẫu số của tỷ lệ
 * @param wasteRatioPercent {@code totalM / stockUsedM}, con số dùng để so sánh giữa các nhóm; so
 *     bằng mét thì nhóm nào chiếm nhiều vật tư nhất cũng luôn đứng đầu bất kể cắt tốt hay xấu
 */
public record SlatGroupWasteResponse(
        SlatGroup slatGroup, BigDecimal totalM, BigDecimal stockUsedM, BigDecimal wasteRatioPercent) {
}
