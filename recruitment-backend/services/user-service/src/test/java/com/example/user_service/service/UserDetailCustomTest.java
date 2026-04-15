package com.example.user_service.service;

import com.example.user_service.config.DataInitializer;
import com.example.user_service.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("UserDetailCustom Unit Test")
class UserDetailCustomTest {

    @Autowired
    private UserDetailCustom userDetailCustom;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private DataInitializer dataInitializer;

    @Test
    @DisplayName("[UDC-TC01] - loadUserByUsername() trả về UserDetails khi email tồn tại")
    void tc01_loadUserByUsername_returnsUserDetails() {
        // Test Case ID: UDC-TC01
        // Mục tiêu: xác minh nhánh thành công khi user tồn tại.

        // Arrange
        // Tạo user giả lập trả về từ UserService (dependency đã mock).
        User user = new User();
        user.setEmail("admin@gmail.com");
        user.setPassword("hashed-password");
        when(userService.handleGetUserByUsername("admin@gmail.com")).thenReturn(user);

        // Act
        // Gọi hàm load theo username để nhận UserDetails của Spring Security.
        UserDetails result = userDetailCustom.loadUserByUsername("admin@gmail.com");

        // Assert
        // Kiểm tra username/password/authority được map đúng.
        assertThat(result.getUsername()).isEqualTo("admin@gmail.com");
        assertThat(result.getPassword()).isEqualTo("hashed-password");
        assertThat(result.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");

        // CheckDB: test này không truy cập DB thật vì UserService đã được @MockitoBean.
    }

    @Test
    @DisplayName("[UDC-TC02] - loadUserByUsername() ném UsernameNotFoundException khi email không tồn tại")
    void tc02_loadUserByUsername_throwsExceptionWhenUserMissing() {
        // Test Case ID: UDC-TC02
        // Mục tiêu: xác minh nhánh lỗi khi không tìm thấy user theo email.

        // Arrange
        // Thiết lập dependency trả về null để kích hoạt exception branch.
        when(userService.handleGetUserByUsername("missing@gmail.com")).thenReturn(null);

        // Act
        // Hàm phải ném UsernameNotFoundException.
        UsernameNotFoundException exception = assertThrows(
                UsernameNotFoundException.class,
                () -> userDetailCustom.loadUserByUsername("missing@gmail.com"));

        // Assert
        // Kiểm tra message lỗi đúng kỳ vọng nghiệp vụ đăng nhập.
        assertThat(exception.getMessage()).isEqualTo("Email/Mật khẩu không hợp lệ");

        // CheckDB: test này không truy cập DB thật vì UserService đã được @MockitoBean.
    }
}
