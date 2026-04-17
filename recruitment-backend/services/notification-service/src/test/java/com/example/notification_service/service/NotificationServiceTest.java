package com.example.notification_service.service;

import com.example.notification_service.dto.Meta;
import com.example.notification_service.dto.PaginationDTO;
import com.example.notification_service.dto.notification.BulkNotificationRequest;
import com.example.notification_service.exception.NotificationNotFoundException;
import com.example.notification_service.messaging.NotificationEvent;
import com.example.notification_service.model.Notification;
import com.example.notification_service.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService Unit Test")
class NotificationServiceTest {

    // ===== Dependencies =====
    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    @SuppressWarnings("unused")
    private UserService userService;

    @Mock
    private SocketIOBroadcastService socketIOBroadcastService;

    @InjectMocks
    private NotificationService notificationService;

    @Captor
    private ArgumentCaptor<Notification> notificationCaptor;

    // ===== NS-TC01 =====
    @Test
    @DisplayName("NS-TC01: createNotification - dữ liệu hợp lệ -> tạo mới notification với trạng thái mặc định unread")
    void NS_TC01_createNotification_validInput_createNewNotificationWithDefaultUnreadStatus() {
        // Arrange
        Long recipientId = 10L;
        String title = "Welcome";
        String message = "Hello";

        when(notificationRepository.save(any(Notification.class))).thenAnswer(new Answer<Notification>() {
            @Override
            public Notification answer(InvocationOnMock invocation) {
                Notification n = invocation.getArgument(0, Notification.class);
                n.setId(1L);
                return n;
            }
        });

        // Act
        Notification saved = notificationService.createNotification(recipientId, title, message);

        // Assert
        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isEqualTo(1L);
        assertThat(saved.getRecipientId()).isEqualTo(recipientId);
        assertThat(saved.getTitle()).isEqualTo(title);
        assertThat(saved.getMessage()).isEqualTo(message);
        assertThat(saved.getSentAt()).isNotNull();
        assertThat(saved.getDeliveryStatus()).isEqualTo("SENT");
        assertThat(saved.isRead()).isFalse();

        verify(notificationRepository, times(1)).save(notificationCaptor.capture());
        Notification toSave = notificationCaptor.getValue();
        assertThat(toSave.getRecipientId()).isEqualTo(recipientId);
        assertThat(toSave.isRead()).isFalse();

        verify(socketIOBroadcastService, times(1)).pushNotification(any());
    }

    // ===== NS-TC02 =====
    @Test
    @DisplayName("NS-TC02: markAsRead - notification tồn tại được đánh dấu đã đọc, không tồn tại ném exception")
    void NS_TC02_markAsRead_existingMarkedRead_notFoundThrows() {
        // --- Case 1: Existing notification, isRead=false -> update to read ---
        Notification existing = new Notification();
        existing.setId(100L);
        existing.setRecipientId(77L);
        existing.setTitle("T");
        existing.setMessage("M");
        existing.setRead(false);

        when(notificationRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationRepository.countByRecipientIdAndIsReadFalse(77L)).thenReturn(3L);

        notificationService.markAsRead(100L);

        verify(notificationRepository, times(1)).findById(100L);
        verify(notificationRepository, times(1)).save(notificationCaptor.capture());
        Notification updated = notificationCaptor.getValue();
        assertThat(updated.isRead()).isTrue();
        assertThat(updated.getReadAt()).isNotNull();

        verify(socketIOBroadcastService, times(1)).pushNotification(any());
        verify(socketIOBroadcastService, times(1)).publishUnreadCount(77L, 3L);

        // --- Case 2: Not found -> throw exception ---
        org.mockito.Mockito.clearInvocations(notificationRepository);
        when(notificationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NotificationNotFoundException.class, new Executable() {
            @Override
            public void execute() {
                notificationService.markAsRead(999L);
            }
        });

        verify(notificationRepository, times(1)).findById(999L);
        verify(notificationRepository, never()).save(any());
    }

