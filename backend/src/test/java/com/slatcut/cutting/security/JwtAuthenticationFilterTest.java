package com.slatcut.cutting.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.slatcut.cutting.AbstractIntegrationTest;
import com.slatcut.cutting.dto.UpdateUserRequest;
import com.slatcut.cutting.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Khóa lại một điểm dễ trôi: <b>quyền phải đọc từ CSDL mỗi request, không lấy từ claim trong token</b>.
 *
 * <p>Token sống 8 giờ. Nếu bộ lọc tin claim như trước đây thì hai thao tác quản trị quan trọng nhất
 * của màn quản lý người dùng đều chỉ là trang trí trong suốt quãng thời gian đó: người bị khóa vẫn
 * làm việc bình thường, còn ADMIN vừa bị hạ quyền vẫn tự tạo lại được tài khoản ADMIN mới.
 */
@AutoConfigureMockMvc
class JwtAuthenticationFilterTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserService userService;

    @Autowired
    private com.slatcut.cutting.repository.UserRepository userRepository;

    private Long idOf(String username) {
        return userRepository.findByUsername(username).orElseThrow().getId();
    }

    private static UpdateUserRequest updateRequest(String roleCode, boolean enabled) {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setRoleCode(roleCode);
        request.setEnabled(enabled);
        return request;
    }

    @Test
    void validToken_ofAnActiveAccount_isAccepted() throws Exception {
        mockMvc.perform(get("/api/v1/sales-orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken("planner", "PLANNER")))
                .andExpect(status().isOk());
    }

    /** Token vẫn còn hạn và chữ ký vẫn đúng, nhưng tài khoản đã bị khóa → coi như chưa đăng nhập. */
    @Test
    void tokenOfALockedAccount_isRejectedImmediately() throws Exception {
        String token = jwtService.generateToken("planner", "PLANNER");
        userService.update(idOf("planner"), updateRequest("PLANNER", false), "admin");

        mockMvc.perform(get("/api/v1/sales-orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Vai trò lấy từ CSDL chứ không từ claim: tài khoản đã bị hạ xuống PLANNER thì token cũ mang
     * claim ADMIN cũng không mở được endpoint dành riêng cho ADMIN nữa.
     */
    @Test
    void tokenStillClaimingAdmin_losesAdminPowerOnceTheAccountIsDemoted() throws Exception {
        userService.create(adminRequest());
        String token = jwtService.generateToken("admin_hai", "ADMIN");
        userService.update(idOf("admin_hai"), updateRequest("PLANNER", true), "admin");

        mockMvc.perform(post("/api/v1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin_ba\",\"password\":\"matkhau123\",\"roleCode\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    private static com.slatcut.cutting.dto.CreateUserRequest adminRequest() {
        var request = new com.slatcut.cutting.dto.CreateUserRequest();
        request.setUsername("admin_hai");
        request.setPassword("matkhau123");
        request.setRoleCode("ADMIN");
        return request;
    }
}
