package com.example.schedule_service.service;

import com.example.schedule_service.dto.PaginationDTO;
import com.example.schedule_service.dto.Response;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.ArrayList;
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
                ReflectionTestUtils.setField(userService, "userServiceUrl", "http://mock-user-service");
        }

        @Test
        @DisplayName("SCH-USER-TC-001: getEmployeeName - parse đúng tên employee")
        void testGetEmployeeName_SCH_USER_TC_001() {
                // Testcase ID: SCH-USER-TC-001
                // Objective: Xác nhận parse đúng tên employee

                // arrange
                Long employeeId = 1L;
                String token = "valid-token";

                Map<String, Object> data = new HashMap<>();
                data.put("name", "A");

                Response<Map<String, Object>> body = new Response<>();
                body.setData(data);

                ResponseEntity<Response<Map<String, Object>>> mockedResponse = ResponseEntity.ok(body);

                when(restTemplate.exchange(
                                eq("http://mock-user-service/api/v1/user-service/employees/" + employeeId),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(mockedResponse);

                // act
                ResponseEntity<JsonNode> result = userService.getEmployeeName(employeeId, token);

                // assert
                assertNotNull(result);
                assertEquals(HttpStatus.OK, result.getStatusCode());
                assertNotNull(result.getBody());
                assertEquals("A", result.getBody().get("name").asText());

                ArgumentCaptor<HttpEntity<Void>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
                verify(restTemplate, times(1)).exchange(
                                eq("http://mock-user-service/api/v1/user-service/employees/" + employeeId),
                                eq(HttpMethod.GET),
                                entityCaptor.capture(),
                                any(ParameterizedTypeReference.class));

                HttpHeaders capturedHeaders = entityCaptor.getValue().getHeaders();
                assertNotNull(capturedHeaders.getFirst(HttpHeaders.AUTHORIZATION));
                assertEquals("Bearer " + token, capturedHeaders.getFirst(HttpHeaders.AUTHORIZATION));
        }

        @Test
        @DisplayName("SCH-USER-TC-002: getEmployeeName - trả notFound khi không có dữ liệu")
        void testGetEmployeeName_NotFound_SCH_USER_TC_002() {
                // Testcase ID: SCH-USER-TC-002
                // Objective: Xác nhận trả notFound khi không có dữ liệu

                // arrange
                Long employeeId = 999L;

                Response<Map<String, Object>> body = new Response<>();
                body.setData(null);

                ResponseEntity<Response<Map<String, Object>>> mockedResponse = ResponseEntity.ok(body);

                when(restTemplate.exchange(
                                eq("http://mock-user-service/api/v1/user-service/employees/" + employeeId),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(mockedResponse);

                // act
                ResponseEntity<JsonNode> result = userService.getEmployeeName(employeeId, null);

                // assert
                assertNotNull(result);
                assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
                assertNull(result.getBody());

                verify(restTemplate, times(1)).exchange(
                                eq("http://mock-user-service/api/v1/user-service/employees/" + employeeId),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class));
        }

        @Test
        @DisplayName("SCH-USER-TC-003: getEmployeeNames - map đúng id sang name")
        void testGetEmployeeNames_SCH_USER_TC_003() {
                // Testcase ID: SCH-USER-TC-003
                // Objective: Xác nhận map đúng id -> name

                // arrange
                List<Long> employeeIds = List.of(1L, 2L);
                String token = "valid-token";

                List<Map<String, Object>> employees = new ArrayList<>();
                employees.add(new HashMap<>(Map.of("id", 1, "name", "A")));
                employees.add(new HashMap<>(Map.of("id", 2, "name", "B")));

                Response<List<Map<String, Object>>> body = new Response<>();
                body.setData(employees);

                ResponseEntity<Response<List<Map<String, Object>>>> mockedResponse = ResponseEntity.ok(body);

                String expectedUrl = "http://mock-user-service/api/v1/user-service/employees?ids=1,2";

                when(restTemplate.exchange(
                                eq(expectedUrl),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(mockedResponse);

                // act
                ResponseEntity<JsonNode> result = userService.getEmployeeNames(employeeIds, token);

                // assert
                assertNotNull(result);
                assertEquals(HttpStatus.OK, result.getStatusCode());
                assertNotNull(result.getBody());
                assertEquals("A", result.getBody().get("1").asText());
                assertEquals("B", result.getBody().get("2").asText());

                ArgumentCaptor<HttpEntity<Void>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
                verify(restTemplate, times(1)).exchange(
                                eq(expectedUrl),
                                eq(HttpMethod.GET),
                                entityCaptor.capture(),
                                any(ParameterizedTypeReference.class));

                HttpHeaders capturedHeaders = entityCaptor.getValue().getHeaders();
                assertEquals("Bearer " + token, capturedHeaders.getFirst(HttpHeaders.AUTHORIZATION));
        }

        @Test
        @DisplayName("SCH-USER-TC-004: getEmployeeNamesAndDepartmentNames - parse đúng name và departmentName")
        void testGetEmployeeNamesAndDepartmentNames_SCH_USER_TC_004() {
                // Testcase ID: SCH-USER-TC-004
                // Objective: Xác nhận parse đúng cả name và departmentName

                // arrange
                List<Long> employeeIds = List.of(1L, 2L);
                String token = "valid-token";

                Map<String, Object> dept1 = new HashMap<>();
                dept1.put("name", "HR");

                Map<String, Object> dept2 = new HashMap<>();
                dept2.put("name", "IT");

                List<Map<String, Object>> employees = new ArrayList<>();
                employees.add(new HashMap<>(Map.of("id", 1, "name", "A", "department", dept1)));
                employees.add(new HashMap<>(Map.of("id", 2, "name", "B", "department", dept2)));

                Response<List<Map<String, Object>>> body = new Response<>();
                body.setData(employees);

                ResponseEntity<Response<List<Map<String, Object>>>> mockedResponse = ResponseEntity.ok(body);

                String expectedUrl = "http://mock-user-service/api/v1/user-service/employees?ids=1,2";

                when(restTemplate.exchange(
                                eq(expectedUrl),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(mockedResponse);

                // act
                ResponseEntity<JsonNode> result = userService.getEmployeeNamesAndDepartmentNames(employeeIds, token);

                // assert
                assertNotNull(result);
                assertEquals(HttpStatus.OK, result.getStatusCode());
                assertNotNull(result.getBody());

                JsonNode bodyNode = result.getBody();
                assertEquals("A", bodyNode.get("1").get("name").asText());
                assertEquals("HR", bodyNode.get("1").get("departmentName").asText());
                assertEquals("B", bodyNode.get("2").get("name").asText());
                assertEquals("IT", bodyNode.get("2").get("departmentName").asText());

                ArgumentCaptor<HttpEntity<Void>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
                verify(restTemplate, times(1)).exchange(
                                eq(expectedUrl),
                                eq(HttpMethod.GET),
                                entityCaptor.capture(),
                                any(ParameterizedTypeReference.class));

                HttpHeaders capturedHeaders = entityCaptor.getValue().getHeaders();
                assertEquals("Bearer " + token, capturedHeaders.getFirst(HttpHeaders.AUTHORIZATION));
        }

        @Test
        @DisplayName("SCH-USER-TC-005: getEmployeeNamesAndDepartmentNames - fallback departmentName là Unknown")
        void testGetEmployeeNamesAndDepartmentNames_FallbackUnknown_SCH_USER_TC_005() {
                // Testcase ID: SCH-USER-TC-005
                // Objective: Xác nhận fallback departmentName=\"Unknown\" khi thiếu phòng ban

                // arrange
                List<Long> employeeIds = List.of(1L);
                String token = "valid-token";

                List<Map<String, Object>> employees = new ArrayList<>();
                employees.add(new HashMap<>(Map.of("id", 1, "name", "A"))); // no department

                Response<List<Map<String, Object>>> body = new Response<>();
                body.setData(employees);

                ResponseEntity<Response<List<Map<String, Object>>>> mockedResponse = ResponseEntity.ok(body);

                String expectedUrl = "http://mock-user-service/api/v1/user-service/employees?ids=1";

                when(restTemplate.exchange(
                                eq(expectedUrl),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(mockedResponse);

                // act
                ResponseEntity<JsonNode> result = userService.getEmployeeNamesAndDepartmentNames(employeeIds, token);

                // assert
                assertNotNull(result);
                assertEquals(HttpStatus.OK, result.getStatusCode());
                assertNotNull(result.getBody());

                JsonNode bodyNode = result.getBody();
                assertEquals("A", bodyNode.get("1").get("name").asText());
                assertEquals("Unknown", bodyNode.get("1").get("departmentName").asText());

                verify(restTemplate, times(1)).exchange(
                                eq(expectedUrl),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class));
        }

        @Test
        @DisplayName("SCH-USER-TC-006: getUserIdByEmail - ưu tiên lấy employeeId")
        void testGetUserIdByEmail_EmployeeId_SCH_USER_TC_006() {
                // Testcase ID: SCH-USER-TC-006
                // Objective: Xác nhận ưu tiên lấy employeeId

                // arrange
                String email = "u@gmail.com";

                Map<String, Object> data = new HashMap<>();
                data.put("employeeId", 555);

                Response<Map<String, Object>> body = new Response<>();
                body.setData(data);

                ResponseEntity<Response<Map<String, Object>>> mockedResponse = ResponseEntity.ok(body);

                String expectedUrl = "http://mock-user-service/api/v1/user-service/users/email/" + email;

                when(restTemplate.exchange(
                                eq(expectedUrl),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(mockedResponse);

                // act
                Long result = userService.getUserIdByEmail(email);

                // assert
                assertNotNull(result);
                assertEquals(555L, result);

                verify(restTemplate, times(1)).exchange(
                                eq(expectedUrl),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class));
        }

        @Test
        @DisplayName("SCH-USER-TC-007: getUserIdByEmail - fallback sang id")
        void testGetUserIdByEmail_FallbackId_SCH_USER_TC_007() {
                // Testcase ID: SCH-USER-TC-007
                // Objective: Xác nhận fallback sang id

                // arrange
                String email = "u@gmail.com";

                Map<String, Object> data = new HashMap<>();
                data.put("id", 777);

                Response<Map<String, Object>> body = new Response<>();
                body.setData(data);

                ResponseEntity<Response<Map<String, Object>>> mockedResponse = ResponseEntity.ok(body);

                String expectedUrl = "http://mock-user-service/api/v1/user-service/users/email/" + email;

                when(restTemplate.exchange(
                                eq(expectedUrl),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(mockedResponse);

                // act
                Long result = userService.getUserIdByEmail(email);

                // assert
                assertNotNull(result);
                assertEquals(777L, result);

                verify(restTemplate, times(1)).exchange(
                                eq(expectedUrl),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class));
        }

        @Test
        @DisplayName("SCH-USER-TC-008: getUserIdByEmail - trả null khi user-service lỗi")
        void testGetUserIdByEmail_Exception_SCH_USER_TC_008() {
                // Testcase ID: SCH-USER-TC-008
                // Objective: Xác nhận trả null khi gọi user-service lỗi

                // arrange
                String email = "u@gmail.com";
                String expectedUrl = "http://mock-user-service/api/v1/user-service/users/email/" + email;

                when(restTemplate.exchange(
                                eq(expectedUrl),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenThrow(new RuntimeException("boom"));

                // act
                Long result = userService.getUserIdByEmail(email);

                // assert
                assertNull(result);

                verify(restTemplate, times(1)).exchange(
                                eq(expectedUrl),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class));
        }

        @Test
        @DisplayName("SCH-USER-TC-009: getEmployeeIdsByFilters - build URL đúng và parse đúng danh sách employeeIds")
        void testGetEmployeeIdsByFilters_SCH_USER_TC_009() {
                // Testcase ID: SCH-USER-TC-009
                // Objective: Xác nhận build URL đúng và parse đúng danh sách employeeIds

                // arrange
                Long departmentId = 2L;
                Long positionId = 3L;
                String authToken = "valid-token";

                PaginationDTO paginationDTO = new PaginationDTO();

                List<Map<String, Object>> resultList = new ArrayList<>();
                resultList.add(new HashMap<>(Map.of("id", 101)));
                resultList.add(new HashMap<>(Map.of("id", 102)));
                resultList.add(new HashMap<>(Map.of("id", 103)));

                paginationDTO.setResult(resultList);

                Response<PaginationDTO> body = new Response<>();
                body.setData(paginationDTO);

                ResponseEntity<Response<PaginationDTO>> mockedResponse = ResponseEntity.ok(body);

                when(restTemplate.exchange(
                                anyString(),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(mockedResponse);

                // act
                List<Long> result = userService.getEmployeeIdsByFilters(departmentId, positionId, authToken);

                // assert
                assertNotNull(result);
                assertEquals(List.of(101L, 102L, 103L), result);

                ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
                ArgumentCaptor<HttpEntity<Void>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

                verify(restTemplate, times(1)).exchange(
                                urlCaptor.capture(),
                                eq(HttpMethod.GET),
                                entityCaptor.capture(),
                                any(ParameterizedTypeReference.class));

                String capturedUrl = urlCaptor.getValue();
                assertNotNull(capturedUrl);
                assertTrue(capturedUrl.startsWith("http://mock-user-service/api/v1/user-service/employees"));
                assertTrue(capturedUrl.contains("page=1"));
                assertTrue(capturedUrl.contains("limit=1000"));
                assertTrue(capturedUrl.contains("departmentId=2"));
                assertTrue(capturedUrl.contains("positionId=3"));

                HttpHeaders capturedHeaders = entityCaptor.getValue().getHeaders();
                assertEquals("Bearer " + authToken, capturedHeaders.getFirst(HttpHeaders.AUTHORIZATION));
        }

        @Test
        @DisplayName("SCH-USER-TC-010: getEmployeeIdsByFilters - trả list rỗng khi lỗi")
        void testGetEmployeeIdsByFilters_ExceptionFallback_SCH_USER_TC_010() {
                // Testcase ID: SCH-USER-TC-010
                // Objective: Xác nhận trả list rỗng khi lỗi

                // arrange
                Long departmentId = 2L;

                when(restTemplate.exchange(
                                anyString(),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenThrow(new RuntimeException("boom"));

                // act
                List<Long> result = userService.getEmployeeIdsByFilters(departmentId, null, "valid-token");

                // assert
                assertNotNull(result);
                assertEquals(List.of(), result);

                verify(restTemplate, times(1)).exchange(
                                anyString(),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class));
        }
}