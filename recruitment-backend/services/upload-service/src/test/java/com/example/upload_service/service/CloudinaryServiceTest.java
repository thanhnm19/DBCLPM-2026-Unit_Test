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

    // Test Case ID: UTIL-CL05
    // Mục tiêu: upload() khi file.getBytes() ném IOException -> phải propagate IOException
    // (vì upload() khai báo `throws IOException` và không bao bọc lỗi)
    @Test
    @DisplayName("UTIL-CL05: upload - file.getBytes() throw IOException, phải propagate IOException")
    void upload_GetBytesThrowsIOException_ShouldPropagateIOException() throws IOException {
        // Chuẩn bị: getBytes() ném IOException (vd: lỗi đọc file tạm)
        when(mockFile.getBytes()).thenThrow(new IOException("Cannot read file bytes"));

        // Thực thi & Kiểm tra: IOException phải được propagate nguyên gốc, không wrap
        assertThatThrownBy(() -> cloudinaryService.upload(mockFile))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Cannot read file bytes");

        // Minh chứng (CheckDB): uploader.upload() KHÔNG được gọi vì lỗi xảy ra ở getBytes()
        verify(uploader, never()).upload(any(byte[].class), any());
    }

    // Test Case ID: UTIL-CL06
    // Mục tiêu: upload() khi Cloudinary trả map rỗng -> trả map rỗng, không NPE
    @Test
    @DisplayName("UTIL-CL06: upload - Cloudinary trả Map rỗng, phải trả Map rỗng không NPE")
    void upload_CloudinaryReturnsEmptyMap_ShouldReturnEmptyMapWithoutNpe() throws IOException {
        // Chuẩn bị: Cloudinary trả về HashMap rỗng (trường hợp biên)
        byte[] fileBytes = "any-content".getBytes();
        Map<String, Object> emptyMap = new HashMap<>();

        when(mockFile.getBytes()).thenReturn(fileBytes);
        when(uploader.upload(any(byte[].class), any())).thenReturn(emptyMap);

        // Thực thi: Phải an toàn, không ném NullPointerException khi cast
        Map<String, Object> result = cloudinaryService.upload(mockFile);

        // Kiểm tra: Kết quả không null, là map rỗng
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();

        // Minh chứng (CheckDB): uploader.upload() được gọi 1 lần
        verify(uploader, times(1)).upload(any(byte[].class), any());
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

    // Test Case ID: UTIL-CL07
    // Mục tiêu: uploadFile() khi Cloudinary trả map KHÔNG có khoá secure_url
    //           -> map.get("secure_url") = null -> trả về null, không ném lỗi
    @Test
    @DisplayName("UTIL-CL07: uploadFile - Cloudinary trả map không có secure_url, phải trả null")
    void uploadFile_NoSecureUrlInResult_ShouldReturnNull() throws IOException {
        // Chuẩn bị: Map trả về không chứa khoá "secure_url"
        byte[] fileBytes = "data".getBytes();
        Map<String, Object> resultWithoutSecureUrl = new HashMap<>();
        resultWithoutSecureUrl.put("public_id", "abc123");
        resultWithoutSecureUrl.put("url", "http://res.cloudinary.com/abc123"); // chỉ có http url

        when(mockFile.getBytes()).thenReturn(fileBytes);
        when(uploader.upload(any(byte[].class), any())).thenReturn(resultWithoutSecureUrl);

        // Thực thi
        String resultUrl = cloudinaryService.uploadFile(mockFile);

        // Kiểm tra: Trả null vì map.get("secure_url") = null, cast (String) null = null
        assertThat(resultUrl).isNull();

        // Minh chứng (CheckDB): uploader.upload() vẫn được gọi (không ném lỗi giữa chừng)
        verify(uploader, times(1)).upload(any(byte[].class), any());
    }

    // Test Case ID: UTIL-CL08
    // Mục tiêu: uploadFile() khi Cloudinary trả map có secure_url=null
    //           -> trả về null an toàn, stream filter ở caller có thể bỏ qua
    @Test
    @DisplayName("UTIL-CL08: uploadFile - secure_url=null, phải trả null an toàn")
    void uploadFile_SecureUrlIsNull_ShouldReturnNullSafely() throws IOException {
        // Chuẩn bị: Map có khoá secure_url nhưng giá trị là null
        byte[] fileBytes = "data".getBytes();
        Map<String, Object> resultWithNullUrl = new HashMap<>();
        resultWithNullUrl.put("secure_url", null);
        resultWithNullUrl.put("public_id", "xyz789");

        when(mockFile.getBytes()).thenReturn(fileBytes);
        when(uploader.upload(any(byte[].class), any())).thenReturn(resultWithNullUrl);

        // Thực thi
        String resultUrl = cloudinaryService.uploadFile(mockFile);

        // Kiểm tra: Trả null an toàn (không NPE khi cast (String) null)
        assertThat(resultUrl).isNull();

        // Minh chứng (CheckDB): uploader.upload() được gọi đúng 1 lần
        verify(uploader, times(1)).upload(any(byte[].class), any());
    }

    // Test Case ID: UTIL-CL09
    // Mục tiêu: uploadFile(null) -> file.getBytes() ném NullPointerException
    //           NPE không phải IOException nên KHÔNG bị catch -> propagate ra ngoài
    @Test
    @DisplayName("UTIL-CL09: uploadFile - file=null, phải ném NullPointerException")
    void uploadFile_NullFile_ShouldThrowNullPointerException() throws IOException {
        // Thực thi & Kiểm tra: file=null -> NPE khi gọi file.getBytes()
        // Catch chỉ bắt IOException, NPE thoát ra nguyên gốc
        assertThatThrownBy(() -> cloudinaryService.uploadFile(null))
                .isInstanceOf(NullPointerException.class);

        // Minh chứng (CheckDB): uploader.upload() KHÔNG được gọi vì NPE xảy ra trước
        verify(uploader, never()).upload(any(byte[].class), any());
    }
}
