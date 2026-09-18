package com.slatcut.cutting.service.optimizer;

import static org.assertj.core.api.Assertions.assertThat;

import com.slatcut.cutting.domain.InventoryBatch;
import com.slatcut.cutting.domain.SlatMaterial;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Test biên cho từng method public của {@link InventoryPool}, độc lập với
 * {@link BestFitDecreasingStrategy} — phần task 8.4 (bộ test đầy đủ mọi case biên cho 8.1-8.3),
 * bổ sung cho {@link BestFitDecreasingStrategyTest} vốn chỉ test gián tiếp qua tầng orchestration.
 */
class InventoryPoolTest {

    private SlatMaterial slatMaterial(long id) {
        SlatMaterial entity = new SlatMaterial();
        entity.setId(id);
        entity.setSlatMaterialName("Thanh nan " + id);
        return entity;
    }

    private InventoryBatch batch(SlatMaterial slatMaterial, int lengthMm, Integer count) {
        InventoryBatch entity = new InventoryBatch();
        entity.setSlatMaterial(slatMaterial);
        entity.setDoDaiThanhMm(lengthMm);
        entity.setSoThanh(count);
        return entity;
    }

    // --- constructor ---

    @Test
    void constructor_skipsBatchesWithZeroOrNullSoThanh() {
        SlatMaterial material = slatMaterial(1);
        InventoryPool pool = new InventoryPool(
                List.of(batch(material, 3000, 0), batch(material, 4000, null), batch(material, 5000, 2)));

        // remainingCount() dùng getOrDefault nên không phân biệt được "không có entry" với "entry
        // tồn tại giá trị 0" — cả 2 đều trả 0. Phải chứng minh qua hành vi thật của findRestockFit():
        // nếu 2 batch soThanh=0/null bị merge nhầm vào map, ceilingEntry() sẽ khớp NGAY độ dài ngắn
        // hơn (3000/4000, dù value=0) thay vì bỏ qua để tới đúng 5000 còn hàng thật.
        assertThat(pool.findRestockFit(material, 100)).contains(5000);
    }

    // --- findRestockFit (Mức 4) ---

    @Test
    void findRestockFit_noStockForMaterial_returnsEmpty() {
        InventoryPool pool = new InventoryPool(List.of());

        assertThat(pool.findRestockFit(slatMaterial(1), 3000)).isEmpty();
    }

    @Test
    void findRestockFit_choosesShortestLengthThatStillLeavesMoreThanThreeMetres() {
        SlatMaterial material = slatMaterial(2);
        // Cắt 3500mm: thanh 4000mm tuy ĐỦ DÀI nhưng chỉ để lại 500mm (lãng phí) nên bị bỏ qua;
        // thanh 6000mm cũng chỉ để lại 2500mm; thanh ngắn nhất hợp lệ là 8000mm (dư 4500mm).
        InventoryPool pool = new InventoryPool(
                List.of(batch(material, 6000, 1), batch(material, 4000, 1), batch(material, 8000, 1)));

        Optional<Integer> match = pool.findRestockFit(material, 3500);

        assertThat(match).contains(8000);
        assertThat(pool.remainingCount(2L, 4000)).isEqualTo(1);
        assertThat(pool.remainingCount(2L, 6000)).isEqualTo(1);
    }

    @Test
    void findRestockFit_stockLongEnoughButLeavingWaste_returnsEmptyAndConsumesNothing() {
        SlatMaterial material = slatMaterial(5);
        InventoryPool pool = new InventoryPool(List.of(batch(material, 4000, 1)));

        assertThat(pool.findRestockFit(material, 3500)).isEmpty();
        assertThat(pool.remainingCount(5L, 4000)).isEqualTo(1);
    }

    @Test
    void findRestockFit_consumingLastUnitRemovesEntryEntirely() {
        SlatMaterial material = slatMaterial(3);
        InventoryPool pool = new InventoryPool(List.of(batch(material, 8000, 1)));

        pool.findRestockFit(material, 3500);

        // remainingCount() không phân biệt được "entry đã xoá" với "entry còn giá trị 0" (cùng trả
        // 0). Chứng minh entry ĐÃ XOÁ thật qua hành vi: nếu chỉ đưa count về 0 mà không xoá key,
        // ceilingEntry() vẫn khớp key đó (không kiểm tra value>0) và sẽ cấp phát nhầm 1 thanh không
        // còn tồn tại — gọi lại phải trả rỗng, không phải khớp nhầm 8000mm.
        assertThat(pool.findRestockFit(material, 100)).isEmpty();
    }

    @Test
    void findRestockFit_consumingOneOfMultipleUnitsKeepsEntry() {
        SlatMaterial material = slatMaterial(4);
        InventoryPool pool = new InventoryPool(List.of(batch(material, 8000, 2)));

        pool.findRestockFit(material, 3500);

        assertThat(pool.remainingCount(4L, 8000)).isEqualTo(1);
    }

    // --- findNearFit ---

    @Test
    void findNearFit_remainderJustUnderThreshold_accepts() {
        SlatMaterial material = slatMaterial(5);
        InventoryPool pool = new InventoryPool(List.of(batch(material, 3299, 1)));

        assertThat(pool.findNearFit(material, 3000)).contains(3299);
        assertThat(pool.remainingCount(5L, 3299)).isZero();
    }

