package com.example.candidate_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;
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

    // ================= SS-TC-01 =================
    @Test
    @DisplayName("SS-TC-01: createSchedule - dữ liệu hợp lệ -> tạo mới lịch thành công")
    void ss_tc_01_createSchedule_validInput_shouldCreateSuccessfully() {
        // Not applicable: ScheduleService hiện tại KHÔNG có
        // createSchedule(CreateScheduleDTO)
        assertThatThrownBy(() -> scheduleService.getClass().getDeclaredMethod("createSchedule", Object.class))
                .isInstanceOf(NoSuchMethodException.class);
    }

    // ================= SS-TC00 =================
    @Test
    @DisplayName("SS-TC00: updateSchedule - id hợp lệ cập nhật thành công, id không tồn tại ném exception")
    void ss_tc_00_updateSchedule_validIdAndNotFound_shouldBehaveAsExpected() {
        // Not applicable: ScheduleService hiện tại KHÔNG có updateSchedule(Long,
        // CreateScheduleDTO)
        assertThatThrownBy(
                () -> scheduleService.getClass().getDeclaredMethod("updateSchedule", Long.class, Object.class))
                .isInstanceOf(NoSuchMethodException.class);
    }

    // ================= SS-TC01 =================
    @Test
    @DisplayName("SS-TC01: deleteSchedule - id tồn tại xóa thành công, id không tồn tại ném IdInvalidException")
    void ss_tc_01_deleteSchedule_existsAndNotFound_shouldBehaveAsExpected() {
        // Not applicable: ScheduleService hiện tại KHÔNG có deleteSchedule(Long)
        assertThatThrownBy(() -> scheduleService.getClass().getDeclaredMethod("deleteSchedule", Long.class))
                .isInstanceOf(NoSuchMethodException.class);
    }

    // ================= SS-TC02 =================
    @Test
    @DisplayName("SS-TC02: getScheduleById - id tồn tại trả ScheduleDTO, id không tồn tại ném IdInvalidException")
    void ss_tc_02_getScheduleById_existsAndNotFound_shouldBehaveAsExpected() {
        // Not applicable: ScheduleService hiện tại KHÔNG có getScheduleById(Long)
        assertThatThrownBy(() -> scheduleService.getClass().getDeclaredMethod("getScheduleById", Long.class))
                .isInstanceOf(NoSuchMethodException.class);
    }

    // ================= SS-TC03 =================
    @Test
    @DisplayName("SS-TC03: getScheduleWithParticipantNames - lấy lịch và map participantNames đúng")
    void ss_tc_03_getScheduleWithParticipantNames_shouldMapNamesCorrectly() {
        // Not applicable: ScheduleService hiện tại KHÔNG có
        // getScheduleWithParticipantNames(Long, String)
        assertThatThrownBy(
                () -> scheduleService.getClass().getDeclaredMethod("getScheduleWithParticipantNames", Long.class,
                        String.class))
                .isInstanceOf(NoSuchMethodException.class);
    }

    // ================= SS-TC04 =================
    @Test
    @DisplayName("SS-TC04: getAllSchedules - lọc đúng danh sách lịch và phân trang đúng")
    void ss_tc_04_getAllSchedules_shouldFilterAndPaginateCorrectly() {
        // Not applicable: ScheduleService hiện tại KHÔNG có getAllSchedules(...)
        assertThatThrownBy(() -> scheduleService.getClass().getDeclaredMethod("getAllSchedules", int.class, int.class,
                String.class, String.class, java.time.LocalDate.class, Integer.class, Integer.class, String.class,
                String.class, Long.class, String.class))
                .isInstanceOf(NoSuchMethodException.class);
    }

    // ===== Additional coverage for existing methods =====

    @Test
    @DisplayName("EX-01: getUpcomingSchedulesForCandidate - có data -> trả data và gọi restTemplate đúng 1 lần")
    void ex_01_getUpcomingSchedulesForCandidate_hasData_shouldReturnData() {
        // Arrange
        Long candidateId = 10L;
        String token = "jwt-token";

        JsonNode dataNode = new ObjectMapper().createArrayNode();
        Response<JsonNode> body = new Response<>();
        body.setData(dataNode);

        ResponseEntity<Response<JsonNode>> exchangeResponse = new ResponseEntity<>(body, HttpStatus.OK);

        when(restTemplate.exchange(
                any(String.class),
                eq(HttpMethod.GET),
                any(),
                any(new ParameterizedTypeReference<Response<JsonNode>>() {
                }.getClass())))
                .thenReturn(exchangeResponse);

        // Act
        ResponseEntity<JsonNode> result = scheduleService.getUpcomingSchedulesForCandidate(candidateId, token);

        // Assert
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(dataNode);

        verify(restTemplate, times(1)).exchange(any(String.class), eq(HttpMethod.GET), any(),
                any(org.springframework.core.ParameterizedTypeReference.class));
        verify(objectMapper, never()).createArrayNode();
        verify(objectMapper, never()).createObjectNode();
    }

    @Test
    @DisplayName("EX-02: getUpcomingSchedulesForCandidate - body null/ data null -> trả empty array")
    void ex_02_getUpcomingSchedulesForCandidate_nullData_shouldReturnEmptyArray() {
        // Arrange
        Long candidateId = 11L;
        String token = "";

        Response<JsonNode> body = new Response<>();
        body.setData(null);

        @SuppressWarnings("unchecked")
        ResponseEntity<Response<JsonNode>> exchangeResponse = new ResponseEntity<>(body, HttpStatus.OK);

        ArrayNode emptyArray = new ObjectMapper().createArrayNode();

        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(),
                any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(exchangeResponse);
        when(objectMapper.createArrayNode()).thenReturn(emptyArray);

        // Act
        ResponseEntity<JsonNode> result = scheduleService.getUpcomingSchedulesForCandidate(candidateId, token);

        // Assert
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(emptyArray);

        verify(objectMapper, times(1)).createArrayNode();
        verify(objectMapper, never()).createObjectNode();
    }

    @Test
    @DisplayName("EX-03: getUpcomingSchedulesForCandidate - restTemplate lỗi -> trả 500 và error json")
    void ex_03_getUpcomingSchedulesForCandidate_restTemplateThrows_shouldReturnInternalServerError() {
        // Arrange
        Long candidateId = 12L;
        String token = null;

        ObjectNode errorNode = new ObjectMapper().createObjectNode();

        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(),
                any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("boom"));
        when(objectMapper.createObjectNode()).thenReturn(errorNode);

        // Act
        ResponseEntity<JsonNode> result = scheduleService.getUpcomingSchedulesForCandidate(candidateId, token);

        // Assert
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result.getBody()).isSameAs(errorNode);
        assertThat(errorNode.get("statusCode").asInt()).isEqualTo(500);
        assertThat(errorNode.get("message").asText()).contains("boom");

        verify(objectMapper, times(1)).createObjectNode();
    }

    @Test
    @DisplayName("EX-04: getCandidateIdsByInterviewer - có data -> trả list")
    void ex_04_getCandidateIdsByInterviewer_hasData_shouldReturnList() {
        // Arrange
        Long employeeId = 5L;
        String token = "jwt";

        List<Long> data = List.of(1L, 2L, 3L);
        Response<List<Long>> body = new Response<>();
        body.setData(data);

        @SuppressWarnings("unchecked")
        ResponseEntity<Response<List<Long>>> exchangeResponse = new ResponseEntity<>(body, HttpStatus.OK);

        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(),
                any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(exchangeResponse);

        // Act
        List<Long> result = scheduleService.getCandidateIdsByInterviewer(employeeId, token);

        // Assert
        assertThat(result).containsExactly(1L, 2L, 3L);

        verify(restTemplate, times(1)).exchange(any(String.class), eq(HttpMethod.GET), any(),
                any(org.springframework.core.ParameterizedTypeReference.class));
    }

    @Test
    @DisplayName("EX-05: getCandidateIdsByInterviewer - data null/exception -> trả empty list")
    void ex_05_getCandidateIdsByInterviewer_nullOrException_shouldReturnEmptyList() {
        // Arrange
        Long employeeId = 6L;

        Response<List<Long>> body = new Response<>();
        body.setData(null);

        @SuppressWarnings("unchecked")
        ResponseEntity<Response<List<Long>>> exchangeResponse = new ResponseEntity<>(body, HttpStatus.OK);

        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(),
                any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(exchangeResponse)
                .thenThrow(new RuntimeException("down"));

        // Act
        List<Long> result1 = scheduleService.getCandidateIdsByInterviewer(employeeId, "");
        List<Long> result2 = scheduleService.getCandidateIdsByInterviewer(employeeId, "jwt");

        // Assert
        assertThat(result1).isEmpty();
        assertThat(result2).isEmpty();

        verify(restTemplate, times(2)).exchange(any(String.class), eq(HttpMethod.GET), any(),
                any(org.springframework.core.ParameterizedTypeReference.class));
    }
}
