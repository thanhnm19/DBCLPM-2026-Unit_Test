package com.example.schedule_service.service;

import com.example.schedule_service.messaging.NotificationProducer;
import com.example.schedule_service.model.Schedule;
import com.example.schedule_service.model.ScheduleParticipant;
import com.example.schedule_service.repository.ScheduleRepository;
import com.example.schedule_service.utils.SecurityUtil;
import com.example.schedule_service.utils.enums.MeetingType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Slice JPA (H2) cho {@link ScheduleStatusUpdateService}: repository thật để CheckDB,
 * mock {@link NotificationProducer}. Các nhánh chỉ khả thi với spy entity giữ ở {@link MessageBuildingSpyCases}.
 */
@DataJpaTest
@Import(ScheduleStatusUpdateService.class)
@ActiveProfiles("test")
@Transactional
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ScheduleStatusUpdateServiceTest {

    @SpyBean
    private ScheduleRepository scheduleRepository;

    @Autowired
    private ScheduleStatusUpdateService scheduleStatusUpdateService;

    @MockitoBean
    private NotificationProducer notificationProducer;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void resetSpyAndClearDatabase() {
        reset(scheduleRepository);
        scheduleRepository.deleteAll();
    }

    private void flushAndClearPersistenceContext() {
        entityManager.flush();
        entityManager.clear();
    }

    private Schedule reloadScheduleFromDb(Long scheduleId) {
        return scheduleRepository.findById(scheduleId).orElseThrow();
    }

    /** Lịch đủ điều kiện {@link ScheduleRepository#findSchedulesToComplete}: SCHEDULED và đã quá endTime. */
    private Schedule persistScheduleEligibleForCompletion(LocalDateTime endTime) {
        Schedule schedule = new Schedule();
        schedule.setTitle("completion-candidate");
        schedule.setDescription("d");
        schedule.setFormat("ONLINE");
        schedule.setMeetingType(MeetingType.INTERVIEW);
        schedule.setStatus("SCHEDULED");
        schedule.setLocation("Room");
        schedule.setStartTime(endTime.minusHours(1));
        schedule.setEndTime(endTime);
        schedule.setCreatedById(1L);
        schedule.setParticipants(new HashSet<>());
        return scheduleRepository.saveAndFlush(schedule);
    }

    /** Lịch đủ điều kiện {@link ScheduleRepository#findSchedulesForReminder}: SCHEDULED, reminder chưa gửi, start trong tương lai. */
    private Schedule persistScheduleEligibleForReminder(
            LocalDateTime startTime,
            int reminderMinutes,
            boolean reminderAlreadySent,
            Set<ScheduleParticipant> participants) {
        Schedule schedule = new Schedule();
        schedule.setTitle("reminder-candidate");
        schedule.setDescription("d");
        schedule.setFormat("ONLINE");
        schedule.setMeetingType(MeetingType.INTERVIEW);
        schedule.setStatus("SCHEDULED");
        schedule.setLocation("Room 101");
        schedule.setStartTime(startTime);
        schedule.setEndTime(startTime.plusHours(1));
        schedule.setReminderTime(reminderMinutes);
        schedule.setReminderSent(reminderAlreadySent);
        schedule.setParticipants(new HashSet<>());
        Schedule saved = scheduleRepository.saveAndFlush(schedule);
        if (participants != null) {
            for (ScheduleParticipant participant : participants) {
                participant.setSchedule(saved);
                saved.getParticipants().add(participant);
            }
            return scheduleRepository.saveAndFlush(saved);
        }
        return saved;
    }

    @Test
    @DisplayName("SCH-STAT-UPD-001: updateScheduleStatuses — đánh dấu DONE và lưu xuống DB")
    void updateScheduleStatuses_whenSchedulesAreDue_marksDoneAndPersists() {
        // Test Case ID: SCH-STAT-UPD-001

        // Arrange
        LocalDateTime now = LocalDateTime.now();
        Schedule firstDueSchedule = persistScheduleEligibleForCompletion(now.minusMinutes(5));
        Schedule secondDueSchedule = persistScheduleEligibleForCompletion(now.minusMinutes(1));

        // Act
        scheduleStatusUpdateService.updateScheduleStatuses();

        // Assert
        verify(scheduleRepository, times(1)).findSchedulesToComplete(any(LocalDateTime.class));
        verify(scheduleRepository, times(1)).saveAll(anyList());

        // CheckDB
        flushAndClearPersistenceContext();
        Schedule reloadedFirst = reloadScheduleFromDb(firstDueSchedule.getId());
        Schedule reloadedSecond = reloadScheduleFromDb(secondDueSchedule.getId());
        assertEquals("DONE", reloadedFirst.getStatus(), "CheckDB — schedule 1 status");
        assertEquals("DONE", reloadedSecond.getStatus(), "CheckDB — schedule 2 status");
    }

    @Test
    @DisplayName("SCH-STAT-UPD-002: updateScheduleStatuses — không gọi saveAll khi không có lịch cần hoàn tất")
    void updateScheduleStatuses_whenNoDueSchedules_doesNotPersist() {
        // Test Case ID: SCH-STAT-UPD-002

        // Arrange
        Schedule notYetCompletable = persistScheduleEligibleForCompletion(LocalDateTime.now().plusHours(2));

        // Act
        scheduleStatusUpdateService.updateScheduleStatuses();

        // Assert
        verify(scheduleRepository, times(1)).findSchedulesToComplete(any(LocalDateTime.class));
        verify(scheduleRepository, never()).saveAll(anyList());

        // CheckDB
        flushAndClearPersistenceContext();
        assertEquals("SCHEDULED", reloadScheduleFromDb(notYetCompletable.getId()).getStatus(),
                "CheckDB — chưa tới endTime nên vẫn SCHEDULED");
    }

    @Test
    @DisplayName("SCH-STAT-UPD-003: updateScheduleStatuses — lỗi khi query, không save")
    void updateScheduleStatuses_whenFindThrows_propagatesAndSkipsSave() {
        // Test Case ID: SCH-STAT-UPD-003

        // Arrange
        doThrow(new RuntimeException("repo-error"))
                .when(scheduleRepository).findSchedulesToComplete(any(LocalDateTime.class));

        // Act
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> scheduleStatusUpdateService.updateScheduleStatuses());

        // Assert
        assertEquals("repo-error", thrown.getMessage());
        verify(scheduleRepository, never()).saveAll(anyList());
        // CheckDB — không có thay đổi DB khi find ném lỗi (không gọi saveAll)
    }

    @Test
    @DisplayName("SCH-STAT-UPD-004: updateScheduleStatuses — lỗi saveAll vẫn giữ trạng thái cũ trên DB")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void updateScheduleStatuses_whenSaveAllThrows_leavesDbUnchanged() {
        // Test Case ID: SCH-STAT-UPD-004

        // Arrange
        Schedule dueSchedule = persistScheduleEligibleForCompletion(LocalDateTime.now().minusMinutes(1));
        doThrow(new RuntimeException("saveAll-error"))
                .when(scheduleRepository).saveAll(anyList());

        // Act
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> scheduleStatusUpdateService.updateScheduleStatuses());

        // Assert
        assertEquals("saveAll-error", thrown.getMessage());

        // CheckDB — NOT_SUPPORTED để thấy rollback của transaction trong service, không dùng cùng persistence context với test
        entityManager.clear();
        assertEquals("SCHEDULED", reloadScheduleFromDb(dueSchedule.getId()).getStatus(),
                "CheckDB — rollback khi saveAll lỗi");
    }

    @Test
    @DisplayName("SCH-STAT-UPD-005: sendReminderNotifications — gửi Kafka và cập nhật reminderSent trên DB")
    void sendReminderNotifications_whenDue_sendsNotificationAndPersistsFlag() {
        // Test Case ID: SCH-STAT-UPD-005

        // Arrange
        LocalDateTime interviewStart = LocalDateTime.now().plusMinutes(2);
        int reminderLeadMinutes = 2;
        ScheduleParticipant interviewer = new ScheduleParticipant();
        interviewer.setParticipantType("USER");
        interviewer.setParticipantId(9001L);
        Schedule persistedSchedule = persistScheduleEligibleForReminder(
                interviewStart, reminderLeadMinutes, false, Set.of(interviewer));

        try (MockedStatic<SecurityUtil> mockedSecurity = org.mockito.Mockito.mockStatic(SecurityUtil.class)) {
            mockedSecurity.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("jwt-token"));

            // Act
            scheduleStatusUpdateService.sendReminderNotifications();

            // Assert
            ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
            verify(notificationProducer, times(1)).sendNotificationToMultiple(
                    eq(List.of(9001L)),
                    eq("Nhắc nhở lịch hẹn"),
                    messageCaptor.capture(),
                    eq("jwt-token"));
            String composedMessage = messageCaptor.getValue();
            assertNotNull(composedMessage);
            assertTrue(composedMessage.contains("Bạn có lịch hẹn"));
            assertTrue(composedMessage.contains("reminder-candidate"));
            assertTrue(composedMessage.contains("Room 101"));
            assertTrue(composedMessage.contains(Integer.toString(reminderLeadMinutes)));
        }

        // CheckDB
        flushAndClearPersistenceContext();
        assertTrue(Boolean.TRUE.equals(reloadScheduleFromDb(persistedSchedule.getId()).getReminderSent()),
                "CheckDB — reminderSent sau khi gửi");
    }

    @Test
    @DisplayName("SCH-STAT-UPD-006: sendReminderNotifications — không có lịch reminder thì thoát sớm")
    void sendReminderNotifications_whenRepositoryReturnsEmpty_doesNotNotifyOrSave() {
        // Test Case ID: SCH-STAT-UPD-006

        // Arrange
        // không insert bản ghi thỏa query reminder

        // Act
        scheduleStatusUpdateService.sendReminderNotifications();

        // Assert
        verify(notificationProducer, never()).sendNotificationToMultiple(anyList(), anyString(), anyString(), any());
        verify(scheduleRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("SCH-STAT-UPD-007: sendReminderNotifications — chỉ CANDIDATE: không gửi nhưng vẫn lưu reminderSent")
    void sendReminderNotifications_whenOnlyCandidateParticipants_skipsKafkaButSetsFlagInDb() {
        // Test Case ID: SCH-STAT-UPD-007

        // Arrange
        LocalDateTime startTime = LocalDateTime.now().plusMinutes(1);
        ScheduleParticipant candidateOnly = new ScheduleParticipant();
        candidateOnly.setParticipantType("CANDIDATE");
        candidateOnly.setParticipantId(7001L);
        Schedule persistedSchedule = persistScheduleEligibleForReminder(
                startTime, 1, false, Set.of(candidateOnly));

        try (MockedStatic<SecurityUtil> mockedSecurity = org.mockito.Mockito.mockStatic(SecurityUtil.class)) {
            mockedSecurity.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.empty());

            // Act
            scheduleStatusUpdateService.sendReminderNotifications();

            // Assert
            verify(notificationProducer, never()).sendNotificationToMultiple(anyList(), anyString(), anyString(),
                    any());
        }

        // CheckDB
        flushAndClearPersistenceContext();
        assertTrue(Boolean.TRUE.equals(reloadScheduleFromDb(persistedSchedule.getId()).getReminderSent()),
                "CheckDB — vẫn đánh dấu đã xử lý reminder");
    }

    @Test
    @DisplayName("SCH-STAT-UPD-008: sendReminderNotifications — một lịch lỗi producer không chặn lịch khác")
    void sendReminderNotifications_whenOneProducerCallFails_continuesAndPersistsBoth() {
        // Test Case ID: SCH-STAT-UPD-008

        // Arrange
        LocalDateTime sharedStart = LocalDateTime.now().plusMinutes(2);
        ScheduleParticipant userOnFailingSchedule = new ScheduleParticipant();
        userOnFailingSchedule.setParticipantType("USER");
        userOnFailingSchedule.setParticipantId(1L);
        Schedule failingSchedule = persistScheduleEligibleForReminder(
                sharedStart, 2, false, Set.of(userOnFailingSchedule));

        ScheduleParticipant userOnSuccessSchedule = new ScheduleParticipant();
        userOnSuccessSchedule.setParticipantType("USER");
        userOnSuccessSchedule.setParticipantId(2L);
        Schedule successSchedule = persistScheduleEligibleForReminder(
                sharedStart, 2, false, Set.of(userOnSuccessSchedule));

        doThrow(new RuntimeException("Producer error"))
                .when(notificationProducer)
                .sendNotificationToMultiple(eq(Collections.singletonList(1L)), anyString(), anyString(), any());

        try (MockedStatic<SecurityUtil> mockedSecurity = org.mockito.Mockito.mockStatic(SecurityUtil.class)) {
            mockedSecurity.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("jwt"));

            // Act
            assertDoesNotThrow(() -> scheduleStatusUpdateService.sendReminderNotifications());

            // Assert
            verify(notificationProducer, times(1))
                    .sendNotificationToMultiple(eq(Collections.singletonList(1L)), anyString(), anyString(), any());
            verify(notificationProducer, times(1))
                    .sendNotificationToMultiple(eq(Collections.singletonList(2L)), anyString(), anyString(), any());
        }

        // CheckDB
        flushAndClearPersistenceContext();
        assertFalse(Boolean.TRUE.equals(reloadScheduleFromDb(failingSchedule.getId()).getReminderSent()),
                "CheckDB — lịch lỗi gửi vẫn chưa reminderSent");
        assertTrue(Boolean.TRUE.equals(reloadScheduleFromDb(successSchedule.getId()).getReminderSent()),
                "CheckDB — lịch thành công đã reminderSent");
    }

    @Test
    @DisplayName("SCH-STAT-UPD-009: sendReminderNotifications — bỏ qua bản ghi không hợp lệ / chưa tới giờ (stub query)")
    void sendReminderNotifications_whenStubbedListHasOnlyInvalidOrNotDue_skipsSave() {
        // Test Case ID: SCH-STAT-UPD-009

        // Arrange
        Schedule missingStartTime = new Schedule();
        missingStartTime.setId(1L);
        missingStartTime.setReminderTime(10);
        missingStartTime.setStartTime(null);

        Schedule missingReminderMinutes = new Schedule();
        missingReminderMinutes.setId(2L);
        missingReminderMinutes.setStartTime(LocalDateTime.now().plusHours(1));
        missingReminderMinutes.setReminderTime(null);

        Schedule notYetDue = new Schedule();
        notYetDue.setId(3L);
        notYetDue.setStartTime(LocalDateTime.now().plusHours(2));
        notYetDue.setReminderTime(10);
        notYetDue.setParticipants(new HashSet<>());

        doReturn(List.of(missingStartTime, missingReminderMinutes, notYetDue))
                .when(scheduleRepository).findSchedulesForReminder(any(LocalDateTime.class));

        // Act
        scheduleStatusUpdateService.sendReminderNotifications();

        // Assert
        verify(notificationProducer, never()).sendNotificationToMultiple(anyList(), anyString(), anyString(), any());
        verify(scheduleRepository, never()).saveAll(anyList());

        // CheckDB — không tạo thêm dòng từ stub (ID giả)
        flushAndClearPersistenceContext();
        assertEquals(0L, scheduleRepository.count(), "CheckDB — không persist entity stub");
    }

    @Test
    @DisplayName("SCH-STAT-UPD-011: sendReminderNotifications — participants rỗng: không gửi, lưu flag")
    void sendReminderNotifications_whenParticipantsEmpty_setsFlagWithoutKafka() {
        // Test Case ID: SCH-STAT-UPD-011

        // Arrange
        Schedule persistedSchedule = persistScheduleEligibleForReminder(
                LocalDateTime.now().plusMinutes(1), 1, false, Collections.emptySet());

        // Act
        scheduleStatusUpdateService.sendReminderNotifications();

        // Assert
        verify(notificationProducer, never()).sendNotificationToMultiple(anyList(), anyString(), anyString(), any());

        // CheckDB
        flushAndClearPersistenceContext();
        assertTrue(Boolean.TRUE.equals(reloadScheduleFromDb(persistedSchedule.getId()).getReminderSent()));
    }

    @Test
    @DisplayName("SCH-STAT-UPD-012: USER participantId null — coi như không có người nhận")
    void sendReminderNotifications_whenUserIdNull_setsFlagWithoutKafka() {
        // Test Case ID: SCH-STAT-UPD-012

        // Arrange
        ScheduleParticipant userWithNullId = new ScheduleParticipant();
        userWithNullId.setParticipantType("USER");
        userWithNullId.setParticipantId(null);
        Schedule persistedSchedule = persistScheduleEligibleForReminder(
                LocalDateTime.now().plusMinutes(1), 1, false, Set.of(userWithNullId));

        // Act
        scheduleStatusUpdateService.sendReminderNotifications();

        // Assert
        verify(notificationProducer, never()).sendNotificationToMultiple(anyList(), anyString(), anyString(), any());

        // CheckDB
        flushAndClearPersistenceContext();
        assertTrue(Boolean.TRUE.equals(reloadScheduleFromDb(persistedSchedule.getId()).getReminderSent()));
    }

    @Test
    @DisplayName("SCH-STAT-UPD-013: sendReminderNotifications — title/location null vẫn gửi message tối giản")
    void sendReminderNotifications_whenTitleAndLocationNull_stillNotifiesWithCoreMessage() {
        // Test Case ID: SCH-STAT-UPD-013

        // Arrange
        LocalDateTime startTime = LocalDateTime.now().plusMinutes(1);
        ScheduleParticipant user = new ScheduleParticipant();
        user.setParticipantType("USER");
        user.setParticipantId(55L);
        Schedule persistedSchedule = persistScheduleEligibleForReminder(startTime, 1, false, Set.of(user));
        persistedSchedule.setTitle(null);
        persistedSchedule.setLocation(null);
        persistedSchedule = scheduleRepository.saveAndFlush(persistedSchedule);

        try (MockedStatic<SecurityUtil> mockedSecurity = org.mockito.Mockito.mockStatic(SecurityUtil.class)) {
            mockedSecurity.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("jwt"));

            // Act
            scheduleStatusUpdateService.sendReminderNotifications();

            // Assert
            ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
            verify(notificationProducer, times(1)).sendNotificationToMultiple(
                    eq(Collections.singletonList(55L)),
                    eq("Nhắc nhở lịch hẹn"),
                    messageCaptor.capture(),
                    eq("jwt"));
            String composed = messageCaptor.getValue();
            assertTrue(composed.startsWith("Bạn có lịch hẹn: "));
            assertTrue(composed.contains(" vào "));
        }

        // CheckDB
        flushAndClearPersistenceContext();
        assertTrue(Boolean.TRUE.equals(reloadScheduleFromDb(persistedSchedule.getId()).getReminderSent()));
    }

    @Test
    @DisplayName("SCH-STAT-UPD-014: sendReminderNotifications — saveAll lỗi sau khi gửi")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void sendReminderNotifications_whenSaveAllFailsAfterSend_propagates() {
        // Test Case ID: SCH-STAT-UPD-014

        // Arrange
        LocalDateTime startTime = LocalDateTime.now().plusMinutes(1);
        ScheduleParticipant user = new ScheduleParticipant();
        user.setParticipantType("USER");
        user.setParticipantId(99L);
        Schedule persistedSchedule = persistScheduleEligibleForReminder(startTime, 1, false, Set.of(user));
        doThrow(new RuntimeException("saveAll-error"))
                .when(scheduleRepository).saveAll(anyList());

        try (MockedStatic<SecurityUtil> mockedSecurity = org.mockito.Mockito.mockStatic(SecurityUtil.class)) {
            mockedSecurity.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("jwt"));

            // Act
            RuntimeException thrown = assertThrows(RuntimeException.class,
                    () -> scheduleStatusUpdateService.sendReminderNotifications());

            // Assert
            assertEquals("saveAll-error", thrown.getMessage());
            verify(notificationProducer, times(1))
                    .sendNotificationToMultiple(eq(Collections.singletonList(99L)), anyString(), anyString(), any());
        }

        // CheckDB — NOT_SUPPORTED để đọc trạng thái sau rollback của transaction service
        entityManager.clear();
        assertFalse(Boolean.TRUE.equals(reloadScheduleFromDb(persistedSchedule.getId()).getReminderSent()),
                "CheckDB — không commit reminderSent khi saveAll lỗi");
    }

    @Test
    @DisplayName("SCH-STAT-UPD-015: sendReminderNotifications — đã quá cửa sổ reminder (>1 phút) thì không lưu")
    void sendReminderNotifications_whenReminderWindowPassed_doesNotMarkSent() {
        // Test Case ID: SCH-STAT-UPD-015

        // Arrange
        // startTime trong tương lai (để query trả về) nhưng (start - reminder) << now − 1 phút
        LocalDateTime futureStart = LocalDateTime.now().plusMinutes(5);
        int reminderLeadMinutes = 10;
        Schedule persistedSchedule = persistScheduleEligibleForReminder(
                futureStart, reminderLeadMinutes, false, Collections.emptySet());

        // Act
        scheduleStatusUpdateService.sendReminderNotifications();

        // Assert
        verify(notificationProducer, never()).sendNotificationToMultiple(anyList(), anyString(), anyString(), any());
        verify(scheduleRepository, never()).saveAll(anyList());

        // CheckDB
        flushAndClearPersistenceContext();
        assertFalse(Boolean.TRUE.equals(reloadScheduleFromDb(persistedSchedule.getId()).getReminderSent()));
    }

    /**
     * Các nhánh chỉ khả thi khi {@link Schedule#getReminderTime()} / {@link Schedule#getStartTime()} trả về khác
     * nhau giữa các lần gọi (không thể với entity JPA thường) — giữ mock thuần để cover nhánh defensively.
     */
    @Nested
    @DisplayName("Message building — spy edge cases (không CheckDB)")
    @ExtendWith(MockitoExtension.class)
    class MessageBuildingSpyCases {

        @Mock
        private ScheduleRepository mockScheduleRepository;

        @Mock
        private NotificationProducer mockNotificationProducer;

        private ScheduleStatusUpdateService scheduleStatusUpdateServiceUnderTest;

        @BeforeEach
        void createServiceWithMocks() {
            scheduleStatusUpdateServiceUnderTest = new ScheduleStatusUpdateService(
                    mockScheduleRepository, mockNotificationProducer);
        }

        @Test
        @DisplayName("SCH-STAT-UPD-010: participants null — không Kafka, vẫn saveAll (mock repo)")
        void sendReminderNotifications_whenParticipantsNull_setsFlagWithoutKafka() {
            // Test Case ID: SCH-STAT-UPD-010

            // Arrange — JPA không cho participants=null an toàn (orphanRemoval); dùng mock như nguồn findSchedulesForReminder
            Schedule scheduleWithNullParticipants = new Schedule();
            scheduleWithNullParticipants.setId(10L);
            scheduleWithNullParticipants.setTitle("Null participants");
            scheduleWithNullParticipants.setStatus("SCHEDULED");
            scheduleWithNullParticipants.setStartTime(LocalDateTime.now().plusMinutes(1));
            scheduleWithNullParticipants.setReminderTime(1);
            scheduleWithNullParticipants.setReminderSent(false);
            scheduleWithNullParticipants.setParticipants(null);

            when(mockScheduleRepository.findSchedulesForReminder(any(LocalDateTime.class)))
                    .thenReturn(Collections.singletonList(scheduleWithNullParticipants));

            try (MockedStatic<SecurityUtil> mockedSecurity = org.mockito.Mockito.mockStatic(SecurityUtil.class)) {
                mockedSecurity.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.empty());

                // Act
                scheduleStatusUpdateServiceUnderTest.sendReminderNotifications();

                // Assert
                verify(mockNotificationProducer, never()).sendNotificationToMultiple(
                        anyList(), anyString(), anyString(), any());
                assertTrue(Boolean.TRUE.equals(scheduleWithNullParticipants.getReminderSent()));
                verify(mockScheduleRepository, times(1)).saveAll(eq(Collections.singletonList(scheduleWithNullParticipants)));
            }
        }

        @Test
        @DisplayName("SCH-STAT-UPD-016: reminderTime null khi build message (spy)")
        void sendReminderNotifications_whenReminderTimeBecomesNullWhileBuilding_omitsMinutesSuffix() {
            // Test Case ID: SCH-STAT-UPD-016

            // Arrange
            Schedule scheduleSpy = spy(new Schedule());
            scheduleSpy.setId(14L);
            scheduleSpy.setTitle(null);
            scheduleSpy.setLocation(null);
            scheduleSpy.setReminderSent(false);
            LocalDateTime startTime = LocalDateTime.now().plusMinutes(1);
            doReturn(startTime).when(scheduleSpy).getStartTime();
            doReturn(1, 1, (Integer) null).when(scheduleSpy).getReminderTime();

            ScheduleParticipant user = new ScheduleParticipant();
            user.setParticipantType("USER");
            user.setParticipantId(55L);
            scheduleSpy.setParticipants(new HashSet<>(Set.of(user)));

            when(mockScheduleRepository.findSchedulesForReminder(any(LocalDateTime.class)))
                    .thenReturn(Collections.singletonList(scheduleSpy));

            try (MockedStatic<SecurityUtil> mockedSecurity = org.mockito.Mockito.mockStatic(SecurityUtil.class)) {
                mockedSecurity.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("jwt"));

                // Act
                scheduleStatusUpdateServiceUnderTest.sendReminderNotifications();

                // Assert
                ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
                verify(mockNotificationProducer, times(1)).sendNotificationToMultiple(
                        eq(Collections.singletonList(55L)),
                        eq("Nhắc nhở lịch hẹn"),
                        messageCaptor.capture(),
                        eq("jwt"));
                String message = messageCaptor.getValue();
                assertTrue(message.startsWith("Bạn có lịch hẹn: "));
                assertTrue(message.contains(" vào "));
                verify(mockScheduleRepository, times(1)).saveAll(eq(Collections.singletonList(scheduleSpy)));
            }
        }

        @Test
        @DisplayName("SCH-STAT-UPD-017: startTime null khi build message (spy)")
        void sendReminderNotifications_whenStartTimeBecomesNullWhileBuilding_skipsTimeClause() {
            // Test Case ID: SCH-STAT-UPD-017

            // Arrange
            Schedule scheduleSpy = spy(new Schedule());
            scheduleSpy.setId(17L);
            scheduleSpy.setTitle("No startTime in message");
            scheduleSpy.setLocation("L");
            scheduleSpy.setReminderSent(false);
            LocalDateTime startTime = LocalDateTime.now().plusMinutes(1);
            doReturn(startTime, startTime, (LocalDateTime) null).when(scheduleSpy).getStartTime();
            doReturn(1).when(scheduleSpy).getReminderTime();

            ScheduleParticipant user = new ScheduleParticipant();
            user.setParticipantType("USER");
            user.setParticipantId(100L);
            scheduleSpy.setParticipants(new HashSet<>(Set.of(user)));

            when(mockScheduleRepository.findSchedulesForReminder(any(LocalDateTime.class)))
                    .thenReturn(Collections.singletonList(scheduleSpy));

            try (MockedStatic<SecurityUtil> mockedSecurity = org.mockito.Mockito.mockStatic(SecurityUtil.class)) {
                mockedSecurity.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("jwt"));

                // Act
                scheduleStatusUpdateServiceUnderTest.sendReminderNotifications();

                // Assert
                ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
                verify(mockNotificationProducer, times(1)).sendNotificationToMultiple(
                        eq(Collections.singletonList(100L)),
                        eq("Nhắc nhở lịch hẹn"),
                        messageCaptor.capture(),
                        eq("jwt"));
                String message = messageCaptor.getValue();
                assertTrue(message.contains("Bạn có lịch hẹn: "));
                assertFalse(message.contains(" vào "));
                assertTrue(Boolean.TRUE.equals(scheduleSpy.getReminderSent()));
                verify(mockScheduleRepository, times(1)).saveAll(eq(Collections.singletonList(scheduleSpy)));
            }
        }
    }
}
