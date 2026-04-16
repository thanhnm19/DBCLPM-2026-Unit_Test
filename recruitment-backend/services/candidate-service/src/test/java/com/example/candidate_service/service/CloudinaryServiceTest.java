package com.example.candidate_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.cloudinary.utils.ObjectUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("CloudinaryService Unit Test")
class CloudinaryServiceTest {

    // ===== Mock dependencies =====
    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    // ===== SUT =====
    @InjectMocks
    private CloudinaryService cloudinaryService;

    // =========================================================
    // CS-TC00
    // Function: upload(MultipartFile file)
    // =========================================================
    @Test
    @DisplayName("CS-TC00: upload - file hợp lệ upload thành công, file lỗi ném exception")
    void csTc00_upload_validFile_success_and_error_throwsException() throws Exception {
        // ---------- Arrange (success case) ----------
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        byte[] fileBytes = "dummy".getBytes();

        when(cloudinary.uploader()).thenReturn(uploader);
        when(file.getBytes()).thenReturn(fileBytes);

        Map<String, Object> successResult = new HashMap<>();
        successResult.put("secure_url", "https://res.cloudinary.com/demo/image/upload/v1/test.png");

        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(successResult);

        // ---------- Act (success case) ----------
        Map<String, Object> actual = cloudinaryService.upload(file);

        // ---------- Assert (success case) ----------
        assertThat(actual).isNotNull();
        assertThat(actual).containsEntry("secure_url", "https://res.cloudinary.com/demo/image/upload/v1/test.png");

        // verify interactions
        verify(cloudinary, times(1)).uploader();
        verify(file, times(1)).getBytes();
        verify(uploader, times(1)).upload(any(byte[].class), anyMap());

        // ---------- Arrange (error case) ----------
        MultipartFile errorFile = org.mockito.Mockito.mock(MultipartFile.class);
        when(errorFile.getBytes()).thenThrow(new IOException("read bytes failed"));

        // ---------- Act + Assert (error case) ----------
        assertThatThrownBy(() -> cloudinaryService.upload(errorFile))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("read bytes failed");

        // verify - uploader.upload should never be called for errorFile (failed before calling cloudinary upload)
        verify(uploader, times(1)).upload(any(byte[].class), anyMap()); // still only 1 time from success case
        verify(errorFile, times(1)).getBytes();
    }

    // =========================================================
    // CS-TC01
    // Function: uploadFile(MultipartFile file)
    // =========================================================
    @Test
    @DisplayName("CS-TC01: uploadFile - gọi wrapper upload và trả về kết quả đúng")
    void csTc01_uploadFile_callsWrapperUpload_and_returnsCorrectResult() throws Exception {
        // ---------- Arrange ----------
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        byte[] fileBytes = "dummy".getBytes();

        when(cloudinary.uploader()).thenReturn(uploader);
        when(file.getBytes()).thenReturn(fileBytes);

        Map<String, Object> uploadResult = new HashMap<>();
        uploadResult.put("secure_url", "https://res.cloudinary.com/demo/raw/upload/v1/cv.docx");
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(uploadResult);

        // capture options map
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> optionsCaptor = ArgumentCaptor.forClass((Class) Map.class);

        // ---------- Act ----------
        String url = cloudinaryService.uploadFile(file);

        // ---------- Assert ----------
        assertThat(url).isEqualTo("https://res.cloudinary.com/demo/raw/upload/v1/cv.docx");

        // verify dependency interaction + mapping format
        verify(cloudinary, times(1)).uploader();
        verify(file, times(1)).getBytes();
        verify(uploader, times(1)).upload(any(byte[].class), optionsCaptor.capture());

        Map<String, Object> capturedOptions = optionsCaptor.getValue();
        assertThat(capturedOptions).containsEntry("resource_type", "auto");
        assertThat(capturedOptions).containsEntry("type", "upload");
    }

    // =========================================================
    // Additional coverage (null / empty / cloudinary error)
    // =========================================================

    @Test
    @DisplayName("CS-EX01: uploadFile - file null ném NullPointerException và không gọi Cloudinary client")
    void csEx01_uploadFile_nullFile_throwsNpe_and_neverCallCloudinary() throws IOException {
        // ---------- Act + Assert ----------
        assertThatThrownBy(() -> cloudinaryService.uploadFile(null))
                .isInstanceOf(NullPointerException.class);

        // ---------- Verify ----------
        verify(cloudinary, never()).uploader();
        verify(uploader, never()).upload(any(byte[].class), anyMap());
    }

    @Test
    @DisplayName("CS-EX02: uploadFile - file rỗng (byte[] empty) vẫn gọi upload và trả về secure_url đúng")
    void csEx02_uploadFile_emptyBytes_stillUploads_and_returnsSecureUrl() throws Exception {
        // ---------- Arrange ----------
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        byte[] empty = new byte[0];

        when(cloudinary.uploader()).thenReturn(uploader);
        when(file.getBytes()).thenReturn(empty);

        Map<String, Object> uploadResult = new HashMap<>();
        uploadResult.put("secure_url", "https://res.cloudinary.com/demo/image/upload/v1/empty.png");
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(uploadResult);

        // ---------- Act ----------
        String url = cloudinaryService.uploadFile(file);

        // ---------- Assert ----------
        assertThat(url).isEqualTo("https://res.cloudinary.com/demo/image/upload/v1/empty.png");

        // ---------- Verify ----------
        verify(cloudinary, times(1)).uploader();
        verify(file, times(1)).getBytes();
        verify(uploader, times(1)).upload(any(byte[].class), anyMap());
    }

    @Test
    @DisplayName("CS-EX03: uploadFile - Cloudinary uploader ném IOException thì service wrap RuntimeException")
    void csEx03_uploadFile_cloudinaryThrowsIOException_wrapsRuntimeException() throws Exception {
        // ---------- Arrange ----------
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(file.getBytes()).thenReturn("dummy".getBytes());

        when(uploader.upload(any(byte[].class), anyMap())).thenThrow(new IOException("cloudinary down"));

        // ---------- Act + Assert ----------
        assertThatThrownBy(() -> cloudinaryService.uploadFile(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Không thể upload file")
                .hasCauseInstanceOf(IOException.class);

        // ---------- Verify ----------
        verify(cloudinary, times(1)).uploader();
        verify(file, times(1)).getBytes();
        verify(uploader, times(1)).upload(any(byte[].class), anyMap());
    }

    @Test
    @DisplayName("CS-EX04: uploadFile - uploadResult không có secure_url thì trả về null và vẫn verify options map")
    void csEx04_uploadFile_missingSecureUrl_returnsNull_and_verifyOptions() throws Exception {
        // ---------- Arrange ----------
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(file.getBytes()).thenReturn("dummy".getBytes());

        Map<String, Object> uploadResult = new HashMap<>();
        // secure_url intentionally missing
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(uploadResult);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> optionsCaptor = ArgumentCaptor.forClass((Class) Map.class);

        // ---------- Act ----------
        String url = cloudinaryService.uploadFile(file);

        // ---------- Assert ----------
        assertThat(url).isNull();

        // ---------- Verify ----------
        verify(uploader, times(1)).upload(any(byte[].class), optionsCaptor.capture());
        assertThat(optionsCaptor.getValue())
                .isEqualTo(ObjectUtils.asMap("resource_type", "auto", "type", "upload"));
    }
}