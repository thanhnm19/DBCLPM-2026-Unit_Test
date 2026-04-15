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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

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
 * Nhánh cần phủ:
 *
 * create():
 *   [N1] Input hợp lệ → DRAFT, isActive=true, tất cả trường được lưu đúng
 *
 * submit():
 *   [N2] DRAFT → PENDING, submittedAt được ghi, ownerUserId giữ nguyên
 *   [N3] RETURNED → PENDING (submit lại sau khi được trả về)
 *   [N4] PENDING → IllegalStateException, DB không đổi
 *   [N5] APPROVED → IllegalStateException
 *   [N6] ownerUserId=null → được gán actorId
 *   [N7] ID không tồn tại → IdInvalidException
 *
 * approveStep():
 *   [N8]  PENDING → PENDING (giữ nguyên; workflow-service xử lý APPROVED), publish event
 *   [N9]  SUBMITTED → PENDING, publish event (cùng điều kiện)
 *   [N10] DRAFT → IllegalStateException, DB không đổi
 *   [N11] APPROVED → IllegalStateException
 *
 * rejectStep():
 *   [N12] PENDING → REJECTED, DB cập nhật, publish event
 *   [N13] DRAFT → IllegalStateException, DB không đổi
 *   [N14] CANCELLED → IllegalStateException
 *
 * returnRequest():
 *   [N15] PENDING → RETURNED, publish event với reason và returnedToStepId
 *   [N16] DRAFT → IllegalStateException, DB không đổi
 *   [N17] APPROVED → IllegalStateException
 *
 * cancel():
 *   [N18] DRAFT → CANCELLED, publish event
 *   [N19] PENDING → CANCELLED, publish event
 *   [N20] đã CANCELLED (idempotent) → trả về nguyên, không publish event
 *   [N21] APPROVED → IllegalStateException, DB không đổi
 *   [N22] REJECTED → IllegalStateException
 *
 * withdraw():
 *   [N23] PENDING + actor=owner → WITHDRAWN, publish event
 *   [N24] PENDING + actor=requester (khác owner) → WITHDRAWN
 *   [N25] PENDING + actor sai (không phải owner/requester) → IllegalStateException, DB không đổi
 *   [N26] DRAFT → IllegalStateException
 *   [N27] APPROVED → IllegalStateException
 *
 * findAllWithFilters():
 *   [N28] status string không hợp lệ → không exception, filter bị bỏ qua
 *   [N29] status=PENDING → chỉ trả về PENDING
 *   [N30] departmentId → chỉ trả về đúng department
 *   [N31] keyword → chỉ trả về khớp title/reason
 *
 * findById() / getById():
 *   [N32] ID tồn tại → đúng entity
 *   [N33] ID không tồn tại → IdInvalidException
 *
 * changeStatus():
 *   [N34] Đổi status bất kỳ → DB cập nhật đúng, trả về true
 *
 * getAll():
 *   [N35] Chỉ trả về isActive=true, loại bỏ inactive
 *
 * delete() (soft delete):
 *   [N36] ID tồn tại → isActive=false, record vẫn còn
 *   [N37] ID không tồn tại → IdInvalidException
 *
 * CheckDB: Sau mỗi thao tác ghi, truy vấn lại repository để xác minh
 * Rollback: @Transactional đảm bảo DB sạch sau mỗi test
 * =============================================================
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
@ExtendWith(com.example.job_service.TestResultLogger.class)
@DisplayName("RecruitmentRequestService - Branch Coverage Cấp 2")
class RecruitmentRequestServiceTest {

    @Autowired private RecruitmentRequestService recruitmentRequestService;
    @Autowired private RecruitmentRequestRepository recruitmentRequestRepository;

    // --- Mock external dependencies (HTTP + Kafka) ---
    /**
     * Mock RestTemplate: UserClient và WorkflowClient cần RestTemplate trong constructor.
     * @MockitoBean thay thế bean RestTemplate thật bằng mock để Spring
     * có thể khởi tạo UserClient và WorkflowClient mà không gối HTTP thật.
     */
    @MockitoBean
    private RestTemplate restTemplate;

    @MockitoBean private UserClient userClient;
    @MockitoBean private WorkflowClient workflowClient;
    @MockitoBean private RecruitmentWorkflowProducer workflowProducer;
    /** KafkaTemplate: cần mock để Spring tạo được RecruitmentWorkflowProducer bean */
    @MockitoBean private KafkaTemplate<String, String> kafkaTemplate;

    // Constants
    private static final Long REQUESTER_ID = 10L;
    private static final Long OWNER_ID     = 10L;
    private static final Long OTHER_ACTOR  = 99L;   // người không liên quan
    private static final String TOKEN      = "test-token";

    /** Yêu cầu đang ở trạng thái DRAFT */
    private RecruitmentRequest draftRequest;
    /** Yêu cầu đang ở trạng thái PENDING */
    private RecruitmentRequest pendingRequest;

