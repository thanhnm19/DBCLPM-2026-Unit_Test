package com.example.candidate_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.cloudinary.utils.ObjectUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("CloudinaryService Unit Test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CloudinaryServiceTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    @InjectMocks
    private CloudinaryService cloudinaryService;

    @Test
    @DisplayName("CLD-TC-001: upload - upload file thành công")
    void testUpload_CLD_TC_001() throws Exception {
        // Testcase ID: CLD-TC-001
        // Objective: Xác nhận upload thành công

        // arrange
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        byte[] bytes = "dummy".getBytes();

        when(cloudinary.uploader()).thenReturn(uploader);
        when(file.getBytes()).thenReturn(bytes);

        Map<String, Object> expected = new HashMap<>();
        expected.put("secure_url", "https://res.cloudinary.com/demo/file.pdf");

        when(uploader.upload(eq(bytes), eq(ObjectUtils.emptyMap()))).thenReturn(expected);

        // act
        Map<String, Object> actual = cloudinaryService.upload(file);

        // assert
        assertNotNull(actual);
        assertEquals("https://res.cloudinary.com/demo/file.pdf", actual.get("secure_url"));

        verify(cloudinary, times(1)).uploader();
        verify(file, times(1)).getBytes();
        verify(uploader, times(1)).upload(eq(bytes), eq(ObjectUtils.emptyMap()));
        verifyNoMoreInteractions(cloudinary, uploader, file);
    }

    @Test
    @DisplayName("CLD-TC-002: upload - propagate IOException khi upload lỗi")
    void testUpload_IOException_CLD_TC_002() throws Exception {
        // Testcase ID: CLD-TC-002
        // Objective: Xác nhận propagate IOException

        // arrange
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        byte[] bytes = "dummy".getBytes();

        when(cloudinary.uploader()).thenReturn(uploader);
        when(file.getBytes()).thenReturn(bytes);

        when(uploader.upload(eq(bytes), eq(ObjectUtils.emptyMap()))).thenThrow(new IOException("cloudinary down"));

        // act
        IOException ex = assertThrows(IOException.class, () -> cloudinaryService.upload(file));

        // assert
        assertEquals("cloudinary down", ex.getMessage());

        verify(cloudinary, times(1)).uploader();
        verify(file, times(1)).getBytes();
        verify(uploader, times(1)).upload(eq(bytes), eq(ObjectUtils.emptyMap()));
        verifyNoMoreInteractions(cloudinary, uploader, file);
    }

    @Test
    @DisplayName("CLD-TC-003: uploadFile - lấy đúng secure_url")
    void testUploadFile_ReturnSecureUrl_CLD_TC_003() throws Exception {
        // Testcase ID: CLD-TC-003
        // Objective: Xác nhận lấy đúng secure_url

        // arrange
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        byte[] bytes = "dummy".getBytes();

        when(cloudinary.uploader()).thenReturn(uploader);
        when(file.getBytes()).thenReturn(bytes);

        Map<String, Object> uploadResult = new HashMap<>();
        uploadResult.put("secure_url", "https://res.cloudinary.com/demo/file.pdf");

        when(uploader.upload(eq(bytes), eq(ObjectUtils.asMap("resource_type", "auto", "type", "upload"))))
                .thenReturn(uploadResult);

        // act
        String actualUrl = cloudinaryService.uploadFile(file);

        // assert
        assertNotNull(actualUrl);
        assertEquals("https://res.cloudinary.com/demo/file.pdf", actualUrl);

        verify(cloudinary, times(1)).uploader();
        verify(file, times(1)).getBytes();
        verify(uploader, times(1)).upload(eq(bytes), eq(ObjectUtils.asMap("resource_type", "auto", "type", "upload")));
        verifyNoMoreInteractions(cloudinary, uploader, file);
    }

    @Test
    @DisplayName("CLD-TC-004: uploadFile - chuyển IOException thành RuntimeException")
    void testUploadFile_ThrowRuntimeException_CLD_TC_004() throws Exception {
        // Testcase ID: CLD-TC-004
        // Objective: Xác nhận chuyển lỗi sang RuntimeException("Không thể upload file")

        // arrange
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        byte[] bytes = "dummy".getBytes();

        when(cloudinary.uploader()).thenReturn(uploader);
        when(file.getBytes()).thenReturn(bytes);

        when(uploader.upload(eq(bytes), eq(ObjectUtils.asMap("resource_type", "auto", "type", "upload"))))
                .thenThrow(new IOException("cloudinary down"));

        // act
        RuntimeException ex = assertThrows(RuntimeException.class, () -> cloudinaryService.uploadFile(file));

        // assert
        assertEquals("Không thể upload file", ex.getMessage());
        assertNotNull(ex.getCause());
        assertEquals(IOException.class, ex.getCause().getClass());
        assertEquals("cloudinary down", ex.getCause().getMessage());

        verify(cloudinary, times(1)).uploader();
        verify(file, times(1)).getBytes();
        verify(uploader, times(1)).upload(eq(bytes), eq(ObjectUtils.asMap("resource_type", "auto", "type", "upload")));
        verifyNoMoreInteractions(cloudinary, uploader, file);
    }
}