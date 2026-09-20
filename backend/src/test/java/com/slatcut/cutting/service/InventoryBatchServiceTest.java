package com.slatcut.cutting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.slatcut.cutting.AbstractIntegrationTest;
import com.slatcut.cutting.config.ConflictException;
import com.slatcut.cutting.config.ResourceNotFoundException;
import com.slatcut.cutting.domain.InventoryBatch;
import com.slatcut.cutting.domain.SlatGroup;
import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.dto.InventoryBatchRequest;
import com.slatcut.cutting.dto.InventoryBatchResponse;
import com.slatcut.cutting.dto.InventorySummaryResponse;
import com.slatcut.cutting.dto.PageResponse;
import com.slatcut.cutting.repository.InventoryBatchRepository;
import com.slatcut.cutting.repository.SlatMaterialRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class InventoryBatchServiceTest extends AbstractIntegrationTest {

    @Autowired
    private InventoryBatchService service;

    @Autowired
    private InventoryBatchRepository inventoryBatchRepository;

    @Autowired
    private SlatMaterialRepository slatMaterialRepository;

    private SlatMaterial persistMaterial(long code, String name) {
        return persistMaterial(code, name, SlatGroup.MAIN_SLAT);
    }

    private SlatMaterial persistMaterial(long code, String name, SlatGroup group) {
        SlatMaterial entity = new SlatMaterial();
        entity.setSlatMaterial(code);
        entity.setSlatMaterialName(name);
        entity.setSlatGroup(group);
        return slatMaterialRepository.save(entity);
    }

    private InventoryBatch persistBatch(SlatMaterial material, int lengthMm, int count) {
        InventoryBatch entity = new InventoryBatch();
        entity.setSlatMaterial(material);
        entity.setDoDaiThanhMm(lengthMm);
        entity.setSoThanh(count);
        return inventoryBatchRepository.save(entity);
    }

    private InventoryBatchRequest request(Long materialId, int lengthMm, int count) {
        InventoryBatchRequest request = new InventoryBatchRequest();
        request.setSlatMaterialId(materialId);
        request.setDoDaiThanhMm(lengthMm);
        request.setSoThanh(count);
        return request;
    }

    @Test
    void create_persistsBatchLinkedToMaterial() {
        SlatMaterial material = persistMaterial(71000001L, "Nan tồn kho");

        InventoryBatchResponse response = service.create(request(material.getId(), 2500, 6));

        assertThat(response.id()).isNotNull();
        assertThat(response.slatMaterialId()).isEqualTo(material.getId());
        assertThat(response.slatMaterialName()).isEqualTo("Nan tồn kho");
        assertThat(response.doDaiThanhMm()).isEqualTo(2500);
        assertThat(response.soThanh()).isEqualTo(6);
    }

    @Test
    void create_throwsNotFoundWhenSlatMaterialDoesNotExist() {
        assertThatThrownBy(() -> service.create(request(999_999L, 2500, 6)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("loại thanh nan");
    }

    @Test
    void create_throwsConflictWhenSameMaterialAndLengthAlreadyExists() {
        SlatMaterial material = persistMaterial(71000002L, "Nan đã có lô");
        persistBatch(material, 3000, 2);

        assertThatThrownBy(() -> service.create(request(material.getId(), 3000, 9)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("3000");
    }

    @Test
    void create_allowsSameMaterialAtDifferentLength() {
        SlatMaterial material = persistMaterial(71000003L, "Nan nhiều độ dài");
        persistBatch(material, 3000, 2);

        InventoryBatchResponse response = service.create(request(material.getId(), 3200, 5));

        assertThat(response.doDaiThanhMm()).isEqualTo(3200);
    }

    @Test
    void getById_throwsNotFoundForUnknownId() {
        assertThatThrownBy(() -> service.getById(999_999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("lô tồn kho");
    }

    @Test
    void getSummary_sumsEveryBatchNotJustOnePage() {
        // Đo bằng phần CHÊNH LỆCH thay vì con số tuyệt đối: lớp test khác có thể để lại dữ liệu khi
        // chạy cả bộ, và một test chỉ đúng lúc chạy riêng thì không bảo vệ được gì.
        InventorySummaryResponse before = service.getSummary();
        SlatMaterial material = persistMaterial(71300000L, "Nan tổng hợp");
        persistBatch(material, 6000, 10);
        persistBatch(material, 4000, 5);

        InventorySummaryResponse after = service.getSummary();

        // 10 thanh 6m + 5 thanh 4m = 80m, thêm 2 lô.
        assertThat(after.batchCount() - before.batchCount()).isEqualTo(2);
        assertThat(after.totalSticks() - before.totalSticks()).isEqualTo(15);
        assertThat(after.totalLengthM().subtract(before.totalLengthM())).isEqualByComparingTo("80.000");
    }

    @Test
    void getPage_exposesMaterialNameOfEachBatch() {
        SlatMaterial material = persistMaterial(71000004L, "Nan hiển thị tên");
        persistBatch(material, 2800, 1);

        assertThat(service.getPage("Nan hiển thị tên", null, PageRequest.of(0, 20)).content())
                .extracting(InventoryBatchResponse::slatMaterialName)
                .contains("Nan hiển thị tên");
    }

    @Test
    void getPage_splitsResultAcrossPagesInStableOrder() {
        SlatMaterial material = persistMaterial(71100000L, "Nan phân trang lô");
        for (int index = 0; index < 25; index++) {
            persistBatch(material, 2000 + index * 10, 1);
        }
        // Mồi nhử ngoài từ khóa: thiếu nó thì test vẫn xanh kể cả khi bộ lọc bị vô hiệu hóa.
        persistBatch(persistMaterial(71199999L, "Nan không thuộc phép đếm"), 2000, 1);
        Pageable byLength = PageRequest.of(0, 10, Sort.by("doDaiThanhMm"));

        PageResponse<InventoryBatchResponse> first = service.getPage("Nan phân trang lô", null, byLength);
        PageResponse<InventoryBatchResponse> second = service.getPage("Nan phân trang lô", null, byLength.withPage(1));
        PageResponse<InventoryBatchResponse> last = service.getPage("Nan phân trang lô", null, byLength.withPage(2));

        assertThat(first.totalElements()).isEqualTo(25);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.content()).hasSize(10);
        assertThat(last.content()).hasSize(5);
        assertThat(first.content()).extracting(InventoryBatchResponse::doDaiThanhMm).startsWith(2000);
        assertThat(second.content()).extracting(InventoryBatchResponse::doDaiThanhMm).startsWith(2100);
        assertThat(first.content())
                .extracting(InventoryBatchResponse::doDaiThanhMm)
                .doesNotContainAnyElementsOf(
                        second.content().stream().map(InventoryBatchResponse::doDaiThanhMm).toList());
    }

    @Test
    void getPage_filtersByMaterialCodeAndBySlatGroup() {
        SlatMaterial mainSlat = persistMaterial(71200001L, "Nan lọc lô chính", SlatGroup.MAIN_SLAT);
        SlatMaterial rail = persistMaterial(71200002L, "Nan lọc lô ray", SlatGroup.RAIL);
        persistBatch(mainSlat, 3000, 4);
        persistBatch(rail, 3000, 7);
        Pageable firstPage = PageRequest.of(0, 20);

        PageResponse<InventoryBatchResponse> byCode = service.getPage("71200002", null, firstPage);
        PageResponse<InventoryBatchResponse> byGroup = service.getPage("Nan lọc lô", SlatGroup.RAIL, firstPage);

        assertThat(byCode.totalElements()).isEqualTo(1);
        assertThat(byCode.content().getFirst().soThanh()).isEqualTo(7);
        assertThat(byGroup.totalElements()).isEqualTo(1);
        assertThat(byGroup.content().getFirst().slatMaterialName()).isEqualTo("Nan lọc lô ray");
    }

    @Test
    void update_changesQuantityOfExistingBatch() {
        SlatMaterial material = persistMaterial(71000005L, "Nan sửa số lượng");
        InventoryBatch batch = persistBatch(material, 2500, 3);

        InventoryBatchResponse response = service.update(batch.getId(), request(material.getId(), 2500, 11));

        assertThat(response.soThanh()).isEqualTo(11);
    }

    @Test
    void update_movesBatchToAnotherMaterial() {
        SlatMaterial source = persistMaterial(71000006L, "Nan nguồn");
        SlatMaterial target = persistMaterial(71000007L, "Nan đích");
        InventoryBatch batch = persistBatch(source, 2500, 3);

        InventoryBatchResponse response = service.update(batch.getId(), request(target.getId(), 2500, 3));

        assertThat(response.slatMaterialId()).isEqualTo(target.getId());
        assertThat(response.slatMaterialName()).isEqualTo("Nan đích");
    }

    @Test
    void update_keepingOwnKeyIsNotTreatedAsDuplicate() {
        SlatMaterial material = persistMaterial(71000008L, "Nan giữ nguyên khóa");
        InventoryBatch batch = persistBatch(material, 2500, 3);

        InventoryBatchResponse response = service.update(batch.getId(), request(material.getId(), 2500, 7));

        assertThat(response.soThanh()).isEqualTo(7);
    }

    @Test
    void update_throwsConflictWhenKeyBelongsToAnotherBatch() {
        SlatMaterial material = persistMaterial(71000009L, "Nan hai lô");
        InventoryBatch first = persistBatch(material, 2500, 3);
        persistBatch(material, 3000, 4);

        assertThatThrownBy(() -> service.update(first.getId(), request(material.getId(), 3000, 3)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void update_throwsNotFoundWhenTargetMaterialDoesNotExist() {
        SlatMaterial material = persistMaterial(71000010L, "Nan còn tồn tại");
        InventoryBatch batch = persistBatch(material, 2500, 3);

        assertThatThrownBy(() -> service.update(batch.getId(), request(999_999L, 2500, 3)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("loại thanh nan");
    }

    @Test
    void delete_removesBatch() {
        SlatMaterial material = persistMaterial(71000011L, "Nan xóa lô");
        InventoryBatch batch = persistBatch(material, 2500, 3);

        service.delete(batch.getId());

        assertThat(inventoryBatchRepository.findById(batch.getId())).isEmpty();
    }

    @Test
    void delete_throwsNotFoundForUnknownId() {
        assertThatThrownBy(() -> service.delete(999_999L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
