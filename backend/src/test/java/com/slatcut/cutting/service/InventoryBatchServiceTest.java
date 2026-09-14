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
import com.slatcut.cutting.repository.InventoryBatchRepository;
import com.slatcut.cutting.repository.SlatMaterialRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InventoryBatchServiceTest extends AbstractIntegrationTest {

    @Autowired
    private InventoryBatchService service;

    @Autowired
    private InventoryBatchRepository inventoryBatchRepository;

    @Autowired
    private SlatMaterialRepository slatMaterialRepository;

    private SlatMaterial persistMaterial(long code, String name) {
        SlatMaterial entity = new SlatMaterial();
        entity.setSlatMaterial(code);
        entity.setSlatMaterialName(name);
        entity.setSlatGroup(SlatGroup.MAIN_SLAT);
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
    void getAll_exposesMaterialNameOfEachBatch() {
        SlatMaterial material = persistMaterial(71000004L, "Nan hiển thị tên");
        persistBatch(material, 2800, 1);

        assertThat(service.getAll())
                .extracting(InventoryBatchResponse::slatMaterialName)
                .contains("Nan hiển thị tên");
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
