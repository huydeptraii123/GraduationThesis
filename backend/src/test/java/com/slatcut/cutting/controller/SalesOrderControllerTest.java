package com.slatcut.cutting.controller;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.slatcut.cutting.AbstractIntegrationTest;
import com.slatcut.cutting.domain.BomItem;
import com.slatcut.cutting.domain.Customer;
import com.slatcut.cutting.domain.DoorProduct;
import com.slatcut.cutting.domain.SalesOrder;
import com.slatcut.cutting.domain.SlatGroup;
import com.slatcut.cutting.domain.SlatMaterial;
import com.slatcut.cutting.repository.BomItemRepository;
import com.slatcut.cutting.repository.CustomerRepository;
import com.slatcut.cutting.repository.DoorProductRepository;
import com.slatcut.cutting.repository.SalesOrderRepository;
import com.slatcut.cutting.repository.SlatMaterialRepository;
import com.slatcut.cutting.security.JwtService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Bộ lọc trạng thái xử lý nhìn từ phía HTTP. SalesOrderServiceTest đã khóa cách suy ra từng trạng
 * thái; lớp này khóa điều mà test tầng service không thấy được: tham số trên URL có thật sự tới
 * được service hay không. Spring bỏ qua im lặng mọi tham số nó không ánh xạ, nên một tên tham số gõ
 * sai ở controller vẫn trả 200 với toàn bộ danh sách — vì vậy phép đo ở đây là số dòng PHẢI GIẢM.
 */
@AutoConfigureMockMvc
class SalesOrderControllerTest extends AbstractIntegrationTest {

    private static final String KEYWORD = "APILOCTT";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private DoorProductRepository doorProductRepository;

    @Autowired
    private SlatMaterialRepository slatMaterialRepository;

    @Autowired
    private BomItemRepository bomItemRepository;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    /** Một đơn chưa xử lý (mẫu cửa có định mức) và hai đơn bị chặn (mẫu cửa không có dòng nào). */
    @BeforeEach
    void persistOrders() {
        Customer customer = new Customer();
        customer.setCustomer(91400001L);
        customer.setCustomerName("Khách lọc qua API");
        customerRepository.save(customer);

        DoorProduct withBom = persistDoorProduct(83400001L);
        SlatMaterial bottomBar = new SlatMaterial();
        bottomBar.setSlatMaterial(70420001L);
        bottomBar.setSlatMaterialName("Thanh đáy lọc qua API");
        bottomBar.setSlatGroup(SlatGroup.BOTTOM_BAR);
        slatMaterialRepository.save(bottomBar);
        BomItem bomItem = new BomItem();
        bomItem.setDoorProduct(withBom);
        bomItem.setSlatMaterial(bottomBar);
        bomItemRepository.save(bomItem);
        DoorProduct withoutBom = persistDoorProduct(83400002L);

        persistOrder(1, customer, withBom);
        persistOrder(2, customer, withoutBom);
        persistOrder(3, customer, withoutBom);
    }

    @Test
    void getAll_processingStatusParameterNarrowsResult() throws Exception {
        mockMvc.perform(get("/api/v1/sales-orders").param("keyword", KEYWORD).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(get("/api/v1/sales-orders")
                        .param("keyword", KEYWORD)
                        .param("processingStatus", "BLOCKED")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].processingStatus", everyItem(is("BLOCKED"))));

        mockMvc.perform(get("/api/v1/sales-orders")
                        .param("keyword", KEYWORD)
                        .param("processingStatus", "PENDING")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].item").value(1))
                .andExpect(jsonPath("$.content[0].processingStatus").value("PENDING"));
    }

    /** Giá trị lạ phải bị từ chối, không được rơi về "không lọc" rồi trả cả danh sách. */
    @Test
    void getAll_unknownProcessingStatusIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/sales-orders")
                        .param("keyword", KEYWORD)
                        .param("processingStatus", "KHONG_HOP_LE")
                        .header("Authorization", bearer()))
                .andExpect(status().isBadRequest());
    }

    private String bearer() {
        return "Bearer " + jwtService.generateToken("planner", "PLANNER");
    }

    private DoorProduct persistDoorProduct(long material) {
        DoorProduct entity = new DoorProduct();
        entity.setMaterial(material);
        entity.setDoorMaterialName("Cửa cuốn " + material);
        entity.setMauSac("#15");
        return doorProductRepository.save(entity);
    }

    private void persistOrder(int item, Customer customer, DoorProduct doorProduct) {
        SalesOrder entity = new SalesOrder();
        entity.setYcsx(KEYWORD);
        entity.setItem(item);
        entity.setCustomer(customer);
        entity.setDoorProduct(doorProduct);
        entity.setChieuCaoDh(new BigDecimal("2.350"));
        entity.setChieuRongDh(new BigDecimal("3.150"));
        entity.setReqdDeliveryDate(LocalDate.of(2026, 10, 20));
        salesOrderRepository.save(entity);
    }
}