    // Extra coverage: already read -> no save/publish/unreadCount
    @Test
    @DisplayName("[Extra] markAsRead - notification đã đọc sẵn -> không cập nhật lại")
    void markAsRead_alreadyRead_noUpdate() {
        Notification existing = new Notification();
        existing.setId(200L);
        existing.setRecipientId(88L);
        existing.setRead(true);
        existing.setReadAt(LocalDateTime.now().minusDays(1));

        when(notificationRepository.findById(200L)).thenReturn(Optional.of(existing));

        notificationService.markAsRead(200L);

        verify(notificationRepository, times(1)).findById(200L);
        verify(notificationRepository, never()).save(any());
        verify(socketIOBroadcastService, never()).pushNotification(any());
        verify(socketIOBroadcastService, never()).publishUnreadCount(anyLong(), anyLong());
    }

    // ===== NS-TC03 =====
    @Test
    @DisplayName("NS-TC03: markAllAsRead - đánh dấu toàn bộ notification của recipient là đã đọc")
    void NS_TC03_markAllAsRead_markAllAsReadAndBroadcastUnreadCount() {
        // Arrange
        Long recipientId = 55L;
        when(notificationRepository.markAllAsReadByRecipientId(eq(recipientId), any(LocalDateTime.class)))
                .thenReturn(5);

        // Act
        int updated = notificationService.markAllAsRead(recipientId);

        // Assert
        assertThat(updated).isEqualTo(5);
        verify(notificationRepository, times(1)).markAllAsReadByRecipientId(eq(recipientId), any(LocalDateTime.class));
        verify(socketIOBroadcastService, times(1)).publishUnreadCount(recipientId, 0L);
    }

    // Extra coverage: updatedCount = 0 -> no broadcast
    @Test
    @DisplayName("[Extra] markAllAsRead - không có notification nào được update -> không broadcast")
    void markAllAsRead_noUpdate_noBroadcast() {
        Long recipientId = 56L;
        when(notificationRepository.markAllAsReadByRecipientId(eq(recipientId), any(LocalDateTime.class)))
                .thenReturn(0);

        int updated = notificationService.markAllAsRead(recipientId);

        assertThat(updated).isZero();
        verify(socketIOBroadcastService, never()).publishUnreadCount(anyLong(), anyLong());
    }

    // ===== NS-TC04 =====
    @Test
    @DisplayName("NS-TC04: getAllNotificationsWithFilters - lọc đúng danh sách notification và phân trang đúng")
    void NS_TC04_getAllNotificationsWithFilters_filterAndPaginationCorrect() {
        // Arrange
        Pageable pageable = PageRequest.of(1, 2); // page index=1 -> meta.page should be 2

        Notification n1 = new Notification();
        n1.setId(1L);
        Notification n2 = new Notification();
        n2.setId(2L);

        Page<Notification> page = new PageImpl<Notification>(Arrays.asList(n1, n2), pageable, 10);

        when(notificationRepository.findByRecipientId(eq(11L), eq(pageable))).thenReturn(page);

        // Act
        PaginationDTO dto = notificationService.getAllNotificationsWithFilters(11L, null, pageable);

        // Assert
        assertThat(dto).isNotNull();
        assertThat(dto.getResult()).isInstanceOf(List.class);
        assertThat((List<?>) dto.getResult()).hasSize(2);
        assertThat(((Notification) ((List<?>) dto.getResult()).get(0)).getId()).isEqualTo(1L);

        Meta meta = dto.getMeta();
        assertThat(meta).isNotNull();
        assertThat(meta.getPage()).isEqualTo(2);
        assertThat(meta.getPageSize()).isEqualTo(2);
        assertThat(meta.getTotal()).isEqualTo(10);
        assertThat(meta.getPages()).isEqualTo(5);

        verify(notificationRepository, times(1)).findByRecipientId(11L, pageable);
        verify(notificationRepository, never()).findByDeliveryStatus(any(), any());
        verify(notificationRepository, never()).findAll(any(Pageable.class));
    }

