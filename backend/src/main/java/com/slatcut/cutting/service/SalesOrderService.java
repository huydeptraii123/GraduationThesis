package com.slatcut.cutting.service;

import com.slatcut.cutting.config.ConflictException;
import com.slatcut.cutting.config.ResourceNotFoundException;
import com.slatcut.cutting.domain.Customer;
import com.slatcut.cutting.domain.DoorProduct;
import com.slatcut.cutting.domain.SalesOrder;
import com.slatcut.cutting.dto.PageResponse;
import com.slatcut.cutting.dto.SalesOrderRequest;
import com.slatcut.cutting.dto.SalesOrderResponse;
import com.slatcut.cutting.mapper.SalesOrderMapper;
import com.slatcut.cutting.repository.CuttingPlanDetailItemRepository;
import com.slatcut.cutting.repository.CustomerRepository;
import com.slatcut.cutting.repository.DoorProductRepository;
import com.slatcut.cutting.repository.SalesOrderRepository;
import com.slatcut.cutting.repository.ShortageRecordRepository;
import com.slatcut.cutting.repository.spec.SalesOrderSpecifications;
import java.time.LocalDate;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalesOrderService {

    private final SalesOrderRepository salesOrderRepository;
    private final CustomerRepository customerRepository;
    private final DoorProductRepository doorProductRepository;
    private final CuttingPlanDetailItemRepository cuttingPlanDetailItemRepository;
    private final ShortageRecordRepository shortageRecordRepository;
    private final SalesOrderMapper mapper;

    public SalesOrderService(
            SalesOrderRepository salesOrderRepository,
            CustomerRepository customerRepository,
            DoorProductRepository doorProductRepository,
            CuttingPlanDetailItemRepository cuttingPlanDetailItemRepository,
            ShortageRecordRepository shortageRecordRepository,
            SalesOrderMapper mapper) {
        this.salesOrderRepository = salesOrderRepository;
        this.customerRepository = customerRepository;
        this.doorProductRepository = doorProductRepository;
        this.cuttingPlanDetailItemRepository = cuttingPlanDetailItemRepository;
        this.shortageRecordRepository = shortageRecordRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesOrderResponse> getPage(
            String keyword, Long customerId, LocalDate deliveryFrom, LocalDate deliveryTo, Pageable pageable) {
        return PageResponse.of(
                salesOrderRepository.findAll(
                        SalesOrderSpecifications.filter(keyword, customerId, deliveryFrom, deliveryTo), pageable),
                mapper::toResponse);
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
        SalesOrder entity = findEntityById(id);
        if (cuttingPlanDetailItemRepository.existsBySalesOrder_Id(id)) {
            throw new ConflictException("Không thể xóa: đơn hàng đã có kết quả trong ít nhất 1 lần chạy phương án cắt");
        }
        if (shortageRecordRepository.existsBySalesOrder_Id(id)) {
            throw new ConflictException("Không thể xóa: đơn hàng đã bị đánh dấu thiếu vật tư trong ít nhất 1 lần chạy phương án cắt");
        }
        salesOrderRepository.delete(entity);
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
        if (request.getSalesDocument() == null || request.getSalesOrderItem() == null) {
            // Đơn tạo thủ công không có 2 giá trị SAP này — không có gì để kiểm tra trùng. Spring Data
            // JPA sẽ tự chuyển tham số null thành "IS NULL" trong query derivation nếu gọi thẳng xuống,
            // khiến 2 đơn thủ công khác nhau bị báo trùng nhầm dù MySQL cho phép nhiều NULL trong UNIQUE.
            return;
        }
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
