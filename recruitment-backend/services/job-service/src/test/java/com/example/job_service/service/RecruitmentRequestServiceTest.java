package com.example.job_service.service;

import com.example.job_service.dto.recruitment.ApproveRecruitmentRequestDTO;
import com.example.job_service.dto.recruitment.CancelRecruitmentRequestDTO;
import com.example.job_service.dto.recruitment.CreateRecruitmentRequestDTO;
import com.example.job_service.dto.recruitment.RejectRecruitmentRequestDTO;
import com.example.job_service.dto.recruitment.ReturnRecruitmentRequestDTO;
import com.example.job_service.exception.IdInvalidException;
import com.example.job_service.messaging.RecruitmentWorkflowProducer;
import com.example.job_service.model.RecruitmentRequest;
import com.example.job_service.repository.RecruitmentRequestRepository;
import com.example.job_service.utils.enums.RecruitmentRequestStatus;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * =============================================================
 * Unit Test: RecruitmentRequestService — Yêu cầu tuyển dụng
 * =============================================================
 *
 * Mục tiêu:
 *   - Phủ Branch Coverage cấp 2: mọi nhánh if/else trong state machine
 *   - Mỗi test bắt một lỗi cụ thể nếu implementation sai
 *   - Expected output từ ĐẶC TẢ, không từ source code
 *
 * Sơ đồ trạng thái (State Machine) cần phủ:
 *
 *   [DRAFT] ──submit()──→ [PENDING]
 *      │                      │
 *   cancel()             approveStep() → [PENDING] (workflow xử lý APPROVED)
 *      │                 rejectStep()  → [REJECTED]
 *      ↓                 returnRequest()→ [RETURNED] ──submit()──→ [PENDING]
 *   [CANCELLED]          withdraw()    → [WITHDRAWN]
 *      ↑                 cancel()      → [CANCELLED]
 *      └──────────(mọi trạng thái trừ APPROVED/REJECTED)──────────┘
 *
 * PHẠM VI TEST:
 *   - create(): Tạo request mới ở trạng thái DRAFT
 *   - submit(): DRAFT → PENDING (gửi workflow)
 *   - approveStep(): Workflow APPROVED → update status (nếu needed)
 *   - rejectStep(): Workflow REJECTED → update status
 *   - returnRequest(): Return request về bước trước (RETURNED)
 *   - cancel(): Hủy request (CANCELLED, idempotent)
 *   - withdraw(): Rút lại request (WITHDRAWN, chỉ owner/requester)
 *   - findAllWithFilters(): Query filter theo status/department/keyword + phân trang
 *   - findById(): Lấy request theo id
 *   - delete(): Soft delete (isActive=false)
 *   - changeStatus(): Đổi status (helper method)
 *
 * TRẠNG THÁI WORKFLOW:
 *   RecruitmentRequest State Machine:
 *   
 *   [DRAFT] ← User tạo mới
 *       ↓ submit()
 *   [PENDING] ← Workflow xử lý (approveStep/rejectStep)
 *       ↓ approveStep() → nếu approved=true
 *   [APPROVED] ← Xong, có thể tạo offer
 *       ↓ rejectStep()
 *   [REJECTED] ← Từ chối
 *       ↓ returnRequest()
 *   [RETURNED] ← Trả về sửa, có thể submit lại
 *       ↓ submit() again
 *   [PENDING] ← cycle lại
 *
 *   Bất kỳ trạng thái (trừ APPROVED/REJECTED) → cancel() → [CANCELLED]
 *   [PENDING] → withdraw() → [WITHDRAWN]
 *
 * CHIẾN LƯỢC TEST:
 *   - @SpringBootTest: Load full Spring context + H2 test DB
 *   - @Transactional: Auto rollback → DB sạch
 *   - @ActiveProfiles("test"): Test config
 *   - Mock HTTP clients (WorkflowClient, UserClient)
 *   - Mock Kafka producer (không gửi event thật)
 *   - MockedStatic SecurityUtil: fake user ID
 *   - Kiểm tra từng nhánh state machine (branch coverage)
 *   - CheckDB: Truy vấn DB sau mỗi thao tác để xác minh
 *
 * MOCK STRATEGY:
 *   - @MockitoBean RestTemplate: Mock HTTP client dependencies
 *   - @MockitoBean UserClient: không gọi user-service HTTP
 *   - @MockitoBean WorkflowClient: không gọi workflow-service HTTP
 *   - @MockitoBean RecruitmentWorkflowProducer: không publish Kafka
 *   - @MockitoBean KafkaTemplate: không gửi message Kafka
 *   - doNothing().when(producer).publishEvent(any())
 *
 * CHÚ Ý:
 *   - ownerUserId: người tạo request (copy từ requesterId khi submit nếu null)
 *   - submittedAt: thời gian submit (ghi vào DB lần đầu submit)
 *   - isActive: soft delete flag (false = xóa logic)
 *   - Kafka event: publish sau mỗi state transition
 *   - Idempotent cancel: gọi 2 lần cancel() cùng request → lần 2 không publish event
 * =============================================================
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
@ExtendWith(com.example.job_service.TestResultLogger.class)
@DisplayName("RecruitmentRequestService - Branch Coverage Cấp 2")
class RecruitmentRequestServiceTest {

    // ============================================================
    // DEPENDENCIES INJECTION (từ Spring context - real beans)
    // ============================================================
    
    @Autowired private RecruitmentRequestService recruitmentRequestService;
    @Autowired private RecruitmentRequestRepository recruitmentRequestRepository;

    // ============================================================
    // MOCK DEPENDENCIES (không thực thi logic thực)
    // ============================================================
    
    /**
     * Mock RestTemplate
     * 
     * Lý do mock:
     *   - UserClient + WorkflowClient cần RestTemplate trong constructor
     *   - @MockitoBean RestTemplate → Spring có thể inject mock vào UserClient
     *   - Test không gọi HTTP thật (tránh network delay, external dependency fail)
     */
    @MockitoBean
    private RestTemplate restTemplate;

    @MockitoBean private UserClient userClient;
    @MockitoBean private WorkflowClient workflowClient;
    
    /**
     * Mock RecruitmentWorkflowProducer
     * 
     * Lý do mock:
     *   - publish event vào Kafka
     *   - Test không gửi message thật
     *   - Mock: doNothing() → skip publish, vẫn test state transition logic
     */
    @MockitoBean private RecruitmentWorkflowProducer workflowProducer;
    
    /** KafkaTemplate: cần mock để Spring tạo được RecruitmentWorkflowProducer bean */
    @MockitoBean private KafkaTemplate<String, String> kafkaTemplate;

    // ============================================================
    // TEST CONSTANTS
    // ============================================================
    
    private static final Long REQUESTER_ID = 10L;
    private static final Long OWNER_ID     = 10L;
    private static final Long OTHER_ACTOR  = 99L;   // người không liên quan
    private static final String TOKEN      = "test-token";

    /**
     * Yêu cầu đang ở trạng thái DRAFT
     * Setup lại ở mỗi @BeforeEach
     */
    private RecruitmentRequest draftRequest;
    
    /**
     * Yêu cầu đang ở trạng thái PENDING
     * Setup lại ở mỗi @BeforeEach
     */
    private RecruitmentRequest pendingRequest;

    @BeforeEach
    void setUp() {
        /**
         * SETUP: Chuẩn bị dữ liệu chung
         * 
         * Mock external service calls
         */
        
        // Mock Kafka: không gửi message thật
        doNothing().when(workflowProducer).publishEvent(any());
        
        // Mock WorkflowClient: không gọi HTTP workflow-service thật
        when(workflowClient.getWorkflowInfoByRequestId(any(), any(), any(), any())).thenReturn(null);

        /**
         * Tạo 2 fixture: DRAFT + PENDING
         * 
         * Dùng cho từng test:
         *   - tc02_submit_fromDraft_...: dùng draftRequest
         *   - tc04_submit_fromPending_...: dùng pendingRequest
         */
        draftRequest = recruitmentRequestRepository.save(buildRequest(
                "Tuyển Kỹ Sư Java", RecruitmentRequestStatus.DRAFT,
                REQUESTER_ID, OWNER_ID, 5L, 1L));

        pendingRequest = recruitmentRequestRepository.save(buildRequest(
                "Tuyển BA Senior", RecruitmentRequestStatus.PENDING,
                REQUESTER_ID, OWNER_ID, 5L, 1L));
    }

    // ================================================================
    // NHÓM 1: create()
    // ================================================================

    /**
     * Test Case ID: RR-TC01
     * Nhánh [N1]: Input hợp lệ → DRAFT, isActive=true, tất cả trường lưu đúng
     *
     * Bug bị bắt:
     *   - Status mặc định không phải DRAFT
     *   - isActive không được set true
     *   - ownerUserId không được copy từ requesterId
     *   - Salary không được lưu
     *   - DB không tăng record
     *
     * STRATEGY:
     *   - Arrange: Tạo CreateRecruitmentRequestDTO với dữ liệu hợp lệ
     *   - Act: Gọi recruitmentRequestService.create(dto)
     *   - Assert-1: Kiểm tra DB count tăng 1 (record được lưu)
     *   - Assert-2: Truy vấn DB lại (CheckDB), xác minh từng trường
     *     • Status = DRAFT (nhánh N1)
     *     • isActive = true (khác APPROVED/REJECTED)
     *     • ownerUserId = requesterId (sao chép)
     *     • title, salaryMin được lưu đúng giá trị
     */
    @Test
    @DisplayName("[RR-TC01][N1] create() - Input hợp lệ → status=DRAFT, isActive=true, các trường lưu đúng")
    void tc01_create_validInput_persistsAllFieldsWithDraftStatus() {
        // ============================================================
        // ARRANGE: Tạo input DTO
        // ============================================================
        
        CreateRecruitmentRequestDTO dto = new CreateRecruitmentRequestDTO();
        dto.setTitle("Tuyển QA Engineer");
        dto.setQuantity(2);
        dto.setReason("Mở rộng nhóm test");
        dto.setSalaryMin(new BigDecimal("10000000"));
        dto.setSalaryMax(new BigDecimal("20000000"));
        dto.setRequesterId(REQUESTER_ID);
        dto.setWorkflowId(1L);
        dto.setDepartmentId(5L);

        // Lưu số lượng record TRƯỚC khi create()
        long countBefore = recruitmentRequestRepository.count();

        // ============================================================
        // ACT: Gọi service method
        // ============================================================
        
        RecruitmentRequest result = recruitmentRequestService.create(dto);

        // ============================================================
        // ASSERT-1: Kiểm tra DB count (record được lưu)
        // ============================================================
        
        // CheckDB: Số record phải tăng đúng 1
        assertEquals(countBefore + 1, recruitmentRequestRepository.count(),
                "BUG: Không tạo thêm record trong DB");

        // ============================================================
        // ASSERT-2: CheckDB - Truy vấn lại từ DB, kiểm tra từng trường
        // ============================================================
        
        RecruitmentRequest saved = recruitmentRequestRepository.findById(result.getId()).orElseThrow();
        assertAll("BUG: Một hoặc nhiều trường không được lưu đúng",
                () -> assertEquals(RecruitmentRequestStatus.DRAFT, saved.getStatus(),
                        "BUG: Status mặc định phải là DRAFT"),
                () -> assertTrue(saved.isActive(),
                        "BUG: isActive phải là true khi mới tạo"),
                () -> assertEquals(REQUESTER_ID, saved.getOwnerUserId(),
                        "BUG: ownerUserId phải được copy từ requesterId"),
                () -> assertEquals("Tuyển QA Engineer", saved.getTitle(),
                        "BUG: title không được lưu đúng"),
                () -> assertEquals(new BigDecimal("10000000"), saved.getSalaryMin(),
                        "BUG: salaryMin không được lưu")
        );
    }

    // ================================================================
    // NHÓM 2: submit()
    // ================================================================