    // Extra coverage: status filter branch when recipientId == null
    @Test
    @DisplayName("[Extra] getAllNotificationsWithFilters - recipientId null, status có giá trị -> lọc theo status")
    void getAllNotificationsWithFilters_statusBranch() {
        Pageable pageable = PageRequest.of(0, 3);
        Page<Notification> page = new PageImpl<Notification>(Collections.<Notification>emptyList(), pageable, 0);

        when(notificationRepository.findByDeliveryStatus(eq("SENT"), eq(pageable))).thenReturn(page);

        PaginationDTO dto = notificationService.getAllNotificationsWithFilters(null, "SENT", pageable);

        assertThat(dto.getMeta().getPage()).isEqualTo(1);
        assertThat(dto.getMeta().getPageSize()).isEqualTo(3);
        assertThat(dto.getMeta().getTotal()).isZero();
        assertThat(dto.getMeta().getPages()).isZero();

        verify(notificationRepository, times(1)).findByDeliveryStatus("SENT", pageable);
        verify(notificationRepository, never()).findAll(any(Pageable.class));
        verify(notificationRepository, never()).findByRecipientId(anyLong(), any(Pageable.class));
    }

    // Extra coverage: all branch when both recipientId and status are null
    @Test
    @DisplayName("[Extra] getAllNotificationsWithFilters - recipientId null, status null -> lấy tất cả")
    void getAllNotificationsWithFilters_findAllBranch() {
        Pageable pageable = PageRequest.of(0, 1);
        Notification n = new Notification();
        n.setId(9L);
        Page<Notification> page = new PageImpl<Notification>(Collections.singletonList(n), pageable, 1);

        when(notificationRepository.findAll(eq(pageable))).thenReturn(page);

        PaginationDTO dto = notificationService.getAllNotificationsWithFilters(null, null, pageable);

        assertThat(((List<?>) dto.getResult())).hasSize(1);
        assertThat(dto.getMeta().getTotal()).isEqualTo(1);

        verify(notificationRepository, times(1)).findAll(pageable);
    }

    // ===== NS-TC05 =====
    @Test
    @DisplayName("NS-TC05: getNotificationStats - trả về thống kê notification đúng theo recipientId")
    void NS_TC05_getNotificationStats_returnCorrectStatsByRecipient() {
        // Arrange
        when(notificationRepository.count()).thenReturn(100L);
        when(notificationRepository.countByRecipientIdAndIsReadFalse(22L)).thenReturn(7L);

        // Act
        Map<String, Object> stats = notificationService.getNotificationStats(22L);

        // Assert
        assertThat(stats).isNotNull();
        assertThat(stats.get("totalNotifications")).isEqualTo(100L);
        assertThat(stats.get("unreadNotifications")).isEqualTo(7L);

        verify(notificationRepository, times(1)).count();
        verify(notificationRepository, times(1)).countByRecipientIdAndIsReadFalse(22L);
        verify(notificationRepository, never()).countByIsReadFalse();
    }

    // Extra coverage: recipientId null -> countByIsReadFalse
    @Test
    @DisplayName("[Extra] getNotificationStats - recipientId null -> thống kê unread toàn hệ thống")
    void getNotificationStats_recipientNull_usesGlobalUnreadCount() {
        when(notificationRepository.count()).thenReturn(5L);
        when(notificationRepository.countByIsReadFalse()).thenReturn(2L);

        Map<String, Object> stats = notificationService.getNotificationStats(null);

        assertThat(stats.get("totalNotifications")).isEqualTo(5L);
        assertThat(stats.get("unreadNotifications")).isEqualTo(2L);

        verify(notificationRepository, times(1)).countByIsReadFalse();
        verify(notificationRepository, never()).countByRecipientIdAndIsReadFalse(anyLong());
    }

