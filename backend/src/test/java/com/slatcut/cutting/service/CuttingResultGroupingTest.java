package com.slatcut.cutting.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.slatcut.cutting.domain.SlatGroup;
import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.service.optimizer.CutLevel;
import com.slatcut.cutting.service.optimizer.CutRecord;
import com.slatcut.cutting.service.optimizer.RemainderCategory;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Test thuần, không cần cơ sở dữ liệu — phép gộp là hàm thuần túy trên kết quả thuật toán. */
class CuttingResultGroupingTest {

    private static SlatMaterial material() {
        return material(1L, 70_000_001L, SlatGroup.BOTTOM_BAR);
    }

    private static SlatMaterial material(long id, long code, SlatGroup slatGroup) {
        SlatMaterial entity = new SlatMaterial();
        entity.setId(id);
        entity.setSlatMaterial(code);
        entity.setSlatMaterialName("Thanh nan kiểm thử " + code);
        entity.setSlatGroup(slatGroup);
        return entity;
    }

    private static CutRecord cut(CutLevel cutLevel) {
        return cut(material(), cutLevel);
    }

    private static CutRecord cut(SlatMaterial material, CutLevel cutLevel) {
        CuttingDemand piece = new CuttingDemand(material, 3000, 1, LocalDate.of(2026, 9, 28), "HY9001", 1);
        return new CutRecord(material, 3000, List.of(piece), 0, RemainderCategory.DISCARDED, cutLevel);
    }

    @Test
    void groupCuts_mergesIdenticalSticksIntoOneRow() {
        List<CuttingResultGrouping.CutGroup> groups = CuttingResultGrouping.groupCuts(List.of(cut(CutLevel.PA1), cut(CutLevel.PA1)));

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).stickCount()).isEqualTo(2);
        assertThat(groups.get(0).cutLevel()).isEqualTo(CutLevel.PA1);
    }

    /**
     * Hai phôi giống hệt nhau về hình dạng cắt và tập đoạn, nhưng cắt ở hai mức khác nhau, thì
     * KHÔNG được gộp. Gộp lại thì dòng kết quả mang mức của phôi đầu tiên, và báo cáo gửi xuống
     * xưởng nói sai phương án đã áp dụng cho nửa số phôi còn lại.
     *
     * <p>Đây là chốt chặn cho hợp đồng của phép gộp chứ không tái hiện một lần chạy cụ thể: bốn mức
     * của thuật toán hiện tại hiếm khi cho ra hai phôi trùng cả hình dạng lẫn tập đoạn, nhưng phép
     * gộp là hàm dùng chung cho cả đường ghi lẫn đường hiển thị nên ràng buộc phải nằm ngay trong
     * nó, không phụ thuộc vào việc hôm nay thuật toán có sinh ra tình huống đó hay không.
     */
    @Test
    void groupCuts_keepsSticksCutAtDifferentLevelsApart() {
        List<CuttingResultGrouping.CutGroup> groups = CuttingResultGrouping.groupCuts(List.of(cut(CutLevel.PA1), cut(CutLevel.PA3)));

        assertThat(groups).hasSize(2);
        assertThat(groups)
                .extracting(CuttingResultGrouping.CutGroup::cutLevel, CuttingResultGrouping.CutGroup::stickCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.api.Assertions.tuple(CutLevel.PA1, 1),
                        org.assertj.core.api.Assertions.tuple(CutLevel.PA3, 1));
    }

    /**
     * Hai loại thanh nan khác nhau cắt cùng một độ dài từ cùng một độ dài phôi thì KHÔNG được gộp.
     * Hình dạng cắt chỉ nói độ dài, không nói vật tư — mà một bộ cửa hoàn toàn có thể cần thanh đáy
     * và nan phụ cùng cắt đúng chiều rộng cửa từ cùng loại phôi.
     *
     * <p>Gộp nhầm ở đây không chỉ làm sai một con số: vật tư thứ hai <b>biến mất khỏi phương án đã
     * duyệt</b> (mất hẳn dòng {@code cutting_plan_detail} của nó) còn số phôi của vật tư thứ nhất
     * bị nhân đôi, nên xưởng nhận được chỉ dẫn cắt thiếu hẳn một loại thanh.
     */
    @Test
    void groupCuts_keepsSticksOfDifferentMaterialsApart() {
        SlatMaterial bottomBar = material(1L, 70_000_001L, SlatGroup.BOTTOM_BAR);
        SlatMaterial subSlat = material(2L, 70_000_002L, SlatGroup.SUB_SLAT);

        List<CuttingResultGrouping.CutGroup> groups =
                CuttingResultGrouping.groupCuts(List.of(cut(bottomBar, CutLevel.PA1), cut(subSlat, CutLevel.PA1)));

        assertThat(groups).hasSize(2);
        assertThat(groups)
                .extracting(group -> group.slatMaterial().getId(), CuttingResultGrouping.CutGroup::stickCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.api.Assertions.tuple(1L, 1),
                        org.assertj.core.api.Assertions.tuple(2L, 1));
    }
}
