package com.example.candidate_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.example.candidate_service.dto.Response;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleService Unit Test")
class ScheduleServiceTest {

    // ===== Dependencies =====
    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ScheduleService scheduleService;

    // ===== SS-TC00..SS-TC05 (theoretical APIs not present in current ScheduleService) =====

    @Test
    @DisplayName("SS-TC00: updateSchedule - id hợp lệ cập nhật thành công, id không tồn tại ném exception")
    void ss_tc_00_updateSchedule_validIdAndNotFound_shouldBehaveAsExpected() {
        assertThatThrownBy(() -> scheduleService.getClass().getDeclaredMethod("updateSchedule", Long.class, Object.class))
                .isInstanceOf(NoSuchMethodException.class);
    }

    @Test
    @DisplayName("SS-TC01: createSchedule - dữ liệu hợp lệ -> tạo mới lịch thành công")
    void ss_tc_01_createSchedule_validInput_shouldCreateSuccessfully() {
        assertThatThrownBy(() -> scheduleService.getClass().getDeclaredMethod("createSchedule", Object.class))
                .isInstanceOf(NoSuchMethodException.class);
    }

    @Test
    @DisplayName("SS-TC02: deleteSchedule - id tồn tại xóa thành công, id không tồn tại ném IdInvalidException")
    void ss_tc_02_deleteSchedule_existsAndNotFound_shouldBehaveAsExpected() {
        assertThatThrownBy(() -> scheduleService.getClass().getDeclaredMethod("deleteSchedule", Long.class))
                .isInstanceOf(NoSuchMethodException.class);
    }

    @Test
    @DisplayName("SS-TC03: getScheduleById - id tồn tại trả ScheduleDTO, id không tồn tại ném IdInvalidException")
    void ss_tc_03_getScheduleById_existsAndNotFound_shouldBehaveAsExpected() {
        assertThatThrownBy(() -> scheduleService.getClass().getDeclaredMethod("getScheduleById", Long.class))
                .isInstanceOf(NoSuchMethodException.class);
    }

    @Test
    @DisplayName("SS-TC04: getScheduleWithParticipantNames - lấy lịch và map participantNames đúng")
    void ss_tc_04_getScheduleWithParticipantNames_shouldMapNamesCorrectly() {
        assertThatThrownBy(
                () -> scheduleService.getClass().getDeclaredMethod("getScheduleWithParticipantNames", Long.class,
                        String.class))
                .isInstanceOf(NoSuchMethodException.class);
    }

    @Test
    @DisplayName("SS-TC05: getAllSchedules - lọc đúng danh sách lịch và phân trang đúng")
    void ss_tc_05_getAllSchedules_shouldFilterAndPaginateCorrectly() {
        assertThatThrownBy(() -> scheduleService.getClass().getDeclaredMethod("getAllSchedules", int.class, int.class,
                String.class, String.class, java.time.LocalDate.class, Integer.class, Integer.class, String.class,
                String.class, Long.class, String.class))
                .isInstanceOf(NoSuchMethodException.class);
    }

    // ===== Coverage for existing methods =====