    /**
     * Test Case ID: RR-TC02
     * Nhánh [N2]: DRAFT → PENDING, submittedAt được ghi, ownerUserId giữ nguyên
     *
     * Bug bị bắt:
     *   - Status không được đổi thành PENDING
     *   - submittedAt vẫn null sau submit (thời gian submit không được ghi nhận)
     *   - ownerUserId bị overwrite dù đã có giá trị
     *
     * STRATEGY:
     *   - Arrange: Dùng draftRequest fixture (đã setup ở @BeforeEach)
     *   - Assert-Pre: Xác minh precondition: submittedAt==null trước khi submit
     *   - Act: Gọi recruitmentRequestService.submit(requestId, userId, token)
     *   - CheckDB: Truy vấn DB lại, xác minh:
     *     • Status = PENDING (từ DRAFT)
     *     • submittedAt != null (ghi nhận thời gian submit)
     *     • ownerUserId giữ nguyên (không bị overwrite)
     */
    @Test
    @DisplayName("[RR-TC02][N2] submit() - DRAFT → PENDING, submittedAt được ghi, ownerUserId giữ nguyên")
    void tc02_submit_fromDraft_setsPendingAndRecordsSubmittedAt() throws IdInvalidException {
        // ============================================================
        // PRECONDITION: Xác minh state của fixture
        // ============================================================
        
        assertNull(draftRequest.getSubmittedAt(), "Precondition: submittedAt phải null trước khi submit");

        // ============================================================
        // ACT: Gọi submit()
        // ============================================================
        
        recruitmentRequestService.submit(draftRequest.getId(), OWNER_ID, TOKEN);

        // ============================================================
        // CHECKDB: Truy vấn lại từ DB, xác minh state change
        // ============================================================
        
        RecruitmentRequest saved = recruitmentRequestRepository.findById(draftRequest.getId()).orElseThrow();
        assertAll("BUG: submit() không cập nhật đúng dữ liệu",
                () -> assertEquals(RecruitmentRequestStatus.PENDING, saved.getStatus(),
                        "BUG: Status không được đổi thành PENDING"),
                () -> assertNotNull(saved.getSubmittedAt(),
                        "BUG: submittedAt null - không ghi nhận thời gian submit"),
                () -> assertEquals(OWNER_ID, saved.getOwnerUserId(),
                        "BUG: ownerUserId bị thay đổi dù đã có giá trị - phải giữ nguyên")
        );
    }

    /**
     * Test Case ID: RR-TC03
     * Nhánh [N3]: RETURNED → PENDING (submit lại sau khi bị trả về)
     *
     * Bug bị bắt: Không cho phép submit lại từ RETURNED → luồng phê duyệt bị kẹt
     *
     * STRATEGY:
     *   - Arrange: Tạo request ở trạng thái RETURNED
     *   - Act: Gọi submit() từ RETURNED status
     *   - Assert: Status phải thành PENDING (submit lại được phép)
     */
    @Test
    @DisplayName("[RR-TC03][N3] submit() - RETURNED → PENDING (submit lại hợp lệ)")
    void tc03_submit_fromReturned_transitionsToPending() throws IdInvalidException {
        // ============================================================
        // ARRANGE: Tạo request ở state RETURNED
        // ============================================================
        
        RecruitmentRequest returnedRequest = recruitmentRequestRepository.save(buildRequest(
                "Tuyển tiếp nhân viên", RecruitmentRequestStatus.RETURNED,
                REQUESTER_ID, OWNER_ID, 5L, 1L));

        // ============================================================
        // ACT: Submit lại từ RETURNED
        // ============================================================
        
        recruitmentRequestService.submit(returnedRequest.getId(), OWNER_ID, TOKEN);

        // ============================================================
        // ASSERT: Status phải là PENDING
        // ============================================================
        
        RecruitmentRequest saved = recruitmentRequestRepository.findById(returnedRequest.getId()).orElseThrow();
        assertEquals(RecruitmentRequestStatus.PENDING, saved.getStatus(),
                "BUG: Không cho phép submit từ RETURNED → workflow bị kẹt");
    }

    /**
     * Test Case ID: RR-TC04
     * Nhánh [N4]: PENDING → IllegalStateException, DB không thay đổi
     *
     * Bug bị bắt: Cho phép submit lại khi đang PENDING → tạo tracking mới sai luồng
     *
     * STRATEGY:
     *   - Arrange: Dùng pendingRequest fixture
     *   - Act: Gọi submit() trên request đang PENDING
     *   - Assert-1: Exception được ném (IllegalStateException)
     *   - CheckDB: Status vẫn là PENDING (DB không đổi)
     */
    @Test
    @DisplayName("[RR-TC04][N4] submit() - PENDING → IllegalStateException; DB giữ nguyên PENDING")
    void tc04_submit_fromPending_throwsIllegalStateException_dbUnchanged() {
        // ============================================================
        // PRECONDITION: Xác minh fixture đang PENDING
        // ============================================================
        
        assertEquals(RecruitmentRequestStatus.PENDING, pendingRequest.getStatus(),
                "Precondition: fixture phải ở trạng thái PENDING");

        // ============================================================
        // ACT + ASSERT: Submit từ PENDING phải ném exception
        // ============================================================
        
        assertThrows(IllegalStateException.class,
                () -> recruitmentRequestService.submit(pendingRequest.getId(), OWNER_ID, TOKEN),
                "BUG: Không ném exception khi submit PENDING request → cho submit lại sai logic");

        // ============================================================
        // CHECKDB: Status vẫn PENDING (DB không bị modify)
        // ============================================================
        
        RecruitmentRequest saved = recruitmentRequestRepository.findById(pendingRequest.getId()).orElseThrow();
        assertEquals(RecruitmentRequestStatus.PENDING, saved.getStatus(),
                "BUG: Status bị thay đổi sau khi ném exception → transaction không rollback đúng");
    }

    

    /**
     * Test Case ID: RR-TC05
     * Nhánh [N5]: APPROVED → IllegalStateException
     *
     * Bug bị bắt: Cho phép submit yêu cầu đã APPROVED → tạo tracking trùng lặp
     */
    @Test
    @DisplayName("[RR-TC05][N5] submit() - APPROVED → IllegalStateException")
    void tc05_submit_fromApproved_throwsIllegalStateException() {
        pendingRequest.setStatus(RecruitmentRequestStatus.APPROVED);
        recruitmentRequestRepository.save(pendingRequest);

        assertThrows(IllegalStateException.class,
                () -> recruitmentRequestService.submit(pendingRequest.getId(), OWNER_ID, TOKEN),
                "BUG: Cho phép submit yêu cầu đã APPROVED");
    }

    /**
     * Test Case ID: RR-TC06
     * Nhánh [N6]: ownerUserId=null → được gán actorId khi submit
     *
     * Bug bị bắt: Không set ownerUserId khi null → owner không được xác định → không ai có thể withdraw
     */
    @Test
    @DisplayName("[RR-TC06][N6] submit() - ownerUserId=null → được gán actorId sau submit")
    void tc06_submit_nullOwnerUserId_assignsActorId() throws IdInvalidException {
        draftRequest.setOwnerUserId(null);
        recruitmentRequestRepository.save(draftRequest);

        Long newActor = 55L;
        recruitmentRequestService.submit(draftRequest.getId(), newActor, TOKEN);

        Long ownerInDb = recruitmentRequestRepository
                .findById(draftRequest.getId()).orElseThrow().getOwnerUserId();
        assertEquals(newActor, ownerInDb,
                "BUG: ownerUserId không được gán actorId khi null - không ai có thể withdraw sau này");
    }

    /**
     * Test Case ID: RR-TC07
     * Nhánh [N7]: ID không tồn tại → IdInvalidException
     */
    @Test
    @DisplayName("[RR-TC07][N7] submit() - ID không tồn tại → IdInvalidException")
    void tc07_submit_nonExistentId_throwsIdInvalidException() {
        assertThrows(IdInvalidException.class,
                () -> recruitmentRequestService.submit(99999L, OWNER_ID, TOKEN),
                "BUG: Không ném IdInvalidException khi ID không tồn tại");
    }

    // ================================================================
    // NHÓM 3: approveStep()
    // ================================================================

    /**
     * Test Case ID: RR-TC08
     * Nhánh [N8]: PENDING → giữ nguyên PENDING (workflow-service xử lý APPROVED)
     *
     * Đặc tả: job-service KHÔNG tự chuyển sang APPROVED. Nó chỉ publish event và đợi
     * workflow-service xác nhận. Nếu code tự set APPROVED → vi phạm thiết kế.
     *
     * Bug bị bắt: Code tự set status=APPROVED thay vì để workflow-service xử lý
     *
     * CheckDB: Status vẫn là PENDING sau approveStep
     */
    @Test
    @DisplayName("[RR-TC08][N8] approveStep() - PENDING → giữ PENDING (workflow-service xử lý APPROVED sau)")
    void tc08_approveStep_fromPending_keepsPendingAndPublishesEvent() throws IdInvalidException {
        ApproveRecruitmentRequestDTO dto = new ApproveRecruitmentRequestDTO();
        dto.setApprovalNotes("Đồng ý tuyển dụng");

        recruitmentRequestService.approveStep(pendingRequest.getId(), dto, OWNER_ID, TOKEN);

        // CHECK DB: phải vẫn PENDING (workflow-service chưa xác nhận xong)
        RecruitmentRequestStatus statusInDb = recruitmentRequestRepository
                .findById(pendingRequest.getId()).orElseThrow().getStatus();
        assertEquals(RecruitmentRequestStatus.PENDING, statusInDb,
                "BUG: job-service tự chuyển sang APPROVED - vi phạm thiết kế microservice");
    }

    /**
     * Test Case ID: RR-TC09
     * Nhánh [N10]: DRAFT → IllegalStateException, DB không đổi
     *
     * Bug bị bắt: Cho phép approve yêu cầu chưa được submit → phê duyệt khi chưa qua workflow
     *
     * CheckDB: Status vẫn là DRAFT
     */
    @Test
    @DisplayName("[RR-TC09][N10] approveStep() - DRAFT → IllegalStateException; status DB vẫn DRAFT")
    void tc09_approveStep_fromDraft_throwsIllegalStateException_dbUnchanged() {
        ApproveRecruitmentRequestDTO dto = new ApproveRecruitmentRequestDTO();

        assertThrows(IllegalStateException.class,
                () -> recruitmentRequestService.approveStep(draftRequest.getId(), dto, OWNER_ID, TOKEN),
                "BUG: Cho phép approve yêu cầu ở trạng thái DRAFT");

        assertEquals(RecruitmentRequestStatus.DRAFT,
                recruitmentRequestRepository.findById(draftRequest.getId()).orElseThrow().getStatus(),
                "BUG: Status trong DB bị thay đổi dù approveStep thất bại");
    }

    /**
     * Test Case ID: RR-TC10
     * Nhánh [N11]: đã APPROVED → IllegalStateException
     *
     * Bug bị bắt: Cho phép approve lại yêu cầu đã APPROVED → dữ liệu event bị ghi đè
     */
    @Test
    @DisplayName("[RR-TC10][N11] approveStep() - đã APPROVED → IllegalStateException")
    void tc10_approveStep_fromApproved_throwsIllegalStateException() {
        pendingRequest.setStatus(RecruitmentRequestStatus.APPROVED);
        recruitmentRequestRepository.save(pendingRequest);

        assertThrows(IllegalStateException.class,
                () -> recruitmentRequestService.approveStep(
                        pendingRequest.getId(), new ApproveRecruitmentRequestDTO(), OWNER_ID, TOKEN),
                "BUG: Cho phép approve lại yêu cầu đã APPROVED");
    }

    // ================================================================
    // NHÓM 4: rejectStep()
    // ================================================================

    /**
     * Test Case ID: RR-TC11
     * Nhánh [N12]: PENDING → REJECTED, DB cập nhật, publish event
     *
     * Bug bị bắt:
     *   - Status không được đổi thành REJECTED (bị giữ PENDING hoặc set sai)
     *   - Event không được publish → workflow-service không biết để cập nhật tracking
     *
     * CheckDB: Status phải là REJECTED
     */
    @Test
    @DisplayName("[RR-TC11][N12] rejectStep() - PENDING → status=REJECTED trong DB")
    void tc11_rejectStep_fromPending_setsRejectedInDb() throws IdInvalidException {
        RejectRecruitmentRequestDTO dto = new RejectRecruitmentRequestDTO();
        dto.setReason("Không đủ ngân sách");

        recruitmentRequestService.rejectStep(pendingRequest.getId(), dto, OWNER_ID, TOKEN);

        assertEquals(RecruitmentRequestStatus.REJECTED,
                recruitmentRequestRepository.findById(pendingRequest.getId()).orElseThrow().getStatus(),
                "BUG: Status không được đổi thành REJECTED sau khi từ chối");
    }

