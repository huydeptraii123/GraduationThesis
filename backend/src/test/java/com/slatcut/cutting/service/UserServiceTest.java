package com.slatcut.cutting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.slatcut.cutting.AbstractIntegrationTest;
import com.slatcut.cutting.config.ConflictException;
import com.slatcut.cutting.config.ResourceNotFoundException;
import com.slatcut.cutting.dto.CreateUserRequest;
import com.slatcut.cutting.dto.LoginRequest;
import com.slatcut.cutting.dto.PageResponse;
import com.slatcut.cutting.dto.ResetPasswordRequest;
import com.slatcut.cutting.dto.UpdateUserRequest;
import com.slatcut.cutting.dto.UserResponse;
import com.slatcut.cutting.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;

/**
 * Tài khoản {@code admin} và {@code planner} dùng trong các test dưới đây đến từ Flyway
 * {@code V2__seed_default_users.sql}; {@code admin} là ADMIN đang hoạt động duy nhất sau khi seed,
 * nên nó cũng chính là "ADMIN cuối cùng" mà các chốt chặn phải bảo vệ.
 */
class UserServiceTest extends AbstractIntegrationTest {

    private static final String SEEDED_ADMIN = "admin";
    private static final String SEEDED_PLANNER = "planner";

    @Autowired
    private UserService service;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    private static CreateUserRequest createRequest(String username, String password, String roleCode) {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername(username);
        request.setPassword(password);
        request.setRoleCode(roleCode);
        return request;
    }

    private static UpdateUserRequest updateRequest(String roleCode, boolean enabled) {
        UpdateUserRequest request = new UpdateUserRequest();
        request.setRoleCode(roleCode);
        request.setEnabled(enabled);
        return request;
    }

    private Long idOf(String username) {
        return userRepository.findByUsername(username).orElseThrow().getId();
    }

    private void login(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        authService.login(request);
    }

    @Test
    void create_storesHashedPasswordAndAllowsLoginWithIt() {
        UserResponse response = service.create(createRequest("planner_moi", "matkhau123", "PLANNER"));

        assertThat(response.id()).isNotNull();
        assertThat(response.username()).isEqualTo("planner_moi");
        assertThat(response.roleCode()).isEqualTo("PLANNER");
        assertThat(response.enabled()).isTrue();
        assertThat(response.createdAt()).isNotNull();

        // Mật khẩu phải được băm, không lưu thẳng — và bản băm đó phải thật sự đăng nhập được.
        assertThat(userRepository.findByUsername("planner_moi").orElseThrow().getPasswordHash())
                .isNotEqualTo("matkhau123")
                .startsWith("$2");
        login("planner_moi", "matkhau123");
    }

