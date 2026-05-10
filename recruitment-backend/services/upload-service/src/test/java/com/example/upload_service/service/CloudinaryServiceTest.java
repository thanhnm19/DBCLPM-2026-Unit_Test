package com.example.upload_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.example.upload_service.testsupport.UploadedFileRecord;
import com.example.upload_service.testsupport.UploadedFileRecordRepository;

// Test Case ID: UPLOAD-CLOUDINARY-SERVICE
@SpringBootTest
@Transactional
@DisplayName("Kiểm thử CloudinaryService")
class CloudinaryServiceTest {

    @MockitoBean
    private Cloudinary cloudinaryClient;

    @Autowired
    private CloudinaryService cloudinaryService;

    @Autowired
    private UploadedFileRecordRepository uploadedFileRecordRepository;

    private Uploader cloudinaryUploader;

    @BeforeEach
    void configureCloudinaryUploaderMock() {
        cloudinaryUploader = mock(Uploader.class);
        when(cloudinaryClient.uploader()).thenReturn(cloudinaryUploader);
    }

    // Test Case ID: UTIL-CL01
    @Test
    @DisplayName("UTIL-CL01: upload trả về map kết quả từ Cloudinary khi file hợp lệ")
    void upload_WithValidFile_ReturnsCloudinaryResponseMap() throws IOException {
        // Arrange
        MultipartFile imageFile = mock(MultipartFile.class);
        byte[] imageBytes = "fake-image-content".getBytes(StandardCharsets.UTF_8);
        String expectedSecureUrl = "https://cloudinary.com/sample.jpg";
        String expectedPublicId = "sample_id";
        Map<String, Object> cloudinaryResponse = new HashMap<>();
        cloudinaryResponse.put("secure_url", expectedSecureUrl);
        cloudinaryResponse.put("public_id", expectedPublicId);

        when(imageFile.getBytes()).thenReturn(imageBytes);
        when(cloudinaryUploader.upload(eq(imageBytes), any())).thenReturn(cloudinaryResponse);

        // Act
        Map<String, Object> actualUploadResponse = cloudinaryService.upload(imageFile);
        uploadedFileRecordRepository.save(new UploadedFileRecord(
                (String) actualUploadResponse.get("secure_url"),
                (String) actualUploadResponse.get("public_id")));

        // Assert
        assertThat(actualUploadResponse)
                .containsEntry("secure_url", expectedSecureUrl)
                .containsEntry("public_id", expectedPublicId);
        verify(cloudinaryUploader, times(1)).upload(eq(imageBytes), any());

        // CheckDB
        UploadedFileRecord savedUploadRecord = uploadedFileRecordRepository
                .findBySecureUrl(expectedSecureUrl)
                .orElseThrow();
        assertThat(savedUploadRecord.getPublicId()).isEqualTo(expectedPublicId);
    }

    // Test Case ID: UTIL-CL05
    @Test
    @DisplayName("UTIL-CL05: upload ném IOException khi không đọc được dữ liệu file")
    void upload_WhenFileBytesCannotBeRead_PropagatesIOException() throws IOException {
        // Arrange
        MultipartFile unreadableFile = mock(MultipartFile.class);
        when(unreadableFile.getBytes()).thenThrow(new IOException("Cannot read file bytes"));

        // Act & Assert
        assertThatThrownBy(() -> cloudinaryService.upload(unreadableFile))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Cannot read file bytes");
        verify(cloudinaryUploader, never()).upload(any(byte[].class), any());

        // CheckDB
        assertThat(uploadedFileRecordRepository.count()).isZero();
    }

    // Test Case ID: UTIL-CL06
    @Test
    @DisplayName("UTIL-CL06: upload trả về map rỗng khi Cloudinary trả response rỗng")
    void upload_WhenCloudinaryReturnsEmptyMap_ReturnsEmptyMap() throws IOException {
        // Arrange
        MultipartFile imageFile = mock(MultipartFile.class);
        byte[] imageBytes = "any-content".getBytes(StandardCharsets.UTF_8);
        Map<String, Object> emptyCloudinaryResponse = new HashMap<>();

        when(imageFile.getBytes()).thenReturn(imageBytes);
        when(cloudinaryUploader.upload(any(byte[].class), any())).thenReturn(emptyCloudinaryResponse);

        // Act
        Map<String, Object> actualUploadResponse = cloudinaryService.upload(imageFile);

        // Assert
        assertThat(actualUploadResponse).isEmpty();
        verify(cloudinaryUploader, times(1)).upload(any(byte[].class), any());

        // CheckDB
        assertThat(uploadedFileRecordRepository.count()).isZero();
    }

    // Test Case ID: UTIL-CL02
    @Test
    @DisplayName("UTIL-CL02: uploadFile trả về secure_url khi file hợp lệ")
    void uploadFile_WithValidFile_ReturnsSecureUrl() throws IOException {
        // Arrange
        MultipartFile documentFile = mock(MultipartFile.class);
        byte[] documentBytes = "valid-file-bytes".getBytes(StandardCharsets.UTF_8);
        String expectedSecureUrl = "https://res.cloudinary.com/test/image/upload/v1/file.pdf";
        Map<String, Object> cloudinaryResponse = new HashMap<>();
        cloudinaryResponse.put("secure_url", expectedSecureUrl);
        cloudinaryResponse.put("public_id", "file_pdf");

        when(documentFile.getBytes()).thenReturn(documentBytes);
        when(cloudinaryUploader.upload(any(byte[].class), any())).thenReturn(cloudinaryResponse);

        // Act
        String actualSecureUrl = cloudinaryService.uploadFile(documentFile);
        uploadedFileRecordRepository.save(new UploadedFileRecord(actualSecureUrl, "file_pdf"));

        // Assert
        assertThat(actualSecureUrl).isEqualTo(expectedSecureUrl);
        verify(cloudinaryUploader, times(1)).upload(any(byte[].class), any());

        // CheckDB
        assertThat(uploadedFileRecordRepository.findBySecureUrl(expectedSecureUrl))
                .isPresent()
                .get()
                .extracting(UploadedFileRecord::getSecureUrl)
                .isEqualTo(expectedSecureUrl);
    }

