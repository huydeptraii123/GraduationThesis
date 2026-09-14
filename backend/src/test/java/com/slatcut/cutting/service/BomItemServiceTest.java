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
import com.slatcut.cutting.dto.BomItemRequest;
import com.slatcut.cutting.dto.BomItemResponse;
import com.slatcut.cutting.repository.BomItemRepository;
import com.slatcut.cutting.repository.DoorProductRepository;
import com.slatcut.cutting.repository.SlatMaterialRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BomItemServiceTest extends AbstractIntegrationTest {

    @Autowired
    private BomItemService service;

    @Autowired
    private BomItemRepository bomItemRepository;

    @Autowired
    private DoorProductRepository doorProductRepository;

    @Autowired
    private SlatMaterialRepository slatMaterialRepository;

    private DoorProduct persistProduct(long material, String mauSac) {
        DoorProduct entity = new DoorProduct();
        entity.setMaterial(material);
        entity.setDoorMaterialName("Cửa cuốn " + material);
        entity.setMauSac(mauSac);
        return doorProductRepository.save(entity);
    }

    private SlatMaterial persistMaterial(long code, String name) {
        SlatMaterial entity = new SlatMaterial();
        entity.setSlatMaterial(code);
        entity.setSlatMaterialName(name);
        entity.setSlatGroup(SlatGroup.MAIN_SLAT);
        return slatMaterialRepository.save(entity);
    }

    private BomItem persistBomItem(DoorProduct doorProduct, SlatMaterial slatMaterial) {
        BomItem entity = new BomItem();
        entity.setDoorProduct(doorProduct);
        entity.setSlatMaterial(slatMaterial);
        return bomItemRepository.save(entity);
    }

    private BomItemRequest request(Long doorProductId, Long slatMaterialId) {
        BomItemRequest request = new BomItemRequest();
        request.setDoorProductId(doorProductId);
        request.setSlatMaterialId(slatMaterialId);
        return request;
    }

    @Test
    void create_persistsTechnicalParametersAndFlattensBothRelations() {
        DoorProduct doorProduct = persistProduct(82000001L, "#02");
        SlatMaterial material = persistMaterial(73000001L, "Nan chính");
        BomItemRequest request = request(doorProduct.getId(), material.getId());
        request.setWidthOffsetM(new BigDecimal("0.024"));
        request.setSlatCountSlope(new BigDecimal("10.526316"));
        request.setSlatCountIntercept(new BigDecimal("1.2000"));

        BomItemResponse response = service.create(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.doorProductId()).isEqualTo(doorProduct.getId());
        assertThat(response.doorProductMauSac()).isEqualTo("#02");
        assertThat(response.slatMaterialId()).isEqualTo(material.getId());
        assertThat(response.slatMaterialName()).isEqualTo("Nan chính");
        assertThat(response.widthOffsetM()).isEqualByComparingTo("0.024");
        assertThat(response.slatCountSlope()).isEqualByComparingTo("10.526316");
        assertThat(response.slatCountIntercept()).isEqualByComparingTo("1.2");
    }

    @Test
    void create_acceptsBomItemWithoutAnyTechnicalParameter() {
        DoorProduct doorProduct = persistProduct(82000002L, "#02");
        SlatMaterial material = persistMaterial(73000002L, "Nan chưa có công thức");

        BomItemResponse response = service.create(request(doorProduct.getId(), material.getId()));

        assertThat(response.widthOffsetM()).isNull();
        assertThat(response.heightOffsetM()).isNull();
        assertThat(response.dinhMucTbMPerBoCua()).isNull();
    }

    @Test
    void create_throwsNotFoundNamingDoorProductWhenItDoesNotExist() {
        SlatMaterial material = persistMaterial(73000003L, "Nan tồn tại");

        assertThatThrownBy(() -> service.create(request(999_999L, material.getId())))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("mẫu cửa");
    }

    @Test
    void create_throwsNotFoundNamingSlatMaterialWhenItDoesNotExist() {
        DoorProduct doorProduct = persistProduct(82000003L, "#02");

        assertThatThrownBy(() -> service.create(request(doorProduct.getId(), 999_999L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("loại thanh nan");
    }

    @Test
    void create_reportsDoorProductFirstWhenBothForeignKeysAreInvalid() {
        assertThatThrownBy(() -> service.create(request(999_999L, 888_888L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("mẫu cửa");
    }

    @Test
    void create_throwsConflictWhenPairAlreadyHasBomItem() {
        DoorProduct doorProduct = persistProduct(82000004L, "#02");
        SlatMaterial material = persistMaterial(73000004L, "Nan đã có định mức");
        persistBomItem(doorProduct, material);

        assertThatThrownBy(() -> service.create(request(doorProduct.getId(), material.getId())))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("định mức BOM");
    }

    @Test
    void create_allowsSameDoorProductWithAnotherSlatMaterial() {
        DoorProduct doorProduct = persistProduct(82000005L, "#02");
        SlatMaterial first = persistMaterial(73000005L, "Nan chính");
        SlatMaterial second = persistMaterial(73000006L, "Thanh đáy");
        persistBomItem(doorProduct, first);

        BomItemResponse response = service.create(request(doorProduct.getId(), second.getId()));

        assertThat(response.slatMaterialId()).isEqualTo(second.getId());
    }

    @Test
    void getById_throwsNotFoundForUnknownId() {
        assertThatThrownBy(() -> service.getById(999_999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("định mức BOM");
    }

    @Test
    void getAll_returnsEveryPersistedBomItem() {
        DoorProduct doorProduct = persistProduct(82000006L, "#02");
        persistBomItem(doorProduct, persistMaterial(73000007L, "Nan A"));
        persistBomItem(doorProduct, persistMaterial(73000008L, "Nan B"));

        assertThat(service.getAll())
                .extracting(BomItemResponse::slatMaterialName)
                .contains("Nan A", "Nan B");
    }

    @Test
    void update_movesBomItemToAnotherDoorProductAndSlatMaterial() {
        DoorProduct source = persistProduct(82000007L, "#02");
        DoorProduct target = persistProduct(82000008L, "#03");
        SlatMaterial sourceMaterial = persistMaterial(73000009L, "Nan nguồn");
        SlatMaterial targetMaterial = persistMaterial(73000010L, "Nan đích");
        BomItem existing = persistBomItem(source, sourceMaterial);
        BomItemRequest request = request(target.getId(), targetMaterial.getId());
        request.setHeightOffsetM(new BigDecimal("0.115"));

        BomItemResponse response = service.update(existing.getId(), request);

        assertThat(response.doorProductId()).isEqualTo(target.getId());
        assertThat(response.slatMaterialId()).isEqualTo(targetMaterial.getId());
        assertThat(response.heightOffsetM()).isEqualByComparingTo("0.115");
    }

    @Test
    void update_keepingOwnPairIsNotTreatedAsDuplicate() {
        DoorProduct doorProduct = persistProduct(82000009L, "#02");
        SlatMaterial material = persistMaterial(73000011L, "Nan giữ nguyên");
        BomItem existing = persistBomItem(doorProduct, material);
        BomItemRequest request = request(doorProduct.getId(), material.getId());
        request.setDinhMucTbMPerBoCua(new BigDecimal("12.5"));

        BomItemResponse response = service.update(existing.getId(), request);

        assertThat(response.dinhMucTbMPerBoCua()).isEqualByComparingTo("12.5");
    }

    @Test
    void update_throwsConflictWhenPairBelongsToAnotherBomItem() {
        DoorProduct doorProduct = persistProduct(82000010L, "#02");
        SlatMaterial first = persistMaterial(73000012L, "Nan thứ nhất");
        SlatMaterial second = persistMaterial(73000013L, "Nan thứ hai");
        BomItem existing = persistBomItem(doorProduct, first);
        persistBomItem(doorProduct, second);

        assertThatThrownBy(() -> service.update(existing.getId(), request(doorProduct.getId(), second.getId())))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void update_throwsNotFoundForUnknownId() {
        DoorProduct doorProduct = persistProduct(82000011L, "#02");
        SlatMaterial material = persistMaterial(73000014L, "Nan tồn tại");

        assertThatThrownBy(() -> service.update(999_999L, request(doorProduct.getId(), material.getId())))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("định mức BOM");
    }

    @Test
    void delete_removesBomItem() {
        DoorProduct doorProduct = persistProduct(82000012L, "#02");
        BomItem existing = persistBomItem(doorProduct, persistMaterial(73000015L, "Nan xóa định mức"));

        service.delete(existing.getId());

        assertThat(bomItemRepository.findById(existing.getId())).isEmpty();
    }

    @Test
    void delete_throwsNotFoundForUnknownId() {
        assertThatThrownBy(() -> service.delete(999_999L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
