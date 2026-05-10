package com.example.schedule_service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.example.schedule_service.dto.PaginationDTO;
import com.example.schedule_service.dto.Response;
import com.example.schedule_service.model.Schedule;
import com.example.schedule_service.repository.ScheduleParticipantRepository;
import com.example.schedule_service.repository.ScheduleRepository;
import com.example.schedule_service.utils.enums.MeetingType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.mockito.ArgumentCaptor;

/**
 * Slice test cho {@link UserService}: mock HTTP ({@link RestTemplate}), JPA thật (H2).
 * CheckDB xác nhận UserService không ghi/đổi dữ liệu nội bộ schedule-service (chỉ gọi ra user-service).
 */
@DataJpaTest
@Import({ UserService.class, UserServiceTest.JacksonTestConfig.class })
@ActiveProfiles("test")
@Transactional
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@SuppressWarnings("unchecked")
class UserServiceTest {

        @TestConfiguration
        static class JacksonTestConfig {
                @Bean
                ObjectMapper objectMapper() {
                        return new ObjectMapper();
                }
        }

        @MockitoBean
        private RestTemplate restTemplate;

        @Autowired
        private UserService userService;

        @Autowired
        private ScheduleRepository scheduleRepository;

        @Autowired
        private ScheduleParticipantRepository scheduleParticipantRepository;

        @Value("${user-service.url}")
        private String userServiceBaseUrl;

        private Long baselineScheduleId;
        private String baselineScheduleTitle;

        @BeforeEach
        void setUp() {
                Schedule baselineSchedule = new Schedule();
                baselineSchedule.setTitle("SCH-USER-DB-BASELINE");
                baselineSchedule.setStatus("SCHEDULED");
                baselineSchedule.setMeetingType(MeetingType.MEETING);
                baselineSchedule.setStartTime(java.time.LocalDateTime.of(2099, 1, 1, 10, 0));
                baselineSchedule.setEndTime(java.time.LocalDateTime.of(2099, 1, 1, 11, 0));
                Schedule persistedBaseline = scheduleRepository.saveAndFlush(baselineSchedule);
                baselineScheduleId = persistedBaseline.getId();
                baselineScheduleTitle = persistedBaseline.getTitle();
        }

        private void assertDatabaseUnchanged(long expectedScheduleCount, long expectedParticipantCount) {
                assertEquals(expectedScheduleCount, scheduleRepository.count(),
                                "CheckDB: số bản ghi schedule không được thay đổi sau khi gọi UserService");
                assertEquals(expectedParticipantCount, scheduleParticipantRepository.count(),
                                "CheckDB: số bản ghi schedule_participant không được thay đổi");
                Schedule reloadedBaseline = scheduleRepository.findById(baselineScheduleId).orElseThrow();
                assertEquals(baselineScheduleTitle, reloadedBaseline.getTitle(),
                                "CheckDB: bản ghi baseline phải giữ nguyên — UserService chỉ gọi HTTP ra ngoài");
        }

        private String employeeDetailUrl(long employeeId) {
                return userServiceBaseUrl + "/api/v1/user-service/employees/" + employeeId;
        }

        private String employeesByIdsUrl(String commaSeparatedIds) {
                return userServiceBaseUrl + "/api/v1/user-service/employees?ids=" + commaSeparatedIds;
        }

        private String userLookupByEmailUrl(String email) {
                return userServiceBaseUrl + "/api/v1/user-service/users/email/" + email;
        }

