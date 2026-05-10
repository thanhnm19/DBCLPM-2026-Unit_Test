package com.example.notification_service.service;

import com.example.notification_service.dto.PaginationDTO;
import com.example.notification_service.dto.notification.BulkNotificationRequest;
import com.example.notification_service.exception.NotificationNotFoundException;
import com.example.notification_service.messaging.NotificationEvent;
import com.example.notification_service.model.Notification;
import com.example.notification_service.repository.NotificationRepository;
import com.example.notification_service.utils.SecurityUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("NotificationService tests")
class NotificationServiceTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationService notificationService;

    @MockBean
    private UserService userService;

    @MockBean
    private SocketIOBroadcastService socketIOBroadcastService;

    // Test Case ID: UTIL-NT01
    @Test
    @DisplayName("UTIL-NT01: createNotification saves notification and broadcasts it")
    void createNotification_WithValidRecipient_ShouldPersistSentNotificationAndBroadcast() {
        // Arrange
        Long recipientId = 100L;
        String notificationTitle = "New Notification";
        String notificationMessage = "Content";
        doNothing().when(socketIOBroadcastService).pushNotification(any());

        // Act
        Notification createdNotification = notificationService.createNotification(
                recipientId,
                notificationTitle,
                notificationMessage);

        // Assert
        assertThat(createdNotification.getId()).isNotNull();
        assertThat(createdNotification.getDeliveryStatus()).isEqualTo("SENT");

        Notification notificationInDatabase = notificationRepository.findById(createdNotification.getId())
                .orElseThrow();
        assertThat(notificationInDatabase.getRecipientId()).isEqualTo(recipientId);
        assertThat(notificationInDatabase.getTitle()).isEqualTo(notificationTitle);
        assertThat(notificationInDatabase.getMessage()).isEqualTo(notificationMessage);
        assertThat(notificationInDatabase.isRead()).isFalse();
        assertThat(notificationInDatabase.getSentAt()).isNotNull();
        verify(socketIOBroadcastService, times(1)).pushNotification(any());
    }

    // Test Case ID: UTIL-NT02
    @Test
    @DisplayName("UTIL-NT02: markAsRead updates unread notification and broadcasts unread count")
    void markAsRead_WithUnreadNotification_ShouldMarkNotificationAsReadAndBroadcastUnreadCount() {
        // Arrange
        Notification unreadNotification = saveNotification(100L, "New Notification", "Content", false, "SENT");
        doNothing().when(socketIOBroadcastService).publishUnreadCount(anyLong(), anyLong());
        doNothing().when(socketIOBroadcastService).pushNotification(any());

        // Act
        notificationService.markAsRead(unreadNotification.getId());

        // Assert
        Notification notificationInDatabase = notificationRepository.findById(unreadNotification.getId())
                .orElseThrow();
        assertThat(notificationInDatabase.isRead()).isTrue();
        assertThat(notificationInDatabase.getReadAt()).isNotNull();
        verify(socketIOBroadcastService, times(1)).pushNotification(any());
        verify(socketIOBroadcastService, times(1)).publishUnreadCount(eq(100L), eq(0L));
    }

    // Test Case ID: UTIL-NT03
    @Test
    @DisplayName("UTIL-NT03: markAsRead ignores notification that is already read")
    void markAsRead_WithAlreadyReadNotification_ShouldNotChangeDatabaseOrBroadcast() {
        // Arrange
        LocalDateTime originalReadAt = LocalDateTime.now().minusDays(1);
        Notification readNotification = saveNotification(100L, "Read Notification", "Content", true, "SENT");
        readNotification.setReadAt(originalReadAt);
        notificationRepository.saveAndFlush(readNotification);

        // Act
        notificationService.markAsRead(readNotification.getId());

        // Assert
        Notification notificationInDatabase = notificationRepository.findById(readNotification.getId())
                .orElseThrow();
        assertThat(notificationInDatabase.isRead()).isTrue();
        assertThat(notificationInDatabase.getReadAt()).isEqualToIgnoringNanos(originalReadAt);
        verify(socketIOBroadcastService, never()).publishUnreadCount(anyLong(), anyLong());
        verify(socketIOBroadcastService, never()).pushNotification(any());
    }

    // Test Case ID: UTIL-NT04
    @Test
    @DisplayName("UTIL-NT04: markAsRead throws exception when notification does not exist")
    void markAsRead_WithMissingNotification_ShouldThrowNotificationNotFoundException() {
        // Arrange
        Long missingNotificationId = 99999L;

        // Act & Assert
        assertThatThrownBy(() -> notificationService.markAsRead(missingNotificationId))
                .isInstanceOf(NotificationNotFoundException.class);
        assertThat(notificationRepository.findById(missingNotificationId)).isEmpty();
    }

    // Test Case ID: UTIL-NT05
    @Test
    @DisplayName("UTIL-NT05: markAllAsRead updates all unread notifications of recipient")
    void markAllAsRead_WithUnreadNotifications_ShouldMarkAllRecipientNotificationsAsRead() {
        // Arrange
        Long recipientId = 100L;
        saveNotification(recipientId, "First", "Content", false, "SENT");
        saveNotification(recipientId, "Second", "Content", false, "SENT");
        saveNotification(recipientId, "Third", "Content", false, "SENT");
        saveNotification(999L, "Other recipient", "Content", false, "SENT");
        doNothing().when(socketIOBroadcastService).publishUnreadCount(anyLong(), anyLong());

        // Act
        int updatedNotificationCount = notificationService.markAllAsRead(recipientId);

        // Assert
        assertThat(updatedNotificationCount).isEqualTo(3);
        List<Notification> recipientNotifications = notificationRepository.findByRecipientId(recipientId);
        assertThat(recipientNotifications).hasSize(3);
        assertThat(recipientNotifications).allSatisfy(notification -> {
            assertThat(notification.isRead()).isTrue();
            assertThat(notification.getReadAt()).isNotNull();
        });
        assertThat(notificationRepository.findByRecipientIdAndIsRead(999L, false)).hasSize(1);
        verify(socketIOBroadcastService, times(1)).publishUnreadCount(eq(recipientId), eq(0L));
    }

    // Test Case ID: UTIL-NT06
    @Test
    @DisplayName("UTIL-NT06: markAllAsRead does not broadcast when no unread notification exists")
    void markAllAsRead_WithNoUnreadNotifications_ShouldReturnZeroAndNotBroadcast() {
        // Arrange
        Long recipientId = 100L;
        saveNotification(recipientId, "Already read", "Content", true, "SENT");

        // Act
        int updatedNotificationCount = notificationService.markAllAsRead(recipientId);

        // Assert
        assertThat(updatedNotificationCount).isEqualTo(0);
        assertThat(notificationRepository.countByRecipientIdAndIsReadFalse(recipientId)).isZero();
        verify(socketIOBroadcastService, never()).publishUnreadCount(anyLong(), anyLong());
    }

    // Test Case ID: UTIL-NT07
    @Test
    @DisplayName("UTIL-NT07: getAllNotificationsWithFilters returns notifications by recipient")
    void getAllNotificationsWithFilters_WithRecipientId_ShouldReturnOnlyRecipientNotifications() {
        // Arrange
        Long recipientId = 100L;
        saveNotification(recipientId, "First", "Content", false, "SENT");
        saveNotification(recipientId, "Second", "Content", false, "SENT");
        saveNotification(999L, "Other recipient", "Content", false, "SENT");
        Pageable pageable = PageRequest.of(0, 10);

        // Act
        PaginationDTO pageResult = notificationService.getAllNotificationsWithFilters(recipientId, null, pageable);

        // Assert
        assertThat(pageResult.getMeta().getTotal()).isEqualTo(2L);
        assertThat((List<?>) pageResult.getResult())
                .hasSize(2)
                .allSatisfy(item -> assertThat(((Notification) item).getRecipientId()).isEqualTo(recipientId));
    }

    // Test Case ID: UTIL-NT08
    @Test
    @DisplayName("UTIL-NT08: getAllNotificationsWithFilters returns notifications by delivery status")
    void getAllNotificationsWithFilters_WithDeliveryStatus_ShouldReturnOnlyMatchingNotifications() {
        // Arrange
        saveNotification(100L, "Failed", "Content", false, "FAILED");
        saveNotification(101L, "Sent", "Content", false, "SENT");
        Pageable pageable = PageRequest.of(0, 10);

        // Act
        PaginationDTO pageResult = notificationService.getAllNotificationsWithFilters(null, "FAILED", pageable);

        // Assert
        assertThat(pageResult.getMeta().getTotal()).isEqualTo(1L);
        assertThat((List<?>) pageResult.getResult())
                .singleElement()
                .satisfies(item -> assertThat(((Notification) item).getDeliveryStatus()).isEqualTo("FAILED"));
    }

    // Test Case ID: UTIL-NT09
    @Test
    @DisplayName("UTIL-NT09: getAllNotificationsWithFilters returns all notifications when filters are empty")
    void getAllNotificationsWithFilters_WithNoFilters_ShouldReturnAllNotifications() {
        // Arrange
        saveNotification(100L, "First", "Content", false, "SENT");
        saveNotification(101L, "Second", "Content", false, "FAILED");
        Pageable pageable = PageRequest.of(0, 10);

        // Act
        PaginationDTO pageResult = notificationService.getAllNotificationsWithFilters(null, null, pageable);

        // Assert
        assertThat(pageResult.getMeta().getTotal()).isEqualTo(notificationRepository.count());
        assertThat((List<?>) pageResult.getResult()).hasSize(2);
    }

    // Test Case ID: UTIL-NT10
    @Test
    @DisplayName("UTIL-NT10: getNotificationStats counts unread notifications for one recipient")
    void getNotificationStats_WithRecipientId_ShouldReturnRecipientUnreadCountAndGlobalTotal() {
        // Arrange
        Long recipientId = 100L;
        saveNotification(recipientId, "Unread", "Content", false, "SENT");
        saveNotification(recipientId, "Read", "Content", true, "SENT");
        saveNotification(999L, "Other unread", "Content", false, "SENT");

        // Act
        Map<String, Object> notificationStats = notificationService.getNotificationStats(recipientId);

        // Assert
        assertThat(notificationStats.get("totalNotifications")).isEqualTo(notificationRepository.count());
        assertThat(notificationStats.get("unreadNotifications"))
                .isEqualTo(notificationRepository.countByRecipientIdAndIsReadFalse(recipientId));
        assertThat(notificationStats.get("unreadNotifications")).isEqualTo(1L);
    }

    // Test Case ID: UTIL-NT11
    @Test
    @DisplayName("UTIL-NT11: getNotificationStats counts global unread notifications")
    void getNotificationStats_WithNullRecipientId_ShouldReturnGlobalUnreadCount() {
        // Arrange
        saveNotification(101L, "Unread one", "Content", false, "SENT");
        saveNotification(102L, "Unread two", "Content", false, "SENT");
        saveNotification(103L, "Read", "Content", true, "SENT");

        // Act
        Map<String, Object> notificationStats = notificationService.getNotificationStats(null);

        // Assert
        assertThat(notificationStats.get("totalNotifications")).isEqualTo(notificationRepository.count());
        assertThat(notificationStats.get("unreadNotifications"))
                .isEqualTo(notificationRepository.countByIsReadFalse());
        assertThat(notificationStats.get("unreadNotifications")).isEqualTo(2L);
    }

    // Test Case ID: UTIL-NT12
    @Test
    @DisplayName("UTIL-NT12: processNotificationEvent creates notification for single recipient")
    void processNotificationEvent_WithSingleRecipient_ShouldCreateOneNotificationInDatabase() {
        // Arrange
        NotificationEvent notificationEvent = new NotificationEvent();
        notificationEvent.setRecipientId(100L);
        notificationEvent.setTitle("Kafka Event");
        notificationEvent.setMessage("Kafka Content");

        // Act
        notificationService.processNotificationEvent(notificationEvent);

        // Assert
        List<Notification> notificationsInDatabase = notificationRepository.findAll();
        assertThat(notificationsInDatabase).hasSize(1);
        assertThat(notificationsInDatabase.get(0).getRecipientId()).isEqualTo(100L);
        assertThat(notificationsInDatabase.get(0).getTitle()).isEqualTo("Kafka Event");
        assertThat(notificationsInDatabase.get(0).getMessage()).isEqualTo("Kafka Content");
    }

    // Test Case ID: UTIL-NT13
    @Test
    @DisplayName("UTIL-NT13: processNotificationEvent creates notifications for all employees")
    void processNotificationEvent_WithAllEmployeesFlag_ShouldCreateNotificationsForAllEmployees() {
        // Arrange
        NotificationEvent notificationEvent = new NotificationEvent();
        notificationEvent.setTitle("Kafka Event");
        notificationEvent.setMessage("Kafka Content");
        notificationEvent.setIncludeAllEmployees(true);
        when(userService.getAllEmployeeIds(any())).thenReturn(Arrays.asList(1L, 2L));

        // Act
        notificationService.processNotificationEvent(notificationEvent);

        // Assert
        assertNotificationRecipientsInDatabase(1L, 2L);
        assertThat(notificationRepository.findAll())
                .allSatisfy(notification -> assertThat(notification.getTitle()).isEqualTo("Kafka Event"));
    }

    // Test Case ID: UTIL-NT14
    @Test
    @DisplayName("UTIL-NT14: processNotificationEvent creates notifications by department filter")
    void processNotificationEvent_WithDepartmentFilter_ShouldCreateNotificationsForMatchedEmployees() {
        // Arrange
        NotificationEvent notificationEvent = new NotificationEvent();
        notificationEvent.setTitle("Kafka Event");
        notificationEvent.setMessage("Kafka Content");
        notificationEvent.setDepartmentId(1L);
        notificationEvent.setPositionId(2L);
        when(userService.getEmployeeIdsByFilters(eq(1L), eq(2L), any())).thenReturn(List.of(3L));

        // Act
        notificationService.processNotificationEvent(notificationEvent);

        // Assert
        assertNotificationRecipientsInDatabase(3L);
    }

    // Test Case ID: UTIL-NT15
    @Test
    @DisplayName("UTIL-NT15: processNotificationEvent creates notifications by position filter")
    void processNotificationEvent_WithPositionFilterOnly_ShouldCreateNotificationsForMatchedEmployees() {
        // Arrange
        NotificationEvent notificationEvent = new NotificationEvent();
        notificationEvent.setTitle("Kafka Event");
        notificationEvent.setMessage("Kafka Content");
        notificationEvent.setPositionId(2L);
        when(userService.getEmployeeIdsByFilters(isNull(), eq(2L), any())).thenReturn(List.of(4L));

        // Act
        notificationService.processNotificationEvent(notificationEvent);

        // Assert
        assertNotificationRecipientsInDatabase(4L);
    }

    // Test Case ID: UTIL-NT16
    @Test
    @DisplayName("UTIL-NT16: processNotificationEvent creates notifications for recipient list")
    void processNotificationEvent_WithRecipientIds_ShouldCreateNotificationsForEachRecipient() {
        // Arrange
        NotificationEvent notificationEvent = new NotificationEvent();
        notificationEvent.setTitle("Kafka Event");
        notificationEvent.setMessage("Kafka Content");
        notificationEvent.setRecipientIds(Arrays.asList(5L, 6L));

        // Act
        notificationService.processNotificationEvent(notificationEvent);

        // Assert
        assertNotificationRecipientsInDatabase(5L, 6L);
    }

    // Test Case ID: UTIL-NT17
    @Test
    @DisplayName("UTIL-NT17: processNotificationEvent does not insert when recipient is empty")
    void processNotificationEvent_WithNoRecipients_ShouldNotCreateNotification() {
        // Arrange
        NotificationEvent notificationEvent = new NotificationEvent();
        notificationEvent.setTitle("Kafka Empty");
        notificationEvent.setMessage("Message");
        long notificationCountBeforeAction = notificationRepository.count();

        // Act
        notificationService.processNotificationEvent(notificationEvent);

        // Assert
        assertThat(notificationRepository.count()).isEqualTo(notificationCountBeforeAction);
        verify(socketIOBroadcastService, never()).pushNotification(any());
    }

    // Test Case ID: UTIL-NT18
    @Test
    @DisplayName("UTIL-NT18: createBulkNotificationsByConditions creates notifications for all employees")
    void createBulkNotificationsByConditions_WithAllEmployeesFlag_ShouldCreateNotificationsForAllEmployees() {
        // Arrange
        BulkNotificationRequest bulkNotificationRequest = new BulkNotificationRequest();
        bulkNotificationRequest.setIncludeAllEmployees(true);
        bulkNotificationRequest.setTitle("Announce");
        bulkNotificationRequest.setMessage("Content");
        when(userService.getAllEmployeeIds(any())).thenReturn(Arrays.asList(1L, 2L, 3L));

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("mock-token"));

            // Act
            int createdNotificationCount = notificationService
                    .createBulkNotificationsByConditions(bulkNotificationRequest);

            // Assert
            assertThat(createdNotificationCount).isEqualTo(3);
            assertNotificationRecipientsInDatabase(1L, 2L, 3L);
        }
    }

    // Test Case ID: UTIL-NT19
    @Test
    @DisplayName("UTIL-NT19: createBulkNotificationsByConditions creates notifications for recipient list")
    void createBulkNotificationsByConditions_WithRecipientIds_ShouldCreateNotificationsForEachRecipient() {
        // Arrange
        BulkNotificationRequest bulkNotificationRequest = new BulkNotificationRequest();
        bulkNotificationRequest.setRecipientIds(Arrays.asList(1L, 2L));
        bulkNotificationRequest.setTitle("Announce");
        bulkNotificationRequest.setMessage("Content");

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("mock-token"));

            // Act
            int createdNotificationCount = notificationService
                    .createBulkNotificationsByConditions(bulkNotificationRequest);

            // Assert
            assertThat(createdNotificationCount).isEqualTo(2);
            assertNotificationRecipientsInDatabase(1L, 2L);
        }
    }

    // Test Case ID: UTIL-NT20
    @Test
    @DisplayName("UTIL-NT20: createBulkNotificationsByConditions creates notifications by employee filters")
    void createBulkNotificationsByConditions_WithEmployeeFilters_ShouldCreateNotificationsForMatchedEmployees() {
        // Arrange
        BulkNotificationRequest bulkNotificationRequest = new BulkNotificationRequest();
        bulkNotificationRequest.setTitle("Announce");
        bulkNotificationRequest.setMessage("Content");
        bulkNotificationRequest.setDepartmentId(1L);
        bulkNotificationRequest.setStatus("ACTIVE");
        when(userService.getEmployeeIdsByFilters(eq(1L), isNull(), eq("ACTIVE"), isNull(), any()))
                .thenReturn(Arrays.asList(10L, 11L));

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("mock-token"));

            // Act
            int createdNotificationCount = notificationService
                    .createBulkNotificationsByConditions(bulkNotificationRequest);

            // Assert
            assertThat(createdNotificationCount).isEqualTo(2);
            assertNotificationRecipientsInDatabase(10L, 11L);
        }
    }

    // Test Case ID: UTIL-NT21
    @Test
    @DisplayName("UTIL-NT21: createBulkNotificationsByConditions creates notification for single recipient")
    void createBulkNotificationsByConditions_WithSingleRecipientId_ShouldCreateOneNotification() {
        // Arrange
        BulkNotificationRequest bulkNotificationRequest = new BulkNotificationRequest();
        bulkNotificationRequest.setTitle("Announce");
        bulkNotificationRequest.setMessage("Content");
        bulkNotificationRequest.setRecipientId(99L);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("mock-token"));

            // Act
            int createdNotificationCount = notificationService
                    .createBulkNotificationsByConditions(bulkNotificationRequest);

            // Assert
            assertThat(createdNotificationCount).isEqualTo(1);
            assertNotificationRecipientsInDatabase(99L);
        }
    }

    // Test Case ID: UTIL-NT22
    @Test
    @DisplayName("UTIL-NT22: createBulkNotificationsByConditions returns zero when no recipient matches")
    void createBulkNotificationsByConditions_WithNoRecipients_ShouldNotCreateNotification() {
        // Arrange
        BulkNotificationRequest bulkNotificationRequest = new BulkNotificationRequest();
        bulkNotificationRequest.setTitle("Announce");
        bulkNotificationRequest.setMessage("Content");

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("mock-token"));

            // Act
            int createdNotificationCount = notificationService
                    .createBulkNotificationsByConditions(bulkNotificationRequest);

            // Assert
            assertThat(createdNotificationCount).isZero();
            assertThat(notificationRepository.count()).isZero();
        }
    }

    private Notification saveNotification(
            Long recipientId,
            String title,
            String message,
            boolean read,
            String deliveryStatus) {
        Notification notification = new Notification();
        notification.setRecipientId(recipientId);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setRead(read);
        notification.setDeliveryStatus(deliveryStatus);
        if (read) {
            notification.setReadAt(LocalDateTime.now());
        }
        return notificationRepository.saveAndFlush(notification);
    }

    private void assertNotificationRecipientsInDatabase(Long... expectedRecipientIds) {
        List<Long> actualRecipientIds = notificationRepository.findAll()
                .stream()
                .map(Notification::getRecipientId)
                .toList();

        assertThat(actualRecipientIds).containsExactlyInAnyOrder(expectedRecipientIds);
        assertThat(notificationRepository.count()).isEqualTo(expectedRecipientIds.length);
    }
}
