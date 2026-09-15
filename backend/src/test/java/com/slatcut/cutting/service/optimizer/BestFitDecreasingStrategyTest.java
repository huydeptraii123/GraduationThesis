package com.slatcut.cutting.service.optimizer;

import static org.assertj.core.api.Assertions.assertThat;

import com.slatcut.cutting.domain.InventoryBatch;
import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.service.CuttingDemand;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Test smoke cho task 8.1-8.3 (đúng 4 mức ưu tiên + thứ tự ưu tiên thật + tái dùng phần dư trong
 * cùng run) — không phải bộ "đầy đủ mọi case biên" của 8.4. JUnit thuần, không cần
 * AbstractIntegrationTest/MySQL vì mọi object ở đây dựng tay, không đụng DB.
 */
class BestFitDecreasingStrategyTest {

    private static final LocalDate DEFAULT_DATE = LocalDate.of(2026, 9, 28);

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
        return demand(slatMaterial, cutLengthMm, quantity, DEFAULT_DATE, "HY90001", 1);
    }

    private CuttingDemand demand(
            SlatMaterial slatMaterial, int cutLengthMm, int quantity, LocalDate reqdDeliveryDate, String ycsx, int item) {
        return new CuttingDemand(slatMaterial, cutLengthMm, quantity, reqdDeliveryDate, ycsx, item);
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
        assertThat(cut.pieces()).hasSize(1);
    }

    @Test
    void computePlan_remainderInWasteRange_classifiedAsWaste() {
        SlatMaterial material = slatMaterial(2);
        // 4000 - 3200 = 800mm, nằm trong 300-3000mm -> WASTE (Mức 1 từ chối vì dư >= 300mm).
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
    void computePlan_processesQueueInPriorityOrder_earlierDeliveryDateProcessedFirst() {
        SlatMaterial material = slatMaterial(5);
        // 2 thanh 6000mm; đơn A giao sớm hơn (20/09) phải được xử lý trước đơn B (25/09), bất kể
        // thứ tự truyền vào danh sách demands hay độ dài đoạn cần cắt.
        InventoryPool pool = new InventoryPool(List.of(batch(material, 6000, 2)));
        CuttingDemand orderB = demand(material, 5000, 1, LocalDate.of(2026, 9, 25), "HY90002", 1);
        CuttingDemand orderA = demand(material, 3000, 1, LocalDate.of(2026, 9, 20), "HY90001", 1);

        CuttingPlanResult result = strategy.computePlan(List.of(orderB, orderA), pool);

        assertThat(result.shortages()).isEmpty();
        assertThat(result.cuts()).hasSize(2);
        assertThat(result.cuts()).extracting(CutRecord::remainderMm).containsExactlyInAnyOrder(1000, 3000);
        assertThat(pool.remainingCount(5L, 6000)).isEqualTo(0);
    }

    @Test
    void computePlan_nearFitAcceptsSmallNonZeroRemainder() {
        SlatMaterial material = slatMaterial(6);
        // 3250 - 3000 = 250mm, < 300mm -> Mức 1 (khớp gần đúng) nhận, không rơi xuống Mức 4.
        InventoryPool pool = new InventoryPool(List.of(batch(material, 3250, 1)));

        CuttingPlanResult result = strategy.computePlan(List.of(demand(material, 3000, 1)), pool);

        assertThat(result.cuts()).hasSize(1);
        CutRecord cut = result.cuts().get(0);
        assertThat(cut.remainderMm()).isEqualTo(250);
        assertThat(cut.remainderCategory()).isEqualTo(RemainderCategory.DISCARDED);
    }

    @Test
    void computePlan_multipleOfSameLength_cutsTwoUnitsFromOneStock() {
        SlatMaterial material = slatMaterial(7);
        // 2 đoạn cùng 1500mm đang chờ, thanh 3000mm = đúng 2 lần 1500mm -> Mức 2, dư=0, gộp
        // vào đúng 1 CutRecord có 2 pieces thay vì cắt 2 thanh riêng.
        InventoryPool pool = new InventoryPool(List.of(batch(material, 3000, 1)));

        CuttingPlanResult result = strategy.computePlan(List.of(demand(material, 1500, 2)), pool);

        assertThat(result.shortages()).isEmpty();
        assertThat(result.cuts()).hasSize(1);
        CutRecord cut = result.cuts().get(0);
        assertThat(cut.pieces()).hasSize(2);
        assertThat(cut.remainderMm()).isEqualTo(0);
        assertThat(cut.remainderCategory()).isEqualTo(RemainderCategory.DISCARDED);
    }

    @Test
    void computePlan_multipleOfSameLength_notEnoughPendingUnitsFallsThroughToBestFit() {
        SlatMaterial material = slatMaterial(8);
        // Chỉ có 1 đoạn 1500mm (không có đoạn thứ 2 cùng độ dài đang chờ) dù thanh 3000mm đúng
        // bội số 2 lần -> Mức 2 từ chối (thiếu đoạn để ghép), rơi xuống Mức 4 (dư 1500mm -> WASTE).
        InventoryPool pool = new InventoryPool(List.of(batch(material, 3000, 1)));

        CuttingPlanResult result = strategy.computePlan(List.of(demand(material, 1500, 1)), pool);

        assertThat(result.cuts()).hasSize(1);
        CutRecord cut = result.cuts().get(0);
        assertThat(cut.pieces()).hasSize(1);
        assertThat(cut.remainderMm()).isEqualTo(1500);
        assertThat(cut.remainderCategory()).isEqualTo(RemainderCategory.WASTE);
    }

    @Test
    void computePlan_combinesTwoDifferentLengthsFromOneStock() {
        SlatMaterial material = slatMaterial(9);
        // Đúng ví dụ trong docs/requirements-functional.md: đơn 3m + đơn 4m dùng chung thanh 7m.
        InventoryPool pool = new InventoryPool(List.of(batch(material, 7000, 1)));
        CuttingDemand demand3m = demand(material, 3000, 1, DEFAULT_DATE, "HY90001", 1);
        CuttingDemand demand4m = demand(material, 4000, 1, DEFAULT_DATE, "HY90001", 2);

        CuttingPlanResult result = strategy.computePlan(List.of(demand3m, demand4m), pool);

        assertThat(result.shortages()).isEmpty();
        assertThat(result.cuts()).hasSize(1);
        CutRecord cut = result.cuts().get(0);
        assertThat(cut.stockLengthMm()).isEqualTo(7000);
        assertThat(cut.pieces()).hasSize(2);
        assertThat(cut.pieces()).extracting(CuttingDemand::cutLengthMm).containsExactlyInAnyOrder(3000, 4000);
        assertThat(cut.remainderMm()).isEqualTo(0);
    }

    @Test
    void computePlan_noCombinationPartner_fallsThroughToBestFit() {
        SlatMaterial material = slatMaterial(10);
        // Chỉ 1 đoạn duy nhất, không có đối tác nào để ghép Mức 3 -> rơi xuống Mức 4.
        InventoryPool pool = new InventoryPool(List.of(batch(material, 5000, 1)));

        CuttingPlanResult result = strategy.computePlan(List.of(demand(material, 3000, 1)), pool);

        assertThat(result.cuts()).hasSize(1);
        CutRecord cut = result.cuts().get(0);
        assertThat(cut.pieces()).hasSize(1);
        assertThat(cut.remainderMm()).isEqualTo(2000);
        assertThat(cut.remainderCategory()).isEqualTo(RemainderCategory.WASTE);
    }

    @Test
    void computePlan_scarceStock_higherPriorityOrderWinsLowerPriorityBecomesShortage() {
        SlatMaterial material = slatMaterial(11);
        // Chỉ 1 thanh 3000mm nhưng 2 đơn cùng cần cắt 3000mm -> đơn giao sớm hơn (10/09) được
        // dùng thanh, đơn giao muộn hơn (15/09) bị đánh dấu thiếu vật tư, không chặn đơn còn lại.
        InventoryPool pool = new InventoryPool(List.of(batch(material, 3000, 1)));
        CuttingDemand earlier = demand(material, 3000, 1, LocalDate.of(2026, 9, 10), "HY90001", 1);
        CuttingDemand later = demand(material, 3000, 1, LocalDate.of(2026, 9, 15), "HY90002", 1);

        CuttingPlanResult result = strategy.computePlan(List.of(later, earlier), pool);

        assertThat(result.cuts()).hasSize(1);
        assertThat(result.cuts().get(0).pieces().get(0).ycsx()).isEqualTo("HY90001");
        assertThat(result.shortages()).hasSize(1);
        assertThat(result.shortages().get(0).demand().ycsx()).isEqualTo("HY90002");
    }

    @Test
    void computePlan_tieBreakSameDeliveryDate_ordersByYcsx() {
        SlatMaterial material = slatMaterial(12);
        InventoryPool pool = new InventoryPool(List.of(batch(material, 3000, 1)));
        CuttingDemand ycsxB = demand(material, 3000, 1, DEFAULT_DATE, "HY90002", 1);
        CuttingDemand ycsxA = demand(material, 3000, 1, DEFAULT_DATE, "HY90001", 1);

        CuttingPlanResult result = strategy.computePlan(List.of(ycsxB, ycsxA), pool);

        assertThat(result.cuts()).hasSize(1);
        assertThat(result.cuts().get(0).pieces().get(0).ycsx()).isEqualTo("HY90001");
        assertThat(result.shortages()).hasSize(1);
        assertThat(result.shortages().get(0).demand().ycsx()).isEqualTo("HY90002");
    }

    @Test
    void computePlan_restocksRemainderAboveThreeMeters_reusedByLaterOrderInSameRun() {
        SlatMaterial material = slatMaterial(13);
        // Kho CHỈ có đúng 1 thanh 10000mm — không có thanh nào khác. Đơn X (giao trước) cắt
        // 1000mm -> dư 9000mm (RESTOCK, >3m) được nhập lại pool ngay. Đơn Y (giao sau) cần
        // 8600mm: không khớp Mức 1-3 với thanh 10000mm gốc (chọn số để không vô tình ghép được
        // Mức 3 với X — 1000+8600=9600, dư so với 10000 là 400mm, không đủ điều kiện "dư<30cm"),
        // nên khi tới lượt Y, thanh 10000mm gốc đã bị X tiêu thụ hết — nếu KHÔNG tái dùng phần dư
        // 9000mm vừa nhập kho thì Y chắc chắn thiếu vật tư; ngược lại Y cắt được từ chính thanh
        // 9000mm đó (độ dài này chưa từng tồn tại trong tồn kho gốc) chứng minh cơ chế tái dùng.
        InventoryPool pool = new InventoryPool(List.of(batch(material, 10000, 1)));
        CuttingDemand orderX = demand(material, 1000, 1, LocalDate.of(2026, 9, 10), "HY90001", 1);
        CuttingDemand orderY = demand(material, 8600, 1, LocalDate.of(2026, 9, 15), "HY90002", 1);

        CuttingPlanResult result = strategy.computePlan(List.of(orderX, orderY), pool);

        assertThat(result.shortages()).isEmpty();
        assertThat(result.cuts()).hasSize(2);
        CutRecord cutForY = result.cuts().stream()
                .filter(c -> c.pieces().get(0).ycsx().equals("HY90002"))
                .findFirst()
                .orElseThrow();
        assertThat(cutForY.stockLengthMm()).isEqualTo(9000);
        assertThat(cutForY.remainderMm()).isEqualTo(400);
        assertThat(cutForY.remainderCategory()).isEqualTo(RemainderCategory.WASTE);
    }
}
