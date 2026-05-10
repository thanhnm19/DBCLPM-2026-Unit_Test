package com.example.notification_service.service;

import com.example.notification_service.dto.PaginationDTO;
import com.example.notification_service.dto.notification.BulkNotificationRequest;
import com.example.notification_service.messaging.NotificationEvent;
import com.example.notification_service.exception.NotificationNotFoundException;
import com.example.notification_service.model.Notification;
import com.example.notification_service.repository.NotificationRepository;
import com.example.notification_service.utils.SecurityUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
 * Test cho NotificationService - Module 10 (Phần 3a): Thông báo.
 *
 * Chiến lược (H2 Database):
 * - Sử dụng @SpringBootTest và @Transactional để thực thi và rollback trên DB H2 in-memory.
 * - @MockitoSpyBean NotificationRepository (delegating thật H2) để có thể stub lỗi gọi repo khi cần.
 * - @Autowired NotificationService để gọi logic thật.
 * - @MockitoBean UserService và SocketIOBroadcastService vì là giao tiếp ngoại biên.
 * - Minh chứng (CheckDB): Query thẳng lên DB qua repository sau mỗi action.
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
// Tránh IDE/bootstrap nhầm profile: luôn thay DataSource bằng DB nhúng (không dùng MySQL thật).
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@DisplayName("NotificationService Integration Tests (H2 Database)")
class NotificationServiceTest {

    @AfterEach
    void resetNotificationRepositorySpy() {
        reset(notificationRepository);
    }

    // -----------------------------------------------------------------------
    // Autowired dependencies (True Database Logic)
    // -----------------------------------------------------------------------
    @MockitoSpyBean
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationService notificationService;

    // -----------------------------------------------------------------------
    // Mock dependencies (External Communication)
    // -----------------------------------------------------------------------
    @MockitoBean
    private UserService userService;

    @MockitoBean
    private SocketIOBroadcastService socketIOBroadcastService;

    // =======================================================================
    // PHẦN 1: Hàm createNotification()
    // =======================================================================

    // Test Case ID: UTIL-NT01
    // Mục tiêu: createNotification - Phải lưu SENT và gọi pushNotification
    @Test
    @DisplayName("UTIL-NT01: createNotification - Phải lưu SENT và gọi pushNotification")
    void createNotification_ValidInputs_ShouldSaveToDbAndBroadcast() {
        // Chuẩn bị: Khởi tạo dữ liệu đầu vào.
        // Mock: Giả định phương thức pushNotification() của SocketIOBroadcastService chạy thành công và không ném lỗi.


        doNothing().when(socketIOBroadcastService).pushNotification(any());

        // Thực thi: Gọi hàm createNotification() với recipientId là 100L.
        Notification result = notificationService.createNotification(100L, "New Notification", "Content");

        // Kiểm tra logic Object trả về: Đảm bảo notification trả về có ID hợp lệ và trạng thái là SENT.
        assertThat(result.getId()).isNotNull();
        assertThat(result.getDeliveryStatus()).isEqualTo("SENT");

        // Minh chứng (CheckDB): Truy vấn DB H2 để xác nhận dòng Entity mới tương ứng đã nằm trong bảng mảng Data.
        Notification inDb = notificationRepository.findById(result.getId()).orElse(null);
        assertThat(inDb).isNotNull();
        assertThat(inDb.getTitle()).isEqualTo("New Notification");
        assertThat(inDb.getRecipientId()).isEqualTo(100L);
        assertThat(inDb.isRead()).isFalse();

        // Kiểm tra giao tiếp ngoại biên: Xác nhận hàm socketIOBroadcastService.pushNotification() được gọi đúng 1 lần.


        verify(socketIOBroadcastService, times(1)).pushNotification(any());
    }

    // -----------------------------------------------------------------------
    // getNotificationsByRecipient — bổ sung độ phủ (JaCoCo) cho NotificationService
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("getNotificationsByRecipient: trả về đúng danh sách theo recipientId từ DB")
    void getNotificationsByRecipient_ShouldReturnFromRepository() {
        Notification a = new Notification();
        a.setRecipientId(200L);
        a.setTitle("A");
        notificationRepository.save(a);
        Notification b = new Notification();
        b.setRecipientId(201L);
        notificationRepository.save(b);

        List<Notification> for200 = notificationService.getNotificationsByRecipient(200L);

        assertThat(for200).hasSize(1);
        assertThat(for200.get(0).getRecipientId()).isEqualTo(200L);
    }

    // =======================================================================
    // PHẦN 2: Hàm markAsRead()
    // =======================================================================

    // Test Case ID: UTIL-NT02
    // Mục tiêu: markAsRead - Chưa đọc, phải set isRead=true và broadcast unreadCount
    @Test
    @DisplayName("UTIL-NT02: markAsRead - Chưa đọc, phải set isRead=true và broadcast unreadCount")
    void markAsRead_UnreadNotification_ShouldUpdateDbAndBroadcast() {
        // Chuẩn bị DB: Insert trực tiếp vào H2 Database một thông báo chưa đọc (isRead = false) của user 100L.
        Notification unreadNoti = new Notification();
        unreadNoti.setRecipientId(100L);
        unreadNoti.setTitle("New Notification");
        unreadNoti.setMessage("Content");
        unreadNoti.setRead(false);
        unreadNoti.setDeliveryStatus("SENT");
        notificationRepository.save(unreadNoti);

        // Mock: Giả định các cuộc gọi publishUnreadCount và pushNotification tới socket không gặp lỗi.
        doNothing().when(socketIOBroadcastService).publishUnreadCount(anyLong(), anyLong());
        doNothing().when(socketIOBroadcastService).pushNotification(any());

        // Thực thi: Đánh dấu thông báo trên là đã đọc bằng hàm markAsRead().
        notificationService.markAsRead(unreadNoti.getId());

        // Minh chứng (CheckDB): Truy vấn lại record từ H2 Database và đối chiếu trường isRead phải là True, và thời gian ReadAt không null.
        Notification updatedInDb = notificationRepository.findById(unreadNoti.getId()).orElse(null);
        assertThat(updatedInDb).isNotNull();
        assertThat(updatedInDb.isRead()).isTrue();
        assertThat(updatedInDb.getReadAt()).isNotNull();

        // Kiểm tra giao tiếp ngoại biên: Hệ thống phải gọi hàm publishUnreadCount để cập nhật Notification Counter qua socket.
        verify(socketIOBroadcastService, times(1)).publishUnreadCount(eq(100L), anyLong());
        verify(socketIOBroadcastService, times(1)).pushNotification(any());

    }

