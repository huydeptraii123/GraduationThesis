package com.slatcut.cutting.service;

import com.slatcut.cutting.config.ConflictException;
import com.slatcut.cutting.config.ResourceNotFoundException;
import com.slatcut.cutting.domain.DoorProduct;
import com.slatcut.cutting.dto.DoorProductRequest;
import com.slatcut.cutting.dto.DoorProductResponse;
import com.slatcut.cutting.mapper.DoorProductMapper;
import com.slatcut.cutting.repository.BomItemRepository;
import com.slatcut.cutting.repository.DoorProductRepository;
import com.slatcut.cutting.repository.SalesOrderRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoorProductService {

    private final DoorProductRepository doorProductRepository;
    private final BomItemRepository bomItemRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final DoorProductMapper mapper;

    public DoorProductService(
            DoorProductRepository doorProductRepository,
            BomItemRepository bomItemRepository,
            SalesOrderRepository salesOrderRepository,
            DoorProductMapper mapper) {
        this.doorProductRepository = doorProductRepository;
        this.bomItemRepository = bomItemRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<DoorProductResponse> getAll() {
        return doorProductRepository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public DoorProductResponse getById(Long id) {
        return mapper.toResponse(findEntityById(id));
    }

    @Transactional
    public DoorProductResponse create(DoorProductRequest request) {
        checkNoDuplicateProduct(request, null);

        DoorProduct entity = new DoorProduct();
        mapper.updateEntity(request, entity);
        return mapper.toResponse(doorProductRepository.save(entity));
    }

    @Transactional
    public DoorProductResponse update(Long id, DoorProductRequest request) {
        DoorProduct entity = findEntityById(id);
        checkNoDuplicateProduct(request, id);

        mapper.updateEntity(request, entity);
        return mapper.toResponse(doorProductRepository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        DoorProduct entity = findEntityById(id);
        if (bomItemRepository.existsByDoorProduct_Id(id)) {
            throw new ConflictException("Không thể xóa: vẫn còn định mức BOM tham chiếu đến mẫu cửa này");
        }
        if (salesOrderRepository.existsByDoorProduct_Id(id)) {
            throw new ConflictException("Không thể xóa: vẫn còn đơn hàng tham chiếu đến mẫu cửa này");
        }
        doorProductRepository.delete(entity);
    }

    private void checkNoDuplicateProduct(DoorProductRequest request, Long excludeId) {
        doorProductRepository.findByMaterialAndMauSac(request.getMaterial(), request.getMauSac())
                .filter(existing -> !existing.getId().equals(excludeId))
                .ifPresent(existing -> {
                    throw new ConflictException(
                            "Mẫu cửa đã tồn tại: mã " + request.getMaterial() + ", màu " + request.getMauSac());
                });
    }

    private DoorProduct findEntityById(Long id) {
        return doorProductRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mẫu cửa với id=" + id));
    }
}
