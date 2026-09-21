package com.slatcut.cutting.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.slatcut.cutting.AbstractIntegrationTest;
import com.slatcut.cutting.security.JwtService;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Khóa toàn bộ ma trận phân quyền của hệ thống bằng một chỗ duy nhất.
 *
 * <p>Ma trận này cố ý KHÔNG đồng nhất giữa các module (ghi tồn kho dành cho PLANNER, ghi định mức
 * BOM dành cho ADMIN, ghi đơn hàng mở cho cả hai, còn nhập Excel đơn hàng lại chỉ PLANNER) — đúng
 * theo phân vai ở docs/requirements-functional.md Nhóm 1/2/4. Vì không đồng nhất nên rất dễ bị đổi
 * ngầm khi thêm endpoint mới; bảng dưới đây là bản ghi duy nhất nói rõ luật thật sự đang chạy.
 *
 * <p>Test chỉ khẳng định <b>có qua được cửa phân quyền hay không</b>, không khẳng định nghiệp vụ:
 * "được phép" nghĩa là trả về bất kỳ mã nào KHÁC 403 (404 vì id không tồn tại, 409 vì trùng khóa,
 * 400 vì file không phải Excel... đều tính là đã qua cửa). Nhờ vậy test không vỡ khi DTO hay quy
 * tắc nghiệp vụ đổi, nhưng fail ngay nếu ai đó gỡ hoặc sửa nhầm {@code @PreAuthorize}.
 *
 * <p>Thân request phải hợp lệ với {@code @Valid}: Spring gắn kết và kiểm tra tham số TRƯỚC khi
 * {@code @PreAuthorize} chạy, nên body sai định dạng sẽ trả 400 và che mất kết quả phân quyền thật.
 */
@AutoConfigureMockMvc
class RolePermissionMatrixTest extends AbstractIntegrationTest {

    private static final String ADMIN = "ADMIN";
    private static final String PLANNER = "PLANNER";

    private static final String API_PREFIX = "/api/v1";

    /**
     * Endpoint ghi cố ý KHÔNG có {@code @PreAuthorize} vì phải gọi được lúc chưa đăng nhập. Danh
     * sách này phải cực ngắn và mỗi lần thêm đều là một quyết định có cân nhắc — mọi handler ghi
     * khác đều bị 2 chốt chặn bên dưới bắt buộc khai báo quyền.
     */
    private static final Set<String> PUBLIC_WRITE_ENDPOINTS = Set.of("POST /auth/login");

    /** Id chắc chắn không tồn tại — nhánh "được phép" sẽ dừng ở 404 thay vì đổi dữ liệu thật. */
    private static final long MISSING_ID = 999_999_999L;

    /** Dấu vân chắc chắn không khớp — nhánh "được phép" dừng ở 409 thay vì duyệt thật một phương án. */
    private static final String APPROVE_PLAN_JSON =
            """
            {"stateFingerprint":"khong-bao-gio-khop"}
            """;

