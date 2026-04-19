package com.example.schedule_service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CandidateServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private CandidateService candidateService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(candidateService, "candidateServiceUrl", "http://candidate-service");
    }

    @Test
    @DisplayName("SCH-CAND-TC-001: getCandidateName - parse đúng tên candidate từ response JSON")
    void testGetCandidateName_SCH_CAND_TC_001() throws Exception {
        // Testcase ID: SCH-CAND-TC-001
        // Objective: Xác nhận parse đúng tên candidate từ response JSON

        // arrange
        Long candidateId = 1L;
        String token = "valid-token";
        String responseBody = "{\"data\":{\"name\":\"An\"}}";

        JsonNode rootNode = mock(JsonNode.class);
        JsonNode dataNode = mock(JsonNode.class);
        JsonNode nameNode = mock(JsonNode.class);

        when(restTemplate.exchange(
                eq("http://candidate-service/api/v1/candidate-service/candidates/1"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)))
                .thenReturn(ResponseEntity.ok(responseBody));

        when(objectMapper.readTree(eq(responseBody))).thenReturn(rootNode);
        when(rootNode.get("data")).thenReturn(dataNode);
        when(dataNode.get("name")).thenReturn(nameNode);

        // act
        ResponseEntity<JsonNode> result = candidateService.getCandidateName(candidateId, token);

        // assert
        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertSame(nameNode, result.getBody());

        verify(restTemplate, times(1)).exchange(
                eq("http://candidate-service/api/v1/candidate-service/candidates/1"),
                eq(HttpMethod.GET),
                argThat(entity -> {
                    HttpHeaders headers = entity.getHeaders();
                    return ("Bearer " + token).equals(headers.getFirst("Authorization"));
                }),
                eq(String.class));
        verify(objectMapper, times(1)).readTree(eq(responseBody));
    }

    @Test
    @DisplayName("SCH-CAND-TC-002: getCandidateName - trả lại đúng error body khi HTTP client error")
    void testGetCandidateName_HttpClientError_SCH_CAND_TC_002() throws Exception {
        // Testcase ID: SCH-CAND-TC-002
        // Objective: Xác nhận trả lại đúng error body khi HTTP client error

        // arrange
        Long candidateId = 999L;
        String responseBody = "{\"statusCode\":404,\"error\":\"Not Found\",\"message\":\"Candidate not found\",\"data\":null}";
        HttpClientErrorException httpEx = HttpClientErrorException.create(
                HttpStatus.NOT_FOUND,
                "Not Found",
                HttpHeaders.EMPTY,
                responseBody.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);

        JsonNode errorNode = mock(JsonNode.class);

        when(restTemplate.exchange(
                eq("http://candidate-service/api/v1/candidate-service/candidates/999"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)))
                .thenThrow(httpEx);

        when(objectMapper.readTree(eq(responseBody))).thenReturn(errorNode);

        // act
        ResponseEntity<JsonNode> result = candidateService.getCandidateName(candidateId, null);

        // assert
        assertNotNull(result);
        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
        assertNotNull(result.getBody());
        assertSame(errorNode, result.getBody());

        verify(restTemplate, times(1)).exchange(
                eq("http://candidate-service/api/v1/candidate-service/candidates/999"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class));
        verify(objectMapper, times(1)).readTree(eq(responseBody));
    }

    @Test
    @DisplayName("SCH-CAND-TC-003: getCandidateName - trả lỗi 500 khi không kết nối được candidate-service")
    void testGetCandidateName_GenericException_SCH_CAND_TC_003() {
        // Testcase ID: SCH-CAND-TC-003
        // Objective: Xác nhận trả lỗi 500 khi không kết nối được candidate-service

        // arrange
        Long candidateId = 1L;

        when(restTemplate.exchange(
                eq("http://candidate-service/api/v1/candidate-service/candidates/1"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        // IMPORTANT: objectMapper is mocked in this test class.
        // CandidateService builds a fallback ObjectNode in the generic-exception path,
        // so we must stub createObjectNode() to avoid returning null.
        ObjectNode fallback = new ObjectMapper().createObjectNode();
        when(objectMapper.createObjectNode()).thenReturn(fallback);

        // act
        ResponseEntity<JsonNode> result = candidateService.getCandidateName(candidateId, "");

        // assert
        assertNotNull(result);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(500, result.getBody().path("statusCode").asInt());
        assertEquals("Internal Server Error", result.getBody().path("error").asText());
        assertTrue(result.getBody().path("message").asText().contains("Không thể kết nối tới User Service"));

        verify(restTemplate, times(1)).exchange(
                eq("http://candidate-service/api/v1/candidate-service/candidates/1"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class));
        verify(objectMapper, times(1)).createObjectNode();
    }

    @Test
    @DisplayName("SCH-CAND-TC-004: getCandidateNames - map đúng id sang name")
    void testGetCandidateNames_SCH_CAND_TC_004() throws Exception {
        // Testcase ID: SCH-CAND-TC-004
        // Objective: Xác nhận map đúng id -> name

        // arrange
        List<Long> ids = List.of(1L, 2L);
        String token = "valid-token";
        String responseBody = "{\"data\":[{\"id\":1,\"name\":\"An\"},{\"id\":2,\"name\":\"Binh\"}]}";

        JsonNode rootNode = mock(JsonNode.class);
        ArrayNode dataArray = mock(ArrayNode.class);

        JsonNode cand1 = mock(JsonNode.class);
        JsonNode cand2 = mock(JsonNode.class);

        JsonNode cand1Id = mock(JsonNode.class);
        JsonNode cand1Name = mock(JsonNode.class);

        JsonNode cand2Id = mock(JsonNode.class);
        JsonNode cand2Name = mock(JsonNode.class);

        ObjectNode idToName = new ObjectMapper().createObjectNode();

        when(restTemplate.exchange(
                eq("http://candidate-service/api/v1/candidate-service/candidates?ids=1,2"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)))
                .thenReturn(ResponseEntity.ok(responseBody));

        when(objectMapper.readTree(eq(responseBody))).thenReturn(rootNode);
        when(rootNode.get("data")).thenReturn(dataArray);

        when(objectMapper.createObjectNode()).thenReturn(idToName);

        when(dataArray.isArray()).thenReturn(true);
        when(dataArray.iterator()).thenReturn(List.<JsonNode>of(cand1, cand2).iterator());

        when(cand1.has("id")).thenReturn(true);
        when(cand1.has("name")).thenReturn(true);
        when(cand1.get("id")).thenReturn(cand1Id);
        when(cand1Id.asLong()).thenReturn(1L);
        when(cand1.get("name")).thenReturn(cand1Name);
        when(cand1Name.asText()).thenReturn("An");

        when(cand2.has("id")).thenReturn(true);
        when(cand2.has("name")).thenReturn(true);
        when(cand2.get("id")).thenReturn(cand2Id);
        when(cand2Id.asLong()).thenReturn(2L);
        when(cand2.get("name")).thenReturn(cand2Name);
        when(cand2Name.asText()).thenReturn("Binh");

        // act
        ResponseEntity<JsonNode> result = candidateService.getCandidateNames(ids, token);

        // assert
        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertTrue(result.getBody().has("1"));
        assertTrue(result.getBody().has("2"));
        assertEquals("An", result.getBody().get("1").asText());
        assertEquals("Binh", result.getBody().get("2").asText());

        verify(restTemplate, times(1)).exchange(
                eq("http://candidate-service/api/v1/candidate-service/candidates?ids=1,2"),
                eq(HttpMethod.GET),
                argThat(entity -> ("Bearer " + token).equals(entity.getHeaders().getFirst("Authorization"))),
                eq(String.class));
        verify(objectMapper, times(1)).readTree(eq(responseBody));
        verify(objectMapper, times(1)).createObjectNode();
    }

    @Test
    @DisplayName("SCH-CAND-TC-005: getCandidateNames - trả error body đúng khi candidate-service lỗi")
    void testGetCandidateNames_ErrorBody_SCH_CAND_TC_005() throws Exception {
        // Testcase ID: SCH-CAND-TC-005
        // Objective: Xác nhận trả error body đúng khi candidate-service lỗi

        // arrange
        List<Long> ids = List.of(1L, 2L);
        String responseBody = "{\"statusCode\":500,\"error\":\"Internal\",\"message\":\"Down\",\"data\":null}";

        HttpClientErrorException httpEx = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                HttpHeaders.EMPTY,
                responseBody.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);

        JsonNode errorNode = mock(JsonNode.class);

        when(restTemplate.exchange(
                eq("http://candidate-service/api/v1/candidate-service/candidates?ids=1,2"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)))
                .thenThrow(httpEx);

        when(objectMapper.readTree(eq(responseBody))).thenReturn(errorNode);

        // act
        ResponseEntity<JsonNode> result = candidateService.getCandidateNames(ids, null);

        // assert
        assertNotNull(result);
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
        assertNotNull(result.getBody());
        assertSame(errorNode, result.getBody());

        verify(restTemplate, times(1)).exchange(
                eq("http://candidate-service/api/v1/candidate-service/candidates?ids=1,2"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class));
        verify(objectMapper, times(1)).readTree(eq(responseBody));
    }

    @Test
    @DisplayName("SCH-CAND-TC-006: getEmployeeIdFromCandidateEmail - ưu tiên updatedBy")
    void testGetEmployeeIdFromCandidateEmail_UseUpdatedBy_SCH_CAND_TC_006() throws Exception {
        // Testcase ID: SCH-CAND-TC-006
        // Objective: Xác nhận ưu tiên updatedBy

        // arrange
        String email = "cand@gmail.com";

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

        // Use real JsonNode trees to avoid brittle chained stubbing of JsonNode.path(...)
        ObjectMapper realMapper = new ObjectMapper();
        when(objectMapper.readTree(eq(searchBody))).thenReturn(realMapper.readTree(searchBody));
        when(objectMapper.readTree(eq(detailBody))).thenReturn(realMapper.readTree(detailBody));

        // act
        Long result = candidateService.getEmployeeIdFromCandidateEmail(email);

        // assert
        assertNotNull(result);
        assertEquals(99L, result);

        verify(restTemplate, times(2)).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        verify(objectMapper, times(2)).readTree(anyString());
    }

    @Test
    @DisplayName("SCH-CAND-TC-007: getEmployeeIdFromCandidateEmail - fallback sang createdBy")
    void testGetEmployeeIdFromCandidateEmail_FallbackCreatedBy_SCH_CAND_TC_007() throws Exception {
        // Testcase ID: SCH-CAND-TC-007
        // Objective: Xác nhận fallback sang createdBy

        // arrange
        String email = "cand@gmail.com";

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

        ObjectMapper realMapper = new ObjectMapper();
        when(objectMapper.readTree(eq(searchBody))).thenReturn(realMapper.readTree(searchBody));
        when(objectMapper.readTree(eq(detailBody))).thenReturn(realMapper.readTree(detailBody));

        // act
        Long result = candidateService.getEmployeeIdFromCandidateEmail(email);

        // assert
        assertNotNull(result);
        assertEquals(77L, result);

        verify(restTemplate, times(2)).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        verify(objectMapper, times(2)).readTree(anyString());
    }

    @Test
    @DisplayName("SCH-CAND-TC-008: getEmployeeIdFromCandidateEmail - trả null khi không tìm được candidate phù hợp")
    void testGetEmployeeIdFromCandidateEmail_ReturnNull_SCH_CAND_TC_008() throws Exception {
        // Testcase ID: SCH-CAND-TC-008
        // Objective: Xác nhận trả null khi không tìm được candidate phù hợp

        // arrange
        String email = "missing@gmail.com";

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

        ObjectMapper realMapper = new ObjectMapper();
        when(objectMapper.readTree(eq(searchBody))).thenReturn(realMapper.readTree(searchBody));

        // act
        Long result = candidateService.getEmployeeIdFromCandidateEmail(email);

        // assert
        assertNull(result);

        verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        verify(restTemplate, never()).exchange(contains("candidateId="), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
        verify(objectMapper, times(1)).readTree(eq(searchBody));
    }
}