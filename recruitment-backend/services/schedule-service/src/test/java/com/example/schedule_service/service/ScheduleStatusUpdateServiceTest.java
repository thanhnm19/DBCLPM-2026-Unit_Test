package com.example.schedule_service.service;

import com.example.schedule_service.messaging.NotificationProducer;
import com.example.schedule_service.model.Schedule;
import com.example.schedule_service.model.ScheduleParticipant;
import com.example.schedule_service.repository.ScheduleRepository;
import com.example.schedule_service.utils.SecurityUtil;
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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ScheduleStatusUpdateServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private NotificationProducer notificationProducer;

    @InjectMocks
    private ScheduleStatusUpdateService scheduleStatusUpdateService;

    @BeforeEach
    void setUp() {
        // no-op
    }

    @Test
    @DisplayName("SCH-STATUS-TC-001: updateScheduleStatuses - chuyển các schedule cần hoàn tất sang DONE")
    void testUpdateScheduleStatuses_SCH_STATUS_TC_001() {
        // Testcase ID: SCH-STATUS-TC-001
        // Objective: Xác nhận chuyển các schedule cần hoàn tất sang DONE

        // arrange
        Schedule s1 = new Schedule();
        s1.setId(1L);
        s1.setStatus("IN_PROGRESS");

        Schedule s2 = new Schedule();
        s2.setId(2L);
        s2.setStatus("IN_PROGRESS");

        List<Schedule> toComplete = Arrays.asList(s1, s2);

        when(scheduleRepository.findSchedulesToComplete(any(LocalDateTime.class))).thenReturn(toComplete);

        // act
        scheduleStatusUpdateService.updateScheduleStatuses();

        // assert
        assertEquals("DONE", s1.getStatus());
        assertEquals("DONE", s2.getStatus());

        verify(scheduleRepository, times(1)).findSchedulesToComplete(any(LocalDateTime.class));
        verify(scheduleRepository, times(1)).saveAll(eq(toComplete));
    }

    @Test
    @DisplayName("SCH-STATUS-TC-002: updateScheduleStatuses - không save khi không có schedule cần cập nhật")
    void testUpdateScheduleStatuses_NoSchedules_SCH_STATUS_TC_002() {
        // Testcase ID: SCH-STATUS-TC-002
        // Objective: Xác nhận không save khi không có schedule cần cập nhật

        // arrange
        when(scheduleRepository.findSchedulesToComplete(any(LocalDateTime.class))).thenReturn(Collections.emptyList());

        // act
        scheduleStatusUpdateService.updateScheduleStatuses();

        // assert
        verify(scheduleRepository, times(1)).findSchedulesToComplete(any(LocalDateTime.class));
        verify(scheduleRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("SCH-STATUS-TC-003: sendReminderNotifications - gửi reminder đúng khi đến thời điểm nhắc")
    void testSendReminderNotifications_SCH_STATUS_TC_003() {
        // Testcase ID: SCH-STATUS-TC-003
        // Objective: Xác nhận gửi reminder đúng khi đến thời điểm nhắc

        // arrange
        LocalDateTime startTime = LocalDateTime.now().plusMinutes(2); // reminderTime=2 => reminderTimeMoment ~= now
        Integer reminderMinutes = 2;

        Schedule schedule = new Schedule();
        schedule.setId(11L);
        schedule.setTitle("Interview");
        schedule.setLocation("Room 101");
        schedule.setStartTime(startTime);
        schedule.setReminderTime(reminderMinutes);
        schedule.setReminderSent(false);

        ScheduleParticipant userParticipant = new ScheduleParticipant();
        userParticipant.setParticipantType("USER");
        userParticipant.setParticipantId(9001L);

        Set<ScheduleParticipant> participants = new HashSet<>();
        participants.add(userParticipant);
        schedule.setParticipants(participants);

        when(scheduleRepository.findSchedulesForReminder(any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(schedule));

        try (MockedStatic<SecurityUtil> mockedSecurity = org.mockito.Mockito.mockStatic(SecurityUtil.class)) {
            mockedSecurity.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("jwt-token"));

            // act
            scheduleStatusUpdateService.sendReminderNotifications();

            // assert
            assertEquals(Boolean.TRUE, schedule.getReminderSent());

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Long>> idsCaptor = (ArgumentCaptor<List<Long>>) (ArgumentCaptor<?>) ArgumentCaptor
                    .forClass(List.class);
            ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

            verify(notificationProducer, times(1)).sendNotificationToMultiple(
                    idsCaptor.capture(),
                    titleCaptor.capture(),
                    messageCaptor.capture(),
                    anyString());

            List<Long> capturedIds = idsCaptor.getValue();
            assertNotNull(capturedIds);
            assertEquals(1, capturedIds.size());
            assertEquals(9001L, capturedIds.get(0));

            assertEquals("Nhắc nhở lịch hẹn", titleCaptor.getValue());

            String msg = messageCaptor.getValue();
            assertNotNull(msg);
            assertTrue(msg.contains("Bạn có lịch hẹn"));
            assertTrue(msg.contains("Interview"));
            assertTrue(msg.contains("Room 101"));
            assertTrue(msg.contains(reminderMinutes.toString()));

            verify(scheduleRepository, times(1)).saveAll(eq(Collections.singletonList(schedule)));
        }
    }

    @Test
    @DisplayName("SCH-STATUS-TC-004: sendReminderNotifications - kết thúc sớm khi không có schedule cần reminder")
    void testSendReminderNotifications_NoPotentialReminders_SCH_STATUS_TC_004() {
        // Testcase ID: SCH-STATUS-TC-004
        // Objective: Xác nhận kết thúc sớm khi không có schedule cần reminder

        // arrange
        when(scheduleRepository.findSchedulesForReminder(any(LocalDateTime.class))).thenReturn(Collections.emptyList());

        // act
        scheduleStatusUpdateService.sendReminderNotifications();

        // assert
        verify(scheduleRepository, times(1)).findSchedulesForReminder(any(LocalDateTime.class));
        verify(notificationProducer, never()).sendNotificationToMultiple(anyList(), anyString(), anyString(), any());
        verify(scheduleRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("SCH-STATUS-TC-005: sendReminderNotifications - vẫn set reminderSent khi không có người nhận reminder")
    void testSendReminderNotifications_NoUserParticipants_SCH_STATUS_TC_005() {
        // Testcase ID: SCH-STATUS-TC-005
        // Objective: Xác nhận vẫn set reminderSent=true dù không có người nhận reminder

        // arrange
        LocalDateTime startTime = LocalDateTime.now().plusMinutes(1); // reminderTime=1 => reminder moment ~= now
        Integer reminderMinutes = 1;

        Schedule schedule = new Schedule();
        schedule.setId(22L);
        schedule.setTitle("No User");
        schedule.setStartTime(startTime);
        schedule.setReminderTime(reminderMinutes);
        schedule.setReminderSent(false);

        // participants only CANDIDATE (no USER)
        ScheduleParticipant candidate = new ScheduleParticipant();
        candidate.setParticipantType("CANDIDATE");
        candidate.setParticipantId(7001L);

        Set<ScheduleParticipant> participants = new HashSet<>();
        participants.add(candidate);
        schedule.setParticipants(participants);

        when(scheduleRepository.findSchedulesForReminder(any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(schedule));

        try (MockedStatic<SecurityUtil> mockedSecurity = org.mockito.Mockito.mockStatic(SecurityUtil.class)) {
            mockedSecurity.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.empty());

            // act
            scheduleStatusUpdateService.sendReminderNotifications();

            // assert
            assertEquals(Boolean.TRUE, schedule.getReminderSent());

            verify(notificationProducer, never()).sendNotificationToMultiple(anyList(), anyString(), anyString(),
                    any());
            verify(scheduleRepository, times(1)).saveAll(eq(Collections.singletonList(schedule)));
        }
    }

    @Test
    @DisplayName("SCH-STATUS-TC-006: sendReminderNotifications - lỗi gửi một schedule không làm crash toàn bộ job")
    void testSendReminderNotifications_ProducerErrorDoesNotCrash_SCH_STATUS_TC_006() {
        // Testcase ID: SCH-STATUS-TC-006
        // Objective: Xác nhận một schedule lỗi gửi reminder không làm crash toàn bộ job

        // arrange
        LocalDateTime startTime1 = LocalDateTime.now().plusMinutes(2);
        LocalDateTime startTime2 = LocalDateTime.now().plusMinutes(2);

        Schedule scheduleFail = new Schedule();
        scheduleFail.setId(101L);
        scheduleFail.setTitle("Will Fail");
        scheduleFail.setLocation("L1");
        scheduleFail.setStartTime(startTime1);
        scheduleFail.setReminderTime(2);
        scheduleFail.setReminderSent(false);

        ScheduleParticipant user1 = new ScheduleParticipant();
        user1.setParticipantType("USER");
        user1.setParticipantId(1L);
        Set<ScheduleParticipant> failParticipants = new HashSet<>();
        failParticipants.add(user1);
        scheduleFail.setParticipants(failParticipants);

        Schedule scheduleOk = new Schedule();
        scheduleOk.setId(102L);
        scheduleOk.setTitle("Will Pass");
        scheduleOk.setLocation("L2");
        scheduleOk.setStartTime(startTime2);
        scheduleOk.setReminderTime(2);
        scheduleOk.setReminderSent(false);

        ScheduleParticipant user2 = new ScheduleParticipant();
        user2.setParticipantType("USER");
        user2.setParticipantId(2L);
        Set<ScheduleParticipant> okParticipants = new HashSet<>();
        okParticipants.add(user2);
        scheduleOk.setParticipants(okParticipants);

        when(scheduleRepository.findSchedulesForReminder(any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(scheduleFail, scheduleOk));

        // fail only when sending to participantId = 1L
        doThrow(new RuntimeException("Producer error"))
                .when(notificationProducer)
                .sendNotificationToMultiple(eq(Collections.singletonList(1L)), anyString(), anyString(), any());

        try (MockedStatic<SecurityUtil> mockedSecurity = org.mockito.Mockito.mockStatic(SecurityUtil.class)) {
            mockedSecurity.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("jwt"));

            // act + assert (must not throw)
            assertDoesNotThrow(() -> scheduleStatusUpdateService.sendReminderNotifications());

            // scheduleFail: exception path => reminderSent should remain false (source code
            // sets true only after successful send)
            assertEquals(Boolean.FALSE, scheduleFail.getReminderSent());

            // scheduleOk: should be processed normally
            assertEquals(Boolean.TRUE, scheduleOk.getReminderSent());

            verify(notificationProducer, times(1))
                    .sendNotificationToMultiple(eq(Collections.singletonList(1L)), anyString(), anyString(), any());

            verify(notificationProducer, times(1))
                    .sendNotificationToMultiple(eq(Collections.singletonList(2L)), anyString(), anyString(), any());

            // saveAll should be called with both schedules in schedulesToRemind
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Schedule>> saveCaptor = (ArgumentCaptor<List<Schedule>>) (ArgumentCaptor<?>) ArgumentCaptor
                    .forClass(List.class);
            verify(scheduleRepository, times(1)).saveAll(saveCaptor.capture());

            List<Schedule> saved = saveCaptor.getValue();
            assertNotNull(saved);
            assertEquals(2, saved.size());
        }
    }

    @Test
    @DisplayName("SCH-STATUS-TC-007: sendReminderNotifications - bỏ qua schedule thiếu startTime/reminderTime và không saveAll nếu không có cái nào đến giờ")
    void testSendReminderNotifications_SkipInvalidAndNoDue_RemainsNoSave_SCH_STATUS_TC_007() {
        // Testcase ID: SCH-STATUS-TC-007
        // Objective: Cover nhánh continue khi thiếu startTime/reminderTime + nhánh
        // schedulesToRemind empty => return sớm

        // arrange
        Schedule missingStart = new Schedule();
        missingStart.setId(1L);
        missingStart.setReminderTime(10);
        missingStart.setStartTime(null);

        Schedule missingReminder = new Schedule();
        missingReminder.setId(2L);
        missingReminder.setStartTime(LocalDateTime.now().plusHours(1));
        missingReminder.setReminderTime(null);

        Schedule notDue = new Schedule();
        notDue.setId(3L);
        notDue.setStartTime(LocalDateTime.now().plusHours(2));
        notDue.setReminderTime(10);
        notDue.setParticipants(new HashSet<>());

        when(scheduleRepository.findSchedulesForReminder(any(LocalDateTime.class)))
                .thenReturn(List.of(missingStart, missingReminder, notDue));

        // act
        scheduleStatusUpdateService.sendReminderNotifications();

        // assert
        verify(notificationProducer, never()).sendNotificationToMultiple(anyList(), anyString(), anyString(), any());
        verify(scheduleRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("SCH-STATUS-TC-008: sendReminderNotifications - participants null -> không gửi, vẫn set reminderSent=true và saveAll")
    void testSendReminderNotifications_NullParticipants_SetReminderSent_SaveAll_SCH_STATUS_TC_008() {
        // Testcase ID: SCH-STATUS-TC-008
        // Objective: Cover nhánh participantIds empty do participants null => set
        // reminderSent=true

        // arrange
        Schedule schedule = new Schedule();
        schedule.setId(10L);
        schedule.setTitle("Null participants");
        schedule.setStartTime(LocalDateTime.now().plusMinutes(1));
        schedule.setReminderTime(1);
        schedule.setReminderSent(false);
        schedule.setParticipants(null);

        when(scheduleRepository.findSchedulesForReminder(any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(schedule));

        try (MockedStatic<SecurityUtil> mockedSecurity = org.mockito.Mockito.mockStatic(SecurityUtil.class)) {
            mockedSecurity.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.empty());

            // act
            scheduleStatusUpdateService.sendReminderNotifications();

            // assert
            assertEquals(Boolean.TRUE, schedule.getReminderSent());
            verify(notificationProducer, never()).sendNotificationToMultiple(anyList(), anyString(), anyString(),
                    any());
            verify(scheduleRepository, times(1)).saveAll(eq(Collections.singletonList(schedule)));
        }
    }
}