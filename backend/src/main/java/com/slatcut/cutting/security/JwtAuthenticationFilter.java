package com.slatcut.cutting.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Token chỉ dùng để biết <b>ai</b> đang gọi; <b>quyền</b> thì đọc lại từ CSDL mỗi request.
     *
     * <p>Trước đây vai trò lấy thẳng từ claim trong token và cờ {@code enabled} không được xem tới,
     * nên hai thao tác quản trị quan trọng nhất đều không có hiệu lực thật: khóa một tài khoản
     * không đuổi được người đang đăng nhập (họ dùng tiếp tới khi token hết hạn, mặc định 8 giờ), và
     * hạ quyền một ADMIN cũng vậy — người vừa bị hạ quyền vẫn còn {@code ROLE_ADMIN} trong token và
     * có thể tự tạo lại tài khoản ADMIN mới trước khi token hết hiệu lực.
     *
     * <p>Giá phải trả là một truy vấn tài khoản cho mỗi request đã xác thực. Với ứng dụng quản trị
     * nội bộ vài người dùng đồng thời thì không đáng kể, và đổi lại thao tác khóa có tác dụng ngay
     * ở request kế tiếp.
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            if (jwtService.isValid(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
                authenticate(jwtService.extractUsername(token));
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Tài khoản đã bị khóa hoặc đã bị xóa thì bỏ qua, không dựng Authentication — context rỗng sẽ
     * rơi vào {@code AuthenticationEntryPoint} của SecurityConfig và trả 401 "cần đăng nhập", đúng
     * mã mà interceptor phía frontend dùng để đưa người dùng về màn đăng nhập.
     */
    private void authenticate(String username) {
        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(username);
        } catch (UsernameNotFoundException e) {
            return;
        }
        if (!userDetails.isEnabled()) {
            return;
        }
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        userDetails.getUsername(), null, userDetails.getAuthorities()));
    }
}
