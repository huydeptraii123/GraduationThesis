package com.slatcut.cutting.service.optimizer;

import static org.assertj.core.api.Assertions.assertThat;

import com.slatcut.cutting.domain.InventoryBatch;
import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.service.CuttingDemand;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Test smoke cho task 8.2 (bản cơ bản, chỉ Mức 4) — không phải bộ "đầy đủ mọi case biên" của 8.4.
 * JUnit thuần, không cần AbstractIntegrationTest/MySQL vì mọi object ở đây dựng tay, không đụng DB.
 */
class BestFitDecreasingStrategyTest {

    private final BestFitDecreasingStrategy strategy = new BestFitDecreasingStrategy();

    private SlatMaterial slatMaterial(long id) {
        SlatMaterial entity = new SlatMaterial();
        entity.setId(id);
        entity.setSlatMaterialName("Thanh nan " + id);
        return entity;
    }

    private InventoryBatch batch(SlatMaterial slatMaterial, int lengthMm, int count) {
        InventoryBatch entity = new InventoryBatch();
        entity.setSlatMaterial(slatMaterial);
        entity.setDoDaiThanhMm(lengthMm);
        entity.setSoThanh(count);
        return entity;
    }

    private CuttingDemand demand(SlatMaterial slatMaterial, int cutLengthMm, int quantity) {
        return new CuttingDemand(slatMaterial, cutLengthMm, quantity, LocalDate.of(2026, 9, 28), "HY90001", 1);
    }

    @Test
    void computePlan_exactFit_remainderZeroIsDiscarded() {
        SlatMaterial material = slatMaterial(1);
        InventoryPool pool = new InventoryPool(List.of(batch(material, 3000, 1)));

        CuttingPlanResult result = strategy.computePlan(List.of(demand(material, 3000, 1)), pool);

        assertThat(result.shortages()).isEmpty();
        assertThat(result.cuts()).hasSize(1);
        CutRecord cut = result.cuts().get(0);
        assertThat(cut.stockLengthMm()).isEqualTo(3000);
        assertThat(cut.remainderMm()).isEqualTo(0);
        assertThat(cut.remainderCategory()).isEqualTo(RemainderCategory.DISCARDED);
    }

    @Test
    void computePlan_remainderInWasteRange_classifiedAsWaste() {
        SlatMaterial material = slatMaterial(2);
        // 4000 - 3200 = 800mm, nằm trong 300-3000mm -> WASTE.
        InventoryPool pool = new InventoryPool(List.of(batch(material, 4000, 1)));

        CuttingPlanResult result = strategy.computePlan(List.of(demand(material, 3200, 1)), pool);

        assertThat(result.cuts()).hasSize(1);
        CutRecord cut = result.cuts().get(0);
        assertThat(cut.remainderMm()).isEqualTo(800);
        assertThat(cut.remainderCategory()).isEqualTo(RemainderCategory.WASTE);
    }

    @Test
    void computePlan_remainderAboveThreeMeters_classifiedAsRestock() {
        SlatMaterial material = slatMaterial(3);
        // 7200 - 3000 = 4200mm, > 3000mm -> RESTOCK.
        InventoryPool pool = new InventoryPool(List.of(batch(material, 7200, 1)));

        CuttingPlanResult result = strategy.computePlan(List.of(demand(material, 3000, 1)), pool);

        assertThat(result.cuts()).hasSize(1);
        CutRecord cut = result.cuts().get(0);
        assertThat(cut.remainderMm()).isEqualTo(4200);
        assertThat(cut.remainderCategory()).isEqualTo(RemainderCategory.RESTOCK);
    }

    @Test
    void computePlan_noStockLongEnough_recordsShortage() {
        SlatMaterial material = slatMaterial(4);
        InventoryPool pool = new InventoryPool(List.of(batch(material, 2000, 5)));

        CuttingPlanResult result = strategy.computePlan(List.of(demand(material, 2500, 1)), pool);

        assertThat(result.cuts()).isEmpty();
        assertThat(result.shortages()).hasSize(1);
        assertThat(result.shortages().get(0).demand().cutLengthMm()).isEqualTo(2500);
    }

    @Test
    void computePlan_multipleUnitsProcessedLargestFirst_consumesStockSequentially() {
        SlatMaterial material = slatMaterial(5);
        // 2 thanh 6000mm trong kho; 1 đơn vị 5000mm và 1 đơn vị 3000mm cần cắt.
        // Xử lý giảm dần: 5000mm trước (khớp thanh 6000mm còn lại, dư 1000mm), rồi 3000mm
        // (khớp thanh 6000mm còn lại kia, dư 3000mm) - cả 2 đều dùng thanh 6000mm, không đơn nào bị shortage.
        InventoryPool pool = new InventoryPool(List.of(batch(material, 6000, 2)));

        CuttingPlanResult result =
                strategy.computePlan(List.of(demand(material, 3000, 1), demand(material, 5000, 1)), pool);

        assertThat(result.shortages()).isEmpty();
        assertThat(result.cuts()).hasSize(2);
        assertThat(result.cuts()).extracting(CutRecord::remainderMm).containsExactlyInAnyOrder(1000, 3000);
        assertThat(pool.remainingCount(5L, 6000)).isEqualTo(0);
    }
}
