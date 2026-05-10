package com.example.schedule_service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.example.schedule_service.model.Schedule;
import com.example.schedule_service.repository.ScheduleParticipantRepository;
import com.example.schedule_service.repository.ScheduleRepository;
import com.example.schedule_service.utils.enums.MeetingType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Unit / slice tests cho {@link CandidateService} với mock HTTP client và JPA thật (H2)
 * để CheckDB đảm bảo không có tác động phụ lên DB nội bộ schedule-service.
 */
@DataJpaTest
@Import(CandidateService.class)
@ActiveProfiles("test")
@Transactional
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CandidateServiceTest {

        @MockitoBean
        private RestTemplate restTemplate;

        @MockitoBean
        private ObjectMapper objectMapper;

        @Autowired
        private ScheduleRepository scheduleRepository;

        @Autowired
        private ScheduleParticipantRepository scheduleParticipantRepository;

        @Autowired
        private CandidateService candidateService;

        private Long baselineScheduleId;
        private String baselineScheduleTitle;

        @BeforeEach
        void setUp() {
                Schedule baseline = new Schedule();
                baseline.setTitle("SCH-CAND-DB-BASELINE");
                baseline.setStatus("SCHEDULED");
                baseline.setMeetingType(MeetingType.MEETING);
                baseline.setStartTime(LocalDateTime.of(2099, 1, 1, 10, 0));
                baseline.setEndTime(LocalDateTime.of(2099, 1, 1, 11, 0));
                Schedule saved = scheduleRepository.saveAndFlush(baseline);
                baselineScheduleId = saved.getId();
                baselineScheduleTitle = saved.getTitle();
        }

        private void assertDatabaseUnchanged(long expectedScheduleCount, long expectedParticipantCount) {
                assertEquals(expectedScheduleCount, scheduleRepository.count(),
                                "CheckDB: số bản ghi bảng schedule không được thay đổi sau khi gọi CandidateService");
                assertEquals(expectedParticipantCount, scheduleParticipantRepository.count(),
                                "CheckDB: số bản ghi bảng schedule_participant không được thay đổi");
                Schedule reloadedBaseline = scheduleRepository.findById(baselineScheduleId).orElseThrow();
                assertEquals(baselineScheduleTitle, reloadedBaseline.getTitle(),
                                "CheckDB: bản ghi baseline phải giữ nguyên — CandidateService chỉ gọi HTTP ra ngoài");
        }

        @Test
        @DisplayName("SCH-CAND-TC-001: getCandidateName - parse đúng tên candidate từ response JSON")
        void getCandidateName_whenApiReturnsOk_returnsCandidateNameJsonNode() throws Exception {
                // Test Case ID: SCH-CAND-TC-001

                // Arrange
                final long scheduleRowCount = scheduleRepository.count();
                final long participantRowCount = scheduleParticipantRepository.count();

                Long candidateId = 1L;
                String bearerToken = "valid-token";
                String candidateApiResponseJson = "{\"data\":{\"name\":\"An\"}}";

                JsonNode jsonRoot = mock(JsonNode.class);
                JsonNode dataSection = mock(JsonNode.class);
                JsonNode nameField = mock(JsonNode.class);

                when(restTemplate.exchange(
                                eq("http://candidate-service/api/v1/candidate-service/candidates/1"),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenReturn(ResponseEntity.ok(candidateApiResponseJson));

                when(objectMapper.readTree(eq(candidateApiResponseJson))).thenReturn(jsonRoot);
                when(jsonRoot.get("data")).thenReturn(dataSection);
                when(dataSection.get("name")).thenReturn(nameField);

                // Act
                ResponseEntity<JsonNode> response = candidateService.getCandidateName(candidateId, bearerToken);

                // Assert
                assertNotNull(response);
                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertNotNull(response.getBody());
                assertSame(nameField, response.getBody());

                verify(restTemplate, times(1)).exchange(
                                eq("http://candidate-service/api/v1/candidate-service/candidates/1"),
                                eq(HttpMethod.GET),
                                argThat(entity -> ("Bearer " + bearerToken)
                                                .equals(entity.getHeaders().getFirst("Authorization"))),
                                eq(String.class));
                verify(objectMapper, times(1)).readTree(eq(candidateApiResponseJson));

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCount, participantRowCount);
        }

        @Test
        @DisplayName("SCH-CAND-TC-002: getCandidateName - trả lại đúng error body khi HTTP client error")
        void getCandidateName_whenHttpClientError_returnsParsedErrorBody() throws Exception {
                // Test Case ID: SCH-CAND-TC-002

                // Arrange
                final long scheduleRowCount = scheduleRepository.count();
                final long participantRowCount = scheduleParticipantRepository.count();

                Long candidateId = 999L;
                String errorResponseJson = "{\"statusCode\":404,\"error\":\"Not Found\",\"message\":\"Candidate not found\",\"data\":null}";
                HttpClientErrorException notFoundException = HttpClientErrorException.create(
                                HttpStatus.NOT_FOUND,
                                "Not Found",
                                HttpHeaders.EMPTY,
                                errorResponseJson.getBytes(StandardCharsets.UTF_8),
                                StandardCharsets.UTF_8);

                JsonNode parsedErrorNode = mock(JsonNode.class);

                when(restTemplate.exchange(
                                eq("http://candidate-service/api/v1/candidate-service/candidates/999"),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenThrow(notFoundException);

                when(objectMapper.readTree(eq(errorResponseJson))).thenReturn(parsedErrorNode);

                // Act
                ResponseEntity<JsonNode> response = candidateService.getCandidateName(candidateId, null);

                // Assert
                assertNotNull(response);
                assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
                assertNotNull(response.getBody());
                assertSame(parsedErrorNode, response.getBody());

                verify(objectMapper, times(1)).readTree(eq(errorResponseJson));

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCount, participantRowCount);
        }

        @Test
        @DisplayName("SCH-CAND-TC-003: getCandidateName - trả lỗi 500 khi không kết nối được candidate-service")
        void getCandidateName_whenConnectionFails_returnsInternalServerErrorPayload() {
                // Test Case ID: SCH-CAND-TC-003

                // Arrange
                final long scheduleRowCount = scheduleRepository.count();
                final long participantRowCount = scheduleParticipantRepository.count();

                Long candidateId = 1L;

                when(restTemplate.exchange(
                                eq("http://candidate-service/api/v1/candidate-service/candidates/1"),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenThrow(new ResourceAccessException("Connection refused"));

                ObjectNode fallbackErrorPayload = new ObjectMapper().createObjectNode();
                when(objectMapper.createObjectNode()).thenReturn(fallbackErrorPayload);

                // Act
                ResponseEntity<JsonNode> response = candidateService.getCandidateName(candidateId, "");

                // Assert
                assertNotNull(response);
                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
                assertNotNull(response.getBody());
                assertEquals(500, response.getBody().path("statusCode").asInt());
                assertEquals("Internal Server Error", response.getBody().path("error").asText());
                assertTrue(response.getBody().path("message").asText().contains("Không thể kết nối"));

                verify(objectMapper, times(1)).createObjectNode();

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCount, participantRowCount);
        }

        @Test
        @DisplayName("SCH-CAND-TC-004: getCandidateNames - map đúng id sang name")
        void getCandidateNames_whenBatchApiReturnsOk_mapsIdsToNames() throws Exception {
                // Test Case ID: SCH-CAND-TC-004

                // Arrange
                final long scheduleRowCount = scheduleRepository.count();
                final long participantRowCount = scheduleParticipantRepository.count();

                List<Long> candidateIds = List.of(1L, 2L);
                String bearerToken = "valid-token";
                String batchResponseJson = "{\"data\":[{\"id\":1,\"name\":\"An\"},{\"id\":2,\"name\":\"Binh\"}]}";

                JsonNode jsonRoot = mock(JsonNode.class);
                ArrayNode candidatesArray = mock(ArrayNode.class);

                JsonNode firstCandidateNode = mock(JsonNode.class);
                JsonNode secondCandidateNode = mock(JsonNode.class);

                JsonNode firstIdNode = mock(JsonNode.class);
                JsonNode firstNameNode = mock(JsonNode.class);

                JsonNode secondIdNode = mock(JsonNode.class);
                JsonNode secondNameNode = mock(JsonNode.class);

                ObjectNode idToNameMap = new ObjectMapper().createObjectNode();

                when(restTemplate.exchange(
                                eq("http://candidate-service/api/v1/candidate-service/candidates?ids=1,2"),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenReturn(ResponseEntity.ok(batchResponseJson));

                when(objectMapper.readTree(eq(batchResponseJson))).thenReturn(jsonRoot);
                when(jsonRoot.get("data")).thenReturn(candidatesArray);

                when(objectMapper.createObjectNode()).thenReturn(idToNameMap);

                when(candidatesArray.isArray()).thenReturn(true);
                when(candidatesArray.iterator()).thenReturn(List.<JsonNode>of(firstCandidateNode, secondCandidateNode).iterator());

                when(firstCandidateNode.has("id")).thenReturn(true);
                when(firstCandidateNode.has("name")).thenReturn(true);
                when(firstCandidateNode.get("id")).thenReturn(firstIdNode);
                when(firstIdNode.asLong()).thenReturn(1L);
                when(firstCandidateNode.get("name")).thenReturn(firstNameNode);
                when(firstNameNode.asText()).thenReturn("An");

                when(secondCandidateNode.has("id")).thenReturn(true);
                when(secondCandidateNode.has("name")).thenReturn(true);
                when(secondCandidateNode.get("id")).thenReturn(secondIdNode);
                when(secondIdNode.asLong()).thenReturn(2L);
                when(secondCandidateNode.get("name")).thenReturn(secondNameNode);
                when(secondNameNode.asText()).thenReturn("Binh");

                // Act
                ResponseEntity<JsonNode> response = candidateService.getCandidateNames(candidateIds, bearerToken);

                // Assert
                assertNotNull(response);
                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertNotNull(response.getBody());
                assertEquals("An", response.getBody().get("1").asText());
                assertEquals("Binh", response.getBody().get("2").asText());

                verify(restTemplate, times(1)).exchange(
                                eq("http://candidate-service/api/v1/candidate-service/candidates?ids=1,2"),
                                eq(HttpMethod.GET),
                                argThat(entity -> ("Bearer " + bearerToken)
                                                .equals(entity.getHeaders().getFirst("Authorization"))),
                                eq(String.class));
                verify(objectMapper, times(1)).createObjectNode();

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCount, participantRowCount);
        }

        @Test
        @DisplayName("SCH-CAND-TC-005: getCandidateNames - trả error body đúng khi candidate-service lỗi")
        void getCandidateNames_whenHttpClientError_returnsParsedErrorBody() throws Exception {
                // Test Case ID: SCH-CAND-TC-005

                // Arrange
                final long scheduleRowCount = scheduleRepository.count();
                final long participantRowCount = scheduleParticipantRepository.count();

                List<Long> candidateIds = List.of(1L, 2L);
                String errorResponseJson = "{\"statusCode\":500,\"error\":\"Internal\",\"message\":\"Down\",\"data\":null}";

                HttpClientErrorException badRequestException = HttpClientErrorException.create(
                                HttpStatus.BAD_REQUEST,
                                "Bad Request",
                                HttpHeaders.EMPTY,
                                errorResponseJson.getBytes(StandardCharsets.UTF_8),
                                StandardCharsets.UTF_8);

                JsonNode parsedErrorNode = mock(JsonNode.class);

                when(restTemplate.exchange(
                                eq("http://candidate-service/api/v1/candidate-service/candidates?ids=1,2"),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenThrow(badRequestException);

                when(objectMapper.readTree(eq(errorResponseJson))).thenReturn(parsedErrorNode);

                // Act
                ResponseEntity<JsonNode> response = candidateService.getCandidateNames(candidateIds, null);

                // Assert
                assertNotNull(response);
                assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
                assertNotNull(response.getBody());
                assertSame(parsedErrorNode, response.getBody());

                verify(objectMapper, times(1)).readTree(eq(errorResponseJson));

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCount, participantRowCount);
        }

        @Test
        @DisplayName("SCH-CAND-TC-006: getEmployeeIdFromCandidateEmail - ưu tiên updatedBy")
        void getEmployeeIdFromCandidateEmail_whenDetailHasUpdatedBy_returnsUpdatedByEmployeeId() throws Exception {
                // Test Case ID: SCH-CAND-TC-006

                // Arrange
                final long scheduleRowCount = scheduleRepository.count();
                final long participantRowCount = scheduleParticipantRepository.count();

                String candidateEmail = "cand@gmail.com";

                String searchBody = """
                                {
                                  "data": {
                                    "result": [
                                      {"id": 1, "email": "cand@gmail.com"},
                                      {"id": 2, "email": "other@gmail.com"}
                                    ]
                                  }
                                }
                                """;

                String detailBody = """
                                {
                                  "data": {
                                    "result": [
                                      {"updatedBy": 99, "createdBy": 77}
                                    ]
                                  }
                                }
                                """;

                when(restTemplate.exchange(
                                startsWith("http://candidate-service/api/v1/candidate-service/candidates?keyword="),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenReturn(ResponseEntity.ok(searchBody));

                when(restTemplate.exchange(
                                eq("http://candidate-service/api/v1/candidate-service/candidates?candidateId=1&page=1&limit=1&sortBy=id&sortOrder=desc"),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenReturn(ResponseEntity.ok(detailBody));

                ObjectMapper jsonParser = new ObjectMapper();
                when(objectMapper.readTree(eq(searchBody))).thenReturn(jsonParser.readTree(searchBody));
                when(objectMapper.readTree(eq(detailBody))).thenReturn(jsonParser.readTree(detailBody));

                // Act
                Long employeeId = candidateService.getEmployeeIdFromCandidateEmail(candidateEmail);

                // Assert
                assertNotNull(employeeId);
                assertEquals(99L, employeeId);

                verify(restTemplate, times(2)).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                                eq(String.class));
                verify(objectMapper, times(2)).readTree(anyString());

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCount, participantRowCount);
        }

        @Test
        @DisplayName("SCH-CAND-TC-007: getEmployeeIdFromCandidateEmail - fallback sang createdBy")
        void getEmployeeIdFromCandidateEmail_whenUpdatedByNull_returnsCreatedByEmployeeId() throws Exception {
                // Test Case ID: SCH-CAND-TC-007

                // Arrange
                final long scheduleRowCount = scheduleRepository.count();
                final long participantRowCount = scheduleParticipantRepository.count();

                String candidateEmail = "cand@gmail.com";

                String searchBody = """
                                {
                                  "data": {
                                    "result": [
                                      {"id": 1, "email": "cand@gmail.com"},
                                      {"id": 2, "email": "other@gmail.com"}
                                    ]
                                  }
                                }
                                """;

                String detailBody = """
                                {
                                  "data": {
                                    "result": [
                                      {"updatedBy": null, "createdBy": 77}
                                    ]
                                  }
                                }
                                """;

                when(restTemplate.exchange(
                                startsWith("http://candidate-service/api/v1/candidate-service/candidates?keyword="),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenReturn(ResponseEntity.ok(searchBody));

                when(restTemplate.exchange(
                                eq("http://candidate-service/api/v1/candidate-service/candidates?candidateId=1&page=1&limit=1&sortBy=id&sortOrder=desc"),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenReturn(ResponseEntity.ok(detailBody));

                ObjectMapper jsonParser = new ObjectMapper();
                when(objectMapper.readTree(eq(searchBody))).thenReturn(jsonParser.readTree(searchBody));
                when(objectMapper.readTree(eq(detailBody))).thenReturn(jsonParser.readTree(detailBody));

                // Act
                Long employeeId = candidateService.getEmployeeIdFromCandidateEmail(candidateEmail);

                // Assert
                assertNotNull(employeeId);
                assertEquals(77L, employeeId);

                verify(restTemplate, times(2)).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                                eq(String.class));
                verify(objectMapper, times(2)).readTree(anyString());

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCount, participantRowCount);
        }

        @Test
        @DisplayName("SCH-CAND-TC-008: getEmployeeIdFromCandidateEmail - trả null khi không tìm được candidate phù hợp")
        void getEmployeeIdFromCandidateEmail_whenNoEmailMatch_skipsDetailCallAndReturnsNull() throws Exception {
                // Test Case ID: SCH-CAND-TC-008

                // Arrange
                final long scheduleRowCount = scheduleRepository.count();
                final long participantRowCount = scheduleParticipantRepository.count();

                String searchEmail = "missing@gmail.com";

                String searchBody = """
                                {
                                  "data": {
                                    "result": [
                                      {"id": 1, "email": "cand@gmail.com"},
                                      {"id": 2, "email": "other@gmail.com"}
                                    ]
                                  }
                                }
                                """;

                when(restTemplate.exchange(
                                startsWith("http://candidate-service/api/v1/candidate-service/candidates?keyword="),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenReturn(ResponseEntity.ok(searchBody));

                ObjectMapper jsonParser = new ObjectMapper();
                when(objectMapper.readTree(eq(searchBody))).thenReturn(jsonParser.readTree(searchBody));

                // Act
                Long employeeId = candidateService.getEmployeeIdFromCandidateEmail(searchEmail);

                // Assert
                assertNull(employeeId);

                verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                                eq(String.class));
                verify(restTemplate, never()).exchange(contains("candidateId="), eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class));

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCount, participantRowCount);
        }

        @Test
        @DisplayName("SCH-CAND-TC-009: getCandidateName - HttpServerErrorException -> trả error body")
        void getCandidateName_whenHttpServerError_returnsParsedErrorBody() throws Exception {
                // Test Case ID: SCH-CAND-TC-009

                // Arrange
                final long scheduleRowCount = scheduleRepository.count();
                final long participantRowCount = scheduleParticipantRepository.count();

                Long candidateId = 5L;
                String errorResponseJson = "{\"statusCode\":500,\"error\":\"Internal Server Error\",\"message\":\"Down\"}";

                HttpServerErrorException serverErrorException = HttpServerErrorException.create(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Internal Server Error",
                                HttpHeaders.EMPTY,
                                errorResponseJson.getBytes(StandardCharsets.UTF_8),
                                StandardCharsets.UTF_8);

                JsonNode parsedErrorNode = mock(JsonNode.class);

                when(restTemplate.exchange(
                                eq("http://candidate-service/api/v1/candidate-service/candidates/5"),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenThrow(serverErrorException);

                when(objectMapper.readTree(eq(errorResponseJson))).thenReturn(parsedErrorNode);

                // Act
                ResponseEntity<JsonNode> response = candidateService.getCandidateName(candidateId, null);

                // Assert
                assertNotNull(response);
                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
                assertSame(parsedErrorNode, response.getBody());

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCount, participantRowCount);
        }

        @Test
        @DisplayName("SCH-CAND-TC-010: getCandidateName - HTTP error nhưng parse error body fail -> fallback ObjectNode")
        void getCandidateName_whenHttpErrorBodyNotJson_returnsFallbackErrorNode() throws Exception {
                // Test Case ID: SCH-CAND-TC-010

                // Arrange
                final long scheduleRowCount = scheduleRepository.count();
                final long participantRowCount = scheduleParticipantRepository.count();

                Long candidateId = 9L;
                String invalidErrorPayload = "not-json";

                HttpClientErrorException badRequestException = HttpClientErrorException.create(
                                HttpStatus.BAD_REQUEST,
                                "Bad Request",
                                HttpHeaders.EMPTY,
                                invalidErrorPayload.getBytes(StandardCharsets.UTF_8),
                                StandardCharsets.UTF_8);

                when(restTemplate.exchange(
                                eq("http://candidate-service/api/v1/candidate-service/candidates/9"),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenThrow(badRequestException);

                when(objectMapper.readTree(eq(invalidErrorPayload))).thenThrow(new RuntimeException("parse fail"));
                ObjectNode fallbackErrorPayload = new ObjectMapper().createObjectNode();
                when(objectMapper.createObjectNode()).thenReturn(fallbackErrorPayload);

                // Act
                ResponseEntity<JsonNode> response = candidateService.getCandidateName(candidateId, null);

                // Assert
                assertNotNull(response);
                assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
                assertNotNull(response.getBody());
                assertEquals(400, response.getBody().path("statusCode").asInt());
                assertEquals("Bad Request", response.getBody().path("error").asText());
                assertTrue(response.getBody().path("message").asText().contains("Không thể parse"));

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCount, participantRowCount);
        }

        @Test
        @DisplayName("SCH-CAND-TC-011: getEmployeeIdFromCandidateEmail - exception -> trả null")
        void getEmployeeIdFromCandidateEmail_whenRestTemplateThrows_returnsNull() {
                // Test Case ID: SCH-CAND-TC-011

                // Arrange
                final long scheduleRowCount = scheduleRepository.count();
                final long participantRowCount = scheduleParticipantRepository.count();

                when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                                .thenThrow(new ResourceAccessException("boom"));

                // Act
                Long employeeId = candidateService.getEmployeeIdFromCandidateEmail("x@gmail.com");

                // Assert
                assertNull(employeeId);

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCount, participantRowCount);
        }

        @Test
        @DisplayName("SCH-CAND-TC-012: getCandidateNames - data không phải array hoặc null -> trả object rỗng")
        void getCandidateNames_whenDataNodeNotArray_returnsEmptyIdToNameMap() throws Exception {
                // Test Case ID: SCH-CAND-TC-012

                // Arrange
                final long scheduleRowCount = scheduleRepository.count();
                final long participantRowCount = scheduleParticipantRepository.count();

                List<Long> candidateIds = List.of(1L);
                String responseWithNullData = "{\"data\":null}";

                JsonNode jsonRoot = mock(JsonNode.class);
                JsonNode dataSection = mock(JsonNode.class);
                ObjectNode emptyIdToNameMap = new ObjectMapper().createObjectNode();

                when(restTemplate.exchange(
                                eq("http://candidate-service/api/v1/candidate-service/candidates?ids=1"),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenReturn(ResponseEntity.ok(responseWithNullData));

                when(objectMapper.readTree(eq(responseWithNullData))).thenReturn(jsonRoot);
                when(jsonRoot.get("data")).thenReturn(dataSection);

                when(objectMapper.createObjectNode()).thenReturn(emptyIdToNameMap);
                when(dataSection.isArray()).thenReturn(false);

                // Act
                ResponseEntity<JsonNode> response = candidateService.getCandidateNames(candidateIds, null);

                // Assert
                assertNotNull(response);
                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertNotNull(response.getBody());
                assertEquals(0, response.getBody().size());

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCount, participantRowCount);
        }

        @Test
        @DisplayName("SCH-CAND-TC-013: getCandidateName - candidate-service trả body null -> trả 500 theo implement")
        void getCandidateName_whenResponseBodyNull_returnsInternalServerError() {
                // Test Case ID: SCH-CAND-TC-013

                // Arrange
                final long scheduleRowCount = scheduleRepository.count();
                final long participantRowCount = scheduleParticipantRepository.count();

                when(restTemplate.exchange(
                                eq("http://candidate-service/api/v1/candidate-service/candidates/1"),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenReturn(ResponseEntity.ok(null));

                ObjectNode errorPayload = new ObjectMapper().createObjectNode();
                when(objectMapper.createObjectNode()).thenReturn(errorPayload);

                // Act
                ResponseEntity<JsonNode> response = candidateService.getCandidateName(1L, "t");

                // Assert
                assertNotNull(response);
                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
                assertNotNull(response.getBody());
                assertEquals(500, response.getBody().path("statusCode").asInt());

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCount, participantRowCount);
        }

        @Test
        @DisplayName("SCH-CAND-TC-014: getCandidateName - JSON thiếu name -> trả 200 với body null theo implement")
        void getCandidateName_whenNameFieldMissing_returnsOkWithNullBody() throws Exception {
                // Test Case ID: SCH-CAND-TC-014

                // Arrange
                final long scheduleRowCount = scheduleRepository.count();
                final long participantRowCount = scheduleParticipantRepository.count();

                String responseJson = "{\"data\":{}}";
                when(restTemplate.exchange(
                                eq("http://candidate-service/api/v1/candidate-service/candidates/1"),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenReturn(ResponseEntity.ok(responseJson));

                ObjectMapper jsonParser = new ObjectMapper();
                when(objectMapper.readTree(eq(responseJson))).thenReturn(jsonParser.readTree(responseJson));

                // Act
                ResponseEntity<JsonNode> response = candidateService.getCandidateName(1L, null);

                // Assert
                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertNull(response.getBody());

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCount, participantRowCount);
        }

        @Test
        @DisplayName("SCH-CAND-TC-015: getCandidateNames - candidateIds rỗng vẫn gọi service và trả map rỗng nếu data array rỗng")
        void getCandidateNames_whenIdListEmpty_returnsEmptyMapFromEmptyArray() throws Exception {
                // Test Case ID: SCH-CAND-TC-015

                // Arrange
                final long scheduleRowCount = scheduleRepository.count();
                final long participantRowCount = scheduleParticipantRepository.count();

                List<Long> emptyCandidateIdList = List.of();
                String responseJson = "{\"data\":[]}";
                when(restTemplate.exchange(
                                eq("http://candidate-service/api/v1/candidate-service/candidates?ids="),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenReturn(ResponseEntity.ok(responseJson));

                ObjectMapper jsonParser = new ObjectMapper();
                when(objectMapper.readTree(eq(responseJson))).thenReturn(jsonParser.readTree(responseJson));
                ObjectNode emptyMap = jsonParser.createObjectNode();
                when(objectMapper.createObjectNode()).thenReturn(emptyMap);

                // Act
                ResponseEntity<JsonNode> response = candidateService.getCandidateNames(emptyCandidateIdList, "");

                // Assert
                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertNotNull(response.getBody());
                assertEquals(0, response.getBody().size());

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCount, participantRowCount);
        }

        @Test
        @DisplayName("SCH-CAND-TC-016: getCandidateNames - candidate-service trả body null -> trả 500 theo implement")
        void getCandidateNames_whenResponseBodyNull_returnsInternalServerError() {
                // Test Case ID: SCH-CAND-TC-016

                // Arrange
                final long scheduleRowCount = scheduleRepository.count();
                final long participantRowCount = scheduleParticipantRepository.count();

                when(restTemplate.exchange(
                                eq("http://candidate-service/api/v1/candidate-service/candidates?ids=1,2"),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenReturn(ResponseEntity.ok(null));

                ObjectNode errorPayload = new ObjectMapper().createObjectNode();
                when(objectMapper.createObjectNode()).thenReturn(errorPayload);

                // Act
                ResponseEntity<JsonNode> response = candidateService.getCandidateNames(List.of(1L, 2L), null);

                // Assert
                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
                assertEquals(500, response.getBody().path("statusCode").asInt());

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCount, participantRowCount);
        }

        @Test
        @DisplayName("SCH-CAND-TC-017: getCandidateNames - HTTP error parse fail -> fallback ObjectNode")
        void getCandidateNames_whenHttpErrorBodyNotJson_returnsFallbackErrorNode() throws Exception {
                // Test Case ID: SCH-CAND-TC-017

                // Arrange
                final long scheduleRowCount = scheduleRepository.count();
                final long participantRowCount = scheduleParticipantRepository.count();

                List<Long> candidateIds = List.of(1L, 2L);
                String invalidErrorPayload = "not-json";

                HttpClientErrorException badRequestException = HttpClientErrorException.create(
                                HttpStatus.BAD_REQUEST,
                                "Bad Request",
                                HttpHeaders.EMPTY,
                                invalidErrorPayload.getBytes(StandardCharsets.UTF_8),
                                StandardCharsets.UTF_8);

                when(restTemplate.exchange(
                                eq("http://candidate-service/api/v1/candidate-service/candidates?ids=1,2"),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenThrow(badRequestException);

                when(objectMapper.readTree(eq(invalidErrorPayload))).thenThrow(new RuntimeException("parse fail"));
                ObjectNode fallbackErrorPayload = new ObjectMapper().createObjectNode();
                when(objectMapper.createObjectNode()).thenReturn(fallbackErrorPayload);

                // Act
                ResponseEntity<JsonNode> response = candidateService.getCandidateNames(candidateIds, null);

                // Assert
                assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
                assertNotNull(response.getBody());
                assertEquals(400, response.getBody().path("statusCode").asInt());
                assertTrue(response.getBody().path("message").asText().contains("Không thể parse"));

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCount, participantRowCount);
        }

        @Test
        @DisplayName("SCH-CAND-TC-018: getCandidateNames - generic exception -> trả 500 theo implement")
        void getCandidateNames_whenConnectionFails_returnsInternalServerErrorPayload() {
                // Test Case ID: SCH-CAND-TC-018

                // Arrange
                final long scheduleRowCount = scheduleRepository.count();
                final long participantRowCount = scheduleParticipantRepository.count();

                when(restTemplate.exchange(
                                eq("http://candidate-service/api/v1/candidate-service/candidates?ids=1"),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenThrow(new ResourceAccessException("Connection refused"));

                ObjectNode fallbackErrorPayload = new ObjectMapper().createObjectNode();
                when(objectMapper.createObjectNode()).thenReturn(fallbackErrorPayload);

                // Act
                ResponseEntity<JsonNode> response = candidateService.getCandidateNames(List.of(1L), "t");

                // Assert
                assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
                assertEquals(500, response.getBody().path("statusCode").asInt());

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCount, participantRowCount);
        }

        @Test
        @DisplayName("SCH-CAND-TC-019: getEmployeeIdFromCandidateEmail - search result rỗng -> null")
        void getEmployeeIdFromCandidateEmail_whenSearchResultEmpty_returnsNull() throws Exception {
                // Test Case ID: SCH-CAND-TC-019

                // Arrange
                final long scheduleRowCount = scheduleRepository.count();
                final long participantRowCount = scheduleParticipantRepository.count();

                String searchEmail = "missing@gmail.com";
                String emptySearchJson = "{\"data\":{\"result\":[]}}";

                when(restTemplate.exchange(
                                startsWith("http://candidate-service/api/v1/candidate-service/candidates?keyword="),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenReturn(ResponseEntity.ok(emptySearchJson));

                ObjectMapper jsonParser = new ObjectMapper();
                when(objectMapper.readTree(eq(emptySearchJson))).thenReturn(jsonParser.readTree(emptySearchJson));

                // Act
                Long employeeId = candidateService.getEmployeeIdFromCandidateEmail(searchEmail);

                // Assert
                assertNull(employeeId);
                verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCount, participantRowCount);
        }

        @Test
        @DisplayName("SCH-CAND-TC-020: getEmployeeIdFromCandidateEmail - candidate có updatedBy và createdBy null -> null")
        void getEmployeeIdFromCandidateEmail_whenUpdatedByAndCreatedByNull_returnsNull() throws Exception {
                // Test Case ID: SCH-CAND-TC-020

                // Arrange
                final long scheduleRowCount = scheduleRepository.count();
                final long participantRowCount = scheduleParticipantRepository.count();

                String candidateEmail = "cand@gmail.com";

                String searchResponseJson = """
                                {"data":{"result":[{"id":1,"email":"cand@gmail.com"}]}}
                                """;
                String detailResponseJson = """
                                {"data":{"result":[{"updatedBy":null,"createdBy":null}]}}
                                """;

                when(restTemplate.exchange(
                                startsWith("http://candidate-service/api/v1/candidate-service/candidates?keyword="),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenReturn(ResponseEntity.ok(searchResponseJson));

                when(restTemplate.exchange(
                                eq("http://candidate-service/api/v1/candidate-service/candidates?candidateId=1&page=1&limit=1&sortBy=id&sortOrder=desc"),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenReturn(ResponseEntity.ok(detailResponseJson));

                ObjectMapper jsonParser = new ObjectMapper();
                when(objectMapper.readTree(eq(searchResponseJson))).thenReturn(jsonParser.readTree(searchResponseJson));
                when(objectMapper.readTree(eq(detailResponseJson))).thenReturn(jsonParser.readTree(detailResponseJson));

                // Act
                Long employeeId = candidateService.getEmployeeIdFromCandidateEmail(candidateEmail);

                // Assert
                assertNull(employeeId);

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCount, participantRowCount);
        }

        @Test
        @DisplayName("SCH-CAND-TC-021: getEmployeeIdFromCandidateEmail - detail result empty -> null")
        void getEmployeeIdFromCandidateEmail_whenDetailResultEmpty_returnsNull() throws Exception {
                // Test Case ID: SCH-CAND-TC-021

                // Arrange
                final long scheduleRowCount = scheduleRepository.count();
                final long participantRowCount = scheduleParticipantRepository.count();

                String candidateEmail = "cand@gmail.com";

                String searchResponseJson = """
                                {"data":{"result":[{"id":1,"email":"cand@gmail.com"}]}}
                                """;
                String detailResponseJson = """
                                {"data":{"result":[]}}
                                """;

                when(restTemplate.exchange(
                                startsWith("http://candidate-service/api/v1/candidate-service/candidates?keyword="),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenReturn(ResponseEntity.ok(searchResponseJson));

                when(restTemplate.exchange(
                                eq("http://candidate-service/api/v1/candidate-service/candidates?candidateId=1&page=1&limit=1&sortBy=id&sortOrder=desc"),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenReturn(ResponseEntity.ok(detailResponseJson));

                ObjectMapper jsonParser = new ObjectMapper();
                when(objectMapper.readTree(eq(searchResponseJson))).thenReturn(jsonParser.readTree(searchResponseJson));
                when(objectMapper.readTree(eq(detailResponseJson))).thenReturn(jsonParser.readTree(detailResponseJson));

                // Act
                Long employeeId = candidateService.getEmployeeIdFromCandidateEmail(candidateEmail);

                // Assert
                assertNull(employeeId);

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCount, participantRowCount);
        }

        @Test
        @DisplayName("SCH-CAND-TC-022: getCandidateNames - root.get('data') null -> trả object rỗng")
        void getCandidateNames_whenDataKeyMissing_returnsEmptyIdToNameMap() throws Exception {
                // Test Case ID: SCH-CAND-TC-022

                // Arrange
                final long scheduleRowCount = scheduleRepository.count();
                final long participantRowCount = scheduleParticipantRepository.count();

                List<Long> candidateIds = List.of(1L);
                String responseWithoutDataKey = "{\"x\":1}";

                JsonNode jsonRoot = mock(JsonNode.class);
                when(restTemplate.exchange(
                                eq("http://candidate-service/api/v1/candidate-service/candidates?ids=1"),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenReturn(ResponseEntity.ok(responseWithoutDataKey));

                when(objectMapper.readTree(eq(responseWithoutDataKey))).thenReturn(jsonRoot);
                when(jsonRoot.get("data")).thenReturn(null);

                ObjectNode emptyIdToNameMap = new ObjectMapper().createObjectNode();
                when(objectMapper.createObjectNode()).thenReturn(emptyIdToNameMap);

                // Act
                ResponseEntity<JsonNode> response = candidateService.getCandidateNames(candidateIds, null);

                // Assert
                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertNotNull(response.getBody());
                assertEquals(0, response.getBody().size());

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCount, participantRowCount);
        }

        @Test
        @DisplayName("SCH-CAND-TC-023: getCandidateNames - candidate thiếu id hoặc name -> không add vào map")
        void getCandidateNames_whenCandidateNodeIncomplete_returnsEmptyMap() throws Exception {
                // Test Case ID: SCH-CAND-TC-023

                // Arrange
                final long scheduleRowCount = scheduleRepository.count();
                final long participantRowCount = scheduleParticipantRepository.count();

                List<Long> candidateIds = List.of(1L, 2L);
                String partialCandidatesJson = "{\"data\":[{\"id\":1},{\"name\":\"B\"}]}";

                ObjectMapper jsonParser = new ObjectMapper();
                when(restTemplate.exchange(
                                eq("http://candidate-service/api/v1/candidate-service/candidates?ids=1,2"),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenReturn(ResponseEntity.ok(partialCandidatesJson));

                when(objectMapper.readTree(eq(partialCandidatesJson))).thenReturn(jsonParser.readTree(partialCandidatesJson));
                ObjectNode emptyIdToNameMap = jsonParser.createObjectNode();
                when(objectMapper.createObjectNode()).thenReturn(emptyIdToNameMap);

                // Act
                ResponseEntity<JsonNode> response = candidateService.getCandidateNames(candidateIds, null);

                // Assert
                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertNotNull(response.getBody());
                assertEquals(0, response.getBody().size());

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCount, participantRowCount);
        }

        @Test
        @DisplayName("SCH-CAND-TC-024: getEmployeeIdFromCandidateEmail - candidateData không phải array -> null")
        void getEmployeeIdFromCandidateEmail_whenSearchResultNotArray_returnsNull() throws Exception {
                // Test Case ID: SCH-CAND-TC-024

                // Arrange
                final long scheduleRowCount = scheduleRepository.count();
                final long participantRowCount = scheduleParticipantRepository.count();

                String candidateEmail = "cand@gmail.com";
                String searchResponseJson = "{\"data\":{\"result\":{\"id\":1}}}";

                when(restTemplate.exchange(
                                startsWith("http://candidate-service/api/v1/candidate-service/candidates?keyword="),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenReturn(ResponseEntity.ok(searchResponseJson));

                ObjectMapper jsonParser = new ObjectMapper();
                when(objectMapper.readTree(eq(searchResponseJson))).thenReturn(jsonParser.readTree(searchResponseJson));

                // Act
                Long employeeId = candidateService.getEmployeeIdFromCandidateEmail(candidateEmail);

                // Assert
                assertNull(employeeId);
                verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCount, participantRowCount);
        }

        @Test
        @DisplayName("SCH-CAND-TC-025: getEmployeeIdFromCandidateEmail - candidate thiếu email field -> null")
        void getEmployeeIdFromCandidateEmail_whenSearchRowMissingEmail_returnsNull() throws Exception {
                // Test Case ID: SCH-CAND-TC-025

                // Arrange
                final long scheduleRowCount = scheduleRepository.count();
                final long participantRowCount = scheduleParticipantRepository.count();

                String candidateEmail = "cand@gmail.com";
                String searchResponseJson = "{\"data\":{\"result\":[{\"id\":1}]}}";

                when(restTemplate.exchange(
                                startsWith("http://candidate-service/api/v1/candidate-service/candidates?keyword="),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenReturn(ResponseEntity.ok(searchResponseJson));

                ObjectMapper jsonParser = new ObjectMapper();
                when(objectMapper.readTree(eq(searchResponseJson))).thenReturn(jsonParser.readTree(searchResponseJson));

                // Act
                Long employeeId = candidateService.getEmployeeIdFromCandidateEmail(candidateEmail);

                // Assert
                assertNull(employeeId);
                verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCount, participantRowCount);
        }

        @Test
        @DisplayName("SCH-CAND-TC-026: getEmployeeIdFromCandidateEmail - detail result không phải array -> null")
        void getEmployeeIdFromCandidateEmail_whenDetailResultNotArray_returnsNull() throws Exception {
                // Test Case ID: SCH-CAND-TC-026

                // Arrange
                final long scheduleRowCount = scheduleRepository.count();
                final long participantRowCount = scheduleParticipantRepository.count();

                String candidateEmail = "cand@gmail.com";
                String searchResponseJson = "{\"data\":{\"result\":[{\"id\":1,\"email\":\"cand@gmail.com\"}]}}";
                String detailResponseJson = "{\"data\":{\"result\":{\"updatedBy\":99}}}";

                when(restTemplate.exchange(
                                startsWith("http://candidate-service/api/v1/candidate-service/candidates?keyword="),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenReturn(ResponseEntity.ok(searchResponseJson));

                when(restTemplate.exchange(
                                eq("http://candidate-service/api/v1/candidate-service/candidates?candidateId=1&page=1&limit=1&sortBy=id&sortOrder=desc"),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenReturn(ResponseEntity.ok(detailResponseJson));

                ObjectMapper jsonParser = new ObjectMapper();
                when(objectMapper.readTree(eq(searchResponseJson))).thenReturn(jsonParser.readTree(searchResponseJson));
                when(objectMapper.readTree(eq(detailResponseJson))).thenReturn(jsonParser.readTree(detailResponseJson));

                // Act
                Long employeeId = candidateService.getEmployeeIdFromCandidateEmail(candidateEmail);

                // Assert
                assertNull(employeeId);

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCount, participantRowCount);
        }

        @Test
        @DisplayName("SCH-CAND-TC-027: getEmployeeIdFromCandidateEmail - thiếu updatedBy/createdBy fields -> null")
        void getEmployeeIdFromCandidateEmail_whenOwnerFieldsAbsent_returnsNull() throws Exception {
                // Test Case ID: SCH-CAND-TC-027

                // Arrange
                final long scheduleRowCount = scheduleRepository.count();
                final long participantRowCount = scheduleParticipantRepository.count();

                String candidateEmail = "cand@gmail.com";
                String searchResponseJson = "{\"data\":{\"result\":[{\"id\":1,\"email\":\"cand@gmail.com\"}]}}";
                String detailResponseJson = "{\"data\":{\"result\":[{\"id\":1}]}}";

                when(restTemplate.exchange(
                                startsWith("http://candidate-service/api/v1/candidate-service/candidates?keyword="),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenReturn(ResponseEntity.ok(searchResponseJson));

                when(restTemplate.exchange(
                                eq("http://candidate-service/api/v1/candidate-service/candidates?candidateId=1&page=1&limit=1&sortBy=id&sortOrder=desc"),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenReturn(ResponseEntity.ok(detailResponseJson));

                ObjectMapper jsonParser = new ObjectMapper();
                when(objectMapper.readTree(eq(searchResponseJson))).thenReturn(jsonParser.readTree(searchResponseJson));
                when(objectMapper.readTree(eq(detailResponseJson))).thenReturn(jsonParser.readTree(detailResponseJson));

                // Act
                Long employeeId = candidateService.getEmployeeIdFromCandidateEmail(candidateEmail);

                // Assert
                assertNull(employeeId);

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCount, participantRowCount);
        }

        @Test
        @DisplayName("SCH-CAND-TC-028: getEmployeeIdFromCandidateEmail - candidateData null (mock path trả null) -> null")
        void getEmployeeIdFromCandidateEmail_whenMockedSearchResultPathNull_returnsNull() throws Exception {
                // Test Case ID: SCH-CAND-TC-028

                // Arrange
                final long scheduleRowCount = scheduleRepository.count();
                final long participantRowCount = scheduleParticipantRepository.count();

                String candidateEmail = "cand@gmail.com";
                String searchResponseJson = "{\"dummy\":true}";

                when(restTemplate.exchange(
                                startsWith("http://candidate-service/api/v1/candidate-service/candidates?keyword="),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenReturn(ResponseEntity.ok(searchResponseJson));

                JsonNode jsonRoot = mock(JsonNode.class);
                JsonNode dataSection = mock(JsonNode.class);
                when(objectMapper.readTree(eq(searchResponseJson))).thenReturn(jsonRoot);
                when(jsonRoot.path("data")).thenReturn(dataSection);
                when(dataSection.path("result")).thenReturn(null);

                // Act
                Long employeeId = candidateService.getEmployeeIdFromCandidateEmail(candidateEmail);

                // Assert
                assertNull(employeeId);

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCount, participantRowCount);
        }

        @Test
        @DisplayName("SCH-CAND-TC-029: getEmployeeIdFromCandidateEmail - candidateDetailData null (mock path trả null) -> null")
        void getEmployeeIdFromCandidateEmail_whenMockedDetailResultPathNull_returnsNull() throws Exception {
                // Test Case ID: SCH-CAND-TC-029

                // Arrange
                final long scheduleRowCount = scheduleRepository.count();
                final long participantRowCount = scheduleParticipantRepository.count();

                String candidateEmail = "cand@gmail.com";
                String searchResponseJson = "{\"data\":{\"result\":[{\"id\":1,\"email\":\"cand@gmail.com\"}]}}";
                String detailResponseJson = "{\"dummy\":true}";

                when(restTemplate.exchange(
                                startsWith("http://candidate-service/api/v1/candidate-service/candidates?keyword="),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenReturn(ResponseEntity.ok(searchResponseJson));

                when(restTemplate.exchange(
                                eq("http://candidate-service/api/v1/candidate-service/candidates?candidateId=1&page=1&limit=1&sortBy=id&sortOrder=desc"),
                                eq(HttpMethod.GET),
                                any(HttpEntity.class),
                                eq(String.class)))
                                .thenReturn(ResponseEntity.ok(detailResponseJson));

                ObjectMapper jsonParser = new ObjectMapper();
                when(objectMapper.readTree(eq(searchResponseJson))).thenReturn(jsonParser.readTree(searchResponseJson));

                JsonNode detailRoot = mock(JsonNode.class);
                JsonNode detailDataSection = mock(JsonNode.class);
                when(objectMapper.readTree(eq(detailResponseJson))).thenReturn(detailRoot);
                when(detailRoot.path("data")).thenReturn(detailDataSection);
                when(detailDataSection.path("result")).thenReturn(null);

                // Act
                Long employeeId = candidateService.getEmployeeIdFromCandidateEmail(candidateEmail);

                // Assert
                assertNull(employeeId);

                // CheckDB
                assertDatabaseUnchanged(scheduleRowCount, participantRowCount);
        }
}