package com.example.user_service.config;

import com.example.user_service.service.PermissionService;
import com.example.user_service.utils.SecurityUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.HandlerMapping;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("PermissionInterceptor Unit Test")
class PermissionInterceptorTest {

    @Autowired
    private PermissionInterceptor permissionInterceptor;

    @MockitoBean
    private PermissionService permissionService;

    @MockitoBean
    private com.example.user_service.config.DataInitializer dataInitializer;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }
    @Test
    @DisplayName("[PIC-TC01] - preHandle() bỏ qua OPTIONS request")
    void tc01_preHandle_optionsRequest_returnsTrueWithoutPermissionCheck() throws Exception {
        // Test Case ID: PIC-TC01
        // Mục tiêu: xác minh CORS preflight không bị chặn bởi cơ chế phân quyền.

        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/user-service/roles");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        boolean allowed = permissionInterceptor.preHandle(request, response, new Object());

        // Assert
        assertThat(allowed).isTrue();
        verify(permissionService, never()).check(eq("user-service:roles:read"), anyLong());
    }
    @Test
    @DisplayName("[PIC-TC02] - preHandle() map GET thành action read")
    void tc02_preHandle_getRequest_mapsToReadPermission() throws Exception {
        // Test Case ID: PIC-TC02
        // Mục tiêu: xác minh rule GET -> read được xây dựng đúng permission name.

        // Arrange
        setAuthenticatedUser(1001L);
        MockHttpServletRequest request = buildRequest("GET", "/api/v1/user-service/roles",
                "/api/v1/user-service/roles");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(permissionService.check("user-service:roles:read", 1001L)).thenReturn(true);

        // Act
        boolean allowed = permissionInterceptor.preHandle(request, response, new Object());

        // Assert
        assertThat(allowed).isTrue();
        verify(permissionService, times(1)).check("user-service:roles:read", 1001L);
    }
    @Test
    @DisplayName("[PIC-TC03] - preHandle() map POST thành action manage")
    void tc03_preHandle_postRequest_mapsToManagePermission() throws Exception {
        // Test Case ID: PIC-TC03
        // Mục tiêu: xác minh rule POST -> manage.

        // Arrange
        setAuthenticatedUser(1002L);
        MockHttpServletRequest request = buildRequest("POST", "/api/v1/user-service/permissions",
                "/api/v1/user-service/permissions");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(permissionService.check("user-service:permissions:manage", 1002L)).thenReturn(true);

        // Act
        boolean allowed = permissionInterceptor.preHandle(request, response, new Object());

        // Assert
        assertThat(allowed).isTrue();
        verify(permissionService, times(1)).check("user-service:permissions:manage", 1002L);
    }
    @Test
    @DisplayName("[PIC-TC04] - preHandle() map endpoint đặc biệt thành manage")
    void tc04_preHandle_specialAction_mapsToManagePermission() throws Exception {
        // Test Case ID: PIC-TC04
        // Mục tiêu: xác minh các action đặc biệt như /approve luôn là manage.

        // Arrange
        setAuthenticatedUser(1003L);
        MockHttpServletRequest request = buildRequest(
                "GET",
                "/api/v1/workflow-service/approval-trackings/123/approve",
                "/api/v1/workflow-service/approval-trackings/{id}/approve");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(permissionService.check("workflow-service:approval-trackings:manage", 1003L)).thenReturn(true);

        // Act
        boolean allowed = permissionInterceptor.preHandle(request, response, new Object());

        // Assert
        assertThat(allowed).isTrue();
        verify(permissionService, times(1)).check("workflow-service:approval-trackings:manage", 1003L);
    }
    @Test
    @DisplayName("[PIC-TC05] - preHandle() ném AccessDeniedException khi không có quyền")
    void tc05_preHandle_permissionDenied_throwsAccessDeniedException() {
        // Test Case ID: PIC-TC05
        // Mục tiêu: xác minh request bị chặn khi check permission trả false.

        // Arrange
        setAuthenticatedUser(1004L);
        MockHttpServletRequest request = buildRequest("DELETE", "/api/v1/user-service/roles/1",
                "/api/v1/user-service/roles/{id}");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(permissionService.check("user-service:roles:manage", 1004L)).thenReturn(false);

        // Act + Assert
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> permissionInterceptor.preHandle(request, response, new Object()));
    }
    @Test
    @DisplayName("[PIC-TC06] - preHandle() dùng memo request-scope để tránh check lặp")
    void tc06_preHandle_requestMemoization_avoidsDuplicatePermissionCheck() throws Exception {
        // Test Case ID: PIC-TC06
        // Mục tiêu: xác minh cùng permission trong cùng request chỉ check 1 lần.

        // Arrange
        setAuthenticatedUser(1005L);
        MockHttpServletRequest request = buildRequest("GET", "/api/v1/user-service/users",
                "/api/v1/user-service/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(permissionService.check("user-service:users:read", 1005L)).thenReturn(true);

        // Act
        boolean first = permissionInterceptor.preHandle(request, response, new Object());
        boolean second = permissionInterceptor.preHandle(request, response, new Object());

        // Assert
        assertThat(first).isTrue();
        assertThat(second).isTrue();
        verify(permissionService, times(1)).check("user-service:users:read", 1005L);
    }
    @Test
    @DisplayName("[PIC-TC07] - preHandle() dùng requestURI khi BEST_MATCHING_PATTERN_ATTRIBUTE là null")
    void tc07_preHandle_nullPathAttribute_fallsBackToRequestUri() throws Exception {
        // Test Case ID: PIC-TC07
        // Mục tiêu: xác minh nhánh TRUE của D2 — if (path == null)
        //           Khi HandlerMapping attribute không được set, dùng request.getRequestURI().
        // Basis Path: D2=True (path == null → path = requestURI)

        // Arrange
        setAuthenticatedUser(1006L);
        // Không set HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE → path sẽ null
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/user-service/departments");
        // (không gọi request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, ...))
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(permissionService.check("user-service:departments:read", 1006L)).thenReturn(true);

        // Act
        boolean allowed = permissionInterceptor.preHandle(request, response, new Object());

        // Assert
        assertThat(allowed).isTrue();
        // Xác minh permission name được build từ requestURI thay vì pattern
        verify(permissionService, times(1)).check("user-service:departments:read", 1006L);
    }
    @Test
    @DisplayName("[PIC-TC08] - preHandle() fallback when path is malformed returns unknown permission name")
    void tc08_preHandle_malformedPath_returnsUnknownPermissionName() throws Exception {
        // Test Case ID: PIC-TC08
        // Mục tiêu: test nhánh catch trong extractPermissionName() -> "unknown:unknown:unknown"

        // Arrange
        setAuthenticatedUser(1007L);
        MockHttpServletRequest request = buildRequest("GET", "/badpath", "/badpath");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(permissionService.check("unknown:unknown:unknown", 1007L)).thenReturn(true);

        // Act
        boolean allowed = permissionInterceptor.preHandle(request, response, new Object());

        // Assert
        assertThat(allowed).isTrue();
        verify(permissionService).check("unknown:unknown:unknown", 1007L);
    }

    @Test
    @DisplayName("[PIC-TC09] - preHandle() handles unknown HTTP method mapping to unknown action")
    void tc09_preHandle_unknownHttpMethod_mapsToUnknownAction() throws Exception {
        // Test Case ID: PIC-TC09
        // Mục tiêu: test mapHttpMethodToAction default -> "unknown"

        // Arrange
        setAuthenticatedUser(1008L);
        MockHttpServletRequest request = new MockHttpServletRequest("TRACE", "/api/v1/user-service/roles");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/user-service/roles");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(permissionService.check("user-service:roles:unknown", 1008L)).thenReturn(true);

        // Act
        boolean allowed = permissionInterceptor.preHandle(request, response, new Object());

        // Assert
        assertThat(allowed).isTrue();
        verify(permissionService).check("user-service:roles:unknown", 1008L);
    }

    @Test
    @DisplayName("[PIC-TC10] - hasSpecialAction() xử lý đúng segment /pending là read (trả false)")
    void tc10_hasSpecialAction_pendingSegment_returnsFalse() throws Exception {
        // Test Case ID: PIC-TC10
        // Mục tiêu: xác minh nhánh FALSE của D7 — "pending"/"by-request" → return false
        //           Segment "pending" không phải manage action → dùng HTTP method để map.
        // Basis Path: D6=False (segment "pending" không phải {}/digit → xét switch),
        //             D7=False (switch case "pending" → return false → dùng GET→read)

        // Arrange
        setAuthenticatedUser(1009L);
        MockHttpServletRequest request = buildRequest(
                "GET",
                "/api/v1/workflow-service/approval-trackings/pending",
                "/api/v1/workflow-service/approval-trackings/pending");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(permissionService.check("workflow-service:approval-trackings:read", 1009L)).thenReturn(true);

        // Act
        boolean allowed = permissionInterceptor.preHandle(request, response, new Object());

        // Assert: "pending" → hasSpecialAction=false → GET→read
        assertThat(allowed).isTrue();
        verify(permissionService, times(1)).check("workflow-service:approval-trackings:read", 1009L);
    }

    @Test
    @DisplayName("[PIC-TC11] - hasSpecialAction() xử lý đúng segment /actions/withdraw là manage (trả true)")
    void tc11_hasSpecialAction_actionsWithdraw_returnsTrue() throws Exception {
        // Test Case ID: PIC-TC11
        // Mục tiêu: xác minh nhánh TRUE của D8 — segment "actions" + next="withdraw" → true
        //           /actions/withdraw là manage operation dù method có thể là POST.
        // Basis Path: D8=True (i+1 < len && segments[i+1]=="withdraw" → return true)

        // Arrange
        setAuthenticatedUser(1010L);
        MockHttpServletRequest request = buildRequest(
                "POST",
                "/api/v1/job-service/jobs/10/actions/withdraw",
                "/api/v1/job-service/jobs/{id}/actions/withdraw");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(permissionService.check("job-service:jobs:manage", 1010L)).thenReturn(true);

        // Act
        boolean allowed = permissionInterceptor.preHandle(request, response, new Object());

        // Assert: actions/withdraw → hasSpecialAction=true → manage
        assertThat(allowed).isTrue();
        verify(permissionService, times(1)).check("job-service:jobs:manage", 1010L);
    }

    @Test
    @DisplayName("[PIC-TC12] - hasSpecialAction() bỏ qua segment {id} và digit, xử lý segment tiếp theo")
    void tc12_hasSpecialAction_pathVariableAndDigit_skipsAndContinues() throws Exception {
        // Test Case ID: PIC-TC12
        // Mục tiêu: xác minh nhánh TRUE của D6 — segment bắt đầu bằng "{" hoặc là số → continue
        //           Sau khi skip {id} và "123", segment "approve" được xét → manage.
        // Basis Path: D6=True (segment là {id}/digit → continue), sau đó D7=True (approve→true)

        // Arrange
        setAuthenticatedUser(1011L);
        MockHttpServletRequest request = buildRequest(
                "GET",
                "/api/v1/workflow-service/workflows/123/approve",
                "/api/v1/workflow-service/workflows/{id}/approve");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(permissionService.check("workflow-service:workflows:manage", 1011L)).thenReturn(true);

        // Act
        boolean allowed = permissionInterceptor.preHandle(request, response, new Object());

        // Assert: {id} bị skip → "approve" được xét → hasSpecialAction=true → manage
        assertThat(allowed).isTrue();
        verify(permissionService, times(1)).check("workflow-service:workflows:manage", 1011L);
    }

    private MockHttpServletRequest buildRequest(String method, String requestUri, String matchingPattern) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, requestUri);
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, matchingPattern);
        return request;
    }

    private void setAuthenticatedUser(Long userId) {
        // Tạo JWT giả chứa claim user.userId để SecurityUtil.extractUserId() đọc được.
        Map<String, Object> user = new HashMap<>();
        user.put("userId", userId);
        Map<String, Object> claims = new HashMap<>();
        claims.put("user", user);
        claims.put("sub", "test-user");

        Jwt jwt = new Jwt(
                "mock-token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "none"),
                claims);
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Dòng assert nhỏ để đảm bảo helper set context đúng trước khi chạy test.
        assertThat(SecurityUtil.extractUserId()).isEqualTo(userId);
    }
}