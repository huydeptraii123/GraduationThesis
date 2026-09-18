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
    void computePlan_onlyStockLeavingWasteRemainder_recordsShortageAndLeavesStockUntouched() {
        SlatMaterial material = slatMaterial(2);
        // Thanh 4000mm ĐỦ DÀI để cắt 3200mm, nhưng dư 800mm rơi vào vùng 30cm-3m mà doanh nghiệp
        // không chấp nhận -> không mức nào nhận, báo thiếu vật tư. Thanh phải còn nguyên trong kho
        // để dành cho một đoạn khác khớp hơn.
        InventoryPool pool = new InventoryPool(List.of(batch(material, 4000, 1)));

        CuttingPlanResult result = strategy.computePlan(List.of(demand(material, 3200, 1)), pool);

        assertThat(result.cuts()).isEmpty();
        assertThat(result.shortages()).hasSize(1);
        assertThat(result.shortages().get(0).demand().cutLengthMm()).isEqualTo(3200);
        assertThat(pool.remainingCount(2L, 4000)).isEqualTo(1);
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
        // Đơn A giao sớm hơn (20/09) phải được xử lý trước đơn B (25/09), bất kể thứ tự truyền vào.
        // Hai thanh KHÁC độ dài để kết quả phân biệt được thứ tự: ai chạy trước lấy thanh 10000mm
        // (ngắn nhất còn để dư > 3m), người sau phải lấy thanh 12000mm. Dùng 2 thanh giống hệt nhau
        // thì đảo thứ tự vẫn ra cùng một tập kết quả, test sẽ xanh mà không chứng minh được gì.
        InventoryPool pool = new InventoryPool(List.of(batch(material, 10000, 1), batch(material, 12000, 1)));
        CuttingDemand orderB = demand(material, 5000, 1, LocalDate.of(2026, 9, 25), "HY90002", 1);
        CuttingDemand orderA = demand(material, 3000, 1, LocalDate.of(2026, 9, 20), "HY90001", 1);

        CuttingPlanResult result = strategy.computePlan(List.of(orderB, orderA), pool);

        assertThat(result.shortages()).isEmpty();
        assertThat(result.cuts()).hasSize(2);
        CutRecord cutForA = result.cuts().stream()
                .filter(c -> c.pieces().get(0).ycsx().equals("HY90001"))
                .findFirst()
                .orElseThrow();
        assertThat(cutForA.stockLengthMm()).isEqualTo(10000);
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
    void computePlan_multipleOfSameLength_notEnoughPendingUnitsFallsThroughToLevel4() {
        SlatMaterial material = slatMaterial(8);
        // Chỉ có 1 đoạn 1500mm (không có đoạn thứ 2 cùng độ dài đang chờ) dù thanh 3000mm đúng bội
        // số 2 lần -> Mức 2 từ chối vì thiếu đoạn để ghép. Mức 4 cũng KHÔNG lấy thanh 3000mm đó
        // (dư 1500mm là lãng phí) mà đi tiếp tới thanh 5000mm, để lại dư 3500mm nhập kho được.
        InventoryPool pool = new InventoryPool(List.of(batch(material, 3000, 1), batch(material, 5000, 1)));

        CuttingPlanResult result = strategy.computePlan(List.of(demand(material, 1500, 1)), pool);

        assertThat(result.cuts()).hasSize(1);
        CutRecord cut = result.cuts().get(0);
        assertThat(cut.pieces()).hasSize(1);
        assertThat(cut.stockLengthMm()).isEqualTo(5000);
        assertThat(cut.remainderMm()).isEqualTo(3500);
        assertThat(cut.remainderCategory()).isEqualTo(RemainderCategory.RESTOCK);
        assertThat(pool.remainingCount(8L, 3000)).isEqualTo(1);
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
    void computePlan_noCombinationPartnerAndStockWouldLeaveWaste_recordsShortage() {
        SlatMaterial material = slatMaterial(10);
        // Chỉ 1 đoạn duy nhất, không có đối tác nào để ghép Mức 3 -> rơi xuống Mức 4; nhưng thanh
        // 5000mm chỉ để lại 2000mm nên Mức 4 cũng từ chối, kết quả là thiếu vật tư.
        InventoryPool pool = new InventoryPool(List.of(batch(material, 5000, 1)));

        CuttingPlanResult result = strategy.computePlan(List.of(demand(material, 3000, 1)), pool);

        assertThat(result.cuts()).isEmpty();
        assertThat(result.shortages()).hasSize(1);
        assertThat(pool.remainingCount(10L, 5000)).isEqualTo(1);
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
        // Kho CHỈ có đúng 1 thanh 10000mm — không có thanh nào khác. Đơn X (giao trước) cắt 1000mm
        // qua Mức 4 -> dư 9000mm (> 3m) được nhập lại pool ngay. Đơn Y (giao sau) cần 5000mm:
        // Mức 3 không ghép được với X (1000+5000=6000, dư 4000mm so với thanh 10000mm, quá xa
        // ngưỡng "dư < 30cm"), và tới lượt Y thì thanh 10000mm gốc đã bị X tiêu thụ hết — nếu
        // KHÔNG tái dùng phần dư 9000mm vừa nhập kho thì Y chắc chắn thiếu vật tư. Y cắt được từ
        // chính thanh 9000mm đó (độ dài chưa từng tồn tại trong tồn kho gốc) là bằng chứng của cơ
        // chế tái dùng.
        InventoryPool pool = new InventoryPool(List.of(batch(material, 10000, 1)));
        CuttingDemand orderX = demand(material, 1000, 1, LocalDate.of(2026, 9, 10), "HY90001", 1);
        CuttingDemand orderY = demand(material, 5000, 1, LocalDate.of(2026, 9, 15), "HY90002", 1);

        CuttingPlanResult result = strategy.computePlan(List.of(orderX, orderY), pool);

        assertThat(result.shortages()).isEmpty();
        assertThat(result.cuts()).hasSize(2);
        CutRecord cutForY = result.cuts().stream()
                .filter(c -> c.pieces().get(0).ycsx().equals("HY90002"))
                .findFirst()
                .orElseThrow();
        assertThat(cutForY.stockLengthMm()).isEqualTo(9000);
        assertThat(cutForY.remainderMm()).isEqualTo(4000);
        assertThat(cutForY.remainderCategory()).isEqualTo(RemainderCategory.RESTOCK);
    }

    @Test
    void computePlan_remainderExactlyAtDiscardThreshold_rejectedByNearFitThenBecomesShortage() {
        SlatMaterial material = slatMaterial(14);
        // 3300 - 3000 = 300mm, đúng bằng ngưỡng -> Mức 1 từ chối (điều kiện là "< 300mm"). Mức 4
        // cũng từ chối vì 300mm không phải phần dư nhập kho được -> thiếu vật tư, thanh còn nguyên.
        InventoryPool pool = new InventoryPool(List.of(batch(material, 3300, 1)));

        CuttingPlanResult result = strategy.computePlan(List.of(demand(material, 3000, 1)), pool);

        assertThat(result.cuts()).isEmpty();
        assertThat(result.shortages()).hasSize(1);
        assertThat(pool.remainingCount(14L, 3300)).isEqualTo(1);
    }

    @Test
    void computePlan_remainderExactlyAtRestockThreshold_rejectedByLevel4BecomesShortage() {
        SlatMaterial material = slatMaterial(15);
        // 6000 - 3000 = 3000mm, ĐÚNG BẰNG ngưỡng. Điều kiện nhập lại kho là "> 3000mm" (lớn hơn
        // ngặt) nên Mức 4 phải từ chối chính xác tại đây, không được nới thành ">=".
        InventoryPool pool = new InventoryPool(List.of(batch(material, 6000, 1)));

        CuttingPlanResult result = strategy.computePlan(List.of(demand(material, 3000, 1)), pool);

        assertThat(result.cuts()).isEmpty();
        assertThat(result.shortages()).hasSize(1);
        assertThat(pool.remainingCount(15L, 6000)).isEqualTo(1);
    }

    /** Cặp đôi của test trên: hơn ngưỡng đúng 1mm thì Mức 4 phải nhận. */
    @Test
    void computePlan_remainderOneMillimetreAboveRestockThreshold_acceptedByLevel4() {
        SlatMaterial material = slatMaterial(25);
        InventoryPool pool = new InventoryPool(List.of(batch(material, 6001, 1)));

        CuttingPlanResult result = strategy.computePlan(List.of(demand(material, 3000, 1)), pool);

        assertThat(result.shortages()).isEmpty();
        assertThat(result.cuts()).hasSize(1);
        CutRecord cut = result.cuts().get(0);
        assertThat(cut.remainderMm()).isEqualTo(3001);
        assertThat(cut.remainderCategory()).isEqualTo(RemainderCategory.RESTOCK);
    }

    @Test
    void computePlan_emptyDemandList_returnsEmptyResult() {
        SlatMaterial material = slatMaterial(16);
        InventoryPool pool = new InventoryPool(List.of(batch(material, 3000, 1)));

        CuttingPlanResult result = strategy.computePlan(List.of(), pool);

        assertThat(result.cuts()).isEmpty();
        assertThat(result.shortages()).isEmpty();
    }

    @Test
    void computePlan_emptyInventory_allDemandsBecomeShortages() {
        SlatMaterial material = slatMaterial(17);
        InventoryPool pool = new InventoryPool(List.of());

        CuttingPlanResult result = strategy.computePlan(List.of(demand(material, 3000, 2)), pool);

        assertThat(result.cuts()).isEmpty();
        assertThat(result.shortages()).hasSize(2);
    }

    @Test
    void computePlan_twoIndependentMaterials_shortageInOneDoesNotBlockTheOther() {
        SlatMaterial materialA = slatMaterial(18);
        SlatMaterial materialB = slatMaterial(19);
        InventoryPool pool = new InventoryPool(List.of(batch(materialB, 3000, 1)));

        CuttingPlanResult result = strategy.computePlan(
                List.of(demand(materialA, 3000, 1), demand(materialB, 3000, 1)), pool);

        assertThat(result.shortages()).hasSize(1);
        assertThat(result.shortages().get(0).slatMaterial()).isEqualTo(materialA);
        assertThat(result.cuts()).hasSize(1);
        assertThat(result.cuts().get(0).slatMaterial()).isEqualTo(materialB);
    }

    @Test
    void computePlan_stockIsolatedBetweenMaterialsWithSameStockLength() {
        SlatMaterial materialA = slatMaterial(20);
        SlatMaterial materialB = slatMaterial(21);
        InventoryPool pool = new InventoryPool(List.of(batch(materialA, 5000, 1), batch(materialB, 5000, 1)));

        // Cắt 1000mm -> dư 4000mm, đủ điều kiện nhập lại kho nên Mức 4 nhận.
        strategy.computePlan(List.of(demand(materialA, 1000, 1)), pool);

        assertThat(pool.remainingCount(materialA.getId(), 5000)).isZero();
        assertThat(pool.remainingCount(materialB.getId(), 5000)).isEqualTo(1);
    }

    @Test
    void computePlan_multipleOfSameLength_extraPendingUnitBeyondMultiplierHandledSeparately() {
        SlatMaterial material = slatMaterial(22);
        // 3 đơn cùng 1500mm đang chờ, kho có 1 thanh 1500mm lẻ (khớp gần đúng, dư=0) + 1 thanh
        // 3000mm (bội 2, không có bội 3). Đơn đầu tiên khớp ngay Mức 1 với thanh 1500mm lẻ (dùng
        // trước, không cần đợi Mức 2); 2 đơn còn lại ghép qua Mức 2 với thanh 3000mm. Kết quả: 2
        // CutRecord, pieces size {1,2}, không đơn nào bị bỏ dở giữa 2 mức.
        InventoryPool pool = new InventoryPool(List.of(batch(material, 3000, 1), batch(material, 1500, 1)));

        CuttingPlanResult result = strategy.computePlan(List.of(demand(material, 1500, 3)), pool);

        assertThat(result.shortages()).isEmpty();
        assertThat(result.cuts()).hasSize(2);
        assertThat(result.cuts()).extracting(c -> c.pieces().size()).containsExactlyInAnyOrder(2, 1);
    }

    @Test
    void computePlan_combination_firstMatchInPriorityOrderWinsOverBetterLaterMatch() {
        SlatMaterial material = slatMaterial(23);
        // Ứng viên đứng TRƯỚC trong hàng đợi (2000mm) chỉ ghép được với dư 250mm (không tối ưu);
        // ứng viên đứng SAU (4000mm) có thể ghép khít tuyệt đối dư=0mm nếu được xét trước. Cả 2
        // lựa chọn đều tồn tại sẵn trong kho cùng lúc -> Mức 3 phải dừng ở ứng viên ĐẦU TIÊN khớp
        // theo đúng thứ tự hàng đợi (dư 250mm), không quét tìm khớp "tốt nhất" toàn cục (dư 0mm).
        // Thanh 7500mm chỉ để đoạn 4000mm còn lại có chỗ cắt hợp lệ ở Mức 4 (dư 3500mm), tránh
        // biến test thành ca thiếu vật tư và che mất điều đang cần kiểm.
        InventoryPool pool = new InventoryPool(
                List.of(batch(material, 5250, 1), batch(material, 7000, 1), batch(material, 7500, 1)));
        CuttingDemand x = demand(material, 3000, 1, LocalDate.of(2026, 9, 10), "HY90001", 1);
        CuttingDemand firstInQueue = demand(material, 2000, 1, LocalDate.of(2026, 9, 12), "HY90002", 1);
        CuttingDemand laterInQueue = demand(material, 4000, 1, LocalDate.of(2026, 9, 15), "HY90003", 1);

        CuttingPlanResult result = strategy.computePlan(List.of(x, firstInQueue, laterInQueue), pool);

        assertThat(result.shortages()).isEmpty();
        CutRecord combinedCut = result.cuts().stream()
                .filter(c -> c.pieces().size() == 2)
                .findFirst()
                .orElseThrow();
        assertThat(combinedCut.stockLengthMm()).isEqualTo(5250);
        assertThat(combinedCut.remainderMm()).isEqualTo(250);
        assertThat(combinedCut.pieces())
                .extracting(CuttingDemand::ycsx)
                .containsExactlyInAnyOrder("HY90001", "HY90002");
    }

    @Test
    void computePlan_nearFitTriedBeforeMultipleOfSameLength() {
        SlatMaterial material = slatMaterial(24);
        // 2 đơn 1500mm đang chờ. Kho có CẢ thanh 3000mm (bội số hợp lệ, Mức 2) LẪN thanh 1600mm
        // (khớp gần đúng riêng cho 1 đơn, dư 100mm<300mm, Mức 1) — Mức 1 phải được thử trước,
        // dùng thanh 1600mm cho đơn đầu, để lại đơn thứ 2 riêng lẻ không đủ đối tác Mức 2 nữa.
        // Đơn thứ 2 vì thế phải xuống Mức 4, và ở đó thanh 3000mm bị loại (dư 1500mm là lãng phí)
        // nên nó lấy thanh 5000mm — thanh 3000mm còn nguyên là bằng chứng Mức 2 đã thật sự từ chối.
        InventoryPool pool = new InventoryPool(
                List.of(batch(material, 3000, 1), batch(material, 1600, 1), batch(material, 5000, 1)));

        CuttingPlanResult result = strategy.computePlan(List.of(demand(material, 1500, 2)), pool);

        assertThat(result.shortages()).isEmpty();
        assertThat(result.cuts()).hasSize(2);
        assertThat(result.cuts())
                .extracting(CutRecord::stockLengthMm)
                .containsExactlyInAnyOrder(1600, 5000);
        assertThat(pool.remainingCount(24L, 3000)).isEqualTo(1);
    }
}
