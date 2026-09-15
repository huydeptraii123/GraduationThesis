package com.slatcut.cutting.service;

import com.slatcut.cutting.config.ConflictException;
import com.slatcut.cutting.config.ResourceNotFoundException;
import com.slatcut.cutting.domain.Customer;
import com.slatcut.cutting.domain.DoorProduct;
import com.slatcut.cutting.domain.SalesOrder;
import com.slatcut.cutting.dto.SalesOrderRequest;
import com.slatcut.cutting.dto.SalesOrderResponse;
import com.slatcut.cutting.mapper.SalesOrderMapper;
import com.slatcut.cutting.repository.CustomerRepository;
import com.slatcut.cutting.repository.DoorProductRepository;
import com.slatcut.cutting.repository.SalesOrderRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalesOrderService {

    private final SalesOrderRepository salesOrderRepository;
    private final CustomerRepository customerRepository;
    private final DoorProductRepository doorProductRepository;
    private final SalesOrderMapper mapper;

    public SalesOrderService(
            SalesOrderRepository salesOrderRepository,
            CustomerRepository customerRepository,
            DoorProductRepository doorProductRepository,
            SalesOrderMapper mapper) {
        this.salesOrderRepository = salesOrderRepository;
        this.customerRepository = customerRepository;
        this.doorProductRepository = doorProductRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<SalesOrderResponse> getAll() {
        return salesOrderRepository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SalesOrderResponse getById(Long id) {
        return mapper.toResponse(findEntityById(id));
    }

    @Transactional
    public SalesOrderResponse create(SalesOrderRequest request) {
        Customer customer = findCustomerById(request.getCustomerId());
        DoorProduct doorProduct = findDoorProductById(request.getDoorProductId());
        checkNoDuplicateYcsxItem(request, null);
        checkNoDuplicateSalesDocumentItem(request, null);

        SalesOrder entity = new SalesOrder();
        entity.setCustomer(customer);
        entity.setDoorProduct(doorProduct);
        mapper.updateEntity(request, entity);
        return mapper.toResponse(salesOrderRepository.save(entity));
    }

    @Transactional
    public SalesOrderResponse update(Long id, SalesOrderRequest request) {
        SalesOrder entity = findEntityById(id);
        Customer customer = findCustomerById(request.getCustomerId());
        DoorProduct doorProduct = findDoorProductById(request.getDoorProductId());
        checkNoDuplicateYcsxItem(request, id);
        checkNoDuplicateSalesDocumentItem(request, id);

        entity.setCustomer(customer);
        entity.setDoorProduct(doorProduct);
        mapper.updateEntity(request, entity);
        return mapper.toResponse(salesOrderRepository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        // TODO(tuần 9): thêm guard existsBySalesOrder_Id khi CuttingPlanDetailItem/ShortageRecord
        // đã có, chặn xóa đơn đã có kết quả cắt/thiếu vật tư tham chiếu (yêu cầu Nhóm 1).
        salesOrderRepository.delete(findEntityById(id));
    }

    private void checkNoDuplicateYcsxItem(SalesOrderRequest request, Long excludeId) {
        salesOrderRepository
                .findByYcsxAndItem(request.getYcsx(), request.getItem())
                .filter(existing -> !existing.getId().equals(excludeId))
                .ifPresent(existing -> {
                    throw new ConflictException(
                            "Đơn hàng đã tồn tại: ycsx " + request.getYcsx() + ", z_item " + request.getItem());
                });
    }

    private void checkNoDuplicateSalesDocumentItem(SalesOrderRequest request, Long excludeId) {
        salesOrderRepository
                .findBySalesDocumentAndSalesOrderItem(request.getSalesDocument(), request.getSalesOrderItem())
                .filter(existing -> !existing.getId().equals(excludeId))
                .ifPresent(existing -> {
                    throw new ConflictException("Đơn hàng đã tồn tại: sales_document " + request.getSalesDocument()
                            + ", sales_order_item " + request.getSalesOrderItem());
                });
    }

    private SalesOrder findEntityById(Long id) {
        return salesOrderRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với id=" + id));
    }

    private Customer findCustomerById(Long id) {
        return customerRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khách hàng với id=" + id));
    }

    private DoorProduct findDoorProductById(Long id) {
        return doorProductRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mẫu cửa với id=" + id));
    }
}
