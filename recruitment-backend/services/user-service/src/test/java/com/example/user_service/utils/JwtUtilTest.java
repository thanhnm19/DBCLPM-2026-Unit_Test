package com.example.user_service.utils;

import com.example.user_service.config.DataInitializer;
import com.example.user_service.dto.login.ResponseLoginDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("JwtUtil Unit Test")
class JwtUtilTest {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private DataInitializer dataInitializer;

    @Test
    @DisplayName("[JWT-TC01] - createAccessToken() sinh token hợp lệ chứa subject và claim user")
    void tc01_createAccessToken_generatesValidToken() {
        // Test Case ID: JWT-TC01
        // Mục tiêu: xác minh access token được sinh đúng định dạng và decode hợp lệ.

        // Arrange
        // Tạo payload user nhúng vào claim "user" của JWT.
        ResponseLoginDTO.UserToken userToken = new ResponseLoginDTO.UserToken(
                1L, 101L, "admin@gmail.com", "Admin", "ADMIN", 5L, "HR");

        // Act
        // Sinh token và decode lại bằng JwtDecoder để kiểm chứng nội dung.
        String accessToken = jwtUtil.createAccessToken("admin@gmail.com", userToken);
        Jwt decodedToken = jwtDecoder.decode(accessToken);

        // Assert
        assertThat(accessToken).isNotBlank();
        assertThat(decodedToken.getSubject()).isEqualTo("admin@gmail.com");
        assertThat(decodedToken.getClaims()).containsKey("user");

        // CheckDB: test JWT không tác động DB.
    }

    @Test
    @DisplayName("[JWT-TC02] - createRefreshToken() sinh token hợp lệ và decode được")
    void tc02_createRefreshToken_generatesValidToken() {
        // Test Case ID: JWT-TC02
        // Mục tiêu: xác minh refresh token hợp lệ và checkValidRefreshToken hoạt động
        // đúng.

        // Arrange
        ResponseLoginDTO.UserToken userToken = new ResponseLoginDTO.UserToken(
                2L, 202L, "staff@company.com", "Staff", "STAFF", 7L, "TECH");

        // Act
        String refreshToken = jwtUtil.createRefreshToken("staff@company.com", userToken);
        Jwt decodedToken = jwtUtil.checkValidRefreshToken(refreshToken);

        // Assert
        assertThat(refreshToken).isNotBlank();
        assertThat(decodedToken.getSubject()).isEqualTo("staff@company.com");
        assertThat(decodedToken.getClaims()).containsKey("user");

        // CheckDB: test JWT không tác động DB.
    }

    @Test
    @DisplayName("[JWT-TC03] - checkValidRefreshToken() ném exception khi token không hợp lệ")
    void tc03_checkValidRefreshToken_throwsExceptionForInvalidToken() {
        // Test Case ID: JWT-TC03
        // Mục tiêu: đảm bảo token sai định dạng sẽ bị từ chối.

        // Arrange
        String invalidToken = "not.a.valid.jwt";

        // Act
        Exception exception = assertThrows(Exception.class, () -> jwtUtil.checkValidRefreshToken(invalidToken));

        // Assert
        assertThat(exception).isNotNull();

        // CheckDB: test JWT không tác động DB.
    }

    @Test
    @DisplayName("[JWT-TC04] - createAccessToken() và createRefreshToken() tạo token khác nhau")
    void tc04_accessTokenAndRefreshToken_areGeneratedSeparately() {
        // Test Case ID: JWT-TC04
        // Mục tiêu: xác minh cả access/refresh token đều sinh thành công và decode hợp
        // lệ.

        // Arrange
        ResponseLoginDTO.UserToken userToken = new ResponseLoginDTO.UserToken(
                3L, 303L, "compare@company.com", "Compare", "STAFF", 9L, "SALES");

        // Act
        String accessToken = jwtUtil.createAccessToken("compare@company.com", userToken);
        String refreshToken = jwtUtil.createRefreshToken("compare@company.com", userToken);
        Jwt decodedAccessToken = jwtDecoder.decode(accessToken);
        Jwt decodedRefreshToken = jwtUtil.checkValidRefreshToken(refreshToken);

        // Assert
        assertThat(accessToken).isNotBlank();
        assertThat(refreshToken).isNotBlank();
        assertThat(decodedAccessToken.getSubject()).isEqualTo("compare@company.com");
        assertThat(decodedRefreshToken.getSubject()).isEqualTo("compare@company.com");

        // CheckDB: test JWT không tác động DB.
    }
}
