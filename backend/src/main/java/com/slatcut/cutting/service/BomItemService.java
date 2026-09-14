package com.slatcut.cutting.service;

import com.slatcut.cutting.config.ConflictException;
import com.slatcut.cutting.config.ResourceNotFoundException;
import com.slatcut.cutting.domain.BomItem;
import com.slatcut.cutting.domain.DoorProduct;
import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.dto.BomItemRequest;
import com.slatcut.cutting.dto.BomItemResponse;
import com.slatcut.cutting.mapper.BomItemMapper;
import com.slatcut.cutting.repository.BomItemRepository;
import com.slatcut.cutting.repository.DoorProductRepository;
import com.slatcut.cutting.repository.SlatMaterialRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BomItemService {

    private final BomItemRepository bomItemRepository;
    private final DoorProductRepository doorProductRepository;
    private final SlatMaterialRepository slatMaterialRepository;
    private final BomItemMapper mapper;

    public BomItemService(
            BomItemRepository bomItemRepository,
            DoorProductRepository doorProductRepository,
            SlatMaterialRepository slatMaterialRepository,
            BomItemMapper mapper) {
        this.bomItemRepository = bomItemRepository;
        this.doorProductRepository = doorProductRepository;
        this.slatMaterialRepository = slatMaterialRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<BomItemResponse> getAll() {
        return bomItemRepository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public BomItemResponse getById(Long id) {
        return mapper.toResponse(findEntityById(id));
    }

    @Transactional
    public BomItemResponse create(BomItemRequest request) {
        DoorProduct doorProduct = findDoorProductById(request.getDoorProductId());
        SlatMaterial slatMaterial = findSlatMaterialById(request.getSlatMaterialId());
        checkNoDuplicateBomItem(request, null);

        BomItem entity = new BomItem();
        entity.setDoorProduct(doorProduct);
        entity.setSlatMaterial(slatMaterial);
        mapper.updateEntity(request, entity);
        return mapper.toResponse(bomItemRepository.save(entity));
    }

    @Transactional
    public BomItemResponse update(Long id, BomItemRequest request) {
        BomItem entity = findEntityById(id);
        DoorProduct doorProduct = findDoorProductById(request.getDoorProductId());
        SlatMaterial slatMaterial = findSlatMaterialById(request.getSlatMaterialId());
        checkNoDuplicateBomItem(request, id);

        entity.setDoorProduct(doorProduct);
        entity.setSlatMaterial(slatMaterial);
        mapper.updateEntity(request, entity);
        return mapper.toResponse(bomItemRepository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        bomItemRepository.delete(findEntityById(id));
    }

    private void checkNoDuplicateBomItem(BomItemRequest request, Long excludeId) {
        bomItemRepository
                .findByDoorProduct_IdAndSlatMaterial_Id(request.getDoorProductId(), request.getSlatMaterialId())
                .filter(existing -> !existing.getId().equals(excludeId))
                .ifPresent(existing -> {
                    throw new ConflictException("Đã tồn tại định mức BOM cho mẫu cửa và loại thanh nan này");
                });
    }

    private BomItem findEntityById(Long id) {
        return bomItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy định mức BOM với id=" + id));
    }

    private DoorProduct findDoorProductById(Long id) {
        return doorProductRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mẫu cửa với id=" + id));
    }

    private SlatMaterial findSlatMaterialById(Long id) {
        return slatMaterialRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại thanh nan với id=" + id));
    }
}
