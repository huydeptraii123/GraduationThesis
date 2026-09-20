package com.slatcut.cutting.service;

import com.slatcut.cutting.config.ConflictException;
import com.slatcut.cutting.config.ResourceNotFoundException;
import com.slatcut.cutting.domain.InventoryBatch;
import com.slatcut.cutting.domain.SlatGroup;
import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.dto.InventoryBatchRequest;
import com.slatcut.cutting.dto.InventoryBatchResponse;
import com.slatcut.cutting.dto.InventorySummaryResponse;
import com.slatcut.cutting.dto.PageResponse;
import com.slatcut.cutting.mapper.InventoryBatchMapper;
import com.slatcut.cutting.repository.InventoryBatchRepository;
import com.slatcut.cutting.repository.SlatMaterialRepository;
import com.slatcut.cutting.repository.spec.InventoryBatchSpecifications;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryBatchService {

    private final InventoryBatchRepository inventoryBatchRepository;
    private final SlatMaterialRepository slatMaterialRepository;
    private final InventoryBatchMapper mapper;

    public InventoryBatchService(
            InventoryBatchRepository inventoryBatchRepository,
            SlatMaterialRepository slatMaterialRepository,
            InventoryBatchMapper mapper) {
        this.inventoryBatchRepository = inventoryBatchRepository;
        this.slatMaterialRepository = slatMaterialRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryBatchResponse> getPage(String keyword, SlatGroup slatGroup, Pageable pageable) {
        return PageResponse.of(
                inventoryBatchRepository.findAll(InventoryBatchSpecifications.filter(keyword, slatGroup), pageable),
                mapper::toResponse);
    }

    /** Quy đổi mm → mét ngay ở backend để mọi nơi hiển thị cùng một con số đã làm tròn như nhau. */
    @Transactional(readOnly = true)
    public InventorySummaryResponse getSummary() {
        InventoryBatchRepository.InventoryTotals totals = inventoryBatchRepository.sumInventoryTotals();
        return new InventorySummaryResponse(
                totals.getBatchCount(),
                totals.getTotalSticks(),
                BigDecimal.valueOf(totals.getTotalLengthMm()).divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP));
    }

    @Transactional(readOnly = true)
    public InventoryBatchResponse getById(Long id) {
        return mapper.toResponse(findEntityById(id));
    }

    @Transactional
    public InventoryBatchResponse create(InventoryBatchRequest request) {
        SlatMaterial slatMaterial = findSlatMaterialById(request.getSlatMaterialId());
        checkNoDuplicateBatch(request, null);

        InventoryBatch entity = new InventoryBatch();
        entity.setSlatMaterial(slatMaterial);
        mapper.updateEntity(request, entity);
        return mapper.toResponse(inventoryBatchRepository.save(entity));
    }

    @Transactional
    public InventoryBatchResponse update(Long id, InventoryBatchRequest request) {
        InventoryBatch entity = findEntityById(id);
        SlatMaterial slatMaterial = findSlatMaterialById(request.getSlatMaterialId());
        checkNoDuplicateBatch(request, id);

        entity.setSlatMaterial(slatMaterial);
        mapper.updateEntity(request, entity);
        return mapper.toResponse(inventoryBatchRepository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        inventoryBatchRepository.delete(findEntityById(id));
    }

    private void checkNoDuplicateBatch(InventoryBatchRequest request, Long excludeId) {
        inventoryBatchRepository
                .findBySlatMaterial_IdAndDoDaiThanhMm(request.getSlatMaterialId(), request.getDoDaiThanhMm())
                .filter(existing -> !existing.getId().equals(excludeId))
                .ifPresent(existing -> {
                    throw new ConflictException(
                            "Đã tồn tại lô tồn kho cho loại thanh nan này ở độ dài " + request.getDoDaiThanhMm() + "mm");
                });
    }

    private InventoryBatch findEntityById(Long id) {
        return inventoryBatchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lô tồn kho với id=" + id));
    }

    private SlatMaterial findSlatMaterialById(Long id) {
        return slatMaterialRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy loại thanh nan với id=" + id));
    }
}
