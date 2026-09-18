package com.slatcut.cutting.dto;

import com.slatcut.cutting.domain.SlatGroup;
import java.math.BigDecimal;

/** Tổng độ dài phế (bỏ + lãng phí) của 1 nhóm thanh nan, cộng dồn qua mọi lần chạy. */
public record SlatGroupWasteResponse(SlatGroup slatGroup, BigDecimal totalM) {
}