    // Test Case ID: UTIL-NT03
    // Mục tiêu: markAsRead - Đã đọc tồn tại trong DB, KHÔNG cập nhật thừa
    @Test
    @DisplayName("UTIL-NT03: markAsRead - Đã đọc tồn tại trong DB, KHÔNG cập nhật thừa")
    void markAsRead_AlreadyReadNotification_ShouldNotBroadcast() {
        // Chuẩn bị DB: Insert trực tiếp 1 bản ghi thông báo đã được đọc từ trước (isRead = true).
        Notification readNoti = new Notification();
        readNoti.setRecipientId(100L);
        readNoti.setRead(true);
        readNoti.setReadAt(LocalDateTime.now().minusDays(1));
        notificationRepository.save(readNoti);

        // Thực thi: Đánh dấu lại một thông báo vốn dĩ đã đọc.
        notificationService.markAsRead(readNoti.getId());

        // Kiểm tra giao tiếp ngoại biên: Phải xác định hệ thống thông minh bỏ qua, KHÔNG gửi lãng phí bất kỳ lượt Socket Broadcast nào.



        verify(socketIOBroadcastService, never()).publishUnreadCount(anyLong(), anyLong());
        verify(socketIOBroadcastService, never()).pushNotification(any());
    }

    // Test Case ID: UTIL-NT04
    // Mục tiêu: markAsRead - ID không tồn tại phải throw NotificationNotFoundException
    @Test
    @DisplayName("UTIL-NT04: markAsRead - ID không tồn tại phải throw NotificationNotFoundException")
    void markAsRead_NonExistentNotification_ShouldThrowNotificationNotFoundException() {
        // Thực thi & Kiểm tra Catch Lỗi: Truyền vào ID = 99999L không tồn tại và hy vọng bắt được ngoại lệ NotificationNotFoundException.
        assertThatThrownBy(() -> notificationService.markAsRead(99999L))



                .isInstanceOf(NotificationNotFoundException.class);

    }

    // =======================================================================
    // PHẦN 3: Hàm markAllAsRead()
    // =======================================================================

    // Test Case ID: UTIL-NT05
    // Mục tiêu: markAllAsRead - Update nhiều record trong DB và phân phát Socket
    @Test
    @DisplayName("UTIL-NT05: markAllAsRead - Update nhiều record trong DB và phân phát Socket")
    void markAllAsRead_WithUnreadNotifications_ShouldUpdateDbAndBroadcastZero() {
        // Chuẩn bị DB: Sử dụng vòng lặp để Insert 3 thông báo đang CHƯA ĐỌC của user 100L vào Database.
        for (int i = 0; i < 3; i++) {
            Notification n = new Notification();
            n.setRecipientId(100L);
            n.setRead(false);
            notificationRepository.save(n);
        }
        
        // Mock: Chuẩn bị đường truyền Socket broadcast ko báo lỗi.
        doNothing().when(socketIOBroadcastService).publishUnreadCount(anyLong(), anyLong());

        // Thực thi: Kích hoạt hàm markAllAsRead cho user 100L.
        int count = notificationService.markAllAsRead(100L);

        // Kiểm tra kết quả trả về: Hàm phải thông báo số lượng bản ghi vừa update là 3.
        assertThat(count).isEqualTo(3);
        
        // Minh chứng (CheckDB): Đúng 3 thông báo của recipient 100 đều đã đọc.
        List<Notification> for100 = notificationRepository.findByRecipientId(100L);
        assertThat(for100).hasSize(3);
        assertThat(for100).allMatch(Notification::isRead);

        // Kiểm tra giao tiếp ngoại biên: SocketIO phải được lệnh gọi thông báo unread count về 0.
        verify(socketIOBroadcastService, times(1)).publishUnreadCount(eq(100L), eq(0L));
    }

    // Test Case ID: UTIL-NT06
    // Mục tiêu: markAllAsRead - Không có thông báo chưa đọc, Không broadcast
    @Test
    @DisplayName("UTIL-NT06: markAllAsRead - Không có thông báo chưa đọc, Không broadcast")
    void markAllAsRead_WithNoUnreadNotifications_ShouldNotBroadcast() {
        // Chuẩn bị: User 100 chỉ có thông báo đã đọc — không còn bản ghi is_read=false.
        Notification readOnly = new Notification();
        readOnly.setRecipientId(100L);
        readOnly.setRead(true);
        readOnly.setReadAt(LocalDateTime.now().minusDays(1));
        notificationRepository.save(readOnly);

        // Thực thi: markAllAsRead khi không có thông báo chưa đọc.
        int count = notificationService.markAllAsRead(100L);

        // Kiểm tra kết quả trả về: Không cập nhật dòng nào (0).
        assertThat(count).isEqualTo(0);
        
        // Kiểm tra giao tiếp ngoại biên: SocketIO tuyệt đối không được kích hoạt thừa thãi.
        verify(socketIOBroadcastService, never()).publishUnreadCount(anyLong(), anyLong());
    }

    // =======================================================================
    // PHẦN 4: Hàm getAllNotificationsWithFilters()
    // =======================================================================

    // Test Case ID: UTIL-NT07
    // Mục tiêu: getAllNotificationsWithFilters - Theo recipientId từ DB
    @Test
    @DisplayName("UTIL-NT07: getAllNotificationsWithFilters - Theo recipientId từ DB")
    void getAllNotificationsWithFilters_WithRecipientId_ShouldQueryDbSuccessfully() {
        // Chuẩn bị DB: Insert 2 thông báo cho user 100L và 1 thông báo cho user 999L.
        Notification n1 = new Notification(); n1.setRecipientId(100L); notificationRepository.save(n1);
        Notification n2 = new Notification(); n2.setRecipientId(100L); notificationRepository.save(n2);
        Notification n3 = new Notification(); n3.setRecipientId(999L); notificationRepository.save(n3);

        Pageable pageable = PageRequest.of(0, 10);

        // Thực thi: Query tất cả thông báo của riêng user 100L.
        PaginationDTO result = notificationService.getAllNotificationsWithFilters(100L, null, pageable);

        // Kiểm tra dữ liệu phân trang: Tổng số lượng lấy được phải đúng là 2. Size list cũng là 2.
        assertThat(result.getMeta().getTotal()).isEqualTo(2L);
        assertThat(((List<?>) result.getResult())).hasSize(2);


    }

