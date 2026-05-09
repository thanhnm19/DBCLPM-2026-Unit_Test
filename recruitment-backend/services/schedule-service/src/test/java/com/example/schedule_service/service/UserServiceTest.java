package com.example.schedule_service.service;

import com.example.schedule_service.dto.PaginationDTO;
import com.example.schedule_service.dto.Response;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
@SuppressWarnings({ "unchecked" })
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

                when(restTemplate.exchange(
                                eq("http://mock-user-service/api/v1/user-service/employees/" + employeeId),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(body));

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

                when(restTemplate.exchange(
                                eq("http://mock-user-service/api/v1/user-service/employees/" + employeeId),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(body));

                // act
                ResponseEntity<JsonNode> result = userService.getEmployeeName(employeeId, ""); // empty token branch

                // assert
                assertNotNull(result);
                assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
                assertNull(result.getBody());

                ArgumentCaptor<HttpEntity<Void>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
                verify(restTemplate, times(1)).exchange(
                                eq("http://mock-user-service/api/v1/user-service/employees/" + employeeId),
                                eq(HttpMethod.GET),
                                entityCaptor.capture(),
                                any(ParameterizedTypeReference.class));
                assertNull(entityCaptor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
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

                String expectedUrl = "http://mock-user-service/api/v1/user-service/employees?ids=1,2";

                when(restTemplate.exchange(
                                eq(expectedUrl),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(body));

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
                // Objective: Xác nhận parse đúng name và departmentName

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

                String expectedUrl = "http://mock-user-service/api/v1/user-service/employees?ids=1,2";

                when(restTemplate.exchange(
                                eq(expectedUrl),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(body));

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
                // Objective: Xác nhận fallback departmentName là "Unknown" khi thiếu/không đúng
                // kiểu department

                // arrange
                List<Long> employeeIds = List.of(1L, 2L);

                List<Map<String, Object>> employees = new ArrayList<>();
                employees.add(new HashMap<>(Map.of("id", 1, "name", "A"))); // no department
                employees.add(new HashMap<>(Map.of("id", 2, "name", "B", "department", "not-a-map"))); // wrong type

                Response<List<Map<String, Object>>> body = new Response<>();
                body.setData(employees);

                String expectedUrl = "http://mock-user-service/api/v1/user-service/employees?ids=1,2";

                when(restTemplate.exchange(
                                eq(expectedUrl),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(body));

                // act
                ResponseEntity<JsonNode> result = userService.getEmployeeNamesAndDepartmentNames(employeeIds, null); // token
                                                                                                                     // null
                                                                                                                     // branch

                // assert
                assertNotNull(result);
                assertEquals(HttpStatus.OK, result.getStatusCode());
                assertNotNull(result.getBody());
                assertEquals("A", result.getBody().get("1").get("name").asText());
                assertEquals("Unknown", result.getBody().get("1").get("departmentName").asText());
                assertEquals("B", result.getBody().get("2").get("name").asText());
                assertEquals("Unknown", result.getBody().get("2").get("departmentName").asText());

                ArgumentCaptor<HttpEntity<Void>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
                verify(restTemplate, times(1)).exchange(
                                eq(expectedUrl),
                                eq(HttpMethod.GET),
                                entityCaptor.capture(),
                                any(ParameterizedTypeReference.class));
                assertNull(entityCaptor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
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

                String expectedUrl = "http://mock-user-service/api/v1/user-service/users/email/" + email;

                when(restTemplate.exchange(
                                eq(expectedUrl),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(body));

                // act
                Long result = userService.getUserIdByEmail(email);

                // assert
                assertNotNull(result);
                assertEquals(555L, result);
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

                String expectedUrl = "http://mock-user-service/api/v1/user-service/users/email/" + email;

                when(restTemplate.exchange(
                                eq(expectedUrl),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(body));

                // act
                Long result = userService.getUserIdByEmail(email);

                // assert
                assertNotNull(result);
                assertEquals(777L, result);
        }

        @Test
        @DisplayName("SCH-USER-TC-008: getUserIdByEmail - trả null khi user-service lỗi")
        void testGetUserIdByEmail_Exception_SCH_USER_TC_008() {
                // Testcase ID: SCH-USER-TC-008
                // Objective: Xác nhận trả null khi user-service lỗi

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

                when(restTemplate.exchange(
                                anyString(),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(body));

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
                when(restTemplate.exchange(
                                anyString(),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenThrow(new RuntimeException("boom"));

                // act
                List<Long> result = userService.getEmployeeIdsByFilters(2L, null, "valid-token");

                // assert
                assertNotNull(result);
                assertEquals(List.of(), result);
        }

        @Test
        @DisplayName("SCH-USER-TC-011: getAllEmployeeIds - delegate đúng sang getEmployeeIdsByFilters(null, null, authToken)")
        void testGetAllEmployeeIds_Delegates_SCH_USER_TC_011() {
                // Testcase ID: SCH-USER-TC-011
                // Objective: Xác nhận getAllEmployeeIds delegate đúng params sang
                // getEmployeeIdsByFilters

                // arrange
                // Dùng spy để verify method nội bộ được gọi đúng params (không gọi API thật)
                UserService spyService = spy(new UserService(restTemplate, objectMapper));
                ReflectionTestUtils.setField(spyService, "userServiceUrl", "http://mock-user-service");

                doReturn(List.of(11L, 22L)).when(spyService).getEmployeeIdsByFilters(isNull(), isNull(), eq("t"));

                // act
                List<Long> result = spyService.getAllEmployeeIds("t");

                // assert
                assertNotNull(result);
                assertEquals(List.of(11L, 22L), result);
                verify(spyService, times(1)).getEmployeeIdsByFilters(null, null, "t");
                verifyNoInteractions(restTemplate);
        }

        @Test
        @DisplayName("SCH-USER-TC-003: getEmployeeName - response body null -> notFound")
        void testGetEmployeeName_ResponseBodyNull_NotFound_SCH_USER_TC_003() {
                // arrange
                Long employeeId = 999L;
                when(restTemplate.exchange(
                                eq("http://mock-user-service/api/v1/user-service/employees/" + employeeId),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(null));

                // act
                ResponseEntity<JsonNode> result = userService.getEmployeeName(employeeId, null);

                // assert
                assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
                assertNull(result.getBody());
        }

        @Test
        @DisplayName("SCH-USER-TC-004: getEmployeeName - JSON thiếu name -> notFound")
        void testGetEmployeeName_MissingName_NotFound_SCH_USER_TC_004() {
                // arrange
                Long employeeId = 1L;
                Response<Map<String, Object>> body = new Response<>();
                body.setData(new HashMap<>()); // no "name"

                when(restTemplate.exchange(
                                eq("http://mock-user-service/api/v1/user-service/employees/" + employeeId),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(body));

                // act
                ResponseEntity<JsonNode> result = userService.getEmployeeName(employeeId, "t");

                // assert
                assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
                assertNull(result.getBody());
        }

        @Test
        @DisplayName("SCH-USER-TC-004B: getEmployeeName - exception -> trả 500")
        void testGetEmployeeName_Exception_Return500_SCH_USER_TC_004B() {
                // arrange
                when(restTemplate.exchange(
                                anyString(),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenThrow(new RuntimeException("boom"));

                // act
                ResponseEntity<JsonNode> result = userService.getEmployeeName(1L, "t");

                // assert
                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
                assertNotNull(result.getBody());
                assertEquals(500, result.getBody().path("statusCode").asInt());
        }

        @Test
        @DisplayName("SCH-USER-TC-005: getEmployeeNames - employeeIds rỗng -> trả map rỗng")
        void testGetEmployeeNames_EmptyIds_ReturnEmptyMap_SCH_USER_TC_005() {
                // arrange
                Response<List<Map<String, Object>>> body = new Response<>();
                body.setData(List.of());
                when(restTemplate.exchange(
                                eq("http://mock-user-service/api/v1/user-service/employees?ids="),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(body));

                // act
                ResponseEntity<JsonNode> result = userService.getEmployeeNames(List.of(), "");

                // assert
                assertEquals(HttpStatus.OK, result.getStatusCode());
                assertNotNull(result.getBody());
                assertEquals(0, result.getBody().size());
        }

        @Test
        @DisplayName("SCH-USER-TC-008: getEmployeeNames - user-service lỗi -> trả 500")
        void testGetEmployeeNames_Exception_Return500_SCH_USER_TC_008() {
                // arrange
                when(restTemplate.exchange(
                                anyString(),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenThrow(new RuntimeException("boom"));

                // act
                ResponseEntity<JsonNode> result = userService.getEmployeeNames(List.of(1L), null);

                // assert
                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
                assertNotNull(result.getBody());
                assertEquals(500, result.getBody().path("statusCode").asInt());
        }

        @Test
        @DisplayName("SCH-USER-TC-008B: getEmployeeNames - response body null -> trả map rỗng")
        void testGetEmployeeNames_ResponseBodyNull_ReturnEmptyMap_SCH_USER_TC_008B() {
                // arrange
                when(restTemplate.exchange(
                                anyString(),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(null));

                // act
                ResponseEntity<JsonNode> result = userService.getEmployeeNames(List.of(1L), null);

                // assert
                assertEquals(HttpStatus.OK, result.getStatusCode());
                assertNotNull(result.getBody());
                assertEquals(0, result.getBody().size());
        }

        @Test
        @DisplayName("SCH-USER-TC-008D: getEmployeeNames - response data null -> trả map rỗng")
        void testGetEmployeeNames_ResponseDataNull_ReturnEmptyMap_SCH_USER_TC_008D() {
                // arrange
                Response<List<Map<String, Object>>> body = new Response<>();
                body.setData(null);
                when(restTemplate.exchange(
                                anyString(),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(body));

                // act
                ResponseEntity<JsonNode> result = userService.getEmployeeNames(List.of(1L), null);

                // assert
                assertEquals(HttpStatus.OK, result.getStatusCode());
                assertNotNull(result.getBody());
                assertEquals(0, result.getBody().size());
        }

        @Test
        @DisplayName("SCH-USER-TC-008C: getEmployeeNames - có item thiếu id/name -> bỏ qua item đó")
        void testGetEmployeeNames_ItemMissingIdOrName_Skip_SCH_USER_TC_008C() {
                // arrange
                List<Long> employeeIds = List.of(1L, 2L);
                List<Map<String, Object>> employees = new ArrayList<>();
                employees.add(new HashMap<>(Map.of("id", 1, "name", "A")));
                employees.add(new HashMap<>(Map.of("id", 2))); // missing name
                employees.add(new HashMap<>(Map.of("name", "C"))); // missing id

                Response<List<Map<String, Object>>> body = new Response<>();
                body.setData(employees);

                when(restTemplate.exchange(
                                eq("http://mock-user-service/api/v1/user-service/employees?ids=1,2"),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(body));

                // act
                ResponseEntity<JsonNode> result = userService.getEmployeeNames(employeeIds, null);

                // assert
                assertEquals(HttpStatus.OK, result.getStatusCode());
                assertNotNull(result.getBody());
                assertEquals("A", result.getBody().get("1").asText());
                assertNull(result.getBody().get("2"));
        }

        @Test
        @DisplayName("SCH-USER-TC-006: getEmployeeNamesAndDepartmentNames - employeeIds rỗng -> trả object rỗng")
        void testGetEmployeeNamesAndDepartmentNames_EmptyIds_ReturnEmptyObject_SCH_USER_TC_006() {
                // arrange
                Response<List<Map<String, Object>>> body = new Response<>();
                body.setData(List.of());
                when(restTemplate.exchange(
                                eq("http://mock-user-service/api/v1/user-service/employees?ids="),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(body));

                // act
                ResponseEntity<JsonNode> result = userService.getEmployeeNamesAndDepartmentNames(List.of(), null);

                // assert
                assertEquals(HttpStatus.OK, result.getStatusCode());
                assertNotNull(result.getBody());
                assertEquals(0, result.getBody().size());
        }

        @Test
        @DisplayName("SCH-USER-TC-009: getEmployeeNamesAndDepartmentNames - thiếu name -> fallback Unknown")
        void testGetEmployeeNamesAndDepartmentNames_MissingName_FallbackUnknown_SCH_USER_TC_009() {
                // arrange
                List<Map<String, Object>> employees = List.of(new HashMap<>(Map.of("id", 1, "department", Map.of("name", "HR"))));
                Response<List<Map<String, Object>>> body = new Response<>();
                body.setData(employees);
                when(restTemplate.exchange(
                                eq("http://mock-user-service/api/v1/user-service/employees?ids=1"),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(body));

                // act
                ResponseEntity<JsonNode> result = userService.getEmployeeNamesAndDepartmentNames(List.of(1L), "t");

                // assert
                assertEquals("Unknown", result.getBody().get("1").get("name").asText());
                assertEquals("HR", result.getBody().get("1").get("departmentName").asText());
        }

        @Test
        @DisplayName("SCH-USER-TC-009B: getEmployeeNamesAndDepartmentNames - response body null -> object rỗng")
        void testGetEmployeeNamesAndDepartmentNames_ResponseBodyNull_ReturnEmpty_SCH_USER_TC_009B() {
                // arrange
                when(restTemplate.exchange(
                                anyString(),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(null));

                // act
                ResponseEntity<JsonNode> result = userService.getEmployeeNamesAndDepartmentNames(List.of(1L), null);

                // assert
                assertEquals(HttpStatus.OK, result.getStatusCode());
                assertNotNull(result.getBody());
                assertEquals(0, result.getBody().size());
        }

        @Test
        @DisplayName("SCH-USER-TC-009E: getEmployeeNamesAndDepartmentNames - response data null -> object rỗng")
        void testGetEmployeeNamesAndDepartmentNames_ResponseDataNull_ReturnEmpty_SCH_USER_TC_009E() {
                // arrange
                Response<List<Map<String, Object>>> body = new Response<>();
                body.setData(null);
                when(restTemplate.exchange(
                                anyString(),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(body));

                // act
                ResponseEntity<JsonNode> result = userService.getEmployeeNamesAndDepartmentNames(List.of(1L), null);

                // assert
                assertEquals(HttpStatus.OK, result.getStatusCode());
                assertNotNull(result.getBody());
                assertEquals(0, result.getBody().size());
        }

        @Test
        @DisplayName("SCH-USER-TC-009C: getEmployeeNamesAndDepartmentNames - item id null -> skip item")
        void testGetEmployeeNamesAndDepartmentNames_IdNull_SkipItem_SCH_USER_TC_009C() {
                // arrange
                List<Map<String, Object>> employees = new ArrayList<>();
                employees.add(new HashMap<>(Map.of("id", 1, "name", "A", "department", Map.of("name", "HR"))));
                Map<String, Object> idNull = new HashMap<>();
                idNull.put("id", null);
                idNull.put("name", "B");
                employees.add(idNull); // id null

                Response<List<Map<String, Object>>> body = new Response<>();
                body.setData(employees);

                when(restTemplate.exchange(
                                eq("http://mock-user-service/api/v1/user-service/employees?ids=1"),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(body));

                // act
                ResponseEntity<JsonNode> result = userService.getEmployeeNamesAndDepartmentNames(List.of(1L), null);

                // assert
                assertNotNull(result.getBody().get("1"));
        }

        @Test
        @DisplayName("SCH-USER-TC-009D: getEmployeeNamesAndDepartmentNames - deptMap có name null -> departmentName Unknown")
        void testGetEmployeeNamesAndDepartmentNames_DepartmentNameNull_Unknown_SCH_USER_TC_009D() {
                // arrange
                Map<String, Object> dept = new HashMap<>();
                dept.put("name", null);
                List<Map<String, Object>> employees = List.of(new HashMap<>(Map.of("id", 1, "name", "A", "department", dept)));

                Response<List<Map<String, Object>>> body = new Response<>();
                body.setData(employees);

                when(restTemplate.exchange(
                                eq("http://mock-user-service/api/v1/user-service/employees?ids=1"),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(body));

                // act
                ResponseEntity<JsonNode> result = userService.getEmployeeNamesAndDepartmentNames(List.of(1L), null);

                // assert
                assertEquals("Unknown", result.getBody().get("1").get("departmentName").asText());
        }

        @Test
        @DisplayName("SCH-USER-TC-010: getEmployeeNamesAndDepartmentNames - user-service lỗi -> trả 500")
        void testGetEmployeeNamesAndDepartmentNames_Exception_Return500_SCH_USER_TC_010() {
                // arrange
                when(restTemplate.exchange(
                                anyString(),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenThrow(new RuntimeException("boom"));

                // act
                ResponseEntity<JsonNode> result = userService.getEmployeeNamesAndDepartmentNames(List.of(1L), "");

                // assert
                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
                assertNotNull(result.getBody());
                assertEquals(500, result.getBody().path("statusCode").asInt());
        }

        @Test
        @DisplayName("SCH-USER-TC-012: getUserIdByEmail - employeeId và id đều null -> null")
        void testGetUserIdByEmail_EmployeeIdAndIdNull_ReturnNull_SCH_USER_TC_012() {
                // arrange
                String email = "u@gmail.com";
                Response<Map<String, Object>> body = new Response<>();
                body.setData(new HashMap<>());
                when(restTemplate.exchange(
                                eq("http://mock-user-service/api/v1/user-service/users/email/" + email),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(body));

                // act
                Long result = userService.getUserIdByEmail(email);

                // assert
                assertNull(result);
        }

        @Test
        @DisplayName("SCH-USER-TC-013: getUserIdByEmail - response body null -> null")
        void testGetUserIdByEmail_ResponseBodyNull_ReturnNull_SCH_USER_TC_013() {
                // arrange
                String email = "u@gmail.com";
                when(restTemplate.exchange(
                                eq("http://mock-user-service/api/v1/user-service/users/email/" + email),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(null));

                // act
                Long result = userService.getUserIdByEmail(email);

                // assert
                assertNull(result);
        }

        @Test
        @DisplayName("SCH-USER-TC-013B: getUserIdByEmail - response data null -> null")
        void testGetUserIdByEmail_ResponseDataNull_ReturnNull_SCH_USER_TC_013B() {
                // arrange
                String email = "u@gmail.com";
                Response<Map<String, Object>> body = new Response<>();
                body.setData(null);
                when(restTemplate.exchange(
                                eq("http://mock-user-service/api/v1/user-service/users/email/" + email),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(body));

                // act
                Long result = userService.getUserIdByEmail(email);

                // assert
                assertNull(result);
        }

        @Test
        @DisplayName("SCH-USER-TC-014: getEmployeeIdsByFilters - chỉ truyền departmentId -> URL chỉ có departmentId")
        void testGetEmployeeIdsByFilters_DepartmentOnly_UrlContainsDepartment_SCH_USER_TC_014() {
                // arrange
                Response<PaginationDTO> body = new Response<>();
                PaginationDTO pagination = new PaginationDTO();
                pagination.setResult(List.of());
                body.setData(pagination);
                when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                                .thenReturn(ResponseEntity.ok(body));

                // act
                userService.getEmployeeIdsByFilters(2L, null, "t");

                // assert
                ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
                verify(restTemplate).exchange(urlCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class));
                String url = urlCaptor.getValue();
                assertTrue(url.contains("departmentId=2"));
                assertFalse(url.contains("positionId="));
        }

        @Test
        @DisplayName("SCH-USER-TC-015: getEmployeeIdsByFilters - chỉ truyền positionId -> URL chỉ có positionId")
        void testGetEmployeeIdsByFilters_PositionOnly_UrlContainsPosition_SCH_USER_TC_015() {
                // arrange
                Response<PaginationDTO> body = new Response<>();
                PaginationDTO pagination = new PaginationDTO();
                pagination.setResult(List.of());
                body.setData(pagination);
                when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                                .thenReturn(ResponseEntity.ok(body));

                // act
                userService.getEmployeeIdsByFilters(null, 3L, "t");

                // assert
                ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
                verify(restTemplate).exchange(urlCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class));
                String url = urlCaptor.getValue();
                assertTrue(url.contains("positionId=3"));
                assertFalse(url.contains("departmentId="));
        }

        @Test
        @DisplayName("SCH-USER-TC-016: getEmployeeIdsByFilters - không truyền filter nào -> URL không có departmentId/positionId")
        void testGetEmployeeIdsByFilters_NoFilters_UrlNoDepartmentNoPosition_SCH_USER_TC_016() {
                // arrange
                Response<PaginationDTO> body = new Response<>();
                PaginationDTO pagination = new PaginationDTO();
                pagination.setResult(List.of());
                body.setData(pagination);
                when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                                .thenReturn(ResponseEntity.ok(body));

                // act
                userService.getEmployeeIdsByFilters(null, null, "t");

                // assert
                ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
                verify(restTemplate).exchange(urlCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class));
                String url = urlCaptor.getValue();
                assertFalse(url.contains("departmentId="));
                assertFalse(url.contains("positionId="));
        }

        @Test
        @DisplayName("SCH-USER-TC-017: getEmployeeIdsByFilters - response body null -> list rỗng")
        void testGetEmployeeIdsByFilters_ResponseBodyNull_ReturnEmpty_SCH_USER_TC_017() {
                // arrange
                when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                                .thenReturn(ResponseEntity.ok(null));

                // act
                List<Long> result = userService.getEmployeeIdsByFilters(2L, 3L, "t");

                // assert
                assertEquals(List.of(), result);
        }

        @Test
        @DisplayName("SCH-USER-TC-018: getEmployeeIdsByFilters - response data null -> list rỗng")
        void testGetEmployeeIdsByFilters_ResponseDataNull_ReturnEmpty_SCH_USER_TC_018() {
                // arrange
                Response<PaginationDTO> body = new Response<>();
                body.setData(null);
                when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                                .thenReturn(ResponseEntity.ok(body));

                // act
                List<Long> result = userService.getEmployeeIdsByFilters(2L, 3L, "t");

                // assert
                assertEquals(List.of(), result);
        }

        @Test
        @DisplayName("SCH-USER-TC-019: getEmployeeIdsByFilters - pagination.result không phải List -> list rỗng")
        void testGetEmployeeIdsByFilters_ResultNotList_ReturnEmpty_SCH_USER_TC_019() {
                // arrange
                PaginationDTO paginationDTO = new PaginationDTO();
                paginationDTO.setResult("not-a-list");
                Response<PaginationDTO> body = new Response<>();
                body.setData(paginationDTO);

                when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                                .thenReturn(ResponseEntity.ok(body));

                // act
                List<Long> result = userService.getEmployeeIdsByFilters(null, null, null);

                // assert
                assertEquals(List.of(), result);
        }

        @Test
        @DisplayName("SCH-USER-TC-020: getEmployeeIdsByFilters - item thiếu id hoặc id không phải Number -> bỏ qua")
        void testGetEmployeeIdsByFilters_ItemMissingIdOrIdNotNumber_Skip_SCH_USER_TC_020() {
                // arrange
                List<Object> items = new ArrayList<>();
                items.add(new HashMap<>(Map.of("id", 1)));
                items.add(new HashMap<>()); // missing id
                items.add(new HashMap<>(Map.of("id", "x"))); // id not number
                items.add("not-a-map");

                PaginationDTO paginationDTO = new PaginationDTO();
                paginationDTO.setResult(items);
                Response<PaginationDTO> body = new Response<>();
                body.setData(paginationDTO);

                when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                                .thenReturn(ResponseEntity.ok(body));

                // act
                List<Long> result = userService.getEmployeeIdsByFilters(null, null, "");

                // assert
                assertEquals(List.of(1L), result);
        }
}