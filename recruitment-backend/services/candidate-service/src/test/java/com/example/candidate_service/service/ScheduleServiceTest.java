package com.example.candidate_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    }