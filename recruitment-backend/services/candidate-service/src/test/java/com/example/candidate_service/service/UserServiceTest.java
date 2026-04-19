package com.example.candidate_service.service;

import com.example.candidate_service.dto.Response;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class UserServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private ObjectMapper objectMapper;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        userService = new UserService(restTemplate, objectMapper);
        ReflectionTestUtils.setField(userService, "userServiceUrl", "http://localhost:8082");
    }

    @Test
    @DisplayName("US-TC-001: getEmployeeName - parse đúng tên nhân viên")
    void testGetEmployeeName_US_TC_001() {
        // Testcase ID: US-TC-001
        // Objective: Xác nhận parse đúng tên nhân viên

        // arrange
        Long employeeId = 1L;
        String token = "token";

        Map<String, Object> data = new HashMap<>();
        data.put("name", "A");

        Response<Map<String, Object>> body = new Response<>();
        body.setData(data);

        @SuppressWarnings("unchecked")
        ResponseEntity<Response<Map<String, Object>>> mockedResponse =
                (ResponseEntity<Response<Map<String, Object>>>) (ResponseEntity<?>) ResponseEntity.ok(body);

        when(restTemplate.exchange(
                eq("http://localhost:8082/api/v1/user-service/employees/1"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(mockedResponse);

        // act
        ResponseEntity<JsonNode> result = userService.getEmployeeName(employeeId, token);

        // assert
        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals("A", result.getBody().get("name").asText());

        verify(restTemplate, times(1)).exchange(
                eq("http://localhost:8082/api/v1/user-service/employees/1"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        );
    }

    @Test
    @DisplayName("US-TC-002: getEmployeeName - trả 404 khi không có data hoặc name")
    void testGetEmployeeName_NotFound_US_TC_002() {
        // Testcase ID: US-TC-002
        // Objective: Xác nhận trả 404 khi không có dữ liệu

        // arrange
        Long employeeId = 999L;
        String token = "token";

        Response<Map<String, Object>> body = new Response<>();
        body.setData(null); // no data

        @SuppressWarnings("unchecked")
        ResponseEntity<Response<Map<String, Object>>> mockedResponse =
                (ResponseEntity<Response<Map<String, Object>>>) (ResponseEntity<?>) ResponseEntity.ok(body);

        when(restTemplate.exchange(
                eq("http://localhost:8082/api/v1/user-service/employees/999"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(mockedResponse);

        // act
        ResponseEntity<JsonNode> result = userService.getEmployeeName(employeeId, token);

        // assert
        assertNotNull(result);
        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());

        verify(restTemplate, times(1)).exchange(
                eq("http://localhost:8082/api/v1/user-service/employees/999"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        );
    }

    @Test
    @DisplayName("US-TC-003: getEmployeeNames - map đúng id sang name")
    void testGetEmployeeNames_US_TC_003() {
        // Testcase ID: US-TC-003
        // Objective: Xác nhận map đúng id -> name

        // arrange
        List<Long> employeeIds = List.of(1L, 2L);
        String token = "token";

        Map<String, Object> e1 = new HashMap<>();
        e1.put("id", 1L);
        e1.put("name", "A");

        Map<String, Object> e2 = new HashMap<>();
        e2.put("id", 2L);
        e2.put("name", "B");

        Response<List<Map<String, Object>>> body = new Response<>();
        body.setData(List.of(e1, e2));

        @SuppressWarnings("unchecked")
        ResponseEntity<Response<List<Map<String, Object>>>> mockedResponse =
                (ResponseEntity<Response<List<Map<String, Object>>>>) (ResponseEntity<?>) ResponseEntity.ok(body);

        when(restTemplate.exchange(
                eq("http://localhost:8082/api/v1/user-service/employees?ids=1,2"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(mockedResponse);

        // act
        ResponseEntity<JsonNode> result = userService.getEmployeeNames(employeeIds, token);

        // assert
        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals("A", result.getBody().get("1").asText());
        assertEquals("B", result.getBody().get("2").asText());

        verify(restTemplate, times(1)).exchange(
                eq("http://localhost:8082/api/v1/user-service/employees?ids=1,2"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        );
    }

    @Test
    @DisplayName("US-TC-004: getUserIdByEmail - ưu tiên lấy employeeId")
    void testGetUserIdByEmail_EmployeeId_US_TC_004() {
        // Testcase ID: US-TC-004
        // Objective: Ưu tiên lấy employeeId khi có

        // arrange
        String email = "a@gmail.com";

        Map<String, Object> data = new HashMap<>();
        data.put("employeeId", 10L);
        data.put("id", 99L); // should not be used

        Response<Map<String, Object>> body = new Response<>();
        body.setData(data);

        @SuppressWarnings("unchecked")
        ResponseEntity<Response<Map<String, Object>>> mockedResponse =
                (ResponseEntity<Response<Map<String, Object>>>) (ResponseEntity<?>) ResponseEntity.ok(body);

        when(restTemplate.exchange(
                eq("http://localhost:8082/api/v1/user-service/users/email/" + email),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(mockedResponse);

        // act
        Long result = userService.getUserIdByEmail(email);

        // assert
        assertNotNull(result);
        assertEquals(10L, result);

        verify(restTemplate, times(1)).exchange(
                eq("http://localhost:8082/api/v1/user-service/users/email/" + email),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        );
    }

    @Test
    @DisplayName("US-TC-005: getUserIdByEmail - fallback sang id khi thiếu employeeId")
    void testGetUserIdByEmail_FallbackId_US_TC_005() {
        // Testcase ID: US-TC-005
        // Objective: Fallback sang id khi thiếu employeeId

        // arrange
        String email = "a@gmail.com";

        Map<String, Object> data = new HashMap<>();
        data.put("id", 99L);

        Response<Map<String, Object>> body = new Response<>();
        body.setData(data);

        @SuppressWarnings("unchecked")
        ResponseEntity<Response<Map<String, Object>>> mockedResponse =
                (ResponseEntity<Response<Map<String, Object>>>) (ResponseEntity<?>) ResponseEntity.ok(body);

        when(restTemplate.exchange(
                eq("http://localhost:8082/api/v1/user-service/users/email/" + email),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(mockedResponse);

        // act
        Long result = userService.getUserIdByEmail(email);

        // assert
        assertNotNull(result);
        assertEquals(99L, result);

        verify(restTemplate, times(1)).exchange(
                eq("http://localhost:8082/api/v1/user-service/users/email/" + email),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        );
    }

    @Test
    @DisplayName("US-TC-006: getUserIdByEmail - trả null khi restTemplate lỗi")
    void testGetUserIdByEmail_Exception_US_TC_006() {
        // Testcase ID: US-TC-006
        // Objective: Xác nhận trả null khi lỗi

        // arrange
        String email = "x@gmail.com";

        when(restTemplate.exchange(
                eq("http://localhost:8082/api/v1/user-service/users/email/" + email),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenThrow(new RuntimeException("boom"));

        // act
        Long result = userService.getUserIdByEmail(email);

        // assert
        assertNull(result);

        verify(restTemplate, times(1)).exchange(
                eq("http://localhost:8082/api/v1/user-service/users/email/" + email),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        );
    }

    @Test
    @DisplayName("US-TC-007: createEmployeeFromCandidate - build request body đúng và unwrap data thành công")
    void testCreateEmployeeFromCandidate_Success_US_TC_007() {
        // Testcase ID: US-TC-007
        // Objective: Xác nhận build request body đúng và unwrap data thành công

        // arrange
        Long candidateId = 1L;
        String name = "A";
        String email = "a@gmail.com";
        String phone = "0123456789";
        String dateOfBirth = "2000-01-01";
        String gender = "MALE";
        String nationality = "VN";
        String idNumber = "123456789";
        String address = "HCM";
        String avatarUrl = "http://img";
        Long departmentId = 2L;
        Long positionId = 3L;
        String status = "ACTIVE";
        String token = "token";

        ObjectNode returnedData = objectMapper.createObjectNode();
        returnedData.put("employeeId", 100);

        Response<JsonNode> body = new Response<>();
        body.setData(returnedData);

        @SuppressWarnings("unchecked")
        ResponseEntity<Response<JsonNode>> mockedResponse =
                (ResponseEntity<Response<JsonNode>>) (ResponseEntity<?>) ResponseEntity.ok(body);

        when(restTemplate.exchange(
                eq("http://localhost:8082/api/v1/user-service/employees/from-candidate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(mockedResponse);

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        // act
        ResponseEntity<JsonNode> result = userService.createEmployeeFromCandidate(
                candidateId, name, email, phone, dateOfBirth, gender, nationality, idNumber, address, avatarUrl,
                departmentId, positionId, status, token
        );

        // assert
        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(100, result.getBody().get("employeeId").asInt());

        verify(restTemplate, times(1)).exchange(
                eq("http://localhost:8082/api/v1/user-service/employees/from-candidate"),
                eq(HttpMethod.POST),
                entityCaptor.capture(),
                any(ParameterizedTypeReference.class)
        );

        HttpEntity captured = entityCaptor.getValue();
        assertNotNull(captured);

        assertTrue(captured.getBody() instanceof ObjectNode);
        ObjectNode sentBody = (ObjectNode) captured.getBody();

        assertNotNull(sentBody);
        assertEquals(1L, sentBody.get("candidateId").asLong());
        assertEquals("A", sentBody.get("name").asText());
        assertEquals("a@gmail.com", sentBody.get("email").asText());
        assertEquals("0123456789", sentBody.get("phone").asText());
        assertEquals("2000-01-01", sentBody.get("dateOfBirth").asText());
        assertEquals("MALE", sentBody.get("gender").asText());
        assertEquals("VN", sentBody.get("nationality").asText());
        assertEquals("123456789", sentBody.get("idNumber").asText());
        assertEquals("HCM", sentBody.get("address").asText());
        assertEquals("http://img", sentBody.get("avatarUrl").asText());
        assertEquals(2L, sentBody.get("departmentId").asLong());
        assertEquals(3L, sentBody.get("positionId").asLong());
        assertEquals("ACTIVE", sentBody.get("status").asText());

        HttpHeaders sentHeaders = captured.getHeaders();
        assertNotNull(sentHeaders);
        assertEquals("Bearer " + token, sentHeaders.getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    @DisplayName("US-TC-008: createEmployeeFromCandidate - trả status gốc khi không có body hoặc data")
    void testCreateEmployeeFromCandidate_NoData_US_TC_008() {
        // Testcase ID: US-TC-008
        // Objective: Xác nhận trả status gốc khi không có data

        // arrange
        Long candidateId = 1L;
        String name = "A";
        String email = "a@gmail.com";
        String phone = "0123456789";
        String dateOfBirth = null;
        String gender = null;
        String nationality = null;
        String idNumber = null;
        String address = null;
        String avatarUrl = null;
        Long departmentId = 2L;
        Long positionId = 3L;
        String status = "ACTIVE";
        String token = "token";

        Response<JsonNode> body = new Response<>();
        body.setData(null); // no data

        @SuppressWarnings("unchecked")
        ResponseEntity<Response<JsonNode>> mockedResponse =
                (ResponseEntity<Response<JsonNode>>) (ResponseEntity<?>) ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);

        when(restTemplate.exchange(
                eq("http://localhost:8082/api/v1/user-service/employees/from-candidate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        )).thenReturn(mockedResponse);

        // act
        ResponseEntity<JsonNode> result = userService.createEmployeeFromCandidate(
                candidateId, name, email, phone, dateOfBirth, gender, nationality, idNumber, address, avatarUrl,
                departmentId, positionId, status, token
        );

        // assert
        assertNotNull(result);
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());

        verify(restTemplate, times(1)).exchange(
                eq("http://localhost:8082/api/v1/user-service/employees/from-candidate"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)
        );
    }
}