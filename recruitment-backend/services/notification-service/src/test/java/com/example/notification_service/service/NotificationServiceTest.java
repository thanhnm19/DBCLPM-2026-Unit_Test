package com.example.notification_service.service;

import com.example.notification_service.dto.PaginationDTO;
import com.example.notification_service.dto.notification.BulkNotificationRequest;
import com.example.notification_service.exception.NotificationNotFoundException;
import com.example.notification_service.messaging.NotificationEvent;
import com.example.notification_service.model.Notification;
import com.example.notification_service.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho NotificationService - Module 10 (Phần 3a): Thông báo.
 *
 * Chiến lược:
 * - Mock NotificationRepository, UserService, SocketIOBroadcastService.
 * - Sử dụng MockedStatic cho SecurityUtil.getCurrentUserJWT() trong createBulkNotificationsByConditions.
 * - Minh chứng (CheckDB): verify(notificationRepository).save(any(Notification.class)).
 * - Dọn dẹp (Rollback): Không dùng DB thật, Mockito reset sau mỗi test.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService Unit Tests")
class NotificationServiceTest {

    // -----------------------------------------------------------------------
    // Mock dependencies - tất cả giao tiếp ngoài service đều được mock
    // -----------------------------------------------------------------------

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserService userService;

    @Mock
    private SocketIOBroadcastService socketIOBroadcastService;

    // Service đang được kiểm tra (System Under Test)
    @InjectMocks
    private NotificationService notificationService;

    // -----------------------------------------------------------------------
    // Dữ liệu dùng chung (Fixture)
    // -----------------------------------------------------------------------

    private Notification unreadNotification;
    private Notification readNotification;

    @BeforeEach
    void setUp() {
        // Thông báo chưa đọc
        unreadNotification = new Notification();
        unreadNotification.setId(1L);
        unreadNotification.setRecipientId(100L);
        unreadNotification.setTitle("Test Title");
        unreadNotification.setMessage("Test Message");
        unreadNotification.setRead(false);
        unreadNotification.setDeliveryStatus("SENT");

        // Thông báo đã đọc
        readNotification = new Notification();
        readNotification.setId(2L);
        readNotification.setRecipientId(100L);
        readNotification.setRead(true);
        readNotification.setDeliveryStatus("SENT");
    }

    // =======================================================================
    // PHẦN 1: Hàm createNotification()
    // =======================================================================

    // Test Case ID: UTIL-NT01
    // Mục tiêu: Tạo thông báo hợp lệ -> lưu với deliveryStatus=SENT, gọi pushNotification
    @Test
    @DisplayName("UTIL-NT01: createNotification - Phải lưu SENT và gọi pushNotification")
    void createNotification_ValidInputs_ShouldSaveWithCorrectFieldsAndBroadcast() {
        // Chuẩn bị: Chuẩn bị dữ liệu trả về khi save()
        Notification savedNotif = new Notification();
        savedNotif.setId(10L);
        savedNotif.setRecipientId(100L);
        savedNotif.setTitle("New Notification");
        savedNotif.setDeliveryStatus("SENT");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        when(notificationRepository.save(captor.capture())).thenReturn(savedNotif);
        doNothing().when(socketIOBroadcastService).pushNotification(any());

        // Thực thi
        Notification result = notificationService.createNotification(100L, "New Notification", "Content");

        // Kiểm tra: deliveryStatus phải là "SENT", sentAt phải có giá trị
        Notification captured = captor.getValue();
        assertThat(captured.getDeliveryStatus()).isEqualTo("SENT");
        assertThat(captured.getSentAt()).isNotNull();
        assertThat(result.getId()).isEqualTo(10L);

        // Minh chứng (CheckDB): save() được gọi 1 lần
        verify(notificationRepository, times(1)).save(any(Notification.class));
        // SocketIO broadcast phải được gọi
        verify(socketIOBroadcastService, times(1)).pushNotification(any());
    }