    // Test Case ID: UTIL-NT08
    // Mục tiêu: getAllNotificationsWithFilters - Lọc theo giao hàng (Status)
    @Test
    @DisplayName("UTIL-NT08: getAllNotificationsWithFilters - Lọc theo giao hàng (Status)")
    void getAllNotificationsWithFilters_WithStatus_ShouldQueryDb() {
        // Chuẩn bị DB: Insert cắm mốc 1 records bị FAILED và hàng loạt cái khác sẽ trống.
        Notification n1 = new Notification(); n1.setDeliveryStatus("FAILED"); notificationRepository.save(n1);
        
        Pageable pageable = PageRequest.of(0, 10);

        // Thực thi: Trực tiếp lấy mọi records lọc theo từ khoá FAILED.
        PaginationDTO result = notificationService.getAllNotificationsWithFilters(null, "FAILED", pageable);

        // Kiểm tra: Kết quả phải giới hạn chính xác là 1 phần tử và nó phải mang trạng thái FAILED.
        assertThat(result.getMeta().getTotal()).isEqualTo(1L);
        assertThat(((Notification) ((List<?>) result.getResult()).get(0)).getDeliveryStatus()).isEqualTo("FAILED");


    }

    // Test Case ID: UTIL-NT09
    // Mục tiêu: getAllNotificationsWithFilters - Cả hai null, query toàn cục
    @Test
    @DisplayName("UTIL-NT09: getAllNotificationsWithFilters - Cả hai null, query toàn cục")
    void getAllNotificationsWithFilters_WithNullBoth_ShouldQueryAllInDb() {
        // Chuẩn bị DB: Nhét vào DB 1 thông báo chung.
        Notification n1 = new Notification(); notificationRepository.save(n1);
        
        Pageable pageable = PageRequest.of(0, 10);

        // Thực thi: Fetch DB với Parameter Null toàn bộ.
        PaginationDTO result = notificationService.getAllNotificationsWithFilters(null, null, pageable);

        // Kiểm tra: Hàm trả ra số đếm bản ghi trong DB vì chạy hàm FindAll() không giới hạn.
        assertThat(result.getMeta().getTotal()).isEqualTo(1L);

    }

    // =======================================================================
    // PHẦN 5: Hàm getNotificationStats()
    // =======================================================================

    // Test Case ID: UTIL-NT10
    // Mục tiêu: getNotificationStats - SQL Count unread cho cụ thể 1 recipient
    @Test
    @DisplayName("UTIL-NT10: getNotificationStats - SQL Count unread cho cụ thể 1 recipient")
    void getNotificationStats_WithRecipientId_ShouldCountFromDb() {
        // Chuẩn bị DB: Insert 1 CHƯA ĐỌC và 1 ĐÃ ĐỌC trực thuộc sự sỡ hữu của Admin (Id: 100L).
        Notification n1 = new Notification(); n1.setRecipientId(100L); n1.setRead(false); notificationRepository.save(n1);
        Notification n2 = new Notification(); n2.setRecipientId(100L); n2.setRead(true); notificationRepository.save(n2);

        // Thực thi: Lấy bộ chỉ số đếm của User 100L.
        Map<String, Object> stats = notificationService.getNotificationStats(100L);

        // Kiểm tra SQL đếm: Tổng trong hệ thống là 2 bản ghi, tuy nhiên User 100L chỉ có đúng 1 bản ghi Chưa đọc.
        assertThat(stats.get("totalNotifications")).isEqualTo(2L);
        assertThat(stats.get("unreadNotifications")).isEqualTo(1L);




    }

    // Test Case ID: UTIL-NT11
    // Mục tiêu: getNotificationStats - recipient null -> thống kê unread toàn hệ thống
    @Test
    @DisplayName("UTIL-NT11: getNotificationStats - recipient null -> thống kê unread toàn hệ thống")
    void getNotificationStats_WithNullRecipientId_ShouldReturnGlobalUnreadCount() {
        // Chuẩn bị DB: 3 User khác nhau. 101 và 102 chưa đọc. 103 đã đọc.
        Notification n1 = new Notification(); n1.setRecipientId(101L); n1.setRead(false); notificationRepository.save(n1);
        Notification n2 = new Notification(); n2.setRecipientId(102L); n2.setRead(false); notificationRepository.save(n2);
        Notification n3 = new Notification(); n3.setRecipientId(103L); n3.setRead(true); notificationRepository.save(n3);

        // Thực thi: Truyền tham số null để lấy Unread Count góc nhìn bao quát.
        Map<String, Object> stats = notificationService.getNotificationStats(null);

        // Kiểm tra: DB đếm bao quát sẽ trả về cho ta là 2 người đang có tin lưu đọng.
        assertThat(stats.get("totalNotifications")).isEqualTo(3L);
        assertThat(stats.get("unreadNotifications")).isEqualTo(2L);


    }

    // =======================================================================
    // PHẦN 6: Hàm processNotificationEvent()
    // =======================================================================

    // Test Case ID: UTIL-NT12
    // Mục tiêu: processNotificationEvent - Tạo và Insert vào DB từ Bulk Kafka Event
    @Test
    @DisplayName("UTIL-NT12: processNotificationEvent - Tạo và Insert vào DB từ Bulk Kafka Event")
    void processNotificationEvent_WithSingleRecipient_ShouldInsertToDb() {
        // Chuẩn bị: Event có Recipient Id tĩnh để gửi thẳng.
        NotificationEvent event = new NotificationEvent();
        event.setRecipientId(100L);
        event.setTitle("Kafka Event");
        event.setMessage("Kafka Content");

        // Thực thi: Quá trình xử lý Kafka Event theo cơ chế đồng bộ (trực tiếp).
        notificationService.processNotificationEvent(event);

        // Minh chứng (CheckDB): Phát hiện số lượng notification trong DB đã nẩy lên 1 dòng, đồng thời đúng dữ liệu title set trong Kafka.
        List<Notification> inDb = notificationRepository.findAll();
        assertThat(inDb).hasSize(1);
        assertThat(inDb.get(0).getTitle()).isEqualTo("Kafka Event");
        assertThat(inDb.get(0).getRecipientId()).isEqualTo(100L);

    }

    // Test Case ID: UTIL-NT13
    // Mục tiêu: processNotificationEvent - includeAllEmployees = true
    @Test
    @DisplayName("UTIL-NT13: processNotificationEvent - includeAllEmployees = true")
    void processNotificationEvent_IncludeAllEmployees() {
        // Chuẩn bị: Đặt cờ Include All = true để gửi tin báo đại chúng.
        NotificationEvent event = new NotificationEvent();
        event.setTitle("Kafka Event");
        event.setMessage("Kafka Content");
        event.setIncludeAllEmployees(true);
        // Mock: Giả định UserService lấy được 2 id employee trong cty.
        when(userService.getAllEmployeeIds(any())).thenReturn(Arrays.asList(1L, 2L));

        // Thực thi: Process kiện tin của Kafka.
        notificationService.processNotificationEvent(event);

        // Minh chứng (CheckDB): Hàm sẽ đẩy độc lập 2 Object khác nhau lưu vào database, Size = 2.
        List<Notification> inDb = notificationRepository.findAll();
        assertThat(inDb).hasSize(2);
        assertThat(inDb.stream().map(Notification::getRecipientId).toList()).containsExactlyInAnyOrder(1L, 2L);
    }

