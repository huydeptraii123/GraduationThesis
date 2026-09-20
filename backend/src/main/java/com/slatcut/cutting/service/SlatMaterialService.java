package com.slatcut.cutting.service;

import com.slatcut.cutting.config.ConflictException;
import com.slatcut.cutting.config.ResourceNotFoundException;
import com.slatcut.cutting.domain.SlatGroup;
import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.dto.PageResponse;
import com.slatcut.cutting.dto.SlatMaterialRequest;
import com.slatcut.cutting.dto.SlatMaterialResponse;
import com.slatcut.cutting.mapper.SlatMaterialMapper;
import com.slatcut.cutting.repository.BomItemRepository;
import com.slatcut.cutting.repository.InventoryBatchRepository;
import com.slatcut.cutting.repository.SlatMaterialRepository;
import com.slatcut.cutting.repository.spec.SlatMaterialSpecifications;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SlatMaterialService {

    private final SlatMaterialRepository slatMaterialRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final BomItemRepository bomItemRepository;
    private final SlatMaterialMapper mapper;

    public SlatMaterialService(
            SlatMaterialRepository slatMaterialRepository,
            InventoryBatchRepository inventoryBatchRepository,
            BomItemRepository bomItemRepository,
            SlatMaterialMapper mapper) {
        this.slatMaterialRepository = slatMaterialRepository;
        this.inventoryBatchRepository = inventoryBatchRepository;
        this.bomItemRepository = bomItemRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<SlatMaterialResponse> getPage(String keyword, SlatGroup slatGroup, Pageable pageable) {
        return PageResponse.of(
                slatMaterialRepository.findAll(SlatMaterialSpecifications.filter(keyword, slatGroup), pageable),
                mapper::toResponse);
    }

    /**
     * Danh mục đầy đủ, không phân trang — dành riêng cho dropdown và cho việc tra cứu
     * mã/nhóm vật tư ở các bảng khác (lô tồn kho, định mức BOM). Phân trang chỗ này sẽ làm mất lựa
     * chọn trong form và làm sai nhãn nhóm ở những dòng không nằm trong trang đầu.
     */
    @Transactional(readOnly = true)
    public List<SlatMaterialResponse> getAllOptions() {
        return slatMaterialRepository.findAll(Sort.by("slatMaterial")).stream()
                .map(mapper::toResponse)
                .toList();
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
        if (bomItemRepository.existsBySlatMaterial_Id(id)) {
            throw new ConflictException("Không thể xóa: vẫn còn định mức BOM tham chiếu đến loại thanh nan này");
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