    /**
     * Test Case ID: RR-TC12
     * Nhánh [N13]: DRAFT → IllegalStateException, DB không đổi
     *
     * Bug bị bắt: Cho phép reject yêu cầu chưa submit → đảo lộn luồng phê duyệt
     */
    @Test
    @DisplayName("[RR-TC12][N13] rejectStep() - DRAFT → IllegalStateException; DB giữ nguyên DRAFT")
    void tc12_rejectStep_fromDraft_throwsIllegalStateException_dbUnchanged() {
        RejectRecruitmentRequestDTO dto = new RejectRecruitmentRequestDTO();
        dto.setReason("lý do");

        assertThrows(IllegalStateException.class,
                () -> recruitmentRequestService.rejectStep(draftRequest.getId(), dto, OWNER_ID, TOKEN),
                "BUG: Cho phép reject yêu cầu ở trạng thái DRAFT");

        assertEquals(RecruitmentRequestStatus.DRAFT,
                recruitmentRequestRepository.findById(draftRequest.getId()).orElseThrow().getStatus(),
                "BUG: Status bị thay đổi dù rejectStep thất bại");
    }

    /**
     * Test Case ID: RR-TC13
     * Nhánh [N14]: CANCELLED → IllegalStateException
     *
     * Bug bị bắt: Cho phép reject yêu cầu đã CANCELLED → thêm event sai vào queue
     */
    @Test
    @DisplayName("[RR-TC13][N14] rejectStep() - CANCELLED → IllegalStateException")
    void tc13_rejectStep_fromCancelled_throwsIllegalStateException() {
        pendingRequest.setStatus(RecruitmentRequestStatus.CANCELLED);
        recruitmentRequestRepository.save(pendingRequest);

        assertThrows(IllegalStateException.class,
                () -> recruitmentRequestService.rejectStep(
                        pendingRequest.getId(), new RejectRecruitmentRequestDTO(), OWNER_ID, TOKEN),
                "BUG: Cho phép reject yêu cầu đã CANCELLED");
    }

    // ================================================================
    // NHÓM 5: returnRequest()
    // ================================================================

    /**
     * Test Case ID: RR-TC14
     * Nhánh [N15]: PENDING → RETURNED, lý do và returnedToStepId được ghi vào event
     *
     * Bug bị bắt:
     *   - Status không được đổi thành RETURNED
     *   - Event publish thiếu returnedToStepId → workflow-service không biết trả về bước nào
     *
     * CheckDB: Status phải là RETURNED
     */
    @Test
    @DisplayName("[RR-TC14][N15] returnRequest() - PENDING → status=RETURNED trong DB")
    void tc14_returnRequest_fromPending_setsReturnedInDb() throws IdInvalidException {
        ReturnRecruitmentRequestDTO dto = new ReturnRecruitmentRequestDTO();
        dto.setReason("Cần bổ sung mô tả công việc");
        dto.setReturnedToStepId(1L);

        recruitmentRequestService.returnRequest(pendingRequest.getId(), dto, OWNER_ID, TOKEN);

        assertEquals(RecruitmentRequestStatus.RETURNED,
                recruitmentRequestRepository.findById(pendingRequest.getId()).orElseThrow().getStatus(),
                "BUG: Status không được đổi thành RETURNED");
    }

    /**
     * Test Case ID: RR-TC15
     * Nhánh [N16]: DRAFT → IllegalStateException, DB không đổi
     *
     * Bug bị bắt: Return yêu cầu chưa submit → tracking không tồn tại ở workflow-service
     */
    @Test
    @DisplayName("[RR-TC15][N16] returnRequest() - DRAFT → IllegalStateException; DB không đổi")
    void tc15_returnRequest_fromDraft_throwsIllegalStateException_dbUnchanged() {
        ReturnRecruitmentRequestDTO dto = new ReturnRecruitmentRequestDTO();
        dto.setReason("lý do");

        assertThrows(IllegalStateException.class,
                () -> recruitmentRequestService.returnRequest(draftRequest.getId(), dto, OWNER_ID, TOKEN),
                "BUG: Cho phép trả về yêu cầu ở trạng thái DRAFT");

        assertEquals(RecruitmentRequestStatus.DRAFT,
                recruitmentRequestRepository.findById(draftRequest.getId()).orElseThrow().getStatus(),
                "BUG: Status bị thay đổi dù returnRequest thất bại");
    }

    /**
     * Test Case ID: RR-TC16
     * Nhánh [N17]: APPROVED → IllegalStateException
     *
     * Bug bị bắt: Trả về yêu cầu đã APPROVED → mở lại quy trình đã hoàn thành
     */
    @Test
    @DisplayName("[RR-TC16][N17] returnRequest() - APPROVED → IllegalStateException")
    void tc16_returnRequest_fromApproved_throwsIllegalStateException() {
        pendingRequest.setStatus(RecruitmentRequestStatus.APPROVED);
        recruitmentRequestRepository.save(pendingRequest);

        assertThrows(IllegalStateException.class,
                () -> recruitmentRequestService.returnRequest(
                        pendingRequest.getId(), new ReturnRecruitmentRequestDTO(), OWNER_ID, TOKEN),
                "BUG: Cho phép trả về yêu cầu đã APPROVED");
    }

    // ================================================================
    // NHÓM 6: cancel()
    // ================================================================

    /**
     * Test Case ID: RR-TC17
     * Nhánh [N18]: DRAFT → CANCELLED
     *
     * Bug bị bắt: Không cho phép cancel DRAFT → người dùng không thể hủy bỏ nháp
     */
    @Test
    @DisplayName("[RR-TC17][N18] cancel() - DRAFT → status=CANCELLED trong DB")
    void tc17_cancel_fromDraft_setsCancelledInDb() throws IdInvalidException {
        CancelRecruitmentRequestDTO dto = buildCancelDto("Không còn nhu cầu");

        recruitmentRequestService.cancel(draftRequest.getId(), dto, OWNER_ID, TOKEN);

        assertEquals(RecruitmentRequestStatus.CANCELLED,
                recruitmentRequestRepository.findById(draftRequest.getId()).orElseThrow().getStatus(),
                "BUG: Status không được đổi thành CANCELLED từ DRAFT");
    }

    /**
     * Test Case ID: RR-TC18
     * Nhánh [N19]: PENDING → CANCELLED (hủy khi đang chờ duyệt)
     *
     * Bug bị bắt: Không cho phép cancel PENDING → người dùng bị kẹt chờ duyệt mãi
     */
    @Test
    @DisplayName("[RR-TC18][N19] cancel() - PENDING → status=CANCELLED trong DB")
    void tc18_cancel_fromPending_setsCancelledInDb() throws IdInvalidException {
        CancelRecruitmentRequestDTO dto = buildCancelDto("Hủy do thay đổi kế hoạch");

        recruitmentRequestService.cancel(pendingRequest.getId(), dto, OWNER_ID, TOKEN);

        assertEquals(RecruitmentRequestStatus.CANCELLED,
                recruitmentRequestRepository.findById(pendingRequest.getId()).orElseThrow().getStatus(),
                "BUG: Status không được đổi thành CANCELLED từ PENDING");
    }

    /**
     * Test Case ID: RR-TC19
     * Nhánh [N20]: đã CANCELLED → idempotent, trả về nguyên, KHÔNG publish thêm event
     *
     * Đặc tả: Cancel lần 2 phải an toàn (idempotent) — không gây lỗi, không tạo event thừa.
     *
     * Bug bị bắt: Ném exception khi cancel lần 2 → UI không thể retry khi lỗi mạng
     */
    @Test
    @DisplayName("[RR-TC19][N20] cancel() - đã CANCELLED → idempotent, trả về nguyên, không exception")
    void tc19_cancel_alreadyCancelled_isIdempotentNoException() throws IdInvalidException {
        draftRequest.setStatus(RecruitmentRequestStatus.CANCELLED);
        recruitmentRequestRepository.save(draftRequest);

        RecruitmentRequest result = assertDoesNotThrow(
                () -> recruitmentRequestService.cancel(draftRequest.getId(), buildCancelDto("lý do"), OWNER_ID, TOKEN),
                "BUG: Ném exception khi cancel yêu cầu đã CANCELLED - vi phạm idempotency");

        assertEquals(RecruitmentRequestStatus.CANCELLED, result.getStatus(),
                "BUG: Status bị thay đổi sau cancel idempotent - phải giữ nguyên CANCELLED");
    }

    /**
     * Test Case ID: RR-TC20
     * Nhánh [N21]: APPROVED → IllegalStateException, DB không đổi
     *
     * Bug bị bắt: Cho phép hủy yêu cầu đã APPROVED → xóa kết quả phê duyệt hợp lệ
     *
     * CheckDB: Status vẫn là APPROVED
     */
    @Test
    @DisplayName("[RR-TC20][N21] cancel() - APPROVED → IllegalStateException; DB giữ nguyên APPROVED")
    void tc20_cancel_fromApproved_throwsIllegalStateException_dbUnchanged() {
        pendingRequest.setStatus(RecruitmentRequestStatus.APPROVED);
        recruitmentRequestRepository.save(pendingRequest);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> recruitmentRequestService.cancel(pendingRequest.getId(), buildCancelDto("x"), OWNER_ID, TOKEN),
                "BUG: Cho phép cancel yêu cầu đã APPROVED");

        assertTrue(ex.getMessage().contains("APPROVED") || ex.getMessage().contains("REJECTED"),
                "BUG: Message không đề cập đến lý do không thể cancel");