    @Test
    void findNearFit_remainderExactlyAtThreshold_rejectsWithoutConsuming() {
        SlatMaterial material = slatMaterial(6);
        InventoryPool pool = new InventoryPool(List.of(batch(material, 3300, 1)));

        assertThat(pool.findNearFit(material, 3000)).isEmpty();
        assertThat(pool.remainingCount(6L, 3300)).isEqualTo(1);
    }

    @Test
    void findNearFit_noCandidateAtAll_returnsEmptyWithoutError() {
        SlatMaterial material = slatMaterial(7);
        InventoryPool pool = new InventoryPool(List.of(batch(material, 2000, 1)));

        assertThat(pool.findNearFit(material, 3000)).isEmpty();
        assertThat(pool.remainingCount(7L, 2000)).isEqualTo(1);
    }

    // --- findMultipleOfSameLength ---

    @Test
    void findMultipleOfSameLength_multiplierAtLowerBoundTwo_matches() {
        SlatMaterial material = slatMaterial(8);
        InventoryPool pool = new InventoryPool(List.of(batch(material, 3000, 1)));

        Optional<InventoryPool.MultipleMatch> match = pool.findMultipleOfSameLength(material, 1500, 2);

        assertThat(match).isPresent();
        assertThat(match.get().stockLengthMm()).isEqualTo(3000);
        assertThat(match.get().multiplier()).isEqualTo(2);
    }

    @Test
    void findMultipleOfSameLength_multiplierAtUpperBoundOfAvailableCount_matches() {
        SlatMaterial material = slatMaterial(9);
        InventoryPool pool = new InventoryPool(List.of(batch(material, 4500, 1)));

        Optional<InventoryPool.MultipleMatch> match = pool.findMultipleOfSameLength(material, 1500, 3);

        assertThat(match).isPresent();
        assertThat(match.get().multiplier()).isEqualTo(3);
    }

    @Test
    void findMultipleOfSameLength_multiplierExceedsAvailableCount_skipsWithoutConsuming() {
        SlatMaterial material = slatMaterial(10);
        // Kho chỉ có thanh bội 3 (4500mm), nhưng chỉ 2 đơn 1500mm đang chờ -> availableCount=2,
        // multiplier=3 vượt quá -> phải bỏ qua, không tiêu thụ nhầm.
        InventoryPool pool = new InventoryPool(List.of(batch(material, 4500, 1)));

        Optional<InventoryPool.MultipleMatch> match = pool.findMultipleOfSameLength(material, 1500, 2);

        assertThat(match).isEmpty();
        assertThat(pool.remainingCount(10L, 4500)).isEqualTo(1);
    }

    @Test
    void findMultipleOfSameLength_multipleCandidateLengths_choosesShortest() {
        SlatMaterial material = slatMaterial(11);
        // 3000mm (k=2) và 4500mm (k=3) đều là bội số hợp lệ với availableCount=3 -> chọn NGẮN NHẤT.
        InventoryPool pool = new InventoryPool(List.of(batch(material, 4500, 1), batch(material, 3000, 1)));

        Optional<InventoryPool.MultipleMatch> match = pool.findMultipleOfSameLength(material, 1500, 3);

        assertThat(match).isPresent();
        assertThat(match.get().stockLengthMm()).isEqualTo(3000);
        assertThat(pool.remainingCount(11L, 4500)).isEqualTo(1);
    }

    // --- findCombination ---

    @Test
    void findCombination_delegatesToNearFitOnSummedLength() {
        SlatMaterial material = slatMaterial(12);
        InventoryPool pool = new InventoryPool(List.of(batch(material, 7000, 1)));

        Optional<Integer> match = pool.findCombination(material, 3000, 4000);

        assertThat(match).contains(7000);
        assertThat(pool.remainingCount(12L, 7000)).isZero();
    }

    // --- restock ---

    @Test
    void restock_onMaterialWithNoExistingStock_createsEntry() {
        SlatMaterial material = slatMaterial(13);
        InventoryPool pool = new InventoryPool(List.of());

        pool.restock(material, 5000);

        assertThat(pool.remainingCount(13L, 5000)).isEqualTo(1);
    }

    @Test
    void restock_calledTwiceForSameLength_accumulatesCount() {
        SlatMaterial material = slatMaterial(14);
        InventoryPool pool = new InventoryPool(List.of());

        pool.restock(material, 5000);
        pool.restock(material, 5000);

        assertThat(pool.remainingCount(14L, 5000)).isEqualTo(2);
    }

    // --- cô lập theo material ---

    @Test
    void stockIsIsolatedPerMaterial_evenWithSameLength() {
        SlatMaterial materialA = slatMaterial(15);
        SlatMaterial materialB = slatMaterial(16);
        InventoryPool pool = new InventoryPool(List.of(batch(materialA, 5000, 1), batch(materialB, 5000, 1)));

        // Cắt 1000mm để dư 4000mm, đủ điều kiện nhập lại kho nên Mức 4 mới tiêu thụ thanh.
        pool.findRestockFit(materialA, 1000);

        assertThat(pool.remainingCount(15L, 5000)).isZero();
        assertThat(pool.remainingCount(16L, 5000)).isEqualTo(1);
    }
}
