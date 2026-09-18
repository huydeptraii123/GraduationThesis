package com.slatcut.cutting.service;

import com.slatcut.cutting.config.ConflictException;
import com.slatcut.cutting.config.ResourceNotFoundException;
import com.slatcut.cutting.dto.ChangePasswordRequest;
import com.slatcut.cutting.dto.LoginRequest;
import com.slatcut.cutting.dto.PasswordRules;
import com.slatcut.cutting.dto.LoginResponse;
import com.slatcut.cutting.repository.UserRepository;
import com.slatcut.cutting.security.JwtProperties;
import com.slatcut.cutting.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            JwtService jwtService,
            JwtProperties jwtProperties,
            PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        var user = userRepository.findByUsername(request.getUsername()).orElseThrow();
        String roleCode = user.getRole().getCode();
        String token = jwtService.generateToken(user.getUsername(), roleCode);

        return new LoginResponse(token, user.getUsername(), roleCode, jwtProperties.getExpirationMs());
    }

    /**
     * Người dùng tự đổi mật khẩu của chính mình (ca sử dụng "Đổi mật khẩu cá nhân", dùng chung cho
     * cả hai vai trò).
     *
     * <p>{@code username} đến từ {@code Authentication#getName()}, tức chủ thể của JWT đã xác thực —
     * không có tham số nào cho phép nơi gọi chỉ định người khác, nên endpoint này không thể bị dùng
     * để đổi mật khẩu của tài khoản khác.
     *
     * <p>Sai mật khẩu hiện tại trả về 409 chứ không phải 401 dù bản chất là hỏng xác thực: 401 sẽ
     * kích hoạt interceptor phiên-hết-hạn ở frontend và đá người dùng về màn đăng nhập chỉ vì gõ
     * nhầm một ký tự, trong khi phiên của họ vẫn còn hiệu lực hoàn toàn.
     */
    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        var user = userRepository
                .findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản: " + username));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new ConflictException("Mật khẩu hiện tại không đúng");
        }
        PasswordRules.checkFitsBcrypt(request.getNewPassword());

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }
}