    // Test Case ID: UTIL-NT14
    // Mục tiêu: processNotificationEvent - departmentId != null ưu tiên
    @Test
    @DisplayName("UTIL-NT14: processNotificationEvent - departmentId != null ưu tiên")
    void processNotificationEvent_DepartmentId() {
        // Chuẩn bị: Thử nghiệm phân giải người gửi kèm cả DepartmentId và PositionId.
        NotificationEvent event = new NotificationEvent();
        event.setTitle("Kafka Event");
        event.setMessage("Kafka Content");
        event.setDepartmentId(1L);
        event.setPositionId(2L); 
        // Mock: Bộ filter của userService trả ra nhân viên ID 3 giả lập.
        when(userService.getEmployeeIdsByFilters(anyLong(), anyLong(), any())).thenReturn(Arrays.asList(3L));

        // Thực thi: Event sẽ chạy vào khối lồng ưu tiên đầu là if(departmentId != null)
        notificationService.processNotificationEvent(event);

        // Minh chứng (CheckDB): Nó sẽ chọc xuống UserService để lấy và tạo insert message cho User 3L.
        List<Notification> inDb = notificationRepository.findAll();
        assertThat(inDb).hasSize(1);
        assertThat(inDb.get(0).getRecipientId()).isEqualTo(3L);
    }

    // Test Case ID: UTIL-NT15
    // Mục tiêu: processNotificationEvent - positionId != null và department = null
    @Test
    @DisplayName("UTIL-NT15: processNotificationEvent - positionId != null và department = null")
    void processNotificationEvent_PositionId() {
        // Chuẩn bị: Thử nghiệm chỉ filter mảng qua chức danh trong toàn tập đoàn.
        NotificationEvent event = new NotificationEvent();
        event.setTitle("Kafka Event");
        event.setMessage("Kafka Content");
        event.setPositionId(2L);
        // Mock: UserService phát hiện 1 nhân sự đáp ứng Position là ID 4L.
        when(userService.getEmployeeIdsByFilters(isNull(), eq(2L), any())).thenReturn(Arrays.asList(4L));

        // Thực thi: Bộ phân luồng filter event.
        notificationService.processNotificationEvent(event);

        // Minh chứng (CheckDB): Chốt hạ db đã bắn bản ghi vào inbox User 4L.
        List<Notification> inDb = notificationRepository.findAll();
        assertThat(inDb).hasSize(1);
        assertThat(inDb.get(0).getRecipientId()).isEqualTo(4L);
    }

    // Test Case ID: UTIL-NT16
    // Mục tiêu: processNotificationEvent - recipientIds list
    @Test
    @DisplayName("UTIL-NT16: processNotificationEvent - recipientIds list")
    void processNotificationEvent_RecipientIds() {
        // Chuẩn bị: Admin ném ngay một mảng danh sách chỉ định thẳng mặt User gửi.
        NotificationEvent event = new NotificationEvent();
        event.setTitle("Kafka Event");
        event.setMessage("Kafka Content");
        event.setRecipientIds(Arrays.asList(5L, 6L));

        // Thực thi: Xử lý danh sách event thẳng tắp tránh khỏi bộ lọc điều kiện rối.
        notificationService.processNotificationEvent(event);

        // Minh chứng (CheckDB): Dữ liệu phọt xuống H2 Database phải là 2.
        List<Notification> inDb = notificationRepository.findAll();
        assertThat(inDb).hasSize(2);
        assertThat(inDb.stream().map(Notification::getRecipientId).toList()).containsExactlyInAnyOrder(5L, 6L);
    }

    // Test Case ID: UTIL-NT17
    // Mục tiêu: processNotificationEvent - Không có recipient -> không insert DB
    @Test
    @DisplayName("UTIL-NT17: processNotificationEvent - Không có recipient -> không insert DB")
    void processNotificationEvent_WithEmptyRecipients_ShouldNotInsertToDb() {
        // Chuẩn bị: Một Kafka event trống không, không có đích đến cụ thể.
        NotificationEvent event = new NotificationEvent();
        event.setTitle("Kafka Empty");
        event.setMessage("Message");


        long countBefore = notificationRepository.count();


        // Thực thi: Cố gắng nạp Kafka Event.
        notificationService.processNotificationEvent(event);

        // Minh chứng (Check DB): Hệ thống từ chối và thoát sớm (return early). Không gia tăng rác DB.
        long countAfter = notificationRepository.count();
        assertThat(countAfter).isEqualTo(countBefore);
    }

    // =======================================================================
    // PHẦN 7: Hàm createBulkNotificationsByConditions()
    // =======================================================================

    // Test Case ID: UTIL-NT18
    // Mục tiêu: createBulkNotifications - includeAllEmployees, Bulk Insert
    @Test
    @DisplayName("UTIL-NT18: createBulkNotifications - includeAllEmployees, Bulk Insert")
    void createBulkNotifications_IncludeAllEmployees_ShouldInsertDbForAll() {
        // Chuẩn bị: Tham số đánh mốc cờ nã pháo diện rộng (All Employees).
        BulkNotificationRequest request = new BulkNotificationRequest();
        request.setIncludeAllEmployees(true);
        request.setTitle("Announce");
        request.setMessage("Content");

        // Mock: Giả lập trong DB User Service đang có bộ ba nhân viên (1, 2, 3).
        when(userService.getAllEmployeeIds(any())).thenReturn(Arrays.asList(1L, 2L, 3L));

        // Setup Mock static: Vì nó bóc JWT Token truyền qua Internal Request tới User Service.
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("mock-token"));

            // Thực thi: Controller truyền xuống bulk function.
            int count = notificationService.createBulkNotificationsByConditions(request);


            // Kiểm tra Logic: Phải confirm tổng số messages tạo là 3 đếm trên result function.
            assertThat(count).isEqualTo(3);
            
