package com.slatcut.cutting.service;

import com.slatcut.cutting.config.ConflictException;
import com.slatcut.cutting.config.ResourceNotFoundException;
import com.slatcut.cutting.domain.Role;
import com.slatcut.cutting.domain.User;
import com.slatcut.cutting.dto.CreateUserRequest;
import com.slatcut.cutting.dto.PageResponse;
import com.slatcut.cutting.dto.PasswordRules;
import com.slatcut.cutting.dto.ResetPasswordRequest;
import com.slatcut.cutting.dto.UpdateUserRequest;
import com.slatcut.cutting.dto.UserResponse;
import com.slatcut.cutting.mapper.UserMapper;
import com.slatcut.cutting.repository.RoleRepository;
import com.slatcut.cutting.repository.UserRepository;
import com.slatcut.cutting.repository.spec.UserSpecifications;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quản lý tài khoản dành cho ADMIN (ca sử dụng "Quản lý tài khoản người dùng", Nhóm 4).
 *
 * <p>Vòng đời tài khoản ở hệ thống này là <b>tạo → sửa → khóa</b>, không có xóa: tài khoản đã từng
 * thao tác trên dữ liệu sản xuất cần giữ lại để còn truy được ai làm gì, nên khóa là cách ngừng
 * truy cập. Cờ {@code enabled} không phải nhãn trang trí — Spring Security đã tôn trọng nó ngay ở
 * {@code CustomUserDetailsService}, nên khóa xong là lần đăng nhập kế tiếp bị từ chối.
 *
 * <p><b>Danh tính "chính mình"</b> luôn do nơi gọi truyền vào từ {@code Authentication#getName()}
 * (chủ thể của JWT đã được xác thực), không bao giờ lấy từ thân request — nếu tin id client gửi lên
 * thì mọi chốt chặn tự-khóa bên dưới đều có thể vượt qua bằng cách sửa một con số.
 */
@Service
public class UserService {

    private static final String ADMIN_ROLE = "ADMIN";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper mapper;

    public UserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            UserMapper mapper) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.mapper = mapper;
    }

    /**
     * Sắp theo tên đăng nhập để thứ tự bảng ổn định giữa các lần tải, không phụ thuộc thứ tự chèn —
     * nay do {@code Pageable} đảm nhiệm, vì sắp xếp trong Java chỉ sắp được đúng trang đang tải.
     */
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getPage(String keyword, String roleCode, Boolean enabled, Pageable pageable) {
        return PageResponse.of(
                userRepository.findAll(UserSpecifications.filter(keyword, roleCode, enabled), pageable),
                mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return mapper.toResponse(findEntityById(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Tên đăng nhập đã tồn tại: " + request.getUsername());
        }
        PasswordRules.checkFitsBcrypt(request.getPassword());

        User entity = new User();
        entity.setUsername(request.getUsername());
        entity.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        entity.setRole(findRoleByCode(request.getRoleCode()));
        entity.setEnabled(true);

        return mapper.toResponse(userRepository.save(entity));
    }

    /**
     * Đổi vai trò và/hoặc khóa–mở khóa. Cũng là đường duy nhất lật cờ {@code enabled}: một cờ chỉ
     * nên có một đường ghi, nếu không hai endpoint sẽ dần trôi ra hai bộ luật khác nhau.
     */
    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request, String currentUsername) {
        User target = findEntityById(id);
        Role newRole = findRoleByCode(request.getRoleCode());
        boolean newEnabled = Boolean.TRUE.equals(request.getEnabled());

        checkNotLockingSelfOut(target, newRole, newEnabled, currentUsername);
        checkNotRemovingLastAdmin(target, newRole, newEnabled);

        target.setRole(newRole);
        target.setEnabled(newEnabled);
        return mapper.toResponse(userRepository.save(target));
    }

    /**
     * ADMIN đặt lại mật khẩu cho tài khoản khác — cố ý không hỏi mật khẩu cũ, vì tình huống dùng đến
     * nó luôn là người dùng đã quên mật khẩu của mình.
     */
    @Transactional
    public void resetPassword(Long id, ResetPasswordRequest request) {
        PasswordRules.checkFitsBcrypt(request.getNewPassword());
        User target = findEntityById(id);
        target.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(target);
    }

    /**
     * Chặn ADMIN tự cắt mất quyền của chính mình. Không phải phép lịch sự với người dùng mà là chặn
     * một trạng thái không tự thoát ra được: người vừa tự khóa mình không còn cách nào mở lại, và
     * nếu đó là ADMIN cuối thì cả hệ thống mất luôn khả năng quản trị.
     */
    private void checkNotLockingSelfOut(User target, Role newRole, boolean newEnabled, String currentUsername) {
        if (!target.getUsername().equals(currentUsername)) {
            return;
        }
        if (!newEnabled) {
            throw new ConflictException("Không thể tự khóa tài khoản của chính mình");
        }
        if (!target.getRole().getCode().equals(newRole.getCode())) {
            throw new ConflictException("Không thể tự đổi vai trò của tài khoản đang đăng nhập");
        }
    }

    /**
     * Giữ lại ít nhất một ADMIN đang hoạt động. Chốt tự-khóa ở trên chưa phủ được trường hợp này:
     * một ADMIN hoàn toàn có thể khóa <i>người khác</i> mà người đó lại là ADMIN hoạt động cuối cùng
     * (ví dụ hai ADMIN cùng khóa chéo nhau).
     */
    private void checkNotRemovingLastAdmin(User target, Role newRole, boolean newEnabled) {
        boolean wasActiveAdmin = target.isEnabled() && ADMIN_ROLE.equals(target.getRole().getCode());
        boolean staysActiveAdmin = newEnabled && ADMIN_ROLE.equals(newRole.getCode());
        if (!wasActiveAdmin || staysActiveAdmin) {
            return;
        }
        if (userRepository.findByRole_CodeAndEnabledTrue(ADMIN_ROLE).size() <= 1) {
            throw new ConflictException(
                    "Không thể khóa hoặc hạ quyền quản trị viên đang hoạt động cuối cùng của hệ thống");
        }
    }

    private User findEntityById(Long id) {
        return userRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản với id " + id));
    }

    private Role findRoleByCode(String code) {
        return roleRepository
                .findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vai trò: " + code));
    }
}
