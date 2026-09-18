package com.slatcut.cutting.dto;

import com.slatcut.cutting.domain.RemainderType;
import java.math.BigDecimal;

/** Tổng độ dài phần dư theo 1 trong 3 ngưỡng nghiệp vụ (bỏ &lt;30cm / lãng phí 30cm–3m / nhập lại kho &gt;3m). */
public record RemainderBreakdownResponse(RemainderType remainderType, BigDecimal totalM) {
}