            // Minh chứng (CheckDB): Vị trí dưới DB phải trọn vẹn 3 rows Notification.
            assertThat(notificationRepository.count()).isEqualTo(3L);
            assertThat(notificationRepository.findAll().stream().map(Notification::getRecipientId).toList())
                    .containsExactlyInAnyOrder(1L, 2L, 3L);
        }
    }

    // Test Case ID: UTIL-NT19
    // Mục tiêu: createBulkNotifications - recipientIds [1,2], insert 2 rows in DB
    @Test
    @DisplayName("UTIL-NT19: createBulkNotifications - recipientIds [1,2], insert 2 rows in DB")
    void createBulkNotifications_WithSpecificRecipientIds_ShouldInsertDb() {
        // Chuẩn bị: Filter điền form gửi notification cho 2 cá thể [1,2] duy nhất.
        BulkNotificationRequest request = new BulkNotificationRequest();
        request.setRecipientIds(Arrays.asList(1L, 2L));
        request.setTitle("Announce");
        request.setMessage("Content");

        // Mock Static security: Lấy JWT của người gửi đính kèm vào.
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("mock-token"));

            // Thực thi
            int count = notificationService.createBulkNotificationsByConditions(request);


            // Kiểm chứng: Return là 2. Check Database bằng count() assert phải là 2 rows.
            assertThat(count).isEqualTo(2);
            assertThat(notificationRepository.count()).isEqualTo(2L);
            assertThat(notificationRepository.findAll().stream().map(Notification::getRecipientId).toList())
                    .containsExactlyInAnyOrder(1L, 2L);
        }
    }

    // Test Case ID: UTIL-NT20
    // Mục tiêu: createBulkNotifications - Theo filters (department, status, ...)
    @Test
    @DisplayName("UTIL-NT20: createBulkNotifications - Theo filters (department, status, ...)")
    void createBulkNotifications_WithFilters() {
        // Chuẩn bị: Mở filter cho tuỳ chỉnh Department và Status (Đang active).
        BulkNotificationRequest request = new BulkNotificationRequest();
        request.setTitle("Announce");
        request.setMessage("Content");
        request.setDepartmentId(1L);
        request.setStatus("ACTIVE");

        // Mock: Giả lập UserService query API EmployeeIdsByFilter và móc ra 10L với 11L.
        when(userService.getEmployeeIdsByFilters(eq(1L), isNull(), eq("ACTIVE"), isNull(), any()))
                .thenReturn(Arrays.asList(10L, 11L));

        // Mock Static config: Inject token context
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("mock-token"));

            // Thực thi: Tạo thông báo hàng loạt thông qua tập mảng lọc.
            int count = notificationService.createBulkNotificationsByConditions(request);


            // Kiểm chứng & Minh chứng Database (H2): Chỉ số phải bằng 2 do 10L và 11L đã vào DB inbox.
            assertThat(count).isEqualTo(2);
            assertThat(notificationRepository.count()).isEqualTo(2L);
            assertThat(notificationRepository.findAll().stream().map(Notification::getRecipientId).toList())
                    .containsExactlyInAnyOrder(10L, 11L);
        }
    }

    // Test Case ID: UTIL-NT21
    // Mục tiêu: createBulkNotifications - Dùng recipientId đơn lẻ
    @Test
    @DisplayName("UTIL-NT21: createBulkNotifications - Dùng recipientId đơn lẻ")
    void createBulkNotifications_WithSingleRecipientId() {
        // Chuẩn bị: Đích đến là 99L.
        BulkNotificationRequest request = new BulkNotificationRequest();
        request.setTitle("Announce");
        request.setMessage("Content");
        request.setRecipientId(99L);

        // Mock Static SecurityUtil để lấy được mock-token truyền vào request
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("mock-token"));

            // Thực thi: Kích hoạt gửi bulk notification theo quy quy mô số ít.
            int count = notificationService.createBulkNotificationsByConditions(request);

            // Kiểm tra: Hàm trả lời là 1 nhân sự đã thụ hưởng.
            assertThat(count).isEqualTo(1);
            assertThat(notificationRepository.count()).isEqualTo(1L);
            Notification saved = notificationRepository.findAll().get(0);
            assertThat(saved.getRecipientId()).isEqualTo(99L);
        }
    }

    // Test Case ID: UTIL-NT22
    // Mục tiêu: createBulkNotifications - Không có ai thoả mãn -> return 0
    @Test
    @DisplayName("UTIL-NT22: createBulkNotifications - Không có ai thoả mãn -> return 0")
    void createBulkNotifications_EmptyRecipients() {
        // Chuẩn bị: Gửi yêu cầu mà rỗng mọi filters.
        BulkNotificationRequest request = new BulkNotificationRequest();

        long before = notificationRepository.count();

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("mock-token"));

            // Thực thi: Gọi cơ chế bulk.
            int count = notificationService.createBulkNotificationsByConditions(request);

            // Kiểm tra: Hàm cảnh giác ngay từ khi Set HashSet recipientIds bị trống, Return 0 tức thì.
            assertThat(count).isEqualTo(0);
            assertThat(notificationRepository.count()).isEqualTo(before);
        }
    }

    // -----------------------------------------------------------------------
    // Bổ sung nhánh resolveRecipientsFromRequest (100% branch JaCoCo)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Bulk: chỉ positionId (không department) — gọi filter 5 tham số")
    void createBulkNotifications_PositionIdOnly_ResolvesViaUserService() {
        BulkNotificationRequest request = new BulkNotificationRequest();
        request.setTitle("Announce");
        request.setMessage("Content");
        request.setPositionId(7L);

        when(userService.getEmployeeIdsByFilters(isNull(), eq(7L), isNull(), isNull(), any()))
                .thenReturn(Collections.singletonList(70L));

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("mock-token"));
            int count = notificationService.createBulkNotificationsByConditions(request);
            assertThat(count).isEqualTo(1);
            assertThat(notificationRepository.findAll().get(0).getRecipientId()).isEqualTo(70L);
        }
    }

    @Test
    @DisplayName("Bulk: chỉ status — nhánh OR thứ ba trong resolveRecipientsFromRequest")
    void createBulkNotifications_StatusOnly_ResolvesViaUserService() {
        BulkNotificationRequest request = new BulkNotificationRequest();
        request.setTitle("Announce");
        request.setMessage("Content");
        request.setStatus("ACTIVE");

        when(userService.getEmployeeIdsByFilters(isNull(), isNull(), eq("ACTIVE"), isNull(), any()))
                .thenReturn(Collections.singletonList(71L));

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("mock-token"));
            int count = notificationService.createBulkNotificationsByConditions(request);
            assertThat(count).isEqualTo(1);
            assertThat(notificationRepository.findAll().get(0).getRecipientId()).isEqualTo(71L);
        }
    }

    @Test
    @DisplayName("Bulk: chỉ keyword — nhánh OR thứ tư trong resolveRecipientsFromRequest")
    void createBulkNotifications_KeywordOnly_ResolvesViaUserService() {
        BulkNotificationRequest request = new BulkNotificationRequest();
        request.setTitle("Announce");
        request.setMessage("Content");
        request.setKeyword("hr@");

        when(userService.getEmployeeIdsByFilters(isNull(), isNull(), isNull(), eq("hr@"), any()))
                .thenReturn(Collections.singletonList(72L));

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("mock-token"));
            int count = notificationService.createBulkNotificationsByConditions(request);
            assertThat(count).isEqualTo(1);
            assertThat(notificationRepository.findAll().get(0).getRecipientId()).isEqualTo(72L);
        }
    }

    @Test
    @DisplayName("Bulk: recipientIds rỗng nhưng có recipientId — bỏ qua addAll danh sách")
    void createBulkNotifications_EmptyRecipientIdsList_StillUsesSingleRecipientId() {
        BulkNotificationRequest request = new BulkNotificationRequest();
        request.setTitle("Announce");
        request.setMessage("Content");
        request.setRecipientIds(Collections.emptyList());
        request.setRecipientId(88L);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("mock-token"));
            int count = notificationService.createBulkNotificationsByConditions(request);
            assertThat(count).isEqualTo(1);
            assertThat(notificationRepository.findAll().get(0).getRecipientId()).isEqualTo(88L);
        }
    }

    @Test
    @DisplayName("Bulk: status rỗng không kích hoạt nhánh filter userService khi không còn điều kiện khác")
    void createBulkNotifications_BlankStatusOnly_ReturnsZero() {
        BulkNotificationRequest request = new BulkNotificationRequest();
        request.setTitle("Announce");
        request.setMessage("Content");
        request.setStatus("");


        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("mock-token"));
            int count = notificationService.createBulkNotificationsByConditions(request);
            assertThat(count).isEqualTo(0);
        }
        verify(userService, never()).getEmployeeIdsByFilters(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Bulk: keyword \"\" (non-null, isEmpty) — đánh giá nhánh !isEmpty trong điều kiện OR")
    void createBulkNotifications_BlankKeywordOnly_ReturnsZero() {
        BulkNotificationRequest request = new BulkNotificationRequest();
        request.setTitle("Announce");
        request.setMessage("Content");
        request.setKeyword("");

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("mock-token"));
            int count = notificationService.createBulkNotificationsByConditions(request);
            assertThat(count).isEqualTo(0);
        }
        verify(userService, never()).getEmployeeIdsByFilters(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Bulk: recipientIds chứa null — lọc Objects::nonNull trước khi tạo notification")
    void createBulkNotifications_RecipientIdsWithNull_filtersNulls() {
        BulkNotificationRequest request = new BulkNotificationRequest();
        request.setTitle("Announce");
        request.setMessage("Content");
        request.setRecipientIds(Arrays.asList(90L, null, 90L));

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("mock-token"));
            int count = notificationService.createBulkNotificationsByConditions(request);
            assertThat(count).isEqualTo(1);
            assertThat(notificationRepository.findAll().get(0).getRecipientId()).isEqualTo(90L);
        }
    }

    // =======================================================================
    // PHẦN 8: Bổ sung theo bảng TC (UTIL-NT23 … UTIL-NT46)
    // Ghi chú: Một số ô “Contrast” trong bảng mâu thuẫn với code hiện tại — test khẳng định hành vi thực tế.
    // =======================================================================

    @Test
    @DisplayName("UTIL-NT23: createNotification khi title=null — vẫn lưu DB và publish socket (theo implement hiện tại)")
    void createNotification_NullTitle_ShouldPersistAndPublish() {
        doNothing().when(socketIOBroadcastService).pushNotification(any());

        Notification result = notificationService.createNotification(100L, null, "Content");

        assertThat(result.getId()).isNotNull();
        assertThat(notificationRepository.findById(result.getId())).isPresent();
        assertThat(notificationRepository.findById(result.getId()).get().getTitle()).isNull();
        verify(socketIOBroadcastService, times(1)).pushNotification(any());
        verify(socketIOBroadcastService, never()).publishUnreadCount(anyLong(), anyLong());
    }

    @Test
    @DisplayName("UTIL-NT24: createNotification khi message=null — không ném NotificationNotFoundException, vẫn lưu")
    void createNotification_NullMessage_ShouldPersistWithoutNotificationNotFoundException() {
        doNothing().when(socketIOBroadcastService).pushNotification(any());

        Notification result = notificationService.createNotification(100L, "Title", null);

        assertThat(result.getId()).isNotNull();
        assertThat(notificationRepository.findById(result.getId()).get().getMessage()).isNull();
        verify(socketIOBroadcastService, times(1)).pushNotification(any());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("UTIL-NT25: pushNotification lỗi — transaction rollback, không lưu DB")
    void createNotification_WhenPushFails_ShouldRollbackAndPropagate() {
        doThrow(new RuntimeException("socket push failed")).when(socketIOBroadcastService).pushNotification(any());

        long before = notificationRepository.count();

        assertThatThrownBy(() -> notificationService.createNotification(100L, "T", "M"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("socket push failed");

        assertThat(notificationRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("UTIL-NT26: markAsRead — hiện tại không kiểm tra recipientId; vẫn đánh dấu theo notificationId")
    void markAsRead_DifferentRecipientAllowedByCurrentImplementation_ShouldStillUpdate() {
        Notification forOtherUser = new Notification();
        forOtherUser.setRecipientId(200L);
        forOtherUser.setRead(false);
        notificationRepository.save(forOtherUser);

        doNothing().when(socketIOBroadcastService).publishUnreadCount(anyLong(), anyLong());
        doNothing().when(socketIOBroadcastService).pushNotification(any());

        notificationService.markAsRead(forOtherUser.getId());

        Notification updated = notificationRepository.findById(forOtherUser.getId()).orElseThrow();
        assertThat(updated.isRead()).isTrue();
        assertThat(updated.getReadAt()).isNotNull();
        verify(socketIOBroadcastService, times(1)).publishUnreadCount(eq(200L), anyLong());
    }

    @Test
    @DisplayName("UTIL-NT27: markAsRead idempotent khi isRead=true nhưng readAt=null — không broadcast, không ghi readAt")
    void markAsRead_ReadFlagTrueButReadAtNull_ShouldSkipSideEffects() {
        Notification odd = new Notification();
        odd.setRecipientId(100L);
        odd.setRead(true);
        odd.setReadAt(null);
        notificationRepository.save(odd);

        notificationService.markAsRead(odd.getId());

        Notification still = notificationRepository.findById(odd.getId()).orElseThrow();
        assertThat(still.isRead()).isTrue();
        assertThat(still.getReadAt()).isNull();
        verify(socketIOBroadcastService, never()).publishUnreadCount(anyLong(), anyLong());
        verify(socketIOBroadcastService, never()).pushNotification(any());
    }

    @Test
    @DisplayName("UTIL-NT28: markAllAsRead chỉ cập nhật bản ghi chưa đọc; bản đã đọc giữ nguyên readAt")
    void markAllAsRead_MixedReadUnread_ShouldOnlyTouchUnread() {
        LocalDateTime oldReadAt = LocalDateTime.now().minusDays(5);

        Notification unread1 = new Notification();
        unread1.setRecipientId(100L);
        unread1.setRead(false);
        notificationRepository.save(unread1);

        Notification unread2 = new Notification();
        unread2.setRecipientId(100L);
        unread2.setRead(false);
        notificationRepository.save(unread2);

        Notification alreadyRead = new Notification();
        alreadyRead.setRecipientId(100L);
        alreadyRead.setRead(true);
        alreadyRead.setReadAt(oldReadAt);
        notificationRepository.save(alreadyRead);

        doNothing().when(socketIOBroadcastService).publishUnreadCount(anyLong(), anyLong());

        int updated = notificationService.markAllAsRead(100L);

        assertThat(updated).isEqualTo(2);

        Notification stillRead = notificationRepository.findById(alreadyRead.getId()).orElseThrow();
        assertThat(stillRead.getReadAt()).isEqualToIgnoringNanos(oldReadAt);

        assertThat(notificationRepository.findByRecipientIdAndIsRead(100L, false)).isEmpty();
        verify(socketIOBroadcastService, times(1)).publishUnreadCount(eq(100L), eq(0L));
    }

    @Test
    @DisplayName("UTIL-NT29: markAllAsRead khi repository.update lỗi — ném exception theo implement")
    void markAllAsRead_WhenRepositoryUpdateFails_ShouldPropagate() {
        doThrow(new RuntimeException("bulk mark failed"))
                .when(notificationRepository).markAllAsReadByRecipientId(anyLong(), any());

        assertThatThrownBy(() -> notificationService.markAllAsRead(3L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("bulk mark failed");

        verify(socketIOBroadcastService, never()).publishUnreadCount(anyLong(), anyLong());
    }

    @Test
    @DisplayName("UTIL-NT30: recipientId và status cùng truyền — nhánh hiện tại ưu tiên recipientId (bỏ qua status)")
    void getAllNotificationsWithFilters_RecipientIdAndStatusBothProvided_StatusIgnoredByImplementation() {
        Notification failedFor100 = new Notification();
        failedFor100.setRecipientId(100L);
        failedFor100.setDeliveryStatus("FAILED");
        notificationRepository.save(failedFor100);

        Notification sentFor100 = new Notification();
        sentFor100.setRecipientId(100L);
        sentFor100.setDeliveryStatus("SENT");
        notificationRepository.save(sentFor100);

        PaginationDTO dto = notificationService.getAllNotificationsWithFilters(100L, "FAILED", PageRequest.of(0, 10));

        assertThat(dto.getMeta().getTotal()).isEqualTo(2L);
        List<?> rows = (List<?>) dto.getResult();
        assertThat(rows).hasSize(2);
    }

    @Test
    @DisplayName("UTIL-NT31: lọc theo status không tồn tại trong DB — trả rỗng")
    void getAllNotificationsWithFilters_UnknownStatus_ShouldReturnEmptyPage() {
        Notification n = new Notification();
        n.setRecipientId(1L);
        n.setDeliveryStatus("SENT");
        notificationRepository.save(n);

        PaginationDTO dto = notificationService.getAllNotificationsWithFilters(null, "UNKNOWN", PageRequest.of(0, 10));

        assertThat(dto.getMeta().getTotal()).isZero();
        assertThat((List<?>) dto.getResult()).isEmpty();
    }

    @Test
    @DisplayName("UTIL-NT32: phân trang — meta đúng khi page=1, size=10, tổng 15 bản ghi")
    void getAllNotificationsWithFilters_ValidPagination_SecondPageMeta() {
        for (int i = 0; i < 15; i++) {
            notificationRepository.save(new Notification());
        }

        PaginationDTO dto = notificationService.getAllNotificationsWithFilters(null, null, PageRequest.of(1, 10));

        assertThat(dto.getMeta().getTotal()).isEqualTo(15L);
        assertThat(dto.getMeta().getPages()).isEqualTo(2);
        assertThat(dto.getMeta().getPage()).isEqualTo(2);
        assertThat(dto.getMeta().getPageSize()).isEqualTo(10);
        assertThat(((List<?>) dto.getResult())).hasSize(5);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("UTIL-NT33: sort createdAt DESC — thứ tự đúng trên Pageable")
    void getAllNotificationsWithFilters_SortByCreatedAtDesc_ShouldOrderNewestFirst() throws InterruptedException {
        Notification older = new Notification();
        older.setRecipientId(99L);
        notificationRepository.save(older);

        Thread.sleep(50);

        Notification newer = new Notification();
        newer.setRecipientId(99L);
        notificationRepository.save(newer);

        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        PaginationDTO dto = notificationService.getAllNotificationsWithFilters(null, null, pageable);

        List<Notification> content = (List<Notification>) (List<?>) dto.getResult();
        assertThat(content.get(0).getId()).isEqualTo(newer.getId());
        assertThat(content.get(1).getId()).isEqualTo(older.getId());
    }

    @Test
    @DisplayName("UTIL-NT34: getNotificationStats recipient không có thông báo — unread=0, total là tổng toàn hệ thống")
    void getNotificationStats_RecipientWithNoRows_UnreadZeroTotalGlobal() {
        Notification other = new Notification();
        other.setRecipientId(50L);
        other.setRead(false);
        notificationRepository.save(other);

        Map<String, Object> stats = notificationService.getNotificationStats(999L);

        assertThat(stats.get("unreadNotifications")).isEqualTo(0L);
        assertThat(stats.get("totalNotifications")).isEqualTo(1L);
    }

    @Test
    @DisplayName("UTIL-NT35: getNotificationStats khi count() lỗi — ném RuntimeException có message")
    void getNotificationStats_WhenCountFails_ShouldPropagate() {
        doThrow(new RuntimeException("count failed")).when(notificationRepository).count();

        assertThatThrownBy(() -> notificationService.getNotificationStats(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("count failed");
    }

    @Test
    @DisplayName("UTIL-NT36: processNotificationEvent(null) — NPE theo implement hiện tại")
    void processNotificationEvent_NullEvent_ShouldThrowNpe() {
        assertThatThrownBy(() -> notificationService.processNotificationEvent(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("UTIL-NT37: includeAllEmployees=true nhưng userService trả rỗng — không tạo notification")
    void processNotificationEvent_IncludeAllEmployeesEmptyList_ShouldNoOp() {
        NotificationEvent event = new NotificationEvent();
        event.setTitle("T");
        event.setMessage("M");
        event.setIncludeAllEmployees(true);
        when(userService.getAllEmployeeIds(any())).thenReturn(Collections.emptyList());

        long before = notificationRepository.count();
        notificationService.processNotificationEvent(event);
        assertThat(notificationRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("UTIL-NT38: userService lỗi khi includeAllEmployees — exception lan truyền")
    void processNotificationEvent_UserServiceFails_ShouldPropagate() {
        NotificationEvent event = new NotificationEvent();
        event.setTitle("T");
        event.setMessage("M");
        event.setIncludeAllEmployees(true);
        when(userService.getAllEmployeeIds(any())).thenThrow(new RuntimeException("user-service-down"));

        long before = notificationRepository.count();

        assertThatThrownBy(() -> notificationService.processNotificationEvent(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("user-service-down");

        assertThat(notificationRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("UTIL-NT39: recipientIds=[] và không có đích khác — không insert")
    void processNotificationEvent_EmptyRecipientIdsOnly_ShouldNoOp() {
        NotificationEvent event = new NotificationEvent();
        event.setTitle("T");
        event.setMessage("M");
        event.setRecipientIds(Collections.emptyList());

        long before = notificationRepository.count();
        notificationService.processNotificationEvent(event);
        assertThat(notificationRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("UTIL-NT40: recipientIds có null — processNotificationEvent lọc null")
    void processNotificationEvent_RecipientIdsWithNull_ShouldFilterNulls() {
        NotificationEvent event = new NotificationEvent();
        event.setTitle("T");
        event.setMessage("M");
        event.setRecipientIds(Arrays.asList(5L, null, 6L));

        notificationService.processNotificationEvent(event);

        List<Notification> all = notificationRepository.findAll();
        assertThat(all).hasSize(2);
        assertThat(all.stream().map(Notification::getRecipientId).toList()).containsExactlyInAnyOrder(5L, 6L);
    }

    @Test
    @DisplayName("UTIL-NT41: createBulkNotificationsByConditions(null) — NPE")
    void createBulkNotifications_NullRequest_ShouldThrowNpe() {
        assertThatThrownBy(() -> notificationService.createBulkNotificationsByConditions(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("UTIL-NT42: bulk includeAllEmployees=true nhưng userService rỗng — không tạo notification")
    void createBulkNotifications_IncludeAllEmployeesEmpty_ShouldReturnZero() {
        BulkNotificationRequest request = new BulkNotificationRequest();
        request.setIncludeAllEmployees(true);
        request.setTitle("T");
        request.setMessage("M");
        when(userService.getAllEmployeeIds(any())).thenReturn(Collections.emptyList());

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("mock-token"));
            long before = notificationRepository.count();
            int count = notificationService.createBulkNotificationsByConditions(request);
            assertThat(count).isZero();
            assertThat(notificationRepository.count()).isEqualTo(before);
        }
    }


    @Test
    @DisplayName("UTIL-NT43: recipientIds trùng — distinct, chỉ một notification / id")
    void createBulkNotifications_DuplicateRecipientIds_ShouldDistinct() {
        BulkNotificationRequest request = new BulkNotificationRequest();
        request.setRecipientIds(Arrays.asList(1L, 1L, 2L));
        request.setTitle("T");
        request.setMessage("M");

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("mock-token"));
            int count = notificationService.createBulkNotificationsByConditions(request);
            assertThat(count).isEqualTo(2);
            assertThat(notificationRepository.findAll().stream().map(Notification::getRecipientId).toList())
                    .containsExactlyInAnyOrder(1L, 2L);
        }
    }

    @Test
    @DisplayName("UTIL-NT44: recipientIds rỗng và không có điều kiện khác — return 0")
    void createBulkNotifications_EmptyRecipientIdsOnly_ShouldReturnZero() {
        BulkNotificationRequest request = new BulkNotificationRequest();
        request.setRecipientIds(Collections.emptyList());
        request.setTitle("T");
        request.setMessage("M");

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("mock-token"));
            long before = notificationRepository.count();
            assertThat(notificationService.createBulkNotificationsByConditions(request)).isZero();
            assertThat(notificationRepository.count()).isEqualTo(before);
        }
    }

    @Test
    @DisplayName("UTIL-NT45: filter departmentId + positionId + status — gọi userService đủ tham số")
    void createBulkNotifications_DepartmentPositionStatus_ShouldCallUserServiceWithFilters() {
        BulkNotificationRequest request = new BulkNotificationRequest();
        request.setTitle("T");
        request.setMessage("M");
        request.setDepartmentId(1L);
        request.setPositionId(2L);
        request.setStatus("ACTIVE");

        when(userService.getEmployeeIdsByFilters(eq(1L), eq(2L), eq("ACTIVE"), isNull(), any()))
                .thenReturn(Arrays.asList(10L, 11L));

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("mock-token"));
            int count = notificationService.createBulkNotificationsByConditions(request);
            assertThat(count).isEqualTo(2);
        }

        verify(userService, times(1)).getEmployeeIdsByFilters(eq(1L), eq(2L), eq("ACTIVE"), isNull(), any());
    }

    @Test
    @DisplayName("UTIL-NT46: bulk filter — userService lỗi, exception lan truyền")
    void createBulkNotifications_FilterUserServiceFails_ShouldPropagate() {
        BulkNotificationRequest request = new BulkNotificationRequest();
        request.setTitle("T");
        request.setMessage("M");
        request.setDepartmentId(1L);
        request.setStatus("ACTIVE");

        when(userService.getEmployeeIdsByFilters(eq(1L), isNull(), eq("ACTIVE"), isNull(), any()))
                .thenThrow(new RuntimeException("filter failed"));

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("mock-token"));
            long before = notificationRepository.count();

            assertThatThrownBy(() -> notificationService.createBulkNotificationsByConditions(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("filter failed");

            assertThat(notificationRepository.count()).isEqualTo(before);
        }
    }
}