    private static final String SLAT_MATERIAL_JSON =
            """
            {"slatMaterial":70000001,"slatMaterialName":"Thanh nan kiểm thử","slatGroup":"BOTTOM_BAR"}
            """;
    private static final String INVENTORY_BATCH_JSON =
            """
            {"slatMaterialId":999999999,"doDaiThanhMm":6000,"soThanh":1}
            """;
    private static final String BOM_ITEM_JSON =
            """
            {"doorProductId":999999999,"slatMaterialId":999999999}
            """;
    private static final String DOOR_PRODUCT_JSON =
            """
            {"material":80000001,"doorMaterialName":"Cửa cuốn kiểm thử","mauSac":"#01"}
            """;
    private static final String CREATE_USER_JSON =
            """
            {"username":"tai_khoan_kiem_thu","password":"matkhau123","roleCode":"PLANNER"}
            """;
    private static final String UPDATE_USER_JSON =
            """
            {"roleCode":"PLANNER","enabled":true}
            """;
    private static final String RESET_PASSWORD_JSON =
            """
            {"newPassword":"matkhau123"}
            """;
    /** Mật khẩu hiện tại cố ý sai: nhánh "được phép" dừng ở 409 thay vì đổi mật khẩu thật. */
    private static final String CHANGE_PASSWORD_JSON =
            """
            {"currentPassword":"khong-phai-mat-khau","newPassword":"matkhau123"}
            """;
    private static final String SALES_ORDER_JSON =
            """
            {"ycsx":"HY9000","item":1,"customerId":999999999,"doorProductId":999999999,
             "chieuCaoDh":2.500,"chieuRongDh":2.000,"reqdDeliveryDate":"2026-01-01"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    private static AbstractMockHttpServletRequestBuilder<?> json(
            AbstractMockHttpServletRequestBuilder<?> builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /** File rác có chủ đích: import sẽ hỏng khi đọc, nhưng cửa phân quyền đã kịp chạy trước đó. */
    private static AbstractMockHttpServletRequestBuilder<?> upload(String path) {
        return multipart(path).file(new MockMultipartFile("file", "khong-phai-excel.xlsx", null, new byte[] {1, 2, 3}));
    }

    private record Endpoint(
            String label, Set<String> allowedRoles, Supplier<AbstractMockHttpServletRequestBuilder<?>> request) {}

    /** Bảng ma trận quyền — thêm endpoint ghi mới thì thêm 1 dòng ở đây. */
    private static List<Endpoint> endpoints() {
        return List.of(
                // Danh mục loại thanh nan: nhóm vật tư ở đây điều khiển việc sinh nhu cầu cắt nên ADMIN
                // cũng phải sửa được (vd mã bị tạo nhầm nhóm OTHER lúc nhập tồn kho).
                new Endpoint("POST /slat-materials", Set.of(ADMIN, PLANNER), () -> json(post("/api/v1/slat-materials"), SLAT_MATERIAL_JSON)),
                new Endpoint("PUT /slat-materials/{id}", Set.of(ADMIN, PLANNER), () -> json(put("/api/v1/slat-materials/{id}", MISSING_ID), SLAT_MATERIAL_JSON)),
                new Endpoint("DELETE /slat-materials/{id}", Set.of(ADMIN, PLANNER), () -> delete("/api/v1/slat-materials/{id}", MISSING_ID)),

                // Lô tồn kho: tác vụ vận hành hằng ngày, chỉ PLANNER (task 10.4).
                new Endpoint("POST /inventory-batches", Set.of(PLANNER), () -> json(post("/api/v1/inventory-batches"), INVENTORY_BATCH_JSON)),
                new Endpoint("PUT /inventory-batches/{id}", Set.of(PLANNER), () -> json(put("/api/v1/inventory-batches/{id}", MISSING_ID), INVENTORY_BATCH_JSON)),
                new Endpoint("DELETE /inventory-batches/{id}", Set.of(PLANNER), () -> delete("/api/v1/inventory-batches/{id}", MISSING_ID)),
                new Endpoint("POST /inventory/import", Set.of(PLANNER), () -> upload("/api/v1/inventory/import")),

                // Dữ liệu nền tảng (định mức BOM, mẫu cửa): ghi dành riêng cho ADMIN.
                new Endpoint("POST /bom-items", Set.of(ADMIN), () -> json(post("/api/v1/bom-items"), BOM_ITEM_JSON)),
                new Endpoint("PUT /bom-items/{id}", Set.of(ADMIN), () -> json(put("/api/v1/bom-items/{id}", MISSING_ID), BOM_ITEM_JSON)),
                new Endpoint("DELETE /bom-items/{id}", Set.of(ADMIN), () -> delete("/api/v1/bom-items/{id}", MISSING_ID)),
                new Endpoint("POST /bom-items/import", Set.of(ADMIN), () -> upload("/api/v1/bom-items/import")),
                new Endpoint("POST /door-products", Set.of(ADMIN), () -> json(post("/api/v1/door-products"), DOOR_PRODUCT_JSON)),
                new Endpoint("PUT /door-products/{id}", Set.of(ADMIN), () -> json(put("/api/v1/door-products/{id}", MISSING_ID), DOOR_PRODUCT_JSON)),
                new Endpoint("DELETE /door-products/{id}", Set.of(ADMIN), () -> delete("/api/v1/door-products/{id}", MISSING_ID)),

                // Đơn hàng: sửa thủ công mở cho cả hai vai trò, riêng nhập hàng loạt là việc vận hành của PLANNER.
                new Endpoint("POST /sales-orders", Set.of(ADMIN, PLANNER), () -> json(post("/api/v1/sales-orders"), SALES_ORDER_JSON)),
                new Endpoint("PUT /sales-orders/{id}", Set.of(ADMIN, PLANNER), () -> json(put("/api/v1/sales-orders/{id}", MISSING_ID), SALES_ORDER_JSON)),
                new Endpoint("DELETE /sales-orders/{id}", Set.of(ADMIN, PLANNER), () -> delete("/api/v1/sales-orders/{id}", MISSING_ID)),
                new Endpoint("POST /sales-orders/import", Set.of(PLANNER), () -> upload("/api/v1/sales-orders/import")),

                // Tính phương án cắt CỐ Ý mở cho cả ADMIN dù là POST: nó chỉ đọc và không chốt
                // quyết định sản xuất nào, nên đứng cùng nhóm với các endpoint đọc bên dưới chứ
                // không cùng nhóm với hai dòng ghi ở trên. Duyệt thì ngược lại — đó là thao tác
                // vận hành duy nhất ghi dữ liệu ở nhóm chức năng này.
                new Endpoint(
                        "POST /cutting-plans/simulate",
                        Set.of(ADMIN, PLANNER),
                        () -> post("/api/v1/cutting-plans/simulate")),
                new Endpoint(
                        "GET /cutting-plans/approval-preview",
                        Set.of(PLANNER),
                        () -> get("/api/v1/cutting-plans/approval-preview")),
                new Endpoint(
                        "POST /cutting-plans/approve",
                        Set.of(PLANNER),
                        () -> json(post("/api/v1/cutting-plans/approve"), APPROVE_PLAN_JSON)),

                // Tài khoản người dùng: ADMIN độc quyền, kể cả GET — khác mọi module còn lại (xem
                // Javadoc của UserController). Vòng đời là tạo/sửa/khóa nên không có DELETE.
                new Endpoint("POST /users", Set.of(ADMIN), () -> json(post("/api/v1/users"), CREATE_USER_JSON)),
                new Endpoint("PUT /users/{id}", Set.of(ADMIN), () -> json(put("/api/v1/users/{id}", MISSING_ID), UPDATE_USER_JSON)),
                new Endpoint("POST /users/{id}/reset-password", Set.of(ADMIN), () -> json(post("/api/v1/users/{id}/reset-password", MISSING_ID), RESET_PASSWORD_JSON)),
                new Endpoint("GET /users", Set.of(ADMIN), () -> get("/api/v1/users")),
                new Endpoint("GET /users/{id}", Set.of(ADMIN), () -> get("/api/v1/users/{id}", MISSING_ID)),

                // Đổi mật khẩu cá nhân: mở cho cả hai vai trò vì ai cũng chỉ đổi được của chính mình
                // (tên đăng nhập lấy từ JWT, không phải từ thân request).
                new Endpoint("POST /auth/change-password", Set.of(ADMIN, PLANNER), () -> json(post("/auth/change-password"), CHANGE_PASSWORD_JSON)),

                // Đọc: mở cho mọi vai trò đã đăng nhập.
                new Endpoint("GET /slat-materials", Set.of(ADMIN, PLANNER), () -> get("/api/v1/slat-materials")),
                new Endpoint(
                        "GET /slat-materials/options",
                        Set.of(ADMIN, PLANNER),
                        () -> get("/api/v1/slat-materials/options")),
                new Endpoint("GET /inventory-batches", Set.of(ADMIN, PLANNER), () -> get("/api/v1/inventory-batches")),
                new Endpoint(
                        "GET /inventory-batches/summary",
                        Set.of(ADMIN, PLANNER),
                        () -> get("/api/v1/inventory-batches/summary")),
                new Endpoint("GET /bom-items", Set.of(ADMIN, PLANNER), () -> get("/api/v1/bom-items")),
                new Endpoint(
                        "GET /bom-items/summary", Set.of(ADMIN, PLANNER), () -> get("/api/v1/bom-items/summary")),
                new Endpoint("GET /sales-orders", Set.of(ADMIN, PLANNER), () -> get("/api/v1/sales-orders")),
                new Endpoint("GET /cutting-plans", Set.of(ADMIN, PLANNER), () -> get("/api/v1/cutting-plans")),
                new Endpoint("GET /dashboard", Set.of(ADMIN, PLANNER), () -> get("/api/v1/dashboard")));
    }

    private static Stream<Arguments> matrix() {
        return endpoints().stream()
                .flatMap(endpoint -> Stream.of(ADMIN, PLANNER)
                        .map(role -> Arguments.of(endpoint.label(), role, endpoint.allowedRoles().contains(role), endpoint)));
    }

    private static Stream<Arguments> everyEndpoint() {
        return endpoints().stream().map(endpoint -> Arguments.of(endpoint.label(), endpoint));
    }

    @ParameterizedTest(name = "{1} → {0} (được phép: {2})")
    @MethodSource("matrix")
    void endpoint_enforcesDeclaredRole(String label, String role, boolean allowed, Endpoint endpoint) throws Exception {
        int status = mockMvc.perform(endpoint
                        .request()
                        .get()
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(role.toLowerCase(), role)))
                .andReturn()
                .getResponse()
                .getStatus();

        if (allowed) {
            assertThat(status)
                    .as("%s phải qua được cửa phân quyền với vai trò %s", label, role)
                    .isNotEqualTo(HttpStatus.FORBIDDEN.value());
        } else {
            assertThat(status)
                    .as("%s phải bị từ chối với vai trò %s", label, role)
                    .isEqualTo(HttpStatus.FORBIDDEN.value());
        }
    }

    /**
     * Không có token thì mọi endpoint đều trả 401 (chưa xác thực), KHÁC với 403 của trường hợp đã
     * đăng nhập nhưng sai vai trò. Phân biệt được hai mã này là điều kiện để client tự xử lý phiên
     * hết hạn — interceptor ở frontend bắt đúng 401 để đưa người dùng về màn đăng nhập.
     */
    @ParameterizedTest(name = "không token → {0}")
    @MethodSource("everyEndpoint")
    void endpoint_withoutToken_isUnauthorized(String label, Endpoint endpoint) throws Exception {
        mockMvc.perform(endpoint.request().get())
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("%s phải trả 401 khi không có token", label)
                        .isEqualTo(HttpStatus.UNAUTHORIZED.value()));
    }

    /**
     * Chốt chặn 1: đọc thẳng bảng định tuyến thật của Spring rồi đối chiếu với bảng ma trận ở trên.
     * Thêm một endpoint ghi mới mà quên khai báo quyền cho nó thì test này fail ngay.
     */
    @Test
    void matrix_coversEveryWriteEndpointRegisteredBySpring() {
        Set<String> declared = endpoints().stream().map(Endpoint::label).collect(Collectors.toSet());

        List<String> missing = handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(RolePermissionMatrixTest::writeLabelsOf)
                .filter(label -> !declared.contains(label))
                .sorted()
                .toList();

        assertThat(missing)
                .as("Endpoint ghi chưa có dòng nào trong bảng ma trận quyền")
                .isEmpty();
    }

    /**
     * Chốt chặn 2: mỗi handler ghi phải thật sự mang {@code @PreAuthorize}.
     *
     * <p>Riêng chốt chặn 1 chưa đủ — chỉ cần thêm một dòng {@code Set.of(ADMIN, PLANNER)} vào bảng
     * là nó im lặng, trong khi endpoint vẫn mở cho mọi tài khoản đăng nhập (đúng cách lỗ hổng tồn
     * kho đã tồn tại từ 5.1 tới 10.4 mà không ai thấy). Test này đọc annotation thật trên method.
     */
    @Test
    void everyWriteHandler_declaresPreAuthorize() {
        List<String> unguarded = handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> writeLabelsOf(entry.getKey()).findAny().isPresent())
                .filter(entry -> entry.getValue().getMethodAnnotation(PreAuthorize.class) == null)
                .map(entry -> entry.getValue().getMethod().getDeclaringClass().getSimpleName()
                        + "#" + entry.getValue().getMethod().getName())
                .sorted()
                .toList();

        assertThat(unguarded)
                .as("Handler ghi thiếu @PreAuthorize — endpoint đang mở cho mọi tài khoản đăng nhập")
                .isEmpty();
    }

    /**
     * Mọi endpoint ghi Spring đang phục vụ, quy về đúng dạng nhãn dùng trong bảng ma trận.
     *
     * <p>KHÔNG lọc theo tiền tố {@code /api/v1}: endpoint ngoài tiền tố đó (vd nhóm tài khoản dưới
     * {@code /auth}) cũng phải khai báo quyền, nếu không sẽ lọt qua cả hai chốt chặn.
     */
    private static Stream<String> writeLabelsOf(RequestMappingInfo info) {
        Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
        return info.getPatternValues().stream()
                .flatMap(pattern -> methods.stream()
                        .filter(method -> method != RequestMethod.GET)
                        .map(method -> method.name() + " " + shortPath(pattern)))
                .filter(label -> !PUBLIC_WRITE_ENDPOINTS.contains(label));
    }

    /** Bỏ tiền tố chung cho gọn nhãn; đường dẫn ngoài tiền tố giữ nguyên để không lẫn với nhau. */
    private static String shortPath(String pattern) {
        return pattern.startsWith(API_PREFIX) ? pattern.substring(API_PREFIX.length()) : pattern;
    }
}
