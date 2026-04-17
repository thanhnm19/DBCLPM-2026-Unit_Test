package com.example.schedule_service.service;

import com.example.schedule_service.dto.schedule.AvailableParticipantDTO;
import com.example.schedule_service.dto.schedule.CreateScheduleDTO;
import com.example.schedule_service.dto.schedule.ScheduleDetailDTO;
import com.example.schedule_service.messaging.NotificationProducer;
import com.example.schedule_service.model.Schedule;
import com.example.schedule_service.model.ScheduleParticipant;
import com.example.schedule_service.repository.ScheduleParticipantRepository;
import com.example.schedule_service.repository.ScheduleRepository;
import com.example.schedule_service.utils.enums.MeetingType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleService Unit Test")
class ScheduleServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ScheduleParticipantRepository scheduleParticipantRepository;

    @Mock
    private UserService userService;

    @Mock
    private CandidateService candidateService;

    @Mock
    private NotificationProducer notificationProducer;

    @InjectMocks
    private ScheduleService scheduleService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("COM-TC-008: createSchedule - build participants đúng và gán default status SCHEDULED")
    void comTc008_createSchedule_buildParticipantsAndDefaultStatus() {
        CreateScheduleDTO request = baseCreateRequest();
        request.setStatus(null);
        request.setCandidateId(200L);
        request.setUserIds(List.of(11L, 12L));

        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Schedule result = scheduleService.createSchedule(request);

        assertThat(result.getStatus()).isEqualTo("SCHEDULED");
        assertThat(result.getParticipants()).hasSize(3);
        assertThat(result.getParticipants())
                .extracting(ScheduleParticipant::getParticipantType, ScheduleParticipant::getParticipantId)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("CANDIDATE", 200L),
                        org.assertj.core.groups.Tuple.tuple("USER", 11L),
                        org.assertj.core.groups.Tuple.tuple("USER", 12L));

        ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
        verify(scheduleRepository, times(2)).save(scheduleCaptor.capture());
        Schedule savedSecondTime = scheduleCaptor.getAllValues().get(1);
        assertThat(savedSecondTime.getParticipants()).hasSize(3);

        verify(notificationProducer, times(1)).sendNotificationToMultiple(
                eq(List.of(11L, 12L)),
                eq("Bạn có lịch hẹn mới"),
                eq("Bạn đã được mời tham gia: Tech Interview vào 2026-04-20T09:00 tại Room A."),
                eq(null));
    }

    @Test
    @DisplayName("COM-EXTRA-001: createSchedule - candidateId null và userIds rỗng không tạo participant")
    void createSchedule_candidateNullAndEmptyUsers_noParticipants() {
        CreateScheduleDTO request = baseCreateRequest();
        request.setCandidateId(null);
        request.setUserIds(List.of());

        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Schedule result = scheduleService.createSchedule(request);

        assertThat(result.getParticipants()).isEmpty();
        assertThat(result.getStatus()).isEqualTo("SCHEDULED");
        verify(notificationProducer, never()).sendNotificationToMultiple(anyList(), any(), any(), any());
    }

    @Test
    @DisplayName("COM-TC-009: getAvailableParticipants - lọc đúng người rảnh và enrich name/department")
    void comTc009_getAvailableParticipants_filterAndEnrich() {
        LocalDateTime start = LocalDateTime.of(2026, 4, 20, 9, 0);
        LocalDateTime end = LocalDateTime.of(2026, 4, 20, 10, 0);

        when(userService.getAllEmployeeIds("token")).thenReturn(List.of(1L, 2L, 3L));

        Schedule overlapping = new Schedule();
        overlapping.setId(100L);
        when(scheduleRepository.findOverlappingSchedules(start, end, null)).thenReturn(List.of(overlapping));
        when(scheduleParticipantRepository.findParticipantIdsByScheduleIds(List.of(100L))).thenReturn(List.of(2L));

        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode user1 = objectMapper.createObjectNode();
        user1.put("name", "Alice");
        user1.put("departmentName", "HR");
        ObjectNode user3 = objectMapper.createObjectNode();
        user3.put("name", "Bob");
        user3.put("departmentName", "IT");
        body.set("1", user1);
        body.set("3", user3);

        when(userService.getEmployeeNamesAndDepartmentNames(List.of(1L, 3L), "token"))
                .thenReturn(ResponseEntity.ok((JsonNode) body));

        List<AvailableParticipantDTO> result = scheduleService.getAvailableParticipants(start, end, null, "token");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(AvailableParticipantDTO::getId).containsExactly(1L, 3L);
        assertThat(result).extracting(AvailableParticipantDTO::getName).containsExactly("Alice", "Bob");
        assertThat(result).extracting(AvailableParticipantDTO::getDepartmentName).containsExactly("HR", "IT");

        verify(userService, times(1)).getAllEmployeeIds("token");
        verify(scheduleRepository, times(1)).findOverlappingSchedules(start, end, null);
        verify(scheduleParticipantRepository, times(1)).findParticipantIdsByScheduleIds(List.of(100L));
    }

    @Test
    @DisplayName("COM-EXTRA-002: getAvailableParticipants - không có busy participant trả tất cả employee")
    void getAvailableParticipants_noBusyParticipants_returnAll() {
        LocalDateTime start = LocalDateTime.of(2026, 4, 20, 13, 0);
        LocalDateTime end = LocalDateTime.of(2026, 4, 20, 14, 0);

        when(userService.getAllEmployeeIds("token")).thenReturn(List.of(1L, 2L));
        when(scheduleRepository.findOverlappingSchedules(start, end, null)).thenReturn(List.of());

        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode user1 = objectMapper.createObjectNode();
        user1.put("name", "A");
        user1.put("departmentName", "D1");
        ObjectNode user2 = objectMapper.createObjectNode();
        user2.put("name", "B");
        user2.put("departmentName", "D2");
        body.set("1", user1);
        body.set("2", user2);

        when(userService.getEmployeeNamesAndDepartmentNames(List.of(1L, 2L), "token"))
                .thenReturn(ResponseEntity.ok((JsonNode) body));

        List<AvailableParticipantDTO> result = scheduleService.getAvailableParticipants(start, end, null, "token");

        assertThat(result).extracting(AvailableParticipantDTO::getId).containsExactly(1L, 2L);
        verify(scheduleParticipantRepository, never()).findParticipantIdsByScheduleIds(anyList());
    }

    @Test
    @DisplayName("COM-EXTRA-003: getAvailableParticipants - enrich thiếu name/department thì fallback Unknown")
    void getAvailableParticipants_missingNameDepartment_fallbackUnknown() {
        LocalDateTime start = LocalDateTime.of(2026, 4, 20, 15, 0);
        LocalDateTime end = LocalDateTime.of(2026, 4, 20, 16, 0);

        when(userService.getAllEmployeeIds("token")).thenReturn(List.of(7L));
        when(scheduleRepository.findOverlappingSchedules(start, end, null)).thenReturn(List.of());

        ObjectNode body = objectMapper.createObjectNode();
        body.set("7", objectMapper.createObjectNode());

        when(userService.getEmployeeNamesAndDepartmentNames(List.of(7L), "token"))
                .thenReturn(ResponseEntity.ok((JsonNode) body));

        List<AvailableParticipantDTO> result = scheduleService.getAvailableParticipants(start, end, null, "token");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Unknown");
        assertThat(result.get(0).getDepartmentName()).isEqualTo("Unknown");
    }

    @Test
    @DisplayName("COM-TC-010: getSchedulesDetailed - map participantName đúng cho USER và CANDIDATE")
    void comTc010_getSchedulesDetailed_mapParticipantNames() {
        Schedule schedule = new Schedule();
        schedule.setId(10L);
        schedule.setTitle("Interview");
        schedule.setMeetingType(MeetingType.INTERVIEW);
        schedule.setStatus("SCHEDULED");

        ScheduleParticipant user = new ScheduleParticipant();
        user.setId(1L);
        user.setParticipantType("USER");
        user.setParticipantId(101L);
        user.setResponseStatus("PENDING");

        ScheduleParticipant candidate = new ScheduleParticipant();
        candidate.setId(2L);
        candidate.setParticipantType("CANDIDATE");
        candidate.setParticipantId(202L);
        candidate.setResponseStatus("PENDING");

        schedule.setParticipants(new HashSet<>(Set.of(user, candidate)));

        when(scheduleRepository.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(schedule));

        ObjectNode userMap = objectMapper.createObjectNode();
        userMap.put("101", "Interviewer A");
        when(userService.getEmployeeNames(List.of(101L), "token")).thenReturn(ResponseEntity.ok((JsonNode) userMap));

        ObjectNode candidateMap = objectMapper.createObjectNode();
        candidateMap.put("202", "Candidate B");
        when(candidateService.getCandidateNames(List.of(202L), "token"))
                .thenReturn(ResponseEntity.ok((JsonNode) candidateMap));

        List<ScheduleDetailDTO> result = scheduleService.getSchedulesDetailed(
                null, null, null, null,
                null, null, null, null,
                "token", null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getParticipants())
                .extracting(p -> p.getParticipantType() + ":" + p.getName())
                .containsExactlyInAnyOrder("USER:Interviewer A", "CANDIDATE:Candidate B");

        verify(userService, times(1)).getEmployeeNames(List.of(101L), "token");
        verify(candidateService, times(1)).getCandidateNames(List.of(202L), "token");
    }

    @Test
    @DisplayName("COM-EXTRA-004: getSchedulesDetailed - participant list rỗng không gọi enrich service")
    void getSchedulesDetailed_emptyParticipants_skipEnrich() {
        Schedule schedule = new Schedule();
        schedule.setId(88L);
        schedule.setParticipants(new HashSet<>());

        when(scheduleRepository.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(schedule));

        List<ScheduleDetailDTO> result = scheduleService.getSchedulesDetailed(
                null, null, null, null,
                null, null, null, null,
                "token", null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getParticipants()).isEmpty();

        verify(userService, never()).getEmployeeNames(anyList(), any());
        verify(candidateService, never()).getCandidateNames(anyList(), any());
    }

    @Test
    @DisplayName("COM-EXTRA-005: getScheduleById - not found thì throw RuntimeException")
    void getScheduleById_notFound_throwException() {
        when(scheduleRepository.findById(999L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> scheduleService.getScheduleById(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("lịch hẹn không tồn tại với id: 999");
    }

    private CreateScheduleDTO baseCreateRequest() {
        CreateScheduleDTO request = new CreateScheduleDTO();
        request.setTitle("Tech Interview");
        request.setDescription("Interview round 1");
        request.setFormat("ONLINE");
        request.setMeetingType(MeetingType.INTERVIEW);
        request.setLocation("Room A");
        request.setStartTime(LocalDateTime.of(2026, 4, 20, 9, 0));
        request.setEndTime(LocalDateTime.of(2026, 4, 20, 10, 0));
        request.setReminderTime(15);
        request.setCreatedById(999L);
        return request;
    }
}

