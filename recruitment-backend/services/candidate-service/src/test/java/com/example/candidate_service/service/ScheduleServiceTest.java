package com.example.candidate_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.example.candidate_service.dto.Response;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@DisplayName("ScheduleService Unit Test")
class ScheduleServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ScheduleService scheduleService;

    private final ObjectMapper realMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduleService, "scheduleServiceUrl", "http://localhost:8085");
    }

    @Test
    @DisplayName("SCH-TC-001: getUpcomingSchedulesForCandidate - unwrap đúng data schedules")
    void testGetUpcomingSchedulesForCandidate_SCH_TC_001() {
        // Testcase ID: SCH-TC-001
        // Objective: Xác nhận unwrap đúng data schedules

        // arrange
        Long candidateId = 1L;
        String token = "valid-jwt";

        ArrayNode schedulesData = realMapper.createArrayNode();
        schedulesData.addObject().put("id", 1001);

        Response<JsonNode> body = new Response<>();
        body.setData(schedulesData);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(body));

        // act
        ResponseEntity<JsonNode> result = scheduleService.getUpcomingSchedulesForCandidate(candidateId, token);

        // assert
        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(schedulesData, result.getBody());

        verify(restTemplate, times(1)).exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class));
    }

    @Test
    @DisplayName("SCH-TC-002: getUpcomingSchedulesForCandidate - fallback mảng rỗng khi không có data")
    void testGetUpcomingSchedulesForCandidate_EmptyFallback_SCH_TC_002() {
        // Testcase ID: SCH-TC-002
        // Objective: Xác nhận fallback mảng rỗng khi không có data

        // arrange
        Long candidateId = 1L;

        Response<JsonNode> body = new Response<>();
        body.setData(null);

        ArrayNode emptyArray = realMapper.createArrayNode();

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(body));

        when(objectMapper.createArrayNode()).thenReturn(emptyArray);

        // act
        ResponseEntity<JsonNode> result = scheduleService.getUpcomingSchedulesForCandidate(candidateId, "");

        // assert
        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(emptyArray, result.getBody());

        verify(restTemplate, times(1)).exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class));
        verify(objectMapper, times(1)).createArrayNode();
    }

    @Test
    @DisplayName("SCH-TC-003: getCandidateIdsByInterviewer - lấy đúng danh sách candidateIds")
    void testGetCandidateIdsByInterviewer_SCH_TC_003() {
        // Testcase ID: SCH-TC-003
        // Objective: Xác nhận lấy đúng danh sách candidateIds

        // arrange
        Long employeeId = 10L;

        Response<List<Long>> body = new Response<>();
        body.setData(List.of(1L, 2L));

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        // act
        List<Long> result = scheduleService.getCandidateIdsByInterviewer(employeeId, "valid-jwt");

        // assert
        assertNotNull(result);
        assertEquals(List.of(1L, 2L), result);

        verify(restTemplate, times(1)).exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class));
    }

    @Test
    @DisplayName("SCH-TC-004: getCandidateIdsByInterviewer - fallback list rỗng khi restTemplate lỗi")
    void testGetCandidateIdsByInterviewer_ExceptionFallback_SCH_TC_004() {
        // Testcase ID: SCH-TC-004
        // Objective: Xác nhận fallback list rỗng khi lỗi service

        // arrange
        Long employeeId = 10L;

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("service down"));

        // act
        List<Long> result = scheduleService.getCandidateIdsByInterviewer(employeeId, "valid-jwt");

        // assert
        assertNotNull(result);
        assertEquals(List.of(), result);

        verify(restTemplate, times(1)).exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class));
    }

    // ================================================================
    // NHÓM 2: Các nhánh còn thiếu — token=null, body=null, exception
    // ================================================================

    /**
     * Test Case ID: SCH-TC-005
     * Nhánh FALSE của `if (token != null && !token.isEmpty())` trong getUpcomingSchedulesForCandidate():
     * token = null → KHÔNG gọi headers.setBearerAuth().
     *
     * Bug bị bắt: NPE khi gọi setBearerAuth(null), hoặc luôn gắn header dù token null.
     */
    @Test
    @DisplayName("SCH-TC-005: getUpcomingSchedulesForCandidate - token=null → không gắn Bearer header, không NPE")
    void testGetUpcomingSchedulesForCandidate_NullToken_SCH_TC_005() {
        // Arrange: token = null → nhánh FALSE của if (token != null && !token.isEmpty())
        Long candidateId = 2L;

        ArrayNode schedulesData = realMapper.createArrayNode();
        Response<JsonNode> body = new Response<>();
        body.setData(schedulesData);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(body));

        // Act
        ResponseEntity<JsonNode> result = scheduleService.getUpcomingSchedulesForCandidate(candidateId, null);

        // Assert: không crash, vẫn trả về OK
        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());

        // restTemplate vẫn được gọi (request vẫn được thực hiện, chỉ không có Bearer)
        verify(restTemplate, times(1)).exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class));
    }

    /**
     * Test Case ID: SCH-TC-006
     * Nhánh FALSE của `if (response.getBody() != null && response.getBody().getData() != null)`:
     * response.getBody() = null → fallback trả về mảng rỗng.
     *
     * Bug bị bắt: NPE khi gọi response.getBody().getData() mà không kiểm tra body null.
     */
    @Test
    @DisplayName("SCH-TC-006: getUpcomingSchedulesForCandidate - body=null → fallback mảng rỗng, không NPE")
    void testGetUpcomingSchedulesForCandidate_NullBody_SCH_TC_006() {
        // Arrange: response body = null (server trả về không có body)
        Long candidateId = 3L;

        ArrayNode emptyArray = realMapper.createArrayNode();
        when(objectMapper.createArrayNode()).thenReturn(emptyArray);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(ResponseEntity.ok(null)); // body = null

        // Act
        ResponseEntity<JsonNode> result = scheduleService.getUpcomingSchedulesForCandidate(candidateId, "token");

        // Assert: không NPE, trả về mảng rỗng
        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(emptyArray, result.getBody());
        verify(objectMapper, times(1)).createArrayNode();
    }

    /**
     * Test Case ID: SCH-TC-007
     * Nhánh catch(Exception e) trong getUpcomingSchedulesForCandidate():
     * restTemplate ném exception → trả về ResponseEntity 500 với JSON lỗi.
     *
     * Bug bị bắt:
     *   - Exception bị nuốt, trả về 200 với null body
     *   - Exception bị re-throw → caller crash
     *   - JSON lỗi thiếu field "statusCode"/"error"/"message"
     */
    @Test
    @DisplayName("SCH-TC-007: getUpcomingSchedulesForCandidate - exception → trả về HTTP 500 với JSON lỗi")
    void testGetUpcomingSchedulesForCandidate_ExceptionReturns500_SCH_TC_007() {
        // Arrange: restTemplate ném exception
        Long candidateId = 4L;
        String errorMsg = "Connection refused";

        ObjectMapper realObjectMapper = new ObjectMapper();
        ReflectionTestUtils.setField(scheduleService, "objectMapper", realObjectMapper);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException(errorMsg));

        // Act
        ResponseEntity<JsonNode> result = scheduleService.getUpcomingSchedulesForCandidate(candidateId, "token");

        // Assert: HTTP 500
        assertNotNull(result);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());

        // Body phải có các field lỗi
        JsonNode body = result.getBody();
        assertNotNull(body, "BUG: Body null khi exception xảy ra");
        assertEquals(500, body.get("statusCode").asInt(),
                "BUG: statusCode phải là 500");
        assertNotNull(body.get("error"),
                "BUG: Thiếu field 'error' trong JSON lỗi");
        assertTrue(body.get("message").asText().contains(errorMsg),
                "BUG: Message không chứa thông tin lỗi gốc");
    }

    /**
     * Test Case ID: SCH-TC-008
     * Nhánh FALSE của `if (token != null && !token.isEmpty())` trong getCandidateIdsByInterviewer():
     * token = null → không gắn Bearer header, không NPE.
     *
     * Bug bị bắt: NPE khi token=null được truyền vào setBearerAuth(null).
     */
    @Test
    @DisplayName("SCH-TC-008: getCandidateIdsByInterviewer - token=null → không gắn Bearer, không NPE")
    void testGetCandidateIdsByInterviewer_NullToken_SCH_TC_008() {
        // Arrange
        Long employeeId = 20L;

        Response<List<Long>> body = new Response<>();
        body.setData(List.of(5L, 6L));

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

        // Act: token = null → nhánh FALSE
        List<Long> result = scheduleService.getCandidateIdsByInterviewer(employeeId, null);

        // Assert
        assertNotNull(result);
        assertEquals(List.of(5L, 6L), result);
        verify(restTemplate, times(1)).exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class));
    }

    /**
     * Test Case ID: SCH-TC-009
     * Nhánh FALSE của `if (response.getBody() != null && response.getBody().getData() != null)`
     * trong getCandidateIdsByInterviewer(): body=null → fallback List.of().
     *
     * Bug bị bắt: NPE khi gọi response.getBody().getData() mà body null.
     */
    @Test
    @DisplayName("SCH-TC-009: getCandidateIdsByInterviewer - body=null → fallback List.of(), không NPE")
    void testGetCandidateIdsByInterviewer_NullBody_SCH_TC_009() {
        // Arrange
        Long employeeId = 30L;

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK)); // body = null

        // Act
        List<Long> result = scheduleService.getCandidateIdsByInterviewer(employeeId, "token");

        // Assert: trả về list rỗng, không NPE
        assertNotNull(result);
        assertTrue(result.isEmpty(),
                "BUG: Không fallback List.of() khi body null");
    }
}