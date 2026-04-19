package com.example.schedule_service.service;

import com.example.schedule_service.dto.PaginationDTO;
import com.example.schedule_service.dto.schedule.AvailableParticipantDTO;
import com.example.schedule_service.dto.schedule.CreateScheduleDTO;
import com.example.schedule_service.dto.schedule.ScheduleDetailDTO;
import com.example.schedule_service.dto.schedule.ScheduleParticipantDTO;
import com.example.schedule_service.dto.schedule.ScheduleStatisticsDTO;
import com.example.schedule_service.messaging.NotificationProducer;
import com.example.schedule_service.model.Schedule;
import com.example.schedule_service.model.ScheduleParticipant;
import com.example.schedule_service.repository.ScheduleParticipantRepository;
import com.example.schedule_service.repository.ScheduleRepository;
import com.example.schedule_service.utils.SecurityUtil;
import com.example.schedule_service.utils.enums.MeetingType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduleService Unit Test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
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

    private CreateScheduleDTO baseCreateRequest;

    @BeforeEach
    void setUp() {
        baseCreateRequest = new CreateScheduleDTO();
        baseCreateRequest.setTitle("Tech Interview");
        baseCreateRequest.setDescription("Interview round 1");
        baseCreateRequest.setFormat("ONLINE");
        baseCreateRequest.setMeetingType(MeetingType.INTERVIEW);
        baseCreateRequest.setLocation("Room A");
        baseCreateRequest.setStartTime(LocalDateTime.of(2026, 4, 20, 9, 0));
        baseCreateRequest.setEndTime(LocalDateTime.of(2026, 4, 20, 10, 0));
        baseCreateRequest.setReminderTime(15);
        baseCreateRequest.setCreatedById(999L);
    }

    @Test
    @DisplayName("SCH-SVC-TC-001: createSchedule - tạo schedule thành công, set default status và build đủ participants")
    void testCreateSchedule_SCH_SVC_TC_001() {
        // Testcase ID: SCH-SVC-TC-001
        // Objective: Xác nhận tạo schedule thành công, set default status và build đủ
        // participants

        // arrange
        CreateScheduleDTO req = cloneBase();
        req.setStatus(null);
        req.setCandidateId(1L);
        req.setUserIds(List.of(10L, 11L));

        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
            mocked.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("jwt-token"));

            // act
            Schedule result = scheduleService.createSchedule(req);

            // assert
            assertNotNull(result);
            assertEquals("SCHEDULED", result.getStatus());
            assertNotNull(result.getParticipants());
            assertEquals(3, result.getParticipants().size());
            assertTrue(result.getParticipants().stream().anyMatch(
                    p -> "CANDIDATE".equalsIgnoreCase(p.getParticipantType()) && p.getParticipantId().equals(1L)));
            assertTrue(result.getParticipants().stream().anyMatch(
                    p -> "USER".equalsIgnoreCase(p.getParticipantType()) && p.getParticipantId().equals(10L)));
            assertTrue(result.getParticipants().stream().anyMatch(
                    p -> "USER".equalsIgnoreCase(p.getParticipantType()) && p.getParticipantId().equals(11L)));

            verify(scheduleRepository, times(2)).save(any(Schedule.class));
            verify(notificationProducer, times(1)).sendNotificationToMultiple(
                    eq(List.of(10L, 11L)),
                    eq("Bạn có lịch hẹn mới"),
                    contains("Bạn đã được mời tham gia:"),
                    eq("jwt-token"));
        }
    }

    @Test
    @DisplayName("SCH-SVC-TC-002: createSchedule - không gửi notification khi không có user tham gia")
    void testCreateSchedule_NoUserIds_NoNotification_SCH_SVC_TC_002() {
        // Testcase ID: SCH-SVC-TC-002
        // Objective: Xác nhận không gửi notification khi không có user tham gia

        // arrange
        CreateScheduleDTO req = cloneBase();
        req.setCandidateId(1L);
        req.setUserIds(null);
        req.setStatus(null);

        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // act
        Schedule result = scheduleService.createSchedule(req);

        // assert
        assertNotNull(result);
        assertEquals("SCHEDULED", result.getStatus());
        assertNotNull(result.getParticipants());
        assertEquals(1, result.getParticipants().size());
        assertTrue(result.getParticipants().stream().anyMatch(
                p -> "CANDIDATE".equalsIgnoreCase(p.getParticipantType()) && p.getParticipantId().equals(1L)));

        verify(notificationProducer, never()).sendNotificationToMultiple(anyList(), any(), any(), any());
        verify(scheduleRepository, times(2)).save(any(Schedule.class));
    }

    @Test
    @DisplayName("SCH-SVC-TC-003: updateSchedule - clear participants cũ và rebuild participants mới đúng")
    void testUpdateSchedule_RebuildParticipants_SCH_SVC_TC_003() {
        // Testcase ID: SCH-SVC-TC-003
        // Objective: Xác nhận clear participants cũ và rebuild participants mới đúng

        // arrange
        Long id = 1L;

        Schedule existing = new Schedule();
        existing.setId(id);
        existing.setParticipants(new HashSet<>());

        ScheduleParticipant oldUser = new ScheduleParticipant();
        oldUser.setId(900L);
        oldUser.setParticipantType("USER");
        oldUser.setParticipantId(99L);
        oldUser.setResponseStatus("PENDING");
        oldUser.setSchedule(existing);
        existing.getParticipants().add(oldUser);

        when(scheduleRepository.findById(id)).thenReturn(Optional.of(existing));
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateScheduleDTO req = cloneBase();
        req.setCandidateId(2L);
        req.setUserIds(List.of(20L, 21L));

        // act
        Schedule saved = scheduleService.updateSchedule(id, req);

        // assert
        assertNotNull(saved);
        assertNotNull(saved.getParticipants());
        assertEquals(3, saved.getParticipants().size());
        assertFalse(saved.getParticipants().stream().anyMatch(p -> p.getParticipantId().equals(99L)));

        assertTrue(saved.getParticipants().stream().anyMatch(
                p -> "CANDIDATE".equalsIgnoreCase(p.getParticipantType()) && p.getParticipantId().equals(2L)));
        assertTrue(saved.getParticipants().stream()
                .anyMatch(p -> "USER".equalsIgnoreCase(p.getParticipantType()) && p.getParticipantId().equals(20L)));
        assertTrue(saved.getParticipants().stream()
                .anyMatch(p -> "USER".equalsIgnoreCase(p.getParticipantType()) && p.getParticipantId().equals(21L)));

        verify(scheduleRepository, times(1)).findById(id);
        verify(scheduleRepository, times(1)).save(any(Schedule.class));
    }

    @Test
    @DisplayName("SCH-SVC-TC-004: updateSchedule - ném lỗi khi schedule không tồn tại")
    void testUpdateSchedule_NotFound_SCH_SVC_TC_004() {
        // Testcase ID: SCH-SVC-TC-004
        // Objective: Xác nhận lỗi khi cập nhật lịch không tồn tại

        // arrange
        when(scheduleRepository.findById(999L)).thenReturn(Optional.empty());
        CreateScheduleDTO req = cloneBase();

        // act
        RuntimeException ex = assertThrows(RuntimeException.class, () -> scheduleService.updateSchedule(999L, req));

        // assert
        assertEquals("lịch hẹn không tồn tại với id: 999", ex.getMessage());
        verify(scheduleRepository, times(1)).findById(999L);
        verify(scheduleRepository, never()).save(any());
        verify(notificationProducer, never()).sendNotificationToMultiple(anyList(), any(), any(), any());
    }

    @Test
    @DisplayName("SCH-SVC-TC-005: updateSchedule - gửi thông báo cập nhật cho participants")
    void testUpdateSchedule_SendNotification_SCH_SVC_TC_005() {
        // Testcase ID: SCH-SVC-TC-005
        // Objective: Xác nhận gửi thông báo cập nhật cho participants

        // arrange
        Long id = 1L;
        Schedule existing = new Schedule();
        existing.setId(id);
        existing.setParticipants(new HashSet<>());

        when(scheduleRepository.findById(id)).thenReturn(Optional.of(existing));
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateScheduleDTO req = cloneBase();
        req.setUserIds(List.of(10L, 11L));
        req.setCandidateId(null);

        try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
            mocked.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("jwt-token"));

            // act
            Schedule saved = scheduleService.updateSchedule(id, req);

            // assert
            assertNotNull(saved);
            verify(notificationProducer, times(1)).sendNotificationToMultiple(
                    eq(List.of(10L, 11L)),
                    eq("Lịch hẹn đã được cập nhật"),
                    contains("đã được cập nhật"),
                    eq("jwt-token"));
        }

        verify(scheduleRepository, times(1)).save(any(Schedule.class));
        verify(scheduleRepository, times(1)).findById(id);
    }

    @Test
    @DisplayName("SCH-SVC-TC-006: deleteSchedule - xóa lịch thành công khi tồn tại")
    void testDeleteSchedule_SCH_SVC_TC_006() {
        // Testcase ID: SCH-SVC-TC-006
        // Objective: Xác nhận xóa lịch thành công khi tồn tại

        // arrange
        when(scheduleRepository.existsById(1L)).thenReturn(true);

        // act
        scheduleService.deleteSchedule(1L);

        // assert
        verify(scheduleRepository, times(1)).existsById(1L);
        verify(scheduleRepository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("SCH-SVC-TC-007: deleteSchedule - ném lỗi khi xóa lịch không tồn tại")
    void testDeleteSchedule_NotFound_SCH_SVC_TC_007() {
        // Testcase ID: SCH-SVC-TC-007
        // Objective: Xác nhận lỗi khi xóa lịch không tồn tại

        // arrange
        when(scheduleRepository.existsById(999L)).thenReturn(false);

        // act
        RuntimeException ex = assertThrows(RuntimeException.class, () -> scheduleService.deleteSchedule(999L));

        // assert
        assertEquals("lịch hẹn không tồn tại với id: 999", ex.getMessage());
        verify(scheduleRepository, times(1)).existsById(999L);
        verify(scheduleRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("SCH-SVC-TC-008: getScheduleById - lấy đúng schedule theo ID")
    void testGetScheduleById_SCH_SVC_TC_008() {
        // Testcase ID: SCH-SVC-TC-008
        // Objective: Xác nhận lấy đúng schedule theo ID

        // arrange
        Schedule s = new Schedule();
        s.setId(1L);
        s.setTitle("Interview");
        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(s));

        // act
        Schedule result = scheduleService.getScheduleById(1L);

        // assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Interview", result.getTitle());
        verify(scheduleRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("SCH-SVC-TC-009: getScheduleById - ném lỗi khi schedule không tồn tại")
    void testGetScheduleById_NotFound_SCH_SVC_TC_009() {
        // Testcase ID: SCH-SVC-TC-009
        // Objective: Xác nhận lỗi khi schedule không tồn tại

        // arrange
        when(scheduleRepository.findById(999L)).thenReturn(Optional.empty());

        // act
        RuntimeException ex = assertThrows(RuntimeException.class, () -> scheduleService.getScheduleById(999L));

        // assert
        assertTrue(ex.getMessage().contains("lịch hẹn không tồn tại với id: 999"));
        verify(scheduleRepository, times(1)).findById(999L);
    }

    @Test
    @DisplayName("SCH-SVC-TC-010: getScheduleWithParticipantNames - enrich đúng tên participant vào detail DTO")
    void testGetScheduleWithParticipantNames_EnrichNames_SCH_SVC_TC_010() {
        // Testcase ID: SCH-SVC-TC-010
        // Objective: Xác nhận enrich đúng tên participant vào detail DTO

        // arrange
        Long scheduleId = 1L;
        String token = "token";

        Schedule schedule = new Schedule();
        schedule.setId(scheduleId);
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

        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));

        ObjectNode userMap = objectMapper.createObjectNode();
        userMap.put("101", "Interviewer A");
        when(userService.getEmployeeNames(List.of(101L), token)).thenReturn(ResponseEntity.ok((JsonNode) userMap));

        ObjectNode candidateMap = objectMapper.createObjectNode();
        candidateMap.put("202", "Candidate B");
        when(candidateService.getCandidateNames(List.of(202L), token))
                .thenReturn(ResponseEntity.ok((JsonNode) candidateMap));

        // act
        ScheduleDetailDTO dto = scheduleService.getScheduleWithParticipantNames(scheduleId, token);

        // assert
        assertNotNull(dto);
        assertEquals(scheduleId, dto.getId());
        assertNotNull(dto.getParticipants());
        assertEquals(2, dto.getParticipants().size());

        assertTrue(dto.getParticipants().stream()
                .anyMatch(p -> "USER".equalsIgnoreCase(p.getParticipantType()) && "Interviewer A".equals(p.getName())));
        assertTrue(dto.getParticipants().stream().anyMatch(
                p -> "CANDIDATE".equalsIgnoreCase(p.getParticipantType()) && "Candidate B".equals(p.getName())));

        verify(userService, times(1)).getEmployeeNames(List.of(101L), token);
        verify(candidateService, times(1)).getCandidateNames(List.of(202L), token);
    }

    @Test
    @DisplayName("SCH-SVC-TC-011: getScheduleWithParticipantNames - trả DTO hợp lệ khi không có participant")
    void testGetScheduleWithParticipantNames_EmptyParticipants_SCH_SVC_TC_011() {
        // Testcase ID: SCH-SVC-TC-011
        // Objective: Xác nhận trả DTO hợp lệ khi không có participant

        // arrange
        Long scheduleId = 1L;
        String token = "token";

        Schedule schedule = new Schedule();
        schedule.setId(scheduleId);
        schedule.setParticipants(new HashSet<>());

        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));

        // act
        ScheduleDetailDTO dto = scheduleService.getScheduleWithParticipantNames(scheduleId, token);

        // assert
        assertNotNull(dto);
        assertEquals(scheduleId, dto.getId());
        assertNotNull(dto.getParticipants());
        assertTrue(dto.getParticipants().isEmpty());

        verify(userService, never()).getEmployeeNames(anyList(), anyString());
        verify(candidateService, never()).getCandidateNames(anyList(), anyString());
    }

    @Test
    @DisplayName("SCH-SVC-TC-012: getAllSchedules - lấy tất cả schedule khi không truyền filter")
    void testGetAllSchedules_NoFilter_SCH_SVC_TC_012() {
        // Testcase ID: SCH-SVC-TC-012
        // Objective: Xác nhận lấy tất cả schedule khi không truyền filter

        // arrange
        Schedule s1 = new Schedule();
        s1.setId(1L);
        Schedule s2 = new Schedule();
        s2.setId(2L);

        Page<Schedule> page = new PageImpl<>(List.of(s1, s2));
        when(scheduleRepository.findAll(any(Pageable.class))).thenReturn(page);

        // act
        PaginationDTO dto = scheduleService.getAllSchedules(
                1, 10, "startTime", "desc",
                null, null, null, null, null,
                null, null);

        // assert
        assertNotNull(dto);
        assertNotNull(dto.getMeta());
        assertEquals(1, dto.getMeta().getPage());
        assertEquals(10, dto.getMeta().getPageSize());
        assertEquals(2, dto.getMeta().getTotal());

        assertNotNull(dto.getResult());
        @SuppressWarnings("unchecked")
        List<Schedule> result = (List<Schedule>) dto.getResult();
        assertEquals(2, result.size());

        verify(scheduleRepository, times(1)).findAll(any(Pageable.class));
        verify(scheduleRepository, never()).findByStartTimeBetween(any(), any(), any(Pageable.class));
        verify(scheduleRepository, never()).findByStatus(anyString(), any(Pageable.class));
        verify(scheduleRepository, never()).findByMeetingType(anyString(), any(Pageable.class));
    }

    @Test
    @DisplayName("SCH-SVC-TC-013: getAllSchedules - lọc theo ngày cụ thể")
    void testGetAllSchedules_FilterByDate_SCH_SVC_TC_013() {
        // Testcase ID: SCH-SVC-TC-013
        // Objective: Xác nhận lọc theo ngày cụ thể

        // arrange
        LocalDate date = LocalDate.of(2026, 4, 20);
        Page<Schedule> page = new PageImpl<>(List.of(new Schedule()));
        when(scheduleRepository.findByStartTimeBetween(any(LocalDateTime.class), any(LocalDateTime.class),
                any(Pageable.class)))
                .thenReturn(page);

        // act
        scheduleService.getAllSchedules(
                1, 10, "startTime", "desc",
                date, null, null, null, null,
                null, null);

        // assert
        ArgumentCaptor<LocalDateTime> startCap = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCap = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(scheduleRepository, times(1)).findByStartTimeBetween(startCap.capture(), endCap.capture(),
                any(Pageable.class));

        assertEquals(date.atStartOfDay(), startCap.getValue());
        assertEquals(date.atTime(LocalTime.MAX), endCap.getValue());
    }

    @Test
    @DisplayName("SCH-SVC-TC-014: getAllSchedules - lọc theo tháng/năm")
    void testGetAllSchedules_FilterByYearMonth_SCH_SVC_TC_014() {
        // Testcase ID: SCH-SVC-TC-014
        // Objective: Xác nhận lọc theo tháng/năm

        // arrange
        Page<Schedule> page = new PageImpl<>(List.of(new Schedule()));
        when(scheduleRepository.findByStartTimeBetween(any(LocalDateTime.class), any(LocalDateTime.class),
                any(Pageable.class)))
                .thenReturn(page);

        // act
        scheduleService.getAllSchedules(
                1, 10, "startTime", "desc",
                null, 2026, 4, null, null,
                null, null);

        // assert
        ArgumentCaptor<LocalDateTime> startCap = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCap = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(scheduleRepository, times(1)).findByStartTimeBetween(startCap.capture(), endCap.capture(),
                any(Pageable.class));

        LocalDate startOfMonth = LocalDate.of(2026, 4, 1);
        LocalDate endOfMonth = startOfMonth.withDayOfMonth(startOfMonth.lengthOfMonth());
        assertEquals(startOfMonth.atStartOfDay(), startCap.getValue());
        assertEquals(endOfMonth.atTime(LocalTime.MAX), endCap.getValue());
    }

    @Test
    @DisplayName("SCH-SVC-TC-015: getAllSchedules - lọc theo status")
    void testGetAllSchedules_FilterByStatus_SCH_SVC_TC_015() {
        // Testcase ID: SCH-SVC-TC-015
        // Objective: Xác nhận lọc theo status

        // arrange
        Page<Schedule> page = new PageImpl<>(List.of(new Schedule()));
        when(scheduleRepository.findByStatus(eq("SCHEDULED"), any(Pageable.class))).thenReturn(page);

        // act
        scheduleService.getAllSchedules(
                1, 10, "startTime", "desc",
                null, null, null, "SCHEDULED", null,
                null, null);

        // assert
        verify(scheduleRepository, times(1)).findByStatus(eq("SCHEDULED"), any(Pageable.class));
        verify(scheduleRepository, never()).findAll(any(Pageable.class));
        verify(scheduleRepository, never()).findByMeetingType(anyString(), any(Pageable.class));
    }

    @Test
    @DisplayName("SCH-SVC-TC-016: getAllSchedules - lọc theo meetingType")
    void testGetAllSchedules_FilterByMeetingType_SCH_SVC_TC_016() {
        // Testcase ID: SCH-SVC-TC-016
        // Objective: Xác nhận lọc theo meetingType

        // arrange
        Page<Schedule> page = new PageImpl<>(List.of(new Schedule()));
        when(scheduleRepository.findByMeetingType(eq("INTERVIEW"), any(Pageable.class))).thenReturn(page);

        // act
        scheduleService.getAllSchedules(
                1, 10, "startTime", "desc",
                null, null, null, null, "INTERVIEW",
                null, null);

        // assert
        verify(scheduleRepository, times(1)).findByMeetingType(eq("INTERVIEW"), any(Pageable.class));
        verify(scheduleRepository, never()).findAll(any(Pageable.class));
        verify(scheduleRepository, never()).findByStatus(anyString(), any(Pageable.class));
    }

    @Test
    @DisplayName("SCH-SVC-TC-017: getAllSchedules - lọc thêm theo participant trong memory")
    void testGetAllSchedules_FilterByParticipantInMemory_SCH_SVC_TC_017() {
        // Testcase ID: SCH-SVC-TC-017
        // Objective: Xác nhận lọc thêm theo participant trong memory

        // arrange
        Schedule withP = new Schedule();
        withP.setId(1L);
        ScheduleParticipant p = new ScheduleParticipant();
        p.setParticipantType("USER");
        p.setParticipantId(10L);
        withP.setParticipants(new HashSet<>(Set.of(p)));

        Schedule withoutP = new Schedule();
        withoutP.setId(2L);
        withoutP.setParticipants(new HashSet<>());

        Page<Schedule> page = new PageImpl<>(List.of(withP, withoutP));
        when(scheduleRepository.findAll(any(Pageable.class))).thenReturn(page);

        // act
        PaginationDTO dto = scheduleService.getAllSchedules(
                1, 10, "startTime", "desc",
                null, null, null, null, null,
                10L, "USER");

        // assert
        assertNotNull(dto);
        assertNotNull(dto.getResult());
        @SuppressWarnings("unchecked")
        List<Schedule> result = (List<Schedule>) dto.getResult();
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());

        verify(scheduleRepository, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("SCH-SVC-TC-018: getAllSchedules - normalize page/limit về giá trị hợp lệ")
    void testGetAllSchedules_NormalizePageLimit_SCH_SVC_TC_018() {
        // Testcase ID: SCH-SVC-TC-018
        // Objective: Xác nhận normalize page/limit về giá trị hợp lệ

        // arrange
        Page<Schedule> page = new PageImpl<>(List.of());
        when(scheduleRepository.findAll(any(Pageable.class))).thenReturn(page);

        // act
        PaginationDTO dto = scheduleService.getAllSchedules(
                0, 500, "startTime", "desc",
                null, null, null, null, null,
                null, null);

        // assert
        assertNotNull(dto);
        assertNotNull(dto.getMeta());
        assertEquals(1, dto.getMeta().getPage());
        assertEquals(10, dto.getMeta().getPageSize());

        verify(scheduleRepository, times(1)).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("SCH-SVC-TC-019: updateScheduleStatus - cập nhật status thành công")
    void testUpdateScheduleStatus_SCH_SVC_TC_019() {
        // Testcase ID: SCH-SVC-TC-019
        // Objective: Xác nhận cập nhật status thành công

        // arrange
        Schedule existing = new Schedule();
        existing.setId(1L);
        existing.setStatus("SCHEDULED");

        when(scheduleRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // act
        Schedule saved = scheduleService.updateScheduleStatus(1L, "DONE");

        // assert
        assertNotNull(saved);
        assertEquals("DONE", saved.getStatus());
        verify(scheduleRepository, times(1)).findById(1L);
        verify(scheduleRepository, times(1)).save(existing);
    }

    @Test
    @DisplayName("SCH-SVC-TC-020: updateScheduleStatus - ném lỗi khi schedule không tồn tại")
    void testUpdateScheduleStatus_NotFound_SCH_SVC_TC_020() {
        // Testcase ID: SCH-SVC-TC-020
        // Objective: Xác nhận lỗi khi schedule không tồn tại

        // arrange
        when(scheduleRepository.findById(999L)).thenReturn(Optional.empty());

        // act
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> scheduleService.updateScheduleStatus(999L, "DONE"));

        // assert
        assertTrue(ex.getMessage().contains("Lịch hẹn không tồn tại với id: 999"));
        verify(scheduleRepository, times(1)).findById(999L);
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("SCH-SVC-TC-021: getSchedulesDetailed - lọc theo khoảng ngày và enrich đúng participant names")
    void testGetSchedulesDetailed_FilterByDateRange_EnrichNames_SCH_SVC_TC_021() {
        // Testcase ID: SCH-SVC-TC-021
        // Objective: Xác nhận lọc theo khoảng ngày và enrich đúng participant names

        // arrange
        LocalDate startDate = LocalDate.of(2026, 4, 1);
        LocalDate endDate = LocalDate.of(2026, 4, 30);
        String token = "token";

        Schedule s = new Schedule();
        s.setId(1L);
        s.setTitle("Interview");
        s.setMeetingType(MeetingType.INTERVIEW);
        s.setStatus("SCHEDULED");

        ScheduleParticipant user = new ScheduleParticipant();
        user.setId(1L);
        user.setParticipantType("USER");
        user.setParticipantId(101L);
        user.setResponseStatus("PENDING");

        ScheduleParticipant cand = new ScheduleParticipant();
        cand.setId(2L);
        cand.setParticipantType("CANDIDATE");
        cand.setParticipantId(202L);
        cand.setResponseStatus("PENDING");

        s.setParticipants(new HashSet<>(Set.of(user, cand)));

        when(scheduleRepository.findByStartTimeBetween(
                eq(startDate.atStartOfDay()),
                eq(endDate.atTime(LocalTime.MAX)),
                any(Sort.class))).thenReturn(List.of(s));

        ObjectNode userMap = objectMapper.createObjectNode();
        userMap.put("101", "Interviewer A");
        when(userService.getEmployeeNames(List.of(101L), token)).thenReturn(ResponseEntity.ok((JsonNode) userMap));

        ObjectNode candMap = objectMapper.createObjectNode();
        candMap.put("202", "Candidate B");
        when(candidateService.getCandidateNames(List.of(202L), token))
                .thenReturn(ResponseEntity.ok((JsonNode) candMap));

        // act
        List<ScheduleDetailDTO> result = scheduleService.getSchedulesDetailed(
                null, null, null, null,
                null, null, null, null,
                token, startDate, endDate);

        // assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());

        List<ScheduleParticipantDTO> participants = result.get(0).getParticipants();
        assertNotNull(participants);
        assertEquals(2, participants.size());
        assertTrue(participants.stream()
                .anyMatch(p -> "USER".equalsIgnoreCase(p.getParticipantType()) && "Interviewer A".equals(p.getName())));
        assertTrue(participants.stream().anyMatch(
                p -> "CANDIDATE".equalsIgnoreCase(p.getParticipantType()) && "Candidate B".equals(p.getName())));

        verify(scheduleRepository, times(1)).findByStartTimeBetween(
                eq(startDate.atStartOfDay()),
                eq(endDate.atTime(LocalTime.MAX)),
                any(Sort.class));
        verify(userService, times(1)).getEmployeeNames(List.of(101L), token);
        verify(candidateService, times(1)).getCandidateNames(List.of(202L), token);
    }

    @Test
    @DisplayName("SCH-SVC-TC-022: getSchedulesDetailed - lọc theo ngày cụ thể")
    void testGetSchedulesDetailed_FilterByDay_SCH_SVC_TC_022() {
        // Testcase ID: SCH-SVC-TC-022
        // Objective: Xác nhận lọc theo ngày cụ thể

        // arrange
        LocalDate day = LocalDate.of(2026, 4, 20);
        when(scheduleRepository.findByStartTimeBetween(any(LocalDateTime.class), any(LocalDateTime.class),
                any(Sort.class)))
                .thenReturn(List.of(new Schedule()));

        // act
        scheduleService.getSchedulesDetailed(
                day, null, null, null,
                null, null, null, null,
                "token", null, null);

        // assert
        ArgumentCaptor<LocalDateTime> startCap = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCap = ArgumentCaptor.forClass(LocalDateTime.class);

        verify(scheduleRepository, times(1)).findByStartTimeBetween(startCap.capture(), endCap.capture(),
                any(Sort.class));
        assertEquals(day.atStartOfDay(), startCap.getValue());
        assertEquals(day.atTime(LocalTime.MAX), endCap.getValue());
    }

    @Test
    @DisplayName("SCH-SVC-TC-023: getSchedulesDetailed - lọc theo tuần ISO")
    void testGetSchedulesDetailed_FilterByWeekIso_SCH_SVC_TC_023() {
        // Testcase ID: SCH-SVC-TC-023
        // Objective: Xác nhận lọc theo tuần ISO

        // arrange
        int week = 16;
        int year = 2026;

        when(scheduleRepository.findByStartTimeBetween(any(LocalDateTime.class), any(LocalDateTime.class),
                any(Sort.class)))
                .thenReturn(List.of(new Schedule()));

        // act
        scheduleService.getSchedulesDetailed(
                null, week, null, year,
                null, null, null, null,
                "token", null, null);

        // assert
        LocalDate start = LocalDate.ofYearDay(year, 1).with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week);
        LocalDate end = start.plusDays(6);

        ArgumentCaptor<LocalDateTime> startCap = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCap = ArgumentCaptor.forClass(LocalDateTime.class);

        verify(scheduleRepository, times(1)).findByStartTimeBetween(startCap.capture(), endCap.capture(),
                any(Sort.class));
        assertEquals(start.atStartOfDay(), startCap.getValue());
        assertEquals(end.atTime(LocalTime.MAX), endCap.getValue());
    }

    @Test
    @DisplayName("SCH-SVC-TC-024: getSchedulesDetailed - lọc theo tháng")
    void testGetSchedulesDetailed_FilterByMonthYear_SCH_SVC_TC_024() {
        // Testcase ID: SCH-SVC-TC-024
        // Objective: Xác nhận lọc theo tháng

        // arrange
        int month = 4;
        int year = 2026;

        when(scheduleRepository.findByStartTimeBetween(any(LocalDateTime.class), any(LocalDateTime.class),
                any(Sort.class)))
                .thenReturn(List.of(new Schedule()));

        // act
        scheduleService.getSchedulesDetailed(
                null, null, month, year,
                null, null, null, null,
                "token", null, null);

        // assert
        LocalDate first = LocalDate.of(year, month, 1);
        LocalDate last = first.withDayOfMonth(first.lengthOfMonth());

        ArgumentCaptor<LocalDateTime> startCap = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCap = ArgumentCaptor.forClass(LocalDateTime.class);

        verify(scheduleRepository, times(1)).findByStartTimeBetween(startCap.capture(), endCap.capture(),
                any(Sort.class));
        assertEquals(first.atStartOfDay(), startCap.getValue());
        assertEquals(last.atTime(LocalTime.MAX), endCap.getValue());
    }

    @Test
    @DisplayName("SCH-SVC-TC-025: getSchedulesDetailed - lọc theo năm")
    void testGetSchedulesDetailed_FilterByYear_SCH_SVC_TC_025() {
        // Testcase ID: SCH-SVC-TC-025
        // Objective: Xác nhận lọc theo năm

        // arrange
        int year = 2026;
        when(scheduleRepository.findByStartTimeBetween(any(LocalDateTime.class), any(LocalDateTime.class),
                any(Sort.class)))
                .thenReturn(List.of(new Schedule()));

        // act
        scheduleService.getSchedulesDetailed(
                null, null, null, year,
                null, null, null, null,
                "token", null, null);

        // assert
        LocalDate first = LocalDate.of(year, 1, 1);
        LocalDate last = LocalDate.of(year, 12, 31);

        ArgumentCaptor<LocalDateTime> startCap = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCap = ArgumentCaptor.forClass(LocalDateTime.class);

        verify(scheduleRepository, times(1)).findByStartTimeBetween(startCap.capture(), endCap.capture(),
                any(Sort.class));
        assertEquals(first.atStartOfDay(), startCap.getValue());
        assertEquals(last.atTime(LocalTime.MAX), endCap.getValue());
    }

    @Test
    @DisplayName("SCH-SVC-TC-026: getSchedulesDetailed - filter hậu xử lý theo status, meetingType, participant")
    void testGetSchedulesDetailed_PostFilter_StatusMeetingTypeParticipant_SCH_SVC_TC_026() {
        // Testcase ID: SCH-SVC-TC-026
        // Objective: Xác nhận filter hậu xử lý theo status, meetingType, participant

        // arrange
        String token = "token";

        Schedule match = new Schedule();
        match.setId(1L);
        match.setStatus("DONE");
        match.setMeetingType(MeetingType.INTERVIEW);
        ScheduleParticipant p = new ScheduleParticipant();
        p.setParticipantType("USER");
        p.setParticipantId(10L);
        match.setParticipants(new HashSet<>(Set.of(p)));

        Schedule wrongStatus = new Schedule();
        wrongStatus.setId(2L);
        wrongStatus.setStatus("SCHEDULED");
        wrongStatus.setMeetingType(MeetingType.INTERVIEW);
        wrongStatus.setParticipants(new HashSet<>(Set.of(p)));

        Schedule wrongMeetingType = new Schedule();
        wrongMeetingType.setId(3L);
        wrongMeetingType.setStatus("DONE");
        wrongMeetingType.setMeetingType(MeetingType.MEETING);
        wrongMeetingType.setParticipants(new HashSet<>(Set.of(p)));

        Schedule wrongParticipant = new Schedule();
        wrongParticipant.setId(4L);
        wrongParticipant.setStatus("DONE");
        wrongParticipant.setMeetingType(MeetingType.INTERVIEW);
        ScheduleParticipant p2 = new ScheduleParticipant();
        p2.setParticipantType("USER");
        p2.setParticipantId(999L);
        wrongParticipant.setParticipants(new HashSet<>(Set.of(p2)));

        when(scheduleRepository.findAll(any(Sort.class)))
                .thenReturn(List.of(match, wrongStatus, wrongMeetingType, wrongParticipant));

        // Stub enrich calls to avoid NPE when service tries to read getStatusCode()
        when(userService.getEmployeeNames(anyList(), eq(token)))
                .thenReturn(ResponseEntity.ok(objectMapper.createObjectNode()));

        // act
        List<ScheduleDetailDTO> result = scheduleService.getSchedulesDetailed(
                null, null, null, null,
                "DONE", "INTERVIEW", 10L, "USER",
                token, null, null);

        // assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());

        verify(scheduleRepository, times(1)).findAll(any(Sort.class));
    }

    @Test
    @DisplayName("SCH-SVC-TC-027: getAvailableParticipants - loại bỏ người bận và map đúng tên/phòng ban")
    void testGetAvailableParticipants_FilterBusyAndMap_SCH_SVC_TC_027() {
        // Testcase ID: SCH-SVC-TC-027
        // Objective: Xác nhận loại bỏ người bận và map đúng tên/phòng ban

        // arrange
        LocalDateTime start = LocalDateTime.of(2026, 4, 20, 9, 0);
        LocalDateTime end = LocalDateTime.of(2026, 4, 20, 10, 0);
        String token = "token";

        when(userService.getAllEmployeeIds(token)).thenReturn(List.of(1L, 2L, 3L));

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

        when(userService.getEmployeeNamesAndDepartmentNames(List.of(1L, 3L), token))
                .thenReturn(ResponseEntity.ok((JsonNode) body));

        // act
        List<AvailableParticipantDTO> result = scheduleService.getAvailableParticipants(start, end, null, token);

        // assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals(3L, result.get(1).getId());
        assertEquals("Alice", result.get(0).getName());
        assertEquals("HR", result.get(0).getDepartmentName());
        assertEquals("Bob", result.get(1).getName());
        assertEquals("IT", result.get(1).getDepartmentName());

        verify(userService, times(1)).getAllEmployeeIds(token);
        verify(scheduleRepository, times(1)).findOverlappingSchedules(start, end, null);
        verify(scheduleParticipantRepository, times(1)).findParticipantIdsByScheduleIds(List.of(100L));
        verify(userService, times(1)).getEmployeeNamesAndDepartmentNames(List.of(1L, 3L), token);
    }

    @Test
    @DisplayName("SCH-SVC-TC-028: getAvailableParticipants - trả rỗng khi không có employee nào")
    void testGetAvailableParticipants_AllEmployeeIdsEmpty_SCH_SVC_TC_028() {
        // Testcase ID: SCH-SVC-TC-028
        // Objective: Xác nhận trả rỗng khi không có employee nào

        // arrange
        LocalDateTime start = LocalDateTime.of(2026, 4, 20, 9, 0);
        LocalDateTime end = LocalDateTime.of(2026, 4, 20, 10, 0);
        String token = "token";

        when(userService.getAllEmployeeIds(token)).thenReturn(List.of());

        // act
        List<AvailableParticipantDTO> result = scheduleService.getAvailableParticipants(start, end, null, token);

        // assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(userService, times(1)).getAllEmployeeIds(token);
        verify(scheduleRepository, never()).findOverlappingSchedules(any(), any(), any());
        verify(scheduleParticipantRepository, never()).findParticipantIdsByScheduleIds(anyList());
        verify(userService, never()).getEmployeeNamesAndDepartmentNames(anyList(), anyString());
    }

    @Test
    @DisplayName("SCH-SVC-TC-029: getAvailableParticipants - trả rỗng khi không còn ai khả dụng")
    void testGetAvailableParticipants_AllBusy_ReturnEmpty_SCH_SVC_TC_029() {
        // Testcase ID: SCH-SVC-TC-029
        // Objective: Xác nhận trả rỗng khi không còn ai khả dụng

        // arrange
        LocalDateTime start = LocalDateTime.of(2026, 4, 20, 9, 0);
        LocalDateTime end = LocalDateTime.of(2026, 4, 20, 10, 0);
        String token = "token";

        when(userService.getAllEmployeeIds(token)).thenReturn(List.of(1L, 2L));

        Schedule overlapping = new Schedule();
        overlapping.setId(100L);
        when(scheduleRepository.findOverlappingSchedules(start, end, null)).thenReturn(List.of(overlapping));
        when(scheduleParticipantRepository.findParticipantIdsByScheduleIds(List.of(100L))).thenReturn(List.of(1L, 2L));

        // act
        List<AvailableParticipantDTO> result = scheduleService.getAvailableParticipants(start, end, null, token);

        // assert
        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(userService, times(1)).getAllEmployeeIds(token);
        verify(scheduleRepository, times(1)).findOverlappingSchedules(start, end, null);
        verify(scheduleParticipantRepository, times(1)).findParticipantIdsByScheduleIds(List.of(100L));
        verify(userService, never()).getEmployeeNamesAndDepartmentNames(anyList(), anyString());
    }

    @Test
    @DisplayName("SCH-SVC-TC-030: getSchedulesForStatistics - lọc theo khoảng ngày")
    void testGetSchedulesForStatistics_FilterByDateRange_SCH_SVC_TC_030() {
        // Testcase ID: SCH-SVC-TC-030
        // Objective: Xác nhận lọc theo khoảng ngày

        // arrange
        LocalDate startDate = LocalDate.of(2026, 4, 1);
        LocalDate endDate = LocalDate.of(2026, 4, 30);

        Schedule s1 = new Schedule();
        s1.setId(1L);
        s1.setTitle("A");
        s1.setStatus("SCHEDULED");
        s1.setMeetingType(MeetingType.INTERVIEW);
        s1.setStartTime(LocalDateTime.of(2026, 4, 10, 9, 0));

        Schedule s2 = new Schedule();
        s2.setId(2L);
        s2.setTitle("B");
        s2.setStatus("DONE");
        s2.setMeetingType(MeetingType.MEETING);
        s2.setStartTime(LocalDateTime.of(2026, 4, 11, 9, 0));

        when(scheduleRepository.findByStartTimeBetween(
                eq(startDate.atStartOfDay()),
                eq(endDate.atTime(LocalTime.MAX)),
                any(Sort.class))).thenReturn(List.of(s1, s2));

        // act
        List<ScheduleStatisticsDTO> result = scheduleService.getSchedulesForStatistics(startDate, endDate, null, null);

        // assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals("SCHEDULED", result.get(0).getStatus());
        assertEquals("INTERVIEW", result.get(0).getMeetingType());
        assertEquals("A", result.get(0).getTitle());

        verify(scheduleRepository, times(1)).findByStartTimeBetween(
                eq(startDate.atStartOfDay()),
                eq(endDate.atTime(LocalTime.MAX)),
                any(Sort.class));
    }

    @Test
    @DisplayName("SCH-SVC-TC-031: getSchedulesForStatistics - filter kết hợp date range + status + meetingType")
    void testGetSchedulesForStatistics_FilterDateRangeStatusMeetingType_SCH_SVC_TC_031() {
        // Testcase ID: SCH-SVC-TC-031
        // Objective: Xác nhận filter kết hợp date range + status + meetingType

        // arrange
        LocalDate startDate = LocalDate.of(2026, 4, 1);
        LocalDate endDate = LocalDate.of(2026, 4, 30);

        Schedule match = new Schedule();
        match.setId(1L);
        match.setStatus("DONE");
        match.setMeetingType(MeetingType.INTERVIEW);
        match.setStartTime(LocalDateTime.of(2026, 4, 10, 9, 0));

        Schedule wrongStatus = new Schedule();
        wrongStatus.setId(2L);
        wrongStatus.setStatus("SCHEDULED");
        wrongStatus.setMeetingType(MeetingType.INTERVIEW);
        wrongStatus.setStartTime(LocalDateTime.of(2026, 4, 10, 9, 0));

        Schedule wrongMeetingType = new Schedule();
        wrongMeetingType.setId(3L);
        wrongMeetingType.setStatus("DONE");
        wrongMeetingType.setMeetingType(MeetingType.MEETING);
        wrongMeetingType.setStartTime(LocalDateTime.of(2026, 4, 10, 9, 0));

        when(scheduleRepository.findByStartTimeBetween(
                eq(startDate.atStartOfDay()),
                eq(endDate.atTime(LocalTime.MAX)),
                any(Sort.class))).thenReturn(List.of(match, wrongStatus, wrongMeetingType));

        // act
        List<ScheduleStatisticsDTO> result = scheduleService.getSchedulesForStatistics(startDate, endDate, "DONE",
                "INTERVIEW");

        // assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals("DONE", result.get(0).getStatus());
        assertEquals("INTERVIEW", result.get(0).getMeetingType());

        verify(scheduleRepository, times(1)).findByStartTimeBetween(
                eq(startDate.atStartOfDay()),
                eq(endDate.atTime(LocalTime.MAX)),
                any(Sort.class));
    }

    @Test
    @DisplayName("SCH-SVC-TC-032: getSchedulesForStatistics - filter theo status")
    void testGetSchedulesForStatistics_FilterByStatus_SCH_SVC_TC_032() {
        // Testcase ID: SCH-SVC-TC-032
        // Objective: Xác nhận filter theo status

        // arrange
        Schedule s1 = new Schedule();
        s1.setId(1L);
        s1.setStatus("SCHEDULED");
        s1.setMeetingType(MeetingType.INTERVIEW);
        s1.setStartTime(LocalDateTime.of(2026, 4, 10, 9, 0));

        Page<Schedule> page = new PageImpl<>(List.of(s1));
        when(scheduleRepository.findByStatus(eq("SCHEDULED"), any(Pageable.class))).thenReturn(page);

        // act
        List<ScheduleStatisticsDTO> result = scheduleService.getSchedulesForStatistics(null, null, "SCHEDULED", null);

        // assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("SCHEDULED", result.get(0).getStatus());

        verify(scheduleRepository, times(1)).findByStatus(eq("SCHEDULED"), any(Pageable.class));
        verify(scheduleRepository, never()).findByMeetingType(anyString(), any(Pageable.class));
    }

    @Test
    @DisplayName("SCH-SVC-TC-033: getSchedulesForStatistics - filter theo meetingType")
    void testGetSchedulesForStatistics_FilterByMeetingType_SCH_SVC_TC_033() {
        // Testcase ID: SCH-SVC-TC-033
        // Objective: Xác nhận filter theo meetingType

        // arrange
        Schedule s1 = new Schedule();
        s1.setId(1L);
        s1.setStatus("SCHEDULED");
        s1.setMeetingType(MeetingType.INTERVIEW);
        s1.setTitle("T");
        s1.setStartTime(LocalDateTime.of(2026, 4, 10, 9, 0));

        Page<Schedule> page = new PageImpl<>(List.of(s1));
        when(scheduleRepository.findByMeetingType(eq("INTERVIEW"), any(Pageable.class))).thenReturn(page);

        // act
        List<ScheduleStatisticsDTO> result = scheduleService.getSchedulesForStatistics(null, null, null, "INTERVIEW");

        // assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals("INTERVIEW", result.get(0).getMeetingType());
        assertEquals("T", result.get(0).getTitle());

        verify(scheduleRepository, times(1)).findByMeetingType(eq("INTERVIEW"), any(Pageable.class));
        verify(scheduleRepository, never()).findByStatus(anyString(), any(Pageable.class));
    }

    @Test
    @DisplayName("SCH-SVC-TC-034: getSchedulesForStatistics - giới hạn tối đa 10000 records")
    void testGetSchedulesForStatistics_Limit10000_SCH_SVC_TC_034() {
        // Testcase ID: SCH-SVC-TC-034
        // Objective: Xác nhận giới hạn tối đa 10000 records

        // arrange
        List<Schedule> schedules = new ArrayList<>();
        for (int i = 1; i <= 10001; i++) {
            Schedule s = new Schedule();
            s.setId((long) i);
            s.setStatus("SCHEDULED");
            s.setStartTime(LocalDateTime.of(2026, 4, 1, 0, 0).plusMinutes(i));
            schedules.add(s);
        }
        when(scheduleRepository.findAll(any(Sort.class))).thenReturn(schedules);

        // act
        List<ScheduleStatisticsDTO> result = scheduleService.getSchedulesForStatistics(null, null, null, null);

        // assert
        assertNotNull(result);
        assertEquals(10000, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals(10000L, result.get(result.size() - 1).getId());

        verify(scheduleRepository, times(1)).findAll(any(Sort.class));
    }

    @Test
    @DisplayName("SCH-SVC-TC-035: getCandidateIdsByInterviewer - lấy đúng danh sách candidateIds theo interviewer")
    void testGetCandidateIdsByInterviewer_SCH_SVC_TC_035() {
        // Testcase ID: SCH-SVC-TC-035
        // Objective: Xác nhận lấy đúng danh sách candidateIds theo interviewer

        // arrange
        when(scheduleParticipantRepository.findCandidateIdsByInterviewer(10L)).thenReturn(List.of(1L, 2L, 3L));

        // act
        List<Long> result = scheduleService.getCandidateIdsByInterviewer(10L);

        // assert
        assertNotNull(result);
        assertEquals(List.of(1L, 2L, 3L), result);
        verify(scheduleParticipantRepository, times(1)).findCandidateIdsByInterviewer(10L);
    }

    private CreateScheduleDTO cloneBase() {
        CreateScheduleDTO req = new CreateScheduleDTO();
        req.setTitle(baseCreateRequest.getTitle());
        req.setDescription(baseCreateRequest.getDescription());
        req.setFormat(baseCreateRequest.getFormat());
        req.setMeetingType(baseCreateRequest.getMeetingType());
        req.setLocation(baseCreateRequest.getLocation());
        req.setStartTime(baseCreateRequest.getStartTime());
        req.setEndTime(baseCreateRequest.getEndTime());
        req.setReminderTime(baseCreateRequest.getReminderTime());
        req.setCreatedById(baseCreateRequest.getCreatedById());
        return req;
    }
}