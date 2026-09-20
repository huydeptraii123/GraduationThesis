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
import com.slatcut.cutting.repository.CustomerRepository;
import com.slatcut.cutting.repository.DoorProductRepository;
import com.slatcut.cutting.repository.SalesOrderRepository;
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
        checkNotApproved(entity, "sửa");
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
        checkNotApproved(entity, "xóa");
        salesOrderRepository.delete(entity);
    }

    /**
     * Đơn đã duyệt là bất biến (docs/requirements-functional.md Nhóm 1): nan của bộ cửa đó đã cắt
     * theo đúng kích thước đang lưu và đã ra khỏi kho, nên sửa chỉ làm hồ sơ lệch với vật tư thực
     * tế mà không khiến bộ cửa được cắt lại, còn xóa thì làm mất dấu vết của phương án đã ghi nhận.
     *
     * <p>Một câu hỏi duy nhất thay cho việc dò hai bảng con như trước: cách cũ bỏ lọt đơn đã duyệt
     * mà không còn dòng kết quả nào.
     */
    private static void checkNotApproved(SalesOrder entity, String action) {
        if (entity.getApprovedPlan() != null) {
            throw new ConflictException("Không thể " + action + ": đơn hàng đã thuộc phương án cắt #"
                    + entity.getApprovedPlan().getId() + " đã được duyệt");
        }
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