    @Test
    void create_rejectsDuplicateUsername() {
        assertThatThrownBy(() -> service.create(createRequest(SEEDED_PLANNER, "matkhau123", "PLANNER")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(SEEDED_PLANNER);
    }

    @Test
    void create_rejectsUnknownRoleCode() {
        assertThatThrownBy(() -> service.create(createRequest("nguoi_la", "matkhau123", "SUPERUSER")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("SUPERUSER");
    }

    @Test
    void update_changesRoleAndKeepsAccountUsable() {
        service.create(createRequest("planner_moi", "matkhau123", "PLANNER"));

        UserResponse response = service.update(idOf("planner_moi"), updateRequest("ADMIN", true), SEEDED_ADMIN);

        assertThat(response.roleCode()).isEqualTo("ADMIN");
        assertThat(response.enabled()).isTrue();
        login("planner_moi", "matkhau123");
    }

    /** Khóa tài khoản phải chặn được đăng nhập thật, không chỉ đổi một cờ trong bảng. */
    @Test
    void update_lockedAccountCannotLogInAnyMore() {
        service.create(createRequest("planner_moi", "matkhau123", "PLANNER"));
        login("planner_moi", "matkhau123");

        service.update(idOf("planner_moi"), updateRequest("PLANNER", false), SEEDED_ADMIN);

        assertThatThrownBy(() -> login("planner_moi", "matkhau123")).isInstanceOf(DisabledException.class);
    }

    @Test
    void update_rejectsLockingYourself() {
        assertThatThrownBy(() -> service.update(idOf(SEEDED_ADMIN), updateRequest("ADMIN", false), SEEDED_ADMIN))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("tự khóa");
    }

    @Test
    void update_rejectsChangingYourOwnRole() {
        assertThatThrownBy(() -> service.update(idOf(SEEDED_ADMIN), updateRequest("PLANNER", true), SEEDED_ADMIN))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("tự đổi vai trò");
    }

    /**
     * Chốt "ADMIN cuối cùng" khác chốt tự-khóa: người bị khóa ở đây là ADMIN <i>khác</i>, nên chốt
     * tự-khóa không đụng tới — nếu thiếu chốt này hệ thống sẽ hết sạch quản trị viên.
     */
    @Test
    void update_rejectsLockingTheLastActiveAdmin() {
        service.create(createRequest("admin_hai", "matkhau123", "ADMIN"));
        service.update(idOf(SEEDED_ADMIN), updateRequest("ADMIN", false), "admin_hai");

        assertThatThrownBy(() -> service.update(idOf("admin_hai"), updateRequest("ADMIN", false), SEEDED_ADMIN))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cuối cùng");
    }

    /** Hạ quyền cũng làm mất một ADMIN hoạt động y như khóa, nên phải bị chặn bằng cùng một luật. */
    @Test
    void update_rejectsDemotingTheLastActiveAdmin() {
        assertThatThrownBy(() -> service.update(idOf(SEEDED_ADMIN), updateRequest("PLANNER", true), "admin_hai"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cuối cùng");
    }

    /** Khi vẫn còn ADMIN hoạt động khác thì thao tác phải đi qua bình thường — chốt chặn không được quá tay. */
    @Test
    void update_allowsLockingAnAdminWhileAnotherActiveAdminRemains() {
        service.create(createRequest("admin_hai", "matkhau123", "ADMIN"));

        UserResponse response = service.update(idOf("admin_hai"), updateRequest("ADMIN", false), SEEDED_ADMIN);

        assertThat(response.enabled()).isFalse();
        assertThat(userRepository.findByRole_CodeAndEnabledTrue("ADMIN")).hasSize(1);
    }

    /** Khóa một tài khoản đã bị khóa sẵn không làm giảm số ADMIN hoạt động, nên không được chặn nhầm. */
    @Test
    void update_allowsEditingAnAlreadyLockedAdmin() {
        service.create(createRequest("admin_hai", "matkhau123", "ADMIN"));
        service.update(idOf("admin_hai"), updateRequest("ADMIN", false), SEEDED_ADMIN);

        UserResponse response = service.update(idOf("admin_hai"), updateRequest("PLANNER", false), SEEDED_ADMIN);

        assertThat(response.roleCode()).isEqualTo("PLANNER");
    }

    @Test
    void resetPassword_makesTheOldPasswordStopWorking() {
        service.create(createRequest("planner_moi", "matkhau123", "PLANNER"));

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setNewPassword("matkhaumoi456");
        service.resetPassword(idOf("planner_moi"), request);

        login("planner_moi", "matkhaumoi456");
        assertThatThrownBy(() -> login("planner_moi", "matkhau123")).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void getPage_returnsEveryAccountSortedByUsername() {
        service.create(createRequest("aaa_dau_bang", "matkhau123", "PLANNER"));

        List<String> usernames = service.getPage(null, null, null, PageRequest.of(0, 20, Sort.by("username")))
                .content()
                .stream()
                .map(UserResponse::username)
                .toList();

        assertThat(usernames).containsExactly("aaa_dau_bang", SEEDED_ADMIN, SEEDED_PLANNER);
    }

    @Test
    void getPage_splitsResultAcrossPages() {
        for (int index = 0; index < 5; index++) {
            service.create(createRequest("pt_taikhoan_%d".formatted(index), "matkhau123", "PLANNER"));
        }
        Pageable byUsername = PageRequest.of(0, 2, Sort.by("username"));

        PageResponse<UserResponse> first = service.getPage("pt_taikhoan", null, null, byUsername);
        PageResponse<UserResponse> second = service.getPage("pt_taikhoan", null, null, byUsername.withPage(1));
        PageResponse<UserResponse> last = service.getPage("pt_taikhoan", null, null, byUsername.withPage(2));

        assertThat(first.totalElements()).isEqualTo(5);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.content()).extracting(UserResponse::username)
                .containsExactly("pt_taikhoan_0", "pt_taikhoan_1");
        assertThat(second.content()).extracting(UserResponse::username)
                .containsExactly("pt_taikhoan_2", "pt_taikhoan_3");
        assertThat(last.content()).extracting(UserResponse::username).containsExactly("pt_taikhoan_4");
    }

    @Test
    void getPage_filtersByRoleAndEnabledFlag() {
        service.create(createRequest("loc_quanly", "matkhau123", "ADMIN"));
        service.create(createRequest("loc_kehoach", "matkhau123", "PLANNER"));
        service.update(idOf("loc_kehoach"), updateRequest("PLANNER", false), SEEDED_ADMIN);
        Pageable firstPage = PageRequest.of(0, 20, Sort.by("username"));

        PageResponse<UserResponse> admins = service.getPage("loc_", "ADMIN", null, firstPage);
        PageResponse<UserResponse> disabled = service.getPage("loc_", null, false, firstPage);

        assertThat(admins.content()).extracting(UserResponse::username).containsExactly("loc_quanly");
        assertThat(disabled.content()).extracting(UserResponse::username).containsExactly("loc_kehoach");
    }

    /**
     * BCrypt chỉ băm 72 byte đầu, trong khi {@code @Size(max = 72)} đếm KÝ TỰ — nên có một khoảng
     * lọt lưới: mật khẩu tiếng Việt có dấu (3 byte/chữ trong UTF-8) qua được @Size mà vẫn vượt 72
     * byte, và phần đuôi bị cắt lặng lẽ.
     *
     * <p>Chuỗi thử phải nằm ĐÚNG trong khoảng đó — không quá 72 ký tự nhưng hơn 72 byte. Dùng chuỗi
     * dài hơn 72 ký tự thì @Size đã chặn từ tầng controller, và test sẽ kiểm một nhánh mà request
     * thật không bao giờ chạm tới.
     */
    @Test
    void create_rejectsAPasswordLongerThanWhatBcryptActuallyHashes() {
        String withinCharLimitButOverByteLimit = "ữ".repeat(30);

        assertThat(withinCharLimitButOverByteLimit.length()).isLessThanOrEqualTo(72);
        assertThat(withinCharLimitButOverByteLimit.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isGreaterThan(72);
        assertThatThrownBy(() -> service.create(createRequest("nguoi_moi", withinCharLimitButOverByteLimit, "PLANNER")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("byte");
    }

    @Test
    void getById_missingAccount_throwsNotFound() {
        assertThatThrownBy(() -> service.getById(999_999_999L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
