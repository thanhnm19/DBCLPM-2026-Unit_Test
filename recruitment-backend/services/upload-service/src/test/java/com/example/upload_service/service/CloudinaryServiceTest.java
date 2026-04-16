package com.example.upload_service.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho CloudinaryService - Module 10 (Phần 3b): Upload file.
 *
 * Chiến lược:
 * - Mock Cloudinary SDK (third-party) và Uploader.
 * - Kiểm tra xử lý IOException -> RuntimeException.
 * - Minh chứng (CheckDB): Không có DB. Kiểm tra bằng verify() Cloudinary.uploader().upload() được gọi đúng.
 * - Dọn dẹp (Rollback): Không có DB thật. Mockito tự động reset sau mỗi test.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CloudinaryService Unit Tests")
class CloudinaryServiceTest {

    // -----------------------------------------------------------------------
    // Mock Cloudinary SDK (external infrastructure)
    // -----------------------------------------------------------------------

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    @Mock
    private MultipartFile mockFile;

    // Service đang được kiểm tra (System Under Test)
    @InjectMocks
    private CloudinaryService cloudinaryService;

    @BeforeEach
    void setUp() {
        // Cloudinary.uploader() phải trả về mock Uploader
        when(cloudinary.uploader()).thenReturn(uploader);
    }

    // =======================================================================
    // PHẦN 1: Hàm upload()
    // =======================================================================

    // Test Case ID: UTIL-CL01
    // Mục tiêu: upload() thành công -> trả về Map kết quả từ Cloudinary
    @Test
    @DisplayName("UTIL-CL01: upload - File hợp lệ, phải gọi Cloudinary và trả về Map kết quả")
    void upload_ValidFile_ShouldCallCloudinaryUploaderAndReturnResultMap() throws IOException {
        // Chuẩn bị: Cloudinary trả về map kết quả với secure_url
        byte[] fileBytes = "fake-image-content".getBytes();
        Map<String, Object> expectedResult = new HashMap<>();
        expectedResult.put("secure_url", "https://cloudinary.com/sample.jpg");
        expectedResult.put("public_id", "sample_id");

        when(mockFile.getBytes()).thenReturn(fileBytes);
        when(uploader.upload(eq(fileBytes), any())).thenReturn(expectedResult);

        // Thực thi
        Map<String, Object> result = cloudinaryService.upload(mockFile);

        // Kiểm tra: Kết quả phải là map từ Cloudinary
        assertThat(result).containsKey("secure_url");
        assertThat(result.get("secure_url")).isEqualTo("https://cloudinary.com/sample.jpg");

        // Minh chứng (CheckDB): uploader.upload() được gọi đúng 1 lần với đúng bytes
        verify(uploader, times(1)).upload(eq(fileBytes), any());
    }

    // =======================================================================
    // PHẦN 2: Hàm uploadFile()
    // =======================================================================

    // Test Case ID: UTIL-CL02
    // Mục tiêu: uploadFile() thành công -> trả về secure_url từ Cloudinary
    @Test
    @DisplayName("UTIL-CL02: uploadFile - File hợp lệ, phải trả về secure_url")
    void uploadFile_ValidFile_ShouldReturnSecureUrl() throws IOException {
        // Chuẩn bị
        byte[] fileBytes = "valid-file-bytes".getBytes();
        Map<String, Object> cloudinaryResult = new HashMap<>();
        cloudinaryResult.put("secure_url", "https://res.cloudinary.com/test/image/upload/v1/file.pdf");

        when(mockFile.getBytes()).thenReturn(fileBytes);
        when(uploader.upload(any(byte[].class), any())).thenReturn(cloudinaryResult);

        // Thực thi
        String resultUrl = cloudinaryService.uploadFile(mockFile);

        // Kiểm tra: Phải trả về URL chính xác
        assertThat(resultUrl).isEqualTo("https://res.cloudinary.com/test/image/upload/v1/file.pdf");

        // Minh chứng (CheckDB): upload() được gọi 1 lần
        verify(uploader, times(1)).upload(any(byte[].class), any());
    }

    // Test Case ID: UTIL-CL03
    // Mục tiêu: Cloudinary throws IOException -> phải wrap thành RuntimeException
    @Test
    @DisplayName("UTIL-CL03: uploadFile - Cloudinary throw IOException, phải throw RuntimeException")
    void uploadFile_CloudinaryThrowsIOException_ShouldThrowRuntimeException() throws IOException {
        // Chuẩn bị: Cloudinary uploader ném IOException (lỗi kết nối)
        byte[] fileBytes = "broken-file".getBytes();
        when(mockFile.getBytes()).thenReturn(fileBytes);
        when(uploader.upload(any(byte[].class), any()))
                .thenThrow(new IOException("Connection timeout"));

        // Thực thi & Kiểm tra: Phải throw RuntimeException với message đúng
        assertThatThrownBy(() -> cloudinaryService.uploadFile(mockFile))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Không thể upload file");
    }

    // Test Case ID: UTIL-CL04
    // Mục tiêu: File rỗng (0 bytes) -> vẫn gọi Cloudinary, không NPE
    @Test
    @DisplayName("UTIL-CL04: uploadFile - File rỗng 0 bytes, phải gọi Cloudinary và không NPE")
    void uploadFile_EmptyFile_ShouldStillCallCloudinaryWithoutThrowingNpe() throws IOException {
        // Chuẩn bị: File rỗng (0 bytes)
        byte[] emptyBytes = new byte[0];
        Map<String, Object> cloudinaryResult = new HashMap<>();
        cloudinaryResult.put("secure_url", "https://res.cloudinary.com/empty.bin");

        when(mockFile.getBytes()).thenReturn(emptyBytes);
        when(uploader.upload(any(byte[].class), any())).thenReturn(cloudinaryResult);

        // Thực thi: Gọi upload với file rỗng -> không được throw NullPointerException
        String resultUrl = cloudinaryService.uploadFile(mockFile);

        // Kiểm tra: Vẫn trả về URL (Cloudinary xử lý file rỗng)
        assertThat(resultUrl).isNotNull();

        // Minh chứng (CheckDB): uploader.upload() vẫn được gọi (service không chặn file rỗng)
        verify(uploader, times(1)).upload(any(byte[].class), any());
    }
}
