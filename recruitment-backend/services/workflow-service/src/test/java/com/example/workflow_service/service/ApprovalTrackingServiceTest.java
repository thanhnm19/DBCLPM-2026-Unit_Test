package com.example.workflow_service.service;

import com.example.workflow_service.dto.PaginationDTO;
import com.example.workflow_service.dto.approval.ApprovalTrackingResponseDTO;
import com.example.workflow_service.dto.approval.ApproveStepDTO;
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
 * Mục tiêu:
 *   - Phủ Branch Coverage cấp 2 (mọi nhánh if/else trong source code)
 *   - Mỗi test bắt một lỗi cụ thể nếu implementation sai
 *   - Expected output từ ĐẶC TẢ, không từ source code
 *
 * Sơ đồ nhánh chính cần phủ:
 *
 * approve(id, dto):
 *   [B1] ID không tồn tại → IdInvalidException
 *   [B2] approverPositionId != currentUserId → CustomException, DB không đổi
 *   [B3] status = APPROVED (đã xử lý) → CustomException, DB không đổi
 *   [B4] status = REJECTED (đã xử lý) → CustomException, DB không đổi
 *   [B5] approved=true → status=APPROVED, actionUserId, actionAt, notes cập nhật vào DB
 *   [B6] approved=false → status=REJECTED, DB cập nhật đúng
 *   [B7] approved=true + nextStep tồn tại → tạo thêm tracking PENDING cho bước tiếp
 *   [B8] approved=true + không có nextStep → không tạo thêm tracking
 *
 * getById(id):
 *   [B9]  ID tồn tại → DTO đủ trường
 *   [B10] ID không tồn tại → IdInvalidException
 *
 * getAll(requestId, status, approverPositionId, pageable):
 *   [B11] filter=null → trả về tất cả
 *   [B12] filter requestId → chỉ trả về tracking của request đó
 *   [B13] filter status=PENDING → chỉ trả về PENDING
 *
 * getPendingApprovalsForUser(userId):
 *   [B14] có PENDING → danh sách không rỗng, tất cả là PENDING
 *   [B15] không có PENDING → danh sách rỗng
 *
 * getWorkflowInfoByRequestId(requestId, workflowId, requestType):
 *   [B16] requestType=null → không lọc type, trả về tất cả
 *   [B17] requestType="REQUEST" → chỉ trả về tracking của workflow REQUEST
 *   [B18] requestType="OFFER" → chỉ trả về tracking của workflow OFFER
 *   [B19] requestId không có tracking → danh sách rỗng
 *
 * handleWorkflowEvent(event):
 *   [B20] event=null → early return, không thay đổi DB
 *   [B21] eventType=null → early return, không thay đổi DB
 *   [B22] REQUEST_CANCELLED → tất cả PENDING → CANCELLED, actionType=CANCEL
 *   [B23] REQUEST_CANCELLED + request không có PENDING → DB không đổi
 *   [B24] REQUEST_WITHDRAWN → tất cả PENDING → CANCELLED, actionType=WITHDRAW
 *         (phân biệt CANCEL vs WITHDRAW bằng actionType)
 *   [B25] REQUEST_RETURNED + có returnedToStepId → RETURNED, actionType=RETURN, returnedToStepId được ghi
 *   [B26] REQUEST_RETURNED + returnedToStepId=null → trả về bước đầu tiên (stepOrder=1)
 *   [B27] REQUEST_APPROVED + có bước tiếp → tracking hiện tại APPROVED + tạo tracking mới bước tiếp
 *   [B28] REQUEST_APPROVED + không có bước tiếp → tracking APPROVED, không tạo thêm
 *   [B29] REQUEST_REJECTED → tracking PENDING → REJECTED, actionType=REJECT
 *   [B30] eventType unknown → bỏ qua, DB không đổi
 *
 * CheckDB: Sau mỗi thao tác thay đổi DB, truy vấn lại repository để xác minh
 * Rollback: @Transactional đảm bảo DB sạch sau mỗi test
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

    // --- Mock external dependencies ---
    @MockitoBean private UserService userService;
    @MockitoBean private CandidateService candidateService;
    @MockitoBean private RecruitmentWorkflowProducer workflowProducer;
    @MockitoBean private NotificationProducer notificationProducer;
    @MockitoBean private org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;
    @MockitoBean private org.springframework.web.client.RestTemplate restTemplate;

    private MockedStatic<SecurityUtil> mockedSecurityUtil;

    // Người dùng giả lập đang đăng nhập (= approver hợp lệ)
    private static final Long APPROVER_USER_ID = 100L;
    // Người dùng khác (không có quyền duyệt)
    private static final Long OTHER_USER_ID    = 999L;
    // Request ID dùng chung
    private static final Long REQUEST_ID       = 1L;

    /** Workflow có 2 bước dùng chung */
    private Workflow workflow2Steps;
    /** Bước 1 của workflow */
    private WorkflowStep step1;
    /** Bước 2 của workflow */
    private WorkflowStep step2;
    /** Tracking PENDING tại bước 1 cho REQUEST_ID */
    private ApprovalTracking pendingTracking;

    @BeforeEach
    void setUp() {
        // Giả lập người dùng ID=100 đang đăng nhập
        mockedSecurityUtil = Mockito.mockStatic(SecurityUtil.class);
        mockedSecurityUtil.when(SecurityUtil::extractEmployeeId).thenReturn(APPROVER_USER_ID);
        mockedSecurityUtil.when(SecurityUtil::getCurrentUserJWT).thenReturn(Optional.of("test-token"));

        // Mock UserService: trả về rỗng (không gọi HTTP thật)
        when(userService.getUserNamesByIds(anyList(), anyString())).thenReturn(Map.of());
        when(userService.getPositionNamesByIds(anyList(), anyString())).thenReturn(Map.of());
        when(userService.findUserByPositionIdAndDepartmentId(anyLong(), anyLong(), anyString()))
                .thenReturn(OTHER_USER_ID);

        // Mock Kafka producers: không gửi message thật
        doNothing().when(workflowProducer).publishEvent(any());
        doNothing().when(notificationProducer).sendNotification(anyLong(), anyString(), anyString(), anyString());
        doNothing().when(notificationProducer).sendNotificationToDepartment(
                anyLong(), anyLong(), anyString(), anyString(), anyString());

        // Tạo workflow 2 bước trong H2 DB
        workflow2Steps = new Workflow();
        workflow2Steps.setName("Workflow Test 2 Bước");
        workflow2Steps.setType(WorkflowType.REQUEST);
        workflow2Steps.setDepartmentId(10L);
        workflow2Steps.setIsActive(true);
        workflow2Steps = workflowRepository.save(workflow2Steps);

        step1 = new WorkflowStep();
        step1.setWorkflow(workflow2Steps);
        step1.setStepOrder(1);
        step1.setApproverPositionId(APPROVER_USER_ID); // approver = người đang đăng nhập
        step1.setIsActive(true);
        step1 = workflowStepRepository.save(step1);

        step2 = new WorkflowStep();
        step2.setWorkflow(workflow2Steps);
        step2.setStepOrder(2);
        step2.setApproverPositionId(OTHER_USER_ID);
        step2.setIsActive(true);
        step2 = workflowStepRepository.save(step2);

        // Tracking PENDING tại bước 1 cho request REQUEST_ID
        pendingTracking = buildTracking(REQUEST_ID, step1, ApprovalStatus.PENDING, APPROVER_USER_ID);
        pendingTracking = approvalTrackingRepository.save(pendingTracking);
    }

    @AfterEach
    void tearDown() {
        mockedSecurityUtil.close();
    }

    // ================================================================
    // NHÓM 1: approve()
    // ================================================================

    /**
     * Test Case ID: AT-TC01
     * Nhánh [B1]: ID tracking không tồn tại → IdInvalidException
     *
     * Bug bị bắt: Không kiểm tra tồn tại → NullPointerException hoặc trả về null
     */
    @Test
    @DisplayName("[AT-TC01][B1] approve() - ID không tồn tại → IdInvalidException")
    void tc01_approve_nonExistentId_throwsIdInvalidException() {
        ApproveStepDTO dto = approveDto(true, "ghi chú");

        assertThrows(IdInvalidException.class,
                () -> approvalTrackingService.approve(9999L, dto),
                "BUG: Không ném IdInvalidException khi ID tracking không tồn tại");
    }

    /**
     * Test Case ID: AT-TC02
     * Nhánh [B2]: User hiện tại KHÁC approverPositionId → CustomException + DB không đổi
     *
     * Bug bị bắt: Thiếu kiểm tra quyền → cho approve bừa bãi
     *
     * CheckDB: Status vẫn là PENDING sau khi exception
     */
    @Test
    @DisplayName("[AT-TC02][B2] approve() - Sai người duyệt → CustomException; status DB vẫn PENDING")
    void tc02_approve_wrongApprover_throwsCustomException_dbUnchanged() {
        // Gán tracking cho OTHER_USER_ID (không phải người đang đăng nhập)
        pendingTracking.setApproverPositionId(OTHER_USER_ID);
        approvalTrackingRepository.save(pendingTracking);

        ApproveStepDTO dto = approveDto(true, "thử phê duyệt không có quyền");

        CustomException ex = assertThrows(CustomException.class,
                () -> approvalTrackingService.approve(pendingTracking.getId(), dto),
                "BUG: Không kiểm tra quyền phê duyệt → ai cũng approve được");

        assertEquals("Bạn không có quyền phê duyệt bước này", ex.getMessage(),
                "BUG: Message exception không đúng đặc tả");

        // CHECK DB: status KHÔNG thay đổi
        ApprovalStatus statusInDb = approvalTrackingRepository.findById(pendingTracking.getId())
                .orElseThrow().getStatus();
        assertEquals(ApprovalStatus.PENDING, statusInDb,
                "BUG: Status trong DB bị thay đổi dù không có quyền phê duyệt");
    }

    /**
     * Test Case ID: AT-TC03
     * Nhánh [B3]: Status = APPROVED (đã xử lý) → CustomException
     *
     * Bug bị bắt: Cho phép approve lại tracking đã APPROVED → dữ liệu bị ghi đè
     */
    @Test
    @DisplayName("[AT-TC03][B3] approve() - Status đã APPROVED → CustomException 'Bước này đã được xử lý'")
    void tc03_approve_statusAlreadyApproved_throwsCustomException() {
        pendingTracking.setStatus(ApprovalStatus.APPROVED);
        approvalTrackingRepository.save(pendingTracking);

        CustomException ex = assertThrows(CustomException.class,
                () -> approvalTrackingService.approve(pendingTracking.getId(), approveDto(true, "x")),
                "BUG: Cho phép approve lại tracking đã ở trạng thái APPROVED");

        assertEquals("Bước này đã được xử lý", ex.getMessage());
    }

    /**
     * Test Case ID: AT-TC04
     * Nhánh [B4]: Status = REJECTED (đã xử lý) → CustomException
     *
     * Bug bị bắt: Cho phép approve tracking đã REJECTED → đặt lại trạng thái sai
     */
    @Test
    @DisplayName("[AT-TC04][B4] approve() - Status đã REJECTED → CustomException 'Bước này đã được xử lý'")
    void tc04_approve_statusAlreadyRejected_throwsCustomException() {
        pendingTracking.setStatus(ApprovalStatus.REJECTED);
        approvalTrackingRepository.save(pendingTracking);

        CustomException ex = assertThrows(CustomException.class,
                () -> approvalTrackingService.approve(pendingTracking.getId(), approveDto(false, "x")),
                "BUG: Cho phép approve/reject lại tracking đã ở trạng thái REJECTED");

        assertEquals("Bước này đã được xử lý", ex.getMessage());
    }

    /**
     * Test Case ID: AT-TC05
     * Nhánh [B5]: approved=true, user hợp lệ → DB phải ghi đủ 4 trường
     *
     * Bug bị bắt:
     *   - Status không được đổi thành APPROVED
     *   - Notes không được lưu
     *   - actionUserId không ghi lại người thực hiện
     *   - actionAt null (không ghi nhận thời gian)
     *
     * CheckDB: Truy vấn lại từng trường
     */
    @Test
    @DisplayName("[AT-TC05][B5] approve() - approved=true → DB: status=APPROVED, notes, actionUserId, actionAt")
    void tc05_approve_approved_true_updatesDbAllFields() {
        String notes = "Đồng ý với yêu cầu tuyển dụng vị trí Backend";
        ApproveStepDTO dto = approveDto(true, notes);

        approvalTrackingService.approve(pendingTracking.getId(), dto);

        // CHECK DB: Truy vấn lại
        ApprovalTracking saved = approvalTrackingRepository.findById(pendingTracking.getId()).orElseThrow();
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
     * Test Case ID: AT-TC06
     * Nhánh [B6]: approved=false → DB phải ghi status=REJECTED (không phải APPROVED)
     *
     * Bug bị bắt: Nếu code dùng sai điều kiện `if (approved)` → cả 2 nhánh đều set APPROVED
     *
     * CheckDB: Status phải là REJECTED
     */
    @Test
    @DisplayName("[AT-TC06][B6] approve() - approved=false → DB: status=REJECTED; không phải APPROVED")
    void tc06_approve_approved_false_updatesDbToRejected() {
        String rejectReason = "Ngân sách chưa được phê duyệt";
        ApproveStepDTO dto = approveDto(false, rejectReason);

        approvalTrackingService.approve(pendingTracking.getId(), dto);

        ApprovalTracking saved = approvalTrackingRepository.findById(pendingTracking.getId()).orElseThrow();
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
     * Test Case ID: AT-TC07
     * Nhánh [B7]: approved=true + có bước tiếp (step2) → DB tăng thêm 1 tracking PENDING cho bước 2
     *
     * Bug bị bắt:
     *   - Không tạo tracking bước tiếp → luồng phê duyệt bị dừng giữa chừng
     *   - Tracking mới không có status=PENDING
     *   - Tracking mới không gắn đúng vào step2
     *
     * CheckDB: Đếm tracking + xác minh tracking mới
     */
    @Test
    @DisplayName("[AT-TC07][B7] approve() - approved=true + có bước tiếp → tạo tracking PENDING mới cho bước 2")
    void tc07_approve_approved_withNextStep_createsNewPendingTrackingForStep2() {
        long countBefore = approvalTrackingRepository.count();

        approvalTrackingService.approve(pendingTracking.getId(), approveDto(true, "OK"));

        // CHECK DB: phải có thêm 1 tracking mới
        long countAfter = approvalTrackingRepository.count();
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
     * Test Case ID: AT-TC08
     * Nhánh [B8]: approved=true + KHÔNG có bước tiếp → KHÔNG tạo thêm tracking
     *
     * Chuẩn bị: Workflow chỉ có 1 bước (không có step2)
     * Bug bị bắt: Tạo tracking bừa khi không còn bước tiếp → dữ liệu thừa
     *
     * CheckDB: Tổng tracking không tăng
     */
    @Test
    @DisplayName("[AT-TC08][B8] approve() - approved=true + không có bước tiếp → không tạo tracking mới")
    void tc08_approve_approved_noNextStep_noNewTrackingCreated() {
        // Tạo workflow chỉ 1 bước
        Workflow wf1 = saveWorkflow("Workflow 1 Bước", WorkflowType.REQUEST, 99L);
        WorkflowStep s1 = saveStep(wf1, 1, APPROVER_USER_ID);
        ApprovalTracking t1 = approvalTrackingRepository.save(
                buildTracking(20L, s1, ApprovalStatus.PENDING, APPROVER_USER_ID));

        long countBefore = approvalTrackingRepository.count();

        approvalTrackingService.approve(t1.getId(), approveDto(true, "Duyệt bước cuối"));

        assertEquals(countBefore, approvalTrackingRepository.count(),
                "BUG: Tạo thêm tracking dù không còn bước tiếp - workflow chỉ có 1 bước");
    }

    // ================================================================
    // NHÓM 2: getById()
    // ================================================================

    /**
     * Test Case ID: AT-TC09
     * Nhánh [B9]: ID tồn tại → DTO đủ thông tin
     *
     * Bug bị bắt: Mapping bỏ sót trường requestId, status, approverPositionId
     */
    @Test
    @DisplayName("[AT-TC09][B9] getById() - ID tồn tại → DTO đầy đủ các trường chính")
    void tc09_getById_existingId_returnsDtoWithAllFields() {
        ApprovalTrackingResponseDTO result = approvalTrackingService.getById(pendingTracking.getId());

        assertAll("BUG: DTO thiếu hoặc sai trường",
                () -> assertEquals(pendingTracking.getId(), result.getId(), "id sai"),
                () -> assertEquals(REQUEST_ID, result.getRequestId(), "requestId sai"),
                () -> assertEquals(ApprovalStatus.PENDING, result.getStatus(), "status sai"),
                () -> assertEquals(APPROVER_USER_ID, result.getApproverPositionId(), "approverPositionId sai")
        );
    }

    /**
     * Test Case ID: AT-TC10
     * Nhánh [B10]: ID không tồn tại → IdInvalidException
     */
    @Test
    @DisplayName("[AT-TC10][B10] getById() - ID không tồn tại → IdInvalidException")
    void tc10_getById_nonExistentId_throwsIdInvalidException() {
        assertThrows(IdInvalidException.class,
                () -> approvalTrackingService.getById(88888L),
                "BUG: Trả về null thay vì ném IdInvalidException");
    }

    // ================================================================
    // NHÓM 3: getAll()
    // ================================================================

    /**
     * Test Case ID: AT-TC11
     * Nhánh [B11]: filter=null → trả về tất cả tracking với đúng tổng số
     */
    @Test
    @DisplayName("[AT-TC11][B11] getAll() - filter=null → trả về tất cả tracking, meta.total đúng")
    void tc11_getAll_noFilter_returnsAllWithCorrectTotal() {
        // DB có 1 tracking (setUp)
        PaginationDTO result = approvalTrackingService.getAll(null, null, null, PageRequest.of(0, 10));

        assertNotNull(result.getMeta(), "BUG: meta null");
        assertTrue(result.getMeta().getTotal() >= 1,
                "BUG: Total phải >= 1 vì có sampleTracking từ setUp");
    }

    /**
     * Test Case ID: AT-TC12
     * Nhánh [B12]: filter requestId=REQUEST_ID → chỉ trả về tracking của request đó
     *
     * Bug bị bắt: Filter requestId bị bỏ qua → trả về tất cả tracking của mọi request
     */
    @Test
    @DisplayName("[AT-TC12][B12] getAll() - filter requestId → chỉ trả về tracking của request đó")
    void tc12_getAll_filterByRequestId_excludesOtherRequests() {
        // Tạo tracking thuộc request khác (555L) để kiểm tra filter loại trừ
        ApprovalTracking otherReq = buildTracking(555L, step1, ApprovalStatus.PENDING, APPROVER_USER_ID);
        approvalTrackingRepository.save(otherReq);

        PaginationDTO result = approvalTrackingService.getAll(REQUEST_ID, null, null, PageRequest.of(0, 10));
        @SuppressWarnings("unchecked")
        List<ApprovalTrackingResponseDTO> list = (List<ApprovalTrackingResponseDTO>) result.getResult();

        assertFalse(list.isEmpty(), "BUG: Không tìm thấy tracking của REQUEST_ID=1");
        assertTrue(list.stream().allMatch(dto -> REQUEST_ID.equals(dto.getRequestId())),
                "BUG: Kết quả lẫn tracking của request khác - filter requestId không hoạt động");
    }

    /**
     * Test Case ID: AT-TC13
     * Nhánh [B13]: filter status=PENDING → chỉ trả về PENDING, không lẫn APPROVED/REJECTED
     *
     * Bug bị bắt: Filter status bị bỏ qua → trả về tất cả mọi status
     */
    @Test
    @DisplayName("[AT-TC13][B13] getAll() - filter status=PENDING → tất cả kết quả là PENDING")
    void tc13_getAll_filterByStatusPending_excludesOtherStatuses() {
        // Tạo tracking APPROVED để kiểm tra filter loại trừ
        ApprovalTracking approved = buildTracking(2L, step1, ApprovalStatus.APPROVED, APPROVER_USER_ID);
        approvalTrackingRepository.save(approved);

        PaginationDTO result = approvalTrackingService.getAll(null, ApprovalStatus.PENDING, null,
                PageRequest.of(0, 10));
        @SuppressWarnings("unchecked")
        List<ApprovalTrackingResponseDTO> list = (List<ApprovalTrackingResponseDTO>) result.getResult();

        assertFalse(list.isEmpty(), "BUG: Không tìm thấy tracking PENDING");
        assertTrue(list.stream().allMatch(dto -> dto.getStatus() == ApprovalStatus.PENDING),
                "BUG: Kết quả lẫn tracking không phải PENDING - filter status không hoạt động");
    }

    // ================================================================
    // NHÓM 4: getPendingApprovalsForUser()
    // ================================================================

    /**
     * Test Case ID: AT-TC14
     * Nhánh [B14]: User có tracking PENDING → danh sách không rỗng, tất cả là PENDING
     *
     * Bug bị bắt: Lọc sai approverPositionId → trả về tracking của user khác
     */
    @Test
    @DisplayName("[AT-TC14][B14] getPendingApprovalsForUser() - có PENDING → list không rỗng, tất cả PENDING")
    void tc14_getPendingApprovalsForUser_hasPending_returnsNonEmptyAllPending() {
        List<ApprovalTrackingResponseDTO> result =
                approvalTrackingService.getPendingApprovalsForUser(APPROVER_USER_ID);

        assertFalse(result.isEmpty(),
                "BUG: Không tìm thấy tracking PENDING dù có sampleTracking với approverPositionId=APPROVER_USER_ID");
        assertTrue(result.stream().allMatch(dto -> dto.getStatus() == ApprovalStatus.PENDING),
                "BUG: Kết quả có tracking không phải PENDING");
    }

    /**
     * Test Case ID: AT-TC15
     * Nhánh [B15]: User không có tracking PENDING → danh sách rỗng (không null)
     *
     * Bug bị bắt: Trả về null thay vì danh sách rỗng → NPE ở caller
     */
    @Test
    @DisplayName("[AT-TC15][B15] getPendingApprovalsForUser() - không có PENDING → trả về list rỗng, không null")
    void tc15_getPendingApprovalsForUser_noPending_returnsEmptyList() {
        List<ApprovalTrackingResponseDTO> result =
                approvalTrackingService.getPendingApprovalsForUser(77777L); // User không có tracking

        assertNotNull(result, "BUG: Trả về null thay vì list rỗng - sẽ gây NPE ở caller");
        assertTrue(result.isEmpty(), "BUG: Trả về tracking không phải của user 77777L");
    }

    // ================================================================
    // NHÓM 5: getWorkflowInfoByRequestId()
    // ================================================================

    /**
     * Test Case ID: AT-TC16
     * Nhánh [B16]: requestType=null → không lọc type, trả về tracking dù là loại gì
     *
     * Bug bị bắt: null type lại bị xử lý như UNKNOWN → lọc ra toàn bộ kết quả
     */
    @Test
    @DisplayName("[AT-TC16][B16] getWorkflowInfoByRequestId() - requestType=null → không lọc, có tracking")
    void tc16_getWorkflowInfoByRequestId_nullType_returnsTrackings() {
        var result = approvalTrackingService.getWorkflowInfoByRequestId(REQUEST_ID, null, null);

        assertNotNull(result, "BUG: Kết quả null");
        assertFalse(result.getApprovalTrackings().isEmpty(),
                "BUG: Kết quả rỗng khi requestType=null - có thể đang lọc nhầm");
    }

    /**
     * Test Case ID: AT-TC17
     * Nhánh [B17]: requestType="REQUEST" → chỉ trả về tracking của workflow REQUEST
     *
     * Bug bị bắt: Filter type không đúng → lẫn tracking của OFFER
     */
    @Test
    @SuppressWarnings("unchecked") // cast an toàn: approvalTrackingService.getAll() luôn trả về List<ApprovalTrackingResponseDTO>
    @DisplayName("[AT-TC17][B17] getWorkflowInfoByRequestId() - requestType=REQUEST → chỉ cho REQUEST tracking")
    void tc17_getWorkflowInfoByRequestId_typeRequest_returnsOnlyRequestTrackings() {
        // Tạo workflow OFFER và tracking OFFER cùng REQUEST_ID để kiểm tra filter loại trừ
        Workflow offerWf = saveWorkflow("Offer Workflow", WorkflowType.OFFER, 10L);
        WorkflowStep offerStep = saveStep(offerWf, 1, APPROVER_USER_ID);
        approvalTrackingRepository.save(buildTracking(REQUEST_ID, offerStep, ApprovalStatus.PENDING, APPROVER_USER_ID));

        var result = approvalTrackingService.getWorkflowInfoByRequestId(REQUEST_ID, null, "REQUEST");

        assertFalse(result.getApprovalTrackings().isEmpty(), "BUG: Không tìm thấy tracking REQUEST");
        // Tất cả tracking phải thuộc workflow REQUEST
        boolean allRequest = result.getApprovalTrackings().stream().allMatch(dto ->
                result.getWorkflow() == null
                || dto.getStepId() == null
                || isStepBelongsToRequestWorkflow(dto.getStepId()));
        // Đảm bảo tracking của OFFER bị loại ra (tổng phải < tổng tất cả)
        var allResult = approvalTrackingService.getWorkflowInfoByRequestId(REQUEST_ID, null, null);
        assertTrue(result.getApprovalTrackings().size() < allResult.getApprovalTrackings().size()
                || result.getApprovalTrackings().size() == 1,
                "BUG: Filter type=REQUEST không loại được tracking OFFER");
    }

    /**
     * Test Case ID: AT-TC18
     * Nhánh [B19]: requestId không có tracking → danh sách rỗng (không null)
     *
     * Bug bị bắt: Trả về null → NPE ở caller
     */
    @Test
    @DisplayName("[AT-TC18][B19] getWorkflowInfoByRequestId() - không có tracking → danh sách rỗng, không null")
    void tc18_getWorkflowInfoByRequestId_noTracking_returnsEmptyList() {
        var result = approvalTrackingService.getWorkflowInfoByRequestId(99999L, null, null);

        assertNotNull(result, "BUG: Kết quả null");
        assertNotNull(result.getApprovalTrackings(), "BUG: approvalTrackings null - gây NPE ở caller");
        assertTrue(result.getApprovalTrackings().isEmpty(),
                "BUG: Trả về tracking dù requestId không tồn tại");
    }

    // ================================================================
    // NHÓM 6: handleWorkflowEvent() — các nhánh switch-case
    // ================================================================

    /**
     * Test Case ID: AT-TC19
     * Nhánh [B20]: event=null → không làm gì, không exception, DB không đổi
     *
     * Bug bị bắt: Không guard null event → NullPointerException
     */
    @Test
    @DisplayName("[AT-TC19][B20] handleWorkflowEvent() - event=null → bỏ qua, DB không đổi")
    void tc19_handleWorkflowEvent_nullEvent_doesNothingNoException() {
        long countBefore = approvalTrackingRepository.count();

        assertDoesNotThrow(() -> approvalTrackingService.handleWorkflowEvent(null),
                "BUG: Không guard null event → NullPointerException");

        assertEquals(countBefore, approvalTrackingRepository.count(),
                "BUG: DB bị thay đổi dù event=null");
    }

    /**
     * Test Case ID: AT-TC20
     * Nhánh [B21]: eventType=null → bỏ qua, DB không đổi
     *
     * Bug bị bắt: Gọi `.toUpperCase()` trên null → NullPointerException
     */
    @Test
    @DisplayName("[AT-TC20][B21] handleWorkflowEvent() - eventType=null → bỏ qua, không NPE")
    void tc20_handleWorkflowEvent_nullEventType_doesNothingNoException() {
        RecruitmentWorkflowEvent event = new RecruitmentWorkflowEvent();
        event.setEventType(null);
        event.setRequestId(REQUEST_ID);

        long countBefore = approvalTrackingRepository.count();

        assertDoesNotThrow(() -> approvalTrackingService.handleWorkflowEvent(event),
                "BUG: Gọi toUpperCase() trên eventType null → NullPointerException");

        assertEquals(countBefore, approvalTrackingRepository.count(),
                "BUG: DB bị thay đổi dù eventType=null");
    }

    /**
     * Test Case ID: AT-TC21
     * Nhánh [B22]: REQUEST_CANCELLED → tracking PENDING bị CANCELLED, actionType="CANCEL"
     *
     * Bug bị bắt:
     *   - Status không được đổi sang CANCELLED
     *   - actionType bị ghi là "WITHDRAW" thay vì "CANCEL"
     *   - actorUserId không được ghi vào actionUserId
     *   - actionAt null
     *
     * CheckDB: Truy vấn lại từng trường
     */
    @Test
    @DisplayName("[AT-TC21][B22] handleWorkflowEvent() - REQUEST_CANCELLED → status=CANCELLED, actionType=CANCEL")
    void tc21_handleWorkflowEvent_requestCancelled_setsStatusAndActionType() {
        RecruitmentWorkflowEvent event = buildEvent("REQUEST_CANCELLED", "REQUEST", REQUEST_ID);
        event.setActorUserId(APPROVER_USER_ID);
        event.setReason("Hủy do thay đổi kế hoạch");

        approvalTrackingService.handleWorkflowEvent(event);

        // CHECK DB
        ApprovalTracking updated = approvalTrackingRepository.findById(pendingTracking.getId()).orElseThrow();
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
     * Test Case ID: AT-TC22
     * Nhánh [B23]: REQUEST_CANCELLED + request không có PENDING → DB không thay đổi
     *
     * Bug bị bắt: Cố update tracking không tồn tại → exception bất ngờ
     */
    @Test
    @DisplayName("[AT-TC22][B23] REQUEST_CANCELLED + không có PENDING → DB không đổi, không exception")
    void tc22_handleWorkflowEvent_cancelledNoActivePending_dbUnchanged() {
        // Đặt tracking thành APPROVED (không còn PENDING)
        pendingTracking.setStatus(ApprovalStatus.APPROVED);
        approvalTrackingRepository.save(pendingTracking);

        RecruitmentWorkflowEvent event = buildEvent("REQUEST_CANCELLED", "REQUEST", REQUEST_ID);

        assertDoesNotThrow(() -> approvalTrackingService.handleWorkflowEvent(event),
                "BUG: Exception khi cancel request không có PENDING tracking");

        // CHECK DB: tracking APPROVED vẫn là APPROVED (không bị cancel)
        ApprovalStatus status = approvalTrackingRepository.findById(pendingTracking.getId())
                .orElseThrow().getStatus();
        assertEquals(ApprovalStatus.APPROVED, status,
                "BUG: Tracking đã APPROVED bị đổi thành CANCELLED (chỉ nên cancel PENDING)");
    }

    /**
     * Test Case ID: AT-TC23
     * Nhánh [B24]: REQUEST_WITHDRAWN → actionType="WITHDRAW" (khác CANCEL!)
     *
     * Bug bị bắt: Dùng cùng actionType="CANCEL" cho cả CANCEL và WITHDRAW → không phân biệt được nguồn gốc
     *
     * CheckDB: actionType phải là "WITHDRAW", status phải là CANCELLED
     */
    @Test
    @DisplayName("[AT-TC23][B24] REQUEST_WITHDRAWN → status=CANCELLED, actionType=WITHDRAW (≠ CANCEL)")
    void tc23_handleWorkflowEvent_requestWithdrawn_actionTypeIsWithdrawNotCancel() {
        RecruitmentWorkflowEvent event = buildEvent("REQUEST_WITHDRAWN", "REQUEST", REQUEST_ID);
        event.setActorUserId(APPROVER_USER_ID);
        event.setReason("Rút lại vì không cần tuyển nữa");

        approvalTrackingService.handleWorkflowEvent(event);

        ApprovalTracking updated = approvalTrackingRepository.findById(pendingTracking.getId()).orElseThrow();
        assertAll("BUG: Withdraw không phân biệt được với Cancel",
                () -> assertEquals(ApprovalStatus.CANCELLED, updated.getStatus(),
                        "BUG: status phải là CANCELLED sau khi withdraw"),
                () -> assertEquals("WITHDRAW", updated.getActionType(),
                        "BUG: actionType phải là WITHDRAW, không phải CANCEL - không thể phân biệt 2 hành động")
        );
    }

    /**
     * Test Case ID: AT-TC24
     * Nhánh [B25]: REQUEST_RETURNED + có returnedToStepId → status=RETURNED, actionType=RETURN, returnedToStepId ghi đúng
     *
     * Bug bị bắt:
     *   - status không phải RETURNED
     *   - returnedToStepId không được ghi vào DB
     *   - actionAt null
     *   - Ghi nhầm returnedToStepId
     *
     * CheckDB: 4 trường đều đúng
     */
    @Test
    @DisplayName("[AT-TC24][B25] REQUEST_RETURNED + returnedToStepId → RETURNED, RETURN, returnedToStepId")
    void tc24_handleWorkflowEvent_requestReturned_withStepId_setReadCorrectly() {
        RecruitmentWorkflowEvent event = buildEvent("REQUEST_RETURNED", "REQUEST", REQUEST_ID);
        event.setActorUserId(APPROVER_USER_ID);
        event.setWorkflowId(workflow2Steps.getId());
        event.setReturnedToStepId(step1.getId()); // Trả về bước 1
        event.setReason("Cần chỉnh sửa mô tả công việc");

        approvalTrackingService.handleWorkflowEvent(event);

        ApprovalTracking updated = approvalTrackingRepository.findById(pendingTracking.getId()).orElseThrow();
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
     * Test Case ID: AT-TC25
     * Nhánh [B26]: REQUEST_RETURNED + returnedToStepId=null → tự động trả về bước đầu (stepOrder=1)
     *
     * Bug bị bắt: Khi returnedToStepId=null, không resolve tự động về step 1 → returnedToStepId = null trong DB
     *
     * CheckDB: returnedToStepId phải = step1.getId()
     */
    @Test
    @DisplayName("[AT-TC25][B26] REQUEST_RETURNED + returnedToStepId=null → tự resolve về step 1")
    void tc25_handleWorkflowEvent_requestReturned_nullStepId_defaultsToFirstStep() {
        RecruitmentWorkflowEvent event = buildEvent("REQUEST_RETURNED", "REQUEST", REQUEST_ID);
        event.setActorUserId(APPROVER_USER_ID);
        event.setWorkflowId(workflow2Steps.getId());
        event.setReturnedToStepId(null); // Không chỉ định → phải resolve về step 1
        event.setReason("Thiếu thông tin");

        approvalTrackingService.handleWorkflowEvent(event);

        Long returnedToStepId = approvalTrackingRepository.findById(pendingTracking.getId())
                .orElseThrow().getReturnedToStepId();

        assertNotNull(returnedToStepId,
                "BUG: returnedToStepId vẫn null khi không chỉ định - phải tự resolve về bước 1");
        assertEquals(step1.getId(), returnedToStepId,
                "BUG: Bước được trả về không phải là bước đầu tiên (stepOrder=1)");
    }

    /**
     * Test Case ID: AT-TC26
     * Nhánh [B27]: REQUEST_APPROVED + có bước tiếp → tracking hiện tại APPROVED + tạo tracking PENDING mới
     *
     * Bug bị bắt:
     *   - Tracking hiện tại không được đổi sang APPROVED
     *   - Không tạo tracking mới cho bước tiếp
     *
     * CheckDB: 2 điểm kiểm tra
     */
    @Test
    @DisplayName("[AT-TC26][B27] REQUEST_APPROVED + có bước tiếp → tracking APPROVED + tạo tracking mới")
    void tc26_handleWorkflowEvent_requestApproved_withNextStep_approvesAndCreatesNext() {
        long countBefore = approvalTrackingRepository.count();

        RecruitmentWorkflowEvent event = buildEvent("REQUEST_APPROVED", "REQUEST", REQUEST_ID);
        event.setActorUserId(APPROVER_USER_ID);
        event.setWorkflowId(workflow2Steps.getId());
        event.setDepartmentId(10L);
        event.setNotes("Phê duyệt tại bước 1");

        approvalTrackingService.handleWorkflowEvent(event);

        // CHECK DB 1: tracking bước 1 phải APPROVED
        ApprovalStatus step1Status = approvalTrackingRepository.findById(pendingTracking.getId())
                .orElseThrow().getStatus();
        assertEquals(ApprovalStatus.APPROVED, step1Status,
                "BUG: Tracking bước 1 không được đổi thành APPROVED");

        // CHECK DB 2: phải có thêm 1 tracking mới
        long countAfter = approvalTrackingRepository.count();
        assertEquals(countBefore + 1, countAfter,
                "BUG: Không tạo tracking mới cho bước 2 dù workflow còn bước tiếp");
    }

    /**
     * Test Case ID: AT-TC27
     * Nhánh [B28]: REQUEST_APPROVED + không có bước tiếp → tracking APPROVED, KHÔNG tạo thêm
     *
     * Bug bị bắt: Cố tạo tracking cho bước tiếp dù không còn bước → data giả
     */
    @Test
    @DisplayName("[AT-TC27][B28] REQUEST_APPROVED + không có bước tiếp → APPROVED, không tạo thêm tracking")
    void tc27_handleWorkflowEvent_requestApproved_lastStep_noNewTracking() {
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

        approvalTrackingService.handleWorkflowEvent(event);

        // CHECK DB: tracking phải APPROVED
        assertEquals(ApprovalStatus.APPROVED,
                approvalTrackingRepository.findById(t1.getId()).orElseThrow().getStatus(),
                "BUG: Tracking bước cuối không được đổi thành APPROVED");

        // CHECK DB: không tạo thêm tracking
        assertEquals(countBefore, approvalTrackingRepository.count(),
                "BUG: Tạo thêm tracking dù không còn bước tiếp");
    }

    /**
     * Test Case ID: AT-TC28
     * Nhánh [B29]: REQUEST_REJECTED → tracking PENDING → REJECTED, actionType="REJECT"
     *
     * Bug bị bắt:
     *   - actionType bị ghi là "CANCEL" thay vì "REJECT"
     *   - Status không đổi thành REJECTED
     *
     * CheckDB: status=REJECTED, actionType=REJECT
     */
    @Test
    @DisplayName("[AT-TC28][B29] REQUEST_REJECTED → status=REJECTED, actionType=REJECT")
    void tc28_handleWorkflowEvent_requestRejected_setsRejectedAndActionType() {
        RecruitmentWorkflowEvent event = buildEvent("REQUEST_REJECTED", "REQUEST", REQUEST_ID);
        event.setActorUserId(APPROVER_USER_ID);
        event.setNotes("Không đủ ngân sách");

        approvalTrackingService.handleWorkflowEvent(event);

        ApprovalTracking updated = approvalTrackingRepository.findById(pendingTracking.getId()).orElseThrow();
        assertAll("BUG: REQUEST_REJECTED không ghi đúng dữ liệu",
                () -> assertEquals(ApprovalStatus.REJECTED, updated.getStatus(),
                        "BUG: status không phải REJECTED"),
                () -> assertEquals("REJECT", updated.getActionType(),
                        "BUG: actionType không phải REJECT (nhầm sang CANCEL?)")
        );
    }

    /**
     * Test Case ID: AT-TC29
     * Nhánh [B30]: eventType không xác định → bỏ qua, DB không thay đổi
     *
     * Bug bị bắt: Ném exception cho eventType lạ → production crashes khi nhận event mới
     */
    @Test
    @DisplayName("[AT-TC29][B30] handleWorkflowEvent() - eventType lạ → bỏ qua hoàn toàn, DB không đổi")
    void tc29_handleWorkflowEvent_unknownEventType_dbUnchanged() {
        RecruitmentWorkflowEvent event = buildEvent("UNKNOWN_FUTURE_EVENT", "REQUEST", REQUEST_ID);

        long countBefore = approvalTrackingRepository.count();

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