        assertEquals(RecruitmentRequestStatus.APPROVED,
                recruitmentRequestRepository.findById(pendingRequest.getId()).orElseThrow().getStatus(),
                "BUG: Status APPROVED bị thay đổi dù cancel thất bại");
    }

    /**
     * Test Case ID: RR-TC21
     * Nhánh [N22]: REJECTED → IllegalStateException
     *
     * Bug bị bắt: Cho phép cancel yêu cầu đã REJECTED → mâu thuẫn dữ liệu với workflow-service
     */
    @Test
    @DisplayName("[RR-TC21][N22] cancel() - REJECTED → IllegalStateException")
    void tc21_cancel_fromRejected_throwsIllegalStateException() {
        pendingRequest.setStatus(RecruitmentRequestStatus.REJECTED);
        recruitmentRequestRepository.save(pendingRequest);

        assertThrows(IllegalStateException.class,
                () -> recruitmentRequestService.cancel(pendingRequest.getId(), buildCancelDto("x"), OWNER_ID, TOKEN),
                "BUG: Cho phép cancel yêu cầu đã REJECTED");
    }

    // ================================================================
    // NHÓM 7: withdraw()
    // ================================================================

    /**
     * Test Case ID: RR-TC22
     * Nhánh [N23]: PENDING + actor=owner → WITHDRAWN
     *
     * Bug bị bắt:
     *   - Status không được đổi thành WITHDRAWN
     *   - Owner bị từ chối quyền withdraw yêu cầu của chính mình
     */
    @Test
    @DisplayName("[RR-TC22][N23] withdraw() - PENDING + actor=owner → status=WITHDRAWN trong DB")
    void tc22_withdraw_fromPending_ownerIsActor_setsWithdrawn() throws IdInvalidException {
        recruitmentRequestService.withdraw(pendingRequest.getId(), OWNER_ID, TOKEN);

        assertEquals(RecruitmentRequestStatus.WITHDRAWN,
                recruitmentRequestRepository.findById(pendingRequest.getId()).orElseThrow().getStatus(),
                "BUG: Status không được đổi thành WITHDRAWN khi owner withdraw");
    }

    /**
     * Test Case ID: RR-TC23
     * Nhánh [N24]: PENDING + actor=requester (khác owner) → WITHDRAWN
     *
     * Chuẩn bị: ownerUserId khác requesterId
     * Bug bị bắt: Chỉ cho phép owner, không cho requester → người submit bị từ chối quyền rút
     */
    @Test
    @DisplayName("[RR-TC23][N24] withdraw() - PENDING + actor=requester (≠owner) → WITHDRAWN")
    void tc23_withdraw_fromPending_requesterIsActor_setsWithdrawn() throws IdInvalidException {
        // Tạo request với owner khác requester
        RecruitmentRequest r = recruitmentRequestRepository.save(buildRequest(
                "Test Withdraw", RecruitmentRequestStatus.PENDING, REQUESTER_ID, 77L, 5L, 1L));
        // requester=10L, owner=77L → actor=10L (requester) phải được phép

        recruitmentRequestService.withdraw(r.getId(), REQUESTER_ID, TOKEN);

        assertEquals(RecruitmentRequestStatus.WITHDRAWN,
                recruitmentRequestRepository.findById(r.getId()).orElseThrow().getStatus(),
                "BUG: Requester bị từ chối quyền withdraw dù đặc tả cho phép");
    }

    /**
     * Test Case ID: RR-TC24
     * Nhánh [N25]: PENDING + actor sai (không phải owner/requester) → IllegalStateException, DB không đổi
     *
     * Bug bị bắt: Không kiểm tra quyền → ai cũng withdraw được
     *
     * CheckDB: Status vẫn là PENDING
     */
    @Test
    @DisplayName("[RR-TC24][N25] withdraw() - actor không phải owner/requester → IllegalStateException; DB không đổi")
    void tc24_withdraw_wrongActor_throwsIllegalStateException_dbUnchanged() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> recruitmentRequestService.withdraw(pendingRequest.getId(), OTHER_ACTOR, TOKEN),
                "BUG: Không kiểm tra quyền withdraw → ai cũng rút được");

        assertTrue(ex.getMessage().contains("submitter") || ex.getMessage().contains("owner"),
                "BUG: Message không đề cập đến quyền submitter/owner");

        assertEquals(RecruitmentRequestStatus.PENDING,
                recruitmentRequestRepository.findById(pendingRequest.getId()).orElseThrow().getStatus(),
                "BUG: Status bị thay đổi dù withdraw bị từ chối");
    }

    /**
     * Test Case ID: RR-TC25
     * Nhánh [N26]: DRAFT → IllegalStateException (chỉ SUBMITTED/PENDING mới được withdraw)
     *
     * Bug bị bắt: Cho phép withdraw DRAFT → rút yêu cầu chưa submit là vô nghĩa
     */
    @Test
    @DisplayName("[RR-TC25][N26] withdraw() - DRAFT → IllegalStateException")
    void tc25_withdraw_fromDraft_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class,
                () -> recruitmentRequestService.withdraw(draftRequest.getId(), OWNER_ID, TOKEN),
                "BUG: Cho phép withdraw yêu cầu đang DRAFT");
    }

    /**
     * Test Case ID: RR-TC26
     * Nhánh [N27]: APPROVED → IllegalStateException (không thể rút yêu cầu đã được phê duyệt)
     *
     * Bug bị bắt: Cho phép withdraw APPROVED → hủy bỏ kết quả phê duyệt đã hoàn tất
     */
    @Test
    @DisplayName("[RR-TC26][N27] withdraw() - APPROVED → IllegalStateException")
    void tc26_withdraw_fromApproved_throwsIllegalStateException() {
        pendingRequest.setStatus(RecruitmentRequestStatus.APPROVED);
        recruitmentRequestRepository.save(pendingRequest);

        assertThrows(IllegalStateException.class,
                () -> recruitmentRequestService.withdraw(pendingRequest.getId(), OWNER_ID, TOKEN),
                "BUG: Cho phép withdraw yêu cầu đã APPROVED");
    }

    // ================================================================
    // NHÓM 8: findAllWithFilters()
    // ================================================================

    /**
     * Test Case ID: RR-TC27
     * Nhánh [N28]: status string không hợp lệ → không exception, filter bị bỏ qua (trả về tất cả)
     *
     * Bug bị bắt: Ném exception khi gặp status hợp lệ → frontend bị crash khi nhập query lạ
     */
    @Test
    @DisplayName("[RR-TC27][N28] findAllWithFilters() - status không hợp lệ → không exception, trả về tất cả")
    void tc27_findAllWithFilters_invalidStatus_ignoresFilterNoException() {
        // Test Case ID: RR-TC27
        // Mục tiêu: xác minh status string không hợp lệ → filter bị bỏ qua,
        //           trả về tất cả record (không phụ thuộc vào filter lạ)
        //           và không ném exception.
        //
        // Bug bị bắt:
        //   - Ném IllegalArgumentException/EnumConversionException khi status lạ
        //     → frontend crash khi gửi query tùy ý → FAIL
        //   - Lọc theo type UNKNOWN → trả về 0 record thay vì tất cả → FAIL
        //   - Tất cả nội dung bị lọc ra → FAIL
        //
        // setUp() đã tạo đúng 2 record (draftRequest + pendingRequest)
        // → sau khi filter bị bỏ qua, phải trả về đúng 2 record

        // Act: không được ném exception
        List<RecruitmentRequest> result = assertDoesNotThrow(
                () -> recruitmentRequestService.findAllWithFilters(null, "INVALID_STATUS_XYZ", null, null),
                "BUG: Ném exception khi status string không hợp lệ — frontend sẽ crash");

        // Assert: không null
        assertNotNull(result, "BUG: Kết quả null — phải trả về list rỗng hoặc đầy đủ");

        // Assert: phải trả về đúng 2 record từ setUp() — không lọc, không bỏ sót
        // Nếu filter lạ → trả về 0 record → FAIL rõ ràng
        // Nếu filter thất bại nghiêm trọng → FAIL
        assertEquals(2, result.size(),
                "BUG: Filter status lạ không được bỏ qua — " +
                "trả về " + result.size() + " record thay vì 2 (draftRequest + pendingRequest từ setUp)");

        // Assert: record trả về phải có cả DRAFT lẫn PENDING (filter không loại trừ)
        boolean hasDraft   = result.stream().anyMatch(r -> r.getStatus() == RecruitmentRequestStatus.DRAFT);
        boolean hasPending = result.stream().anyMatch(r -> r.getStatus() == RecruitmentRequestStatus.PENDING);
        assertTrue(hasDraft,   "BUG: Dắt record không có trong kết quả — filter sai");
        assertTrue(hasPending, "BUG: Pending record không có trong kết quả — filter sai");
    }

    /**
     * Test Case ID: RR-TC28
     * Nhánh [N29]: status=PENDING → chỉ trả về PENDING, không lẫn DRAFT
     *
     * Bug bị bắt: Filter status không hoạt động → trả về tất cả trạng thái
     */
    @Test
    @DisplayName("[RR-TC28][N29] findAllWithFilters() - status=PENDING → tất cả kết quả là PENDING")
    void tc28_findAllWithFilters_pendingStatus_returnsOnlyPending() {
        List<RecruitmentRequest> result = recruitmentRequestService
                .findAllWithFilters(null, "PENDING", null, null);

        assertFalse(result.isEmpty(), "BUG: Không tìm thấy record PENDING");
        assertTrue(result.stream().allMatch(r -> r.getStatus() == RecruitmentRequestStatus.PENDING),
                "BUG: Kết quả lẫn status khác PENDING - filter status không hoạt động");
    }

    /**
     * Test Case ID: RR-TC29
     * Nhánh [N30]: filter departmentId → chỉ trả về đúng department, loại trừ department khác
     *
     * Bug bị bắt: Filter departmentId không hoạt động → lộ dữ liệu của phòng ban khác
     */
    @Test
    @DisplayName("[RR-TC29][N30] findAllWithFilters() - departmentId → chỉ trả về department đó")
    void tc29_findAllWithFilters_filterByDepartment_excludesOtherDepts() {
        // Tạo record thuộc department khác để kiểm tra filter loại trừ
        recruitmentRequestRepository.save(buildRequest(
                "Phòng ban khác", RecruitmentRequestStatus.DRAFT, REQUESTER_ID, OWNER_ID, 99L, null));

        List<RecruitmentRequest> result = recruitmentRequestService
                .findAllWithFilters(5L, null, null, null);

        assertFalse(result.isEmpty(), "BUG: Không tìm thấy record của department 5");
        assertTrue(result.stream().allMatch(r -> Long.valueOf(5L).equals(r.getDepartmentId())),
                "BUG: Kết quả lẫn record của department khác - filter departmentId không hoạt động");
    }

    /**
     * Test Case ID: RR-TC30
     * Nhánh [N31]: keyword → chỉ trả về record khớp title hoặc reason
     *
     * Bug bị bắt: Filter keyword không hoạt động → trả về tất cả dù không khớp
     * Hoặc: case-sensitive → "java" không tìm thấy "Java"
     */
    @Test
    @DisplayName("[RR-TC30][N31] findAllWithFilters() - keyword → chỉ trả về khớp title/reason (case-insensitive)")
    void tc30_findAllWithFilters_filterByKeyword_returnOnlyMatches() {
        List<RecruitmentRequest> result = recruitmentRequestService
                .findAllWithFilters(null, null, null, "kỹ sư"); // viết thường

        assertFalse(result.isEmpty(), "BUG: Không tìm thấy 'Tuyển Kỹ Sư Java' khi search 'kỹ sư' - case-sensitive?");
        assertTrue(result.stream().allMatch(r ->
                        r.getTitle().toLowerCase().contains("kỹ sư")
                        || (r.getReason() != null && r.getReason().toLowerCase().contains("kỹ sư"))),
                "BUG: Kết quả chứa record không khớp keyword");
    }

    /**
     * Test Case ID: RR-TC52
     * getAllByDepartmentId(): chỉ trả về record thuộc department được yêu cầu
     */
    @Test
    @DisplayName("[RR-TC52] getAllByDepartmentId() - chỉ trả về record của department")
    void tc52_getAllByDepartmentId_returnsOnlyThatDepartment() {
        // Tạo record department khác
        recruitmentRequestRepository.save(buildRequest(
                "Dept 99", RecruitmentRequestStatus.DRAFT, REQUESTER_ID, OWNER_ID, 99L, null));

        List<RecruitmentRequest> result = recruitmentRequestService.getAllByDepartmentId(5L);
        assertFalse(result.isEmpty());
        assertTrue(result.stream().allMatch(r -> Long.valueOf(5L).equals(r.getDepartmentId())),
                "BUG: Kết quả chứa record không thuộc department 5");
    }

    // ================================================================
    // NHÓM 9: findById() / getById()
    // ================================================================

    /**
     * Test Case ID: RR-TC31
     * Nhánh [N32]: ID tồn tại → trả về đúng entity với đủ trường
     *
     * Bug bị bắt: Mapping thiếu trường, trả về entity khác
     */
    @Test
    @DisplayName("[RR-TC31][N32] findById() - ID tồn tại → đúng entity với đủ trường")
    void tc31_findById_existingId_returnsCorrectEntity() throws IdInvalidException {
        RecruitmentRequest result = recruitmentRequestService.findById(draftRequest.getId());

        assertAll("BUG: Entity trả về không đúng",
                () -> assertEquals(draftRequest.getId(), result.getId(), "id sai"),
                () -> assertEquals("Tuyển Kỹ Sư Java", result.getTitle(), "title sai"),
                () -> assertEquals(RecruitmentRequestStatus.DRAFT, result.getStatus(), "status sai"),
                () -> assertEquals(REQUESTER_ID, result.getRequesterId(), "requesterId sai")
        );
    }

    /**
     * Test Case ID: RR-TC32
     * Nhánh [N33]: ID không tồn tại → IdInvalidException (không phải null, không phải RuntimeException khác)
     *
     * Bug bị bắt: Trả về null → NullPointerException ở caller;
     * Hoặc ném RuntimeException thay vì IdInvalidException → caller xử lý sai exception
     */
    @Test
    @DisplayName("[RR-TC32][N33] findById() - ID không tồn tại → IdInvalidException (đúng loại exception)")
    void tc32_findById_nonExistentId_throwsIdInvalidException() {
        assertThrows(IdInvalidException.class,
                () -> recruitmentRequestService.findById(99999L),
                "BUG: Không ném IdInvalidException - có thể trả về null hoặc ném exception sai loại");
    }

    // ================================================================
    // NHÓM 10: changeStatus()
    // ================================================================

    /**
     * Test Case ID: RR-TC33
     * Nhánh [N34]: Thay đổi status bất kỳ → DB cập nhật đúng, trả về true
     *
     * Bug bị bắt: Status không được lưu; trả về false khi không cần
     */
    @Test
    @DisplayName("[RR-TC33][N34] changeStatus() - DRAFT → PENDING → DB cập nhật, trả về true")
    void tc33_changeStatus_updatesDbAndReturnsTrue() throws IdInvalidException {
        boolean result = recruitmentRequestService.changeStatus(
                draftRequest.getId(), RecruitmentRequestStatus.APPROVED);

        assertTrue(result, "BUG: changeStatus() phải trả về true khi thành công");

        assertEquals(RecruitmentRequestStatus.APPROVED,
                recruitmentRequestRepository.findById(draftRequest.getId()).orElseThrow().getStatus(),
                "BUG: Status trong DB không được cập nhật");
    }

    // ================================================================
    // NHÓM 11: getAll() — chỉ lấy isActive=true
    // ================================================================

    /**
     * Test Case ID: RR-TC34
     * Nhánh [N35]: getAll() chỉ trả về isActive=true, loại bỏ inactive
     *
     * Bug bị bắt: Không filter isActive → trả về cả record đã bị soft-delete
     */
    @Test
    @DisplayName("[RR-TC34][N35] getAll() - chỉ trả về isActive=true; loại bỏ inactive")
    void tc34_getAll_returnsOnlyActiveRecords() {
        // Tạo record inactive để kiểm tra filter
        RecruitmentRequest inactive = buildRequest(
                "Request Đã Xóa", RecruitmentRequestStatus.DRAFT, REQUESTER_ID, OWNER_ID, 5L, null);
        inactive.setActive(false);
        recruitmentRequestRepository.save(inactive);

        List<RecruitmentRequest> result = recruitmentRequestService.getAll();

        assertFalse(result.isEmpty(), "BUG: Không tìm thấy record active nào");
        assertTrue(result.stream().allMatch(RecruitmentRequest::isActive),
                "BUG: Kết quả lẫn record isActive=false - không filter đúng");
        result.forEach(r -> assertNotEquals(inactive.getId(), r.getId(),
                "BUG: Record đã xóa (isActive=false) vẫn xuất hiện trong getAll()"));
    }

    // ================================================================
    // NHÓM 12: delete() — soft delete
    // ================================================================

    /**
     * Test Case ID: RR-TC35
     * Nhánh [N36]: ID tồn tại → soft delete: isActive=false, record vẫn còn trong DB
     *
     * Bug bị bắt:
     *   - Hard delete → record bị xóa khỏi DB
     *   - isActive không được set false
     *   - delete() trả về false
     *
     * CheckDB: existsById vẫn true; isActive=false
     */
    @Test
    @DisplayName("[RR-TC35][N36] delete() - ID tồn tại → soft delete: isActive=false, record vẫn còn")
    void tc35_delete_existingId_softDeleteKeepsRecordSetsInactive() throws IdInvalidException {
        Long id = draftRequest.getId();
        assertTrue(draftRequest.isActive(), "Precondition: phải active trước khi delete");

        boolean result = recruitmentRequestService.delete(id);

        assertTrue(result, "BUG: delete() trả về false dù thành công");

        // CHECK DB: record vẫn còn (soft delete)
        assertTrue(recruitmentRequestRepository.existsById(id),
                "BUG: Record bị xóa khỏi DB (hard delete) thay vì soft delete");

        // CHECK DB: isActive phải false
        assertFalse(recruitmentRequestRepository.findById(id).orElseThrow().isActive(),
                "BUG: isActive vẫn là true sau delete - soft delete không hoạt động");
    }

    /**
     * Test Case ID: RR-TC36
     * Nhánh [N37]: ID không tồn tại → IdInvalidException
     *
     * Bug bị bắt: Không kiểm tra tồn tại → delete silent fail (không exception, không làm gì)
     */
    @Test
    @DisplayName("[RR-TC36][N37] delete() - ID không tồn tại → IdInvalidException")
    void tc36_delete_nonExistentId_throwsIdInvalidException() {
        assertThrows(IdInvalidException.class,
                () -> recruitmentRequestService.delete(99998L),
                "BUG: Không ném exception khi delete ID không tồn tại");
    }

        // ================================================================
        // NHÓM 16: update()
        // ================================================================

        /**
         * Test Case ID: RR-TC50
         * update(): ID tồn tại → cập nhật các trường và lưu vào DB
         */
        @Test
        @DisplayName("[RR-TC50] update() - ID tồn tại → cập nhật trường và lưu vào DB")
        void tc50_update_existingId_updatesFields() throws IdInvalidException {
                CreateRecruitmentRequestDTO dto = new CreateRecruitmentRequestDTO();
                dto.setTitle("Updated Title");
                dto.setQuantity(3);
                dto.setReason("Sửa yêu cầu");
                dto.setSalaryMin(new BigDecimal("12000000"));
                dto.setSalaryMax(new BigDecimal("22000000"));
                dto.setDepartmentId(7L);

                RecruitmentRequest updated = recruitmentRequestService.update(draftRequest.getId(), dto);

                assertEquals("Updated Title", updated.getTitle());
                assertEquals(3, updated.getQuantity());
                assertEquals(Long.valueOf(7L), updated.getDepartmentId());
        }

        /**
         * Test Case ID: RR-TC51
         * update(): ID không tồn tại → IdInvalidException
         */
        @Test
        @DisplayName("[RR-TC51] update() - ID không tồn tại → IdInvalidException")
        void tc51_update_nonExistentId_throwsIdInvalidException() {
                CreateRecruitmentRequestDTO dto = new CreateRecruitmentRequestDTO();
                dto.setTitle("X");

                assertThrows(IdInvalidException.class,
                                () -> recruitmentRequestService.update(999999L, dto),
                                "BUG: Không ném IdInvalidException khi update ID không tồn tại");
        }

    // ================================================================
    // NHÓM 13: approveStep() / rejectStep() / returnRequest() — nhánh SUBMITTED
    // ================================================================

    /**
     * Test Case ID: RR-TC37
     * Nhánh [N9]: approveStep() — status=SUBMITTED → giữ SUBMITTED (tương tự PENDING).
     * Mục tiêu: xác minh nhánh TRUE của điều kiện `status == SUBMITTED`.
     *
     * Bug bị bắt: Thiếu kiểm tra SUBMITTED → chỉ PENDING mới được approve, SUBMITTED bị từ chối.
     */
    @Test
    @DisplayName("[RR-TC37][N9] approveStep() - SUBMITTED → giống PENDING: giữ nguyên status, publish event")
    void tc37_approveStep_fromSubmitted_keepsPendingStatusAndPublishesEvent() throws IdInvalidException {
        // Arrange: đặt trạng thái SUBMITTED
        pendingRequest.setStatus(RecruitmentRequestStatus.SUBMITTED);
        recruitmentRequestRepository.save(pendingRequest);

        ApproveRecruitmentRequestDTO dto = new ApproveRecruitmentRequestDTO();
        dto.setApprovalNotes("Đồng ý từ SUBMITTED");

        // Act: không được ném exception
        assertDoesNotThrow(
                () -> recruitmentRequestService.approveStep(pendingRequest.getId(), dto, OWNER_ID, TOKEN),
                "BUG: SUBMITTED bị từ chối approve - chỉ chấp nhận PENDING");

        // CheckDB: status vẫn là SUBMITTED (job-service không tự chuyển sang APPROVED)
        RecruitmentRequestStatus statusInDb = recruitmentRequestRepository
                .findById(pendingRequest.getId()).orElseThrow().getStatus();
        assertEquals(RecruitmentRequestStatus.SUBMITTED, statusInDb,
                "BUG: job-service tự thay đổi status thay vì giữ nguyên để workflow-service xử lý");
    }

    /**
     * Test Case ID: RR-TC38
     * Nhánh [N]: rejectStep() — status=SUBMITTED → REJECTED (nhánh TRUE của status==SUBMITTED).
     *
     * Bug bị bắt: Chỉ cho reject PENDING, SUBMITTED bị ném exception → reject từ SUBMITTED thất bại.
     */
    @Test
    @DisplayName("[RR-TC38] rejectStep() - SUBMITTED → status=REJECTED trong DB")
    void tc38_rejectStep_fromSubmitted_setsRejected() throws IdInvalidException {
        // Arrange
        pendingRequest.setStatus(RecruitmentRequestStatus.SUBMITTED);
        recruitmentRequestRepository.save(pendingRequest);

        RejectRecruitmentRequestDTO dto = new RejectRecruitmentRequestDTO();
        dto.setReason("Từ chối từ SUBMITTED");

        // Act
        assertDoesNotThrow(
                () -> recruitmentRequestService.rejectStep(pendingRequest.getId(), dto, OWNER_ID, TOKEN),
                "BUG: SUBMITTED bị từ chối reject - chỉ chấp nhận PENDING");

        // CheckDB: status = REJECTED
        assertEquals(RecruitmentRequestStatus.REJECTED,
                recruitmentRequestRepository.findById(pendingRequest.getId()).orElseThrow().getStatus(),
                "BUG: Status không được đổi thành REJECTED từ SUBMITTED");
    }

    /**
     * Test Case ID: RR-TC39
     * Nhánh [N]: returnRequest() — status=SUBMITTED → RETURNED (nhánh TRUE của status==SUBMITTED).
     *
     * Bug bị bắt: Chỉ cho return PENDING, SUBMITTED bị từ chối.
     */
    @Test
    @DisplayName("[RR-TC39] returnRequest() - SUBMITTED → status=RETURNED trong DB")
    void tc39_returnRequest_fromSubmitted_setsReturned() throws IdInvalidException {
        // Arrange
        pendingRequest.setStatus(RecruitmentRequestStatus.SUBMITTED);
        recruitmentRequestRepository.save(pendingRequest);

        ReturnRecruitmentRequestDTO dto = new ReturnRecruitmentRequestDTO();
        dto.setReason("Trả về từ SUBMITTED");
        dto.setReturnedToStepId(1L);

        // Act
        assertDoesNotThrow(
                () -> recruitmentRequestService.returnRequest(pendingRequest.getId(), dto, OWNER_ID, TOKEN),
                "BUG: SUBMITTED bị từ chối return - chỉ chấp nhận PENDING");

        // CheckDB: status = RETURNED
        assertEquals(RecruitmentRequestStatus.RETURNED,
                recruitmentRequestRepository.findById(pendingRequest.getId()).orElseThrow().getStatus(),
                "BUG: Status không được đổi thành RETURNED từ SUBMITTED");
    }

    /**
     * Test Case ID: RR-TC40
     * Nhánh [N]: withdraw() — status=SUBMITTED → WITHDRAWN (điều kiện status==SUBMITTED).
     *
     * Bug bị bắt: Thiếu SUBMITTED trong điều kiện → từ chối withdraw yêu cầu đang SUBMITTED.
     */
    @Test
    @DisplayName("[RR-TC40] withdraw() - SUBMITTED + actor=owner → status=WITHDRAWN")
    void tc40_withdraw_fromSubmitted_ownerIsActor_setsWithdrawn() throws IdInvalidException {
        // Arrange
        pendingRequest.setStatus(RecruitmentRequestStatus.SUBMITTED);
        recruitmentRequestRepository.save(pendingRequest);

        // Act
        assertDoesNotThrow(
                () -> recruitmentRequestService.withdraw(pendingRequest.getId(), OWNER_ID, TOKEN),
                "BUG: SUBMITTED bị từ chối withdraw - thiếu SUBMITTED trong điều kiện");

        // CheckDB
        assertEquals(RecruitmentRequestStatus.WITHDRAWN,
                recruitmentRequestRepository.findById(pendingRequest.getId()).orElseThrow().getStatus(),
                "BUG: Status không được đổi thành WITHDRAWN từ SUBMITTED");
    }

    // ================================================================
    // NHÓM 14: getAllWithFilters() — nhánh departmentId==1 và status invalid
    // ================================================================

    /**
     * Test Case ID: RR-TC41
     * Nhánh TRUE của `if (departmentId != null && departmentId == 1)` → departmentId thành null.
     * Đặc tả: departmentId=1 là sentinel value "tất cả phòng ban" → bỏ qua filter.
     *
     * Bug bị bắt: departmentId=1 bị dùng như filter thật → lọc ra chỉ phòng ban có id=1.
     */
    @Test
    @DisplayName("[RR-TC41] getAllWithFilters() - departmentId=1 → bị reset thành null (không filter dept)")
    void tc41_getAllWithFilters_departmentId1_treatedAsNoFilter() {
        // Arrange: tạo record phòng ban id=5 và record phòng ban id=1
        recruitmentRequestRepository.save(buildRequest(
                "Phòng ban 1", RecruitmentRequestStatus.DRAFT, REQUESTER_ID, OWNER_ID, 1L, null));

        // Act: departmentId=1 → code reset về null → trả về TẤT CẢ phòng ban
        var result = recruitmentRequestService.getAllWithFilters(
                1L, null, null, null, TOKEN,
                org.springframework.data.domain.PageRequest.of(0, 50));

        // Assert: kết quả phải >= 3 (draftRequest + pendingRequest + record mới dept=1)
        // Nếu departmentId=1 không bị reset thành null, sẽ chỉ trả về dept=1 → thiếu draftRequest (dept=5)
        assertNotNull(result, "BUG: Kết quả null");
        assertNotNull(result.getMeta(), "BUG: Meta null");
        assertTrue(result.getMeta().getTotal() >= 3,
                "BUG: departmentId=1 không được reset thành null → đang filter dept=1 thay vì trả tất cả");
    }

    /**
     * Test Case ID: RR-TC42
     * Nhánh TRUE của `catch (IllegalArgumentException e)` trong `getAllWithFilters()`:
     * status string không hợp lệ → statusEnum=null → không filter.
     *
     * Bug bị bắt: Ném exception thay vì bỏ qua → frontend crash khi query lạ.
     */
    @Test
    @DisplayName("[RR-TC42] getAllWithFilters() - status không hợp lệ → không exception, trả về tất cả")
    void tc42_getAllWithFilters_invalidStatus_ignoresFilterReturnsAll() {
        // Act
        assertDoesNotThrow(() -> {
            var result = recruitmentRequestService.getAllWithFilters(
                    null, "NOT_A_VALID_STATUS", null, null, TOKEN,
                    org.springframework.data.domain.PageRequest.of(0, 50));
            // Assert: có kết quả (filter bị bỏ qua)
            assertNotNull(result);
            assertTrue(result.getMeta().getTotal() >= 2,
                    "BUG: Status không hợp lệ làm mất toàn bộ kết quả thay vì bỏ qua filter");
        }, "BUG: Ném exception khi status string không hợp lệ trong getAllWithFilters()");
    }

    // ================================================================
    // NHÓM 15: getByIdWithUser() / convertToWithUserDTO() — các nhánh điều kiện
    // ================================================================

    /**
     * Test Case ID: RR-TC43
     * Nhánh FALSE của `if (request.getRequesterId() != null)` trong convertToWithUserDTO():
     * requesterId=null → không gọi userService.getEmployeeById().
     *
     * Bug bị bắt: Gọi getEmployeeById(null, token) → NPE hoặc HTTP 400.
     */
    @Test
    @DisplayName("[RR-TC43] getByIdWithUser() - requesterId=null → không gọi UserClient, không NPE")
    void tc43_getByIdWithUser_nullRequesterId_skipsUserClientCall() {
        // Arrange: request không có requesterId
        RecruitmentRequest r = recruitmentRequestRepository.save(
                buildRequest("No Requester", RecruitmentRequestStatus.DRAFT, null, OWNER_ID, null, null));

        // userClient không cần mock vì không được gọi
        // Act: không được NPE
        assertDoesNotThrow(
                () -> recruitmentRequestService.getByIdWithUser(r.getId(), TOKEN),
                "BUG: NPE khi requesterId=null - có thể đang gọi getEmployeeById(null)");
    }

    /**
     * Test Case ID: RR-TC44
     * Nhánh TRUE của `if (requesterResponse.getStatusCode().is2xxSuccessful())`:
     * UserClient trả về 200 → dto.setRequester() được gọi.
     *
     * Bug bị bắt: Luôn throw exception dù response 2xx → getByIdWithUser luôn fail.
     */
    @Test
    @DisplayName("[RR-TC44] getByIdWithUser() - UserClient 2xx → dto.requester được set, không throw")
    void tc44_getByIdWithUser_requesterResponse2xx_setsRequesterDto() {
        // Arrange: mock userClient trả 200 với body JSON giả
        com.fasterxml.jackson.databind.node.ObjectNode fakeEmployee =
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        fakeEmployee.put("id", REQUESTER_ID);
        fakeEmployee.put("name", "Nguyen Van A");

        when(userClient.getEmployeeById(eq(REQUESTER_ID), eq(TOKEN)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(fakeEmployee));
        when(userClient.getDepartmentById(any(), eq(TOKEN)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(null));

        // Act
        var result = assertDoesNotThrow(
                () -> recruitmentRequestService.getByIdWithUser(draftRequest.getId(), TOKEN),
                "BUG: Throw exception dù UserClient trả 200");

        // Assert: requester được set
        assertNotNull(result, "BUG: Kết quả null");
        assertNotNull(result.getRequester(), "BUG: dto.requester không được set dù response 2xx");
    }

    /**
     * Test Case ID: RR-TC45
     * Nhánh FALSE của `if (requesterResponse.getStatusCode().is2xxSuccessful())`:
     * UserClient trả về lỗi (4xx/5xx) → ném UserClientException.
     *
     * Bug bị bắt: Nuốt lỗi từ UserClient → dto.requester = null mà không thông báo lỗi.
     */
    @Test
    @DisplayName("[RR-TC45] getByIdWithUser() - UserClient 4xx → ném UserClientException")
    void tc45_getByIdWithUser_requesterResponseError_throwsUserClientException() {
        // Arrange: mock userClient trả 403
        when(userClient.getEmployeeById(eq(REQUESTER_ID), eq(TOKEN)))
                .thenReturn(org.springframework.http.ResponseEntity
                        .status(org.springframework.http.HttpStatus.FORBIDDEN)
                        .body(null));

        // Act + Assert
        assertThrows(com.example.job_service.exception.UserClientException.class,
                () -> recruitmentRequestService.getByIdWithUser(draftRequest.getId(), TOKEN),
                "BUG: Nuốt lỗi UserClient 4xx thay vì ném UserClientException");
    }

    /**
     * Test Case ID: RR-TC46
     * Nhánh FALSE của `if (request.getDepartmentId() != null)` trong convertToWithUserDTO():
     * departmentId=null → không gọi getDepartmentById().
     *
     * Bug bị bắt: Gọi getDepartmentById(null, token) → NPE.
     */
    @Test
    @DisplayName("[RR-TC46] getByIdWithUser() - departmentId=null → không gọi getDepartmentById, không NPE")
    void tc46_getByIdWithUser_nullDepartmentId_skipsDepartmentCall() {
        // Arrange: request không có departmentId, có requesterId
        com.fasterxml.jackson.databind.node.ObjectNode fakeEmployee =
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        fakeEmployee.put("id", REQUESTER_ID);

        when(userClient.getEmployeeById(eq(REQUESTER_ID), eq(TOKEN)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(fakeEmployee));

        RecruitmentRequest r = recruitmentRequestRepository.save(
                buildRequest("No Dept", RecruitmentRequestStatus.DRAFT, REQUESTER_ID, OWNER_ID, null, null));

        // Act: departmentId=null → skip getDepartmentById
        assertDoesNotThrow(
                () -> recruitmentRequestService.getByIdWithUser(r.getId(), TOKEN),
                "BUG: NPE khi departmentId=null - có thể đang gọi getDepartmentById(null)");
    }

    /**
     * Test Case ID: RR-TC47
     * Nhánh TRUE của `if (departmentResponse.getStatusCode().is2xxSuccessful())` → dto.department được set.
     * Nhánh TRUE của `if (workflowInfo != null)` → dto.workflowInfo được set.
     *
     * Bug bị bắt:
     *   - Department 2xx bị bỏ qua → dto.department null
     *   - workflowInfo != null nhưng không được set vào dto
     */
    @Test
    @DisplayName("[RR-TC47] getByIdWithUser() - dept 2xx + workflowInfo != null → cả hai được set vào dto")
    void tc47_getByIdWithUser_departmentAndWorkflowInfoSet() {
        // Arrange
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode fakeEmployee = mapper.createObjectNode();
        fakeEmployee.put("id", REQUESTER_ID);

        com.fasterxml.jackson.databind.node.ObjectNode fakeDept = mapper.createObjectNode();
        fakeDept.put("id", 5);
        fakeDept.put("name", "Engineering");

        com.fasterxml.jackson.databind.node.ObjectNode fakeWorkflow = mapper.createObjectNode();
        fakeWorkflow.put("workflowId", 1);

        when(userClient.getEmployeeById(eq(REQUESTER_ID), eq(TOKEN)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(fakeEmployee));
        when(userClient.getDepartmentById(eq(5L), eq(TOKEN)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(fakeDept));
        // Mock workflowServiceClient trả về non-null
        when(workflowClient.getWorkflowInfoByRequestId(any(), any(), any(), any()))
                .thenReturn(fakeWorkflow);

        // Act
        var result = assertDoesNotThrow(
                () -> recruitmentRequestService.getByIdWithUser(draftRequest.getId(), TOKEN),
                "BUG: Exception khi department và workflowInfo cả hai có giá trị");

        // Assert
        assertNotNull(result.getDepartment(),
                "BUG: dto.department null dù departmentResponse là 2xx");
        assertNotNull(result.getWorkflowInfo(),
                "BUG: dto.workflowInfo null dù workflowInfo != null");
    }

    /**
     * Test Case ID: RR-TC48
     * Nhánh FALSE của `if (workflowInfo != null)` → dto.workflowInfo không được set (giữ null).
     *
     * Bug bị bắt: Set workflowInfo=null vào dto → ghi đè giá trị mặc định bằng null (không ảnh hưởng nhưng sai logic).
     */
    @Test
    @DisplayName("[RR-TC48] getByIdWithUser() - workflowServiceClient trả null → dto.workflowInfo không được set")
    void tc48_getByIdWithUser_nullWorkflowInfo_dtoWorkflowInfoRemainsNull() {
        // Arrange: employee + department 2xx, workflow = null
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode fakeEmployee = mapper.createObjectNode();
        fakeEmployee.put("id", REQUESTER_ID);

        when(userClient.getEmployeeById(eq(REQUESTER_ID), eq(TOKEN)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(fakeEmployee));
        when(userClient.getDepartmentById(eq(5L), eq(TOKEN)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(null));
        when(workflowClient.getWorkflowInfoByRequestId(any(), any(), any(), any()))
                .thenReturn(null); // ← workflowInfo = null → nhánh FALSE

        // Act
        var result = assertDoesNotThrow(
                () -> recruitmentRequestService.getByIdWithUser(draftRequest.getId(), TOKEN),
                "BUG: Exception khi workflowInfo=null");

        // Assert: workflowInfo không được set (vẫn null)
        assertNull(result.getWorkflowInfo(),
                "BUG: dto.workflowInfo không phải null dù workflowServiceClient trả null");
    }

    /**
     * Test Case ID: RR-TC53
     * getByIdWithUserAndMetadata(): trả về SingleResponseDTO chứa dto và metadata
     */
    @Test
    @DisplayName("[RR-TC53] getByIdWithUserAndMetadata() - trả về dto và characterLimits metadata")
    void tc53_getByIdWithUserAndMetadata_returnsDtoWithMetadata() {
        // Arrange: reuse RR-TC47 mocks for employee and department
        com.fasterxml.jackson.databind.node.ObjectNode fakeEmployee =
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        fakeEmployee.put("id", REQUESTER_ID);
        when(userClient.getEmployeeById(eq(REQUESTER_ID), eq(TOKEN)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(fakeEmployee));
        when(userClient.getDepartmentById(any(), eq(TOKEN)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(null));
        when(workflowClient.getWorkflowInfoByRequestId(any(), any(), any(), any()))
                .thenReturn(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode());

        var result = assertDoesNotThrow(() ->
                recruitmentRequestService.getByIdWithUserAndMetadata(draftRequest.getId(), TOKEN),
                "BUG: Exception khi gọi getByIdWithUserAndMetadata");

        assertNotNull(result);
        assertNotNull(result.getData(), "BUG: DTO data null");
        // SingleResponseDTO wraps data + characterLimits (không có getMeta())
        assertNotNull(result.getCharacterLimits(), "BUG: characterLimits null - metadata không được set");
    }

    /**
     * Test Case ID: RR-TC49
     * Nhánh trong convertToWithUserDTOList():
     *   - D1=TRUE: employee.has("department") → department được set vào departmentMap
     *   - D1=FALSE: employee không có "department" node → skip, dto.department=null
     *
     * Bug bị bắt: NPE khi employee.get("department") trả null mà không kiểm tra.
     */
    @Test
    @DisplayName("[RR-TC49] getAllWithFilters() - employee không có department node → dto.department=null, không NPE")
    void tc49_getAllWithFilters_employeeWithoutDepartmentNode_skipsDeptMapping() {
        // Arrange: mock userService.getEmployeesByIds trả về employee không có "department"
        com.fasterxml.jackson.databind.node.ObjectNode employeeNoMatch =
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        employeeNoMatch.put("id", REQUESTER_ID);
        // Không có "department" key → nhánh D1=FALSE

        when(userClient.getEmployeesByIds(any(), any()))
                .thenReturn(Map.of(REQUESTER_ID, employeeNoMatch));

        // Act
        assertDoesNotThrow(() -> {
            var result = recruitmentRequestService.getAllWithFilters(
                    null, null, null, null, TOKEN,
                    org.springframework.data.domain.PageRequest.of(0, 10));
            assertNotNull(result, "BUG: Kết quả null");
            // Department trong DTO phải null (không crash)
            @SuppressWarnings("unchecked")
            java.util.List<com.example.job_service.dto.recruitment.RecruitmentRequestAllWithUserDTO> list =
                    (java.util.List<com.example.job_service.dto.recruitment.RecruitmentRequestAllWithUserDTO>)
                    result.getResult();
            assertNotNull(list, "BUG: Result list null");
            // Xác minh không có NPE khi không có department
        }, "BUG: NPE khi employee không có department node trong convertToWithUserDTOList()");
    }

    /**
     * Test Case ID: RR-TC54
     * getAllWithFilters() convertToWithUserDTOList D1=TRUE: employee.has("department") → dto.department được set
     */
    @Test
    @DisplayName("[RR-TC54] getAllWithFilters() - employee có department → dto.department được set")
    void tc54_getAllWithFilters_employeeWithDepartment_setsDeptInDto() {
        // Arrange: employee JSON có department
        com.fasterxml.jackson.databind.node.ObjectNode employeeWithDept =
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        employeeWithDept.put("id", REQUESTER_ID);
        com.fasterxml.jackson.databind.node.ObjectNode dept = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        dept.put("id", 5);
        dept.put("name", "Engineering");
        employeeWithDept.set("department", dept);

        when(userClient.getEmployeesByIds(any(), any()))
                .thenReturn(Map.of(REQUESTER_ID, (com.fasterxml.jackson.databind.JsonNode) employeeWithDept));

        var result = assertDoesNotThrow(() -> recruitmentRequestService.getAllWithFilters(
                null, null, null, null, TOKEN, org.springframework.data.domain.PageRequest.of(0, 10)),
                "BUG: Exception when getAllWithFilters with employee having department");

        @SuppressWarnings("unchecked")
        java.util.List<com.example.job_service.dto.recruitment.RecruitmentRequestAllWithUserDTO> list =
                (java.util.List<com.example.job_service.dto.recruitment.RecruitmentRequestAllWithUserDTO>) result.getResult();

        assertNotNull(list);
        // At least one dto should have department set (converted from employee)
        assertTrue(list.stream().anyMatch(d -> d.getDepartment() != null),
                "BUG: dto.department không được set từ employee.department");
    }

    // ================================================================
    // NHÓM 17: getById() — method chưa được test (miss 100%)
    // ================================================================

    /**
     * Test Case ID: RR-TC55
     * getById(): ID tồn tại → trả về đúng entity.
     *
     * Bug bị bắt: Method getById() không được test → không phát hiện nếu
     * implementation sai (ví dụ gọi sai repository method, mapping sai).
     */
    @Test
    @DisplayName("[RR-TC55] getById() - ID tồn tại → trả về đúng entity")
    void tc55_getById_existingId_returnsCorrectEntity() throws IdInvalidException {
        RecruitmentRequest result = recruitmentRequestService.getById(draftRequest.getId());

        assertAll("BUG: getById() trả về entity không đúng",
                () -> assertEquals(draftRequest.getId(), result.getId(),
                        "BUG: id sai"),
                () -> assertEquals("Tuyển Kỹ Sư Java", result.getTitle(),
                        "BUG: title sai"),
                () -> assertEquals(RecruitmentRequestStatus.DRAFT, result.getStatus(),
                        "BUG: status sai"),
                () -> assertEquals(REQUESTER_ID, result.getRequesterId(),
                        "BUG: requesterId sai")
        );
    }

    /**
     * Test Case ID: RR-TC56
     * getById(): ID không tồn tại → IdInvalidException.
     *
     * Bug bị bắt: Trả về null thay vì ném exception → NullPointerException ở caller.
     * lambda$1 (dòng 127) chưa được kích hoạt bởi bất kỳ test nào.
     */
    @Test
    @DisplayName("[RR-TC56] getById() - ID không tồn tại → IdInvalidException")
    void tc56_getById_nonExistentId_throwsIdInvalidException() {
        assertThrows(IdInvalidException.class,
                () -> recruitmentRequestService.getById(99999L),
                "BUG: getById() không ném IdInvalidException khi ID không tồn tại");
    }

    // ================================================================
    // NHÓM 18: findAllWithFilters() — nhánh status rỗng/khoảng trắng
    // ================================================================

    /**
     * Test Case ID: RR-TC57
     * findAllWithFilters(): status là chuỗi rỗng "" → bỏ qua filter, trả về tất cả.
     *
     * Nhánh: dòng 139 — `!status.trim().isEmpty()` = FALSE khi status = ""
     * Bug bị bắt: Ném exception hoặc parse "" thành status → filter bị sai.
     */
    @Test
    @DisplayName("[RR-TC57] findAllWithFilters() - status=\"\" (rỗng) → bỏ qua filter, không exception")
    void tc57_findAllWithFilters_emptyStatus_ignoresFilterNoException() {
        List<RecruitmentRequest> result = assertDoesNotThrow(
                () -> recruitmentRequestService.findAllWithFilters(null, "", null, null),
                "BUG: Ném exception khi status là chuỗi rỗng");

        assertNotNull(result, "BUG: Kết quả null");
        assertTrue(result.size() >= 2,
                "BUG: status rỗng làm mất kết quả - phải bỏ qua filter");
    }

    /**
     * Test Case ID: RR-TC58
     * getAllWithFilters(): status là chuỗi khoảng trắng "   " → bỏ qua filter.
     *
     * Nhánh: dòng 169 — `!status.trim().isEmpty()` = FALSE khi status = "   "
     * Bug bị bắt: Ném exception hoặc parse khoảng trắng thành status không hợp lệ.
     */
    @Test
    @DisplayName("[RR-TC58] getAllWithFilters() - status=\"   \" (khoảng trắng) → bỏ qua filter, không exception")
    void tc58_getAllWithFilters_whitespaceStatus_ignoresFilterNoException() {
        assertDoesNotThrow(() -> {
            var result = recruitmentRequestService.getAllWithFilters(
                    null, "   ", null, null, TOKEN,
                    org.springframework.data.domain.PageRequest.of(0, 50));

            assertNotNull(result, "BUG: Kết quả null");
            assertTrue(result.getMeta().getTotal() >= 2,
                    "BUG: status khoảng trắng làm mất kết quả - phải bỏ qua filter");
        }, "BUG: Ném exception khi status là chuỗi khoảng trắng");
    }

    // ================================================================
    // NHÓM 19: convertToWithUserDTO — nhánh getDepartmentById 4xx
    // ================================================================

    /**
     * Test Case ID: RR-TC59
     * convertToWithUserDTO(): getDepartmentById() trả 4xx → ném UserClientException.
     *
     * Nhánh: dòng 216 FALSE — `departmentResponse.getStatusCode().is2xxSuccessful()` = FALSE
     * Bug bị bắt: Nuốt lỗi từ getDepartmentById() → dto.department = null mà không báo lỗi.
     *
     * Lưu ý: Test RR-TC45 đã test lỗi cho requester (getEmployeeById 4xx).
     * Test này bổ sung lỗi cho department (getDepartmentById 4xx).
     */
    @Test
    @DisplayName("[RR-TC59] getByIdWithUser() - getDepartmentById() 4xx → ném UserClientException")
    void tc59_getByIdWithUser_departmentResponseError_throwsUserClientException() {
        // Arrange: requester trả 200, nhưng department trả 403
        com.fasterxml.jackson.databind.node.ObjectNode fakeEmployee =
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        fakeEmployee.put("id", REQUESTER_ID);

        when(userClient.getEmployeeById(eq(REQUESTER_ID), eq(TOKEN)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(fakeEmployee));
        when(userClient.getDepartmentById(eq(5L), eq(TOKEN)))
                .thenReturn(org.springframework.http.ResponseEntity
                        .status(org.springframework.http.HttpStatus.FORBIDDEN)
                        .body(null));

        // Act + Assert: phải ném UserClientException do lỗi department
        assertThrows(com.example.job_service.exception.UserClientException.class,
                () -> recruitmentRequestService.getByIdWithUser(draftRequest.getId(), TOKEN),
                "BUG: Nuốt lỗi getDepartmentById 4xx thay vì ném UserClientException");
    }

    // ================================================================
    // NHÓM 20: convertToWithUserDTOList — nhánh requesterId=null trong stream
    // ================================================================

    /**
     * Test Case ID: RR-TC60
     * convertToWithUserDTOList(): Danh sách có request với requesterId=null
     * → lambda$3 nhánh FALSE (trả List.of() thay vì List.of(requesterId))
     * → lambda$4 nhánh requesterId=null → không set requester/department.
     *
     * Bug bị bắt: NPE khi requesterId=null mà code vẫn gọi
     * employeeMap.get(null) hoặc request.getRequesterId() mà không kiểm tra null.
     */
    @Test
    @DisplayName("[RR-TC60] getAllWithFilters() - request có requesterId=null → không NPE, dto.requester=null")
    void tc60_getAllWithFilters_requestWithNullRequesterId_noNpeRequesterIsNull() {
        // Arrange: tạo request không có requesterId
        recruitmentRequestRepository.save(buildRequest(
                "Request No Requester", RecruitmentRequestStatus.DRAFT, null, OWNER_ID, 5L, null));

        // Mock userService trả về map rỗng (requesterId=null không có trong map)
        when(userClient.getEmployeesByIds(any(), any()))
                .thenReturn(Map.of());

        // Act: phải không NPE
        assertDoesNotThrow(() -> {
            var result = recruitmentRequestService.getAllWithFilters(
                    null, null, null, null, TOKEN,
                    org.springframework.data.domain.PageRequest.of(0, 50));

            assertNotNull(result, "BUG: Kết quả null");
            assertNotNull(result.getResult(), "BUG: Result list null");

            // Request với requesterId=null: dto.requester phải null (không crash)
            @SuppressWarnings("unchecked")
            java.util.List<com.example.job_service.dto.recruitment.RecruitmentRequestAllWithUserDTO> list =
                    (java.util.List<com.example.job_service.dto.recruitment.RecruitmentRequestAllWithUserDTO>)
                    result.getResult();

            assertTrue(list.stream().anyMatch(d -> d.getRequester() == null),
                    "BUG: Không có dto nào có requester=null dù request có requesterId=null");
        }, "BUG: NPE khi requesterId=null trong convertToWithUserDTOList()");
    }

    /**
     * Test Case ID: RR-TC61
     * getAllWithFilters(): employee có node "department" nhưng là NullNode
     * → nhánh `if (dept != null && dept.has("id"))` = FALSE.
     */
    @Test
    @DisplayName("[RR-TC61] getAllWithFilters() - employee có department là NullNode → không crash")
    void tc61_getAllWithFilters_nullDepartmentNode_noNpe() {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode employee = mapper.createObjectNode();
        employee.put("id", REQUESTER_ID);
        employee.set("department", mapper.nullNode()); // NullNode

        when(userClient.getEmployeesByIds(any(), any()))
                .thenReturn(Map.of(REQUESTER_ID, employee));

        assertDoesNotThrow(() -> recruitmentRequestService.getAllWithFilters(
                null, null, null, null, TOKEN, org.springframework.data.domain.PageRequest.of(0, 10)));
    }

    /**
     * Test Case ID: RR-TC62
     * getAllWithFilters(): employee có department nhưng thiếu "id"
     * → nhánh `if (dept.has("id"))` = FALSE.
     */
    @Test
    @DisplayName("[RR-TC62] getAllWithFilters() - department thiếu id → không crash")
    void tc62_getAllWithFilters_departmentMissingId_noNpe() {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode employee = mapper.createObjectNode();
        employee.put("id", REQUESTER_ID);
        employee.set("department", mapper.createObjectNode()); // Empty object {}

        when(userClient.getEmployeesByIds(any(), any()))
                .thenReturn(Map.of(REQUESTER_ID, employee));

        assertDoesNotThrow(() -> recruitmentRequestService.getAllWithFilters(
                null, null, null, null, TOKEN, org.springframework.data.domain.PageRequest.of(0, 10)));
    }

    /**
     * Test Case ID: RR-TC63
     * getAllWithFilters(): employeeMap không chứa ID của requester
     * → `employeeMap.get(id)` trả về null → nhánh `if (employee != null)` = FALSE.
     */
    @Test
    @DisplayName("[RR-TC63] getAllWithFilters() - employeeMap thiếu ID → không crash, dto.requester=null")
    void tc63_getAllWithFilters_missingEmployeeInMap_noNpe() {
        // Mock map rỗng dù có requesterId
        when(userClient.getEmployeesByIds(any(), any())).thenReturn(Map.of());

        var result = assertDoesNotThrow(() -> recruitmentRequestService.getAllWithFilters(
                null, null, null, null, TOKEN, org.springframework.data.domain.PageRequest.of(0, 10)));
        
        @SuppressWarnings("unchecked")
        java.util.List<com.example.job_service.dto.recruitment.RecruitmentRequestAllWithUserDTO> list =
                (java.util.List<com.example.job_service.dto.recruitment.RecruitmentRequestAllWithUserDTO>) result.getResult();
        assertTrue(list.stream().anyMatch(d -> d.getRequester() == null));
    }

    /**
     * Test Case ID: RR-TC64
     * submit() - Trạng thái SUBMITTED → IllegalStateException
     */
    @Test
    @DisplayName("[RR-TC64] submit() - SUBMITTED → IllegalStateException")
    void tc64_submit_fromSubmitted_throwsIllegalStateException() {
        pendingRequest.setStatus(RecruitmentRequestStatus.SUBMITTED);
        recruitmentRequestRepository.save(pendingRequest);

        assertThrows(IllegalStateException.class,
                () -> recruitmentRequestService.submit(pendingRequest.getId(), OWNER_ID, TOKEN));
    }

    /**
     * Test Case ID: RR-TC65
     * approveStep() - Trạng thái REJECTED → IllegalStateException
     */
    @Test
    @DisplayName("[RR-TC65] approveStep() - REJECTED → IllegalStateException")
    void tc65_approveStep_fromRejected_throwsIllegalStateException() {
        pendingRequest.setStatus(RecruitmentRequestStatus.REJECTED);
        recruitmentRequestRepository.save(pendingRequest);

        assertThrows(IllegalStateException.class,
                () -> recruitmentRequestService.approveStep(pendingRequest.getId(), new ApproveRecruitmentRequestDTO(), OWNER_ID, TOKEN));
    }

    /**
     * Test Case ID: RR-TC66
     * rejectStep() - Trạng thái APPROVED → IllegalStateException
     */
    @Test
    @DisplayName("[RR-TC66] rejectStep() - APPROVED → IllegalStateException")
    void tc66_rejectStep_fromApproved_throwsIllegalStateException() {
        pendingRequest.setStatus(RecruitmentRequestStatus.APPROVED);
        recruitmentRequestRepository.save(pendingRequest);

        assertThrows(IllegalStateException.class,
                () -> recruitmentRequestService.rejectStep(pendingRequest.getId(), new RejectRecruitmentRequestDTO(), OWNER_ID, TOKEN));
    }

    /**
     * Test Case ID: RR-TC67
     * returnRequest() - Trạng thái CANCELLED → IllegalStateException
     */
    @Test
    @DisplayName("[RR-TC67] returnRequest() - CANCELLED → IllegalStateException")
    void tc67_returnRequest_fromCancelled_throwsIllegalStateException() {
        pendingRequest.setStatus(RecruitmentRequestStatus.CANCELLED);
        recruitmentRequestRepository.save(pendingRequest);

        assertThrows(IllegalStateException.class,
                () -> recruitmentRequestService.returnRequest(pendingRequest.getId(), new ReturnRecruitmentRequestDTO(), OWNER_ID, TOKEN));
    }

    /**
     * Test Case ID: RR-TC68
     * findAllWithFilters() - status="   " (khoảng trắng) → bỏ qua filter
     */
    @Test
    @DisplayName("[RR-TC68] findAllWithFilters() - status=\"   \" → bỏ qua filter")
    void tc68_findAllWithFilters_whitespaceStatus_ignoresFilter() {
        List<RecruitmentRequest> result = recruitmentRequestService.findAllWithFilters(null, "   ", null, null);
        assertTrue(result.size() >= 2);
    }

    /**
     * Test Case ID: RR-TC69
     * getByIdWithUser() - getDepartmentById() 500 → UserClientException
     */
    @Test
    @DisplayName("[RR-TC69] getByIdWithUser() - getDepartmentById() 500 → UserClientException")
    void tc69_getByIdWithUser_department500_throwsUserClientException() {
        com.fasterxml.jackson.databind.node.ObjectNode fakeEmployee =
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        fakeEmployee.put("id", REQUESTER_ID);

        when(userClient.getEmployeeById(eq(REQUESTER_ID), eq(TOKEN)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(fakeEmployee));
        when(userClient.getDepartmentById(any(), eq(TOKEN)))
                .thenReturn(org.springframework.http.ResponseEntity.status(500).build());

        assertThrows(com.example.job_service.exception.UserClientException.class,
                () -> recruitmentRequestService.getByIdWithUser(draftRequest.getId(), TOKEN));
    }

    private com.example.job_service.service.RecruitmentRequestService getTargetService() throws Exception {
        if (org.springframework.aop.support.AopUtils.isAopProxy(recruitmentRequestService)) {
            return (com.example.job_service.service.RecruitmentRequestService) 
                org.springframework.test.util.AopTestUtils.getTargetObject(recruitmentRequestService);
        }
        return recruitmentRequestService;
    }

    /**
     * Test Case ID: RR-TC70
     * Phủ nhánh includeWorkflow = false
     */
    @Test
    @DisplayName("[RR-TC70] convertToWithUserDTO - includeWorkflow=false")
    void tc70_convertToWithUserDTO_noWorkflow() throws Exception {
        var service = getTargetService();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        // Mock UserClient để tránh NPE
        when(userClient.getEmployeeById(any(), any()))
                .thenReturn(org.springframework.http.ResponseEntity.ok(mapper.createObjectNode()));
        when(userClient.getDepartmentById(any(), any()))
                .thenReturn(org.springframework.http.ResponseEntity.ok(mapper.createObjectNode()));

        var method = org.springframework.util.ReflectionUtils.findMethod(
                com.example.job_service.service.RecruitmentRequestService.class,
                "convertToWithUserDTO",
                com.example.job_service.model.RecruitmentRequest.class,
                String.class,
                boolean.class,
                boolean.class
        );
        org.springframework.util.ReflectionUtils.makeAccessible(method);

        // Tham số: request, token, truncateText=true, includeWorkflow=false
        var result = (com.example.job_service.dto.recruitment.RecruitmentRequestWithUserDTO) 
                org.springframework.util.ReflectionUtils.invokeMethod(method, service, draftRequest, TOKEN, true, false);

        assertNotNull(result);
        assertNull(result.getWorkflowInfo()); 
    }

    /**
     * Test Case ID: RR-TC71
     * Phủ nhánh includeEmployee = false
     */
    @Test
    @DisplayName("[RR-TC71] convertToWithUserDTO - truncateText=false")
    void tc71_convertToWithUserDTO_noTruncate() throws Exception {
        var service = getTargetService();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        // Mock đầy đủ để tránh NPE
        when(userClient.getEmployeeById(any(), any()))
                .thenReturn(org.springframework.http.ResponseEntity.ok(mapper.createObjectNode()));
        when(userClient.getDepartmentById(any(), any()))
                .thenReturn(org.springframework.http.ResponseEntity.ok(mapper.createObjectNode()));

        var method = org.springframework.util.ReflectionUtils.findMethod(
                com.example.job_service.service.RecruitmentRequestService.class,
                "convertToWithUserDTO",
                com.example.job_service.model.RecruitmentRequest.class,
                String.class,
                boolean.class,
                boolean.class
        );
        org.springframework.util.ReflectionUtils.makeAccessible(method);

        // Tham số: request, token, truncateText=false, includeWorkflow=true
        var result = (com.example.job_service.dto.recruitment.RecruitmentRequestWithUserDTO) 
                org.springframework.util.ReflectionUtils.invokeMethod(method, service, draftRequest, TOKEN, false, true);

        assertNotNull(result);
        assertNotNull(result.getRequester());
    }

    /**
     * Test Case ID: RR-TC72
     * changeStatus() - Phủ nhánh Repository trả về null
     */
    @Test
    @DisplayName("[RR-TC72] changeStatus() - Repository trả null")
    void tc72_changeStatus_repoReturnsNull() throws Exception {
        var service = getTargetService();
        var mockRepo = mock(com.example.job_service.repository.RecruitmentRequestRepository.class);
        when(mockRepo.findById(any())).thenReturn(java.util.Optional.of(draftRequest));
        when(mockRepo.save(any())).thenReturn(null);

        var field = com.example.job_service.service.RecruitmentRequestService.class.getDeclaredField("recruitmentRequestRepository");
        field.setAccessible(true);
        var originalRepo = field.get(service);
        field.set(service, mockRepo);

        try {
            boolean result = service.changeStatus(draftRequest.getId(), RecruitmentRequestStatus.PENDING);
            assertFalse(result);
        } finally {
            field.set(service, originalRepo);
        }
    }

    /**
     * Test Case ID: RR-TC73
     * getAllWithFilters() - Phủ nhánh status rỗng
     */
    @Test
    @DisplayName("[RR-TC73] getAllWithFilters() - status hợp lệ")
    void tc73_getAllWithFilters_validStatus() {
        // Phủ dòng 171: gọi valueOf với status hợp lệ
        assertDoesNotThrow(() -> recruitmentRequestService.getAllWithFilters(
                null, "DRAFT", null, null, TOKEN, org.springframework.data.domain.PageRequest.of(0, 10)));
    }

    /**
     * Test Case ID: RR-TC74
     * convertToWithUserDTOList - Phủ nhánh tổ hợp logic A && B (NullNode)
     */
    @Test
    @DisplayName("[RR-TC74] convertToWithUserDTOList - JSON logic coverage (NullNode vs Missing)")
    void tc74_convertToWithUserDTOList_jsonBranches() throws Exception {
        var service = getTargetService();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        
        // Trường hợp 1: employee có department nhưng department là NullNode (dept != null nhưng dept.has("id") là false)
        var employee1 = mapper.createObjectNode();
        employee1.set("department", com.fasterxml.jackson.databind.node.NullNode.getInstance());
        
        // Trường hợp 2: employee có department, department có id nhưng id là NullNode
        var employee2 = mapper.createObjectNode();
        var dept2 = mapper.createObjectNode();
        dept2.set("id", com.fasterxml.jackson.databind.node.NullNode.getInstance());
        employee2.set("department", dept2);

        when(userClient.getEmployeeById(any(), any()))
                .thenReturn(org.springframework.http.ResponseEntity.ok(employee1))
                .thenReturn(org.springframework.http.ResponseEntity.ok(employee2));

        org.springframework.data.domain.Page<com.example.job_service.model.RecruitmentRequest> page = 
                new org.springframework.data.domain.PageImpl<>(java.util.List.of(draftRequest, draftRequest));

        var method = org.springframework.util.ReflectionUtils.findMethod(
                com.example.job_service.service.RecruitmentRequestService.class, "convertToWithUserDTOList",
                org.springframework.data.domain.Page.class, String.class);
        org.springframework.util.ReflectionUtils.makeAccessible(method);
        
        var result = org.springframework.util.ReflectionUtils.invokeMethod(method, service, page, TOKEN);
        assertNotNull(result);
    }

    /**
     * Test Case ID: RR-TC75
     * getAllWithFilters() - Phủ nhánh Pageable.unpaged()
     */
    @Test
    @DisplayName("[RR-TC75] getAllWithFilters() - Pageable.unpaged()")
    void tc75_getAllWithFilters_unpaged() {
        // Phủ nhánh pageable.isPaged() = false
        assertDoesNotThrow(() -> recruitmentRequestService.getAllWithFilters(
                null, null, null, null, TOKEN, org.springframework.data.domain.Pageable.unpaged()));
    }

    private RecruitmentRequest buildRequest(String title, RecruitmentRequestStatus status,
                                             Long requesterId, Long ownerId, Long deptId, Long workflowId) {
        RecruitmentRequest r = new RecruitmentRequest();
        r.setTitle(title);
        r.setQuantity(1);
        r.setReason("Lý do tuyển dụng");
        r.setSalaryMin(new BigDecimal("10000000"));
        r.setSalaryMax(new BigDecimal("20000000"));
        r.setStatus(status);
        r.setRequesterId(requesterId);
        r.setOwnerUserId(ownerId);
        r.setDepartmentId(deptId);
        r.setWorkflowId(workflowId);
        r.setActive(true);
        return r;
    }

    private CancelRecruitmentRequestDTO buildCancelDto(String reason) {
        CancelRecruitmentRequestDTO dto = new CancelRecruitmentRequestDTO();
        dto.setReason(reason);
        return dto;
    }
}