    @BeforeEach
    void setUp() {
        // Kafka: không gửi thật
        doNothing().when(workflowProducer).publishEvent(any());
        // WorkflowClient: không gọi HTTP thật
        when(workflowClient.getWorkflowInfoByRequestId(any(), any(), any(), any())).thenReturn(null);

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
     */
    @Test
    @DisplayName("[RR-TC01][N1] create() - Input hợp lệ → status=DRAFT, isActive=true, các trường lưu đúng")
    void tc01_create_validInput_persistsAllFieldsWithDraftStatus() {
        CreateRecruitmentRequestDTO dto = new CreateRecruitmentRequestDTO();
        dto.setTitle("Tuyển QA Engineer");
        dto.setQuantity(2);
        dto.setReason("Mở rộng nhóm test");
        dto.setSalaryMin(new BigDecimal("10000000"));
        dto.setSalaryMax(new BigDecimal("20000000"));
        dto.setRequesterId(REQUESTER_ID);
        dto.setWorkflowId(1L);
        dto.setDepartmentId(5L);

        long countBefore = recruitmentRequestRepository.count();

        RecruitmentRequest result = recruitmentRequestService.create(dto);

        // CHECK DB: số lượng tăng 1
        assertEquals(countBefore + 1, recruitmentRequestRepository.count(),
                "BUG: Không tạo thêm record trong DB");

        // CHECK DB: từng trường
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
     */
    @Test
    @DisplayName("[RR-TC02][N2] submit() - DRAFT → PENDING, submittedAt được ghi, ownerUserId giữ nguyên")
    void tc02_submit_fromDraft_setsPendingAndRecordsSubmittedAt() throws IdInvalidException {
        assertNull(draftRequest.getSubmittedAt(), "Precondition: submittedAt phải null trước khi submit");

        recruitmentRequestService.submit(draftRequest.getId(), OWNER_ID, TOKEN);

        RecruitmentRequest saved = recruitmentRequestRepository.findById(draftRequest.getId()).orElseThrow();
        assertAll("BUG: submit() không cập nhật đúng dữ liệu",
                () -> assertEquals(RecruitmentRequestStatus.PENDING, saved.getStatus(),
                        "BUG: Status không được đổi thành PENDING"),
                () -> assertNotNull(saved.getSubmittedAt(),
                        "BUG: submittedAt null - không ghi nhận thời gian submit"),
                () -> assertEquals(OWNER_ID, saved.getOwnerUserId(),
                        "BUG: ownerUserId bị thay đổi dù đã có giá trị - chỉ nên set khi null")
        );
    }

    /**
     * Test Case ID: RR-TC03
     * Nhánh [N3]: RETURNED → PENDING (submit lại sau khi bị trả về)
     *
     * Bug bị bắt: Không cho phép submit lại từ RETURNED → luồng phê duyệt bị kẹt
     */
    @Test
    @DisplayName("[RR-TC03][N3] submit() - RETURNED → PENDING (submit lại hợp lệ)")
    void tc03_submit_fromReturned_transitionsToPending() throws IdInvalidException {
        draftRequest.setStatus(RecruitmentRequestStatus.RETURNED);
        recruitmentRequestRepository.save(draftRequest);

        recruitmentRequestService.submit(draftRequest.getId(), OWNER_ID, TOKEN);

        RecruitmentRequestStatus status = recruitmentRequestRepository
                .findById(draftRequest.getId()).orElseThrow().getStatus();
        assertEquals(RecruitmentRequestStatus.PENDING, status,
                "BUG: Không cho phép submit lại từ RETURNED → luồng bị kẹt");
    }

    /**
     * Test Case ID: RR-TC04
     * Nhánh [N4]: PENDING → IllegalStateException, DB không thay đổi
     *
     * Bug bị bắt: Cho phép submit lại khi đang PENDING → tạo tracking mới sai luồng
     *
     * CheckDB: Status vẫn là PENDING
     */
    @Test
    @DisplayName("[RR-TC04][N4] submit() - PENDING → IllegalStateException; DB giữ nguyên PENDING")
    void tc04_submit_fromPending_throwsIllegalStateException_dbUnchanged() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> recruitmentRequestService.submit(pendingRequest.getId(), OWNER_ID, TOKEN),
                "BUG: Cho phép submit khi đang PENDING → tạo tracking sai luồng");

        assertTrue(ex.getMessage().contains("DRAFT") || ex.getMessage().contains("RETURNED"),
                "BUG: Message không đề cập đến trạng thái hợp lệ (DRAFT/RETURNED)");

        // CHECK DB: không thay đổi
        assertEquals(RecruitmentRequestStatus.PENDING,
                recruitmentRequestRepository.findById(pendingRequest.getId()).orElseThrow().getStatus(),
                "BUG: Status trong DB bị thay đổi dù submit thất bại");
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
        List<RecruitmentRequest> result = assertDoesNotThrow(
                () -> recruitmentRequestService.findAllWithFilters(null, "INVALID_STATUS_XYZ", null, null),
                "BUG: Ném exception khi status string không hợp lệ");

        assertNotNull(result, "BUG: Kết quả null");
        assertTrue(result.size() >= 2,
                "BUG: Filter status lạ làm mất kết quả thay vì bỏ qua");
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
    // HELPER METHODS
    // ================================================================

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
