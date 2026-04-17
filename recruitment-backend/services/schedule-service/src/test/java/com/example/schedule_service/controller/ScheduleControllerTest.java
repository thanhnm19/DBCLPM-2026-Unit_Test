package com.example.schedule_service.controller;

import com.example.schedule_service.dto.PaginationDTO;
import com.example.schedule_service.dto.schedule.CreateScheduleDTO;
import com.example.schedule_service.model.Schedule;
import com.example.schedule_service.service.ScheduleService;
import com.example.schedule_service.utils.enums.MeetingType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleController Unit Test")
class ScheduleControllerTest {

    @Mock
    private ScheduleService scheduleService;

    @InjectMocks
    private ScheduleController scheduleController;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("SCH-TC-010: getCalendarView - filter đúng theo endDate, không trả lịch vượt khoảng lọc")
    void schTc010_getCalendarView_filterByEndDate() {
        LocalDate startDate = LocalDate.of(2026, 4, 1);
        LocalDate endDate = LocalDate.of(2026, 4, 30);

        Schedule inRange = new Schedule();
        inRange.setId(1L);
        inRange.setStartTime(LocalDateTime.of(2026, 4, 10, 9, 0));

        Schedule atBoundary = new Schedule();
        atBoundary.setId(2L);
        atBoundary.setStartTime(LocalDateTime.of(2026, 4, 30, 18, 0));

        Schedule outRange = new Schedule();
        outRange.setId(3L);
        outRange.setStartTime(LocalDateTime.of(2026, 5, 1, 8, 0));

        PaginationDTO paginationDTO = new PaginationDTO();
        paginationDTO.setResult(List.of(inRange, atBoundary, outRange));

        when(scheduleService.getAllSchedules(
                eq(1), eq(1000), eq("startTime"), eq("asc"),
                eq(startDate), isNull(), isNull(), isNull(), isNull(), eq(9L), eq("USER")))
                .thenReturn(paginationDTO);

        ResponseEntity<Map<String, Object>> response = scheduleController.getCalendarView(
                startDate, endDate, 9L, "USER");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();

        @SuppressWarnings("unchecked")
        List<Schedule> schedules = (List<Schedule>) response.getBody().get("schedules");

        assertThat(schedules).hasSize(2);
        assertThat(schedules).extracting(Schedule::getId).containsExactly(1L, 2L);
        assertThat(response.getBody().get("total")).isEqualTo(2);
        assertThat(response.getBody().get("startDate")).isEqualTo(startDate);
        assertThat(response.getBody().get("endDate")).isEqualTo(endDate);

        verify(scheduleService, times(1)).getAllSchedules(
                1, 1000, "startTime", "asc",
                startDate, null, null, null, null, 9L, "USER");
    }

    @Test
    @DisplayName("SCH-EXTRA-001: createSchedule - gán createdById từ SecurityUtil.extractEmployeeId")
    void createSchedule_setCreatedByFromSecurityContext() {
        Jwt jwt = Jwt.withTokenValue("token-value")
                .header("alg", "none")
                .claim("user", Map.of("employeeId", 123L))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        CreateScheduleDTO request = new CreateScheduleDTO();
        request.setTitle("Interview");
        request.setFormat("ONLINE");
        request.setMeetingType(MeetingType.INTERVIEW);
        request.setStartTime(LocalDateTime.of(2026, 4, 20, 9, 0));
        request.setEndTime(LocalDateTime.of(2026, 4, 20, 10, 0));

        Schedule schedule = new Schedule();
        schedule.setId(77L);
        when(scheduleService.createSchedule(request)).thenReturn(schedule);

        ResponseEntity<Schedule> response = scheduleController.createSchedule(request);

        ArgumentCaptor<CreateScheduleDTO> captor = ArgumentCaptor.forClass(CreateScheduleDTO.class);
        verify(scheduleService, times(1)).createSchedule(captor.capture());

        assertThat(captor.getValue().getCreatedById()).isEqualTo(123L);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(77L);
    }
}

