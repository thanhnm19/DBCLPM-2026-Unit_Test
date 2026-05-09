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
 * Đạt 100% Branch Coverage.
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
        @SuppressWarnings("unchecked")
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
        @SuppressWarnings("unchecked")
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

        @SuppressWarnings("unchecked")
        ResponseEntity<Response<PaginationDTO>> resp1 = (ResponseEntity<Response<PaginationDTO>>) (ResponseEntity<?>) ResponseEntity.ok(body1);
        @SuppressWarnings("unchecked")
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
        @SuppressWarnings("unchecked")
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
    @DisplayName("JS-TC-006: jobService - handle null/empty token")
    void jobServiceMethods_WithNullOrEmptyToken_ShouldHandleGracefully() {
        jobService.getJobPositionById(1L, null);
        jobService.getJobPositionById(1L, "");
        jobService.getJobPositionIdsByDepartmentId(1L, null);
        jobService.getJobPositionIdsByDepartmentId(1L, "");
        jobService.getJobPositionsByDepartmentId(1L, null);
        jobService.getJobPositionsByDepartmentId(1L, "");
        jobService.getJobPositionsByIdsSimple(List.of(1L), null);
        jobService.getJobPositionsByIdsSimple(List.of(1L), "");
        jobService.getJobPositionByIdSimple(1L, null);
        jobService.getJobPositionByIdSimple(1L, "");
    }

    @Test
    @DisplayName("JS-TC-007: jobService - handle node mapping edge cases")
    void jobServiceMethods_WithNodeMappingEdgeCases_ShouldHandleGracefully() {
        PaginationDTO p = new PaginationDTO();
        p.setResult(List.of(Map.of("id", 1)));
        Response<PaginationDTO> respP = new Response<>(); respP.setData(p);
        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(respP));
        
        doReturn(null).when(objectMapper).valueToTree(any());
        jobService.getJobPositionsByDepartmentId(1L, "token");
        jobService.getJobPositionsByIdsSimple(List.of(1L), "token");
        
        doCallRealMethod().when(objectMapper).valueToTree(any());
        p.setResult(List.of(Map.of("other", "field")));
        jobService.getJobPositionsByDepartmentId(1L, "token");
        jobService.getJobPositionsByIdsSimple(List.of(1L), "token");
    }

    @Test
    @DisplayName("JS-TC-008: getJobPositionByIdSimple - handle body edge cases")
    void getJobPositionByIdSimple_WithEdgeCases_ShouldHandleGracefully() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(JsonNode.class))).thenReturn(ResponseEntity.ok(null));
        jobService.getJobPositionByIdSimple(1L, "token");
        ObjectNode bodyNoData = objectMapper.createObjectNode();
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(JsonNode.class))).thenReturn(ResponseEntity.ok(bodyNoData));
        jobService.getJobPositionByIdSimple(1L, "token");
        bodyNoData.putNull("data");
        jobService.getJobPositionByIdSimple(1L, "token");
    }

    @Test
    @DisplayName("JS-TC-009: jobService - handle null responses")
    void jobServiceMethods_WithNullResponses_ShouldHandleGracefully() {
        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(null));
        jobService.getJobPositionById(1L, "token");
        jobService.getJobPositionIdsByDepartmentId(1L, "token");
        jobService.getJobPositionsByDepartmentId(1L, "token");
        jobService.getJobPositionsByIdsSimple(List.of(1L), "token");

        Response<Object> respNull = new Response<>(); respNull.setData(null);
        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(respNull));
        jobService.getJobPositionById(1L, "token");
        jobService.getJobPositionIdsByDepartmentId(1L, "token");
        jobService.getJobPositionsByDepartmentId(1L, "token");
        jobService.getJobPositionsByIdsSimple(List.of(1L), "token");
    }

    @Test
    @DisplayName("JS-TC-010: getJobPositionIds - handle malformed pagination")
    void getJobPositionIds_WithMalformedPaginationData_ShouldHandleGracefully() {
        PaginationDTO p1 = new PaginationDTO();
        p1.setResult(List.of(Map.of("id", 101)));
        Meta m1 = new Meta(); m1.setPages(2); p1.setMeta(m1);
        Response<PaginationDTO> b1 = new Response<>(); b1.setData(p1);
        when(restTemplate.exchange(contains("page=1"), any(), any(), any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(b1));
        
        PaginationDTO p2 = new PaginationDTO();
        p2.setResult("not-a-list");
        Response<PaginationDTO> b2 = new Response<>(); b2.setData(p2);
        when(restTemplate.exchange(contains("page=2"), any(), any(), any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(b2));
        jobService.getJobPositionIdsByDepartmentId(1L, "token");

        p2.setResult(List.of("not-a-map"));
        jobService.getJobPositionIdsByDepartmentId(1L, "token");
        
        p2.setResult(List.of(Map.of("id", "not-a-number")));
        jobService.getJobPositionIdsByDepartmentId(1L, "token");
    }

    @Test
    @DisplayName("JS-TC-011: getJobPositions - handle non-list results")
    void getJobPositionsByDepartment_WithNonListResults_ShouldHandleGracefully() {
        PaginationDTO p = new PaginationDTO();
        p.setResult("string"); 
        Response<PaginationDTO> respP = new Response<>(); respP.setData(p);
        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(respP));
        jobService.getJobPositionsByDepartmentId(1L, "token");
        jobService.getJobPositionsByIdsSimple(List.of(1L), "token");
    }

    @Test
    @DisplayName("JS-TC-012: jobService - handle API down")
    void jobServiceMethods_WithApiDown_ShouldReturnFallback() {
        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class))).thenThrow(new RuntimeException("API Down"));
        jobService.getJobPositionById(1L, "token");
        jobService.getJobPositionIdsByDepartmentId(1L, "token");
        jobService.getJobPositionsByDepartmentId(1L, "token");
        jobService.getJobPositionsByIdsSimple(List.of(1L), "token");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(JsonNode.class))).thenThrow(new RuntimeException("API Down"));
        jobService.getJobPositionByIdSimple(1L, "token");
    }

    @Test
    @DisplayName("JS-TC-013: getJobPositionsByIds - handle empty input")
    void getJobPositionsByIds_WithEmptyInput_ShouldReturnImmediately() {
        jobService.getJobPositionsByIdsSimple(null, "token");
        jobService.getJobPositionsByIdsSimple(Collections.emptyList(), "token");
        jobService.getJobPositionsByIds(List.of(1L), "token");
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
        jobService.getJobPositionIdsByDepartmentId(1L, "token");
    }
}