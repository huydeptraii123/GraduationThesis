package com.slatcut.cutting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.slatcut.cutting.AbstractIntegrationTest;
import com.slatcut.cutting.config.ConflictException;
import com.slatcut.cutting.config.ResourceNotFoundException;
import com.slatcut.cutting.domain.BomItem;
import com.slatcut.cutting.domain.DoorProduct;
import com.slatcut.cutting.domain.SlatGroup;
import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.dto.DoorProductRequest;
import com.slatcut.cutting.dto.DoorProductResponse;
import com.slatcut.cutting.repository.BomItemRepository;
import com.slatcut.cutting.repository.DoorProductRepository;
import com.slatcut.cutting.repository.SlatMaterialRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DoorProductServiceTest extends AbstractIntegrationTest {

    @Autowired
    private DoorProductService service;

    @Autowired
    private DoorProductRepository doorProductRepository;

    @Autowired
    private SlatMaterialRepository slatMaterialRepository;

    @Autowired
    private BomItemRepository bomItemRepository;

    private DoorProduct persistProduct(long material, String name, String mauSac) {
        DoorProduct entity = new DoorProduct();
        entity.setMaterial(material);
        entity.setDoorMaterialName(name);
        entity.setMauSac(mauSac);
        return doorProductRepository.save(entity);
    }

    private DoorProductRequest request(long material, String name, String mauSac) {
        DoorProductRequest request = new DoorProductRequest();
        request.setMaterial(material);
        request.setDoorMaterialName(name);
        request.setMauSac(mauSac);
        return request;
    }

    @Test
    void create_persistsAllFieldsAndReturnsResponse() {
        DoorProductResponse response = service.create(request(81000001L, "Cửa cuốn khe thoáng", "#02"));

        assertThat(response.id()).isNotNull();
        assertThat(response.material()).isEqualTo(81000001L);
        assertThat(response.doorMaterialName()).isEqualTo("Cửa cuốn khe thoáng");
        assertThat(response.mauSac()).isEqualTo("#02");
    }

    @Test
    void create_throwsConflictWhenSameMaterialAndColourExists() {
        persistProduct(81000002L, "Cửa cuốn", "#02");

        assertThatThrownBy(() -> service.create(request(81000002L, "Cửa cuốn", "#02")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("#02");
    }

    @Test
    void create_allowsSameMaterialWithDifferentColour() {
        persistProduct(81000003L, "Cửa cuốn", "#02");

        DoorProductResponse response = service.create(request(81000003L, "Cửa cuốn", "#03"));

        assertThat(response.mauSac()).isEqualTo("#03");
        assertThat(doorProductRepository.findByMaterialAndMauSac(81000003L, "#02")).isPresent();
    }

    @Test
    void getById_throwsNotFoundForUnknownId() {
        assertThatThrownBy(() -> service.getById(999_999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("mẫu cửa");
    }

    @Test
    void getAll_returnsEveryPersistedProduct() {
        persistProduct(81000004L, "Cửa A", "#01");
        persistProduct(81000005L, "Cửa B", "#01");

        assertThat(service.getAll())
                .extracting(DoorProductResponse::material)
                .contains(81000004L, 81000005L);
    }

    @Test
    void update_overwritesFieldsOfExistingProduct() {
        DoorProduct existing = persistProduct(81000006L, "Tên cũ", "#01");

        DoorProductResponse response = service.update(existing.getId(), request(81000007L, "Tên mới", "#05"));

        assertThat(response.material()).isEqualTo(81000007L);
        assertThat(response.doorMaterialName()).isEqualTo("Tên mới");
        assertThat(response.mauSac()).isEqualTo("#05");
    }

    @Test
    void update_keepingOwnKeyIsNotTreatedAsDuplicate() {
        DoorProduct existing = persistProduct(81000008L, "Tên cũ", "#02");

        DoorProductResponse response = service.update(existing.getId(), request(81000008L, "Tên mới", "#02"));

        assertThat(response.doorMaterialName()).isEqualTo("Tên mới");
    }

    @Test
    void update_throwsConflictWhenKeyBelongsToAnotherProduct() {
        DoorProduct first = persistProduct(81000009L, "Cửa thứ nhất", "#02");
        persistProduct(81000009L, "Cửa thứ hai", "#03");

        assertThatThrownBy(() -> service.update(first.getId(), request(81000009L, "Cửa thứ nhất", "#03")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void update_throwsNotFoundForUnknownId() {
        assertThatThrownBy(() -> service.update(999_999L, request(81000010L, "Không tồn tại", "#01")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_removesProductWithoutBomItems() {
        DoorProduct existing = persistProduct(81000011L, "Cửa không định mức", "#01");

        service.delete(existing.getId());

        assertThat(doorProductRepository.findById(existing.getId())).isEmpty();
    }

    @Test
    void delete_throwsConflictWhenBomItemReferencesProduct() {
        DoorProduct existing = persistProduct(81000012L, "Cửa có định mức", "#01");
        SlatMaterial material = new SlatMaterial();
        material.setSlatMaterial(72000001L);
        material.setSlatMaterialName("Nan cho định mức");
        material.setSlatGroup(SlatGroup.MAIN_SLAT);
        slatMaterialRepository.save(material);
        BomItem bomItem = new BomItem();
        bomItem.setDoorProduct(existing);
        bomItem.setSlatMaterial(material);
        bomItemRepository.save(bomItem);

        assertThatThrownBy(() -> service.delete(existing.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("định mức BOM");

        assertThat(doorProductRepository.findById(existing.getId())).isPresent();
    }
}