        @Test
        @DisplayName("SCH-USER-TC-001: getEmployeeName — parse đúng tên employee")
        void getEmployeeName_whenUserServiceReturnsName_returnsOkWithNameAndAuthorizationHeader() {
                // Test Case ID: SCH-USER-TC-001

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                Long requestedEmployeeId = 1L;
                String bearerToken = "valid-token";

                Map<String, Object> employeePayload = new HashMap<>();
                employeePayload.put("name", "Nguyen Van A");

                Response<Map<String, Object>> apiEnvelope = new Response<>();
                apiEnvelope.setData(employeePayload);

                when(restTemplate.exchange(
                                eq(employeeDetailUrl(requestedEmployeeId)),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(apiEnvelope));

                // Act
                ResponseEntity<JsonNode> employeeNameResponse = userService.getEmployeeName(requestedEmployeeId,
                                bearerToken);

                // Assert
                assertNotNull(employeeNameResponse);
                assertEquals(HttpStatus.OK, employeeNameResponse.getStatusCode());
                assertNotNull(employeeNameResponse.getBody());
                assertEquals("Nguyen Van A", employeeNameResponse.getBody().get("name").asText());

                ArgumentCaptor<HttpEntity<Void>> httpEntityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
                verify(restTemplate, times(1)).exchange(
                                eq(employeeDetailUrl(requestedEmployeeId)),
                                eq(HttpMethod.GET),
                                httpEntityCaptor.capture(),
                                any(ParameterizedTypeReference.class));

                HttpHeaders capturedRequestHeaders = httpEntityCaptor.getValue().getHeaders();
                assertEquals("Bearer " + bearerToken, capturedRequestHeaders.getFirst(HttpHeaders.AUTHORIZATION));

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-002: getEmployeeName — trả NOT_FOUND khi payload data null")
        void getEmployeeName_whenResponseDataNull_returnsNotFoundAndNoAuthorizationHeader() {
                // Test Case ID: SCH-USER-TC-002

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                Long missingEmployeeId = 999L;

                Response<Map<String, Object>> apiEnvelope = new Response<>();
                apiEnvelope.setData(null);

                when(restTemplate.exchange(
                                eq(employeeDetailUrl(missingEmployeeId)),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(apiEnvelope));

                // Act
                ResponseEntity<JsonNode> employeeNameResponse = userService.getEmployeeName(missingEmployeeId, "");

                // Assert
                assertNotNull(employeeNameResponse);
                assertEquals(HttpStatus.NOT_FOUND, employeeNameResponse.getStatusCode());
                assertNull(employeeNameResponse.getBody());

                ArgumentCaptor<HttpEntity<Void>> httpEntityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
                verify(restTemplate, times(1)).exchange(
                                eq(employeeDetailUrl(missingEmployeeId)),
                                eq(HttpMethod.GET),
                                httpEntityCaptor.capture(),
                                any(ParameterizedTypeReference.class));
                assertNull(httpEntityCaptor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-003: getEmployeeNames — map đúng id sang name")
        void getEmployeeNames_whenBatchApiReturnsEmployees_returnsIdToNameMap() {
                // Test Case ID: SCH-USER-TC-003

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                List<Long> requestedEmployeeIds = List.of(1L, 2L);
                String bearerToken = "valid-token";

                List<Map<String, Object>> employeeRows = new ArrayList<>();
                employeeRows.add(new HashMap<>(Map.of("id", 1, "name", "Nguyen Van A")));
                employeeRows.add(new HashMap<>(Map.of("id", 2, "name", "Tran Thi B")));

                Response<List<Map<String, Object>>> apiEnvelope = new Response<>();
                apiEnvelope.setData(employeeRows);

                String batchEmployeesUrl = employeesByIdsUrl("1,2");

                when(restTemplate.exchange(
                                eq(batchEmployeesUrl),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(apiEnvelope));

                // Act
                ResponseEntity<JsonNode> idToNameResponse = userService.getEmployeeNames(requestedEmployeeIds,
                                bearerToken);

                // Assert
                assertNotNull(idToNameResponse);
                assertEquals(HttpStatus.OK, idToNameResponse.getStatusCode());
                assertNotNull(idToNameResponse.getBody());
                assertEquals("Nguyen Van A", idToNameResponse.getBody().get("1").asText());
                assertEquals("Tran Thi B", idToNameResponse.getBody().get("2").asText());

                ArgumentCaptor<HttpEntity<Void>> httpEntityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
                verify(restTemplate, times(1)).exchange(
                                eq(batchEmployeesUrl),
                                eq(HttpMethod.GET),
                                httpEntityCaptor.capture(),
                                any(ParameterizedTypeReference.class));

                assertEquals("Bearer " + bearerToken,
                                httpEntityCaptor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-004: getEmployeeNamesAndDepartmentNames — parse name và departmentName")
        void getEmployeeNamesAndDepartmentNames_whenDepartmentsPresent_returnsNestedFields() {
                // Test Case ID: SCH-USER-TC-004

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                List<Long> requestedEmployeeIds = List.of(1L, 2L);
                String bearerToken = "valid-token";

                Map<String, Object> humanResourcesDept = new HashMap<>();
                humanResourcesDept.put("name", "HR");

                Map<String, Object> informationTechnologyDept = new HashMap<>();
                informationTechnologyDept.put("name", "IT");

                List<Map<String, Object>> employeeRows = new ArrayList<>();
                employeeRows.add(new HashMap<>(
                                Map.of("id", 1, "name", "Nguyen Van A", "department", humanResourcesDept)));
                employeeRows.add(new HashMap<>(
                                Map.of("id", 2, "name", "Tran Thi B", "department", informationTechnologyDept)));

                Response<List<Map<String, Object>>> apiEnvelope = new Response<>();
                apiEnvelope.setData(employeeRows);

                String batchEmployeesUrl = employeesByIdsUrl("1,2");

                when(restTemplate.exchange(
                                eq(batchEmployeesUrl),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(apiEnvelope));

                // Act
                ResponseEntity<JsonNode> enrichedEmployeeResponse = userService
                                .getEmployeeNamesAndDepartmentNames(requestedEmployeeIds, bearerToken);

                // Assert
                assertNotNull(enrichedEmployeeResponse);
                assertEquals(HttpStatus.OK, enrichedEmployeeResponse.getStatusCode());
                assertNotNull(enrichedEmployeeResponse.getBody());

                JsonNode responseBody = enrichedEmployeeResponse.getBody();
                assertEquals("Nguyen Van A", responseBody.get("1").get("name").asText());
                assertEquals("HR", responseBody.get("1").get("departmentName").asText());
                assertEquals("Tran Thi B", responseBody.get("2").get("name").asText());
                assertEquals("IT", responseBody.get("2").get("departmentName").asText());

                ArgumentCaptor<HttpEntity<Void>> httpEntityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
                verify(restTemplate, times(1)).exchange(
                                eq(batchEmployeesUrl),
                                eq(HttpMethod.GET),
                                httpEntityCaptor.capture(),
                                any(ParameterizedTypeReference.class));

                assertEquals("Bearer " + bearerToken,
                                httpEntityCaptor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-005: getEmployeeNamesAndDepartmentNames — departmentName mặc định Unknown")
        void getEmployeeNamesAndDepartmentNames_whenDepartmentMissingOrInvalid_returnsUnknownDepartment() {
                // Test Case ID: SCH-USER-TC-005

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                List<Long> requestedEmployeeIds = List.of(1L, 2L);

                List<Map<String, Object>> employeeRows = new ArrayList<>();
                employeeRows.add(new HashMap<>(Map.of("id", 1, "name", "Nguyen Van A")));
                employeeRows.add(new HashMap<>(Map.of("id", 2, "name", "Tran Thi B", "department", "not-a-map")));

                Response<List<Map<String, Object>>> apiEnvelope = new Response<>();
                apiEnvelope.setData(employeeRows);

                String batchEmployeesUrl = employeesByIdsUrl("1,2");

                when(restTemplate.exchange(
                                eq(batchEmployeesUrl),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(apiEnvelope));

                // Act
                ResponseEntity<JsonNode> enrichedEmployeeResponse = userService
                                .getEmployeeNamesAndDepartmentNames(requestedEmployeeIds, null);

                // Assert
                assertNotNull(enrichedEmployeeResponse);
                assertEquals(HttpStatus.OK, enrichedEmployeeResponse.getStatusCode());
                assertNotNull(enrichedEmployeeResponse.getBody());
                assertEquals("Nguyen Van A", enrichedEmployeeResponse.getBody().get("1").get("name").asText());
                assertEquals("Unknown", enrichedEmployeeResponse.getBody().get("1").get("departmentName").asText());
                assertEquals("Tran Thi B", enrichedEmployeeResponse.getBody().get("2").get("name").asText());
                assertEquals("Unknown", enrichedEmployeeResponse.getBody().get("2").get("departmentName").asText());

                ArgumentCaptor<HttpEntity<Void>> httpEntityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
                verify(restTemplate, times(1)).exchange(
                                eq(batchEmployeesUrl),
                                eq(HttpMethod.GET),
                                httpEntityCaptor.capture(),
                                any(ParameterizedTypeReference.class));
                assertNull(httpEntityCaptor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-006: getUserIdByEmail — ưu tiên employeeId")
        void getUserIdByEmail_whenEmployeeIdPresent_returnsEmployeeId() {
                // Test Case ID: SCH-USER-TC-006

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                String candidateEmail = "user@example.com";

                Map<String, Object> userPayload = new HashMap<>();
                userPayload.put("employeeId", 555);

                Response<Map<String, Object>> apiEnvelope = new Response<>();
                apiEnvelope.setData(userPayload);

                when(restTemplate.exchange(
                                eq(userLookupByEmailUrl(candidateEmail)),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(apiEnvelope));

                // Act
                Long resolvedInternalUserId = userService.getUserIdByEmail(candidateEmail);

                // Assert
                assertNotNull(resolvedInternalUserId);
                assertEquals(555L, resolvedInternalUserId);

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-007: getUserIdByEmail — fallback sang id khi không có employeeId")
        void getUserIdByEmail_whenOnlyLegacyIdPresent_returnsId() {
                // Test Case ID: SCH-USER-TC-007

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                String candidateEmail = "user@example.com";

                Map<String, Object> userPayload = new HashMap<>();
                userPayload.put("id", 777);

                Response<Map<String, Object>> apiEnvelope = new Response<>();
                apiEnvelope.setData(userPayload);

                when(restTemplate.exchange(
                                eq(userLookupByEmailUrl(candidateEmail)),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(apiEnvelope));

                // Act
                Long resolvedInternalUserId = userService.getUserIdByEmail(candidateEmail);

                // Assert
                assertNotNull(resolvedInternalUserId);
                assertEquals(777L, resolvedInternalUserId);

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-008: getUserIdByEmail — trả null khi user-service ném exception")
        void getUserIdByEmail_whenRestTemplateThrows_returnsNull() {
                // Test Case ID: SCH-USER-TC-008

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                String candidateEmail = "user@example.com";

                when(restTemplate.exchange(
                                eq(userLookupByEmailUrl(candidateEmail)),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class)))
                                .thenThrow(new RuntimeException("user-service unavailable"));

                // Act
                Long resolvedInternalUserId = userService.getUserIdByEmail(candidateEmail);

                // Assert
                assertNull(resolvedInternalUserId);

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-009: getEmployeeIdsByFilters — build URL và parse danh sách id")
        void getEmployeeIdsByFilters_whenPaginationContainsRows_returnsParsedLongIds() {
                // Test Case ID: SCH-USER-TC-009

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                Long filterDepartmentId = 2L;
                Long filterPositionId = 3L;
                String bearerToken = "valid-token";

                PaginationDTO paginationPayload = new PaginationDTO();

                List<Map<String, Object>> employeeRows = new ArrayList<>();
                employeeRows.add(new HashMap<>(Map.of("id", 101)));
                employeeRows.add(new HashMap<>(Map.of("id", 102)));
                employeeRows.add(new HashMap<>(Map.of("id", 103)));

                paginationPayload.setResult(employeeRows);

                Response<PaginationDTO> apiEnvelope = new Response<>();
                apiEnvelope.setData(paginationPayload);

                when(restTemplate.exchange(
                                anyString(),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(apiEnvelope));

                // Act
                List<Long> filteredEmployeeIds = userService.getEmployeeIdsByFilters(filterDepartmentId,
                                filterPositionId, bearerToken);

                // Assert
                assertNotNull(filteredEmployeeIds);
                assertEquals(List.of(101L, 102L, 103L), filteredEmployeeIds);

                ArgumentCaptor<String> requestUrlCaptor = ArgumentCaptor.forClass(String.class);
                ArgumentCaptor<HttpEntity<Void>> httpEntityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

                verify(restTemplate, times(1)).exchange(
                                requestUrlCaptor.capture(),
                                eq(HttpMethod.GET),
                                httpEntityCaptor.capture(),
                                any(ParameterizedTypeReference.class));

                String capturedRequestUrl = requestUrlCaptor.getValue();
                assertNotNull(capturedRequestUrl);
                assertTrue(capturedRequestUrl.startsWith(userServiceBaseUrl + "/api/v1/user-service/employees"));
                assertTrue(capturedRequestUrl.contains("page=1"));
                assertTrue(capturedRequestUrl.contains("limit=1000"));
                assertTrue(capturedRequestUrl.contains("departmentId=2"));
                assertTrue(capturedRequestUrl.contains("positionId=3"));

                assertEquals("Bearer " + bearerToken,
                                httpEntityCaptor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-010: getEmployeeIdsByFilters — trả list rỗng khi có exception")
        void getEmployeeIdsByFilters_whenRestTemplateThrows_returnsEmptyList() {
                // Test Case ID: SCH-USER-TC-010

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                when(restTemplate.exchange(
                                anyString(),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class)))
                                .thenThrow(new RuntimeException("network failure"));

                // Act
                List<Long> filteredEmployeeIds = userService.getEmployeeIdsByFilters(2L, null, "valid-token");

                // Assert
                assertNotNull(filteredEmployeeIds);
                assertEquals(List.of(), filteredEmployeeIds);

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-011: getAllEmployeeIds — GET employees không filter, có Bearer token")
        void getAllEmployeeIds_callsUnfilteredEmployeesEndpointWithAuthorization() {
                // Test Case ID: SCH-USER-TC-011

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                Response<PaginationDTO> apiEnvelope = new Response<>();
                PaginationDTO paginationPayload = new PaginationDTO();
                paginationPayload.setResult(List.of(
                                new HashMap<>(Map.of("id", 11)),
                                new HashMap<>(Map.of("id", 22))));
                apiEnvelope.setData(paginationPayload);

                when(restTemplate.exchange(
                                anyString(),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(apiEnvelope));

                // Act
                List<Long> allEmployeeIds = userService.getAllEmployeeIds("t");

                // Assert
                assertNotNull(allEmployeeIds);
                assertEquals(List.of(11L, 22L), allEmployeeIds);

                ArgumentCaptor<String> requestUrlCaptor = ArgumentCaptor.forClass(String.class);
                ArgumentCaptor<HttpEntity<Void>> httpEntityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
                verify(restTemplate, times(1)).exchange(
                                requestUrlCaptor.capture(),
                                eq(HttpMethod.GET),
                                httpEntityCaptor.capture(),
                                any(ParameterizedTypeReference.class));

                String capturedRequestUrl = requestUrlCaptor.getValue();
                assertTrue(capturedRequestUrl.startsWith(userServiceBaseUrl + "/api/v1/user-service/employees"));
                assertFalse(capturedRequestUrl.contains("departmentId="));
                assertFalse(capturedRequestUrl.contains("positionId="));
                assertEquals("Bearer t",
                                httpEntityCaptor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-012: getEmployeeName — ResponseEntity body null → NOT_FOUND")
        void getEmployeeName_whenHttpBodyNull_returnsNotFound() {
                // Test Case ID: SCH-USER-TC-012

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                Long requestedEmployeeId = 999L;
                when(restTemplate.exchange(
                                eq(employeeDetailUrl(requestedEmployeeId)),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(null));

                // Act
                ResponseEntity<JsonNode> employeeNameResponse = userService.getEmployeeName(requestedEmployeeId, null);

                // Assert
                assertEquals(HttpStatus.NOT_FOUND, employeeNameResponse.getStatusCode());
                assertNull(employeeNameResponse.getBody());

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-013: getEmployeeName — thiếu field name → NOT_FOUND")
        void getEmployeeName_whenNameFieldAbsent_returnsNotFound() {
                // Test Case ID: SCH-USER-TC-013

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                Long requestedEmployeeId = 1L;
                Response<Map<String, Object>> apiEnvelope = new Response<>();
                apiEnvelope.setData(new HashMap<>());

                when(restTemplate.exchange(
                                eq(employeeDetailUrl(requestedEmployeeId)),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(apiEnvelope));

                // Act
                ResponseEntity<JsonNode> employeeNameResponse = userService.getEmployeeName(requestedEmployeeId, "t");

                // Assert
                assertEquals(HttpStatus.NOT_FOUND, employeeNameResponse.getStatusCode());
                assertNull(employeeNameResponse.getBody());

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-014: getEmployeeName — exception → HTTP 500 với payload lỗi")
        void getEmployeeName_whenRestTemplateThrows_returnsInternalServerErrorJson() {
                // Test Case ID: SCH-USER-TC-014

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                when(restTemplate.exchange(
                                anyString(),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class)))
                                .thenThrow(new RuntimeException("connection reset"));

                // Act
                ResponseEntity<JsonNode> employeeNameResponse = userService.getEmployeeName(1L, "t");

                // Assert
                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, employeeNameResponse.getStatusCode());
                assertNotNull(employeeNameResponse.getBody());
                assertEquals(500, employeeNameResponse.getBody().path("statusCode").asInt());

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-015: getEmployeeNames — employeeIds rỗng → object rỗng")
        void getEmployeeNames_whenIdListEmpty_returnsEmptyObjectNode() {
                // Test Case ID: SCH-USER-TC-015

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                Response<List<Map<String, Object>>> apiEnvelope = new Response<>();
                apiEnvelope.setData(List.of());
                when(restTemplate.exchange(
                                eq(employeesByIdsUrl("")),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(apiEnvelope));

                // Act
                ResponseEntity<JsonNode> idToNameResponse = userService.getEmployeeNames(List.of(), "");

                // Assert
                assertEquals(HttpStatus.OK, idToNameResponse.getStatusCode());
                assertNotNull(idToNameResponse.getBody());
                assertEquals(0, idToNameResponse.getBody().size());

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-016: getEmployeeNames — exception → HTTP 500")
        void getEmployeeNames_whenRestTemplateThrows_returnsInternalServerErrorJson() {
                // Test Case ID: SCH-USER-TC-016

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                when(restTemplate.exchange(
                                anyString(),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class)))
                                .thenThrow(new RuntimeException("timeout"));

                // Act
                ResponseEntity<JsonNode> idToNameResponse = userService.getEmployeeNames(List.of(1L), null);

                // Assert
                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, idToNameResponse.getStatusCode());
                assertNotNull(idToNameResponse.getBody());
                assertEquals(500, idToNameResponse.getBody().path("statusCode").asInt());

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-017: getEmployeeNames — HTTP body null → map rỗng")
        void getEmployeeNames_whenHttpEnvelopeNull_returnsEmptyMap() {
                // Test Case ID: SCH-USER-TC-017

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                when(restTemplate.exchange(
                                anyString(),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(null));

                // Act
                ResponseEntity<JsonNode> idToNameResponse = userService.getEmployeeNames(List.of(1L), null);

                // Assert
                assertEquals(HttpStatus.OK, idToNameResponse.getStatusCode());
                assertNotNull(idToNameResponse.getBody());
                assertEquals(0, idToNameResponse.getBody().size());

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-018: getEmployeeNames — data null → map rỗng")
        void getEmployeeNames_whenDataNull_returnsEmptyMap() {
                // Test Case ID: SCH-USER-TC-018

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                Response<List<Map<String, Object>>> apiEnvelope = new Response<>();
                apiEnvelope.setData(null);
                when(restTemplate.exchange(
                                anyString(),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(apiEnvelope));

                // Act
                ResponseEntity<JsonNode> idToNameResponse = userService.getEmployeeNames(List.of(1L), null);

                // Assert
                assertEquals(HttpStatus.OK, idToNameResponse.getStatusCode());
                assertNotNull(idToNameResponse.getBody());
                assertEquals(0, idToNameResponse.getBody().size());

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-019: getEmployeeNames — bỏ qua bản ghi thiếu id hoặc name")
        void getEmployeeNames_whenSomeRowsIncomplete_omitsInvalidEntries() {
                // Test Case ID: SCH-USER-TC-019

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                List<Long> requestedEmployeeIds = List.of(1L, 2L);
                List<Map<String, Object>> employeeRows = new ArrayList<>();
                employeeRows.add(new HashMap<>(Map.of("id", 1, "name", "Nguyen Van A")));
                employeeRows.add(new HashMap<>(Map.of("id", 2)));
                employeeRows.add(new HashMap<>(Map.of("name", "Le Van C")));

                Response<List<Map<String, Object>>> apiEnvelope = new Response<>();
                apiEnvelope.setData(employeeRows);

                when(restTemplate.exchange(
                                eq(employeesByIdsUrl("1,2")),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(apiEnvelope));

                // Act
                ResponseEntity<JsonNode> idToNameResponse = userService.getEmployeeNames(requestedEmployeeIds, null);

                // Assert
                assertEquals(HttpStatus.OK, idToNameResponse.getStatusCode());
                assertNotNull(idToNameResponse.getBody());
                assertEquals("Nguyen Van A", idToNameResponse.getBody().get("1").asText());
                assertNull(idToNameResponse.getBody().get("2"));

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-020: getEmployeeNamesAndDepartmentNames — ids rỗng → object rỗng")
        void getEmployeeNamesAndDepartmentNames_whenIdListEmpty_returnsEmptyObject() {
                // Test Case ID: SCH-USER-TC-020

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                Response<List<Map<String, Object>>> apiEnvelope = new Response<>();
                apiEnvelope.setData(List.of());
                when(restTemplate.exchange(
                                eq(employeesByIdsUrl("")),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(apiEnvelope));

                // Act
                ResponseEntity<JsonNode> enrichedEmployeeResponse = userService
                                .getEmployeeNamesAndDepartmentNames(List.of(), null);

                // Assert
                assertEquals(HttpStatus.OK, enrichedEmployeeResponse.getStatusCode());
                assertNotNull(enrichedEmployeeResponse.getBody());
                assertEquals(0, enrichedEmployeeResponse.getBody().size());

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-021: getEmployeeNamesAndDepartmentNames — thiếu name → Unknown")
        void getEmployeeNamesAndDepartmentNames_whenNameMissing_usesUnknownName() {
                // Test Case ID: SCH-USER-TC-021

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                List<Map<String, Object>> employeeRows = List
                                .of(new HashMap<>(Map.of("id", 1, "department", Map.of("name", "HR"))));
                Response<List<Map<String, Object>>> apiEnvelope = new Response<>();
                apiEnvelope.setData(employeeRows);
                when(restTemplate.exchange(
                                eq(employeesByIdsUrl("1")),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(apiEnvelope));

                // Act
                ResponseEntity<JsonNode> enrichedEmployeeResponse = userService
                                .getEmployeeNamesAndDepartmentNames(List.of(1L), "t");

                // Assert
                assertEquals("Unknown", enrichedEmployeeResponse.getBody().get("1").get("name").asText());
                assertEquals("HR", enrichedEmployeeResponse.getBody().get("1").get("departmentName").asText());

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-022: getEmployeeNamesAndDepartmentNames — HTTP body null → rỗng")
        void getEmployeeNamesAndDepartmentNames_whenHttpEnvelopeNull_returnsEmptyObject() {
                // Test Case ID: SCH-USER-TC-022

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                when(restTemplate.exchange(
                                anyString(),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(null));

                // Act
                ResponseEntity<JsonNode> enrichedEmployeeResponse = userService
                                .getEmployeeNamesAndDepartmentNames(List.of(1L), null);

                // Assert
                assertEquals(HttpStatus.OK, enrichedEmployeeResponse.getStatusCode());
                assertNotNull(enrichedEmployeeResponse.getBody());
                assertEquals(0, enrichedEmployeeResponse.getBody().size());

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-023: getEmployeeNamesAndDepartmentNames — data null → rỗng")
        void getEmployeeNamesAndDepartmentNames_whenDataNull_returnsEmptyObject() {
                // Test Case ID: SCH-USER-TC-023

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                Response<List<Map<String, Object>>> apiEnvelope = new Response<>();
                apiEnvelope.setData(null);
                when(restTemplate.exchange(
                                anyString(),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(apiEnvelope));

                // Act
                ResponseEntity<JsonNode> enrichedEmployeeResponse = userService
                                .getEmployeeNamesAndDepartmentNames(List.of(1L), null);

                // Assert
                assertEquals(HttpStatus.OK, enrichedEmployeeResponse.getStatusCode());
                assertNotNull(enrichedEmployeeResponse.getBody());
                assertEquals(0, enrichedEmployeeResponse.getBody().size());

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-024: getEmployeeNamesAndDepartmentNames — id null → bỏ qua dòng")
        void getEmployeeNamesAndDepartmentNames_whenRowIdNull_skipsRow() {
                // Test Case ID: SCH-USER-TC-024

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                List<Map<String, Object>> employeeRows = new ArrayList<>();
                employeeRows.add(new HashMap<>(Map.of("id", 1, "name", "Nguyen Van A", "department",
                                Map.of("name", "HR"))));
                Map<String, Object> rowWithNullId = new HashMap<>();
                rowWithNullId.put("id", null);
                rowWithNullId.put("name", "Tran Thi B");
                employeeRows.add(rowWithNullId);

                Response<List<Map<String, Object>>> apiEnvelope = new Response<>();
                apiEnvelope.setData(employeeRows);

                when(restTemplate.exchange(
                                eq(employeesByIdsUrl("1")),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(apiEnvelope));

                // Act
                ResponseEntity<JsonNode> enrichedEmployeeResponse = userService
                                .getEmployeeNamesAndDepartmentNames(List.of(1L), null);

                // Assert
                assertNotNull(enrichedEmployeeResponse.getBody().get("1"));

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-025: getEmployeeNamesAndDepartmentNames — department.name null → Unknown")
        void getEmployeeNamesAndDepartmentNames_whenDepartmentNameNull_returnsUnknownDepartment() {
                // Test Case ID: SCH-USER-TC-025

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                Map<String, Object> departmentWithNullName = new HashMap<>();
                departmentWithNullName.put("name", null);
                List<Map<String, Object>> employeeRows = List.of(
                                new HashMap<>(Map.of("id", 1, "name", "Nguyen Van A", "department",
                                                departmentWithNullName)));

                Response<List<Map<String, Object>>> apiEnvelope = new Response<>();
                apiEnvelope.setData(employeeRows);

                when(restTemplate.exchange(
                                eq(employeesByIdsUrl("1")),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(apiEnvelope));

                // Act
                ResponseEntity<JsonNode> enrichedEmployeeResponse = userService
                                .getEmployeeNamesAndDepartmentNames(List.of(1L), null);

                // Assert
                assertEquals("Unknown", enrichedEmployeeResponse.getBody().get("1").get("departmentName").asText());

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-026: getEmployeeNamesAndDepartmentNames — exception → HTTP 500")
        void getEmployeeNamesAndDepartmentNames_whenRestTemplateThrows_returnsInternalServerErrorJson() {
                // Test Case ID: SCH-USER-TC-026

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                when(restTemplate.exchange(
                                anyString(),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class)))
                                .thenThrow(new RuntimeException("bad gateway"));

                // Act
                ResponseEntity<JsonNode> enrichedEmployeeResponse = userService
                                .getEmployeeNamesAndDepartmentNames(List.of(1L), "");

                // Assert
                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, enrichedEmployeeResponse.getStatusCode());
                assertNotNull(enrichedEmployeeResponse.getBody());
                assertEquals(500, enrichedEmployeeResponse.getBody().path("statusCode").asInt());

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-027: getUserIdByEmail — employeeId và id đều absent → null")
        void getUserIdByEmail_whenNoNumericIdentifiers_returnsNull() {
                // Test Case ID: SCH-USER-TC-027

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                String candidateEmail = "user@example.com";
                Response<Map<String, Object>> apiEnvelope = new Response<>();
                apiEnvelope.setData(new HashMap<>());
                when(restTemplate.exchange(
                                eq(userLookupByEmailUrl(candidateEmail)),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(apiEnvelope));

                // Act
                Long resolvedInternalUserId = userService.getUserIdByEmail(candidateEmail);

                // Assert
                assertNull(resolvedInternalUserId);

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-028: getUserIdByEmail — HTTP body null → null")
        void getUserIdByEmail_whenHttpEnvelopeNull_returnsNull() {
                // Test Case ID: SCH-USER-TC-028

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                String candidateEmail = "user@example.com";
                when(restTemplate.exchange(
                                eq(userLookupByEmailUrl(candidateEmail)),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(null));

                // Act
                Long resolvedInternalUserId = userService.getUserIdByEmail(candidateEmail);

                // Assert
                assertNull(resolvedInternalUserId);

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-029: getUserIdByEmail — data null → null")
        void getUserIdByEmail_whenDataNull_returnsNull() {
                // Test Case ID: SCH-USER-TC-029

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                String candidateEmail = "user@example.com";
                Response<Map<String, Object>> apiEnvelope = new Response<>();
                apiEnvelope.setData(null);
                when(restTemplate.exchange(
                                eq(userLookupByEmailUrl(candidateEmail)),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                any(ParameterizedTypeReference.class))).thenReturn(ResponseEntity.ok(apiEnvelope));

                // Act
                Long resolvedInternalUserId = userService.getUserIdByEmail(candidateEmail);

                // Assert
                assertNull(resolvedInternalUserId);

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-030: getEmployeeIdsByFilters — chỉ departmentId trong query")
        void getEmployeeIdsByFilters_whenOnlyDepartmentFilter_buildsUrlWithDepartmentOnly() {
                // Test Case ID: SCH-USER-TC-030

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                Response<PaginationDTO> apiEnvelope = new Response<>();
                PaginationDTO paginationPayload = new PaginationDTO();
                paginationPayload.setResult(List.of());
                apiEnvelope.setData(paginationPayload);
                when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                                any(ParameterizedTypeReference.class)))
                                .thenReturn(ResponseEntity.ok(apiEnvelope));

                // Act
                userService.getEmployeeIdsByFilters(2L, null, "t");

                // Assert
                ArgumentCaptor<String> requestUrlCaptor = ArgumentCaptor.forClass(String.class);
                verify(restTemplate).exchange(requestUrlCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class),
                                any(ParameterizedTypeReference.class));
                String capturedRequestUrl = requestUrlCaptor.getValue();
                assertTrue(capturedRequestUrl.contains("departmentId=2"));
                assertFalse(capturedRequestUrl.contains("positionId="));

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-031: getEmployeeIdsByFilters — chỉ positionId trong query")
        void getEmployeeIdsByFilters_whenOnlyPositionFilter_buildsUrlWithPositionOnly() {
                // Test Case ID: SCH-USER-TC-031

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                Response<PaginationDTO> apiEnvelope = new Response<>();
                PaginationDTO paginationPayload = new PaginationDTO();
                paginationPayload.setResult(List.of());
                apiEnvelope.setData(paginationPayload);
                when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                                any(ParameterizedTypeReference.class)))
                                .thenReturn(ResponseEntity.ok(apiEnvelope));

                // Act
                userService.getEmployeeIdsByFilters(null, 3L, "t");

                // Assert
                ArgumentCaptor<String> requestUrlCaptor = ArgumentCaptor.forClass(String.class);
                verify(restTemplate).exchange(requestUrlCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class),
                                any(ParameterizedTypeReference.class));
                String capturedRequestUrl = requestUrlCaptor.getValue();
                assertTrue(capturedRequestUrl.contains("positionId=3"));
                assertFalse(capturedRequestUrl.contains("departmentId="));

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-032: getEmployeeIdsByFilters — không filter → URL không có department/position")
        void getEmployeeIdsByFilters_whenNoFilters_omitsDepartmentAndPositionParams() {
                // Test Case ID: SCH-USER-TC-032

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                Response<PaginationDTO> apiEnvelope = new Response<>();
                PaginationDTO paginationPayload = new PaginationDTO();
                paginationPayload.setResult(List.of());
                apiEnvelope.setData(paginationPayload);
                when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                                any(ParameterizedTypeReference.class)))
                                .thenReturn(ResponseEntity.ok(apiEnvelope));

                // Act
                userService.getEmployeeIdsByFilters(null, null, "t");

                // Assert
                ArgumentCaptor<String> requestUrlCaptor = ArgumentCaptor.forClass(String.class);
                verify(restTemplate).exchange(requestUrlCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class),
                                any(ParameterizedTypeReference.class));
                String capturedRequestUrl = requestUrlCaptor.getValue();
                assertFalse(capturedRequestUrl.contains("departmentId="));
                assertFalse(capturedRequestUrl.contains("positionId="));

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-033: getEmployeeIdsByFilters — HTTP body null → list rỗng")
        void getEmployeeIdsByFilters_whenHttpEnvelopeNull_returnsEmptyList() {
                // Test Case ID: SCH-USER-TC-033

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                                any(ParameterizedTypeReference.class)))
                                .thenReturn(ResponseEntity.ok(null));

                // Act
                List<Long> filteredEmployeeIds = userService.getEmployeeIdsByFilters(2L, 3L, "t");

                // Assert
                assertEquals(List.of(), filteredEmployeeIds);

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-034: getEmployeeIdsByFilters — data null → list rỗng")
        void getEmployeeIdsByFilters_whenDataNull_returnsEmptyList() {
                // Test Case ID: SCH-USER-TC-034

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                Response<PaginationDTO> apiEnvelope = new Response<>();
                apiEnvelope.setData(null);
                when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                                any(ParameterizedTypeReference.class)))
                                .thenReturn(ResponseEntity.ok(apiEnvelope));

                // Act
                List<Long> filteredEmployeeIds = userService.getEmployeeIdsByFilters(2L, 3L, "t");

                // Assert
                assertEquals(List.of(), filteredEmployeeIds);

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-035: getEmployeeIdsByFilters — result không phải List → rỗng")
        void getEmployeeIdsByFilters_whenResultNotList_returnsEmptyList() {
                // Test Case ID: SCH-USER-TC-035

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                PaginationDTO paginationPayload = new PaginationDTO();
                paginationPayload.setResult("not-a-list");
                Response<PaginationDTO> apiEnvelope = new Response<>();
                apiEnvelope.setData(paginationPayload);

                when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                                any(ParameterizedTypeReference.class)))
                                .thenReturn(ResponseEntity.ok(apiEnvelope));

                // Act
                List<Long> filteredEmployeeIds = userService.getEmployeeIdsByFilters(null, null, null);

                // Assert
                assertEquals(List.of(), filteredEmployeeIds);

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }

        @Test
        @DisplayName("SCH-USER-TC-036: getEmployeeIdsByFilters — chỉ giữ id kiểu Number hợp lệ")
        void getEmployeeIdsByFilters_whenMixedRowShapes_collectsOnlyNumericIds() {
                // Test Case ID: SCH-USER-TC-036

                // Arrange
                final long scheduleRowCountBefore = scheduleRepository.count();
                final long participantRowCountBefore = scheduleParticipantRepository.count();

                List<Object> heterogeneousRows = new ArrayList<>();
                heterogeneousRows.add(new HashMap<>(Map.of("id", 1)));
                heterogeneousRows.add(new HashMap<>());
                heterogeneousRows.add(new HashMap<>(Map.of("id", "x")));
                heterogeneousRows.add("not-a-map");

                PaginationDTO paginationPayload = new PaginationDTO();
                paginationPayload.setResult(heterogeneousRows);
                Response<PaginationDTO> apiEnvelope = new Response<>();
                apiEnvelope.setData(paginationPayload);

                when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                                any(ParameterizedTypeReference.class)))
                                .thenReturn(ResponseEntity.ok(apiEnvelope));

                // Act
                List<Long> filteredEmployeeIds = userService.getEmployeeIdsByFilters(null, null, "");

                // Assert
                assertEquals(List.of(1L), filteredEmployeeIds);

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCountBefore, participantRowCountBefore);
        }
}
