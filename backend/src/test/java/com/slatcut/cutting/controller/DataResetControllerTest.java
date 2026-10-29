package com.slatcut.cutting.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.slatcut.cutting.AbstractIntegrationTest;
import com.slatcut.cutting.domain.Customer;
import com.slatcut.cutting.repository.CustomerRepository;
import com.slatcut.cutting.repository.SalesOrderRepository;
import com.slatcut.cutting.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Hợp đồng HTTP của {@code POST /api/v1/data-reset}. Phân quyền (chỉ ADMIN, không token → 401) nằm ở
 * {@link RolePermissionMatrixTest}; logic xóa từng bảng nằm ở {@code DataResetServiceTest}.
 */
@AutoConfigureMockMvc
class DataResetControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    private String adminToken() {
        return jwtService.generateToken("admin", "ADMIN");
    }

    private void persistCustomer() {
        Customer entity = new Customer();
        entity.setCustomer(91_234_567L);
        entity.setCustomerName("Khách hàng kiểm thử");
        customerRepository.save(entity);
    }

    /** Đếm trước/sau thay vì con số tuyệt đối: cơ sở dữ liệu kiểm thử dùng chung giữa các lớp. */
    @Test
    void reset_asAdminWithPhrase_returnsDeletedCountsPerTable() throws Exception {
        persistCustomer();
        long customersBefore = customerRepository.count();
        long salesOrdersBefore = salesOrderRepository.count();

        mockMvc.perform(post("/api/v1/data-reset")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"XOA DU LIEU\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customers").value((int) customersBefore))
                .andExpect(jsonPath("$.salesOrders").value((int) salesOrdersBefore));

        assertThat(customerRepository.count()).isZero();
    }

    /** Sai cụm xác nhận là 422 (giao diện chỉ cần hiện thông báo), không phải 400 hay 409. */
    @Test
    void reset_withWrongPhrase_isUnprocessable_andDeletesNothing() throws Exception {
        persistCustomer();
        long customersBefore = customerRepository.count();

        mockMvc.perform(post("/api/v1/data-reset")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"xoa du lieu\"}"))
                .andExpect(status().isUnprocessableContent());

        assertThat(customerRepository.count()).isEqualTo(customersBefore);
    }
}
