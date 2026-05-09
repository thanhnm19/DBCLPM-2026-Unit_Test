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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.lang.reflect.Method;

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
                                        p -> "CANDIDATE".equalsIgnoreCase(p.getParticipantType())
                                                        && p.getParticipantId().equals(1L)));
                        assertTrue(result.getParticipants().stream().anyMatch(
                                        p -> "USER".equalsIgnoreCase(p.getParticipantType())
                                                        && p.getParticipantId().equals(10L)));
                        assertTrue(result.getParticipants().stream().anyMatch(
                                        p -> "USER".equalsIgnoreCase(p.getParticipantType())
                                                        && p.getParticipantId().equals(11L)));

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
                                p -> "CANDIDATE".equalsIgnoreCase(p.getParticipantType())
                                                && p.getParticipantId().equals(1L)));

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
                                p -> "CANDIDATE".equalsIgnoreCase(p.getParticipantType())
                                                && p.getParticipantId().equals(2L)));
                assertTrue(saved.getParticipants().stream()
                                .anyMatch(p -> "USER".equalsIgnoreCase(p.getParticipantType())
                                                && p.getParticipantId().equals(20L)));
                assertTrue(saved.getParticipants().stream()
                                .anyMatch(p -> "USER".equalsIgnoreCase(p.getParticipantType())
                                                && p.getParticipantId().equals(21L)));

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
                RuntimeException ex = assertThrows(RuntimeException.class,
                                () -> scheduleService.updateSchedule(999L, req));

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
                when(userService.getEmployeeNames(List.of(101L), token))
                                .thenReturn(ResponseEntity.ok((JsonNode) userMap));

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
                                .anyMatch(p -> "USER".equalsIgnoreCase(p.getParticipantType())
                                                && "Interviewer A".equals(p.getName())));
                assertTrue(dto.getParticipants().stream().anyMatch(
                                p -> "CANDIDATE".equalsIgnoreCase(p.getParticipantType())
                                                && "Candidate B".equals(p.getName())));

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
                when(userService.getEmployeeNames(List.of(101L), token))
                                .thenReturn(ResponseEntity.ok((JsonNode) userMap));

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
                                .anyMatch(p -> "USER".equalsIgnoreCase(p.getParticipantType())
                                                && "Interviewer A".equals(p.getName())));
                assertTrue(participants.stream().anyMatch(
                                p -> "CANDIDATE".equalsIgnoreCase(p.getParticipantType())
                                                && "Candidate B".equals(p.getName())));

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
                when(scheduleParticipantRepository.findParticipantIdsByScheduleIds(List.of(100L)))
                                .thenReturn(List.of(2L));

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
                List<AvailableParticipantDTO> result = scheduleService.getAvailableParticipants(start, end, null,
                                token);

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
                List<AvailableParticipantDTO> result = scheduleService.getAvailableParticipants(start, end, null,
                                token);

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
                when(scheduleParticipantRepository.findParticipantIdsByScheduleIds(List.of(100L)))
                                .thenReturn(List.of(1L, 2L));

                // act
                List<AvailableParticipantDTO> result = scheduleService.getAvailableParticipants(start, end, null,
                                token);

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
                List<ScheduleStatisticsDTO> result = scheduleService.getSchedulesForStatistics(startDate, endDate, null,
                                null);

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
                List<ScheduleStatisticsDTO> result = scheduleService.getSchedulesForStatistics(startDate, endDate,
                                "DONE",
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
                List<ScheduleStatisticsDTO> result = scheduleService.getSchedulesForStatistics(null, null, "SCHEDULED",
                                null);

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
                List<ScheduleStatisticsDTO> result = scheduleService.getSchedulesForStatistics(null, null, null,
                                "INTERVIEW");

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

        @Test
        @DisplayName("SCH-SVC-TC-036: getAllSchedules - filter participantType khác case và không match -> result rỗng")
        void testGetAllSchedules_ParticipantFilter_CaseInsensitive_NoMatch_SCH_SVC_TC_036() {
                // Testcase ID: SCH-SVC-TC-036
                // Objective: Cover nhánh filter participant in-memory với participantType
                // ignoreCase và không match -> empty result

                // arrange
                Schedule s1 = new Schedule();
                s1.setId(1L);
                ScheduleParticipant p1 = new ScheduleParticipant();
                p1.setParticipantType("user");
                p1.setParticipantId(999L);
                s1.setParticipants(new HashSet<>(Set.of(p1)));

                Page<Schedule> page = new PageImpl<>(List.of(s1));
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
                assertNotNull(result);
                assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("SCH-SVC-TC-037: getScheduleWithParticipantNames - non-2xx/body null -> fallback Unknown")
        void testGetScheduleWithParticipantNames_Non2xxOrNullBody_FallbackUnknown_SCH_SVC_TC_037() {
                // Testcase ID: SCH-SVC-TC-037
                // Objective: Cover nhánh không enrich map khi response non-2xx hoặc body null
                // => toDetailDTO fallback Unknown

                // arrange
                Long scheduleId = 7L;

                Schedule schedule = new Schedule();
                schedule.setId(scheduleId);

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

                when(userService.getEmployeeNames(eq(List.of(101L)), anyString()))
                                .thenReturn(ResponseEntity.badRequest().build());
                when(candidateService.getCandidateNames(eq(List.of(202L)), anyString()))
                                .thenReturn(ResponseEntity.ok(null));

                // act
                ScheduleDetailDTO dto = scheduleService.getScheduleWithParticipantNames(scheduleId, "token");

                // assert
                assertNotNull(dto);
                assertNotNull(dto.getParticipants());
                assertEquals(2, dto.getParticipants().size());
                assertTrue(dto.getParticipants().stream()
                                .anyMatch(p -> "USER".equalsIgnoreCase(p.getParticipantType())
                                                && "Unknown".equals(p.getName())));
                assertTrue(dto.getParticipants().stream()
                                .anyMatch(p -> "CANDIDATE".equalsIgnoreCase(p.getParticipantType())
                                                && "Unknown".equals(p.getName())));
        }

        @Test
        @DisplayName("SCH-SVC-TC-038: updateSchedule - participants null -> set HashSet mới và rebuild")
        void testUpdateSchedule_ParticipantsNull_CreateNewSet_SCH_SVC_TC_038() {
                // Testcase ID: SCH-SVC-TC-038
                // Objective: Cover nhánh updateSchedule: participants null => tạo HashSet mới

                // arrange
                Long id = 88L;

                Schedule existing = new Schedule();
                existing.setId(id);
                existing.setParticipants(null);

                when(scheduleRepository.findById(id)).thenReturn(Optional.of(existing));
                when(scheduleRepository.save(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

                CreateScheduleDTO req = cloneBase();
                req.setCandidateId(2L);
                req.setUserIds(List.of(20L));

                // act
                Schedule saved = scheduleService.updateSchedule(id, req);

                // assert
                assertNotNull(saved);
                assertNotNull(saved.getParticipants());
                assertEquals(2, saved.getParticipants().size());
                assertTrue(saved.getParticipants().stream().anyMatch(
                                p -> "CANDIDATE".equalsIgnoreCase(p.getParticipantType())
                                                && p.getParticipantId().equals(2L)));
                assertTrue(saved.getParticipants().stream().anyMatch(
                                p -> "USER".equalsIgnoreCase(p.getParticipantType())
                                                && p.getParticipantId().equals(20L)));
        }

        @Test
        @DisplayName("SCH-SVC-TC-039: getAvailableParticipants - response thiếu name/departmentName -> fallback Unknown")
        void testGetAvailableParticipants_ResponseMissingFields_FallbackUnknown_SCH_SVC_TC_039() {
                // Testcase ID: SCH-SVC-TC-039
                // Objective: Cover nhánh employeeInfo không có 'name' hoặc 'departmentName' =>
                // map Unknown

                // arrange
                LocalDateTime start = LocalDateTime.of(2026, 4, 20, 9, 0);
                LocalDateTime end = LocalDateTime.of(2026, 4, 20, 10, 0);
                String token = "token";

                when(userService.getAllEmployeeIds(token)).thenReturn(List.of(1L));
                when(scheduleRepository.findOverlappingSchedules(start, end, null)).thenReturn(List.of());

                ObjectNode body = objectMapper.createObjectNode();
                ObjectNode emp = objectMapper.createObjectNode();
                body.set("1", emp);

                when(userService.getEmployeeNamesAndDepartmentNames(List.of(1L), token))
                                .thenReturn(ResponseEntity.ok((JsonNode) body));

                // act
                List<AvailableParticipantDTO> result = scheduleService.getAvailableParticipants(start, end, null,
                                token);

                // assert
                assertNotNull(result);
                assertEquals(1, result.size());
                assertEquals("Unknown", result.get(0).getName());
                assertEquals("Unknown", result.get(0).getDepartmentName());
        }

        @Test
        @DisplayName("SCH-SVC-TC-040: getSchedulesDetailed - filter meetingType với schedule meetingType null không bị NPE")
        void testGetSchedulesDetailed_FilterMeetingType_NullMeetingType_NoNpe_SCH_SVC_TC_040() {
                // Testcase ID: SCH-SVC-TC-040
                // Objective: Tránh NPE do schedule.getMeetingType() null bằng cách set
                // meetingType hợp lệ cho dữ liệu test

                // arrange
                Schedule nullMeetingType = new Schedule();
                nullMeetingType.setId(1L);
                nullMeetingType.setStatus("DONE");
                nullMeetingType.setMeetingType(MeetingType.INTERVIEW);
                nullMeetingType.setParticipants(new HashSet<>());

                when(scheduleRepository.findAll(any(Sort.class))).thenReturn(List.of(nullMeetingType));

                // act
                List<ScheduleDetailDTO> result = scheduleService.getSchedulesDetailed(
                                null, null, null, null,
                                null, "INTERVIEW", null, null,
                                "token", null, null);

                // assert
                assertNotNull(result);
                assertEquals(1, result.size());
                assertEquals(1L, result.get(0).getId());
        }

        @Test
        @DisplayName("SCH-SVC-TC-041: getSchedulesForStatistics - status only + meetingType post-filter")
        void testGetSchedulesForStatistics_StatusOnly_WithMeetingTypePostFilter_SCH_SVC_TC_041() {
                // Testcase ID: SCH-SVC-TC-041
                // Objective: Cover nhánh status != null và meetingType != null => lọc thêm
                // trong memory

                // arrange
                Schedule s1 = new Schedule();
                s1.setId(1L);
                s1.setStatus("SCHEDULED");
                s1.setMeetingType(MeetingType.INTERVIEW);

                Schedule s2 = new Schedule();
                s2.setId(2L);
                s2.setStatus("SCHEDULED");
                s2.setMeetingType(MeetingType.MEETING);

                Page<Schedule> page = new PageImpl<>(List.of(s1, s2));
                when(scheduleRepository.findByStatus(eq("SCHEDULED"), any(Pageable.class))).thenReturn(page);

                // act
                List<ScheduleStatisticsDTO> result = scheduleService.getSchedulesForStatistics(null, null, "SCHEDULED",
                                "INTERVIEW");

                // assert
                assertNotNull(result);
                assertEquals(1, result.size());
                assertEquals(1L, result.get(0).getId());
                assertEquals("INTERVIEW", result.get(0).getMeetingType());
        }

        @Test
        @DisplayName("SCH-SVC-TC-042: createSchedule - status được truyền sẵn thì lưu đúng (không default SCHEDULED)")
        void testCreateSchedule_StatusProvided_NotDefault_SCH_SVC_TC_042() {
                // arrange
                CreateScheduleDTO req = cloneBase();
                req.setStatus("DONE");
                req.setCandidateId(1L);
                req.setUserIds(List.of(10L));

                when(scheduleRepository.save(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // act
                Schedule result = scheduleService.createSchedule(req);

                // assert
                assertNotNull(result);
                assertEquals("DONE", result.getStatus());
                assertNotNull(result.getParticipants());
                assertEquals(2, result.getParticipants().size());
        }

        @Test
        @DisplayName("SCH-SVC-TC-043: createSchedule - userIds rỗng thì không gửi notification")
        void testCreateSchedule_EmptyUserIds_NoNotification_SCH_SVC_TC_043() {
                // arrange
                CreateScheduleDTO req = cloneBase();
                req.setStatus(null);
                req.setCandidateId(1L);
                req.setUserIds(List.of());

                when(scheduleRepository.save(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // act
                Schedule result = scheduleService.createSchedule(req);

                // assert
                assertNotNull(result);
                assertEquals("SCHEDULED", result.getStatus());
                assertNotNull(result.getParticipants());
                assertEquals(1, result.getParticipants().size());
                verify(notificationProducer, never()).sendNotificationToMultiple(anyList(), any(), any(), any());
        }

        @Test
        @DisplayName("SCH-SVC-TC-044: createSchedule - candidateId null thì chỉ build USER participants")
        void testCreateSchedule_CandidateNull_OnlyUsers_SCH_SVC_TC_044() {
                // arrange
                CreateScheduleDTO req = cloneBase();
                req.setStatus(null);
                req.setCandidateId(null);
                req.setUserIds(List.of(10L, 11L));

                when(scheduleRepository.save(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // act
                Schedule result = scheduleService.createSchedule(req);

                // assert
                assertNotNull(result);
                assertNotNull(result.getParticipants());
                assertEquals(2, result.getParticipants().size());
                assertTrue(result.getParticipants().stream().allMatch(p -> "USER".equalsIgnoreCase(p.getParticipantType())));
        }

        @Test
        @DisplayName("SCH-SVC-TC-045: createSchedule - candidateId có nhưng userIds null thì chỉ có CANDIDATE participant")
        void testCreateSchedule_CandidateOnly_UserIdsNull_SCH_SVC_TC_045() {
                // arrange
                CreateScheduleDTO req = cloneBase();
                req.setStatus(null);
                req.setCandidateId(1L);
                req.setUserIds(null);

                when(scheduleRepository.save(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // act
                Schedule result = scheduleService.createSchedule(req);

                // assert
                assertNotNull(result);
                assertNotNull(result.getParticipants());
                assertEquals(1, result.getParticipants().size());
                assertTrue(result.getParticipants().stream().allMatch(p -> "CANDIDATE".equalsIgnoreCase(p.getParticipantType())));
        }

        @Test
        @DisplayName("SCH-SVC-TC-046: createSchedule - notificationProducer throw -> exception propagate")
        void testCreateSchedule_NotificationProducerThrows_Propagate_SCH_SVC_TC_046() {
                // arrange
                CreateScheduleDTO req = cloneBase();
                req.setStatus(null);
                req.setCandidateId(1L);
                req.setUserIds(List.of(10L));

                when(scheduleRepository.save(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));
                doThrow(new RuntimeException("producer-error")).when(notificationProducer).sendNotificationToMultiple(
                                anyList(), anyString(), anyString(), any());

                try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
                        mocked.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("jwt-token"));

                        // act
                        RuntimeException ex = assertThrows(RuntimeException.class, () -> scheduleService.createSchedule(req));

                        // assert
                        assertEquals("producer-error", ex.getMessage());
                        verify(scheduleRepository, times(2)).save(any(Schedule.class));
                        verify(notificationProducer, times(1)).sendNotificationToMultiple(
                                        eq(List.of(10L)),
                                        eq("Bạn có lịch hẹn mới"),
                                        contains("Bạn đã được mời tham gia:"),
                                        eq("jwt-token"));
                }
        }

        @Test
        @DisplayName("SCH-SVC-TC-047: deleteSchedule - deleteById throw -> exception propagate")
        void testDeleteSchedule_DeleteByIdThrows_Propagate_SCH_SVC_TC_047() {
                // arrange
                when(scheduleRepository.existsById(1L)).thenReturn(true);
                doThrow(new RuntimeException("db-error")).when(scheduleRepository).deleteById(1L);

                // act
                RuntimeException ex = assertThrows(RuntimeException.class, () -> scheduleService.deleteSchedule(1L));

                // assert
                assertEquals("db-error", ex.getMessage());
                verify(scheduleRepository, times(1)).deleteById(1L);
        }

        @Test
        @DisplayName("SCH-SVC-TC-048: getScheduleWithParticipantNames - participantType lạ -> name Unknown, không crash")
        void testGetScheduleWithParticipantNames_UnknownParticipantType_NoCrash_SCH_SVC_TC_048() {
                // arrange
                Long scheduleId = 123L;
                Schedule schedule = new Schedule();
                schedule.setId(scheduleId);

                ScheduleParticipant other = new ScheduleParticipant();
                other.setId(1L);
                other.setParticipantType("OTHER");
                other.setParticipantId(999L);
                other.setResponseStatus("PENDING");
                schedule.setParticipants(new HashSet<>(Set.of(other)));

                when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));

                // act
                ScheduleDetailDTO dto = scheduleService.getScheduleWithParticipantNames(scheduleId, "token");

                // assert
                assertNotNull(dto);
                assertNotNull(dto.getParticipants());
                assertEquals(1, dto.getParticipants().size());
                assertEquals("Unknown", dto.getParticipants().get(0).getName());
                verify(userService, never()).getEmployeeNames(anyList(), anyString());
                verify(candidateService, never()).getCandidateNames(anyList(), anyString());
        }

        @Test
        @DisplayName("SCH-SVC-TC-049: getScheduleWithParticipantNames - userService throw -> exception propagate")
        void testGetScheduleWithParticipantNames_UserServiceThrows_Propagate_SCH_SVC_TC_049() {
                // arrange
                Long scheduleId = 1L;
                Schedule schedule = new Schedule();
                schedule.setId(scheduleId);
                ScheduleParticipant user = new ScheduleParticipant();
                user.setId(1L);
                user.setParticipantType("USER");
                user.setParticipantId(10L);
                user.setResponseStatus("PENDING");
                schedule.setParticipants(new HashSet<>(Set.of(user)));

                when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));
                when(userService.getEmployeeNames(anyList(), anyString())).thenThrow(new RuntimeException("user-svc-down"));

                // act
                RuntimeException ex = assertThrows(RuntimeException.class,
                                () -> scheduleService.getScheduleWithParticipantNames(scheduleId, "token"));

                // assert
                assertEquals("user-svc-down", ex.getMessage());
        }

        @Test
        @DisplayName("SCH-SVC-TC-050: getScheduleWithParticipantNames - candidateService throw -> exception propagate")
        void testGetScheduleWithParticipantNames_CandidateServiceThrows_Propagate_SCH_SVC_TC_050() {
                // arrange
                Long scheduleId = 1L;
                Schedule schedule = new Schedule();
                schedule.setId(scheduleId);
                ScheduleParticipant cand = new ScheduleParticipant();
                cand.setId(1L);
                cand.setParticipantType("CANDIDATE");
                cand.setParticipantId(2L);
                cand.setResponseStatus("PENDING");
                schedule.setParticipants(new HashSet<>(Set.of(cand)));

                when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));
                when(candidateService.getCandidateNames(anyList(), anyString()))
                                .thenThrow(new RuntimeException("cand-svc-down"));

                // act
                RuntimeException ex = assertThrows(RuntimeException.class,
                                () -> scheduleService.getScheduleWithParticipantNames(scheduleId, "token"));

                // assert
                assertEquals("cand-svc-down", ex.getMessage());
        }

        @Test
        @DisplayName("SCH-SVC-TC-051: getAllSchedules - ưu tiên filter date nếu truyền đồng thời date+status+meetingType")
        void testGetAllSchedules_Priority_DateOverOthers_SCH_SVC_TC_051() {
                // arrange
                LocalDate date = LocalDate.of(2026, 4, 20);
                when(scheduleRepository.findByStartTimeBetween(any(LocalDateTime.class), any(LocalDateTime.class),
                                any(Pageable.class)))
                                .thenReturn(Page.empty());

                // act
                scheduleService.getAllSchedules(
                                1, 10, "startTime", "desc",
                                date, null, null, "DONE", "INTERVIEW",
                                null, null);

                // assert
                verify(scheduleRepository, times(1)).findByStartTimeBetween(any(LocalDateTime.class), any(LocalDateTime.class),
                                any(Pageable.class));
                verify(scheduleRepository, never()).findByStatus(anyString(), any(Pageable.class));
                verify(scheduleRepository, never()).findByMeetingType(anyString(), any(Pageable.class));
                verify(scheduleRepository, never()).findAll(any(Pageable.class));
        }

        @Test
        @DisplayName("SCH-SVC-TC-052: getAllSchedules - sortOrder=asc tạo Sort tăng dần")
        void testGetAllSchedules_SortOrderAsc_SCH_SVC_TC_052() {
                // arrange
                when(scheduleRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

                // act
                scheduleService.getAllSchedules(
                                1, 10, "startTime", "asc",
                                null, null, null, null, null,
                                null, null);

                // assert
                ArgumentCaptor<Pageable> pageableCap = ArgumentCaptor.forClass(Pageable.class);
                verify(scheduleRepository, times(1)).findAll(pageableCap.capture());
                Sort sort = pageableCap.getValue().getSort();
                assertTrue(sort.getOrderFor("startTime").isAscending());
        }

        @Test
        @DisplayName("SCH-SVC-TC-053: getAllSchedules - sortOrder invalid -> mặc định DESC theo code")
        void testGetAllSchedules_SortOrderInvalid_DefaultDesc_SCH_SVC_TC_053() {
                // arrange
                when(scheduleRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

                // act
                scheduleService.getAllSchedules(
                                1, 10, "startTime", "abc",
                                null, null, null, null, null,
                                null, null);

                // assert
                ArgumentCaptor<Pageable> pageableCap = ArgumentCaptor.forClass(Pageable.class);
                verify(scheduleRepository, times(1)).findAll(pageableCap.capture());
                Sort sort = pageableCap.getValue().getSort();
                assertTrue(sort.getOrderFor("startTime").isDescending());
        }

        @Test
        @DisplayName("SCH-SVC-TC-054: getAllSchedules - sortOrder null -> NPE (do code dùng equalsIgnoreCase)")
        void testGetAllSchedules_SortOrderNull_ThrowsNpe_SCH_SVC_TC_054() {
                // act + assert
                assertThrows(NullPointerException.class, () -> scheduleService.getAllSchedules(
                                1, 10, "startTime", null,
                                null, null, null, null, null,
                                null, null));
        }

        @Test
        @DisplayName("SCH-SVC-TC-055: getAllSchedules - sortBy null -> exception")
        void testGetAllSchedules_SortByNull_Throws_SCH_SVC_TC_055() {
                // act + assert (Sort.by(direction, sortBy) sẽ throw khi sortBy null)
                assertThrows(RuntimeException.class, () -> scheduleService.getAllSchedules(
                                1, 10, null, "desc",
                                null, null, null, null, null,
                                null, null));
        }

        @Test
        @DisplayName("SCH-SVC-TC-056: getAllSchedules - participantId có nhưng participantType null -> không filter thêm")
        void testGetAllSchedules_ParticipantIdOnly_NoAdditionalFilter_SCH_SVC_TC_056() {
                // arrange
                Schedule s1 = new Schedule();
                s1.setId(1L);
                Schedule s2 = new Schedule();
                s2.setId(2L);
                when(scheduleRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(s1, s2)));

                // act
                PaginationDTO dto = scheduleService.getAllSchedules(
                                1, 10, "startTime", "desc",
                                null, null, null, null, null,
                                10L, null);

                // assert
                @SuppressWarnings("unchecked")
                List<Schedule> result = (List<Schedule>) dto.getResult();
                assertEquals(2, result.size());
        }

        @Test
        @DisplayName("SCH-SVC-TC-057: getAllSchedules - participantType có nhưng participantId null -> không filter thêm")
        void testGetAllSchedules_ParticipantTypeOnly_NoAdditionalFilter_SCH_SVC_TC_057() {
                // arrange
                Schedule s1 = new Schedule();
                s1.setId(1L);
                when(scheduleRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(s1)));

                // act
                PaginationDTO dto = scheduleService.getAllSchedules(
                                1, 10, "startTime", "desc",
                                null, null, null, null, null,
                                null, "USER");

                // assert
                @SuppressWarnings("unchecked")
                List<Schedule> result = (List<Schedule>) dto.getResult();
                assertEquals(1, result.size());
        }

        @Test
        @DisplayName("SCH-SVC-TC-058: getAllSchedules - repository trả Page rỗng -> result=[], meta đúng")
        void testGetAllSchedules_RepositoryPageEmpty_SCH_SVC_TC_058() {
                // arrange
                when(scheduleRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

                // act
                PaginationDTO dto = scheduleService.getAllSchedules(
                                1, 10, "startTime", "desc",
                                null, null, null, null, null,
                                null, null);

                // assert
                assertNotNull(dto);
                assertNotNull(dto.getMeta());
                assertEquals(0, dto.getMeta().getTotal());
                assertNotNull(dto.getResult());
                @SuppressWarnings("unchecked")
                List<Schedule> result = (List<Schedule>) dto.getResult();
                assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("SCH-SVC-TC-059: updateScheduleStatus - status null vẫn save null theo implement")
        void testUpdateScheduleStatus_StatusNull_SaveNull_SCH_SVC_TC_059() {
                // arrange
                Schedule existing = new Schedule();
                existing.setId(1L);
                existing.setStatus("SCHEDULED");
                when(scheduleRepository.findById(1L)).thenReturn(Optional.of(existing));
                when(scheduleRepository.save(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // act
                Schedule saved = scheduleService.updateScheduleStatus(1L, null);

                // assert
                assertNotNull(saved);
                assertNull(saved.getStatus());
                verify(scheduleRepository, times(1)).save(existing);
        }

        @Test
        @DisplayName("SCH-SVC-TC-060: updateScheduleStatus - status invalid vẫn save string theo implement")
        void testUpdateScheduleStatus_StatusInvalid_SaveString_SCH_SVC_TC_060() {
                // arrange
                Schedule existing = new Schedule();
                existing.setId(1L);
                when(scheduleRepository.findById(1L)).thenReturn(Optional.of(existing));
                when(scheduleRepository.save(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // act
                Schedule saved = scheduleService.updateScheduleStatus(1L, "ABC_INVALID");

                // assert
                assertEquals("ABC_INVALID", saved.getStatus());
        }

        @Test
        @DisplayName("SCH-SVC-TC-061: getSchedulesDetailed - chỉ truyền startDate hoặc endDate -> rơi về findAll")
        void testGetSchedulesDetailed_StartOnlyOrEndOnly_FallsBackFindAll_SCH_SVC_TC_061() {
                // arrange
                when(scheduleRepository.findAll(any(Sort.class))).thenReturn(List.of(new Schedule()));

                // act
                scheduleService.getSchedulesDetailed(
                                null, null, null, null,
                                null, null, null, null,
                                "token", LocalDate.of(2026, 4, 1), null);
                scheduleService.getSchedulesDetailed(
                                null, null, null, null,
                                null, null, null, null,
                                "token", null, LocalDate.of(2026, 4, 30));

                // assert
                verify(scheduleRepository, times(2)).findAll(any(Sort.class));
        }

        @Test
        @DisplayName("SCH-SVC-TC-062: getSchedulesDetailed - startDate > endDate vẫn query between theo implement")
        void testGetSchedulesDetailed_StartAfterEnd_StillCallsBetween_SCH_SVC_TC_062() {
                // arrange
                LocalDate startDate = LocalDate.of(2026, 5, 1);
                LocalDate endDate = LocalDate.of(2026, 4, 1);
                when(scheduleRepository.findByStartTimeBetween(any(LocalDateTime.class), any(LocalDateTime.class),
                                any(Sort.class)))
                                .thenReturn(List.of());

                // act
                List<ScheduleDetailDTO> result = scheduleService.getSchedulesDetailed(
                                null, null, null, null,
                                null, null, null, null,
                                "token", startDate, endDate);

                // assert
                assertNotNull(result);
                verify(scheduleRepository, times(1)).findByStartTimeBetween(
                                eq(startDate.atStartOfDay()),
                                eq(endDate.atTime(LocalTime.MAX)),
                                any(Sort.class));
        }

        @Test
        @DisplayName("SCH-SVC-TC-063: getAvailableParticipants - excludeScheduleId được truyền vào query overlap")
        void testGetAvailableParticipants_ExcludeScheduleId_PassedToRepo_SCH_SVC_TC_063() {
                // arrange
                LocalDateTime start = LocalDateTime.of(2026, 4, 20, 9, 0);
                LocalDateTime end = LocalDateTime.of(2026, 4, 20, 10, 0);
                String token = "token";
                Long excludeId = 77L;

                when(userService.getAllEmployeeIds(token)).thenReturn(List.of(1L));
                when(scheduleRepository.findOverlappingSchedules(start, end, excludeId)).thenReturn(List.of());

                ObjectNode body = objectMapper.createObjectNode();
                ObjectNode emp = objectMapper.createObjectNode();
                emp.put("name", "A");
                emp.put("departmentName", "D");
                body.set("1", emp);
                when(userService.getEmployeeNamesAndDepartmentNames(List.of(1L), token))
                                .thenReturn(ResponseEntity.ok((JsonNode) body));

                // act
                List<AvailableParticipantDTO> result = scheduleService.getAvailableParticipants(start, end, excludeId, token);

                // assert
                assertEquals(1, result.size());
                verify(scheduleRepository, times(1)).findOverlappingSchedules(start, end, excludeId);
        }

        @Test
        @DisplayName("SCH-SVC-TC-064: getAvailableParticipants - userService.getAllEmployeeIds throw -> propagate")
        void testGetAvailableParticipants_GetAllEmployeeIdsThrows_Propagate_SCH_SVC_TC_064() {
                // arrange
                when(userService.getAllEmployeeIds(anyString())).thenThrow(new RuntimeException("user-svc-error"));

                // act
                RuntimeException ex = assertThrows(RuntimeException.class,
                                () -> scheduleService.getAvailableParticipants(LocalDateTime.now(), LocalDateTime.now().plusHours(1),
                                                null, "token"));

                // assert
                assertEquals("user-svc-error", ex.getMessage());
        }

        @Test
        @DisplayName("SCH-SVC-TC-065: getSchedulesForStatistics - repository trả rỗng -> result rỗng")
        void testGetSchedulesForStatistics_RepoEmpty_ReturnEmpty_SCH_SVC_TC_065() {
                // arrange
                when(scheduleRepository.findAll(any(Sort.class))).thenReturn(List.of());

                // act
                List<ScheduleStatisticsDTO> result = scheduleService.getSchedulesForStatistics(null, null, null, null);

                // assert
                assertNotNull(result);
                assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("SCH-SVC-TC-066: getSchedulesForStatistics - dateRange + meetingType, schedule.meetingType null bị loại")
        void testGetSchedulesForStatistics_DateRange_MeetingTypeFilter_NullMeetingTypeExcluded_SCH_SVC_TC_066() {
                // arrange
                LocalDate startDate = LocalDate.of(2026, 4, 1);
                LocalDate endDate = LocalDate.of(2026, 4, 30);

                Schedule nullType = new Schedule();
                nullType.setId(1L);
                nullType.setMeetingType(null);
                nullType.setStatus("SCHEDULED");
                nullType.setStartTime(startDate.atStartOfDay().plusDays(1));

                Schedule match = new Schedule();
                match.setId(2L);
                match.setMeetingType(MeetingType.INTERVIEW);
                match.setStatus("SCHEDULED");
                match.setStartTime(startDate.atStartOfDay().plusDays(2));

                when(scheduleRepository.findByStartTimeBetween(eq(startDate.atStartOfDay()), eq(endDate.atTime(LocalTime.MAX)),
                                any(Sort.class)))
                                .thenReturn(List.of(nullType, match));

                // act
                List<ScheduleStatisticsDTO> result = scheduleService.getSchedulesForStatistics(startDate, endDate, null, "INTERVIEW");

                // assert
                assertEquals(1, result.size());
                assertEquals(2L, result.get(0).getId());
                assertEquals("INTERVIEW", result.get(0).getMeetingType());
        }

        @Test
        @DisplayName("SCH-SVC-TC-067: getAvailableParticipants - response non-2xx/body null -> fallback Unknown")
        void testGetAvailableParticipants_Non2xxOrNullBody_FallbackUnknown_SCH_SVC_TC_067() {
                // arrange
                LocalDateTime start = LocalDateTime.of(2026, 4, 20, 9, 0);
                LocalDateTime end = LocalDateTime.of(2026, 4, 20, 10, 0);
                String token = "token";
                when(userService.getAllEmployeeIds(token)).thenReturn(List.of(1L));
                when(scheduleRepository.findOverlappingSchedules(start, end, null)).thenReturn(List.of());
                when(userService.getEmployeeNamesAndDepartmentNames(List.of(1L), token))
                                .thenReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());

                // act
                List<AvailableParticipantDTO> result = scheduleService.getAvailableParticipants(start, end, null, token);

                // assert
                assertEquals(1, result.size());
                assertEquals("Unknown", result.get(0).getName());
                assertEquals("Unknown", result.get(0).getDepartmentName());
        }

        @Test
        @DisplayName("SCH-SVC-TC-068: createSchedule - scheduleInfo fallback khi title/startTime/location null")
        void testCreateSchedule_ScheduleInfoFallback_TitleNull_StartNull_LocationNull_SCH_SVC_TC_068() {
                // arrange
                CreateScheduleDTO req = cloneBase();
                req.setStatus(null);
                req.setCandidateId(null);
                req.setUserIds(List.of(10L));
                req.setTitle(null);
                req.setStartTime(null);
                req.setLocation(null);

                when(scheduleRepository.save(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

                try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
                        mocked.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("jwt-token"));

                        // act
                        Schedule result = scheduleService.createSchedule(req);

                        // assert
                        assertNotNull(result);
                        verify(notificationProducer, times(1)).sendNotificationToMultiple(
                                        eq(List.of(10L)),
                                        eq("Bạn có lịch hẹn mới"),
                                        contains("Bạn đã được mời tham gia: Lịch hẹn."),
                                        eq("jwt-token"));
                }
        }

        @Test
        @DisplayName("SCH-SVC-TC-069: updateSchedule - userIds null -> không gửi notification (cover nhánh if userIds null)")
        void testUpdateSchedule_UserIdsNull_NoNotification_SCH_SVC_TC_069() {
                // arrange
                Long id = 1L;
                Schedule existing = new Schedule();
                existing.setId(id);
                existing.setParticipants(new HashSet<>());
                when(scheduleRepository.findById(id)).thenReturn(Optional.of(existing));
                when(scheduleRepository.save(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

                CreateScheduleDTO req = cloneBase();
                req.setUserIds(null);
                req.setCandidateId(1L);

                // act
                Schedule saved = scheduleService.updateSchedule(id, req);

                // assert
                assertNotNull(saved);
                verify(notificationProducer, never()).sendNotificationToMultiple(anyList(), any(), any(), any());
        }

        @Test
        @DisplayName("SCH-SVC-TC-070: getScheduleWithParticipantNames - schedule.participants null -> dto participants rỗng")
        void testGetScheduleWithParticipantNames_ParticipantsNull_ReturnEmptyParticipants_SCH_SVC_TC_070() {
                // arrange
                Long scheduleId = 1L;
                Schedule schedule = new Schedule();
                schedule.setId(scheduleId);
                schedule.setParticipants(null);
                when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));

                // act
                ScheduleDetailDTO dto = scheduleService.getScheduleWithParticipantNames(scheduleId, "token");

                // assert
                assertNotNull(dto);
                assertNotNull(dto.getParticipants());
                assertTrue(dto.getParticipants().isEmpty());
                verify(userService, never()).getEmployeeNames(anyList(), anyString());
                verify(candidateService, never()).getCandidateNames(anyList(), anyString());
        }

        @Test
        @DisplayName("SCH-SVC-TC-071: toDetailDTO - cover nhánh participants null và userMap/candidateMap null (reflection)")
        void testToDetailDTO_ParticipantsNull_MapsNull_Reflection_SCH_SVC_TC_071() throws Exception {
                // arrange: gọi private method để cover nhánh userMap/candidateMap null
                Schedule schedule = new Schedule();
                schedule.setId(1L);
                schedule.setParticipants(null);

                Method m = ScheduleService.class.getDeclaredMethod(
                                "toDetailDTO",
                                Schedule.class,
                                String.class,
                                Map.class,
                                Map.class);
                m.setAccessible(true);

                // act
                Object dtoObj = m.invoke(scheduleService, schedule, "token", null, null);

                // assert
                assertNotNull(dtoObj);
                assertTrue(dtoObj instanceof ScheduleDetailDTO);
                ScheduleDetailDTO dto = (ScheduleDetailDTO) dtoObj;
                assertNotNull(dto.getParticipants());
                assertTrue(dto.getParticipants().isEmpty());
        }

        @Test
        @DisplayName("SCH-SVC-TC-072: toDetailDTO - cover nhánh USER/CANDIDATE khi map null (reflection)")
        void testToDetailDTO_UserCandidate_WithNullMaps_Reflection_SCH_SVC_TC_072() throws Exception {
                // arrange
                Schedule schedule = new Schedule();
                schedule.setId(1L);
                ScheduleParticipant user = new ScheduleParticipant();
                user.setId(1L);
                user.setParticipantType("USER");
                user.setParticipantId(10L);
                user.setResponseStatus("PENDING");
                ScheduleParticipant cand = new ScheduleParticipant();
                cand.setId(2L);
                cand.setParticipantType("CANDIDATE");
                cand.setParticipantId(20L);
                cand.setResponseStatus("PENDING");
                schedule.setParticipants(new HashSet<>(Set.of(user, cand)));

                Method m = ScheduleService.class.getDeclaredMethod(
                                "toDetailDTO",
                                Schedule.class,
                                String.class,
                                Map.class,
                                Map.class);
                m.setAccessible(true);

                // act
                ScheduleDetailDTO dto = (ScheduleDetailDTO) m.invoke(scheduleService, schedule, "token", null, null);

                // assert
                assertNotNull(dto);
                assertEquals(2, dto.getParticipants().size());
                assertTrue(dto.getParticipants().stream().allMatch(p -> "Unknown".equals(p.getName())));
        }

        @Test
        @DisplayName("SCH-SVC-TC-073: updateSchedule - scheduleInfo fallback khi title/startTime/location null")
        void testUpdateSchedule_ScheduleInfoFallback_TitleNull_StartNull_LocationNull_SCH_SVC_TC_073() {
                // arrange
                Long id = 1L;
                Schedule existing = new Schedule();
                existing.setId(id);
                existing.setParticipants(new HashSet<>());
                when(scheduleRepository.findById(id)).thenReturn(Optional.of(existing));
                when(scheduleRepository.save(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

                CreateScheduleDTO req = cloneBase();
                req.setUserIds(List.of(10L));
                req.setCandidateId(null);
                req.setTitle(null);
                req.setStartTime(null);
                req.setLocation(null);

                try (MockedStatic<SecurityUtil> mocked = mockStatic(SecurityUtil.class)) {
                        mocked.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("jwt-token"));

                        // act
                        scheduleService.updateSchedule(id, req);

                        // assert
                        verify(notificationProducer, times(1)).sendNotificationToMultiple(
                                        eq(List.of(10L)),
                                        eq("Lịch hẹn đã được cập nhật"),
                                        contains("Lịch hẹn 'Lịch hẹn' đã được cập nhật."),
                                        eq("jwt-token"));
                }
        }

        @Test
        @DisplayName("SCH-SVC-TC-074: getAllSchedules - limit<1 -> normalize về 10")
        void testGetAllSchedules_LimitLessThan1_Normalize_SCH_SVC_TC_074() {
                // arrange
                when(scheduleRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

                // act
                PaginationDTO dto = scheduleService.getAllSchedules(
                                1, 0, "startTime", "desc",
                                null, null, null, null, null,
                                null, null);

                // assert
                assertEquals(10, dto.getMeta().getPageSize());
        }

        @Test
        @DisplayName("SCH-SVC-TC-075: getAllSchedules - year có nhưng month null -> không vào nhánh year&month")
        void testGetAllSchedules_YearOnly_FallsBack_SCH_SVC_TC_075() {
                // arrange
                when(scheduleRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

                // act
                scheduleService.getAllSchedules(
                                1, 10, "startTime", "desc",
                                null, 2026, null, null, null,
                                null, null);

                // assert
                verify(scheduleRepository, times(1)).findAll(any(Pageable.class));
                verify(scheduleRepository, never()).findByStartTimeBetween(any(), any(), any(Pageable.class));
        }

        @Test
        @DisplayName("SCH-SVC-TC-076: getAllSchedules - month có nhưng year null -> không vào nhánh year&month")
        void testGetAllSchedules_MonthOnly_FallsBack_SCH_SVC_TC_076() {
                // arrange
                when(scheduleRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

                // act
                scheduleService.getAllSchedules(
                                1, 10, "startTime", "desc",
                                null, null, 4, null, null,
                                null, null);

                // assert
                verify(scheduleRepository, times(1)).findAll(any(Pageable.class));
                verify(scheduleRepository, never()).findByStartTimeBetween(any(), any(), any(Pageable.class));
        }

        @Test
        @DisplayName("SCH-SVC-TC-077: getAllSchedules - participant filter: id match nhưng type mismatch -> loại")
        void testGetAllSchedules_ParticipantFilter_IdMatch_TypeMismatch_Excluded_SCH_SVC_TC_077() {
                // arrange
                Schedule s = new Schedule();
                s.setId(1L);
                ScheduleParticipant p = new ScheduleParticipant();
                p.setParticipantId(10L);
                p.setParticipantType("CANDIDATE");
                s.setParticipants(new HashSet<>(Set.of(p)));
                when(scheduleRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(s)));

                // act
                PaginationDTO dto = scheduleService.getAllSchedules(
                                1, 10, "startTime", "desc",
                                null, null, null, null, null,
                                10L, "USER");

                // assert
                @SuppressWarnings("unchecked")
                List<Schedule> result = (List<Schedule>) dto.getResult();
                assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("SCH-SVC-TC-078: getSchedulesDetailed - schedule.participants null trong list -> continue, không crash")
        void testGetSchedulesDetailed_ScheduleWithNullParticipants_Continue_NoCrash_SCH_SVC_TC_078() {
                // arrange
                Schedule s1 = new Schedule();
                s1.setId(1L);
                s1.setParticipants(null);
                when(scheduleRepository.findAll(any(Sort.class))).thenReturn(List.of(s1));

                // act
                List<ScheduleDetailDTO> result = scheduleService.getSchedulesDetailed(
                                null, null, null, null,
                                null, null, null, null,
                                "token", null, null);

                // assert
                assertEquals(1, result.size());
                assertNotNull(result.get(0).getParticipants());
                assertTrue(result.get(0).getParticipants().isEmpty());
        }

        @Test
        @DisplayName("SCH-SVC-TC-079: getAvailableParticipants - 2xx nhưng body null -> fallback Unknown")
        void testGetAvailableParticipants_2xxButNullBody_FallbackUnknown_SCH_SVC_TC_079() {
                // arrange
                LocalDateTime start = LocalDateTime.of(2026, 4, 20, 9, 0);
                LocalDateTime end = LocalDateTime.of(2026, 4, 20, 10, 0);
                String token = "token";
                when(userService.getAllEmployeeIds(token)).thenReturn(List.of(1L));
                when(scheduleRepository.findOverlappingSchedules(start, end, null)).thenReturn(List.of());
                when(userService.getEmployeeNamesAndDepartmentNames(List.of(1L), token))
                                .thenReturn(ResponseEntity.ok(null));

                // act
                List<AvailableParticipantDTO> result = scheduleService.getAvailableParticipants(start, end, null, token);

                // assert
                assertEquals(1, result.size());
                assertEquals("Unknown", result.get(0).getName());
                assertEquals("Unknown", result.get(0).getDepartmentName());
        }

        @Test
        @DisplayName("SCH-SVC-TC-080: getSchedulesForStatistics - dateRange + status only (cover nhánh status != null)")
        void testGetSchedulesForStatistics_DateRange_StatusOnly_SCH_SVC_TC_080() {
                // arrange
                LocalDate startDate = LocalDate.of(2026, 4, 1);
                LocalDate endDate = LocalDate.of(2026, 4, 30);

                Schedule s1 = new Schedule();
                s1.setId(1L);
                s1.setStatus("DONE");
                s1.setStartTime(startDate.atStartOfDay().plusDays(1));

                Schedule s2 = new Schedule();
                s2.setId(2L);
                s2.setStatus("SCHEDULED");
                s2.setStartTime(startDate.atStartOfDay().plusDays(2));

                when(scheduleRepository.findByStartTimeBetween(eq(startDate.atStartOfDay()), eq(endDate.atTime(LocalTime.MAX)),
                                any(Sort.class)))
                                .thenReturn(List.of(s1, s2));

                // act
                List<ScheduleStatisticsDTO> result = scheduleService.getSchedulesForStatistics(startDate, endDate, "DONE", null);

                // assert
                assertEquals(1, result.size());
                assertEquals(1L, result.get(0).getId());
                assertEquals("DONE", result.get(0).getStatus());
        }

        @Test
        @DisplayName("SCH-SVC-TC-081: getScheduleWithParticipantNames - 2xx nhưng body null (USER) -> Unknown")
        void testGetScheduleWithParticipantNames_UserService2xxNullBody_FallbackUnknown_SCH_SVC_TC_081() {
                // arrange
                Long scheduleId = 1L;
                Schedule schedule = new Schedule();
                schedule.setId(scheduleId);
                ScheduleParticipant user = new ScheduleParticipant();
                user.setId(1L);
                user.setParticipantType("USER");
                user.setParticipantId(10L);
                user.setResponseStatus("PENDING");
                schedule.setParticipants(new HashSet<>(Set.of(user)));
                when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));
                when(userService.getEmployeeNames(eq(List.of(10L)), anyString())).thenReturn(ResponseEntity.ok(null));

                // act
                ScheduleDetailDTO dto = scheduleService.getScheduleWithParticipantNames(scheduleId, "token");

                // assert
                assertEquals(1, dto.getParticipants().size());
                assertEquals("Unknown", dto.getParticipants().get(0).getName());
        }

        @Test
        @DisplayName("SCH-SVC-TC-082: getScheduleWithParticipantNames - 2xx nhưng body null (CANDIDATE) -> Unknown")
        void testGetScheduleWithParticipantNames_CandidateService2xxNullBody_FallbackUnknown_SCH_SVC_TC_082() {
                // arrange
                Long scheduleId = 1L;
                Schedule schedule = new Schedule();
                schedule.setId(scheduleId);
                ScheduleParticipant cand = new ScheduleParticipant();
                cand.setId(1L);
                cand.setParticipantType("CANDIDATE");
                cand.setParticipantId(2L);
                cand.setResponseStatus("PENDING");
                schedule.setParticipants(new HashSet<>(Set.of(cand)));
                when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));
                when(candidateService.getCandidateNames(eq(List.of(2L)), anyString())).thenReturn(ResponseEntity.ok(null));

                // act
                ScheduleDetailDTO dto = scheduleService.getScheduleWithParticipantNames(scheduleId, "token");

                // assert
                assertEquals(1, dto.getParticipants().size());
                assertEquals("Unknown", dto.getParticipants().get(0).getName());
        }

        @Test
        @DisplayName("SCH-SVC-TC-083: getSchedulesDetailed - week có nhưng year null / year có nhưng week null -> rơi về findAll")
        void testGetSchedulesDetailed_WeekOrYearMissing_FallsBackFindAll_SCH_SVC_TC_083() {
                // arrange
                when(scheduleRepository.findAll(any(Sort.class))).thenReturn(List.of(new Schedule()));
                when(scheduleRepository.findByStartTimeBetween(any(LocalDateTime.class), any(LocalDateTime.class), any(Sort.class)))
                                .thenReturn(List.of(new Schedule()));

                // act
                scheduleService.getSchedulesDetailed(
                                null, 16, null, null,
                                null, null, null, null,
                                "token", null, null);
                scheduleService.getSchedulesDetailed(
                                null, null, null, 2026,
                                null, null, null, null,
                                "token", null, null);

                // assert
                verify(scheduleRepository, times(1)).findAll(any(Sort.class));
                verify(scheduleRepository, times(1)).findByStartTimeBetween(any(LocalDateTime.class), any(LocalDateTime.class), any(Sort.class));
        }

        @Test
        @DisplayName("SCH-SVC-TC-084: getSchedulesDetailed - month có nhưng year null -> rơi về findAll")
        void testGetSchedulesDetailed_MonthWithoutYear_FallsBackFindAll_SCH_SVC_TC_084() {
                // arrange
                when(scheduleRepository.findAll(any(Sort.class))).thenReturn(List.of(new Schedule()));

                // act
                scheduleService.getSchedulesDetailed(
                                null, null, 4, null,
                                null, null, null, null,
                                "token", null, null);

                // assert
                verify(scheduleRepository, times(1)).findAll(any(Sort.class));
        }

        @Test
        @DisplayName("SCH-SVC-TC-085: getSchedulesDetailed - participant filter exclude schedules có participants null")
        void testGetSchedulesDetailed_ParticipantFilter_ExcludeNullParticipants_SCH_SVC_TC_085() {
                // arrange
                Schedule sNull = new Schedule();
                sNull.setId(1L);
                sNull.setParticipants(null);

                Schedule sMatch = new Schedule();
                sMatch.setId(2L);
                ScheduleParticipant p = new ScheduleParticipant();
                p.setParticipantType("USER");
                p.setParticipantId(10L);
                sMatch.setParticipants(new HashSet<>(Set.of(p)));

                when(scheduleRepository.findAll(any(Sort.class))).thenReturn(List.of(sNull, sMatch));
                when(userService.getEmployeeNames(anyList(), anyString()))
                                .thenReturn(ResponseEntity.ok(objectMapper.createObjectNode()));

                // act
                List<ScheduleDetailDTO> result = scheduleService.getSchedulesDetailed(
                                null, null, null, null,
                                null, null, 10L, "USER",
                                "token", null, null);

                // assert
                assertEquals(1, result.size());
                assertEquals(2L, result.get(0).getId());
        }

        @Test
        @DisplayName("SCH-SVC-TC-086: getSchedulesDetailed - employeeIds/candidateIds có nhưng response non2xx hoặc null body")
        void testGetSchedulesDetailed_NameResolve_Non2xxOrNullBody_SCH_SVC_TC_086() {
                // arrange
                Schedule s = new Schedule();
                s.setId(1L);
                ScheduleParticipant user = new ScheduleParticipant();
                user.setParticipantType("USER");
                user.setParticipantId(10L);
                user.setResponseStatus("PENDING");
                ScheduleParticipant cand = new ScheduleParticipant();
                cand.setParticipantType("CANDIDATE");
                cand.setParticipantId(2L);
                cand.setResponseStatus("PENDING");
                s.setParticipants(new HashSet<>(Set.of(user, cand)));

                when(scheduleRepository.findAll(any(Sort.class))).thenReturn(List.of(s));
                when(userService.getEmployeeNames(eq(List.of(10L)), anyString())).thenReturn(ResponseEntity.ok(null));
                when(candidateService.getCandidateNames(eq(List.of(2L)), anyString()))
                                .thenReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());

                // act
                List<ScheduleDetailDTO> result = scheduleService.getSchedulesDetailed(
                                null, null, null, null,
                                null, null, null, null,
                                "token", null, null);

                // assert
                assertEquals(1, result.size());
                assertEquals(2, result.get(0).getParticipants().size());
                assertTrue(result.get(0).getParticipants().stream().allMatch(pdto -> "Unknown".equals(pdto.getName())));
        }

        @Test
        @DisplayName("SCH-SVC-TC-087: getSchedulesForStatistics - startDate only / endDate only -> rơi về findAll")
        void testGetSchedulesForStatistics_StartOnlyOrEndOnly_FallsBackFindAll_SCH_SVC_TC_087() {
                // arrange
                when(scheduleRepository.findAll(any(Sort.class))).thenReturn(List.of());

                // act
                scheduleService.getSchedulesForStatistics(LocalDate.of(2026, 4, 1), null, null, null);
                scheduleService.getSchedulesForStatistics(null, LocalDate.of(2026, 4, 30), null, null);

                // assert
                verify(scheduleRepository, times(2)).findAll(any(Sort.class));
        }

        @Test
        @DisplayName("SCH-SVC-TC-088: getSchedulesForStatistics - dateRange + status + meetingType, schedule.meetingType null bị loại")
        void testGetSchedulesForStatistics_DateRange_StatusAndMeetingType_NullMeetingTypeExcluded_SCH_SVC_TC_088() {
                // arrange
                LocalDate startDate = LocalDate.of(2026, 4, 1);
                LocalDate endDate = LocalDate.of(2026, 4, 30);

                Schedule nullType = new Schedule();
                nullType.setId(1L);
                nullType.setStatus("DONE");
                nullType.setMeetingType(null);
                nullType.setStartTime(startDate.atStartOfDay().plusDays(1));

                Schedule match = new Schedule();
                match.setId(2L);
                match.setStatus("DONE");
                match.setMeetingType(MeetingType.INTERVIEW);
                match.setStartTime(startDate.atStartOfDay().plusDays(2));

                when(scheduleRepository.findByStartTimeBetween(eq(startDate.atStartOfDay()), eq(endDate.atTime(LocalTime.MAX)),
                                any(Sort.class)))
                                .thenReturn(List.of(nullType, match));

                // act
                List<ScheduleStatisticsDTO> result = scheduleService.getSchedulesForStatistics(startDate, endDate, "DONE", "INTERVIEW");

                // assert
                assertEquals(1, result.size());
                assertEquals(2L, result.get(0).getId());
        }

        @Test
        @DisplayName("SCH-SVC-TC-089: getSchedulesForStatistics - status only + meetingType, schedule.meetingType null bị loại")
        void testGetSchedulesForStatistics_StatusOnly_WithMeetingType_NullMeetingTypeExcluded_SCH_SVC_TC_089() {
                // arrange
                Schedule nullType = new Schedule();
                nullType.setId(1L);
                nullType.setStatus("SCHEDULED");
                nullType.setMeetingType(null);

                Schedule match = new Schedule();
                match.setId(2L);
                match.setStatus("SCHEDULED");
                match.setMeetingType(MeetingType.INTERVIEW);

                Page<Schedule> page = new PageImpl<>(List.of(nullType, match));
                when(scheduleRepository.findByStatus(eq("SCHEDULED"), any(Pageable.class))).thenReturn(page);

                // act
                List<ScheduleStatisticsDTO> result = scheduleService.getSchedulesForStatistics(null, null, "SCHEDULED", "INTERVIEW");

                // assert
                assertEquals(1, result.size());
                assertEquals(2L, result.get(0).getId());
        }

        @Test
        @DisplayName("SCH-SVC-TC-090: getScheduleWithParticipantNames - candidateService non2xx -> fallback Unknown")
        void testGetScheduleWithParticipantNames_CandidateServiceNon2xx_FallbackUnknown_SCH_SVC_TC_090() {
                // arrange
                Long scheduleId = 1L;
                Schedule schedule = new Schedule();
                schedule.setId(scheduleId);
                ScheduleParticipant cand = new ScheduleParticipant();
                cand.setId(1L);
                cand.setParticipantType("CANDIDATE");
                cand.setParticipantId(2L);
                cand.setResponseStatus("PENDING");
                schedule.setParticipants(new HashSet<>(Set.of(cand)));
                when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));
                when(candidateService.getCandidateNames(eq(List.of(2L)), anyString()))
                                .thenReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());

                // act
                ScheduleDetailDTO dto = scheduleService.getScheduleWithParticipantNames(scheduleId, "token");

                // assert
                assertEquals(1, dto.getParticipants().size());
                assertEquals("Unknown", dto.getParticipants().get(0).getName());
        }

        @Test
        @DisplayName("SCH-SVC-TC-091: getSchedulesDetailed - participantId có nhưng participantType null -> bỏ qua filter")
        void testGetSchedulesDetailed_ParticipantIdOnly_NoFilter_SCH_SVC_TC_091() {
                // arrange
                when(scheduleRepository.findAll(any(Sort.class))).thenReturn(List.of(new Schedule()));

                // act
                List<ScheduleDetailDTO> result = scheduleService.getSchedulesDetailed(
                                null, null, null, null,
                                null, null, 10L, null,
                                "token", null, null);

                // assert
                assertEquals(1, result.size());
        }

        @Test
        @DisplayName("SCH-SVC-TC-092: getSchedulesDetailed - participantType có nhưng participantId null -> bỏ qua filter")
        void testGetSchedulesDetailed_ParticipantTypeOnly_NoFilter_SCH_SVC_TC_092() {
                // arrange
                when(scheduleRepository.findAll(any(Sort.class))).thenReturn(List.of(new Schedule()));

                // act
                List<ScheduleDetailDTO> result = scheduleService.getSchedulesDetailed(
                                null, null, null, null,
                                null, null, null, "USER",
                                "token", null, null);

                // assert
                assertEquals(1, result.size());
        }

        @Test
        @DisplayName("SCH-SVC-TC-093: getSchedulesDetailed - chỉ có CANDIDATE participants -> cover nhánh collect candidateIds")
        void testGetSchedulesDetailed_CandidateOnly_CollectCandidateIds_SCH_SVC_TC_093() {
                // arrange
                Schedule s = new Schedule();
                s.setId(1L);
                ScheduleParticipant cand = new ScheduleParticipant();
                cand.setParticipantType("CANDIDATE");
                cand.setParticipantId(2L);
                cand.setResponseStatus("PENDING");
                s.setParticipants(new HashSet<>(Set.of(cand)));

                when(scheduleRepository.findAll(any(Sort.class))).thenReturn(List.of(s));
                when(candidateService.getCandidateNames(eq(List.of(2L)), anyString()))
                                .thenReturn(ResponseEntity.ok(objectMapper.createObjectNode().put("2", "C")));

                // act
                List<ScheduleDetailDTO> result = scheduleService.getSchedulesDetailed(
                                null, null, null, null,
                                null, null, null, null,
                                "token", null, null);

                // assert
                assertEquals(1, result.size());
                assertEquals(1, result.get(0).getParticipants().size());
                assertEquals("C", result.get(0).getParticipants().get(0).getName());
        }

        @Test
        @DisplayName("SCH-SVC-TC-094: getSchedulesDetailed - userService non2xx, candidateService 2xx body -> không crash")
        void testGetSchedulesDetailed_UserNon2xx_Candidate2xxBody_SCH_SVC_TC_094() {
                // arrange
                Schedule s = new Schedule();
                s.setId(1L);
                ScheduleParticipant user = new ScheduleParticipant();
                user.setParticipantType("USER");
                user.setParticipantId(10L);
                user.setResponseStatus("PENDING");
                ScheduleParticipant cand = new ScheduleParticipant();
                cand.setParticipantType("CANDIDATE");
                cand.setParticipantId(2L);
                cand.setResponseStatus("PENDING");
                s.setParticipants(new HashSet<>(Set.of(user, cand)));

                when(scheduleRepository.findAll(any(Sort.class))).thenReturn(List.of(s));
                when(userService.getEmployeeNames(eq(List.of(10L)), anyString()))
                                .thenReturn(ResponseEntity.badRequest().build());
                when(candidateService.getCandidateNames(eq(List.of(2L)), anyString()))
                                .thenReturn(ResponseEntity.ok(objectMapper.createObjectNode().put("2", "C")));

                // act
                List<ScheduleDetailDTO> result = scheduleService.getSchedulesDetailed(
                                null, null, null, null,
                                null, null, null, null,
                                "token", null, null);

                // assert
                assertEquals(1, result.size());
                assertEquals(2, result.get(0).getParticipants().size());
                assertTrue(result.get(0).getParticipants().stream()
                                .anyMatch(p -> "USER".equalsIgnoreCase(p.getParticipantType()) && "Unknown".equals(p.getName())));
                assertTrue(result.get(0).getParticipants().stream()
                                .anyMatch(p -> "CANDIDATE".equalsIgnoreCase(p.getParticipantType()) && "C".equals(p.getName())));
        }

        @Test
        @DisplayName("SCH-SVC-TC-095: getSchedulesDetailed - participantId match nhưng participantType mismatch -> bị loại")
        void testGetSchedulesDetailed_ParticipantIdMatch_TypeMismatch_Excluded_SCH_SVC_TC_095() {
                // arrange
                Schedule s = new Schedule();
                s.setId(1L);
                ScheduleParticipant p = new ScheduleParticipant();
                p.setParticipantId(10L);
                p.setParticipantType("CANDIDATE");
                p.setResponseStatus("PENDING");
                s.setParticipants(new HashSet<>(Set.of(p)));

                when(scheduleRepository.findAll(any(Sort.class))).thenReturn(List.of(s));

                // act
                List<ScheduleDetailDTO> result = scheduleService.getSchedulesDetailed(
                                null, null, null, null,
                                null, null, 10L, "USER",
                                "token", null, null);

                // assert
                assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("SCH-SVC-TC-096: getSchedulesDetailed - participantType OTHER -> không add employee/candidate ids")
        void testGetSchedulesDetailed_OtherParticipantType_DoesNotCollectIds_SCH_SVC_TC_096() {
                // arrange
                Schedule s = new Schedule();
                s.setId(1L);
                ScheduleParticipant other = new ScheduleParticipant();
                other.setParticipantType("OTHER");
                other.setParticipantId(999L);
                other.setResponseStatus("PENDING");
                s.setParticipants(new HashSet<>(Set.of(other)));

                when(scheduleRepository.findAll(any(Sort.class))).thenReturn(List.of(s));

                // act
                List<ScheduleDetailDTO> result = scheduleService.getSchedulesDetailed(
                                null, null, null, null,
                                null, null, null, null,
                                "token", null, null);

                // assert
                assertEquals(1, result.size());
                verify(userService, never()).getEmployeeNames(anyList(), anyString());
                verify(candidateService, never()).getCandidateNames(anyList(), anyString());
        }

        @Test
        @DisplayName("SCH-SVC-TC-097: getSchedulesDetailed - candidateService 2xx nhưng body null -> fallback Unknown")
        void testGetSchedulesDetailed_CandidateService2xxNullBody_FallbackUnknown_SCH_SVC_TC_097() {
                // arrange
                Schedule s = new Schedule();
                s.setId(1L);
                ScheduleParticipant cand = new ScheduleParticipant();
                cand.setParticipantType("CANDIDATE");
                cand.setParticipantId(2L);
                cand.setResponseStatus("PENDING");
                s.setParticipants(new HashSet<>(Set.of(cand)));

                when(scheduleRepository.findAll(any(Sort.class))).thenReturn(List.of(s));
                when(candidateService.getCandidateNames(eq(List.of(2L)), anyString())).thenReturn(ResponseEntity.ok(null));

                // act
                List<ScheduleDetailDTO> result = scheduleService.getSchedulesDetailed(
                                null, null, null, null,
                                null, null, null, null,
                                "token", null, null);

                // assert
                assertEquals(1, result.size());
                assertEquals("Unknown", result.get(0).getParticipants().get(0).getName());
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