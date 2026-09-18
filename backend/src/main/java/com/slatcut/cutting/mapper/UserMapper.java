package com.slatcut.cutting.mapper;

import com.slatcut.cutting.domain.User;
import com.slatcut.cutting.dto.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    /**
     * Không có {@code updateEntity} đối xứng như các mapper khác: mọi trường của {@link User} đều
     * phải đi qua luật nghiệp vụ ở {@code UserService} (băm mật khẩu, tra vai trò, chặn tự khóa),
     * nên một hàm gán hàng loạt từ request sẽ là đường vòng đi tắt qua các luật đó.
     */
    @Mapping(target = "roleCode", source = "role.code")
    UserResponse toResponse(User entity);
}
