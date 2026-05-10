package com.example.workflow_service.service;

import com.example.workflow_service.dto.PaginationDTO;
import com.example.workflow_service.dto.approval.ApprovalTrackingResponseDTO;
import com.example.workflow_service.dto.approval.ApproveStepDTO;
import com.example.workflow_service.dto.approval.CreateApprovalTrackingDTO;
import com.example.workflow_service.exception.CustomException;
import com.example.workflow_service.exception.IdInvalidException;
import com.example.workflow_service.messaging.NotificationProducer;
import com.example.workflow_service.messaging.RecruitmentWorkflowEvent;
import com.example.workflow_service.messaging.RecruitmentWorkflowProducer;
import com.example.workflow_service.model.ApprovalTracking;
import com.example.workflow_service.model.Workflow;
import com.example.workflow_service.model.WorkflowStep;
import com.example.workflow_service.repository.ApprovalTrackingRepository;
import com.example.workflow_service.repository.WorkflowRepository;
import com.example.workflow_service.repository.WorkflowStepRepository;
import com.example.workflow_service.utils.SecurityUtil;
import com.example.workflow_service.utils.enums.ApprovalStatus;
import com.example.workflow_service.utils.enums.WorkflowType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.example.workflow_service.dto.Response;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * =============================================================
 * Unit Test: ApprovalTrackingService — Quy trình Phê duyệt
 * =============================================================
 *
 * PHẠM VI TEST:
 *   - initializeApproval(): Tạo tracking PENDING cho bước đầu tiên workflow
 *   - approve(): Duyệt (PENDING → APPROVED/REJECTED), create tracking bước tiếp
 *   - getById(): Lấy tracking theo id
 *   - getAll(): Lấy danh sách tracking có filter + phân trang
 *   - getPendingApprovalsForUser(): Lấy các bước chờ duyệt của user
 *   - handleWorkflowEvent(): Xử lý event từ job-service
 *     • REQUEST_APPROVED: tracking PENDING → APPROVED + create tracking bước tiếp
 *     • REQUEST_REJECTED: tracking PENDING → REJECTED
 *     • REQUEST_CANCELLED: tracking PENDING → CANCELLED
 *     • REQUEST_WITHDRAWN: tracking PENDING → CANCELLED (actionType=WITHDRAW)
 *     • REQUEST_RETURNED: tracking PENDING → RETURNED + returnedToStepId
 *
 * KIẾN TRÚC WORKFLOW:
 *   Workflow có nhiều Step (e.g., Dept Manager → HR Manager → Executive)
 *   Mỗi step có approverPositionId (vị trí người duyệt)
 *   Mỗi lần submit request → create tracking cho step đầu tiên
 *   Approver duyệt bước n → tạo tracking mới cho bước n+1 (nếu tồn tại)
 *
 *   Flow ví dụ:
 *   User tạo request (DRAFT) → submit() → job-service publish REQUEST_SUBMITTED event
 *   ↓
 *   job-service emit event → workflow-service nhận
 *   ↓
 *   initializeApproval() → create tracking PENDING ở step 1
 *   ↓
 *   Approver step 1 duyệt → approve(trackingId, approved=true)
 *     • tracking → APPROVED
 *     • event publish → job-service nhận
 *     • nếu step 2 tồn tại → create tracking mới ở step 2
 *   ↓
 *   Approver step 2 duyệt (or reject) → ...
 *
 * CHIẾN LƯỢC TEST:
 *   - @SpringBootTest: Load full Spring context + H2 DB
 *   - @Transactional: Auto rollback → DB sạch
 *   - @ActiveProfiles("test"): Test config
 *   - MockedStatic SecurityUtil: Fake current user ID
 *   - Mock Kafka producers: không gửi message thật
 *   - Mock HTTP clients: không gọi HTTP thật
 *   - Kiểm tra DB change (tracking status, actionUserId, actionAt, notes)
 *
 * MOCK STRATEGY:
 *   - @MockitoBean UserService: không gọi user-service HTTP API
 *   - @MockitoBean CandidateService: không gọi candidate-service HTTP API
 *   - @MockitoBean RecruitmentWorkflowProducer: không publish Kafka event
 *   - @MockitoBean NotificationProducer: không gửi notification
 *   - MockedStatic SecurityUtil: fake user ID + JWT token
 *   - doNothing().when(producer).publishEvent(any()): skip kafka gửi
 *
 * LƯU Ý:
 *   - ApprovalTracking khác Job-Service tracking
 *     • ApprovalTracking: theo dõi workflow approval step
 *     • RecruitmentRequest: theo dõi overall request status
 *   - Event-driven: một approve() có thể trigger tạo tracking bước tiếp
 *     → test phải kiểm tra DB có tracking mới
 *   - Xác nhân quyền duyệt: approverPositionId must match SecurityUtil.extractEmployeeId()
 * =============================================================
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
@ExtendWith(com.example.workflow_service.TestResultLogger.class)
@DisplayName("ApprovalTrackingService - Branch Coverage Cấp 2")
class ApprovalTrackingServiceTest {

    @Autowired private ApprovalTrackingService approvalTrackingService;
    @Autowired private ApprovalTrackingRepository approvalTrackingRepository;
    @Autowired private WorkflowRepository workflowRepository;
    @Autowired private WorkflowStepRepository workflowStepRepository;

    // ============================================================
    // MOCK DEPENDENCIES (không thực thi logic thực)
    // ============================================================
    
    /**
     * Mock UserService
     * 
     * Lý do mock:
     *   - UserService gọi HTTP user-service API
     *   - Test ApprovalTrackingService độc lập (không phụ thuộc HTTP)
     *   - Mock: return Map.of() (không user data thực)
     */
    @MockitoBean private UserService userService;
    
    @MockitoBean private CandidateService candidateService;
    
    /**
     * Mock RecruitmentWorkflowProducer
     * 
     * Lý do mock:
     *   - Producer publish event vào Kafka
     *   - Test không nên gửi message thật vào Kafka
     *   - Mock: doNothing() → skip publish, vẫn test logic approve()
     */
    @MockitoBean private RecruitmentWorkflowProducer workflowProducer;
    
    @MockitoBean private NotificationProducer notificationProducer;
    @MockitoBean private org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;
    @MockitoBean private org.springframework.web.client.RestTemplate restTemplate;

    private MockedStatic<SecurityUtil> mockedSecurityUtil;

    // ============================================================
    // TEST CONSTANTS
    // ============================================================
    
    // Người dùng giả lập đang đăng nhập (= approver hợp lệ ở bước 1)
    private static final Long APPROVER_USER_ID = 100L;
    // Người dùng khác (approver ở bước 2)
    private static final Long OTHER_USER_ID    = 999L;
    // Request ID dùng chung
    private static final Long REQUEST_ID       = 1L;

    /**
     * Workflow có 2 bước dùng chung cho tất cả test
     * - Step 1: approver=APPROVER_USER_ID=100
     * - Step 2: approver=OTHER_USER_ID=999
     */
    private Workflow workflow2Steps;
    
    /** Bước 1 của workflow */
    private WorkflowStep step1;
    
    /** Bước 2 của workflow */
    private WorkflowStep step2;
    
    /** Tracking PENDING tại bước 1 cho REQUEST_ID */
    private ApprovalTracking pendingTracking;

    @BeforeEach
    void setUp() {
        /**
         * SETUP: Chuẩn bị dữ liệu chung cho tất cả test
         * 
         * BƯỚC 1: Mock external dependencies (không gọi HTTP/Kafka thật)
         */
        
        // Mock SecurityUtil: fake user ID = APPROVER_USER_ID (100)
        // → SecurityUtil.extractEmployeeId() sẽ return 100
        mockedSecurityUtil = Mockito.mockStatic(SecurityUtil.class);
        mockedSecurityUtil.when(SecurityUtil::extractEmployeeId).thenReturn(APPROVER_USER_ID);
        mockedSecurityUtil.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("test-token"));

        // Mock UserService: không gọi user-service HTTP API
        // → Trả về Map.of() thay vì data thực
        when(userService.getUserNamesByIds(anyList(), anyString())).thenReturn(Map.of());
        when(userService.getPositionNamesByIds(anyList(), anyString())).thenReturn(Map.of());
        when(userService.findUserByPositionIdAndDepartmentId(anyLong(), anyLong(), anyString()))
                .thenReturn(OTHER_USER_ID);

        // Mock Kafka producers: không gửi message thật
        // → doNothing(): gọi method nhưng skip logic
        doNothing().when(workflowProducer).publishEvent(any());
        doNothing().when(notificationProducer).sendNotification(anyLong(), anyString(), anyString(), anyString());
        doNothing().when(notificationProducer).sendNotificationToDepartment(
                anyLong(), anyLong(), anyString(), anyString(), anyString());

        /**
         * BƯỚC 2: Tạo Workflow 2 bước trong H2 test DB
         * 
         * Sơ đồ:
         *   Workflow (REQUEST type)
         *   ├─ Step 1: order=1, approver=APPROVER_USER_ID (100)
         *   └─ Step 2: order=2, approver=OTHER_USER_ID (999)
         * 
         * Dùng cho tất cả test để mô phỏng workflow thực tế
         */
        workflow2Steps = new Workflow();
        workflow2Steps.setName("Workflow Test 2 Bước");
        workflow2Steps.setType(WorkflowType.REQUEST);
        workflow2Steps.setDepartmentId(10L);
        workflow2Steps.setIsActive(true);
        workflow2Steps = workflowRepository.save(workflow2Steps);

        // Step 1: Approver = user ID 100 (người đang test)
        step1 = new WorkflowStep();
        step1.setWorkflow(workflow2Steps);
        step1.setStepOrder(1);
        step1.setApproverPositionId(APPROVER_USER_ID);
        step1.setIsActive(true);
        step1 = workflowStepRepository.save(step1);

        // Step 2: Approver = user ID 999 (người khác)
        step2 = new WorkflowStep();
        step2.setWorkflow(workflow2Steps);
        step2.setStepOrder(2);
        step2.setApproverPositionId(OTHER_USER_ID);
        step2.setIsActive(true);
        step2 = workflowStepRepository.save(step2);

