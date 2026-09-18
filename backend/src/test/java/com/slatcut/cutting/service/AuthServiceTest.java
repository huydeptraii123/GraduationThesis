package com.slatcut.cutting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.slatcut.cutting.AbstractIntegrationTest;
import com.slatcut.cutting.config.ConflictException;
import com.slatcut.cutting.config.ResourceNotFoundException;
import com.slatcut.cutting.dto.ChangePasswordRequest;
import com.slatcut.cutting.dto.LoginRequest;
import com.slatcut.cutting.dto.LoginResponse;
import com.slatcut.cutting.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;

/** Tài khoản {@code planner} và mật khẩu gốc đến từ Flyway {@code V2__seed_default_users.sql}. */
class AuthServiceTest extends AbstractIntegrationTest {

    private static final String PLANNER = "planner";
    private static final String SEEDED_PASSWORD = "planner123!";

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    private static ChangePasswordRequest changeRequest(String current, String next) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword(current);
        request.setNewPassword(next);
        return request;
    }

    private LoginResponse login(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return authService.login(request);
    }

    @Test
    void login_returnsTokenAndRoleOfTheAccount() {
        LoginResponse response = login(PLANNER, SEEDED_PASSWORD);

        assertThat(response.token()).isNotBlank();
        assertThat(response.username()).isEqualTo(PLANNER);
        assertThat(response.role()).isEqualTo("PLANNER");
        assertThat(response.expiresInMs()).isPositive();
    }

    @Test
    void changePassword_replacesTheOldPasswordEverywhere() {
        authService.changePassword(PLANNER, changeRequest(SEEDED_PASSWORD, "matkhaumoi456"));

        login(PLANNER, "matkhaumoi456");
        assertThatThrownBy(() -> login(PLANNER, SEEDED_PASSWORD)).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void changePassword_rejectsAWrongCurrentPassword() {
        assertThatThrownBy(() -> authService.changePassword(PLANNER, changeRequest("sai-mat-khau", "matkhaumoi456")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("hiện tại");

        // Mật khẩu cũ phải còn nguyên hiệu lực — một lần gõ sai không được làm hỏng tài khoản.
        login(PLANNER, SEEDED_PASSWORD);
    }

    /**
     * Mật khẩu mới luôn được băm lại. Nếu vô tình lưu thẳng chuỗi thô thì test đăng nhập ở trên vẫn
     * xanh (mật khẩu thô không khớp bất kỳ hash nào nên chỉ lỗi ở chiều ngược lại) — nên kiểm riêng.
     */
    @Test
    void changePassword_storesAHashNeverThePlainText() {
        authService.changePassword(PLANNER, changeRequest(SEEDED_PASSWORD, "matkhaumoi456"));

        assertThat(userRepository.findByUsername(PLANNER).orElseThrow().getPasswordHash())
                .isNotEqualTo("matkhaumoi456")
                .startsWith("$2");
    }

    @Test
    void changePassword_unknownAccount_throwsNotFound() {
        assertThatThrownBy(() ->
                        authService.changePassword("khong-ton-tai", changeRequest(SEEDED_PASSWORD, "matkhaumoi456")))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
