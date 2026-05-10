package com.example.candidate_service.service;

import com.example.candidate_service.dto.Meta;
import com.example.candidate_service.dto.PaginationDTO;
import com.example.candidate_service.dto.Response;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho JobService (Candidate Service side) - Module 7.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JobService Unit Tests (Candidate Service)")
class JobServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private JobService jobService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        jobService = new JobService(restTemplate, objectMapper);
        ReflectionTestUtils.setField(jobService, "jobServiceUrl", "http://job-service");
    }

    @Test
    @DisplayName("JS-TC-001: getJobPositionById")
    void testGetJobPositionById_JS_TC_001() {
        ObjectNode data = objectMapper.createObjectNode().put("id", 1);
        Response<JsonNode> body = new Response<>();
        body.setData(data);

        ResponseEntity<Response<JsonNode>> mockedResponse = (ResponseEntity<Response<JsonNode>>) (ResponseEntity<?>) ResponseEntity.ok(body);
        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class))).thenReturn(mockedResponse);

        ResponseEntity<JsonNode> result = jobService.getJobPositionById(1L, "token");
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("JS-TC-002: getJobPositionById - notFound")
    void testGetJobPositionById_NotFound_JS_TC_002() {
        Response<JsonNode> body = new Response<>();
        body.setData(null);

        ResponseEntity<Response<JsonNode>> mockedResponse = (ResponseEntity<Response<JsonNode>>) (ResponseEntity<?>) ResponseEntity.ok(body);
        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class))).thenReturn(mockedResponse);

        ResponseEntity<JsonNode> result = jobService.getJobPositionById(1L, "token");
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("JS-TC-003: getJobPositionIdsByDepartmentId - Pagination")
    void testGetJobPositionIdsByDepartmentId_Pagination_JS_TC_003() {
        PaginationDTO p1 = new PaginationDTO();
        p1.setResult(List.of(Map.of("id", 101)));
        Meta m1 = new Meta(); m1.setPages(2); p1.setMeta(m1);
        Response<PaginationDTO> body1 = new Response<>(); body1.setData(p1);

        PaginationDTO p2 = new PaginationDTO();
        p2.setResult(List.of(Map.of("id", 102)));
        Response<PaginationDTO> body2 = new Response<>(); body2.setData(p2);

        ResponseEntity<Response<PaginationDTO>> resp1 = (ResponseEntity<Response<PaginationDTO>>) (ResponseEntity<?>) ResponseEntity.ok(body1);
        ResponseEntity<Response<PaginationDTO>> resp2 = (ResponseEntity<Response<PaginationDTO>>) (ResponseEntity<?>) ResponseEntity.ok(body2);

        when(restTemplate.exchange(contains("page=1"), any(), any(), any(ParameterizedTypeReference.class))).thenReturn(resp1);
        when(restTemplate.exchange(contains("page=2"), any(), any(), any(ParameterizedTypeReference.class))).thenReturn(resp2);

        List<Long> ids = jobService.getJobPositionIdsByDepartmentId(1L, "token");
        assertThat(ids).containsExactly(101L, 102L);
    }

    @Test
    @DisplayName("JS-TC-004: getJobPositionsByDepartmentId")
    void testGetJobPositionsByDepartmentId_JS_TC_004() {
        PaginationDTO p = new PaginationDTO();
        p.setResult(List.of(Map.of("id", 1)));
        Response<PaginationDTO> body = new Response<>(); body.setData(p);

        ResponseEntity<Response<PaginationDTO>> mockedResponse = (ResponseEntity<Response<PaginationDTO>>) (ResponseEntity<?>) ResponseEntity.ok(body);
        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class))).thenReturn(mockedResponse);

        Map<Long, JsonNode> result = jobService.getJobPositionsByDepartmentId(1L, "token");
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("JS-TC-005: getJobPositionByIdSimple")
    void testGetJobPositionByIdSimple_JS_TC_005() {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("statusCode", 200);
        body.set("data", objectMapper.createObjectNode().put("id", 1));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(JsonNode.class))).thenReturn(ResponseEntity.ok(body));

        ResponseEntity<JsonNode> result = jobService.getJobPositionByIdSimple(1L, "token");
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("JS-TC-006: getJobPositionById - null token")
    void testGetJobPositionById_NullToken() {
        // 2. Thực thi & 3. Kiểm tra
        assertThat(jobService.getJobPositionById(1L, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("JS-TC-007: jobService - handle node mapping edge cases")
    void jobServiceMethods_WithNodeMappingEdgeCases_ShouldHandleGracefully() {
        PaginationDTO p = new PaginationDTO();
        p.setResult(List.of(Map.of("id", 1)));
        Response<PaginationDTO> respP = new Response<>(); respP.setData(p);
        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(respP));
        
        doReturn(null).when(objectMapper).valueToTree(any());
        assertThat(jobService.getJobPositionsByDepartmentId(1L, "token")).isEmpty();
    }

    @Test
    @DisplayName("JS-TC-008: getJobPositionByIdSimple - null body response")
    void testGetJobPositionByIdSimple_NullBody() {
        // 1. Chuẩn bị
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(null));
        
        // 2. Thực thi & 3. Kiểm tra
        assertThat(jobService.getJobPositionByIdSimple(1L, "token").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("JS-TC-010: getJobPositionByIdSimple - body without data")
    void testGetJobPositionByIdSimple_BodyNoData() {
        // 1. Chuẩn bị
        ObjectNode bodyNoData = objectMapper.createObjectNode();
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(bodyNoData));
        
        // 2. Thực thi & 3. Kiểm tra
        assertThat(jobService.getJobPositionByIdSimple(1L, "token").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("JS-TC-009: getJobPositionById - handle null response")
    void testGetJobPositionById_NullResponse() {
        // 1. Chuẩn bị
        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(null));
        
        // 2. Thực thi & 3. Kiểm tra
        assertThat(jobService.getJobPositionById(1L, "token").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("JS-TC-011: getJobPositionIdsByDepartmentId - handle null response")
    void testGetJobPositionIdsByDepartmentId_NullResponse() {
        // 1. Chuẩn bị
        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(null));
        
        // 2. Thực thi & 3. Kiểm tra
        assertThat(jobService.getJobPositionIdsByDepartmentId(1L, "token")).isEmpty();
    }

    @Test
    @DisplayName("JS-TC-012: getJobPositionById - API Down")
    void testGetJobPositionById_ApiDown() {
        // 1. Chuẩn bị
        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("API Down"));
        
        // 2. Thực thi & 3. Kiểm tra
        assertThat(jobService.getJobPositionById(1L, "token").getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("JS-TC-019: getJobPositionIdsByDepartmentId - API Down")
    void testGetJobPositionIdsByDepartmentId_ApiDown() {
        // 1. Chuẩn bị
        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("API Down"));
        
        // 2. Thực thi & 3. Kiểm tra
        assertThat(jobService.getJobPositionIdsByDepartmentId(1L, "token")).isEmpty();
    }

    @Test
    @DisplayName("JS-TC-020: getJobPositionByIdSimple - API Down")
    void testGetJobPositionByIdSimple_ApiDown() {
        // 1. Chuẩn bị
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(JsonNode.class)))
                .thenThrow(new RuntimeException("API Down"));
        
        // 2. Thực thi & 3. Kiểm tra
        assertThat(jobService.getJobPositionByIdSimple(1L, "token").getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("JS-TC-013: getJobPositionsByIds - null input")
    void testGetJobPositionsByIds_NullInput() {
        // 2. Thực thi & 3. Kiểm tra
        assertThat(jobService.getJobPositionsByIdsSimple(null, "token")).isEmpty();
    }

    @Test
    @DisplayName("JS-TC-021: getJobPositionsByIds - empty list input")
    void testGetJobPositionsByIds_EmptyList() {
        // 2. Thực thi & 3. Kiểm tra
        assertThat(jobService.getJobPositionsByIdsSimple(Collections.emptyList(), "token")).isEmpty();
    }

    @Test
    @DisplayName("JS-TC-014: getJobPositionIds - handle sub-page exception")
    void getJobPositionIds_WithSubPageException_ShouldHandleGracefully() {
        PaginationDTO p1 = new PaginationDTO();
        p1.setResult(List.of(Map.of("id", 101)));
        Meta m1 = new Meta(); m1.setPages(2); p1.setMeta(m1);
        Response<PaginationDTO> b1 = new Response<>(); b1.setData(p1);
        when(restTemplate.exchange(contains("page=1"), any(), any(), any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(b1));
        when(restTemplate.exchange(contains("page=2"), any(), any(), any(ParameterizedTypeReference.class))).thenThrow(new RuntimeException());
        
        // Vẫn phải lấy được data của page 1 thay vì trả về rỗng hoàn toàn
        assertThat(jobService.getJobPositionIdsByDepartmentId(1L, "token")).contains(101L);
    }

    @Test
    @DisplayName("JS-TC-015: getJobPositionById - empty token")
    void testGetJobPositionById_EmptyToken() {
        // 2. Thực thi & 3. Kiểm tra
        assertThat(jobService.getJobPositionById(1L, "").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("JS-TC-016: getJobPositionIdsByDepartmentId - null token")
    void testGetJobPositionIdsByDepartmentId_NullToken() {
        // 2. Thực thi & 3. Kiểm tra
        assertThat(jobService.getJobPositionIdsByDepartmentId(1L, null)).isEmpty();
    }

    @Test
    @DisplayName("JS-TC-017: getJobPositionIdsByDepartmentId - empty token")
    void testGetJobPositionIdsByDepartmentId_EmptyToken() {
        // 2. Thực thi & 3. Kiểm tra
        assertThat(jobService.getJobPositionIdsByDepartmentId(1L, "")).isEmpty();
    }

    @Test
    @DisplayName("JS-TC-018: getJobPositionsByDepartmentId - null token")
    void testGetJobPositionsByDepartmentId_NullToken() {
        // 2. Thực thi & 3. Kiểm tra
        assertThat(jobService.getJobPositionsByDepartmentId(1L, null)).isEmpty();
    }
}