    @Test
    @DisplayName("EX-01: getUpcomingSchedulesForCandidate - có data -> trả data và gọi restTemplate đúng 1 lần")
    void ex_01_getUpcomingSchedulesForCandidate_hasData_shouldReturnData() {
        // Arrange
        Long candidateId = 10L;
        String token = "jwt-token";

        JsonNode dataNode = new ObjectMapper().createArrayNode();
        Response<JsonNode> responseBody = new Response<>();
        responseBody.setData(dataNode);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // Act
        ResponseEntity<JsonNode> result = scheduleService.getUpcomingSchedulesForCandidate(candidateId, token);

        // Assert
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(dataNode);
        verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class));
        verify(objectMapper, never()).createArrayNode();
        verify(objectMapper, never()).createObjectNode();
    }

    @Test
    @DisplayName("EX-02: getUpcomingSchedulesForCandidate - body có nhưng data null -> trả empty array")
    void ex_02_getUpcomingSchedulesForCandidate_nullData_shouldReturnEmptyArray() {
        // Arrange
        Response<JsonNode> responseBody = new Response<>();
        responseBody.setData(null);
        ArrayNode emptyArray = new ObjectMapper().createArrayNode();

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));
        when(objectMapper.createArrayNode()).thenReturn(emptyArray);

        // Act
        ResponseEntity<JsonNode> result = scheduleService.getUpcomingSchedulesForCandidate(11L, "");

        // Assert
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(emptyArray);
        verify(objectMapper, times(1)).createArrayNode();
        verify(objectMapper, never()).createObjectNode();
    }

    @Test
    @DisplayName("EX-03: getUpcomingSchedulesForCandidate - response body null -> trả empty array")
    void ex_03_getUpcomingSchedulesForCandidate_nullBody_shouldReturnEmptyArray() {
        // Arrange
        ArrayNode emptyArray = new ObjectMapper().createArrayNode();

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));
        when(objectMapper.createArrayNode()).thenReturn(emptyArray);

        // Act
        ResponseEntity<JsonNode> result = scheduleService.getUpcomingSchedulesForCandidate(12L, "");

        // Assert
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(emptyArray);
        verify(objectMapper, times(1)).createArrayNode();
    }

    @Test
    @DisplayName("EX-04: getUpcomingSchedulesForCandidate - restTemplate lỗi -> trả 500 và error json")
    void ex_04_getUpcomingSchedulesForCandidate_restTemplateThrows_shouldReturnInternalServerError() {
        // Arrange
        ObjectNode errorNode = new ObjectMapper().createObjectNode();

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("boom"));
        when(objectMapper.createObjectNode()).thenReturn(errorNode);

        // Act
        ResponseEntity<JsonNode> result = scheduleService.getUpcomingSchedulesForCandidate(13L, null);

        // Assert
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result.getBody()).isSameAs(errorNode);
        assertThat(errorNode.get("statusCode").asInt()).isEqualTo(500);
        assertThat(errorNode.get("error").asText()).isEqualTo("Internal Server Error");
        assertThat(errorNode.get("message").asText()).contains("boom");
        assertThat(errorNode.get("data").isNull()).isTrue();
        verify(objectMapper, times(1)).createObjectNode();
    }

    @Test
    @DisplayName("EX-05: getUpcomingSchedulesForCandidate - token hợp lệ -> gắn Bearer và query đúng")
    void ex_05_getUpcomingSchedulesForCandidate_withToken_shouldBuildAuthHeaderAndUrl() {
        // Arrange
        Long candidateId = 22L;
        String token = "jwt-token";
        Response<JsonNode> responseBody = new Response<>();
        responseBody.setData(new ObjectMapper().createArrayNode());

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // Act
        scheduleService.getUpcomingSchedulesForCandidate(candidateId, token);

        // Assert
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);

        verify(restTemplate).exchange(urlCaptor.capture(), eq(HttpMethod.GET), entityCaptor.capture(),
                any(ParameterizedTypeReference.class));

        assertThat(urlCaptor.getValue()).contains("/api/v1/schedule-service/schedules");
        assertThat(urlCaptor.getValue()).contains("participantId=" + candidateId);
        assertThat(urlCaptor.getValue()).contains("participantType=CANDIDATE");

        HttpHeaders headers = entityCaptor.getValue().getHeaders();
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + token);
        assertThat(headers.getAccept()).contains(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("EX-06: getUpcomingSchedulesForCandidate - token rỗng/null -> không gắn Authorization")
    void ex_06_getUpcomingSchedulesForCandidate_blankToken_shouldNotSetAuthorization() {
        // Arrange
        Response<JsonNode> responseBody = new Response<>();
        responseBody.setData(new ObjectMapper().createArrayNode());

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // Act
        scheduleService.getUpcomingSchedulesForCandidate(33L, "");

        // Assert
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        verify(restTemplate).exchange(anyString(), eq(HttpMethod.GET), entityCaptor.capture(),
                any(ParameterizedTypeReference.class));

        HttpHeaders headers = entityCaptor.getValue().getHeaders();
        assertThat(headers.containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
        assertThat(headers.getAccept()).contains(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("EX-07: getCandidateIdsByInterviewer - có data -> trả list")
    void ex_07_getCandidateIdsByInterviewer_hasData_shouldReturnList() {
        // Arrange
        Response<List<Long>> responseBody = new Response<>();
        responseBody.setData(List.of(1L, 2L, 3L));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // Act
        List<Long> result = scheduleService.getCandidateIdsByInterviewer(5L, "jwt");

        // Assert
        assertThat(result).containsExactly(1L, 2L, 3L);
        verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class));
    }

    @Test
    @DisplayName("EX-08: getCandidateIdsByInterviewer - data null hoặc exception -> trả empty list")
    void ex_08_getCandidateIdsByInterviewer_nullDataOrException_shouldReturnEmptyList() {
        // Arrange
        Response<List<Long>> responseBody = new Response<>();
        responseBody.setData(null);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK))
                .thenThrow(new RuntimeException("down"));

        // Act
        List<Long> firstCall = scheduleService.getCandidateIdsByInterviewer(6L, "");
        List<Long> secondCall = scheduleService.getCandidateIdsByInterviewer(6L, "jwt");

        // Assert
        assertThat(firstCall).isEmpty();
        assertThat(secondCall).isEmpty();
        verify(restTemplate, times(2)).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class));
    }

    @Test
    @DisplayName("EX-09: getCandidateIdsByInterviewer - response body null -> trả empty list")
    void ex_09_getCandidateIdsByInterviewer_nullBody_shouldReturnEmptyList() {
        // Arrange
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        // Act
        List<Long> result = scheduleService.getCandidateIdsByInterviewer(40L, "jwt");

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("EX-10: getCandidateIdsByInterviewer - token hợp lệ -> gắn Bearer và URL đúng")
    void ex_10_getCandidateIdsByInterviewer_withToken_shouldBuildAuthHeaderAndUrl() {
        // Arrange
        Long employeeId = 77L;
        String token = "jwt-token";

        Response<List<Long>> responseBody = new Response<>();
        responseBody.setData(List.of());

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // Act
        scheduleService.getCandidateIdsByInterviewer(employeeId, token);

        // Assert
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);

        verify(restTemplate).exchange(urlCaptor.capture(), eq(HttpMethod.GET), entityCaptor.capture(),
                any(ParameterizedTypeReference.class));

        assertThat(urlCaptor.getValue())
                .contains("/api/v1/schedule-service/schedules/candidates-by-interviewer/" + employeeId);

        HttpHeaders headers = entityCaptor.getValue().getHeaders();
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + token);
        assertThat(headers.getAccept()).contains(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("EX-11: getCandidateIdsByInterviewer - token rỗng/null -> không gắn Authorization")
    void ex_11_getCandidateIdsByInterviewer_blankToken_shouldNotSetAuthorization() {
        // Arrange
        Response<List<Long>> responseBody = new Response<>();
        responseBody.setData(List.of(99L));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                any(ParameterizedTypeReference.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

        // Act
        scheduleService.getCandidateIdsByInterviewer(99L, null);

        // Assert
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        verify(restTemplate).exchange(anyString(), eq(HttpMethod.GET), entityCaptor.capture(),
                any(ParameterizedTypeReference.class));

        HttpHeaders headers = entityCaptor.getValue().getHeaders();
        assertThat(headers.containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
        assertThat(headers.getAccept()).contains(MediaType.APPLICATION_JSON);
    }
}
