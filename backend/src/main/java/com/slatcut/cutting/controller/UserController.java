package com.slatcut.cutting.controller;

import com.slatcut.cutting.dto.CreateUserRequest;
import com.slatcut.cutting.dto.ResetPasswordRequest;
import com.slatcut.cutting.dto.UpdateUserRequest;
import com.slatcut.cutting.dto.UserResponse;
import com.slatcut.cutting.service.UserService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quản lý tài khoản — toàn bộ dành riêng cho ADMIN, kể cả các phương thức đọc.
 *
 * <p>Đây là chỗ duy nhất trong hệ thống mà GET cũng bị giới hạn vai trò. Các module nghiệp vụ khác
 * mở GET cho mọi tài khoản đã đăng nhập vì dữ liệu ở đó (tồn kho, định mức, đơn hàng) là thứ cả hai
 * vai trò đều cần nhìn thấy để làm việc; danh sách tài khoản thì không — PLANNER không có việc gì
 * phải biết hệ thống có bao nhiêu quản trị viên và tên đăng nhập của họ là gì.
 *
 * <p>Không có DELETE: theo docs/use-case-diagram.md vòng đời tài khoản là tạo – sửa – khóa. Ngừng
 * truy cập bằng cách khóa, để dấu vết thao tác trên dữ liệu sản xuất vẫn còn chủ.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserResponse> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    /**
     * Sửa vai trò và/hoặc khóa–mở khóa.
     *
     * <p>{@link Principal} là tên đăng nhập lấy từ JWT đã xác thực, không phải dữ liệu client tự
     * khai — các chốt chặn "không tự khóa mình" ở tầng service dựa hoàn toàn vào giá trị này.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse update(
            @PathVariable Long id, @Valid @RequestBody UpdateUserRequest request, Principal principal) {
        return service.update(id, request, principal.getName());
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        service.resetPassword(id, request);
        return ResponseEntity.noContent().build();
    }
}
