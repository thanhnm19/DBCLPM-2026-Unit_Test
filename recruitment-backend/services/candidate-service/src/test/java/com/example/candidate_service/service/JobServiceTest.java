package com.example.candidate_service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;

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
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.example.candidate_service.dto.PaginationDTO;
import com.example.candidate_service.dto.Response;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class JobServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private JobService jobService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jobService, "jobServiceUrl", "http://job-service");
    }

    @Test
    @DisplayName("JS-TC-001: getJobPositionById - unwrap đúng data jobPosition")
    void testGetJobPositionById_JS_TC_001() {
        // Testcase ID: JS-TC-001
        // Objective: Xác nhận unwrap đúng data jobPosition

        // arrange
        Long id = 1L;
        String token = "token";
        JsonNode jobPositionNode = mock(JsonNode.class);

        Response<JsonNode> wrapped = (Response<JsonNode>) mock(Response.class);
        when(wrapped.getData()).thenReturn(jobPositionNode);

        ResponseEntity<Response<JsonNode>> exchangeResponse = ResponseEntity.ok(wrapped);

        when(restTemplate.exchange(
                contains("/api/v1/job-service/job-positions/" + id),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn((ResponseEntity) exchangeResponse);

        // act
        ResponseEntity<JsonNode> result = jobService.getJobPositionById(id, token);

        // assert
        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(jobPositionNode, result.getBody());

        verify(restTemplate, times(1)).exchange(
                contains("/api/v1/job-service/job-positions/" + id),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class));
    }

    @Test
    @DisplayName("JS-TC-002: getJobPositionById - trả notFound khi không có data")
    void testGetJobPositionById_NotFound_JS_TC_002() {
        // Testcase ID: JS-TC-002
        // Objective: Xác nhận trả notFound khi không có data

        // arrange
        Long id = 999L;
        String token = "token";

        Response<JsonNode> wrapped = (Response<JsonNode>) mock(Response.class);
        when(wrapped.getData()).thenReturn(null);

        ResponseEntity<Response<JsonNode>> exchangeResponse = ResponseEntity.ok(wrapped);

        when(restTemplate.exchange(
                contains("/api/v1/job-service/job-positions/" + id),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn((ResponseEntity) exchangeResponse);

        // act
        ResponseEntity<JsonNode> result = jobService.getJobPositionById(id, token);

        // assert
        assertNotNull(result);
        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
        assertNull(result.getBody());

        verify(restTemplate, times(1)).exchange(
                contains("/api/v1/job-service/job-positions/" + id),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class));
    }

    private static PaginationDTO newPaginationDTOWithResultAndPages(Object result, int pages) {
        try {
            Constructor<PaginationDTO> ctor = PaginationDTO.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            PaginationDTO dto = ctor.newInstance();

            // set result
            Field resultField = PaginationDTO.class.getDeclaredField("result");
            resultField.setAccessible(true);
            resultField.set(dto, result);

            // set meta.pages (if meta exists as a field)
            Field metaField = PaginationDTO.class.getDeclaredField("meta");
            metaField.setAccessible(true);
            Object metaObj = metaField.get(dto);
            if (metaObj == null) {
                Class<?> metaType = metaField.getType();
                Constructor<?> metaCtor = metaType.getDeclaredConstructor();
                metaCtor.setAccessible(true);
                metaObj = metaCtor.newInstance();
                metaField.set(dto, metaObj);
            }

            try {
                Field pagesField = metaObj.getClass().getDeclaredField("pages");
                pagesField.setAccessible(true);
                pagesField.set(metaObj, pages);
            } catch (NoSuchFieldException ignored) {
                // fallback: maybe getter derives pages elsewhere; leave as-is
            }

            return dto;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("JS-TC-003: getJobPositionIdsByDepartmentId - duyệt nhiều page và gom đủ IDs")
    void testGetJobPositionIdsByDepartmentId_MultiPage_JS_TC_003() {
        // Testcase ID: JS-TC-003
        // Objective: Xác nhận duyệt nhiều page và gom đủ IDs

        // arrange
        Long departmentId = 5L;
        String token = "token";

        Map<String, Object> jp10 = new HashMap<>();
        jp10.put("id", 10);
        Map<String, Object> jp11 = new HashMap<>();
        jp11.put("id", 11);
        Map<String, Object> jp12 = new HashMap<>();
        jp12.put("id", 12L);

        PaginationDTO pagination1 = newPaginationDTOWithResultAndPages(List.of(jp10, jp11), 2);
        PaginationDTO pagination2 = newPaginationDTOWithResultAndPages(List.of(jp12), 1);

        Response<PaginationDTO> wrapped1 = (Response<PaginationDTO>) mock(Response.class);
        when(wrapped1.getData()).thenReturn(pagination1);
        ResponseEntity<Response<PaginationDTO>> exchangeResponse1 = ResponseEntity.ok(wrapped1);

        Response<PaginationDTO> wrapped2 = (Response<PaginationDTO>) mock(Response.class);
        when(wrapped2.getData()).thenReturn(pagination2);
        ResponseEntity<Response<PaginationDTO>> exchangeResponse2 = ResponseEntity.ok(wrapped2);

        when(restTemplate.exchange(
                contains("/api/v1/job-service/job-positions?departmentId=" + departmentId + "&page=1&limit=100"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn((ResponseEntity) exchangeResponse1);

        when(restTemplate.exchange(
                contains("/api/v1/job-service/job-positions?departmentId=" + departmentId + "&page=2&limit=100"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn((ResponseEntity) exchangeResponse2);

        // act
        List<Long> result = jobService.getJobPositionIdsByDepartmentId(departmentId, token);

        // assert
        assertNotNull(result);
        assertEquals(List.of(10L, 11L, 12L), result);

        verify(restTemplate, times(2)).exchange(
                contains("/api/v1/job-service/job-positions?departmentId=" + departmentId),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class));
    }

    @Test
    @DisplayName("JS-TC-004: getJobPositionIdsByDepartmentId - vẫn trả kết quả page trước khi page sau lỗi")
    void testGetJobPositionIdsByDepartmentId_PartialFailure_JS_TC_004() {
        // Testcase ID: JS-TC-004
        // Objective: Xác nhận vẫn trả kết quả page trước khi page sau lỗi

        // arrange
        Long departmentId = 5L;
        String token = "token";

        Map<String, Object> jp10 = new HashMap<>();
        jp10.put("id", 10);
        Map<String, Object> jp11 = new HashMap<>();
        jp11.put("id", 11);

        PaginationDTO pagination1 = newPaginationDTOWithResultAndPages(List.of(jp10, jp11), 2);

        Response<PaginationDTO> wrapped1 = (Response<PaginationDTO>) mock(Response.class);
        when(wrapped1.getData()).thenReturn(pagination1);
        ResponseEntity<Response<PaginationDTO>> exchangeResponse1 = ResponseEntity.ok(wrapped1);

        when(restTemplate.exchange(
                contains("/api/v1/job-service/job-positions?departmentId=" + departmentId + "&page=1&limit=100"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn((ResponseEntity) exchangeResponse1);

        when(restTemplate.exchange(
                contains("/api/v1/job-service/job-positions?departmentId=" + departmentId + "&page=2&limit=100"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("page 2 error"));

        // act
        List<Long> result = jobService.getJobPositionIdsByDepartmentId(departmentId, token);

        // assert
        assertNotNull(result);
        assertEquals(List.of(10L, 11L), result);

        verify(restTemplate, times(2)).exchange(
                contains("/api/v1/job-service/job-positions?departmentId=" + departmentId),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class));
    }

    @Test
    @DisplayName("JS-TC-005: getJobPositionsByDepartmentId - build đúng map jobPositionId sang JsonNode")
    void testGetJobPositionsByDepartmentId_JS_TC_005() {
        // Testcase ID: JS-TC-005
        // Objective: Xác nhận build đúng map jobPositionId -> JsonNode

        // arrange
        Long departmentId = 5L;
        String token = "token";

        Map<String, Object> item1 = new HashMap<>();
        item1.put("id", 1);
        item1.put("name", "JP1");
        Map<String, Object> item2 = new HashMap<>();
        item2.put("id", 2L);
        item2.put("name", "JP2");

        PaginationDTO pagination = mock(PaginationDTO.class);
        when(pagination.getResult()).thenReturn(List.of(item1, item2));

        Response<PaginationDTO> wrapped = (Response<PaginationDTO>) mock(Response.class);
        when(wrapped.getData()).thenReturn(pagination);

        ResponseEntity<Response<PaginationDTO>> exchangeResponse = ResponseEntity.ok(wrapped);

        ObjectNode jp1Node = new ObjectMapper().createObjectNode().put("id", 1).put("name", "JP1");
        ObjectNode jp2Node = new ObjectMapper().createObjectNode().put("id", 2).put("name", "JP2");

        when(objectMapper.valueToTree(item1)).thenReturn(jp1Node);
        when(objectMapper.valueToTree(item2)).thenReturn(jp2Node);

        when(restTemplate.exchange(
                contains("/api/v1/job-service/job-positions/simple?departmentId=" + departmentId),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn((ResponseEntity) exchangeResponse);

        // act
        Map<Long, JsonNode> result = jobService.getJobPositionsByDepartmentId(departmentId, token);

        // assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.containsKey(1L));
        assertTrue(result.containsKey(2L));
        assertEquals("JP1", result.get(1L).get("name").asText());
        assertEquals("JP2", result.get(2L).get("name").asText());

        verify(restTemplate, times(1)).exchange(
                contains("/api/v1/job-service/job-positions/simple?departmentId=" + departmentId),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class));
        verify(objectMapper, times(2)).valueToTree(any());
    }

    @Test
    @DisplayName("JS-TC-006: getJobPositionsByIdsSimple - trả map rỗng khi input rỗng")
    void testGetJobPositionsByIdsSimple_EmptyInput_JS_TC_006() {
        // Testcase ID: JS-TC-006
        // Objective: Xác nhận xử lý input rỗng

        // arrange
        List<Long> ids = Collections.emptyList();
        String token = "token";

        // act
        Map<Long, JsonNode> result = jobService.getJobPositionsByIdsSimple(ids, token);

        // assert
        assertNotNull(result);
        assertEquals(Map.of(), result);

        verifyNoInteractions(restTemplate);
        verifyNoInteractions(objectMapper);
    }

    @Test
    @DisplayName("JS-TC-007: getJobPositionsByIdsSimple - parse list sang map đúng")
    void testGetJobPositionsByIdsSimple_Success_JS_TC_007() {
        // Testcase ID: JS-TC-007
        // Objective: Xác nhận parse list sang map đúng

        // arrange
        List<Long> ids = List.of(1L, 2L);
        String token = "token";

        Map<String, Object> item1 = new HashMap<>();
        item1.put("id", 1);
        item1.put("title", "A");
        Map<String, Object> item2 = new HashMap<>();
        item2.put("id", 2);
        item2.put("title", "B");

        PaginationDTO pagination = mock(PaginationDTO.class);
        when(pagination.getResult()).thenReturn(List.of(item1, item2));

        Response<PaginationDTO> wrapped = (Response<PaginationDTO>) mock(Response.class);
        when(wrapped.getData()).thenReturn(pagination);

        ResponseEntity<Response<PaginationDTO>> exchangeResponse = ResponseEntity.ok(wrapped);

        ObjectNode jp1Node = new ObjectMapper().createObjectNode().put("id", 1).put("title", "A");
        ObjectNode jp2Node = new ObjectMapper().createObjectNode().put("id", 2).put("title", "B");

        when(objectMapper.valueToTree(item1)).thenReturn(jp1Node);
        when(objectMapper.valueToTree(item2)).thenReturn(jp2Node);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        when(restTemplate.exchange(
                urlCaptor.capture(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn((ResponseEntity) exchangeResponse);

        // act
        Map<Long, JsonNode> result = jobService.getJobPositionsByIdsSimple(ids, token);

        // assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.containsKey(1L));
        assertTrue(result.containsKey(2L));
        assertEquals("A", result.get(1L).get("title").asText());
        assertEquals("B", result.get(2L).get("title").asText());

        String calledUrl = urlCaptor.getValue();
        assertNotNull(calledUrl);
        assertTrue(calledUrl.contains("/api/v1/job-service/job-positions/simple?ids="));
        assertTrue(calledUrl.contains("1,2"));

        verify(restTemplate, times(1)).exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                any(ParameterizedTypeReference.class));
        verify(objectMapper, times(2)).valueToTree(any());
    }

    @Test
    @DisplayName("JS-TC-008: getJobPositionByIdSimple - unwrap đúng body.data")
    void testGetJobPositionByIdSimple_JS_TC_008() {
        // Testcase ID: JS-TC-008
        // Objective: Xác nhận unwrap đúng body.data

        // arrange
        Long id = 1L;
        String token = "token";

        ObjectMapper realMapper = new ObjectMapper();
        ObjectNode body = realMapper.createObjectNode();
        ObjectNode data = realMapper.createObjectNode().put("id", 1).put("name", "JP");
        body.set("data", data);

        when(restTemplate.exchange(
                contains("/api/v1/job-service/job-positions/simple/" + id),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(body));

        // act
        ResponseEntity<JsonNode> result = jobService.getJobPositionByIdSimple(id, token);

        // assert
        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(1L, result.getBody().get("id").asLong());
        assertEquals("JP", result.getBody().get("name").asText());

        verify(restTemplate, times(1)).exchange(
                contains("/api/v1/job-service/job-positions/simple/" + id),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(JsonNode.class));
    }

    @Test
    @DisplayName("JS-TC-009: getJobPositionByIdSimple - trả 404 hoặc 500 đúng nhánh")
    void testGetJobPositionByIdSimple_NotFoundOrError_JS_TC_009() {
        // Testcase ID: JS-TC-009
        // Objective: Xác nhận trả 404 hoặc 500 đúng nhánh

        // arrange
        Long idNotFound = 999L;
        String token = "token";

        ObjectMapper realMapper = new ObjectMapper();
        ObjectNode bodyNoData = realMapper.createObjectNode();
        // no "data" field -> should return notFound

        when(restTemplate.exchange(
                contains("/api/v1/job-service/job-positions/simple/" + idNotFound),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(bodyNoData));

        // act (notFound branch)
        ResponseEntity<JsonNode> notFoundResult = jobService.getJobPositionByIdSimple(idNotFound, token);

        // assert (notFound branch)
        assertNotNull(notFoundResult);
        assertEquals(HttpStatus.NOT_FOUND, notFoundResult.getStatusCode());
        assertNull(notFoundResult.getBody());

        // arrange (exception branch)
        Long idError = 1000L;

        ObjectNode errorNode = realMapper.createObjectNode();
        when(objectMapper.createObjectNode()).thenReturn(errorNode);

        when(restTemplate.exchange(
                contains("/api/v1/job-service/job-positions/simple/" + idError),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(JsonNode.class)))
                .thenThrow(new RuntimeException("connection refused"));

        // act (error branch)
        ResponseEntity<JsonNode> errorResult = jobService.getJobPositionByIdSimple(idError, token);

        // assert (error branch)
        assertNotNull(errorResult);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, errorResult.getStatusCode());
        assertNotNull(errorResult.getBody());
        assertTrue(errorResult.getBody().has("statusCode"));
        assertEquals(500, errorResult.getBody().get("statusCode").asInt());
        assertTrue(errorResult.getBody().has("message"));
        assertTrue(errorResult.getBody().get("message").asText().contains("Không thể kết nối tới Job Service"));

        verify(restTemplate, times(2)).exchange(
                contains("/api/v1/job-service/job-positions/simple/"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(JsonNode.class));
        verify(objectMapper, times(1)).createObjectNode();
    }
}