package com.slatcut.cutting.service;

import com.slatcut.cutting.config.ConflictException;
import com.slatcut.cutting.config.ResourceNotFoundException;
import com.slatcut.cutting.domain.Customer;
import com.slatcut.cutting.domain.DoorProduct;
import com.slatcut.cutting.domain.SalesOrder;
import com.slatcut.cutting.domain.SalesOrderProcessingStatus;
import com.slatcut.cutting.dto.PageResponse;
import com.slatcut.cutting.dto.SalesOrderRequest;
import com.slatcut.cutting.dto.SalesOrderResponse;
import com.slatcut.cutting.mapper.SalesOrderMapper;
import com.slatcut.cutting.repository.CustomerRepository;
import com.slatcut.cutting.repository.DoorProductRepository;
import com.slatcut.cutting.repository.SalesOrderRepository;
import com.slatcut.cutting.repository.spec.SalesOrderSpecifications;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
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
            String keyword,
            Long customerId,
            LocalDate deliveryFrom,
            LocalDate deliveryTo,
            SalesOrderProcessingStatus status,
            Pageable pageable) {
        Page<SalesOrder> page = salesOrderRepository.findAll(
                SalesOrderSpecifications.filter(keyword, customerId, deliveryFrom, deliveryTo, status), pageable);
        if (status != null) {
            // Bộ lọc chính là statusIs(status), nên mọi dòng trả về mang đúng trạng thái đó — hỏi lại
            // chỉ tốn thêm truy vấn để ra cùng một câu trả lời.
            return PageResponse.of(page, order -> mapper.toResponse(order, status));
        }
        Map<Long, SalesOrderProcessingStatus> statuses = resolveStatuses(page.getContent());
        return PageResponse.of(page, order -> mapper.toResponse(order, statuses.get(order.getId())));
    }

    @Transactional(readOnly = true)
    public SalesOrderResponse getById(Long id) {
        return toResponse(findEntityById(id));
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
        return toResponse(salesOrderRepository.save(entity));
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
        return toResponse(salesOrderRepository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        SalesOrder entity = findEntityById(id);
        checkNotApproved(entity, "xóa");
        salesOrderRepository.delete(entity);
    }

    private SalesOrderResponse toResponse(SalesOrder order) {
        return mapper.toResponse(order, resolveStatuses(List.of(order)).get(order.getId()));
    }

    /**
     * Trạng thái xử lý của từng đơn trong danh sách — tối đa hai truy vấn cho cả danh sách, không
     * phải hai truy vấn cho mỗi dòng. Câu hỏi "bị chặn không" chỉ gửi cho các đơn chưa duyệt, câu
     * hỏi "thiếu không" chỉ gửi cho các đơn đã duyệt; nhóm nào rỗng thì bỏ hẳn truy vấn của nhóm đó.
     *
     * <p>Hỏi lại chính {@link SalesOrderSpecifications#statusIs} chứ không viết điều kiện thứ hai ở
     * đây. Hai trạng thái phải dò bảng khác (bị chặn: định mức của mẫu cửa; thiếu: shortage_record)
     * lấy thẳng từ đó; hai trạng thái còn lại là phần bù của chúng trong nhóm chưa duyệt và nhóm
     * đã duyệt. Nhờ vậy đơn hiện "Đang bị chặn" trên cột luôn đúng là đơn mà bộ lọc "Đang bị chặn"
     * trả về.
     *
     * <p>Với {@code update}, câu truy vấn ở đây tự đẩy thay đổi mẫu cửa vừa gán xuống CSDL trước khi
     * chạy (flush tự động của Hibernate), nên trạng thái trả về phản ánh mẫu cửa MỚI.
     */
    private Map<Long, SalesOrderProcessingStatus> resolveStatuses(List<SalesOrder> orders) {
        Set<Long> blocked = idsMatching(
                orders.stream().filter(o -> o.getApprovedPlan() == null).map(SalesOrder::getId).toList(),
                SalesOrderProcessingStatus.BLOCKED);
        Set<Long> shortage = idsMatching(
                orders.stream().filter(o -> o.getApprovedPlan() != null).map(SalesOrder::getId).toList(),
                SalesOrderProcessingStatus.SHORTAGE);
        Map<Long, SalesOrderProcessingStatus> statuses = new HashMap<>();
        for (SalesOrder order : orders) {
            SalesOrderProcessingStatus status;
            if (order.getApprovedPlan() == null) {
                status = blocked.contains(order.getId())
                        ? SalesOrderProcessingStatus.BLOCKED
                        : SalesOrderProcessingStatus.PENDING;
            } else {
                status = shortage.contains(order.getId())
                        ? SalesOrderProcessingStatus.SHORTAGE
                        : SalesOrderProcessingStatus.SUFFICIENT;
            }
            statuses.put(order.getId(), status);
        }
        return statuses;
    }

    private Set<Long> idsMatching(List<Long> ids, SalesOrderProcessingStatus status) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        return salesOrderRepository
                .findAll(SalesOrderSpecifications.idIn(ids).and(SalesOrderSpecifications.statusIs(status)))
                .stream()
                .map(SalesOrder::getId)
                .collect(Collectors.toSet());
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