    // =======================================================================
    // PHẦN 2: Hàm markAsRead()
    // =======================================================================

    // Test Case ID: UTIL-NT02
    // Mục tiêu: Thông báo chưa đọc -> set isRead=true, khuếch đại unreadCount
    @Test
    @DisplayName("UTIL-NT02: markAsRead - Chưa đọc, phải set isRead=true và broadcast unreadCount")
    void markAsRead_UnreadNotification_ShouldSetReadTrueAndBroadcastUnreadCount() {
        // Chuẩn bị
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(unreadNotification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.countByRecipientIdAndIsReadFalse(100L)).thenReturn(0L);
        doNothing().when(socketIOBroadcastService).publishUnreadCount(anyLong(), anyLong());

        // Thực thi
        notificationService.markAsRead(1L);

        // Kiểm tra: isRead phải là true, readAt được set
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().isRead()).isTrue();
        assertThat(captor.getValue().getReadAt()).isNotNull();

        // Minh chứng (CheckDB): publishUnreadCount được gọi
        verify(socketIOBroadcastService, times(1)).publishUnreadCount(eq(100L), anyLong());
    }

    // Test Case ID: UTIL-NT03
    // Mục tiêu: Thông báo đã đọc -> KHÔNG gọi save() lẫn publishUnreadCount() (tránh ghi dữ liệu thừa)
    @Test
    @DisplayName("UTIL-NT03: markAsRead - Đã đọc, KHÔNG gọi save() lẫn publishUnreadCount()")
    void markAsRead_AlreadyReadNotification_ShouldNotSaveNorBroadcast() {
        // Chuẩn bị: Notification đã isRead=true
        when(notificationRepository.findById(2L)).thenReturn(Optional.of(readNotification));

        // Thực thi
        notificationService.markAsRead(2L);

        // Minh chứng (CheckDB): save() KHÔNG được gọi (không cập nhật DB)
        verify(notificationRepository, never()).save(any());
        // Broadcast KHÔNG được gọi
        verify(socketIOBroadcastService, never()).publishUnreadCount(anyLong(), anyLong());
    }

    // Test Case ID: UTIL-NT04
    // Mục tiêu: ID không tồn tại -> throw NotificationNotFoundException
    @Test
    @DisplayName("UTIL-NT04: markAsRead - ID không tồn tại, phải throw NotificationNotFoundException")
    void markAsRead_NonExistentNotification_ShouldThrowNotificationNotFoundException() {
        // Chuẩn bị: Repository không tìm thấy
        when(notificationRepository.findById(999L)).thenReturn(Optional.empty());

        // Thực thi & Kiểm tra
        assertThatThrownBy(() -> notificationService.markAsRead(999L))
                .isInstanceOf(NotificationNotFoundException.class);

        // Minh chứng (CheckDB): save() KHÔNG được gọi
        verify(notificationRepository, never()).save(any());
    }

    // =======================================================================
    // PHẦN 3: Hàm markAllAsRead()
    // =======================================================================

    // Test Case ID: UTIL-NT05
    // Mục tiêu: Có thông báo chưa đọc -> trả về count và broadcast unreadCount=0
    @Test
    @DisplayName("UTIL-NT05: markAllAsRead - Có thông báo chưa đọc, phải broadcast unreadCount=0")
    void markAllAsRead_WithUnreadNotifications_ShouldReturnCountAndBroadcastZero() {
        // Chuẩn bị: repository.markAllAsReadByRecipientId trả về 3 (3 bản ghi được cập nhật)
        when(notificationRepository.markAllAsReadByRecipientId(eq(100L), any())).thenReturn(3);
        doNothing().when(socketIOBroadcastService).publishUnreadCount(anyLong(), anyLong());

        // Thực thi
        int count = notificationService.markAllAsRead(100L);

        // Kiểm tra: Trả về số lượng đã cập nhật
        assertThat(count).isEqualTo(3);
        // Minh chứng (CheckDB): publishUnreadCount được gọi với unreadCount=0
        verify(socketIOBroadcastService, times(1)).publishUnreadCount(eq(100L), eq(0L));
    }

    // Test Case ID: UTIL-NT06
    // Mục tiêu: Không có thông báo chưa đọc -> KHÔNG broadcast (tránh bắn tin thừa)
    @Test
    @DisplayName("UTIL-NT06: markAllAsRead - Không có thông báo chưa đọc, KHÔNG broadcast")
    void markAllAsRead_WithNoUnreadNotifications_ShouldReturnZeroAndNotBroadcast() {
        // Chuẩn bị: 0 bản ghi được cập nhật
        when(notificationRepository.markAllAsReadByRecipientId(eq(100L), any())).thenReturn(0);

        // Thực thi
        int count = notificationService.markAllAsRead(100L);

        // Kiểm tra: Trả về 0
        assertThat(count).isEqualTo(0);
        // Broadcast KHÔNG được gọi khi count = 0
        verify(socketIOBroadcastService, never()).publishUnreadCount(anyLong(), anyLong());
    }

    // =======================================================================
    // PHẦN 4: Hàm getAllNotificationsWithFilters()
    // =======================================================================

    // Test Case ID: UTIL-NT07
    // Mục tiêu: Có recipientId -> gọi repository.findByRecipientId()
    @Test
    @DisplayName("UTIL-NT07: getAllNotificationsWithFilters - Có recipientId, gọi findByRecipientId")
    void getAllNotificationsWithFilters_WithRecipientId_ShouldQueryByRecipientId() {
        // Chuẩn bị
        Pageable pageable = PageRequest.of(0, 10);
        Page<Notification> mockPage = new PageImpl<>(Collections.singletonList(unreadNotification), pageable, 1);
        when(notificationRepository.findByRecipientId(eq(100L), eq(pageable))).thenReturn(mockPage);

        // Thực thi
        PaginationDTO result = notificationService.getAllNotificationsWithFilters(100L, null, pageable);

        // Kiểm tra
        assertThat(result.getMeta().getTotal()).isEqualTo(1L);
        // Minh chứng (CheckDB): findByRecipientId được gọi, findByDeliveryStatus KHÔNG được gọi
        verify(notificationRepository, times(1)).findByRecipientId(eq(100L), eq(pageable));
        verify(notificationRepository, never()).findByDeliveryStatus(any(), any());
        verify(notificationRepository, never()).findAll(any(Pageable.class));
    }

    // Test Case ID: UTIL-NT08
    // Mục tiêu: recipientId=null + có status -> gọi repository.findByDeliveryStatus()
    @Test
    @DisplayName("UTIL-NT08: getAllNotificationsWithFilters - Có status, gọi findByDeliveryStatus")
    void getAllNotificationsWithFilters_WithStatus_ShouldQueryByStatus() {
        // Chuẩn bị
        Pageable pageable = PageRequest.of(0, 10);
        Page<Notification> mockPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        when(notificationRepository.findByDeliveryStatus(eq("SENT"), eq(pageable))).thenReturn(mockPage);

        // Thực thi
        PaginationDTO result = notificationService.getAllNotificationsWithFilters(null, "SENT", pageable);

        // Minh chứng (CheckDB): findByDeliveryStatus được gọi
        verify(notificationRepository, times(1)).findByDeliveryStatus(eq("SENT"), eq(pageable));
    }

    // Test Case ID: UTIL-NT09
    // Mục tiêu: Cả hai null -> gọi repository.findAll()
    @Test
    @DisplayName("UTIL-NT09: getAllNotificationsWithFilters - Cả hai null, gọi findAll()")
    void getAllNotificationsWithFilters_WithNullBoth_ShouldQueryAll() {
        // Chuẩn bị
        Pageable pageable = PageRequest.of(0, 10);
        Page<Notification> mockPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        when(notificationRepository.findAll(eq(pageable))).thenReturn(mockPage);

        // Thực thi
        PaginationDTO result = notificationService.getAllNotificationsWithFilters(null, null, pageable);

        // Minh chứng (CheckDB): findAll() được gọi
        verify(notificationRepository, times(1)).findAll(eq(pageable));
    }

    // =======================================================================
    // PHẦN 5: Hàm getNotificationStats()
    // =======================================================================

    // Test Case ID: UTIL-NT10
    // Mục tiêu: Có recipientId -> gọi countByRecipientIdAndIsReadFalse (không gọi toàn hệ thống)
    @Test
    @DisplayName("UTIL-NT10: getNotificationStats - Có recipientId, phải gọi countByRecipient")
    void getNotificationStats_WithRecipientId_ShouldReturnUnreadCountForRecipient() {
        // Chuẩn bị
        when(notificationRepository.count()).thenReturn(10L);
        when(notificationRepository.countByRecipientIdAndIsReadFalse(100L)).thenReturn(3L);

        // Thực thi
        Map<String, Object> stats = notificationService.getNotificationStats(100L);

        // Kiểm tra: unreadNotifications phải là 3 (của recipient cụ thể)
        assertThat(stats.get("unreadNotifications")).isEqualTo(3L);
        // KHÔNG gọi countByIsReadFalse() toàn hệ thống
        verify(notificationRepository, never()).countByIsReadFalse();
    }

    // Test Case ID: UTIL-NT11
    // Mục tiêu: recipientId=null -> gọi countByIsReadFalse() toàn hệ thống
    @Test
    @DisplayName("UTIL-NT11: getNotificationStats - recipientId null, phải gọi countByIsReadFalse toàn hệ thống")
    void getNotificationStats_WithNullRecipientId_ShouldReturnGlobalUnreadCount() {
        // Chuẩn bị
        when(notificationRepository.count()).thenReturn(50L);
        when(notificationRepository.countByIsReadFalse()).thenReturn(15L);

        // Thực thi
        Map<String, Object> stats = notificationService.getNotificationStats(null);

        // Kiểm tra: unreadNotifications là 15 (toàn hệ thống)
        assertThat(stats.get("unreadNotifications")).isEqualTo(15L);
        // KHÔNG gọi countByRecipientIdAndIsReadFalse
        verify(notificationRepository, never()).countByRecipientIdAndIsReadFalse(anyLong());
    }

    // =======================================================================
    // PHẦN 6: Hàm processNotificationEvent()
    // =======================================================================

    // Test Case ID: UTIL-NT12
    // Mục tiêu: Event có recipientId -> gọi createNotification() đúng 1 lần
    @Test
    @DisplayName("UTIL-NT12: processNotificationEvent - 1 recipient, createNotification được gọi 1 lần")
    void processNotificationEvent_WithSingleRecipient_ShouldCreateOneNotification() {
        // Chuẩn bị: Event có recipientId cụ thể
        NotificationEvent event = new NotificationEvent();
        event.setRecipientId(100L);
        event.setTitle("Event Title");
        event.setMessage("Event Message");

        // Mock createNotification (gọi save bên trong)
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(99L);
            return n;
        });
        doNothing().when(socketIOBroadcastService).pushNotification(any());

        // Thực thi
        notificationService.processNotificationEvent(event);

        // Minh chứng (CheckDB): save() được gọi đúng 1 lần (cho 1 recipient)
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    // Test Case ID: UTIL-NT13
    // Mục tiêu: Event không có recipient nào -> KHÔNG gọi createNotification (tránh save thừa)
    @Test
    @DisplayName("UTIL-NT13: processNotificationEvent - Không có recipient, không gọi save()")
    void processNotificationEvent_WithEmptyRecipients_ShouldNotCreateAnyNotification() {
        // Chuẩn bị: Event không có recipientId, recipientIds, includeAllEmployees
        NotificationEvent event = new NotificationEvent();
        event.setTitle("Title");
        event.setMessage("Message");
        // Tất cả recipient source đều null

        // Thực thi
        notificationService.processNotificationEvent(event);

        // Minh chứng (CheckDB): save() KHÔNG được gọi
        verify(notificationRepository, never()).save(any());
    }

    // =======================================================================
    // PHẦN 7: Hàm createBulkNotificationsByConditions()
    // =======================================================================

    // Test Case ID: UTIL-NT14
    // Mục tiêu: includeAllEmployees=true -> gọi getAllEmployeeIds() và tạo cho từng người
    @Test
    @DisplayName("UTIL-NT14: createBulkNotifications - includeAllEmployees, phải gọi getAllEmployeeIds")
    void createBulkNotifications_IncludeAllEmployees_ShouldCallGetAllEmployeeIdsAndCreateForEach() {
        // Chuẩn bị
        BulkNotificationRequest request = new BulkNotificationRequest();
        request.setIncludeAllEmployees(true);
        request.setTitle("Company Announcement");
        request.setMessage("Important message");

        // UserService trả về 3 nhân viên
        when(userService.getAllEmployeeIds(any())).thenReturn(Arrays.asList(1L, 2L, 3L));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId((long) (Math.random() * 1000));
            return n;
        });
        doNothing().when(socketIOBroadcastService).pushNotification(any());

        // Dùng MockedStatic để mock SecurityUtil.getCurrentUserJWT() (static method)
        try (MockedStatic<com.example.notification_service.utils.SecurityUtil> securityUtil =
                     mockStatic(com.example.notification_service.utils.SecurityUtil.class)) {
            securityUtil.when(com.example.notification_service.utils.SecurityUtil::getCurrentUserJWT)
                    .thenReturn(java.util.Optional.of("mock-token"));

            // Thực thi
            int count = notificationService.createBulkNotificationsByConditions(request);

            // Kiểm tra: Tạo 3 thông báo (cho 3 nhân viên)
            assertThat(count).isEqualTo(3);
            // Minh chứng (CheckDB): save() được gọi 3 lần
            verify(notificationRepository, times(3)).save(any(Notification.class));
            // Xác nhận getAllEmployeeIds() được gọi
            verify(userService, times(1)).getAllEmployeeIds(any());
        }
    }

    // Test Case ID: UTIL-NT15
    // Mục tiêu: recipientIds cụ thể -> tạo thông báo cho từng ID, trả về số lượng đúng
    @Test
    @DisplayName("UTIL-NT15: createBulkNotifications - recipientIds [1L,2L], phải tạo 2 thông báo")
    void createBulkNotifications_WithSpecificRecipientIds_ShouldCreateForEachId() {
        // Chuẩn bị
        BulkNotificationRequest request = new BulkNotificationRequest();
        request.setRecipientIds(Arrays.asList(1L, 2L));
        request.setTitle("Hello");
        request.setMessage("World");

        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId((long) (Math.random() * 1000));
            return n;
        });
        doNothing().when(socketIOBroadcastService).pushNotification(any());

        // Dùng MockedStatic cho SecurityUtil
        try (MockedStatic<com.example.notification_service.utils.SecurityUtil> securityUtil =
                     mockStatic(com.example.notification_service.utils.SecurityUtil.class)) {
            securityUtil.when(com.example.notification_service.utils.SecurityUtil::getCurrentUserJWT)
                    .thenReturn(java.util.Optional.of("mock-token"));

            // Thực thi
            int count = notificationService.createBulkNotificationsByConditions(request);

            // Kiểm tra: Tạo cho 2 nhân viên
            assertThat(count).isEqualTo(2);
            // Minh chứng (CheckDB): save() được gọi 2 lần
            verify(notificationRepository, times(2)).save(any(Notification.class));
        }
    }
}
