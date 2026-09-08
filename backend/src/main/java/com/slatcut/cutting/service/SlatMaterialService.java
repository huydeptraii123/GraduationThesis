package com.slatcut.cutting.service;

import com.slatcut.cutting.config.ConflictException;
import com.slatcut.cutting.config.ResourceNotFoundException;
import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.dto.SlatMaterialRequest;
import com.slatcut.cutting.dto.SlatMaterialResponse;
import com.slatcut.cutting.mapper.SlatMaterialMapper;
import com.slatcut.cutting.repository.InventoryBatchRepository;
import com.slatcut.cutting.repository.SlatMaterialRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SlatMaterialService {

    private final SlatMaterialRepository slatMaterialRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final SlatMaterialMapper mapper;

    public SlatMaterialService(
            SlatMaterialRepository slatMaterialRepository,
            InventoryBatchRepository inventoryBatchRepository,
            SlatMaterialMapper mapper) {
        this.slatMaterialRepository = slatMaterialRepository;
        this.inventoryBatchRepository = inventoryBatchRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<SlatMaterialResponse> getAll() {
        return slatMaterialRepository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SlatMaterialResponse getById(Long id) {
        return mapper.toResponse(findEntityById(id));
    }

    @Transactional
    public SlatMaterialResponse create(SlatMaterialRequest request) {
        checkNoDuplicateCode(request.getSlatMaterial(), null);

        SlatMaterial entity = new SlatMaterial();
        mapper.updateEntity(request, entity);
        return mapper.toResponse(slatMaterialRepository.save(entity));
    }

    @Transactional
    public SlatMaterialResponse update(Long id, SlatMaterialRequest request) {
        SlatMaterial entity = findEntityById(id);
        checkNoDuplicateCode(request.getSlatMaterial(), id);

        mapper.updateEntity(request, entity);
        return mapper.toResponse(slatMaterialRepository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        SlatMaterial entity = findEntityById(id);
        if (inventoryBatchRepository.existsBySlatMaterial_Id(id)) {
            throw new ConflictException("Không thể xóa: vẫn còn tồn kho tham chiếu đến loại thanh nan này");
        }
        slatMaterialRepository.delete(entity);
    }

    private void checkNoDuplicateCode(Long slatMaterialCode, Long excludeId) {
        slatMaterialRepository.findBySlatMaterial(slatMaterialCode)
                .filter(existing -> !existing.getId().equals(excludeId))
                .ifPresent(existing -> {
                    throw new ConflictException("Mã thanh nan đã tồn tại: " + slatMaterialCode);
                });
    }

    private SlatMaterial findEntityById(Long id) {
        return slatMaterialRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại thanh nan với id=" + id));
    }
}
