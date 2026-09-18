package com.slatcut.cutting.controller;

import com.slatcut.cutting.dto.ChangePasswordRequest;
import com.slatcut.cutting.dto.LoginRequest;
import com.slatcut.cutting.dto.LoginResponse;
import com.slatcut.cutting.service.AuthService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * Đổi mật khẩu của chính người đang đăng nhập — dùng chung cho cả ADMIN lẫn PLANNER.
     *
     * <p>{@code @PreAuthorize("isAuthenticated()")} không thừa dù bộ lọc bảo mật đã chặn request
     * không có token: {@code RolePermissionMatrixTest} soát mọi handler ghi trong bảng định tuyến
     * (không lọc theo tiền tố {@code /api/v1}) và bắt buộc mỗi handler tự khai báo quyền của mình,
     * để quyền của một endpoint đọc được ngay tại chỗ thay vì phải suy từ cấu hình ở nơi khác.
     */
    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request, Principal principal) {
        authService.changePassword(principal.getName(), request);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<String> handleAuthenticationException() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Sai tên đăng nhập hoặc mật khẩu");
    }
}
