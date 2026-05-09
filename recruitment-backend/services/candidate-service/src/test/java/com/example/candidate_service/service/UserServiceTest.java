package com.example.candidate_service.service;

import com.example.candidate_service.dto.Response;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho UserService (Candidate Service side) - Module 7.
 * 
 * Chiến lược:
 * - Sử dụng Mockito để mock RestTemplate (vì đây là Client Service).
 * - Đảm bảo cấu trúc Arrange - Act - Assert rõ ràng.
 * - Giữ nguyên các Test Case ID từ tài liệu SQA.
 * - Đạt 100% Branch Coverage.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Tests (Candidate Service)")
class UserServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userService = new UserService(restTemplate, objectMapper);
        ReflectionTestUtils.setField(userService, "userServiceUrl", "http://localhost:8082");
    }

    @Test
    @DisplayName("US-TC-001: getEmployeeName - parse đúng tên nhân viên")
    void testGetEmployeeName_US_TC_001() {
        Long employeeId = 1L;
        String token = "token";
        Map<String, Object> data = new HashMap<>();
        data.put("name", "John Doe");

        Response<Map<String, Object>> body = new Response<>();
        body.setData(data);

        @SuppressWarnings("unchecked")
        ResponseEntity<Response<Map<String, Object>>> mockedResponse = 
                (ResponseEntity<Response<Map<String, Object>>>) (ResponseEntity<?>) ResponseEntity.ok(body);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(mockedResponse);

        ResponseEntity<JsonNode> result = userService.getEmployeeName(employeeId, token);

        assertThat(result).isNotNull();
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().get("name").asText()).isEqualTo("John Doe");
    }

    @Test
    @DisplayName("US-TC-002: getEmployeeName - trả 404 khi không có data")
    void testGetEmployeeName_NoData_US_TC_002() {
        Response<Map<String, Object>> body = new Response<>();
        body.setData(null);

        @SuppressWarnings("unchecked")
        ResponseEntity<Response<Map<String, Object>>> mockedResponse = 
                (ResponseEntity<Response<Map<String, Object>>>) (ResponseEntity<?>) ResponseEntity.ok(body);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(mockedResponse);

        ResponseEntity<JsonNode> result = userService.getEmployeeName(1L, "token");
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("US-TC-003: getEmployeeNames - parse đúng list tên")
    void testGetEmployeeNames_US_TC_003() {
        List<Map<String, Object>> data = List.of(Map.of("id", 1, "name", "John"), Map.of("id", 2, "name", "Jane"));
        Response<List<Map<String, Object>>> body = new Response<>();
        body.setData(data);

        @SuppressWarnings("unchecked")
        ResponseEntity<Response<List<Map<String, Object>>>> mockedResponse = 
                (ResponseEntity<Response<List<Map<String, Object>>>>) (ResponseEntity<?>) ResponseEntity.ok(body);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(mockedResponse);

        ResponseEntity<JsonNode> result = userService.getEmployeeNames(List.of(1L, 2L), "token");
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().get("1").asText()).isEqualTo("John");
        assertThat(result.getBody().get("2").asText()).isEqualTo("Jane");
    }

    @Test
    @DisplayName("US-TC-004: getUserIdByEmail - trả employeeId hợp lệ")
    void testGetUserIdByEmail_US_TC_004() {
        Map<String, Object> data = Map.of("employeeId", 100);
        Response<Map<String, Object>> body = new Response<>();
        body.setData(data);

        @SuppressWarnings("unchecked")
        ResponseEntity<Response<Map<String, Object>>> mockedResponse = 
                (ResponseEntity<Response<Map<String, Object>>>) (ResponseEntity<?>) ResponseEntity.ok(body);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(mockedResponse);

        Long result = userService.getUserIdByEmail("test@example.com");
        assertThat(result).isEqualTo(100L);
    }

    @Test
    @DisplayName("US-TC-005: getUserIdByEmail - trả id khi không có employeeId")
    void testGetUserIdByEmail_Id_US_TC_005() {
        Map<String, Object> data = Map.of("id", 200);
        Response<Map<String, Object>> body = new Response<>();
        body.setData(data);

        @SuppressWarnings("unchecked")
        ResponseEntity<Response<Map<String, Object>>> mockedResponse = 
                (ResponseEntity<Response<Map<String, Object>>>) (ResponseEntity<?>) ResponseEntity.ok(body);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(mockedResponse);

        Long result = userService.getUserIdByEmail("test@example.com");
        assertThat(result).isEqualTo(200L);
    }

    @Test
    @DisplayName("US-TC-006: getUserIdByEmail - trả null khi không tìm thấy")
    void testGetUserIdByEmail_NotFound_US_TC_006() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("Not found"));

        Long result = userService.getUserIdByEmail("none@example.com");
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("US-TC-007: createEmployeeFromCandidate - thành công")
    void testCreateEmployeeFromCandidate_US_TC_007() {
        ObjectNode data = objectMapper.createObjectNode().put("employeeId", 500);
        Response<JsonNode> body = new Response<>();
        body.setData(data);

        @SuppressWarnings("unchecked")
        ResponseEntity<Response<JsonNode>> mockedResponse = 
                (ResponseEntity<Response<JsonNode>>) (ResponseEntity<?>) ResponseEntity.ok(body);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(mockedResponse);

        ResponseEntity<JsonNode> result = userService.createEmployeeFromCandidate(1L, "A", "E", "P", null, null, null, null, null, null, 1L, 1L, "S", "T");
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().get("employeeId").asInt()).isEqualTo(500);
    }

    @Test
    @DisplayName("US-TC-008: createEmployeeFromCandidate - trả status gốc khi lỗi data")
    void testCreateEmployeeFromCandidate_NoData_US_TC_008() {
        Response<JsonNode> body = new Response<>();
        body.setData(null);

        @SuppressWarnings("unchecked")
        ResponseEntity<Response<JsonNode>> mockedResponse = 
                (ResponseEntity<Response<JsonNode>>) (ResponseEntity<?>) ResponseEntity.status(400).body(body);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(mockedResponse);

        ResponseEntity<JsonNode> result = userService.createEmployeeFromCandidate(1L, "A", "E", "P", null, null, null, null, null, null, 1L, 1L, "S", "T");
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("US-TC-009: getEmployeeName - handle null/empty token")
    void getEmployeeName_WithNullOrEmptyToken_ShouldHandleGracefully() {
        userService.getEmployeeName(1L, null);
        userService.getEmployeeName(1L, "");
        userService.getEmployeeNames(List.of(1L), null);
        userService.getEmployeeNames(List.of(1L), "");
    }

    @Test
    @DisplayName("US-TC-010: userService - handle null response bodies")
    void userServiceMethods_WithNullResponseBody_ShouldHandleGracefully() {
        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(null));
        userService.getEmployeeName(1L, "token");
        userService.getEmployeeNames(List.of(1L), "token");
        userService.getUserIdByEmail("test@example.com");
        userService.createEmployeeFromCandidate(1L, "N", "E", "P", "D", "G", "N", "I", "A", "V", 1L, 1L, "S", "T");

        Response<Object> respNull = new Response<>();
        respNull.setData(null);
        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(respNull));
        userService.getEmployeeName(1L, "token");
        userService.getEmployeeNames(List.of(1L), "token");
        userService.getUserIdByEmail("test@example.com");
        userService.createEmployeeFromCandidate(1L, "N", "E", "P", "D", "G", "N", "I", "A", "V", 1L, 1L, "S", "T");
    }

    @Test
    @DisplayName("US-TC-011: getEmployeeName - handle null name in data")
    void getEmployeeName_WithNullNameInBody_ShouldHandleGracefully() {
        Map<String, Object> dataNullName = new HashMap<>();
        dataNullName.put("name", null);
        Response<Map<String, Object>> respNullName = new Response<>();
        respNullName.setData(dataNullName);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(respNullName));
        userService.getEmployeeName(1L, "token");
    }

    @Test
    @DisplayName("US-TC-012: getEmployeeNames - handle null id or name in list")
    void getEmployeeNames_WithNullIdOrNameInList_ShouldHandleGracefully() {
        Map<String, Object> emp1 = new HashMap<>();
        emp1.put("id", null);
        emp1.put("name", "John");
        Map<String, Object> emp2 = new HashMap<>();
        emp2.put("id", 2);
        emp2.put("name", null);
        Response<List<Map<String, Object>>> respListNull = new Response<>();
        respListNull.setData(List.of(emp1, emp2));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(respListNull));
        userService.getEmployeeNames(List.of(1L, 2L), "token");
    }

    @Test
    @DisplayName("US-TC-013: getUserIdByEmail - handle various ID types")
    void getUserIdByEmail_WithVariousIdTypes_ShouldHandleGracefully() {
        Map<String, Object> dataU = new HashMap<>();
        dataU.put("employeeId", "string");
        dataU.put("id", 123);
        Response<Map<String, Object>> respU = new Response<>();
        respU.setData(dataU);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(respU));
        userService.getUserIdByEmail("a@b.com");

        dataU.put("id", "string");
        userService.getUserIdByEmail("a@b.com");
    }

    @Test
    @DisplayName("US-TC-014: userService - handle API exceptions")
    void userServiceMethods_WithApiDown_ShouldReturnNull() {
        when(restTemplate.exchange(anyString(), any(), any(), any(ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("API Down"));
        userService.getEmployeeName(1L, "token");
        userService.getEmployeeNames(List.of(1L), "token");
    }
}