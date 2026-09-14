package com.slatcut.cutting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.slatcut.cutting.AbstractIntegrationTest;
import com.slatcut.cutting.config.ConflictException;
import com.slatcut.cutting.config.ResourceNotFoundException;
import com.slatcut.cutting.domain.BomItem;
import com.slatcut.cutting.domain.DoorProduct;
import com.slatcut.cutting.domain.InventoryBatch;
import com.slatcut.cutting.domain.SlatGroup;
import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.dto.SlatMaterialRequest;
import com.slatcut.cutting.dto.SlatMaterialResponse;
import com.slatcut.cutting.repository.BomItemRepository;
import com.slatcut.cutting.repository.DoorProductRepository;
import com.slatcut.cutting.repository.InventoryBatchRepository;
import com.slatcut.cutting.repository.SlatMaterialRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SlatMaterialServiceTest extends AbstractIntegrationTest {

    @Autowired
    private SlatMaterialService service;

    @Autowired
    private SlatMaterialRepository slatMaterialRepository;

    @Autowired
    private InventoryBatchRepository inventoryBatchRepository;

    @Autowired
    private DoorProductRepository doorProductRepository;

    @Autowired
    private BomItemRepository bomItemRepository;

    private SlatMaterial persistMaterial(long code, String name) {
        SlatMaterial entity = new SlatMaterial();
        entity.setSlatMaterial(code);
        entity.setSlatMaterialName(name);
        entity.setSlatGroup(SlatGroup.MAIN_SLAT);
        return slatMaterialRepository.save(entity);
    }

    private SlatMaterialRequest request(long code, String name, SlatGroup group) {
        SlatMaterialRequest request = new SlatMaterialRequest();
        request.setSlatMaterial(code);
        request.setSlatMaterialName(name);
        request.setSlatGroup(group);
        return request;
    }

    @Test
    void create_persistsAllFieldsAndReturnsResponse() {
        SlatMaterialResponse response = service.create(request(70000001L, "Nan chính 77mm", SlatGroup.MAIN_SLAT));

        assertThat(response.id()).isNotNull();
        assertThat(response.slatMaterial()).isEqualTo(70000001L);
        assertThat(response.slatMaterialName()).isEqualTo("Nan chính 77mm");
        assertThat(response.slatGroup()).isEqualTo(SlatGroup.MAIN_SLAT);
        assertThat(slatMaterialRepository.findBySlatMaterial(70000001L)).isPresent();
    }

    @Test
    void create_throwsConflictWhenCodeAlreadyExists() {
        persistMaterial(70000002L, "Nan có sẵn");

        assertThatThrownBy(() -> service.create(request(70000002L, "Nan trùng mã", SlatGroup.RAIL)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("70000002");

        assertThat(slatMaterialRepository.findBySlatMaterial(70000002L).orElseThrow().getSlatMaterialName())
                .isEqualTo("Nan có sẵn");
    }

    @Test
    void getAll_returnsEveryPersistedMaterial() {
        persistMaterial(70000003L, "Nan A");
        persistMaterial(70000004L, "Nan B");

        assertThat(service.getAll())
                .extracting(SlatMaterialResponse::slatMaterial)
                .contains(70000003L, 70000004L);
    }

    @Test
    void getById_returnsPersistedMaterial() {
        SlatMaterial existing = persistMaterial(70000005L, "Nan C");

        assertThat(service.getById(existing.getId()).slatMaterialName()).isEqualTo("Nan C");
    }

    @Test
    void getById_throwsNotFoundForUnknownId() {
        assertThatThrownBy(() -> service.getById(999_999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999999");
    }

    @Test
    void update_overwritesFieldsOfExistingMaterial() {
        SlatMaterial existing = persistMaterial(70000006L, "Tên cũ");

        SlatMaterialResponse response =
                service.update(existing.getId(), request(70000007L, "Tên mới", SlatGroup.BOTTOM_BAR));

        assertThat(response.slatMaterial()).isEqualTo(70000007L);
        assertThat(response.slatMaterialName()).isEqualTo("Tên mới");
        assertThat(response.slatGroup()).isEqualTo(SlatGroup.BOTTOM_BAR);
    }

    @Test
    void update_keepingOwnCodeIsNotTreatedAsDuplicate() {
        SlatMaterial existing = persistMaterial(70000008L, "Tên cũ");

        SlatMaterialResponse response =
                service.update(existing.getId(), request(70000008L, "Tên mới", SlatGroup.MAIN_SLAT));

        assertThat(response.slatMaterialName()).isEqualTo("Tên mới");
    }

    @Test
    void update_throwsConflictWhenCodeBelongsToAnotherMaterial() {
        SlatMaterial first = persistMaterial(70000009L, "Nan thứ nhất");
        persistMaterial(70000010L, "Nan thứ hai");

        assertThatThrownBy(() -> service.update(first.getId(), request(70000010L, "Đổi sang mã đã có", SlatGroup.RAIL)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void update_throwsNotFoundForUnknownId() {
        assertThatThrownBy(() -> service.update(999_999L, request(70000011L, "Không tồn tại", SlatGroup.OTHER)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_removesMaterialWithoutReferences() {
        SlatMaterial existing = persistMaterial(70000012L, "Nan không tham chiếu");

        service.delete(existing.getId());

        assertThat(slatMaterialRepository.findById(existing.getId())).isEmpty();
    }

    @Test
    void delete_throwsConflictWhenInventoryBatchReferencesMaterial() {
        SlatMaterial existing = persistMaterial(70000013L, "Nan còn tồn kho");
        InventoryBatch batch = new InventoryBatch();
        batch.setSlatMaterial(existing);
        batch.setDoDaiThanhMm(2500);
        batch.setSoThanh(4);
        inventoryBatchRepository.save(batch);

        assertThatThrownBy(() -> service.delete(existing.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("tồn kho");

        assertThat(slatMaterialRepository.findById(existing.getId())).isPresent();
    }

    @Test
    void delete_throwsConflictWhenOnlyBomItemReferencesMaterial() {
        SlatMaterial existing = persistMaterial(70000014L, "Nan chỉ nằm trong định mức");
        DoorProduct doorProduct = new DoorProduct();
        doorProduct.setMaterial(80000001L);
        doorProduct.setDoorMaterialName("Cửa cuốn thử nghiệm");
        doorProduct.setMauSac("#01");
        doorProductRepository.save(doorProduct);
        BomItem bomItem = new BomItem();
        bomItem.setDoorProduct(doorProduct);
        bomItem.setSlatMaterial(existing);
        bomItemRepository.save(bomItem);

        assertThatThrownBy(() -> service.delete(existing.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("định mức BOM");

        assertThat(slatMaterialRepository.findById(existing.getId())).isPresent();
    }
}