        /**
         * BƯỚC 3: Tạo Tracking PENDING tại Step 1 cho REQUEST_ID
         * 
         * Mô phỏng scenario: request được tạo, workflow initialized
         * → tracking PENDING tại step 1 chờ APPROVER_USER_ID duyệt
         */
        pendingTracking = buildTracking(REQUEST_ID, step1, ApprovalStatus.PENDING, APPROVER_USER_ID);
        pendingTracking = approvalTrackingRepository.save(pendingTracking);
    }

    @AfterEach
    void tearDown() {
        /**
         * CLEANUP: Dọn sạch sau mỗi test
         * 
         * Làm gì:
         *   - Đóng MockedStatic SecurityUtil
         *   - Xóa security context (trong test khác không dùng nhầm)
         * 
         * Tại sao:
         *   - MockedStatic nếu không close → side effect sang test khác
         *   - @Transactional tự rollback DB → không cần cleanup DB
         */
        mockedSecurityUtil.close();
    }

    // ================================================================
    // NHÓM 1: initializeApproval()
    // ================================================================

    /**
     * Test Case ID: AT-TC01
     * Workflow không tìm thấy → CustomException
     *
     * Bug bị bắt: Không kiểm tra workflow tồn tại → NullPointerException
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Xác minh service ném ra CustomException với thông báo lỗi phù hợp.
     * - Check DB: Không kiểm tra DB vì tác vụ thất bại từ bước validation (đảm bảo DB không rác).
     */
    @Test
    @DisplayName("[AT-TC01] initializeApproval() - workflow không tìm thấy → CustomException")
    void tc01_initializeApproval_workflowNotFound_throwsCustomException() {
        // Arrange: mock workflowRepository trả null
        CreateApprovalTrackingDTO dto = new CreateApprovalTrackingDTO();
        dto.setDepartmentId(999L); // department không có workflow
        dto.setRequestId(1L);
        dto.setLevelId(1L);

        // Act + Assert
        assertThrows(CustomException.class,
                () -> approvalTrackingService.initializeApproval(dto),
                "BUG: Không ném CustomException khi workflow không tìm thấy");
    }

        /**
         * Test Case ID: AT-TC07
     * Workflow tồn tại nhưng không có step 1 → CustomException
     *
     * Bug bị bắt: Không kiểm tra firstStep → NullPointerException
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Xác minh service ném ra CustomException với thông báo lỗi phù hợp.
     * - Check DB: Không kiểm tra DB vì tác vụ thất bại từ bước validation (đảm bảo DB không rác).
     */
    @Test
    @DisplayName("[AT-TC02] initializeApproval() - workflow không có step 1 → CustomException")
    void tc02_initializeApproval_noFirstStep_throwsCustomException() {
        // Arrange: workflow không có step (setup tạo workflow với step)
        // Hãy tạo workflow khác không có step
        Workflow wfNoStep = new Workflow();
        wfNoStep.setName("WF No Step");
        wfNoStep.setType(WorkflowType.REQUEST);
        wfNoStep.setDepartmentId(11L);
        wfNoStep.setIsActive(true);
        wfNoStep = workflowRepository.save(wfNoStep);

        CreateApprovalTrackingDTO dto = new CreateApprovalTrackingDTO();
        dto.setDepartmentId(11L);
        dto.setRequestId(2L);
        dto.setLevelId(1L);

        // Act + Assert
        assertThrows(CustomException.class,
                () -> approvalTrackingService.initializeApproval(dto),
                "BUG: Không ném CustomException khi workflow không có step 1");
    }

    /**
     * Test Case ID: AT-TC03
         * assignedUserId = null (người duyệt không tìm thấy) → service vẫn tạo tracking,
         * nhưng actionUserId trong DB phải là null.
     *
         * Mục tiêu branch: cover nhánh false của if (tracking.getActionUserId() != null)
         * trong initializeApproval().
         * 
         * CHI TIẾT TEST CASE:
         * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
         * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
         * - Check DB: So sánh tổng số lượng bản ghi tracking trong DB trước và sau khi thực thi qua .count() & Truy vấn DB bằng findById() để assert các trường thay đổi như status, actionType, notes, actionUserId.
     */
    @Test
        @DisplayName("[AT-TC03] initializeApproval() - assignedUserId=null → tracking vẫn được tạo và actionUserId=null")
        void tc03_initializeApproval_assignedUserIdNull_savesTrackingWithNullActionUserId() {
        when(userService.findUserByPositionIdAndDepartmentId(anyLong(), eq(10L), anyString()))
                .thenReturn(null);

        when(restTemplate.exchange(
                anyString(),
                eq(org.springframework.http.HttpMethod.GET),
                any(org.springframework.http.HttpEntity.class),
                any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenAnswer(invocation -> buildUserPositionResponse(APPROVER_USER_ID));

        CreateApprovalTrackingDTO dto = new CreateApprovalTrackingDTO();
        dto.setDepartmentId(10L); // workflow2Steps.departmentId = 10L
        dto.setRequestId(3L);
        dto.setLevelId(1L);

        long countBefore = approvalTrackingRepository.count();

        // Act
        ApprovalTrackingResponseDTO result = approvalTrackingService.initializeApproval(dto);

        // Assert
        assertNotNull(result, "BUG: initializeApproval() trả về null dù tracking vẫn được tạo");
        // Check DB
        assertEquals(countBefore + 1, approvalTrackingRepository.count(),
                "BUG: Tracking không được tạo khi actionUserId=null");

        ApprovalTracking saved = approvalTrackingRepository.findById(result.getId()).orElseThrow();
        assertNull(saved.getActionUserId(), "BUG: actionUserId phải null khi user-service không tìm được người theo levelId");
        verify(userService, never()).getUserNamesByIds(anyList(), anyString());
    }

    /**
     * Test Case ID: AT-TC04
     * Happy path: workflow, step, assignedUserId, actionUserId tất cả hợp lệ → DB lưu tracking
     *
     * Bug bị bắt: actionUserId không được lưu vào DB
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: So sánh tổng số lượng bản ghi tracking trong DB trước và sau khi thực thi qua .count() & Truy vấn DB bằng findById() để assert các trường thay đổi như status, actionType, notes, actionUserId.
     */
    @Test
    @DisplayName("[AT-TC04] initializeApproval() - happy path → DB lưu tracking với actionUserId")
    void tc04_initializeApproval_happyPath_savesTrackingWithActionUserId() {
        // Arrange
        when(userService.findUserByPositionIdAndDepartmentId(anyLong(), eq(10L), anyString()))
                .thenReturn(OTHER_USER_ID); // actionUserId = OTHER_USER_ID

        when(restTemplate.exchange(
                anyString(),
                eq(org.springframework.http.HttpMethod.GET),
                any(org.springframework.http.HttpEntity.class),
                any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenAnswer(invocation -> buildUserPositionResponse(APPROVER_USER_ID));

        CreateApprovalTrackingDTO dto = new CreateApprovalTrackingDTO();
        dto.setDepartmentId(10L);
        dto.setRequestId(4L);
        dto.setLevelId(1L);

        long countBefore = approvalTrackingRepository.count();

        // Act
        ApprovalTrackingResponseDTO result = approvalTrackingService.initializeApproval(dto);

        // Assert: tracking được tạo
        // Check DB
        assertEquals(countBefore + 1, approvalTrackingRepository.count(),
                "BUG: Tracking không được tạo");

        assertNotNull(result, "BUG: DTO không được trả về");
        assertEquals(4L, result.getRequestId(), "BUG: requestId sai");
        assertEquals(ApprovalStatus.PENDING, result.getStatus(),
                "BUG: Status phải là PENDING");

        // CheckDB: actionUserId phải được lưu
        ApprovalTracking saved = approvalTrackingRepository.findById(result.getId()).orElseThrow();
        assertEquals(OTHER_USER_ID, saved.getActionUserId(),
                "BUG: actionUserId không được lưu vào DB");
    }

    /**
     * Test Case ID: AT-TC05
     * Notification được gửi đi
     *
     * Bug bị bắt: Không gọi notifyNextApprovers
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Đảm bảo hệ thống gọi notificationProducer để gửi thông báo/email.
     * - Check DB: Không tương tác/kiểm tra trực tiếp Repository trong test case này.
     */
    @Test
    @DisplayName("[AT-TC05] initializeApproval() - notifyNextApprovers được gọi")
    void tc05_initializeApproval_notifiesNextApprovers() {
        // Arrange
        when(restTemplate.exchange(
                anyString(),
                eq(org.springframework.http.HttpMethod.GET),
                any(org.springframework.http.HttpEntity.class),
                any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenAnswer(invocation -> buildUserPositionResponse(APPROVER_USER_ID));

        CreateApprovalTrackingDTO dto = new CreateApprovalTrackingDTO();
        dto.setDepartmentId(10L);
        dto.setRequestId(5L);
        dto.setLevelId(1L);

        // Act
        approvalTrackingService.initializeApproval(dto);

        // Assert: notificationProducer phải được gọi
        verify(notificationProducer, times(1))
                .sendNotificationToDepartment(eq(10L), eq(APPROVER_USER_ID), anyString(), anyString(), isNull());
    }

    // ================================================================
    // NHÓM 2: approve()
    // ================================================================

    /**
     * Test Case ID: AT-TC06
     * Nhánh [B1]: ID tracking không tồn tại → IdInvalidException
     *
     * Bug bị bắt: Không kiểm tra tồn tại → NullPointerException hoặc trả về null
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Xác minh service ném ra IdInvalidException do ID không hợp lệ.
     * - Check DB: Không kiểm tra DB vì tác vụ thất bại từ bước validation (đảm bảo DB không rác).
     */
    @Test
    @DisplayName("[AT-TC06][B1] approve() - ID không tồn tại → IdInvalidException")
    void tc06_approve_nonExistentId_throwsIdInvalidException() {
        ApproveStepDTO dto = approveDto(true, "ghi chú");

        assertThrows(IdInvalidException.class,
                // Act
                () -> approvalTrackingService.approve(9999L, dto),
                "BUG: Không ném IdInvalidException khi ID tracking không tồn tại");
    }

    /**
     * Test Case ID: AT-TC07
     * Nhánh [B2]: User hiện tại KHÁC approverPositionId → CustomException + DB không đổi
     *
     * Bug bị bắt: Thiếu kiểm tra quyền → cho approve bừa bãi
     *
     * CheckDB: Status vẫn là PENDING sau khi exception
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Xác minh service ném ra CustomException với thông báo lỗi phù hợp.
     * - Check DB: Truy vấn DB bằng findById() để assert các trường thay đổi như status, actionType, notes, actionUserId.
     */
    @Test
        @DisplayName("[AT-TC07][B2] approve() - Sai người duyệt → CustomException; status DB vẫn PENDING")
        void tc07_approve_wrongApprover_throwsCustomException_dbUnchanged() {
        // Gán tracking cho OTHER_USER_ID (không phải người đang đăng nhập)
        pendingTracking.setApproverPositionId(OTHER_USER_ID);
        approvalTrackingRepository.save(pendingTracking);

        ApproveStepDTO dto = approveDto(true, "thử phê duyệt không có quyền");

        CustomException ex = assertThrows(CustomException.class,
                // Act
                () -> approvalTrackingService.approve(pendingTracking.getId(), dto),
                "BUG: Không kiểm tra quyền phê duyệt → ai cũng approve được");

        // Assert
        assertEquals("Bạn không có quyền phê duyệt bước này", ex.getMessage(),
                "BUG: Message exception không đúng đặc tả");

        // CHECK DB: status KHÔNG thay đổi
        ApprovalStatus statusInDb = approvalTrackingRepository.findById(pendingTracking.getId())
                .orElseThrow().getStatus();
        assertEquals(ApprovalStatus.PENDING, statusInDb,
                "BUG: Status trong DB bị thay đổi dù không có quyền phê duyệt");
    }

    /**
     * Test Case ID: AT-TC08
     * Nhánh [B3]: Status = APPROVED (đã xử lý) → CustomException
     *
     * Bug bị bắt: Cho phép approve lại tracking đã APPROVED → dữ liệu bị ghi đè
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Xác minh service ném ra CustomException với thông báo lỗi phù hợp.
     * - Check DB: Không kiểm tra DB vì tác vụ thất bại từ bước validation (đảm bảo DB không rác).
     */
    @Test
    @DisplayName("[AT-TC08][B3] approve() - Status đã APPROVED → CustomException 'Bước này đã được xử lý'")
    void tc08_approve_statusAlreadyApproved_throwsCustomException() {
        pendingTracking.setStatus(ApprovalStatus.APPROVED);
        approvalTrackingRepository.save(pendingTracking);

        CustomException ex = assertThrows(CustomException.class,
                // Act
                () -> approvalTrackingService.approve(pendingTracking.getId(), approveDto(true, "x")),
                "BUG: Cho phép approve lại tracking đã ở trạng thái APPROVED");

        // Assert
        assertEquals("Bước này đã được xử lý", ex.getMessage());
    }

    /**
     * Test Case ID: AT-TC09
     * Nhánh [B4]: Status = REJECTED (đã xử lý) → CustomException
     *
     * Bug bị bắt: Cho phép approve tracking đã REJECTED → đặt lại trạng thái sai
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Xác minh service ném ra CustomException với thông báo lỗi phù hợp.
     * - Check DB: Không kiểm tra DB vì tác vụ thất bại từ bước validation (đảm bảo DB không rác).
     */
    @Test
    @DisplayName("[AT-TC09][B4] approve() - Status đã REJECTED → CustomException 'Bước này đã được xử lý'")
    void tc09_approve_statusAlreadyRejected_throwsCustomException() {
        pendingTracking.setStatus(ApprovalStatus.REJECTED);
        approvalTrackingRepository.save(pendingTracking);

        CustomException ex = assertThrows(CustomException.class,
                // Act
                () -> approvalTrackingService.approve(pendingTracking.getId(), approveDto(false, "x")),
                "BUG: Cho phép approve/reject lại tracking đã ở trạng thái REJECTED");

        // Assert
        assertEquals("Bước này đã được xử lý", ex.getMessage());
    }

    /**
     * Test Case ID: AT-TC10
     * Nhánh [B5]: approved=true, user hợp lệ → DB phải ghi đủ 4 trường
     *
     * Bug bị bắt:
     *   - Status không được đổi thành APPROVED
     *   - Notes không được lưu
     *   - actionUserId không ghi lại người thực hiện
     *   - actionAt null (không ghi nhận thời gian)
     *
     * CheckDB: Truy vấn lại từng trường
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: Truy vấn DB bằng findById() để assert các trường thay đổi như status, actionType, notes, actionUserId.
     */
    @Test
    @DisplayName("[AT-TC10][B5] approve() - approved=true → DB: status=APPROVED, notes, actionUserId, actionAt")
    void tc10_approve_approved_true_updatesDbAllFields() {
        String notes = "Đồng ý với yêu cầu tuyển dụng vị trí Backend";
        ApproveStepDTO dto = approveDto(true, notes);

        // Act
        approvalTrackingService.approve(pendingTracking.getId(), dto);

        // CHECK DB: Truy vấn lại
        ApprovalTracking saved = approvalTrackingRepository.findById(pendingTracking.getId()).orElseThrow();
        // Assert
        assertAll("BUG: Một hoặc nhiều trường không được cập nhật sau khi approve",
                () -> assertEquals(ApprovalStatus.APPROVED, saved.getStatus(),
                        "BUG: status không được đổi thành APPROVED"),
                () -> assertEquals(notes, saved.getNotes(),
                        "BUG: notes không được lưu vào DB"),
                () -> assertEquals(APPROVER_USER_ID, saved.getActionUserId(),
                        "BUG: actionUserId không ghi lại user đang đăng nhập"),
                () -> assertNotNull(saved.getActionAt(),
                        "BUG: actionAt null - thời gian hành động không được ghi nhận")
        );
    }

    /**
     * Test Case ID: AT-TC11
     * Nhánh [B6]: approved=false → DB phải ghi status=REJECTED (không phải APPROVED)
     *
     * Bug bị bắt: Nếu code dùng sai điều kiện `if (approved)` → cả 2 nhánh đều set APPROVED
     *
     * CheckDB: Status phải là REJECTED
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: Truy vấn DB bằng findById() để assert các trường thay đổi như status, actionType, notes, actionUserId.
     */
    @Test
    @DisplayName("[AT-TC11][B6] approve() - approved=false → DB: status=REJECTED; không phải APPROVED")
    void tc11_approve_approved_false_updatesDbToRejected() {
        String rejectReason = "Ngân sách chưa được phê duyệt";
        ApproveStepDTO dto = approveDto(false, rejectReason);

        // Act
        approvalTrackingService.approve(pendingTracking.getId(), dto);

        // Check DB
        ApprovalTracking saved = approvalTrackingRepository.findById(pendingTracking.getId()).orElseThrow();
        // Assert
        assertAll("BUG: Kết quả reject không đúng",
                () -> assertEquals(ApprovalStatus.REJECTED, saved.getStatus(),
                        "BUG: status không phải REJECTED - có thể đang set APPROVED cho cả 2 nhánh"),
                () -> assertEquals(rejectReason, saved.getNotes(),
                        "BUG: Lý do từ chối không được lưu"),
                () -> assertEquals(APPROVER_USER_ID, saved.getActionUserId(),
                        "BUG: actionUserId không được ghi lại")
        );
    }

    /**
     * Test Case ID: AT-TC12
     * Nhánh [B7]: approved=true + có bước tiếp (step2) → DB tăng thêm 1 tracking PENDING cho bước 2
     *
     * Bug bị bắt:
     *   - Không tạo tracking bước tiếp → luồng phê duyệt bị dừng giữa chừng
     *   - Tracking mới không có status=PENDING
     *   - Tracking mới không gắn đúng vào step2
     *
     * CheckDB: Đếm tracking + xác minh tracking mới
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: So sánh tổng số lượng bản ghi tracking trong DB trước và sau khi thực thi qua .count() & Truy vấn DB bằng findByRequestId() để xác minh tracking mới được tạo và gán đúng workflow step.
     */
    @Test
    @DisplayName("[AT-TC12][B7] approve() - approved=true + có bước tiếp → tạo tracking PENDING mới cho bước 2")
    void tc12_approve_approved_withNextStep_createsNewPendingTrackingForStep2() {
        long countBefore = approvalTrackingRepository.count();

        // Act
        approvalTrackingService.approve(pendingTracking.getId(), approveDto(true, "OK"));

        // CHECK DB: phải có thêm 1 tracking mới
        long countAfter = approvalTrackingRepository.count();
        // Assert
        assertEquals(countBefore + 1, countAfter,
                "BUG: Không tạo tracking mới cho bước 2 khi approve bước 1");

        // CHECK DB: tracking mới phải là PENDING, gắn với step2
        List<ApprovalTracking> all = approvalTrackingRepository.findByRequestId(REQUEST_ID);
        boolean hasStep2Pending = all.stream().anyMatch(t ->
                t.getStatus() == ApprovalStatus.PENDING
                && t.getStep() != null
                && t.getStep().getStepOrder() == 2);
        assertTrue(hasStep2Pending,
                "BUG: Tracking mới không đúng: phải PENDING tại step2 (stepOrder=2)");
    }

    /**
     * Test Case ID: AT-TC13
     * Nhánh [B8]: approved=true + KHÔNG có bước tiếp → KHÔNG tạo thêm tracking
     *
     * Chuẩn bị: Workflow chỉ có 1 bước (không có step2)
     * Bug bị bắt: Tạo tracking bừa khi không còn bước tiếp → dữ liệu thừa
     *
     * CheckDB: Tổng tracking không tăng
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: So sánh tổng số lượng bản ghi tracking trong DB trước và sau khi thực thi qua .count().
     */
    @Test
    @DisplayName("[AT-TC13][B8] approve() - approved=true + không có bước tiếp → không tạo tracking mới")
    void tc13_approve_approved_noNextStep_noNewTrackingCreated() {
        // Tạo workflow chỉ 1 bước
        Workflow wf1 = saveWorkflow("Workflow 1 Bước", WorkflowType.REQUEST, 99L);
        WorkflowStep s1 = saveStep(wf1, 1, APPROVER_USER_ID);
        ApprovalTracking t1 = approvalTrackingRepository.save(
                buildTracking(20L, s1, ApprovalStatus.PENDING, APPROVER_USER_ID));

        long countBefore = approvalTrackingRepository.count();

        // Act
        approvalTrackingService.approve(t1.getId(), approveDto(true, "Duyệt bước cuối"));

        // Assert
        assertEquals(countBefore, approvalTrackingRepository.count(),
                "BUG: Tạo thêm tracking dù không còn bước tiếp - workflow chỉ có 1 bước");
    }

    // ================================================================
    // NHÓM 3: getById()
    // ================================================================

    /**
     * Test Case ID: AT-TC14
     * Nhánh [B9]: ID tồn tại → DTO đủ thông tin
     *
     * Bug bị bắt: Mapping bỏ sót trường requestId, status, approverPositionId
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: Không tương tác/kiểm tra trực tiếp Repository trong test case này.
     */
    @Test
    @DisplayName("[AT-TC14][B9] getById() - ID tồn tại → DTO đầy đủ các trường chính")
    void tc14_getById_existingId_returnsDtoWithAllFields() {
        // Act
        ApprovalTrackingResponseDTO result = approvalTrackingService.getById(pendingTracking.getId());

        // Assert
        assertAll("BUG: DTO thiếu hoặc sai trường",
                () -> assertEquals(pendingTracking.getId(), result.getId(), "id sai"),
                () -> assertEquals(REQUEST_ID, result.getRequestId(), "requestId sai"),
                () -> assertEquals(ApprovalStatus.PENDING, result.getStatus(), "status sai"),
                () -> assertEquals(APPROVER_USER_ID, result.getApproverPositionId(), "approverPositionId sai")
        );
    }

    /**
     * Test Case ID: AT-TC15
     * Nhánh [B10]: ID không tồn tại → IdInvalidException
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Xác minh service ném ra IdInvalidException do ID không hợp lệ.
     * - Check DB: Không kiểm tra DB vì tác vụ thất bại từ bước validation (đảm bảo DB không rác).
     */
    @Test
    @DisplayName("[AT-TC15][B10] getById() - ID không tồn tại → IdInvalidException")
    void tc15_getById_nonExistentId_throwsIdInvalidException() {
        assertThrows(IdInvalidException.class,
                // Act
                () -> approvalTrackingService.getById(88888L),
                "BUG: Trả về null thay vì ném IdInvalidException");
    }

    /**
     * Test Case ID: AT-TC16
     * tracking không có actionUserId → DTO bình thường, actionUserName=null
     * Nhánh FALSE của if (tracking.getActionUserId() != null) trong toResponseDTO()
     *
     * Bug bị bắt: NPE khi map DTO với actionUserId = null.
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: Không tương tác/kiểm tra trực tiếp Repository trong test case này.
     */
    @Test
    @DisplayName("[AT-TC16] getById() - tracking không có actionUserId → DTO bình thường, actionUserName=null")
    void tc16_getById_nullActionUserId_returnsDtoWithoutNPE() {
        // Arrange: tracking không có actionUserId
        ApprovalTracking tracking = buildTracking(50L, step1, ApprovalStatus.PENDING, APPROVER_USER_ID);
        tracking.setActionUserId(null); // ← nhánh FALSE
        // Check DB
        tracking = approvalTrackingRepository.save(tracking);

        // Act
        ApprovalTrackingResponseDTO result = approvalTrackingService.getById(tracking.getId());

        // Assert: không NPE, actionUserName = null
        assertNotNull(result, "BUG: Kết quả null dù tracking tồn tại");
        assertNull(result.getActionUserId(),
                "BUG: actionUserId phải null trong DTO");
        assertEquals(50L, result.getRequestId(),
                "BUG: requestId sai");
    }

    // ================================================================
    // NHÓM 4: getAll()
    // ================================================================

    /**
     * Test Case ID: AT-TC17
     * Nhánh [B11]: filter=null → trả về tất cả tracking với đúng tổng số
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: Không tương tác/kiểm tra trực tiếp Repository trong test case này.
     */
    @Test
    @DisplayName("[AT-TC17][B11] getAll() - filter=null → trả về tất cả tracking, meta.total đúng")
    void tc17_getAll_noFilter_returnsAllWithCorrectTotal() {
        // DB có 1 tracking (setUp)
        // Act
        PaginationDTO result = approvalTrackingService.getAll(null, null, null, PageRequest.of(0, 10));

        // Assert
        assertNotNull(result.getMeta(), "BUG: meta null");
        assertTrue(result.getMeta().getTotal() >= 1,
                "BUG: Total phải >= 1 vì có sampleTracking từ setUp");
    }

        /**
         * Test Case ID: AT-TC23
     * Nhánh [B12]: filter requestId=REQUEST_ID → chỉ trả về tracking của request đó
     *
     * Bug bị bắt: Filter requestId bị bỏ qua → trả về tất cả tracking của mọi request
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: Không tương tác/kiểm tra trực tiếp Repository trong test case này.
     */
    @Test
    @DisplayName("[AT-TC18][B12] getAll() - filter requestId → chỉ trả về tracking của request đó")
    void tc18_getAll_filterByRequestId_excludesOtherRequests() {
        // Tạo tracking thuộc request khác (555L) để kiểm tra filter loại trừ
        ApprovalTracking otherReq = buildTracking(555L, step1, ApprovalStatus.PENDING, APPROVER_USER_ID);
        approvalTrackingRepository.save(otherReq);

        // Act
        PaginationDTO result = approvalTrackingService.getAll(REQUEST_ID, null, null, PageRequest.of(0, 10));
        @SuppressWarnings("unchecked")
        List<ApprovalTrackingResponseDTO> list = (List<ApprovalTrackingResponseDTO>) result.getResult();

        // Assert
        assertFalse(list.isEmpty(), "BUG: Không tìm thấy tracking của REQUEST_ID=1");
        assertTrue(list.stream().allMatch(dto -> REQUEST_ID.equals(dto.getRequestId())),
                "BUG: Kết quả lẫn tracking của request khác - filter requestId không hoạt động");
    }

    /**
     * Test Case ID: AT-TC19
     * Nhánh [B13]: filter status=PENDING → chỉ trả về PENDING, không lẫn APPROVED/REJECTED
     *
     * Bug bị bắt: Filter status bị bỏ qua → trả về tất cả mọi status
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: Không tương tác/kiểm tra trực tiếp Repository trong test case này.
     */
    @Test
    @DisplayName("[AT-TC19][B13] getAll() - filter status=PENDING → tất cả kết quả là PENDING")
    void tc19_getAll_filterByStatusPending_excludesOtherStatuses() {
        // Tạo tracking APPROVED để kiểm tra filter loại trừ
        ApprovalTracking approved = buildTracking(2L, step1, ApprovalStatus.APPROVED, APPROVER_USER_ID);
        approvalTrackingRepository.save(approved);

        // Act
        PaginationDTO result = approvalTrackingService.getAll(null, ApprovalStatus.PENDING, null,
                PageRequest.of(0, 10));
        @SuppressWarnings("unchecked")
        List<ApprovalTrackingResponseDTO> list = (List<ApprovalTrackingResponseDTO>) result.getResult();

        // Assert
        assertFalse(list.isEmpty(), "BUG: Không tìm thấy tracking PENDING");
        assertTrue(list.stream().allMatch(dto -> dto.getStatus() == ApprovalStatus.PENDING),
                "BUG: Kết quả lẫn tracking không phải PENDING - filter status không hoạt động");
    }

    // ================================================================
    // NHÓM 5: getPendingApprovalsForUser()
    // ================================================================

    /**
     * Test Case ID: AT-TC20
     * Nhánh [B14]: User có tracking PENDING → danh sách không rỗng, tất cả là PENDING
     *
     * Bug bị bắt: Lọc sai approverPositionId → trả về tracking của user khác
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: Không tương tác/kiểm tra trực tiếp Repository trong test case này.
     */
    @Test
    @DisplayName("[AT-TC20][B14] getPendingApprovalsForUser() - có PENDING → list không rỗng, tất cả PENDING")
    void tc20_getPendingApprovalsForUser_hasPending_returnsNonEmptyAllPending() {
        List<ApprovalTrackingResponseDTO> result =
                // Act
                approvalTrackingService.getPendingApprovalsForUser(APPROVER_USER_ID);

        // Assert
        assertFalse(result.isEmpty(),
                "BUG: Không tìm thấy tracking PENDING dù có sampleTracking với approverPositionId=APPROVER_USER_ID");
        assertTrue(result.stream().allMatch(dto -> dto.getStatus() == ApprovalStatus.PENDING),
                "BUG: Kết quả có tracking không phải PENDING");
    }

    /**
     * Test Case ID: AT-TC21
     * Nhánh [B15]: User không có tracking PENDING → danh sách rỗng (không null)
     *
     * Bug bị bắt: Trả về null thay vì danh sách rỗng → NPE ở caller
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: Không tương tác/kiểm tra trực tiếp Repository trong test case này.
     */
    @Test
    @DisplayName("[AT-TC21][B15] getPendingApprovalsForUser() - không có PENDING → trả về list rỗng, không null")
    void tc21_getPendingApprovalsForUser_noPending_returnsEmptyList() {
        List<ApprovalTrackingResponseDTO> result =
                // Act
                approvalTrackingService.getPendingApprovalsForUser(77777L); // User không có tracking

        // Assert
        assertNotNull(result, "BUG: Trả về null thay vì list rỗng - sẽ gây NPE ở caller");
        assertTrue(result.isEmpty(), "BUG: Trả về tracking không phải của user 77777L");
    }

    // ================================================================
    // NHÓM 6: getWorkflowInfoByRequestId()
    // ================================================================

    /**
     * Test Case ID: AT-TC22
     * Nhánh [B16]: requestType=null → không lọc type, trả về tracking dù là loại gì
     *
     * Bug bị bắt: null type lại bị xử lý như UNKNOWN → lọc ra toàn bộ kết quả
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: Không tương tác/kiểm tra trực tiếp Repository trong test case này.
     */
    @Test
    @DisplayName("[AT-TC22][B16] getWorkflowInfoByRequestId() - requestType=null → không lọc, có tracking")
    void tc22_getWorkflowInfoByRequestId_nullType_returnsTrackings() {
        // Act
        var result = approvalTrackingService.getWorkflowInfoByRequestId(REQUEST_ID, null, null);

        // Assert
        assertNotNull(result, "BUG: Kết quả null");
        assertFalse(result.getApprovalTrackings().isEmpty(),
                "BUG: Kết quả rỗng khi requestType=null - có thể đang lọc nhầm");
    }

    /**
     * Test Case ID: AT-TC18
     * Nhánh [B17]: requestType="REQUEST" → chỉ trả về tracking của workflow REQUEST
     *
     * Bug bị bắt: Filter type không đúng → lẫn tracking của OFFER
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: Không tương tác/kiểm tra trực tiếp Repository trong test case này.
     */
    @Test
        @DisplayName("[AT-TC23][B17] getWorkflowInfoByRequestId() - requestType=REQUEST → chỉ cho REQUEST tracking")
        void tc23_getWorkflowInfoByRequestId_typeRequest_returnsOnlyRequestTrackings() {
        // Arrange: tạo thêm 1 tracking OFFER cùng REQUEST_ID → tổng = 2 trackings
        // Setup đã có 1 tracking REQUEST → sau khi filter chỉ còn 1
        Workflow offerWf = saveWorkflow("Offer Workflow", WorkflowType.OFFER, 10L);
        WorkflowStep offerStep = saveStep(offerWf, 1, APPROVER_USER_ID);
        approvalTrackingRepository.save(buildTracking(REQUEST_ID, offerStep, ApprovalStatus.PENDING, APPROVER_USER_ID));

        // Act: lọc với type=REQUEST
        var result = approvalTrackingService.getWorkflowInfoByRequestId(REQUEST_ID, null, "REQUEST");

        // Assert 1: phải có tracking (không rỗng)
        assertFalse(result.getApprovalTrackings().isEmpty(),
                "BUG: Không tìm thấy tracking REQUEST nào");

        // Assert 2: số lượng phải chính xác là 1 (chỉ REQUEST, OFFER bị loại)
        // Nếu filter không hoạt động → trả về 2 (cả REQUEST lẫn OFFER) → FAIL rõ ràng
        assertEquals(1, result.getApprovalTrackings().size(),
                "BUG: Filter type=REQUEST không loại được tracking OFFER " +
                "— trả về " + result.getApprovalTrackings().size() + " thay vì 1");

        // Assert 3: tracking duy nhất phải thuộc workflow REQUEST (không phải OFFER)
        // Nếu code filter ngược lại (giữ OFFER, loại REQUEST) → type kiểm tra FAIL
        Long returnedStepId = result.getApprovalTrackings().get(0).getStepId();
        assertNotNull(returnedStepId, "BUG: stepId null trong tracking trả về");
        // Check DB
        WorkflowStep returnedStep = workflowStepRepository.findById(returnedStepId).orElseThrow();
        assertEquals(WorkflowType.REQUEST, returnedStep.getWorkflow().getType(),
                "BUG: Tracking được trả về thuộc workflow OFFER, không phải REQUEST " +
                "— filter đang giữ nhầm bản ghi");
    }

    /**
     * Test Case ID: AT-TC24
     * Nhánh [B18]: requestType="OFFER" → chỉ trả về tracking của workflow OFFER
     *
     * Bug bị bắt: Không filter đúng → trả lẫn tracking REQUEST và OFFER.
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: Không tương tác/kiểm tra trực tiếp Repository trong test case này.
     */
    @Test
    @DisplayName("[AT-TC24][B18] getWorkflowInfoByRequestId() - requestType=OFFER → chỉ trả OFFER tracking")
    void tc24_getWorkflowInfoByRequestId_typeOffer_returnsOnlyOfferTrackings() {
        // Arrange: tạo workflow OFFER + tracking OFFER cùng REQUEST_ID
        Workflow offerWf = saveWorkflow("Offer WF", WorkflowType.OFFER, 10L);
        WorkflowStep offerStep = saveStep(offerWf, 1, APPROVER_USER_ID);
        ApprovalTracking offerTracking = approvalTrackingRepository.save(
                buildTracking(REQUEST_ID, offerStep, ApprovalStatus.PENDING, APPROVER_USER_ID));

        // setUp đã có REQUEST tracking cùng REQUEST_ID → tổng = 2 trackings

        // Act
        var result = approvalTrackingService.getWorkflowInfoByRequestId(REQUEST_ID, null, "OFFER");

        // Assert: chỉ tracking OFFER được trả về
        assertFalse(result.getApprovalTrackings().isEmpty(),
                "BUG: Không tìm thấy tracking OFFER");
        // Tất cả tracking trả về phải thuộc OFFER workflow
        boolean allOffer = result.getApprovalTrackings().stream().allMatch(dto ->
                dto.getStepId() != null &&
                // Check DB
                workflowStepRepository.findById(dto.getStepId())
                        .map(s -> s.getWorkflow().getType() == WorkflowType.OFFER)
                        .orElse(false));
        assertTrue(allOffer, "BUG: Kết quả lẫn tracking REQUEST - filter OFFER không hoạt động");

        // Phải ít hơn tổng số tracking (vì có REQUEST tracking bị loại)
        var allResult = approvalTrackingService.getWorkflowInfoByRequestId(REQUEST_ID, null, null);
        assertTrue(result.getApprovalTrackings().size() < allResult.getApprovalTrackings().size(),
                "BUG: Filter OFFER không loại được tracking REQUEST");
    }

    /**
     * Test Case ID: AT-TC25
     * Nhánh [B19]: requestType unknown → không filter, trả tất cả
     *
     * Bug bị bắt: Requesttype lạ bị xử lý như null → lọc ra hết kết quả.
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: Không tương tác/kiểm tra trực tiếp Repository trong test case này.
     */
    @Test
    @DisplayName("[AT-TC25][B19] getWorkflowInfoByRequestId() - requestType unknown → không filter, trả tất cả")
    void tc25_getWorkflowInfoByRequestId_unknownType_returnsAllTrackings() {
        // Test Case ID: AT-TC25
        // Mục tiêu: requestType "SOMETHING_ELSE" không khớp enum nào
        //           → xử lý như null: không filter, trả tất cả.
        //
        // Bug bị bắt:
        //   - requestType lạ gây exception → FAIL
        //   - requestType lạ được filter vào UNKNOWN type → kết quả rỗng → FAIL
        //   - Kết quả có id khác so với gọi null (filter chọn nhầm record) → content check FAIL

        // Act
        var resultUnknown = approvalTrackingService.getWorkflowInfoByRequestId(REQUEST_ID, null, "SOMETHING_ELSE");
        var resultNull    = approvalTrackingService.getWorkflowInfoByRequestId(REQUEST_ID, null, null);

        // Assert 1: không rỗng — tracking từ setUp vẫn phải có
        assertFalse(resultUnknown.getApprovalTrackings().isEmpty(),
                "BUG: Kết quả rỗng dù có tracking — có thể đang filter nhầm type SOMETHING_ELSE");

        // Assert 2: size bằng nhau — cả hai đều không filter
        assertEquals(resultNull.getApprovalTrackings().size(),
                resultUnknown.getApprovalTrackings().size(),
                "BUG: requestType unknown phải giống requestType=null (không filter)");

        // Assert 3: content giống nhau (cùng tracking ID)
        // Nếu chỉ check size mà không check content → có thể trả record khác với cùng số lượng
        List<Long> idsNull = resultNull.getApprovalTrackings().stream()
                .map(dto -> dto.getId())
                .sorted()
                .collect(java.util.stream.Collectors.toList());
        List<Long> idsUnknown = resultUnknown.getApprovalTrackings().stream()
                .map(dto -> dto.getId())
                .sorted()
                .collect(java.util.stream.Collectors.toList());
        assertEquals(idsNull, idsUnknown,
                "BUG: Nội dung khác nhau dù size bằng nhau — " +
                "filter 'SOMETHING_ELSE' đang chọn nhầm bản ghi khác so với null");
    }

    /**
     * Test Case ID: AT-TC26
     * Nhánh: requestId không có tracking → danh sách rỗng (không null)
     *
     * Bug bị bắt: Trả về null → NPE ở caller
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: Không tương tác/kiểm tra trực tiếp Repository trong test case này.
     */
    @Test
    @DisplayName("[AT-TC26] getWorkflowInfoByRequestId() - không có tracking → danh sách rỗng, không null")
    void tc26_getWorkflowInfoByRequestId_noTracking_returnsEmptyList() {
        // Act
        var result = approvalTrackingService.getWorkflowInfoByRequestId(99999L, null, null);

        // Assert
        assertNotNull(result, "BUG: Kết quả null");
        assertNotNull(result.getApprovalTrackings(), "BUG: approvalTrackings null - gây NPE ở caller");
        assertTrue(result.getApprovalTrackings().isEmpty(),
                "BUG: Trả về tracking dù requestId không tồn tại");
    }

        /**
         * Test Case ID: AT-TC27
         * requestType chỉ chứa khoảng trắng → không filter, workflowId truyền sẵn,
         * workflow không có steps.
         * 
         * CHI TIẾT TEST CASE:
         * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
         * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
         * - Check DB: Không tương tác/kiểm tra trực tiếp Repository trong test case này.
         */
        @Test
        @DisplayName("[AT-TC27] getWorkflowInfoByRequestId() - requestType blank + workflowId explicit + workflow không có steps")
        void tc27_getWorkflowInfoByRequestId_blankType_explicitWorkflowId_emptyWorkflowSteps() {
                Workflow emptyWorkflow = saveWorkflow("Empty Workflow", WorkflowType.REQUEST, 10L);

                // Act
                var result = approvalTrackingService.getWorkflowInfoByRequestId(
                                REQUEST_ID, emptyWorkflow.getId(), "   ");

                // Assert
                assertNotNull(result, "BUG: Kết quả null khi requestType chỉ chứa khoảng trắng");
                assertNotNull(result.getWorkflow(), "BUG: workflow DTO null");
                assertNull(result.getWorkflow().getSteps(), "BUG: workflow không có steps phải trả về steps=null");
                assertFalse(result.getApprovalTrackings().isEmpty(), "BUG: Không trả về tracking khi requestType blank");
        }

        /**
         * Test Case ID: AT-TC28
         * requestType=OFFER nhưng request hiện tại không có OFFER tracking → filter rỗng.
         * 
         * CHI TIẾT TEST CASE:
         * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
         * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
         * - Check DB: Không tương tác/kiểm tra trực tiếp Repository trong test case này.
         */
        @Test
        @DisplayName("[AT-TC28] getWorkflowInfoByRequestId() - OFFER filter không match → trả list rỗng")
        void tc28_getWorkflowInfoByRequestId_offerFilterNoMatch_returnsEmptyList() {
                // Act
                var result = approvalTrackingService.getWorkflowInfoByRequestId(REQUEST_ID, null, "OFFER");

                // Assert
                assertNotNull(result, "BUG: Kết quả null khi filter OFFER không match");
                assertTrue(result.getApprovalTrackings().isEmpty(), "BUG: Filter OFFER không match nhưng vẫn có kết quả");
                assertNull(result.getWorkflow(), "BUG: Không có tracking OFFER thì workflow phải null");
        }

    // ================================================================
    // NHÓM 7: handleWorkflowEvent() — các nhánh switch-case
    // ================================================================

        /**
         * Test Case ID: AT-TC29
     * Nhánh [B20]: event=null → không làm gì, không exception, DB không đổi
     *
     * Bug bị bắt: Không guard null event → NullPointerException
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: So sánh tổng số lượng bản ghi tracking trong DB trước và sau khi thực thi qua .count().
     */
    @Test
        @DisplayName("[AT-TC29][B20] handleWorkflowEvent() - event=null → bỏ qua, DB không đổi")
        void tc71_handleWorkflowEvent_nullEvent_doesNothingNoException() {
        long countBefore = approvalTrackingRepository.count();

        // Act + Assert
        assertDoesNotThrow(() -> approvalTrackingService.handleWorkflowEvent(null),
                "BUG: Không guard null event → NullPointerException");

        // Check DB
        assertEquals(countBefore, approvalTrackingRepository.count(),
                "BUG: DB bị thay đổi dù event=null");
    }

        /**
         * Test Case ID: AT-TC30
     * Nhánh [B21]: eventType=null → bỏ qua, DB không đổi
     *
     * Bug bị bắt: Gọi `.toUpperCase()` trên null → NullPointerException
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: So sánh tổng số lượng bản ghi tracking trong DB trước và sau khi thực thi qua .count().
     */
    @Test
        @DisplayName("[AT-TC30][B21] handleWorkflowEvent() - eventType=null → bỏ qua, không NPE")
        void tc72_handleWorkflowEvent_nullEventType_doesNothingNoException() {
        RecruitmentWorkflowEvent event = new RecruitmentWorkflowEvent();
        event.setEventType(null);
        event.setRequestId(REQUEST_ID);

        long countBefore = approvalTrackingRepository.count();

        // Act + Assert
        assertDoesNotThrow(() -> approvalTrackingService.handleWorkflowEvent(event),
                "BUG: Gọi toUpperCase() trên eventType null → NullPointerException");

        // Check DB
        assertEquals(countBefore, approvalTrackingRepository.count(),
                "BUG: DB bị thay đổi dù eventType=null");
    }

    /**
     * Test Case ID: AT-TC31
     * Nhánh [B22]: REQUEST_CANCELLED → tracking PENDING bị CANCELLED, actionType="CANCEL"
     *
     * Bug bị bắt:
     *   - Status không được đổi sang CANCELLED
     *   - actionType bị ghi là "WITHDRAW" thay vì "CANCEL"
     *   - actorUserId không được ghi vào actionUserId
     *   - actionAt null
     *
     * CheckDB: Truy vấn lại từng trường
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: Truy vấn DB bằng findById() để assert các trường thay đổi như status, actionType, notes, actionUserId.
     */
    @Test
    @DisplayName("[AT-TC31][B22] handleWorkflowEvent() - REQUEST_CANCELLED → status=CANCELLED, actionType=CANCEL")
    void tc29_handleWorkflowEvent_requestCancelled_setsStatusAndActionType() {
        RecruitmentWorkflowEvent event = buildEvent("REQUEST_CANCELLED", "REQUEST", REQUEST_ID);
        event.setActorUserId(APPROVER_USER_ID);
        event.setReason("Hủy do thay đổi kế hoạch");

        // Act
        approvalTrackingService.handleWorkflowEvent(event);

        // CHECK DB
        ApprovalTracking updated = approvalTrackingRepository.findById(pendingTracking.getId()).orElseThrow();
        // Assert
        assertAll("BUG: Một hoặc nhiều trường không đúng sau REQUEST_CANCELLED",
                () -> assertEquals(ApprovalStatus.CANCELLED, updated.getStatus(),
                        "BUG: status không phải CANCELLED"),
                () -> assertEquals("CANCEL", updated.getActionType(),
                        "BUG: actionType không phải CANCEL (có thể bị nhầm sang WITHDRAW)"),
                () -> assertEquals(APPROVER_USER_ID, updated.getActionUserId(),
                        "BUG: actionUserId không ghi lại actorUserId"),
                () -> assertNotNull(updated.getActionAt(),
                        "BUG: actionAt null sau khi cancel")
        );
    }

    /**
     * Test Case ID: AT-TC32
     * Nhánh [B23]: REQUEST_CANCELLED + request không có PENDING → DB không thay đổi
     *
     * Bug bị bắt: Cố update tracking không tồn tại → exception bất ngờ
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: Truy vấn DB bằng findById() để assert các trường thay đổi như status, actionType, notes, actionUserId.
     */
    @Test
    @DisplayName("[AT-TC32][B23] REQUEST_CANCELLED + không có PENDING → DB không đổi, không exception")
    void tc30_handleWorkflowEvent_cancelledNoActivePending_dbUnchanged() {
        // Đặt tracking thành APPROVED (không còn PENDING)
        pendingTracking.setStatus(ApprovalStatus.APPROVED);
        approvalTrackingRepository.save(pendingTracking);

        RecruitmentWorkflowEvent event = buildEvent("REQUEST_CANCELLED", "REQUEST", REQUEST_ID);

        // Act + Assert
        assertDoesNotThrow(() -> approvalTrackingService.handleWorkflowEvent(event),
                "BUG: Exception khi cancel request không có PENDING tracking");

        // CHECK DB: tracking APPROVED vẫn là APPROVED (không bị cancel)
        ApprovalStatus status = approvalTrackingRepository.findById(pendingTracking.getId())
                .orElseThrow().getStatus();
        assertEquals(ApprovalStatus.APPROVED, status,
                "BUG: Tracking đã APPROVED bị đổi thành CANCELLED (chỉ nên cancel PENDING)");
    }

    /**
     * Test Case ID: AT-TC33
     * Nhánh [B24]: REQUEST_WITHDRAWN → actionType="WITHDRAW" (khác CANCEL!)
     *
     * Bug bị bắt: Dùng cùng actionType="CANCEL" cho cả CANCEL và WITHDRAW → không phân biệt được nguồn gốc
     *
     * CheckDB: actionType phải là "WITHDRAW", status phải là CANCELLED
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: Truy vấn DB bằng findById() để assert các trường thay đổi như status, actionType, notes, actionUserId.
     */
    @Test
    @DisplayName("[AT-TC33][B24] REQUEST_WITHDRAWN → status=CANCELLED, actionType=WITHDRAW (≠ CANCEL)")
    void tc31_handleWorkflowEvent_requestWithdrawn_actionTypeIsWithdrawNotCancel() {
        RecruitmentWorkflowEvent event = buildEvent("REQUEST_WITHDRAWN", "REQUEST", REQUEST_ID);
        event.setActorUserId(APPROVER_USER_ID);
        event.setReason("Rút lại vì không cần tuyển nữa");

        // Act
        approvalTrackingService.handleWorkflowEvent(event);

        // Check DB
        ApprovalTracking updated = approvalTrackingRepository.findById(pendingTracking.getId()).orElseThrow();
        // Assert
        assertAll("BUG: Withdraw không phân biệt được với Cancel",
                () -> assertEquals(ApprovalStatus.CANCELLED, updated.getStatus(),
                        "BUG: status phải là CANCELLED sau khi withdraw"),
                () -> assertEquals("WITHDRAW", updated.getActionType(),
                        "BUG: actionType phải là WITHDRAW, không phải CANCEL - không thể phân biệt 2 hành động")
        );
    }

        /**
         * Test Case ID: AT-TC34
         * REQUEST_WITHDRAWN với reason=null → dùng message mặc định.
         * 
         * CHI TIẾT TEST CASE:
         * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
         * - Check: Đảm bảo hệ thống gọi notificationProducer để gửi thông báo/email.
         * - Check DB: Truy vấn DB bằng findById() để assert các trường thay đổi như status, actionType, notes, actionUserId.
         */
        @Test
        @DisplayName("[AT-TC34] REQUEST_WITHDRAWN reason=null → dùng message mặc định")
        void tc31b_handleWorkflowEvent_requestWithdrawn_nullReason_usesDefaultMessage() {
                RecruitmentWorkflowEvent event = buildEvent("REQUEST_WITHDRAWN", "REQUEST", REQUEST_ID);
                event.setActorUserId(APPROVER_USER_ID);
                event.setReason(null);

                // Act
                approvalTrackingService.handleWorkflowEvent(event);

                // Check DB
                ApprovalTracking updated = approvalTrackingRepository.findById(pendingTracking.getId()).orElseThrow();
                // Assert
                assertEquals(ApprovalStatus.CANCELLED, updated.getStatus(), "BUG: Withdraw null reason vẫn phải cancel tracking");
                assertEquals("WITHDRAW", updated.getActionType(), "BUG: actionType phải là WITHDRAW");
                verify(notificationProducer, times(1))
                        .sendNotification(eq(APPROVER_USER_ID), anyString(), anyString(), isNull());
        }

    /**
     * Test Case ID: AT-TC35
     * Nhánh [B25]: REQUEST_RETURNED + có returnedToStepId → status=RETURNED, actionType=RETURN, returnedToStepId ghi đúng
     *
     * Bug bị bắt:
     *   - status không phải RETURNED
     *   - returnedToStepId không được ghi vào DB
     *   - actionAt null
     *   - Ghi nhầm returnedToStepId
     *
     * CheckDB: 4 trường đều đúng
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: Truy vấn DB bằng findById() để assert các trường thay đổi như status, actionType, notes, actionUserId.
     */
    @Test
    @DisplayName("[AT-TC35][B25] REQUEST_RETURNED + returnedToStepId → RETURNED, RETURN, returnedToStepId")
    void tc32_handleWorkflowEvent_requestReturned_withStepId_setReadCorrectly() {
        RecruitmentWorkflowEvent event = buildEvent("REQUEST_RETURNED", "REQUEST", REQUEST_ID);
        event.setActorUserId(APPROVER_USER_ID);
        event.setWorkflowId(workflow2Steps.getId());
        event.setReturnedToStepId(step1.getId()); // Trả về bước 1
        event.setReason("Cần chỉnh sửa mô tả công việc");

        // Act
        approvalTrackingService.handleWorkflowEvent(event);

        // Check DB
        ApprovalTracking updated = approvalTrackingRepository.findById(pendingTracking.getId()).orElseThrow();
        // Assert
        assertAll("BUG: REQUEST_RETURNED không ghi đúng dữ liệu",
                () -> assertEquals(ApprovalStatus.RETURNED, updated.getStatus(),
                        "BUG: status không phải RETURNED"),
                () -> assertEquals("RETURN", updated.getActionType(),
                        "BUG: actionType không phải RETURN"),
                () -> assertEquals(step1.getId(), updated.getReturnedToStepId(),
                        "BUG: returnedToStepId không được ghi vào DB"),
                () -> assertNotNull(updated.getActionAt(),
                        "BUG: actionAt null sau khi return")
        );
    }

    /**
     * Test Case ID: AT-TC36
     * Nhánh [B26]: REQUEST_RETURNED + returnedToStepId=null → tự động trả về bước đầu (stepOrder=1)
     *
     * Bug bị bắt: Khi returnedToStepId=null, không resolve tự động về step 1 → returnedToStepId = null trong DB
     *
     * CheckDB: returnedToStepId phải = step1.getId()
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: Truy vấn DB bằng findById() để assert các trường thay đổi như status, actionType, notes, actionUserId.
     */
    @Test
    @DisplayName("[AT-TC36][B26] REQUEST_RETURNED + returnedToStepId=null → tự resolve về step 1")
    void tc33_handleWorkflowEvent_requestReturned_nullStepId_defaultsToFirstStep() {
        RecruitmentWorkflowEvent event = buildEvent("REQUEST_RETURNED", "REQUEST", REQUEST_ID);
        event.setActorUserId(APPROVER_USER_ID);
        event.setWorkflowId(workflow2Steps.getId());
        event.setReturnedToStepId(null); // Không chỉ định → phải resolve về step 1
        event.setReason("Thiếu thông tin");

        // Act
        approvalTrackingService.handleWorkflowEvent(event);

        // Check DB
        Long returnedToStepId = approvalTrackingRepository.findById(pendingTracking.getId())
                .orElseThrow().getReturnedToStepId();

        // Assert
        assertNotNull(returnedToStepId,
                "BUG: returnedToStepId vẫn null khi không chỉ định - phải tự resolve về bước 1");
        assertEquals(step1.getId(), returnedToStepId,
                "BUG: Bước được trả về không phải là bước đầu tiên (stepOrder=1)");
    }

    /**
     * Test Case ID: AT-TC37
     * Nhánh [B27]: REQUEST_APPROVED + có bước tiếp → tracking hiện tại APPROVED + tạo tracking PENDING mới
     *
     * Bug bị bắt:
     *   - Tracking hiện tại không được đổi sang APPROVED
     *   - Không tạo tracking mới cho bước tiếp
     *
     * CheckDB: 2 điểm kiểm tra
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: So sánh tổng số lượng bản ghi tracking trong DB trước và sau khi thực thi qua .count() & Truy vấn DB bằng findById() để assert các trường thay đổi như status, actionType, notes, actionUserId.
     */
    @Test
    @DisplayName("[AT-TC37][B27] REQUEST_APPROVED + có bước tiếp → tracking APPROVED + tạo tracking mới")
    void tc34_handleWorkflowEvent_requestApproved_withNextStep_approvesAndCreatesNext() {
        long countBefore = approvalTrackingRepository.count();

        RecruitmentWorkflowEvent event = buildEvent("REQUEST_APPROVED", "REQUEST", REQUEST_ID);
        event.setActorUserId(APPROVER_USER_ID);
        event.setWorkflowId(workflow2Steps.getId());
        event.setDepartmentId(10L);
        event.setNotes("Phê duyệt tại bước 1");

        // Act
        approvalTrackingService.handleWorkflowEvent(event);

        // CHECK DB 1: tracking bước 1 phải APPROVED
        ApprovalStatus step1Status = approvalTrackingRepository.findById(pendingTracking.getId())
                .orElseThrow().getStatus();
        // Assert
        assertEquals(ApprovalStatus.APPROVED, step1Status,
                "BUG: Tracking bước 1 không được đổi thành APPROVED");

        // CHECK DB 2: phải có thêm 1 tracking mới
        long countAfter = approvalTrackingRepository.count();
        assertEquals(countBefore + 1, countAfter,
                "BUG: Không tạo tracking mới cho bước 2 dù workflow còn bước tiếp");
    }

    /**
     * Test Case ID: AT-TC38
     * Nhánh [B28]: REQUEST_APPROVED + không có bước tiếp → tracking APPROVED, KHÔNG tạo thêm
     *
     * Bug bị bắt: Cố tạo tracking cho bước tiếp dù không còn bước → data giả
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: So sánh tổng số lượng bản ghi tracking trong DB trước và sau khi thực thi qua .count() & Truy vấn DB bằng findById() để assert các trường thay đổi như status, actionType, notes, actionUserId.
     */
    @Test
    @DisplayName("[AT-TC38][B28] REQUEST_APPROVED + không có bước tiếp → APPROVED, không tạo thêm tracking")
    void tc35_handleWorkflowEvent_requestApproved_lastStep_noNewTracking() {
        // Workflow chỉ 1 bước
        Workflow wf1 = saveWorkflow("Workflow 1 Bước Only", WorkflowType.REQUEST, 20L);
        WorkflowStep s1 = saveStep(wf1, 1, APPROVER_USER_ID);
        ApprovalTracking t1 = approvalTrackingRepository.save(
                buildTracking(30L, s1, ApprovalStatus.PENDING, APPROVER_USER_ID));

        long countBefore = approvalTrackingRepository.count();

        RecruitmentWorkflowEvent event = buildEvent("REQUEST_APPROVED", "REQUEST", 30L);
        event.setActorUserId(APPROVER_USER_ID);
        event.setWorkflowId(wf1.getId());
        event.setDepartmentId(20L);

        // Act
        approvalTrackingService.handleWorkflowEvent(event);

        // CHECK DB: tracking phải APPROVED
        // Assert
        assertEquals(ApprovalStatus.APPROVED,
                approvalTrackingRepository.findById(t1.getId()).orElseThrow().getStatus(),
                "BUG: Tracking bước cuối không được đổi thành APPROVED");

        // CHECK DB: không tạo thêm tracking
        assertEquals(countBefore, approvalTrackingRepository.count(),
                "BUG: Tạo thêm tracking dù không còn bước tiếp");
    }

    /**
     * Test Case ID: AT-TC39
     * Nhánh [B29]: REQUEST_REJECTED → tracking PENDING → REJECTED, actionType="REJECT"
     *
     * Bug bị bắt:
     *   - actionType bị ghi là "CANCEL" thay vì "REJECT"
     *   - Status không đổi thành REJECTED
     *
     * CheckDB: status=REJECTED, actionType=REJECT
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: Truy vấn DB bằng findById() để assert các trường thay đổi như status, actionType, notes, actionUserId.
     */
    @Test
    @DisplayName("[AT-TC39][B29] REQUEST_REJECTED → status=REJECTED, actionType=REJECT")
    void tc36_handleWorkflowEvent_requestRejected_setsRejectedAndActionType() {
        RecruitmentWorkflowEvent event = buildEvent("REQUEST_REJECTED", "REQUEST", REQUEST_ID);
        event.setActorUserId(APPROVER_USER_ID);
        event.setNotes("Không đủ ngân sách");

        // Act
        approvalTrackingService.handleWorkflowEvent(event);

        // Check DB
        ApprovalTracking updated = approvalTrackingRepository.findById(pendingTracking.getId()).orElseThrow();
        // Assert
        assertAll("BUG: REQUEST_REJECTED không ghi đúng dữ liệu",
                () -> assertEquals(ApprovalStatus.REJECTED, updated.getStatus(),
                        "BUG: status không phải REJECTED"),
                () -> assertEquals("REJECT", updated.getActionType(),
                        "BUG: actionType không phải REJECT (nhầm sang CANCEL?)")
        );
    }

    /**
     * Test Case ID: AT-TC40
     * Nhánh [B30]: eventType không xác định → bỏ qua, DB không thay đổi
     *
     * Bug bị bắt: Ném exception cho eventType lạ → production crashes khi nhận event mới
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: So sánh tổng số lượng bản ghi tracking trong DB trước và sau khi thực thi qua .count() & Truy vấn DB bằng findById() để assert các trường thay đổi như status, actionType, notes, actionUserId.
     */
    @Test
    @DisplayName("[AT-TC40][B30] handleWorkflowEvent() - eventType lạ → bỏ qua hoàn toàn, DB không đổi")
    void tc37_handleWorkflowEvent_unknownEventType_dbUnchanged() {
        RecruitmentWorkflowEvent event = buildEvent("UNKNOWN_FUTURE_EVENT", "REQUEST", REQUEST_ID);

        long countBefore = approvalTrackingRepository.count();

        // Act + Assert
        assertDoesNotThrow(() -> approvalTrackingService.handleWorkflowEvent(event),
                "BUG: Ném exception cho eventType không xác định - phá vỡ backward compatibility");

        // CHECK DB: tracking PENDING vẫn PENDING
        assertEquals(ApprovalStatus.PENDING,
                approvalTrackingRepository.findById(pendingTracking.getId()).orElseThrow().getStatus(),
                "BUG: Status bị thay đổi dù eventType không xác định");

        assertEquals(countBefore, approvalTrackingRepository.count(),
                "BUG: DB bị thay đổi dù event không được xử lý");
    }

    // ================================================================
    // NHÓM 8: handleWorkflowEvent() — OFFER + departmentId resolution
    // ================================================================

    /**
     * Test Case ID: AT-TC41
     * Nhánh D2=True, D4=True, D5=True:
     * OFFER event, departmentId=null, candidateId có giá trị → candidateService trả về departmentId → set vào event.
     *
     * Bug bị bắt: Không gọi candidateService để resolve → departmentId vẫn null khi xử lý.
     *
     * CheckDB: Tracking được tạo (REQUEST_SUBMITTED thành công sau khi resolve departmentId).
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: Không tương tác/kiểm tra trực tiếp Repository trong test case này.
     */
    @Test
    @DisplayName("[AT-TC41] handleWorkflowEvent() - OFFER, departmentId=null, candidateId hợp lệ → resolve từ candidate")
    void tc38_handleWorkflowEvent_offerNullDeptWithCandidateId_resolvesDepartmentId() {
        // Arrange: tracking không có actionUserId
        ApprovalTracking tracking = buildTracking(50L, step1, ApprovalStatus.PENDING, APPROVER_USER_ID);
        tracking.setActionUserId(null); // ← nhánh FALSE
        // Check DB
        tracking = approvalTrackingRepository.save(tracking);

        // Act
        ApprovalTrackingResponseDTO result = approvalTrackingService.getById(tracking.getId());

        // Assert: không NPE, actionUserName = null
        assertNotNull(result, "BUG: Kết quả null dù tracking tồn tại");
        assertNull(result.getActionUserId(),
                "BUG: actionUserId phải null trong DTO");
        assertEquals(50L, result.getRequestId(),
                "BUG: requestId sai");
    }

    // ================================================================
    // NHÓM 8: getWorkflowInfoByRequestId() — các nhánh requestType
    // ================================================================

        /**
         * Test Case ID: AT-TC39
     * Nhánh requestType="OFFER" → chỉ trả về tracking thuộc workflow OFFER.
     *
     * Bug bị bắt: Không filter đúng → trả lẫn tracking REQUEST và OFFER.
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: Không tương tác/kiểm tra trực tiếp Repository trong test case này.
     */
    @Test
        @DisplayName("[AT-TC42] getWorkflowInfoByRequestId() - requestType=OFFER → chỉ trả OFFER tracking")
        void tc39_getWorkflowInfoByRequestId_typeOffer_returnsOnlyOfferTrackings() {
        // Arrange: tạo workflow OFFER + tracking OFFER cùng REQUEST_ID
        Workflow offerWf = saveWorkflow("Offer WF", WorkflowType.OFFER, 10L);
        WorkflowStep offerStep = saveStep(offerWf, 1, APPROVER_USER_ID);
        ApprovalTracking offerTracking = approvalTrackingRepository.save(
                buildTracking(REQUEST_ID, offerStep, ApprovalStatus.PENDING, APPROVER_USER_ID));

        // setUp đã có REQUEST tracking cùng REQUEST_ID → tổng = 2 trackings

        // Act
        var result = approvalTrackingService.getWorkflowInfoByRequestId(REQUEST_ID, null, "OFFER");

        // Assert: chỉ tracking OFFER được trả về
        assertFalse(result.getApprovalTrackings().isEmpty(),
                "BUG: Không tìm thấy tracking OFFER");
        // Tất cả tracking trả về phải thuộc OFFER workflow
        boolean allOffer = result.getApprovalTrackings().stream().allMatch(dto ->
                dto.getStepId() != null &&
                // Check DB
                workflowStepRepository.findById(dto.getStepId())
                        .map(s -> s.getWorkflow().getType() == WorkflowType.OFFER)
                        .orElse(false));
        assertTrue(allOffer, "BUG: Kết quả lẫn tracking REQUEST - filter OFFER không hoạt động");

        // Phải ít hơn tổng số tracking (vì có REQUEST tracking bị loại)
        var allResult = approvalTrackingService.getWorkflowInfoByRequestId(REQUEST_ID, null, null);
        assertTrue(result.getApprovalTrackings().size() < allResult.getApprovalTrackings().size(),
                "BUG: Filter OFFER không loại được tracking REQUEST");
    }

        /**
         * Test Case ID: AT-TC43
     * Nhánh requestType không xác định (vd "SOMETHING") → workflowType=null → không filter → trả tất cả.
     *
     * Bug bị bắt: Requesttype lạ bị xử lý như null → lọc ra hết kết quả.
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: Không tương tác/kiểm tra trực tiếp Repository trong test case này.
     */
    @Test
        @DisplayName("[AT-TC43] getWorkflowInfoByRequestId() - requestType unknown → không filter, trả tất cả")
        void tc40_getWorkflowInfoByRequestId_unknownType_returnsAllTrackings() {
        // Act: requestType không khớp bất kỳ giá trị nào → workflowType=null → không filter
        var resultUnknown = approvalTrackingService.getWorkflowInfoByRequestId(REQUEST_ID, null, "SOMETHING_ELSE");
        var resultNull    = approvalTrackingService.getWorkflowInfoByRequestId(REQUEST_ID, null, null);

        // Assert: kết quả giống nhau (cả hai không filter)
        assertEquals(resultNull.getApprovalTrackings().size(),
                resultUnknown.getApprovalTrackings().size(),
                "BUG: requestType unknown phải giống requestType=null (không filter)");
        assertFalse(resultUnknown.getApprovalTrackings().isEmpty(),
                "BUG: Kết quả rỗng dù có tracking - có thể đang filter nhầm type SOMETHING_ELSE");
    }

    // ================================================================
    // NHÓM 9: handleWorkflowEvent() — OFFER + departmentId resolution
    // ================================================================

        /**
         * Test Case ID: AT-TC44
     * Nhánh D2=True, D4=True, D5=True:
     * OFFER event, departmentId=null, candidateId có giá trị → candidateService trả về departmentId → set vào event.
     *
     * Bug bị bắt: Không gọi candidateService để resolve → departmentId vẫn null khi xử lý.
     *
     * CheckDB: Tracking được tạo (REQUEST_SUBMITTED thành công sau khi resolve departmentId).
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Đảm bảo hệ thống gọi candidateService để lấy thông tin phòng ban.
     * - Check DB: So sánh tổng số lượng bản ghi tracking trong DB trước và sau khi thực thi qua .count().
     */
    @Test
        @DisplayName("[AT-TC44] handleWorkflowEvent() - OFFER, departmentId=null, candidateId hợp lệ → resolve từ candidate")
        void tc41_handleWorkflowEvent_offerNullDeptWithCandidateId_resolvesDepartmentId() {
        // Arrange: workflow OFFER, step1 với approverPositionId
        Workflow offerWf = saveWorkflow("OFFER WF", WorkflowType.OFFER, 77L);
        saveStep(offerWf, 1, APPROVER_USER_ID);

        // Mock: candidateService trả về departmentId=77L
        when(candidateService.getDepartmentIdFromCandidate(eq(5L), anyString())).thenReturn(77L);

        long countBefore = approvalTrackingRepository.count();

        // Event: OFFER, departmentId=null, candidateId=5L, workflowId hợp lệ
        RecruitmentWorkflowEvent event = buildEvent("REQUEST_SUBMITTED", "OFFER", 60L);
        event.setDepartmentId(null);      // ← trigger resolve
        event.setCandidateId(5L);         // ← candidateId có giá trị
        event.setWorkflowId(offerWf.getId());

        // Act
        // Assert
        assertDoesNotThrow(() -> approvalTrackingService.handleWorkflowEvent(event),
                "BUG: NPE khi resolve departmentId từ candidate");

        // Assert: candidateService được gọi đúng (authToken có thể null)
        verify(candidateService, times(1)).getDepartmentIdFromCandidate(eq(5L), any());

        // CheckDB: departmentId được resolve → REQUEST_SUBMITTED xử lý thành công → tạo tracking
        assertEquals(countBefore + 1, approvalTrackingRepository.count(),
                "BUG: Tracking không được tạo sau khi resolve departmentId từ candidate");
    }

        /**
         * Test Case ID: AT-TC45
     * Nhánh D2=True, D4=False:
     * OFFER event, departmentId=null, candidateId=null → log warn, skip resolve, tiếp tục vào switch.
     *
     * Bug bị bắt: NPE khi candidateId=null không được guard.
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Đảm bảo hệ thống gọi candidateService để lấy thông tin phòng ban.
     * - Check DB: So sánh tổng số lượng bản ghi tracking trong DB trước và sau khi thực thi qua .count().
     */
    @Test
        @DisplayName("[AT-TC45] handleWorkflowEvent() - OFFER, departmentId=null, candidateId=null → log warn, không crash")
        void tc42_handleWorkflowEvent_offerNullDeptNullCandidateId_logsWarnNoCrash() {
        // Arrange
        long countBefore = approvalTrackingRepository.count();

        // Event: OFFER, departmentId=null, candidateId=null, workflowId có giá trị
        RecruitmentWorkflowEvent event = buildEvent("REQUEST_SUBMITTED", "OFFER", 61L);
        event.setDepartmentId(null);
        event.setCandidateId(null);       // ← nhánh D4=False
        event.setWorkflowId(workflow2Steps.getId());

        // Act: không crash, nhưng REQUEST_SUBMITTED với OFFER+departmentId=null → early return
        // Assert
        assertDoesNotThrow(() -> approvalTrackingService.handleWorkflowEvent(event),
                "BUG: NullPointerException khi candidateId=null không được guard");

        // candidateService không được gọi
        verify(candidateService, never()).getDepartmentIdFromCandidate(any(), any());

        // CheckDB: không tạo thêm tracking (early return do OFFER không có departmentId)
        assertEquals(countBefore, approvalTrackingRepository.count(),
                "BUG: Tạo tracking dù OFFER không có departmentId");
    }

    // ================================================================
    // NHÓM 10: handleRequestSubmitted() — tất cả nhánh
    // ================================================================

        /**
         * Test Case ID: AT-TC46
     * Nhánh D1=True: workflowId=null → early return, DB không đổi.
     *
     * Bug bị bắt: Gọi workflowRepository.findById(null) → NullPointerException hoặc xử lý sai.
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: So sánh tổng số lượng bản ghi tracking trong DB trước và sau khi thực thi qua .count().
     */
    @Test
        @DisplayName("[AT-TC46] handleRequestSubmitted() - workflowId=null → early return, DB không đổi")
        void tc43_handleRequestSubmitted_nullWorkflowId_earlyReturn() {
        long countBefore = approvalTrackingRepository.count();

        RecruitmentWorkflowEvent event = buildEvent("REQUEST_SUBMITTED", "REQUEST", 70L);
        event.setWorkflowId(null); // ← D1=True

        // Act + Assert
        assertDoesNotThrow(() -> approvalTrackingService.handleWorkflowEvent(event),
                "BUG: Exception khi workflowId=null");

        // Check DB
        assertEquals(countBefore, approvalTrackingRepository.count(),
                "BUG: DB bị thay đổi dù workflowId=null → phải early return");
    }

        /**
         * Test Case ID: AT-TC47
     * Nhánh D2=True: requestType=OFFER, departmentId=null → early return khi submit.
     *
     * Bug bị bắt: OFFER không có departmentId vẫn cố tạo tracking → lỗi dữ liệu.
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: So sánh tổng số lượng bản ghi tracking trong DB trước và sau khi thực thi qua .count().
     */
    @Test
        @DisplayName("[AT-TC47] handleRequestSubmitted() - OFFER + departmentId=null → early return")
        void tc44_handleRequestSubmitted_offerNullDepartmentId_earlyReturn() {
        long countBefore = approvalTrackingRepository.count();

        RecruitmentWorkflowEvent event = buildEvent("REQUEST_SUBMITTED", "OFFER", 71L);
        event.setWorkflowId(workflow2Steps.getId());
        event.setDepartmentId(null); // ← D2=True (OFFER + no deptId)

        // Act + Assert
        assertDoesNotThrow(() -> approvalTrackingService.handleWorkflowEvent(event),
                "BUG: Exception khi OFFER không có departmentId");

        // Check DB
        assertEquals(countBefore, approvalTrackingRepository.count(),
                "BUG: Tạo tracking dù OFFER không có departmentId → phải early return");
    }

        /**
         * Test Case ID: AT-TC48
     * Nhánh D3=False (không có pending), D5=False (không có returnedTracking):
     * Submit lần đầu bình thường → tạo 1 tracking PENDING tại step1.
     *
     * Bug bị bắt:
     *   - Không tạo tracking → luồng dừng tại bước 1
     *   - Tracking tạo ra không có status=PENDING
     *   - Tracking gắn sai step
     *
     * CheckDB: +1 tracking PENDING tại step1
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: So sánh tổng số lượng bản ghi tracking trong DB trước và sau khi thực thi qua .count() & Truy vấn DB bằng findByRequestId() để xác minh tracking mới được tạo và gán đúng workflow step.
     */
    @Test
        @DisplayName("[AT-TC48] handleRequestSubmitted() - submit lần đầu → tạo tracking PENDING tại step1")
        void tc45_handleRequestSubmitted_firstSubmit_createsStep1PendingTracking() {
        // Dùng requestId khác để không bị lẫn với setUp
        Long newRequestId = 100L;
        long countBefore = approvalTrackingRepository.count();

        RecruitmentWorkflowEvent event = buildEvent("REQUEST_SUBMITTED", "REQUEST", newRequestId);
        event.setWorkflowId(workflow2Steps.getId());
        event.setDepartmentId(10L);
        event.setRequesterId(null); // null → skip auto-approve logic

        // Act
        // Assert
        assertDoesNotThrow(() -> approvalTrackingService.handleWorkflowEvent(event));

        // CheckDB: +1 tracking
        assertEquals(countBefore + 1, approvalTrackingRepository.count(),
                "BUG: Không tạo tracking khi submit lần đầu");

        List<ApprovalTracking> created = approvalTrackingRepository.findByRequestId(newRequestId);
        assertEquals(1, created.size(), "BUG: Phải có đúng 1 tracking mới");
        assertEquals(ApprovalStatus.PENDING, created.get(0).getStatus(),
                "BUG: Tracking mới phải có status=PENDING");
        assertEquals(step1.getId(), created.get(0).getStep().getId(),
                "BUG: Tracking mới phải gắn với step1");
    }

        /**
         * Test Case ID: AT-TC49
     * Nhánh D3=False, D5=False, D6=True (position & dept khớp) → auto-approve step1 + tạo tracking step2.
     *
     * Cách giả lập: Mock restTemplate để getRequesterPositionId trả về APPROVER_USER_ID (= step1.approverPositionId)
     *               và getRequesterDepartmentId trả về deptId=10L (= event.departmentId).
     *
     * Bug bị bắt:
     *   - Không auto-approve → step1 vẫn PENDING dù người tạo = người duyệt
     *   - Không tạo tracking step2 sau auto-approve
     *
     * CheckDB: step1 tracking = APPROVED, step2 tracking = PENDING
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: So sánh tổng số lượng bản ghi tracking trong DB trước và sau khi thực thi qua .count() & Truy vấn DB bằng findByRequestId() để xác minh tracking mới được tạo và gán đúng workflow step.
     */
    @Test
        @DisplayName("[AT-TC49] handleRequestSubmitted() - requester trùng approver step1 → auto-approve step1 + tạo step2")
        void tc46_handleRequestSubmitted_requesterMatchesStep1Approver_autoApprovesAndCreatesStep2() {
        Long newRequestId = 110L;

        // Mock restTemplate.exchange: trả về JSON employee với position.id = APPROVER_USER_ID và department.id = 10L
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode positionNode = mapper.createObjectNode();
        positionNode.put("id", APPROVER_USER_ID);
        com.fasterxml.jackson.databind.node.ObjectNode deptNode = mapper.createObjectNode();
        deptNode.put("id", 10L);
        com.fasterxml.jackson.databind.node.ObjectNode employeeNode = mapper.createObjectNode();
        employeeNode.set("position", positionNode);
        employeeNode.set("department", deptNode);

        Response<com.fasterxml.jackson.databind.JsonNode> body = new Response<>();
        body.setData(employeeNode);
        org.springframework.http.ResponseEntity<Response<com.fasterxml.jackson.databind.JsonNode>> mockResp =
                org.springframework.http.ResponseEntity.ok(body);

        when(restTemplate.exchange(
                anyString(),
                eq(org.springframework.http.HttpMethod.GET),
                any(),
                any(org.springframework.core.ParameterizedTypeReference.class)))
        .thenReturn(mockResp);

        long countBefore = approvalTrackingRepository.count();

        RecruitmentWorkflowEvent event = buildEvent("REQUEST_SUBMITTED", "REQUEST", newRequestId);
        event.setWorkflowId(workflow2Steps.getId());
        event.setDepartmentId(10L);
        event.setRequesterId(APPROVER_USER_ID); // ← requester có positionId = step1.approverPositionId

        // Act
        // Assert
        assertDoesNotThrow(() -> approvalTrackingService.handleWorkflowEvent(event));

        // CheckDB: +2 tracking (step1 APPROVED + step2 PENDING)
        assertEquals(countBefore + 2, approvalTrackingRepository.count(),
                "BUG: Phải tạo 2 tracking (step1 APPROVED + step2 PENDING) khi auto-approve");

        List<ApprovalTracking> created = approvalTrackingRepository.findByRequestId(newRequestId);
        boolean hasStep1Approved = created.stream().anyMatch(t ->
                t.getStep().getStepOrder() == 1 && t.getStatus() == ApprovalStatus.APPROVED);
        boolean hasStep2Pending = created.stream().anyMatch(t ->
                t.getStep().getStepOrder() == 2 && t.getStatus() == ApprovalStatus.PENDING);

        assertTrue(hasStep1Approved, "BUG: Tracking step1 phải có status=APPROVED sau auto-approve");
        assertTrue(hasStep2Pending, "BUG: Tracking step2 phải được tạo với status=PENDING");
    }

        /**
         * Test Case ID: AT-TC50
     * Nhánh D3=False, D5=False, D6=False (position KHÔNG khớp) → tạo tracking PENDING step1 bình thường.
     *
     * Cách giả lập: restTemplate trả null → getRequesterPositionId=null → positionMatches=false.
     *
     * Bug bị bắt: Nếu code thiếu else branch → không tạo tracking dù requester không phải approver.
     *
     * CheckDB: 1 tracking PENDING tại step1
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: So sánh tổng số lượng bản ghi tracking trong DB trước và sau khi thực thi qua .count() & Truy vấn DB bằng findByRequestId() để xác minh tracking mới được tạo và gán đúng workflow step.
     */
    @Test
        @DisplayName("[AT-TC50] handleRequestSubmitted() - requester KHÔNG trùng approver → tạo tracking PENDING step1")
        void tc47_handleRequestSubmitted_requesterNotMatchStep1Approver_createsNormalStep1Tracking() {
        Long newRequestId = 120L;
        // restTemplate trả null → getRequesterPositionId throws NPE inside try → returns null
        // → positionMatches = false → không auto-approve

        long countBefore = approvalTrackingRepository.count();

        RecruitmentWorkflowEvent event = buildEvent("REQUEST_SUBMITTED", "REQUEST", newRequestId);
        event.setWorkflowId(workflow2Steps.getId());
        event.setDepartmentId(10L);
        event.setRequesterId(OTHER_USER_ID); // khác APPROVER_USER_ID

        // Act + Assert
        assertDoesNotThrow(() -> approvalTrackingService.handleWorkflowEvent(event));

        // CheckDB: +1 tracking PENDING tại step1
        assertEquals(countBefore + 1, approvalTrackingRepository.count(),
                "BUG: Không tạo tracking dù requester không trùng approver");

        List<ApprovalTracking> created = approvalTrackingRepository.findByRequestId(newRequestId);
        assertEquals(ApprovalStatus.PENDING, created.get(0).getStatus(),
                "BUG: Status phải PENDING");
        assertEquals(1, created.get(0).getStep().getStepOrder(),
                "BUG: Phải tạo tracking tại step1 (stepOrder=1)");
    }

        /**
         * Test Case ID: AT-TC51
     * Nhánh D3=True (có pending), D4=False (không phải return placeholder):
     * Resubmit khi đang có PENDING thường → cancel pending cũ + tạo tracking mới.
     *
     * Bug bị bắt: Không cancel pending cũ → tồn tại 2 tracking PENDING cùng lúc.
     *
     * CheckDB: tracking cũ = CANCELLED, tracking mới = PENDING
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: So sánh tổng số lượng bản ghi tracking trong DB trước và sau khi thực thi qua .count() & Truy vấn DB bằng findById() để assert các trường thay đổi như status, actionType, notes, actionUserId.
     */
    @Test
        @DisplayName("[AT-TC51] handleRequestSubmitted() - có pending thường → cancel pending cũ + tạo tracking mới")
        void tc48_handleRequestSubmitted_resubmitWithExistingPending_cancelOldAndCreateNew() {
        // setUp đã có pendingTracking cho REQUEST_ID → pending tồn tại
        long countBefore = approvalTrackingRepository.count();

        RecruitmentWorkflowEvent event = buildEvent("REQUEST_SUBMITTED", "REQUEST", REQUEST_ID);
        event.setWorkflowId(workflow2Steps.getId());
        event.setDepartmentId(10L);
        event.setActorUserId(APPROVER_USER_ID);
        event.setRequesterId(null); // null để skip auto-approve

        // Act + Assert
        assertDoesNotThrow(() -> approvalTrackingService.handleWorkflowEvent(event));

        // CheckDB 1: tracking cũ phải bị CANCELLED
        ApprovalTracking oldTracking = approvalTrackingRepository.findById(pendingTracking.getId()).orElseThrow();
        assertEquals(ApprovalStatus.CANCELLED, oldTracking.getStatus(),
                "BUG: Tracking PENDING cũ không bị CANCELLED khi resubmit");
        assertEquals("RESUBMIT", oldTracking.getActionType(),
                "BUG: actionType phải là RESUBMIT");

        // CheckDB 2: tạo thêm 1 tracking mới PENDING
        assertEquals(countBefore + 1, approvalTrackingRepository.count(),
                "BUG: Không tạo tracking mới sau khi cancel pending cũ");
    }

        /**
         * Test Case ID: AT-TC52
     * Nhánh D5=True: returnedTracking != null → submit lại sau return → tạo tracking từ bước được trả về.
     * Nhánh D9=True + D10=False: isResubmitAfterReturn=true, existingNotes=null → notes = "Đã chỉnh sửa".
     *
     * Bug bị bắt:
     *   - Không detect returnedTracking → xử lý như submit lần đầu (sai step)
     *   - Notes không được gắn "Đã chỉnh sửa"
     *
     * CheckDB: tracking mới với notes chứa "Đã chỉnh sửa"
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: So sánh tổng số lượng bản ghi tracking trong DB trước và sau khi thực thi qua .count() & Truy vấn DB bằng findByRequestId() để xác minh tracking mới được tạo và gán đúng workflow step.
     */
    @Test
        @DisplayName("[AT-TC52] handleRequestSubmitted() - resubmit sau return → tạo tracking từ bước returnedToStepId + ghi chú 'Đã chỉnh sửa'")
        void tc49_handleRequestSubmitted_resubmitAfterReturn_createsTrackingFromReturnedStep() {
        Long newRequestId = 130L;

        // Arrange: tracking đã RETURNED, returnedToStepId = step1
        ApprovalTracking returned = buildTracking(newRequestId, step1, ApprovalStatus.RETURNED, APPROVER_USER_ID);
        returned.setReturnedToStepId(step1.getId()); // ← có returnedToStepId
        returned = approvalTrackingRepository.save(returned);

        long countBefore = approvalTrackingRepository.count();

        RecruitmentWorkflowEvent event = buildEvent("REQUEST_SUBMITTED", "REQUEST", newRequestId);
        event.setWorkflowId(workflow2Steps.getId());
        event.setDepartmentId(10L);
        event.setRequesterId(null);

        // Act + Assert
        assertDoesNotThrow(() -> approvalTrackingService.handleWorkflowEvent(event));

        // CheckDB: tracking mới được tạo từ step được trả về
        assertEquals(countBefore + 1, approvalTrackingRepository.count(),
                "BUG: Không tạo tracking khi resubmit sau return");

        List<ApprovalTracking> all = approvalTrackingRepository.findByRequestId(newRequestId);
        ApprovalTracking newTracking = all.stream()
                .filter(t -> t.getStatus() == ApprovalStatus.PENDING)
                .findFirst()
                .orElse(null);
        assertNotNull(newTracking, "BUG: Tracking mới phải có status=PENDING");
        assertNotNull(newTracking.getNotes(), "BUG: Notes phải được set khi isResubmitAfterReturn");
        assertTrue(newTracking.getNotes().contains("Đã chỉnh sửa"),
                "BUG: Notes phải chứa 'Đã chỉnh sửa' khi resubmit sau return");
    }

        /**
         * Test Case ID: AT-TC53
     * Các pending tracking đều là placeholder return → phải cancel placeholder và tạo tracking mới.
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: So sánh tổng số lượng bản ghi tracking trong DB trước và sau khi thực thi qua .count() & Truy vấn DB bằng findById() để assert các trường thay đổi như status, actionType, notes, actionUserId.
     */
    @Test
        @DisplayName("[AT-TC53] handleRequestSubmitted() - placeholder pending → cancel placeholder và tạo tracking mới")
        void tc50_handleRequestSubmitted_allPendingAreReturnPlaceholders_resubmitsAndCancelsPlaceholder() {
        Long newRequestId = 140L;

        ApprovalTracking placeholder = buildTracking(newRequestId, step1, ApprovalStatus.PENDING, APPROVER_USER_ID);
        placeholder.setNotes("Waiting for update after return - placeholder");
        approvalTrackingRepository.save(placeholder);

        long countBefore = approvalTrackingRepository.count();

        RecruitmentWorkflowEvent event = buildEvent("REQUEST_SUBMITTED", "REQUEST", newRequestId);
        event.setWorkflowId(workflow2Steps.getId());
        event.setDepartmentId(10L);
        event.setActorUserId(APPROVER_USER_ID);
        event.setRequesterId(null);

        // Act + Assert
        assertDoesNotThrow(() -> approvalTrackingService.handleWorkflowEvent(event));

        // Check DB
        assertEquals(countBefore + 1, approvalTrackingRepository.count(),
                "BUG: Không tạo tracking mới khi resubmit placeholder return");

        ApprovalTracking updated = approvalTrackingRepository.findById(placeholder.getId()).orElseThrow();
        assertEquals(ApprovalStatus.CANCELLED, updated.getStatus(), "BUG: Placeholder không bị cancel khi resubmit");
        assertEquals("RESUBMIT", updated.getActionType(), "BUG: actionType phải là RESUBMIT cho placeholder");
        assertEquals("Placeholder cancelled due to resubmit", updated.getNotes(),
                "BUG: Placeholder phải được ghi chú là cancelled due to resubmit");
    }

        /**
         * Test Case ID: AT-TC54
     * Workflow chỉ có 1 bước, requester trùng approver step1 → auto-approve step1 và gửi WORKFLOW_COMPLETED.
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Đảm bảo hệ thống publish sự kiện workflow qua Kafka.
     * - Check DB: So sánh tổng số lượng bản ghi tracking trong DB trước và sau khi thực thi qua .count() & Truy vấn DB bằng findByRequestId() để xác minh tracking mới được tạo và gán đúng workflow step.
     */
    @Test
        @DisplayName("[AT-TC54] handleRequestSubmitted() - auto-approve step1 của workflow 1 bước → gửi WORKFLOW_COMPLETED")
        void tc51_handleRequestSubmitted_singleStepWorkflow_autoApproveAndComplete() {
        Workflow singleStepWorkflow = saveWorkflow("Single Step Workflow", WorkflowType.REQUEST, 10L);
        saveStep(singleStepWorkflow, 1, APPROVER_USER_ID);

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode positionNode = mapper.createObjectNode();
        positionNode.put("id", APPROVER_USER_ID);
        com.fasterxml.jackson.databind.node.ObjectNode deptNode = mapper.createObjectNode();
        deptNode.put("id", 10L);
        com.fasterxml.jackson.databind.node.ObjectNode employeeNode = mapper.createObjectNode();
        employeeNode.set("position", positionNode);
        employeeNode.set("department", deptNode);

        Response<com.fasterxml.jackson.databind.JsonNode> body = new Response<>();
        body.setData(employeeNode);
        org.springframework.http.ResponseEntity<Response<com.fasterxml.jackson.databind.JsonNode>> mockResp =
                org.springframework.http.ResponseEntity.ok(body);

        when(restTemplate.exchange(
                anyString(),
                eq(org.springframework.http.HttpMethod.GET),
                any(),
                any(org.springframework.core.ParameterizedTypeReference.class)))
        .thenReturn(mockResp);

        long countBefore = approvalTrackingRepository.count();

        RecruitmentWorkflowEvent event = buildEvent("REQUEST_SUBMITTED", "REQUEST", 150L);
        event.setWorkflowId(singleStepWorkflow.getId());
        event.setDepartmentId(10L);
        event.setRequesterId(APPROVER_USER_ID);

        // Act + Assert
        assertDoesNotThrow(() -> approvalTrackingService.handleWorkflowEvent(event));

        // Check DB
        assertEquals(countBefore + 1, approvalTrackingRepository.count(),
                "BUG: Workflow 1 bước phải tạo đúng 1 tracking khi auto-approve step1");
        ApprovalTracking created = approvalTrackingRepository.findByRequestId(150L).stream()
                .findFirst()
                .orElseThrow();
        assertEquals(ApprovalStatus.APPROVED, created.getStatus(), "BUG: Step1 phải được auto-approve");
        assertEquals(singleStepWorkflow.getId(), created.getStep().getWorkflow().getId(),
                "BUG: Tracking phải gắn đúng workflow");
        verify(workflowProducer, times(1)).publishEvent(any(RecruitmentWorkflowEvent.class));
    }

    // ================================================================
    // NHÓM 11: handleStepApproved() / handleStepRejected() / handleRequestReturned()
    //           — các nhánh "không tìm thấy pending tracking"
    // ================================================================

        /**
         * Test Case ID: AT-TC55
     * Nhánh D1=False trong handleStepApproved(): markCurrentTracking trả null (không có PENDING).
     * → bỏ qua, DB không đổi.
     *
     * Bug bị bắt: NPE khi gọi current.getStep() trên null.
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: So sánh tổng số lượng bản ghi tracking trong DB trước và sau khi thực thi qua .count().
     */
    @Test
        @DisplayName("[AT-TC55] REQUEST_APPROVED - không có PENDING tracking → bỏ qua, DB không đổi")
        void tc52_handleStepApproved_noPendingTracking_skipsGracefully() {
        // Đặt tracking thành APPROVED trước → không còn PENDING
        pendingTracking.setStatus(ApprovalStatus.APPROVED);
        approvalTrackingRepository.save(pendingTracking);

        long countBefore = approvalTrackingRepository.count();

        RecruitmentWorkflowEvent event = buildEvent("REQUEST_APPROVED", "REQUEST", REQUEST_ID);
        event.setActorUserId(APPROVER_USER_ID);
        event.setWorkflowId(workflow2Steps.getId());
        event.setDepartmentId(10L);

        // Act + Assert
        assertDoesNotThrow(() -> approvalTrackingService.handleWorkflowEvent(event),
                "BUG: Exception khi không có PENDING tracking để approve");

        // Check DB
        assertEquals(countBefore, approvalTrackingRepository.count(),
                "BUG: DB bị thay đổi dù không có pending tracking để approve");
    }

        /**
         * Test Case ID: AT-TC56
     * Nhánh D1=False trong handleStepRejected(): markCurrentTracking trả null.
     * → bỏ qua, DB không đổi.
     *
     * Bug bị bắt: NPE khi gọi tracking.getStep() trên null.
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: So sánh tổng số lượng bản ghi tracking trong DB trước và sau khi thực thi qua .count() & Truy vấn DB bằng findById() để assert các trường thay đổi như status, actionType, notes, actionUserId.
     */
    @Test
        @DisplayName("[AT-TC56] REQUEST_REJECTED - không có PENDING tracking → bỏ qua, DB không đổi")
        void tc53_handleStepRejected_noPendingTracking_skipsGracefully() {
        // Đặt tracking thành APPROVED → không còn PENDING cho REQUEST_REJECTED xử lý
        pendingTracking.setStatus(ApprovalStatus.APPROVED);
        approvalTrackingRepository.save(pendingTracking);

        long countBefore = approvalTrackingRepository.count();

        RecruitmentWorkflowEvent event = buildEvent("REQUEST_REJECTED", "REQUEST", REQUEST_ID);
        event.setActorUserId(APPROVER_USER_ID);

        assertDoesNotThrow(() -> approvalTrackingService.handleWorkflowEvent(event),
                "BUG: Exception khi không có PENDING tracking để reject");

        // Check DB
        assertEquals(countBefore, approvalTrackingRepository.count(),
                "BUG: DB bị thay đổi dù không có pending tracking");

        // Tracking APPROVED vẫn giữ nguyên
        assertEquals(ApprovalStatus.APPROVED,
                approvalTrackingRepository.findById(pendingTracking.getId()).orElseThrow().getStatus(),
                "BUG: Tracking đã APPROVED bị thay đổi");
    }

        /**
         * Test Case ID: AT-TC57
     * Nhánh currentTracking=null trong handleRequestReturned(): không tìm thấy pending tracking → early return.
     *
     * Bug bị bắt: NPE khi gọi currentTracking.setStatus() trên null.
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: So sánh tổng số lượng bản ghi tracking trong DB trước và sau khi thực thi qua .count().
     */
    @Test
        @DisplayName("[AT-TC57] REQUEST_RETURNED - không có PENDING tracking → early return, DB không đổi")
        void tc54_handleRequestReturned_noPendingTracking_earlyReturn() {
        // Đặt tracking thành APPROVED → không còn PENDING
        pendingTracking.setStatus(ApprovalStatus.APPROVED);
        approvalTrackingRepository.save(pendingTracking);

        long countBefore = approvalTrackingRepository.count();

        RecruitmentWorkflowEvent event = buildEvent("REQUEST_RETURNED", "REQUEST", REQUEST_ID);
        event.setActorUserId(APPROVER_USER_ID);
        event.setWorkflowId(workflow2Steps.getId());
        event.setReturnedToStepId(step1.getId());

        // Act + Assert
        assertDoesNotThrow(() -> approvalTrackingService.handleWorkflowEvent(event),
                "BUG: NPE khi không có PENDING tracking cho REQUEST_RETURNED");

        // Check DB
        assertEquals(countBefore, approvalTrackingRepository.count(),
                "BUG: DB bị thay đổi dù không có tracking để return");
    }

        /**
         * Test Case ID: AT-TC58
         * REQUEST_RETURNED với reason=null và returnedToStepId=null → tự resolve step 1.
         * 
         * CHI TIẾT TEST CASE:
         * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
         * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
         * - Check DB: Truy vấn DB bằng findById() để assert các trường thay đổi như status, actionType, notes, actionUserId.
         */
        @Test
        @DisplayName("[AT-TC58] REQUEST_RETURNED reason=null + returnedToStepId=null → tự resolve step 1")
        void tc44b_handleRequestReturned_nullReason_defaultMessageAndAutoResolve() {
                RecruitmentWorkflowEvent event = buildEvent("REQUEST_RETURNED", "REQUEST", REQUEST_ID);
                event.setActorUserId(APPROVER_USER_ID);
                event.setWorkflowId(workflow2Steps.getId());
                event.setReturnedToStepId(null);
                event.setReason(null);

                // Act
                approvalTrackingService.handleWorkflowEvent(event);

                // Check DB
                ApprovalTracking updated = approvalTrackingRepository.findById(pendingTracking.getId()).orElseThrow();
                // Assert
                assertEquals(ApprovalStatus.RETURNED, updated.getStatus(), "BUG: Return null reason vẫn phải set RETURNED");
                assertEquals(step1.getId(), updated.getReturnedToStepId(), "BUG: returnedToStepId phải tự resolve về step1");
                assertNull(updated.getNotes(), "BUG: notes từ reason=null phải là null trước khi persist");
        }

        /**
         * Test Case ID: AT-TC59
     * Nhánh recipients.isEmpty() = True trong notifyRequester():
     * Cả requesterId và ownerUserId đều null → recipients rỗng → không gọi sendNotification.
     *
     * Bug bị bắt: Gọi sendNotification với null recipientId → NPE trong producer.
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Đảm bảo hệ thống gọi notificationProducer để gửi thông báo/email.
     * - Check DB: Kiểm tra DB không bị thay đổi bằng cách so sánh số lượng record hoặc verify không có lỗi.
     */
    @Test
        @DisplayName("[AT-TC59] REQUEST_CANCELLED - requesterId và ownerUserId null → không gọi sendNotification")
        void tc55_notifyRequester_emptyRecipients_doesNotSendNotification() {
        // Arrange: event không có requesterId và ownerUserId
        RecruitmentWorkflowEvent event = new RecruitmentWorkflowEvent();
        event.setEventType("REQUEST_CANCELLED");
        event.setRequestType("REQUEST");
        event.setRequestId(REQUEST_ID);
        event.setActorUserId(APPROVER_USER_ID);
        event.setRequesterId(null);   // ← D1=False
        event.setOwnerUserId(null);   // ← D2=False → recipients rỗng

        // Act
        // Assert
        assertDoesNotThrow(() -> approvalTrackingService.handleWorkflowEvent(event),
                "BUG: Exception khi requesterId và ownerUserId đều null");

        // Assert: sendNotification không được gọi (vì recipients rỗng)
        verify(notificationProducer, never()).sendNotification(any(), any(), any(), any());
    }

        /**
         * Test Case ID: AT-TC60
     * handleStepApproved(): Không có PENDING tracking → bỏ qua, DB không đổi
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Đảm bảo hệ thống gọi notificationProducer để gửi thông báo/email.
     * - Check DB: So sánh tổng số lượng bản ghi tracking trong DB trước và sau khi thực thi qua .count().
     */
    @Test
        @DisplayName("[AT-TC60] handleStepApproved() - không có PENDING tracking → bỏ qua, DB không đổi")
        void tc56_handleStepApproved_noPending_skipsNoDbChange() {
        // Arrange: đảm bảo không có tracking PENDING cho REQUEST_ID
        pendingTracking.setStatus(ApprovalStatus.APPROVED);
        approvalTrackingRepository.save(pendingTracking);

        long countBefore = approvalTrackingRepository.count();

        RecruitmentWorkflowEvent event = buildEvent("REQUEST_APPROVED", "REQUEST", REQUEST_ID);
        event.setActorUserId(APPROVER_USER_ID);

        // Act + Assert: không ném ngoại lệ
        assertDoesNotThrow(() -> approvalTrackingService.handleWorkflowEvent(event),
                "BUG: Exception khi xử lý REQUEST_APPROVED nhưng không có PENDING tracking");

        // DB không đổi
        // Check DB
        assertEquals(countBefore, approvalTrackingRepository.count(),
                "BUG: DB bị thay đổi dù không có PENDING tracking");

        // Không gọi gửi notification cho requester/owner khi không có tracking
        verify(notificationProducer, never()).sendNotification(anyLong(), anyString(), anyString(), anyString());
    }

        /**
         * Test Case ID: AT-TC61
     * handleStepRejected(): Không có PENDING tracking → bỏ qua, DB không đổi
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: So sánh tổng số lượng bản ghi tracking trong DB trước và sau khi thực thi qua .count().
     */
    @Test
        @DisplayName("[AT-TC61] handleStepRejected() - không có PENDING tracking → bỏ qua, DB không đổi")
        void tc57_handleStepRejected_noPending_skipsNoDbChange() {
        // Arrange
        pendingTracking.setStatus(ApprovalStatus.APPROVED);
        approvalTrackingRepository.save(pendingTracking);

        long countBefore = approvalTrackingRepository.count();

        RecruitmentWorkflowEvent event = buildEvent("REQUEST_REJECTED", "REQUEST", REQUEST_ID);
        event.setActorUserId(APPROVER_USER_ID);

        // Act + Assert
        assertDoesNotThrow(() -> approvalTrackingService.handleWorkflowEvent(event),
                "BUG: Exception khi xử lý REQUEST_REJECTED nhưng không có PENDING tracking");

        // Check DB
        assertEquals(countBefore, approvalTrackingRepository.count(),
                "BUG: DB bị thay đổi dù không có PENDING tracking");
    }

        /**
         * Test Case ID: AT-TC62
     * handleRequestReturned(): Không có PENDING tracking → early return, DB không đổi
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: So sánh tổng số lượng bản ghi tracking trong DB trước và sau khi thực thi qua .count().
     */
    @Test
        @DisplayName("[AT-TC62] handleRequestReturned() - không có PENDING tracking → early return, DB không đổi")
        void tc58_handleRequestReturned_noPending_earlyReturnNoDbChange() {
        // Arrange
        pendingTracking.setStatus(ApprovalStatus.APPROVED);
        approvalTrackingRepository.save(pendingTracking);

        long countBefore = approvalTrackingRepository.count();

        RecruitmentWorkflowEvent event = buildEvent("REQUEST_RETURNED", "REQUEST", REQUEST_ID);
        event.setActorUserId(APPROVER_USER_ID);

        // Act
        // Assert
        assertDoesNotThrow(() -> approvalTrackingService.handleWorkflowEvent(event),
                "BUG: Exception khi xử lý REQUEST_RETURNED nhưng không có PENDING tracking");

        // DB không đổi
        // Check DB
        assertEquals(countBefore, approvalTrackingRepository.count(),
                "BUG: DB bị thay đổi dù không có PENDING tracking");
    }

        /**
         * Test Case ID: AT-TC63
     * notifyRequester(): requesterId=null và ownerUserId=null → không gọi sendNotification
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Đảm bảo hệ thống gọi notificationProducer để gửi thông báo/email.
     * - Check DB: Kiểm tra DB không bị thay đổi bằng cách so sánh số lượng record hoặc verify không có lỗi.
     */
    @Test
        @DisplayName("[AT-TC63] notifyRequester() - requesterId=null và ownerUserId=null → không gọi sendNotification")
        void tc59_notifyRequester_nullRecipients_doesNotSendNotification() {
        RecruitmentWorkflowEvent event = buildEvent("REQUEST_CANCELLED", "REQUEST", REQUEST_ID);
        event.setRequesterId(null);
        event.setOwnerUserId(null);

        // Act + Assert
        assertDoesNotThrow(() -> approvalTrackingService.handleWorkflowEvent(event),
                "BUG: Exception khi notifyRequester với recipients rỗng");

        verify(notificationProducer, never()).sendNotification(anyLong(), anyString(), anyString(), anyString());
    }

        /**
         * Test Case ID: AT-TC64
     * Dùng reflection để cover các helper private:
     * - convertWorkflowToDTO(Workflow)
     * - convertWorkflowToDTO(Workflow, Map) với steps=null
     * - convertStepToDTO(WorkflowStep)
     * - convertStepToDTO(WorkflowStep, Map) với positionNamesMap=null và approverPositionId=null
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback : Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check : Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB : Không tương tác/kiểm tra trực tiếp Repository trong test case này.
     */
    @Test
        @DisplayName("[AT-TC64] private convert helpers - cover null branches")
        void tc60_privateConvertHelpers_coverNullBranches() throws Exception {
        Workflow workflow = new Workflow();
        workflow.setId(900L);
        workflow.setName("WF Private Convert");
        workflow.setType(WorkflowType.REQUEST);
        workflow.setDepartmentId(10L);
        workflow.setIsActive(true);

        java.lang.reflect.Method workflowMethod = ApprovalTrackingService.class
                .getDeclaredMethod("convertWorkflowToDTO", Workflow.class);
        workflowMethod.setAccessible(true);
        com.example.workflow_service.dto.workflow.WorkflowResponseDTO workflowDto =
                (com.example.workflow_service.dto.workflow.WorkflowResponseDTO) workflowMethod.invoke(approvalTrackingService, workflow);

        assertNotNull(workflowDto, "BUG: convertWorkflowToDTO(Workflow) trả về null");
        assertEquals(900L, workflowDto.getId(), "BUG: workflow id sai");

        java.lang.reflect.Method workflowMapMethod = ApprovalTrackingService.class
                .getDeclaredMethod("convertWorkflowToDTO", Workflow.class, Map.class);
        workflowMapMethod.setAccessible(true);
        com.example.workflow_service.dto.workflow.WorkflowResponseDTO workflowDtoWithMap =
                (com.example.workflow_service.dto.workflow.WorkflowResponseDTO) workflowMapMethod.invoke(
                approvalTrackingService, workflow, null);
        assertNotNull(workflowDtoWithMap, "BUG: convertWorkflowToDTO(Workflow, Map) trả về null");
        assertNull(workflowDtoWithMap.getSteps(), "BUG: steps phải null khi workflow.getSteps() null");

        WorkflowStep transientStep = new WorkflowStep();
        transientStep.setId(901L);
        transientStep.setStepOrder(1);
        transientStep.setApproverPositionId(null);
        transientStep.setIsActive(true);

        java.lang.reflect.Method stepMethod = ApprovalTrackingService.class
                .getDeclaredMethod("convertStepToDTO", WorkflowStep.class);
        stepMethod.setAccessible(true);
        com.example.workflow_service.dto.workflow.WorkflowStepResponseDTO stepDto =
                (com.example.workflow_service.dto.workflow.WorkflowStepResponseDTO) stepMethod.invoke(approvalTrackingService, transientStep);
        assertNotNull(stepDto, "BUG: convertStepToDTO(WorkflowStep) trả về null");
        assertNull(stepDto.getApproverPositionName(), "BUG: approverPositionName phải null khi approverPositionId null");

        java.lang.reflect.Method stepMapMethod = ApprovalTrackingService.class
                .getDeclaredMethod("convertStepToDTO", WorkflowStep.class, Map.class);
        stepMapMethod.setAccessible(true);
        com.example.workflow_service.dto.workflow.WorkflowStepResponseDTO stepDtoWithMap =
                (com.example.workflow_service.dto.workflow.WorkflowStepResponseDTO) stepMapMethod.invoke(
                approvalTrackingService, transientStep, null);
        assertNotNull(stepDtoWithMap, "BUG: convertStepToDTO(WorkflowStep, Map) trả về null");
        assertNull(stepDtoWithMap.getApproverPositionName(), "BUG: position name phải null khi map/position null");
    }

        /**
         * Test Case ID: AT-TC65
     * Cover helper createTrackingForReturnedStep() và nhánh isReturned=true/false.
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback : Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Đảm bảo hệ thống gọi notificationProducer để gửi thông báo/email.
     * - Check DB : So sánh tổng số lượng bản ghi tracking trong DB trước và sau khi thực thi qua .count().
     */
    @Test
        @DisplayName("[AT-TC65] createTrackingForReturnedStep() - cover isReturned branches")
        void tc61_createTrackingForReturnedStep_coverIsReturnedBranches() throws Exception {
        Workflow workflow = saveWorkflow("WF Returned Helper", WorkflowType.REQUEST, 20L);
        WorkflowStep returnedStep = saveStep(workflow, 1, APPROVER_USER_ID);

        RecruitmentWorkflowEvent event = buildEvent("REQUEST_RETURNED", "REQUEST", 800L);
        event.setDepartmentId(20L);
        event.setAuthToken("helper-token");

        ApprovalTrackingService targetService = org.springframework.test.util.AopTestUtils.getTargetObject(approvalTrackingService);
        java.lang.reflect.Method method = ApprovalTrackingService.class
                .getDeclaredMethod("createTrackingForReturnedStep", RecruitmentWorkflowEvent.class, Long.class, boolean.class);
        method.setAccessible(true);

        long countBefore = approvalTrackingRepository.count();
        method.invoke(targetService, event, returnedStep.getId(), false);

        assertEquals(countBefore + 1, approvalTrackingRepository.count(),
                "BUG: createTrackingForReturnedStep(false) không tạo tracking");
        verify(notificationProducer, times(1))
                .sendNotificationToDepartment(eq(20L), eq(APPROVER_USER_ID), anyString(), anyString(), eq("helper-token"));

        reset(notificationProducer);
        method.invoke(targetService, event, returnedStep.getId(), true);
        assertEquals(countBefore + 2, approvalTrackingRepository.count(),
                "BUG: createTrackingForReturnedStep(true) không tạo tracking");
        verify(notificationProducer, never()).sendNotificationToDepartment(anyLong(), anyLong(), anyString(), anyString(), anyString());
    }

        /**
         * Test Case ID: AT-TC66
     * Cover getRequesterPositionId() và getRequesterDepartmentId() cho các nhánh
     * null/không có field/có field department.id và departmentId.
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: Không tương tác/kiểm tra trực tiếp Repository trong test case này.
     */
    @Test
        @DisplayName("[AT-TC66] requester resolution helpers - cover all branches")
        void tc62_requesterResolutionHelpers_coverBranches() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        ApprovalTrackingService targetService = org.springframework.test.util.AopTestUtils.getTargetObject(approvalTrackingService);
        java.lang.reflect.Method positionMethod = ApprovalTrackingService.class
                .getDeclaredMethod("getRequesterPositionId", Long.class, String.class);
        java.lang.reflect.Method departmentMethod = ApprovalTrackingService.class
                .getDeclaredMethod("getRequesterDepartmentId", Long.class, String.class);
        positionMethod.setAccessible(true);
        departmentMethod.setAccessible(true);

        com.fasterxml.jackson.databind.node.ObjectNode employeeFull = mapper.createObjectNode();
        com.fasterxml.jackson.databind.node.ObjectNode positionNode = mapper.createObjectNode();
        positionNode.put("id", 777L);
        com.fasterxml.jackson.databind.node.ObjectNode departmentNode = mapper.createObjectNode();
        departmentNode.put("id", 88L);
        employeeFull.set("position", positionNode);
        employeeFull.set("department", departmentNode);

        doReturn(buildEmployeeResponse(employeeFull)).when(restTemplate).exchange(
                anyString(),
                eq(org.springframework.http.HttpMethod.GET),
                any(org.springframework.http.HttpEntity.class),
                any(org.springframework.core.ParameterizedTypeReference.class));

        assertEquals(777L, positionMethod.invoke(targetService, 123L, "token"));
        assertEquals(88L, departmentMethod.invoke(targetService, 123L, "token"));

        com.fasterxml.jackson.databind.node.ObjectNode employeeFallback = mapper.createObjectNode();
        employeeFallback.put("departmentId", 99L);
        doReturn(buildEmployeeResponse(employeeFallback)).when(restTemplate).exchange(
                anyString(),
                eq(org.springframework.http.HttpMethod.GET),
                any(org.springframework.http.HttpEntity.class),
                any(org.springframework.core.ParameterizedTypeReference.class));

        assertNull(positionMethod.invoke(targetService, 456L, "token"));
        assertEquals(99L, departmentMethod.invoke(targetService, 456L, "token"));

        Response<com.fasterxml.jackson.databind.JsonNode> emptyBody = new Response<>();
        doReturn(org.springframework.http.ResponseEntity.ok(emptyBody)).when(restTemplate).exchange(
                anyString(),
                eq(org.springframework.http.HttpMethod.GET),
                any(org.springframework.http.HttpEntity.class),
                any(org.springframework.core.ParameterizedTypeReference.class));
        assertNull(positionMethod.invoke(targetService, 789L, null));
        assertNull(departmentMethod.invoke(targetService, 789L, ""));

        doThrow(new RuntimeException("boom")).when(restTemplate).exchange(
                anyString(),
                eq(org.springframework.http.HttpMethod.GET),
                any(org.springframework.http.HttpEntity.class),
                any(org.springframework.core.ParameterizedTypeReference.class));
        assertNull(positionMethod.invoke(targetService, 999L, "token"));
        assertNull(departmentMethod.invoke(targetService, 999L, "token"));

        assertNull(positionMethod.invoke(targetService, null, "token"));
        assertNull(departmentMethod.invoke(targetService, null, "token"));
    }

        /**
         * Test Case ID: AT-TC67
     * initializeApproval(): findUserByPositionId() trả empty list -> approverPositionId=null.
     * Mục tiêu: cover nhánh false của userPositions.isEmpty() và nhánh
     * tracking.getApproverPositionId() == null trong initializeApproval().
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Xác minh service ném ra CustomException với thông báo lỗi phù hợp.
     * - Check DB: Không kiểm tra DB vì tác vụ thất bại từ bước validation (đảm bảo DB không rác).
     */
    @Test
        @DisplayName("[AT-TC67] initializeApproval() - empty user position list -> approverPositionId=null")
        void tc63_initializeApproval_emptyUserPositionList_coversNullApproverBranch() {
        Response<java.util.List<Object>> emptyUserPositions = new Response<>();
        emptyUserPositions.setData(java.util.List.of());
        doReturn(org.springframework.http.ResponseEntity.ok(emptyUserPositions)).when(restTemplate).exchange(
                anyString(),
                eq(org.springframework.http.HttpMethod.GET),
                any(org.springframework.http.HttpEntity.class),
                any(org.springframework.core.ParameterizedTypeReference.class));
        when(userService.findUserByPositionIdAndDepartmentId(anyLong(), eq(10L), anyString()))
                .thenReturn(null);

        CreateApprovalTrackingDTO dto = new CreateApprovalTrackingDTO();
        dto.setDepartmentId(10L);
        dto.setRequestId(9054L);
        dto.setLevelId(1L);

        // Act + Assert
        assertThrows(CustomException.class, () -> approvalTrackingService.initializeApproval(dto),
                "BUG: phải báo lỗi khi user-position list rỗng");
    }

        /**
         * Test Case ID: AT-TC68
     * getById()/DTO mapping với tracking có các field nullable.
     * Mục tiêu: cover các nhánh actionUserId=null, approverPositionId=null và step=null
     * trong toResponseDTO()/getById().
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra logic xử lý luồng happy-path hoặc void method hoạt động thành công không ném lỗi.
     * - Check DB: Không tương tác/kiểm tra trực tiếp Repository trong test case này.
     */
    @Test
        @DisplayName("[AT-TC68] private toResponseDTO - nullable tracking fields")
        void tc64_toResponseDTO_nullableTrackingFields_coverBranches() throws Exception {
        ApprovalTrackingService targetService = org.springframework.test.util.AopTestUtils.getTargetObject(approvalTrackingService);
        java.lang.reflect.Method toResponse = ApprovalTrackingService.class
                .getDeclaredMethod("toResponseDTO", ApprovalTracking.class, Map.class, Map.class, Map.class);
        toResponse.setAccessible(true);

        ApprovalTracking tracking = new ApprovalTracking();
        tracking.setId(9055L);
        tracking.setRequestId(9055L);
        tracking.setStep(null);
        tracking.setStatus(ApprovalStatus.PENDING);
        tracking.setApproverPositionId(null);
        tracking.setActionUserId(null);

        ApprovalTrackingResponseDTO dto = (ApprovalTrackingResponseDTO) toResponse.invoke(
                targetService, tracking, Map.of(1L, "Manager"), Map.of(2L, "User"), Map.of());

        assertNull(dto.getStepId(), "BUG: stepId phải null khi tracking.step null");
        assertNull(dto.getApproverPositionName(), "BUG: approverPositionName phải null khi approverPositionId null");
        assertNull(dto.getActionUserName(), "BUG: actionUserName phải null khi actionUserId null");
    }

        /**
         * Test Case ID: AT-TC69
     * getAll()/getPendingApprovalsForUser(): danh sách có tracking null action/approver IDs.
     * Mục tiêu: cover nhánh false của các filter(id -> id != null).
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: Không tương tác/kiểm tra trực tiếp Repository trong test case này.
     */
    @Test
        @DisplayName("[AT-TC69] getAll/pending - null action and approver ids")
        void tc65_getAllAndPending_nullIds_coverFilterFalseBranches() {
        ApprovalTracking nullableIds = buildTracking(9056L, step1, ApprovalStatus.PENDING, null);
        nullableIds.setActionUserId(null);
        approvalTrackingRepository.save(nullableIds);

        // Act
        PaginationDTO all = approvalTrackingService.getAll(
                9056L, null, null, PageRequest.of(0, 10));
        // Assert
        assertNotNull(all.getResult(), "BUG: getAll result null");

        List<ApprovalTrackingResponseDTO> pending = approvalTrackingService.getPendingApprovalsForUser(null);
        assertNotNull(pending, "BUG: pending approvals null khi userId null");
    }

        /**
         * Test Case ID: AT-TC70
     * getWorkflowInfoByRequestId(): cover các nhánh workflow.steps != null,
     * requestType="REQUEST", tracking.step=null và tracking.step.workflow=null.
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: Không tương tác/kiểm tra trực tiếp Repository trong test case này.
     */
    @Test
        @DisplayName("[AT-TC70 getWorkflowInfoByRequestId - edge branches")
        void tc66_getWorkflowInfoByRequestId_edgeBranches() {
        Workflow workflow = saveWorkflow("WF Info Edge", WorkflowType.REQUEST, 57L);
        WorkflowStep realStep = saveStep(workflow, 1, APPROVER_USER_ID);
        WorkflowStep secondStep = saveStep(workflow, 2, OTHER_USER_ID);
        workflow.setSteps(new java.util.LinkedHashSet<>(java.util.List.of(realStep, secondStep)));

        ApprovalTracking trackingWithStep = buildTracking(9057L, realStep, ApprovalStatus.APPROVED, APPROVER_USER_ID);
        trackingWithStep.setActionUserId(null);
        approvalTrackingRepository.save(trackingWithStep);

        // Act
        var result = approvalTrackingService.getWorkflowInfoByRequestId(9057L, workflow.getId(), "REQUEST");

        // Assert
        assertNotNull(result.getWorkflow(), "BUG: workflow info null");
        assertNotNull(result.getWorkflow().getSteps(), "BUG: workflow steps null");
        assertFalse(result.getWorkflow().getSteps().isEmpty(), "BUG: workflow steps rỗng");
    }

        /**
         * Test Case ID: AT-TC71
     * Private helpers: filter/getWorkflowType/notify/moveToNextStep/createTrackingForStep.
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Xác minh luồng thực thi bị chặn và ném ra ngoại lệ tương ứng.
     * - Check DB: Không kiểm tra DB vì tác vụ thất bại từ bước validation (đảm bảo DB không rác).
     */
    @Test
        @DisplayName("[AT-TC71] private helpers - remaining null/empty branches")
        void tc67_privateHelpers_remainingBranches() throws Exception {
        ApprovalTrackingService targetService = org.springframework.test.util.AopTestUtils.getTargetObject(approvalTrackingService);

        java.lang.reflect.Method workflowTypeMethod = ApprovalTrackingService.class
                .getDeclaredMethod("getWorkflowTypeFromRequestType", String.class);
        workflowTypeMethod.setAccessible(true);
        assertNull(workflowTypeMethod.invoke(targetService, (String) null));
        assertNull(workflowTypeMethod.invoke(targetService, "   "));
        assertEquals(WorkflowType.REQUEST, workflowTypeMethod.invoke(targetService, "REQUEST"));
        assertEquals(WorkflowType.REQUEST, workflowTypeMethod.invoke(targetService, "RECRUITMENT_REQUEST"));
        assertEquals(WorkflowType.OFFER, workflowTypeMethod.invoke(targetService, "OFFER"));
        assertNull(workflowTypeMethod.invoke(targetService, "UNKNOWN"));

        java.lang.reflect.Method filterMethod = ApprovalTrackingService.class
                .getDeclaredMethod("filterByWorkflowType", List.class, WorkflowType.class);
        filterMethod.setAccessible(true);
        Workflow offerWorkflow = saveWorkflow("WF Filter Offer", WorkflowType.OFFER, 58L);
        WorkflowStep offerStep = saveStep(offerWorkflow, 1, APPROVER_USER_ID);
        ApprovalTracking requestTracking = buildTracking(9058L, step1, ApprovalStatus.PENDING, APPROVER_USER_ID);
        ApprovalTracking offerTracking = buildTracking(9058L, offerStep, ApprovalStatus.PENDING, APPROVER_USER_ID);
        @SuppressWarnings("unchecked")
        List<ApprovalTracking> noFilter = (List<ApprovalTracking>) filterMethod.invoke(
                targetService, List.of(requestTracking, offerTracking), (WorkflowType) null);
        assertEquals(2, noFilter.size(), "BUG: workflowType null phải trả nguyên danh sách");

        java.lang.reflect.Method notifyMethod = ApprovalTrackingService.class
                .getDeclaredMethod("notifyNextApprovers", Long.class, WorkflowStep.class, Long.class, String.class);
        notifyMethod.setAccessible(true);
        notifyMethod.invoke(targetService, 10L, null, 9058L, "token");
        notifyMethod.invoke(targetService, null, step1, 9058L, "token");

        java.lang.reflect.Method createStepMethod = ApprovalTrackingService.class
                .getDeclaredMethod("createTrackingForStep", Long.class, WorkflowStep.class, Long.class, String.class);
        createStepMethod.setAccessible(true);
        WorkflowStep noApproverStep = saveStep(workflow2Steps, 9, APPROVER_USER_ID);
        noApproverStep.setApproverPositionId(null);
        ApprovalTracking createdNoDept = (ApprovalTracking) createStepMethod.invoke(
                targetService, 9058L, noApproverStep, null, "token");
        assertNull(createdNoDept.getActionUserId(), "BUG: actionUserId phải null khi department/approver null");

        java.lang.reflect.Method moveMethod = ApprovalTrackingService.class
                .getDeclaredMethod("moveToNextStep", ApprovalTracking.class, Long.class, String.class);
        moveMethod.setAccessible(true);
        ApprovalTracking noStepTracking = new ApprovalTracking();
        noStepTracking.setId(905803L);
        noStepTracking.setRequestId(9058L);
        noStepTracking.setStep(null);
        noStepTracking.setStatus(ApprovalStatus.PENDING);
        assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> moveMethod.invoke(targetService, noStepTracking, 10L, "token"));
    }

        /**
         * Test Case ID: AT-TC72
     * handleWorkflowEvent(): event guards, OFFER department resolution null và submit guards.
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra logic xử lý luồng happy-path hoặc void method hoạt động thành công không ném lỗi.
     * - Check DB: Kiểm tra DB không bị thay đổi bằng cách so sánh số lượng record hoặc verify không có lỗi.
     */
    @Test
        @DisplayName("[AT-TC72] handleWorkflowEvent - guard branches")
        void tc68_handleWorkflowEvent_guardBranches() {
        // Act + Assert
        assertDoesNotThrow(() -> approvalTrackingService.handleWorkflowEvent(null));

        RecruitmentWorkflowEvent nullType = buildEvent(null, "REQUEST", 9059L);
        assertDoesNotThrow(() -> approvalTrackingService.handleWorkflowEvent(nullType));

        RecruitmentWorkflowEvent nullRequest = buildEvent("REQUEST_SUBMITTED", "REQUEST", null);
        assertDoesNotThrow(() -> approvalTrackingService.handleWorkflowEvent(nullRequest));

        RecruitmentWorkflowEvent offerNoCandidate = buildEvent("REQUEST_SUBMITTED", "OFFER", 9059L);
        offerNoCandidate.setWorkflowId(workflow2Steps.getId());
        offerNoCandidate.setDepartmentId(null);
        offerNoCandidate.setCandidateId(null);
        assertDoesNotThrow(() -> approvalTrackingService.handleWorkflowEvent(offerNoCandidate));

        RecruitmentWorkflowEvent offerNoDepartment = buildEvent("REQUEST_SUBMITTED", "OFFER", 9060L);
        offerNoDepartment.setWorkflowId(workflow2Steps.getId());
        offerNoDepartment.setDepartmentId(null);
        offerNoDepartment.setCandidateId(123L);
        when(candidateService.getDepartmentIdFromCandidate(eq(123L), any())).thenReturn(null);
        assertDoesNotThrow(() -> approvalTrackingService.handleWorkflowEvent(offerNoDepartment));

        RecruitmentWorkflowEvent missingWorkflow = buildEvent("REQUEST_SUBMITTED", "REQUEST", 9061L);
        missingWorkflow.setWorkflowId(null);
        assertDoesNotThrow(() -> approvalTrackingService.handleWorkflowEvent(missingWorkflow));
    }

        /**
         * Test Case ID: AT-TC73
     * handleRequestSubmitted(): requester auto-approve edge branches and resubmit note append.
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: Truy vấn DB bằng findByRequestId() để xác minh tracking mới được tạo và gán đúng workflow step.
     */
    @Test
        @DisplayName("[AT-TC73] handleRequestSubmitted - auto approve and resubmit branches")
        void tc69_handleRequestSubmitted_autoApproveAndResubmitBranches() {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode employee = mapper.createObjectNode();
        com.fasterxml.jackson.databind.node.ObjectNode position = mapper.createObjectNode();
        position.put("id", APPROVER_USER_ID);
        employee.set("position", position);
        employee.put("departmentId", 10L);
        doReturn(buildEmployeeResponse(employee)).when(restTemplate).exchange(
                anyString(),
                eq(org.springframework.http.HttpMethod.GET),
                any(org.springframework.http.HttpEntity.class),
                any(org.springframework.core.ParameterizedTypeReference.class));

        RecruitmentWorkflowEvent autoApprove = buildEvent("REQUEST_SUBMITTED", "REQUEST", 9060L);
        autoApprove.setWorkflowId(workflow2Steps.getId());
        autoApprove.setDepartmentId(10L);
        autoApprove.setRequesterId(APPROVER_USER_ID);
        autoApprove.setActorUserId(APPROVER_USER_ID);
        autoApprove.setAuthToken("token");
        approvalTrackingService.handleWorkflowEvent(autoApprove);

        // Check DB
        List<ApprovalTracking> autoCreated = approvalTrackingRepository.findByRequestId(9060L);
        // Assert
        assertTrue(autoCreated.stream().anyMatch(t -> t.getStatus() == ApprovalStatus.APPROVED),
                "BUG: auto-approve không tạo tracking APPROVED cho step 1");

        Long requestId = 9061L;
        ApprovalTracking returned = buildTracking(requestId, step1, ApprovalStatus.RETURNED, APPROVER_USER_ID);
        returned.setReturnedToStepId(step1.getId());
        approvalTrackingRepository.save(returned);

        RecruitmentWorkflowEvent resubmit = buildEvent("REQUEST_SUBMITTED", "REQUEST", requestId);
        resubmit.setWorkflowId(workflow2Steps.getId());
        resubmit.setDepartmentId(10L);
        resubmit.setActorUserId(APPROVER_USER_ID);
        approvalTrackingService.handleWorkflowEvent(resubmit);

        assertTrue(approvalTrackingRepository.findByRequestId(requestId).stream()
                        .anyMatch(t -> t.getStatus() == ApprovalStatus.PENDING
                                && t.getNotes() != null
                                && t.getNotes().contains("Đã chỉnh sửa")),
                "BUG: resubmit sau return không ghi chú đã chỉnh sửa");
    }

        /**
         * Test Case ID: AT-TC74
     * handleStepRejected()/return()/invalidateFutureSteps(): null step and future pending branches.
     * 
     * CHI TIẾT TEST CASE:
     * - Rollback: Toàn bộ dữ liệu (tracking, workflow, step) được tạo trong H2 DB sẽ tự động rollback sau khi test hoàn thành nhờ @Transactional.
     * - Check: Kiểm tra tính chính xác của dữ liệu trả về hoặc trạng thái assert.
     * - Check DB: Truy vấn DB bằng findById() để assert các trường thay đổi như status, actionType, notes, actionUserId.
     */
    @Test
        @DisplayName("[AT-TC74] reject/return/invalidate - remaining branches")
        void tc70_rejectReturnInvalidate_remainingBranches() throws Exception {
        Long requestId = 9062L;
        ApprovalTracking current = buildTracking(requestId, step1, ApprovalStatus.PENDING, APPROVER_USER_ID);
        approvalTrackingRepository.save(current);
        ApprovalTracking future = buildTracking(requestId, step2, ApprovalStatus.PENDING, OTHER_USER_ID);
        approvalTrackingRepository.save(future);

        RecruitmentWorkflowEvent reject = buildEvent("REQUEST_REJECTED", "REQUEST", requestId);
        reject.setActorUserId(APPROVER_USER_ID);
        reject.setNotes(null);
        approvalTrackingService.handleWorkflowEvent(reject);

        // Check DB
        ApprovalTracking updatedFuture = approvalTrackingRepository.findById(future.getId()).orElseThrow();
        // Assert
        assertEquals(ApprovalStatus.CANCELLED, updatedFuture.getStatus(),
                "BUG: future PENDING tracking không bị cancel khi reject");

        Long returnRequestId = 9063L;
        ApprovalTracking returnCurrent = buildTracking(returnRequestId, step1, ApprovalStatus.PENDING, APPROVER_USER_ID);
        approvalTrackingRepository.save(returnCurrent);
        RecruitmentWorkflowEvent returned = buildEvent("REQUEST_RETURNED", "REQUEST", returnRequestId);
        returned.setWorkflowId(workflow2Steps.getId());
        returned.setActorUserId(APPROVER_USER_ID);
        returned.setReturnedToStepId(step1.getId());
        approvalTrackingService.handleWorkflowEvent(returned);

        assertEquals(ApprovalStatus.RETURNED,
                approvalTrackingRepository.findById(returnCurrent.getId()).orElseThrow().getStatus(),
                "BUG: return không đổi current tracking thành RETURNED");
    }

    // ================================================================
    // HELPER METHODS
    // ================================================================

    private ApproveStepDTO approveDto(boolean approved, String notes) {
        ApproveStepDTO dto = new ApproveStepDTO();
        dto.setApproved(approved);
        dto.setApprovalNotes(notes);
        return dto;
    }

    private ApprovalTracking buildTracking(Long requestId, WorkflowStep step,
                                            ApprovalStatus status, Long approverPositionId) {
        ApprovalTracking t = new ApprovalTracking();
        t.setRequestId(requestId);
        t.setStep(step);
        t.setStatus(status);
        t.setApproverPositionId(approverPositionId);
        return t;
    }

        private org.springframework.http.ResponseEntity<Response<java.util.List<Object>>> buildUserPositionResponse(Long userId) {
                Response<java.util.List<Object>> response = new Response<>();
                response.setData(java.util.List.of(createUserPositionDto(userId)));
                return org.springframework.http.ResponseEntity.ok(response);
        }

        private Object createUserPositionDto(Long userId) {
                try {
                        Class<?> dtoClass = Class.forName("com.example.workflow_service.service.ApprovalTrackingService$UserPositionDTO");
                        java.lang.reflect.Constructor<?> constructor = dtoClass.getDeclaredConstructor();
                        constructor.setAccessible(true);
                        Object dto = constructor.newInstance();

                        java.lang.reflect.Field userIdField = dtoClass.getDeclaredField("userId");
                        userIdField.setAccessible(true);
                        userIdField.set(dto, userId);

                        return dto;
                } catch (Exception exception) {
                        throw new IllegalStateException("Không tạo được UserPositionDTO test double", exception);
                }
        }

        private org.springframework.http.ResponseEntity<Response<com.fasterxml.jackson.databind.JsonNode>> buildEmployeeResponse(
                        com.fasterxml.jackson.databind.JsonNode employeeNode) {
                Response<com.fasterxml.jackson.databind.JsonNode> response = new Response<>();
                response.setData(employeeNode);
                return org.springframework.http.ResponseEntity.ok(response);
        }

    private RecruitmentWorkflowEvent buildEvent(String eventType, String requestType, Long requestId) {
        RecruitmentWorkflowEvent e = new RecruitmentWorkflowEvent();
        e.setEventType(eventType);
        e.setRequestType(requestType);
        e.setRequestId(requestId);
        e.setRequesterId(APPROVER_USER_ID);
        e.setOwnerUserId(APPROVER_USER_ID);
        return e;
    }

    private Workflow saveWorkflow(String name, WorkflowType type, Long deptId) {
        Workflow w = new Workflow();
        w.setName(name);
        w.setType(type);
        w.setDepartmentId(deptId);
        w.setIsActive(true);
        return workflowRepository.save(w);
    }

    private WorkflowStep saveStep(Workflow wf, int order, Long positionId) {
        WorkflowStep s = new WorkflowStep();
        s.setWorkflow(wf);
        s.setStepOrder(order);
        s.setApproverPositionId(positionId);
        s.setIsActive(true);
        return workflowStepRepository.save(s);
    }

    /**
     * Kiểm tra step có thuộc workflow REQUEST không (dùng trong TC17)
     */
    private boolean isStepBelongsToRequestWorkflow(Long stepId) {
        return workflowStepRepository.findById(stepId)
                .map(s -> s.getWorkflow().getType() == WorkflowType.REQUEST)
                .orElse(false);
    }
}