    // ===== NS-TC06 =====
    @Test
    @DisplayName("NS-TC06: processNotificationEvent - xử lý event và tạo notification đúng dữ liệu")
    void NS_TC06_processNotificationEvent_createNotificationsFromEvent() {
        // Arrange
        NotificationEvent event = new NotificationEvent();
        event.setTitle("Event Title");
        event.setMessage("Event Message");
        event.setRecipientIds(java.util.Arrays.asList(1L, 2L, 2L, null));

        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0, Notification.class);
            n.setId(999L);
            return n;
        });

        // Act
        notificationService.processNotificationEvent(event);

        // Assert
        verify(notificationRepository, times(2)).save(any(Notification.class));
        verify(socketIOBroadcastService, times(2)).pushNotification(any());

        verify(notificationRepository, times(2)).save(notificationCaptor.capture());
        List<Notification> savedList = notificationCaptor.getAllValues();
        for (Notification n : savedList) {
            assertThat(n.getTitle()).isEqualTo("Event Title");
            assertThat(n.getMessage()).isEqualTo("Event Message");
            assertThat(n.getDeliveryStatus()).isEqualTo("SENT");
        }

        Set<Long> recipientIds = new HashSet<Long>();
        for (Notification n : savedList) {
            recipientIds.add(n.getRecipientId());
        }
        assertThat(recipientIds).containsExactlyInAnyOrder(1L, 2L);
    }

    // Extra coverage: event resolves to empty recipients -> do nothing
    @Test
    @DisplayName("[Extra] processNotificationEvent - không có recipient -> không tạo notification")
    void processNotificationEvent_emptyRecipients_doNothing() {
        NotificationEvent event = new NotificationEvent();
        event.setTitle("T");
        event.setMessage("M");
        event.setRecipientIds(Collections.emptyList());

        notificationService.processNotificationEvent(event);

        verify(notificationRepository, never()).save(any());
        verify(socketIOBroadcastService, never()).pushNotification(any());
    }

    // ===== NS-TC07 =====
    @Test
    @DisplayName("NS-TC07: createBulkNotificationsByConditions - tạo bulk notification đúng đối tượng nhận")
    void NS_TC07_createBulkNotificationsByConditions_createBulkCorrectRecipients() {
        // Arrange
        BulkNotificationRequest request = new BulkNotificationRequest();
        request.setTitle("Bulk Title");
        request.setMessage("Bulk Message");
        request.setRecipientIds(Arrays.asList(10L, 11L, 10L));

        when(notificationRepository.save(any(Notification.class))).thenAnswer(new Answer<Notification>() {
            @Override
            public Notification answer(InvocationOnMock invocation) {
                Notification n = invocation.getArgument(0, Notification.class);
                n.setId(1L);
                return n;
            }
        });

        // Act
        int createdCount = notificationService.createBulkNotificationsByConditions(request);

        // Assert
        assertThat(createdCount).isEqualTo(2);
        verify(notificationRepository, times(2)).save(any(Notification.class));
        verify(socketIOBroadcastService, times(2)).pushNotification(any());

        verify(notificationRepository, times(2)).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getAllValues())
                .extracting("recipientId")
                .containsExactlyInAnyOrder(10L, 11L);
    }

    // Extra coverage: bulk recipients empty -> returns 0 / no interactions
    @Test
    @DisplayName("[Extra] createBulkNotificationsByConditions - recipient list rỗng -> không tạo gì")
    void createBulkNotificationsByConditions_emptyRecipients_returnZero() {
        BulkNotificationRequest request = new BulkNotificationRequest();
        request.setTitle("Bulk");
        request.setMessage("Msg");
        request.setRecipientIds(Collections.emptyList());

        int createdCount = notificationService.createBulkNotificationsByConditions(request);

        assertThat(createdCount).isZero();
        verify(notificationRepository, never()).save(any());
        verify(socketIOBroadcastService, never()).pushNotification(any());
    }
}