    // Test Case ID: UTIL-CL03
    @Test
    @DisplayName("UTIL-CL03: uploadFile bọc IOException từ Cloudinary thành RuntimeException")
    void uploadFile_WhenCloudinaryThrowsIOException_ThrowsRuntimeException() throws IOException {
        // Arrange
        MultipartFile documentFile = mock(MultipartFile.class);
        byte[] documentBytes = "broken-file".getBytes(StandardCharsets.UTF_8);

        when(documentFile.getBytes()).thenReturn(documentBytes);
        when(cloudinaryUploader.upload(any(byte[].class), any()))
                .thenThrow(new IOException("Connection timeout"));

        // Act & Assert
        assertThatThrownBy(() -> cloudinaryService.uploadFile(documentFile))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(IOException.class);

        // CheckDB
        assertThat(uploadedFileRecordRepository.count()).isZero();
    }

    // Test Case ID: UTIL-CL04
    @Test
    @DisplayName("UTIL-CL04: uploadFile xử lý file rỗng mà không ném NullPointerException")
    void uploadFile_WithEmptyFile_ReturnsSecureUrlFromCloudinary() throws IOException {
        // Arrange
        MultipartFile emptyFile = mock(MultipartFile.class);
        byte[] emptyBytes = new byte[0];
        String expectedSecureUrl = "https://res.cloudinary.com/empty.bin";
        Map<String, Object> cloudinaryResponse = new HashMap<>();
        cloudinaryResponse.put("secure_url", expectedSecureUrl);
        cloudinaryResponse.put("public_id", "empty_bin");

        when(emptyFile.getBytes()).thenReturn(emptyBytes);
        when(cloudinaryUploader.upload(any(byte[].class), any())).thenReturn(cloudinaryResponse);

        // Act
        String actualSecureUrl = cloudinaryService.uploadFile(emptyFile);
        uploadedFileRecordRepository.save(new UploadedFileRecord(actualSecureUrl, "empty_bin"));

        // Assert
        assertThat(actualSecureUrl).isEqualTo(expectedSecureUrl);
        verify(cloudinaryUploader, times(1)).upload(any(byte[].class), any());

        // CheckDB
        assertThat(uploadedFileRecordRepository.findBySecureUrl(expectedSecureUrl)).isPresent();
    }

    // Test Case ID: UTIL-CL07
    @Test
    @DisplayName("UTIL-CL07: uploadFile trả về null khi response không có secure_url")
    void uploadFile_WhenSecureUrlIsMissing_ReturnsNull() throws IOException {
        // Arrange
        MultipartFile imageFile = mock(MultipartFile.class);
        byte[] imageBytes = "data".getBytes(StandardCharsets.UTF_8);
        Map<String, Object> cloudinaryResponseWithoutSecureUrl = new HashMap<>();
        cloudinaryResponseWithoutSecureUrl.put("public_id", "abc123");
        cloudinaryResponseWithoutSecureUrl.put("url", "http://res.cloudinary.com/abc123");

        when(imageFile.getBytes()).thenReturn(imageBytes);
        when(cloudinaryUploader.upload(any(byte[].class), any())).thenReturn(cloudinaryResponseWithoutSecureUrl);

        // Act
        String actualSecureUrl = cloudinaryService.uploadFile(imageFile);

        // Assert
        assertThat(actualSecureUrl).isNull();
        verify(cloudinaryUploader, times(1)).upload(any(byte[].class), any());

        // CheckDB
        assertThat(uploadedFileRecordRepository.count()).isZero();
    }

    // Test Case ID: UTIL-CL08
    @Test
    @DisplayName("UTIL-CL08: uploadFile trả về null an toàn khi secure_url là null")
    void uploadFile_WhenSecureUrlIsNull_ReturnsNull() throws IOException {
        // Arrange
        MultipartFile imageFile = mock(MultipartFile.class);
        byte[] imageBytes = "data".getBytes(StandardCharsets.UTF_8);
        Map<String, Object> cloudinaryResponseWithNullSecureUrl = new HashMap<>();
        cloudinaryResponseWithNullSecureUrl.put("secure_url", null);
        cloudinaryResponseWithNullSecureUrl.put("public_id", "xyz789");

        when(imageFile.getBytes()).thenReturn(imageBytes);
        when(cloudinaryUploader.upload(any(byte[].class), any())).thenReturn(cloudinaryResponseWithNullSecureUrl);

        // Act
        String actualSecureUrl = cloudinaryService.uploadFile(imageFile);

        // Assert
        assertThat(actualSecureUrl).isNull();
        verify(cloudinaryUploader, times(1)).upload(any(byte[].class), any());

        // CheckDB
        assertThat(uploadedFileRecordRepository.count()).isZero();
    }

    // Test Case ID: UTIL-CL09
    @Test
    @DisplayName("UTIL-CL09: uploadFile ném NullPointerException khi file là null")
    void uploadFile_WhenFileIsNull_ThrowsNullPointerException() throws IOException {
        // Arrange
        MultipartFile nullFile = null;

        // Act & Assert
        assertThatThrownBy(() -> cloudinaryService.uploadFile(nullFile))
                .isInstanceOf(NullPointerException.class);
        verify(cloudinaryUploader, never()).upload(any(byte[].class), any());

        // CheckDB
        assertThat(uploadedFileRecordRepository.count()).isZero();
    }
}
