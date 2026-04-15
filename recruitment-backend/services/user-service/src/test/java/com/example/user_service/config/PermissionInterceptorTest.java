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