package com.example.job_service.service;

import com.example.job_service.dto.PaginationDTO;
import com.example.job_service.dto.SingleResponseDTO;
import com.example.job_service.dto.offer.*;
import com.example.job_service.exception.IdInvalidException;
import com.example.job_service.messaging.OfferWorkflowProducer;
import com.example.job_service.model.JobPosition;
import com.example.job_service.model.Offer;
import com.example.job_service.model.RecruitmentRequest;
import com.example.job_service.repository.JobPositionRepository;
import com.example.job_service.repository.OfferRepository;
import com.example.job_service.repository.RecruitmentRequestRepository;
import com.example.job_service.utils.enums.OfferStatus;
import com.example.job_service.utils.enums.RecruitmentRequestStatus;
import java.util.Optional;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho OfferService - Module 8: Offer (Hợp đồng / Lương thưởng).
 * Sử dụng H2 Database để kiểm thử tích hợp thực tế với JPA.
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("OfferService Unit Tests with H2 DB")
class OfferServiceTest {

    @Autowired
    private OfferRepository offerRepository;

    @Autowired
    private RecruitmentRequestRepository recruitmentRequestRepository;

    @Autowired
    private JobPositionRepository jobPositionRepository;

    @Mock
    private UserClient userService;

    @Mock
    private OfferWorkflowProducer workflowProducer;

    @Mock
    private WorkflowClient workflowServiceClient;

    @Mock
    private CandidateClient candidateClient;

    @Mock
    private JobPositionService jobPositionService;

    private OfferService offerService;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        offerService = new OfferService(
            offerRepository,
            userService,
            workflowProducer,
            workflowServiceClient,
            candidateClient,
            jobPositionService
        );
        
        // Mock workflow producer to do nothing
        doNothing().when(workflowProducer).publishEvent(any());

        // Default mocks for external clients to avoid NPE in common paths
        lenient().when(userService.getEmployeeById(any(), anyString()))
                 .thenReturn(ResponseEntity.ok(objectMapper.createObjectNode()));
        lenient().when(candidateClient.getCandidateById(any(), anyString()))
                 .thenReturn(ResponseEntity.ok(objectMapper.createObjectNode()));
        lenient().when(workflowServiceClient.getWorkflowInfoByRequestId(any(), any(), any(), any()))
                 .thenReturn(objectMapper.createObjectNode());
    }

    /**
     * Helper method để tạo RecruitmentRequest hợp lệ
     */
    private RecruitmentRequest createSampleRecruitmentRequest(RecruitmentRequestStatus status) {
        RecruitmentRequest rr = new RecruitmentRequest();
        rr.setStatus(status);
        rr.setDepartmentId(1L);
        rr.setActive(true);
        rr.setQuantity(1);
        rr.setReason("Test");
        rr.setTitle("Test RR");
        return rr;
    }

    /**
     * Helper method để tạo Offer hợp lệ
     */
    private Offer createSampleOffer(Long candidateId, OfferStatus status) {
        Offer offer = new Offer();
        offer.setCandidateId(candidateId);
        offer.setStatus(status);
        offer.setBasicSalary(20000000L);
        offer.setIsActive(true);
        offer.setWorkflowId(100L);
        offer.setOwnerUserId(1L);
        offer.setRequesterId(1L);
        return offer;
    }

    // =======================================================================
    // PHẦN 1: Hàm create()
    // =======================================================================

    @Test
    @Transactional
    @DisplayName("OFF-TC01: create - DTO hợp lệ, phải lưu với status DRAFT")
    void create_ValidDto_ShouldSaveOfferWithDraftStatus() {
        CreateOfferDTO dto = new CreateOfferDTO();
        dto.setCandidateId(100L);
        dto.setBasicSalary(20000000L);
        dto.setProbationSalaryRate(85);
        dto.setOnboardingDate(LocalDate.now().plusDays(15));
        dto.setProbationPeriod(2);
        dto.setWorkflowId(50L);

        Offer saved = offerService.create(dto);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(OfferStatus.DRAFT);
        assertThat(saved.getIsActive()).isTrue();
        assertThat(offerRepository.findById(saved.getId())).isPresent();
    }

    // =======================================================================
    // PHẦN 2: Hàm update()
    // =======================================================================

    @Test
    @Transactional
    @DisplayName("OFF-TC02: update - Offer DRAFT, cập nhật đầy đủ các trường")
    void update_DraftOffer_ShouldUpdateFields() throws IdInvalidException {
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer = offerRepository.save(offer);

        UpdateOfferDTO dto = new UpdateOfferDTO();
        dto.setCandidateId(101L);
        dto.setBasicSalary(25000000L);
        dto.setProbationSalaryRate(90);
        dto.setNotes("Updated notes");

        Offer updated = offerService.update(offer.getId(), dto);

        assertThat(updated.getCandidateId()).isEqualTo(101L);
        assertThat(updated.getBasicSalary()).isEqualTo(25000000L);
        assertThat(updated.getNotes()).isEqualTo("Updated notes");
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC03: update - Offer PENDING, phải throw IllegalStateException")
    void update_PendingOffer_ShouldThrowException() {
        Offer offer = createSampleOffer(100L, OfferStatus.PENDING);
        offer = offerRepository.save(offer);

        UpdateOfferDTO dto = new UpdateOfferDTO();
        dto.setBasicSalary(30000000L);

        Long id = offer.getId();
        assertThatThrownBy(() -> offerService.update(id, dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC04: update - Offer APPROVED, phải throw IllegalStateException")
    void update_ApprovedOffer_ShouldThrowException() {
        Offer offer = createSampleOffer(100L, OfferStatus.APPROVED);
        offer = offerRepository.save(offer);

        Long id = offer.getId();
        assertThatThrownBy(() -> offerService.update(id, new UpdateOfferDTO()))
                .isInstanceOf(IllegalStateException.class);
    }

    // =======================================================================
    // PHẦN 3: Hàm submit()
    // =======================================================================

    @Test
    @Transactional
    @DisplayName("OFF-TC05: submit - DRAFT -> PENDING")
    void submit_DraftOffer_ShouldChangeToPending() throws IdInvalidException {
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer.setWorkflowId(50L);
        offer = offerRepository.save(offer);

        Offer result = offerService.submit(offer.getId(), 1L, "token");

        assertThat(result.getStatus()).isEqualTo(OfferStatus.PENDING);
        assertThat(result.getSubmittedAt()).isNotNull();
        verify(workflowProducer, times(1)).publishEvent(any());
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC06: submit - workflowId null")
    void submit_NullWorkflowId_ShouldThrowException() {
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer.setWorkflowId(null);
        offer = offerRepository.save(offer);

        Long id = offer.getId();
        assertThatThrownBy(() -> offerService.submit(id, 1L, "token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WorkflowId");
    }

    // =======================================================================
    // PHẦN 4: Hàm approveStep() / rejectStep()
    // =======================================================================

    @Test
    @Transactional
    @DisplayName("OFF-TC07: approveStep - PENDING")
    void approveStep_Pending_ShouldPublishEvent() throws IdInvalidException {
        Offer offer = createSampleOffer(100L, OfferStatus.PENDING);
        offer = offerRepository.save(offer);

        offerService.approveStep(offer.getId(), new ApproveOfferDTO(), 1L, "token");

        verify(workflowProducer).publishEvent(argThat(event -> event.getEventType().equals("REQUEST_APPROVED")));
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC08: rejectStep - PENDING -> REJECTED")
    void rejectStep_Pending_ShouldSetRejected() throws IdInvalidException {
        Offer offer = createSampleOffer(100L, OfferStatus.PENDING);
        offer = offerRepository.save(offer);

        Offer result = offerService.rejectStep(offer.getId(), new RejectOfferDTO(), 1L, "token");

        assertThat(result.getStatus()).isEqualTo(OfferStatus.REJECTED);
        verify(workflowProducer).publishEvent(argThat(event -> event.getEventType().equals("REQUEST_REJECTED")));
    }

    // =======================================================================
    // PHẦN 5: Hàm cancel() / withdraw()
    // =======================================================================

    @Test
    @Transactional
    @DisplayName("OFF-TC09: cancel - Idempotent check")
    void cancel_AlreadyCancelled_ShouldReturnImmediately() throws IdInvalidException {
        Offer offer = createSampleOffer(100L, OfferStatus.CANCELLED);
        offer = offerRepository.save(offer);

        Offer result = offerService.cancel(offer.getId(), new CancelOfferDTO(), 1L, "token");

        assertThat(result.getStatus()).isEqualTo(OfferStatus.CANCELLED);
        // We verify that no publish event happens if already cancelled
        verify(workflowProducer, times(0)).publishEvent(any());
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC10: withdraw - Owner can withdraw")
    void withdraw_ByOwner_ShouldSetWithdrawn() throws IdInvalidException {
        Offer offer = createSampleOffer(100L, OfferStatus.PENDING);
        offer.setOwnerUserId(20L);
        offer = offerRepository.save(offer);

        Offer result = offerService.withdraw(offer.getId(), new WithdrawOfferDTO(), 20L, "token");

        assertThat(result.getStatus()).isEqualTo(OfferStatus.WITHDRAWN);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC11: withdraw - Unauthorized user")
    void withdraw_ByUnauthorized_ShouldThrowException() {
        Offer offer = createSampleOffer(100L, OfferStatus.PENDING);
        offer.setOwnerUserId(20L);
        offer.setRequesterId(20L);
        offer = offerRepository.save(offer);

        Long id = offer.getId();
        assertThatThrownBy(() -> offerService.withdraw(id, new WithdrawOfferDTO(), 99L, "token"))
                .isInstanceOf(IllegalStateException.class);
    }

    // =======================================================================
    // PHẦN 6: Read Operations & Aggregation
    // =======================================================================

    @Test
    @Transactional
    @DisplayName("OFF-TC12: findById - Existing vs Non-existing")
    void findById_ShouldWork() throws IdInvalidException {
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer = offerRepository.save(offer);

        assertThat(offerService.findById(offer.getId())).isNotNull();
        assertThatThrownBy(() -> offerService.findById(999L)).isInstanceOf(IdInvalidException.class);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC13: getByIdWithUser - Aggregation test")
    void getByIdWithUser_ShouldAggregateData() throws Exception {
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer.setRequesterId(10L);
        offer = offerRepository.save(offer);

        // Mock external services
        ObjectNode emp = objectMapper.createObjectNode().put("name", "John Doe");
        ObjectNode pos = objectMapper.createObjectNode().put("level", "Senior");
        emp.set("position", pos);
        
        ObjectNode cand = objectMapper.createObjectNode().put("name", "Jane Smith").put("jobPositionId", 500L);
        ObjectNode dept = objectMapper.createObjectNode().put("name", "HR");
        ObjectNode workflow = objectMapper.createObjectNode().put("status", "ACTIVE");

        RecruitmentRequest rr = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr.setDepartmentId(9L);
        rr = recruitmentRequestRepository.save(rr);

        JobPosition jp = new JobPosition();
        jp.setTitle("Java Dev");
        jp.setRecruitmentRequest(rr);
        
        when(userService.getEmployeeById(10L, "token")).thenReturn(ResponseEntity.ok(emp));
        when(candidateClient.getCandidateById(100L, "token")).thenReturn(ResponseEntity.ok(cand));
        when(jobPositionService.findById(500L)).thenReturn(jp);
        when(userService.getDepartmentById(9L, "token")).thenReturn(ResponseEntity.ok(dept));
        when(workflowServiceClient.getWorkflowInfoByRequestId(any(), any(), any(), any())).thenReturn(workflow);

        OfferWithUserDTO result = offerService.getByIdWithUser(offer.getId(), "token");

        assertThat(result.getJobPositionTitle()).isEqualTo("Java Dev");
        assertThat(result.getDepartmentName()).isEqualTo("HR");
        assertThat(result.getLevelName()).isEqualTo("Senior");
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC14: getByIdDetail - Detail aggregation")
    void getByIdDetail_ShouldWork() throws Exception {
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer = offerRepository.save(offer);

        ObjectNode cand = objectMapper.createObjectNode().put("name", "Jane").put("email", "jane@test.com").put("jobPositionId", 500L);
        when(candidateClient.getCandidateById(100L, "token")).thenReturn(ResponseEntity.ok(cand));
        // Mocking other dependencies to avoid null errors
        when(userService.getEmployeeById(anyLong(), anyString())).thenReturn(ResponseEntity.ok(objectMapper.createObjectNode()));
        when(workflowServiceClient.getWorkflowInfoByRequestId(any(), any(), any(), any())).thenReturn(objectMapper.createObjectNode());
        
        JobPosition jp = new JobPosition();
        jp.setTitle("Dev");
        when(jobPositionService.findById(500L)).thenReturn(jp);

        OfferDetailDTO result = offerService.getByIdDetail(offer.getId(), "token");

        assertThat(result.getCandidateName()).isEqualTo("Jane");
        assertThat(result.getJobPositionTitle()).isEqualTo("Dev");
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC15: getAllWithFilters - Paged results")
    void getAllWithFilters_ShouldReturnPaged() {
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offerRepository.save(offer);

        when(userService.getEmployeesByIds(any(), anyString())).thenReturn(Map.of());

        PaginationDTO result = offerService.getAllWithFilters("DRAFT", 1L, null, "token", PageRequest.of(0, 10));

        assertThat(result.getMeta().getTotal()).isEqualTo(1);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC16: findAllWithFilters - List results")
    void findAllWithFilters_ShouldReturnList() {
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offerRepository.save(offer);

        List<Offer> results = offerService.findAllWithFilters("DRAFT", 100L, 100L, 1L, null, null, null, null, null);

        assertThat(results).isNotEmpty();
    }

    // =======================================================================
    // BỔ SUNG: 100% Branch Coverage & Edge Cases
    // =======================================================================

    @Test
    @Transactional
    @DisplayName("OFF-TC17: submit - IDs already set")
    void submit_WithIdsSet_ShouldNotOverwrite() throws IdInvalidException {
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer.setRequesterId(500L);
        offer.setOwnerUserId(600L);
        offer = offerRepository.save(offer);

        Offer result = offerService.submit(offer.getId(), 1L, "token");

        assertThat(result.getRequesterId()).isEqualTo(500L);
        assertThat(result.getOwnerUserId()).isEqualTo(600L);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC18: getAllWithFilters - Invalid status")
    void getAllWithFilters_InvalidStatus_ShouldIgnore() {
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offerRepository.save(offer);

        PaginationDTO result = offerService.getAllWithFilters("INVALID_STATUS", null, null, "token", PageRequest.of(0, 10));
        // Should fallback to no status filter and return the offer
        assertThat(result.getMeta().getTotal()).isGreaterThan(0);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC19: delete - Soft delete check")
    void delete_ShouldSetIsActiveFalse() throws IdInvalidException {
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer = offerRepository.save(offer);

        offerService.delete(offer.getId());

        Offer deleted = offerRepository.findById(offer.getId()).get();
        assertThat(deleted.getIsActive()).isFalse();
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC20: getByIdWithUser - UserClient error")
    void getByIdWithUser_UserClientError_ShouldThrowException() {
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer.setRequesterId(10L);
        offer = offerRepository.save(offer);

        when(userService.getEmployeeById(10L, "token")).thenReturn(ResponseEntity.status(500).build());

        Long id = offer.getId();
        assertThatThrownBy(() -> offerService.getByIdWithUser(id, "token"))
                .isInstanceOf(com.example.job_service.exception.UserClientException.class);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC21: submit - requesterId null")
    void submit_DraftOfferWithNullRequesterId_ShouldSetRequesterIdFromActor() throws IdInvalidException {
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer.setRequesterId(null);
        offer.setWorkflowId(50L);
        offer = offerRepository.save(offer);

        offerService.submit(offer.getId(), 99L, "token");

        Offer updated = offerRepository.findById(offer.getId()).get();
        assertThat(updated.getRequesterId()).isEqualTo(99L);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC22: submit - Offer PENDING")
    void submit_PendingOffer_ShouldThrowIllegalStateException() {
        Offer offer = createSampleOffer(100L, OfferStatus.PENDING);
        offer = offerRepository.save(offer);

        Long id = offer.getId();
        assertThatThrownBy(() -> offerService.submit(id, 20L, "token"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC23: approveStep - Offer DRAFT")
    void approveStep_DraftOffer_ShouldThrowIllegalStateException() {
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer = offerRepository.save(offer);

        Long id = offer.getId();
        assertThatThrownBy(() -> offerService.approveStep(id, new ApproveOfferDTO(), 50L, "token"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC24: rejectStep - Offer DRAFT")
    void rejectStep_DraftOffer_ShouldThrowIllegalStateException() {
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer = offerRepository.save(offer);

        Long id = offer.getId();
        assertThatThrownBy(() -> offerService.rejectStep(id, new RejectOfferDTO(), 50L, "token"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC25: returnOffer - Không hỗ trợ")
    void returnOffer_AnyOffer_ShouldAlwaysThrowIllegalStateException() {
        assertThatThrownBy(() -> offerService.returnOffer(1L, new ReturnOfferDTO(), 10L, "token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("return");
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC26: cancel - Offer PENDING")
    void cancel_PendingOffer_ShouldSetCancelledStatusAndPublishEvent() throws IdInvalidException {
        Offer offer = createSampleOffer(100L, OfferStatus.PENDING);
        offer = offerRepository.save(offer);

        Offer result = offerService.cancel(offer.getId(), new CancelOfferDTO(), 20L, "token");

        assertThat(result.getStatus()).isEqualTo(OfferStatus.CANCELLED);
        verify(workflowProducer).publishEvent(argThat(e -> e.getEventType().equals("REQUEST_CANCELLED")));
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC27: cancel - Offer APPROVED")
    void cancel_ApprovedOffer_ShouldThrowIllegalStateException() {
        Offer offer = createSampleOffer(100L, OfferStatus.APPROVED);
        offer = offerRepository.save(offer);

        Long id = offer.getId();
        assertThatThrownBy(() -> offerService.cancel(id, new CancelOfferDTO(), 10L, "token"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC28: withdraw - by Requester")
    void withdraw_PendingOfferByRequester_ShouldSetWithdrawnStatus() throws IdInvalidException {
        Offer offer = createSampleOffer(100L, OfferStatus.PENDING);
        offer.setOwnerUserId(20L);
        offer.setRequesterId(30L);
        offer = offerRepository.save(offer);

        Offer result = offerService.withdraw(offer.getId(), new WithdrawOfferDTO(), 30L, "token");

        assertThat(result.getStatus()).isEqualTo(OfferStatus.WITHDRAWN);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC29: withdraw - Offer DRAFT")
    void withdraw_DraftOffer_ShouldThrowIllegalStateException() {
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer = offerRepository.save(offer);

        Long id = offer.getId();
        assertThatThrownBy(() -> offerService.withdraw(id, new WithdrawOfferDTO(), 10L, "token"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC30: getByIdWithUserAndMetadata - Wrapper")
    void getByIdWithUserAndMetadata_ShouldWrapInSingleResponseDTO() throws Exception {
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer = offerRepository.save(offer);

        SingleResponseDTO<OfferWithUserDTO> result = offerService.getByIdWithUserAndMetadata(offer.getId(), "token");

        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getId()).isEqualTo(offer.getId());
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC31: convertToDetailDTO - Level fallback")
    void convertToDetailDTO_LevelNameFallback_ShouldUsePositionName() throws Exception {
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer.setRequesterId(10L);
        offer = offerRepository.save(offer);

        ObjectNode emp = objectMapper.createObjectNode();
        ObjectNode pos = objectMapper.createObjectNode().put("name", "Team Lead"); // No 'level' field
        emp.set("position", pos);

        when(userService.getEmployeeById(10L, "token")).thenReturn(ResponseEntity.ok(emp));

        OfferDetailDTO result = offerService.getByIdDetail(offer.getId(), "token");

        assertThat(result.getLevelName()).isEqualTo("Team Lead");
    }

    // =======================================================================
    // "BUG TRAPS" (Sử dụng đặc tả từ docs/system-test)
    // =======================================================================

    @Test
    @Transactional
    @DisplayName("OFF-TC32 [Bẫy Lỗi] create - Onboarding quá khứ")
    void create_PastOnboardingDate_ShouldFail_ButAllows() {
        CreateOfferDTO dto = new CreateOfferDTO();
        dto.setCandidateId(100L);
        dto.setOnboardingDate(LocalDate.now().minusDays(10)); // Quá khứ

        // 2. Thực thi & Kiểm tra
        // Chú ý: Test case này sẽ FAIL cho đến khi Bug được fix trong OfferService
        assertThatThrownBy(() -> offerService.create(dto))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Ngày nhận việc không được ở trong quá khứ");
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC33 [Bẫy Lỗi] create - Lương âm")
    void create_NegativeSalary_ShouldFail_ButAllows() {
        CreateOfferDTO dto = new CreateOfferDTO();
        dto.setCandidateId(100L);
        dto.setBasicSalary(-1000000L); // Lương âm

        // 2. Thực thi & Kiểm tra
        // Chú ý: Test case này sẽ FAIL cho đến khi Bug được fix trong OfferService
        assertThatThrownBy(() -> offerService.create(dto))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Lương cơ bản không được là số âm");
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC34: update - Partial update")
    void update_PartialUpdate_ShouldOnlyUpdateNonNullFields() throws IdInvalidException {
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer.setBasicSalary(20000000L);
        offer = offerRepository.save(offer);

        UpdateOfferDTO dto = new UpdateOfferDTO();
        dto.setBasicSalary(22000000L);
        dto.setCandidateId(null); // Should not change

        Offer updated = offerService.update(offer.getId(), dto);
        assertThat(updated.getBasicSalary()).isEqualTo(22000000L);
        assertThat(updated.getCandidateId()).isEqualTo(100L);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC35: cancel - Offer REJECTED")
    void cancel_RejectedOffer_ShouldThrowIllegalStateException() {
        Offer offer = createSampleOffer(100L, OfferStatus.REJECTED);
        offer = offerRepository.save(offer);

        Long id = offer.getId();
        assertThatThrownBy(() -> offerService.cancel(id, new CancelOfferDTO(), 1L, "token"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC36: getAllWithFilters - Empty status")
    void getAllWithFilters_EmptyStatus_ShouldIgnore() {
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offerRepository.save(offer);

        PaginationDTO result = offerService.getAllWithFilters("   ", null, null, "token", PageRequest.of(0, 10));
        assertThat(result.getMeta().getTotal()).isGreaterThan(0);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC37: convertToWithUserDTO - Edge cases")
    void convertToWithUserDTO_AggregationEdgeCases_ShouldHandleNullsAndErrors() throws Exception {
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer.setRequesterId(10L);
        offer = offerRepository.save(offer);

        // 1. Candidate body null
        when(candidateClient.getCandidateById(100L, "token")).thenReturn(ResponseEntity.ok(null));
        OfferWithUserDTO res1 = offerService.getByIdWithUser(offer.getId(), "token");
        assertThat(res1.getCandidate()).isNull();

        // 2. Candidate no jobPositionId
        ObjectNode candNoJp = objectMapper.createObjectNode().put("name", "No JP");
        when(candidateClient.getCandidateById(100L, "token")).thenReturn(ResponseEntity.ok(candNoJp));
        OfferWithUserDTO res2 = offerService.getByIdWithUser(offer.getId(), "token");
        assertThat(res2.getJobPositionTitle()).isNull();

        // 3. Candidate jobPositionId is null
        ObjectNode candNullJp = objectMapper.createObjectNode().put("name", "Null JP");
        candNullJp.putNull("jobPositionId");
        when(candidateClient.getCandidateById(100L, "token")).thenReturn(ResponseEntity.ok(candNullJp));
        OfferWithUserDTO res2b = offerService.getByIdWithUser(offer.getId(), "token");
        assertThat(res2b.getJobPositionTitle()).isNull();

        // 4. JobPositionService throws exception (catch at line 315)
        ObjectNode candWithJp = objectMapper.createObjectNode().put("jobPositionId", 501L);
        when(candidateClient.getCandidateById(100L, "token")).thenReturn(ResponseEntity.ok(candWithJp));
        when(jobPositionService.findById(501L)).thenThrow(new RuntimeException("Service Error"));
        OfferWithUserDTO res3 = offerService.getByIdWithUser(offer.getId(), "token");
        assertThat(res3.getJobPositionTitle()).isNull();

        // 5. JobPosition has no RecruitmentRequest (branch line 304)
        ObjectNode candWithJp2 = objectMapper.createObjectNode().put("jobPositionId", 502L);
        when(candidateClient.getCandidateById(100L, "token")).thenReturn(ResponseEntity.ok(candWithJp2));
        JobPosition jpNoRR = new JobPosition();
        when(jobPositionService.findById(502L)).thenReturn(jpNoRR);
        OfferWithUserDTO res4 = offerService.getByIdWithUser(offer.getId(), "token");
        assertThat(res4.getDepartmentName()).isNull();

        // 6. JobPosition has RecruitmentRequest but DepartmentId is null
        JobPosition jpNoDept = new JobPosition();
        jpNoDept.setRecruitmentRequest(new RecruitmentRequest());
        when(jobPositionService.findById(502L)).thenReturn(jpNoDept);
        OfferWithUserDTO res4b = offerService.getByIdWithUser(offer.getId(), "token");
        assertThat(res4b.getDepartmentName()).isNull();

        // 7. Dept response body null (branch line 308)
        ObjectNode candWithJp3 = objectMapper.createObjectNode().put("jobPositionId", 503L);
        when(candidateClient.getCandidateById(100L, "token")).thenReturn(ResponseEntity.ok(candWithJp3));
        JobPosition jpWithRR = new JobPosition();
        RecruitmentRequest rr = new RecruitmentRequest();
        rr.setDepartmentId(9L);
        jpWithRR.setRecruitmentRequest(rr);
        when(jobPositionService.findById(503L)).thenReturn(jpWithRR);
        when(userService.getDepartmentById(9L, "token")).thenReturn(ResponseEntity.ok(null));
        OfferWithUserDTO res5 = offerService.getByIdWithUser(offer.getId(), "token");
        assertThat(res5.getDepartmentName()).isNull();

        // 8. Dept response success but body has no 'name' (branch line 310)
        when(userService.getDepartmentById(9L, "token")).thenReturn(ResponseEntity.ok(objectMapper.createObjectNode()));
        OfferWithUserDTO res5b = offerService.getByIdWithUser(offer.getId(), "token");
        assertThat(res5b.getDepartmentName()).isNull();

        // 9. Employee has no 'position' (branch line 331)
        ObjectNode empNoPos = objectMapper.createObjectNode().put("name", "No Pos");
        when(userService.getEmployeeById(10L, "token")).thenReturn(ResponseEntity.ok(empNoPos));
        OfferWithUserDTO res9 = offerService.getByIdWithUser(offer.getId(), "token");
        assertThat(res9.getLevelName()).isNull();

        // 10. Employee has 'position' but no level/name (branch line 335 else if)
        ObjectNode empEmptyPos = objectMapper.createObjectNode().put("name", "Empty Pos");
        empEmptyPos.set("position", objectMapper.createObjectNode());
        when(userService.getEmployeeById(10L, "token")).thenReturn(ResponseEntity.ok(empEmptyPos));
        OfferWithUserDTO res10 = offerService.getByIdWithUser(offer.getId(), "token");
        assertThat(res10.getLevelName()).isNull();

        // 11. WorkflowInfo null (branch line 352)
        when(workflowServiceClient.getWorkflowInfoByRequestId(any(), any(), any(), any())).thenReturn(null);
        OfferWithUserDTO res11 = offerService.getByIdWithUser(offer.getId(), "token");
        assertThat(res11.getWorkflowInfo()).isNull();

        // 12. Requester null, dùng OwnerUserId (branch line 325)
        offer.setRequesterId(null);
        offer.setOwnerUserId(20L);
        offerRepository.save(offer);
        when(userService.getEmployeeById(20L, "token")).thenReturn(ResponseEntity.ok(objectMapper.createObjectNode()));
        OfferWithUserDTO res12 = offerService.getByIdWithUser(offer.getId(), "token");
        assertThat(res12).isNotNull();
    }

    @Test
    @DisplayName("OFF-TC38: getWithUser - handle null ids")

    void getByIdWithUser_WithVariousNullIds_ShouldHandleGracefully() throws IdInvalidException {
        OfferRepository mockRepo = org.mockito.Mockito.mock(OfferRepository.class);
        OfferService localService = new OfferService(mockRepo, userService, workflowProducer, workflowServiceClient, candidateClient, jobPositionService);

        Offer mockOffer = new Offer();
        mockOffer.setCandidateId(null);
        mockOffer.setRequesterId(10L);
        when(mockRepo.findById(999L)).thenReturn(Optional.of(mockOffer));
        
        localService.getByIdWithUser(999L, "token");

        mockOffer.setRequesterId(null);
        mockOffer.setOwnerUserId(null);
        localService.getByIdWithUser(999L, "token");
    }

    @Test
    @DisplayName("OFF-TC39: getWithUser - handle service failures")
    void getByIdWithUser_WithUserServiceFailures_ShouldHandleGracefully() throws IdInvalidException {
        OfferRepository mockRepo = org.mockito.Mockito.mock(OfferRepository.class);
        OfferService localService = new OfferService(mockRepo, userService, workflowProducer, workflowServiceClient, candidateClient, jobPositionService);

        Offer mockOffer = new Offer();
        mockOffer.setOwnerUserId(20L);
        when(mockRepo.findById(999L)).thenReturn(Optional.of(mockOffer));
        
        when(userService.getEmployeeById(20L, "token")).thenReturn(ResponseEntity.status(500).build());
        localService.getByIdWithUser(999L, "token");
        
        when(userService.getEmployeeById(20L, "token")).thenReturn(ResponseEntity.ok(null));
        localService.getByIdWithUser(999L, "token");
    }

    @Test
    @DisplayName("OFF-TC40: getByIdDetail - handle various nulls")
    void getByIdDetail_WithVariousNullFields_ShouldHandleGracefully() throws IdInvalidException {
        OfferRepository mockRepo = org.mockito.Mockito.mock(OfferRepository.class);
        OfferService localService = new OfferService(mockRepo, userService, workflowProducer, workflowServiceClient, candidateClient, jobPositionService);

        Offer mockOffer = new Offer();
        mockOffer.setRequesterId(null);
        when(mockRepo.findById(999L)).thenReturn(Optional.of(mockOffer));
        
        localService.getByIdDetail(999L, "token");

        mockOffer.setRequesterId(10L);
        when(userService.getEmployeeById(10L, "token")).thenReturn(ResponseEntity.status(404).build());
        localService.getByIdDetail(999L, "token");

        mockOffer.setCandidateId(null);
        localService.getByIdDetail(999L, "token");

        mockOffer.setCandidateId(100L);
        when(candidateClient.getCandidateById(100L, "token")).thenReturn(ResponseEntity.status(500).build());
        localService.getByIdDetail(999L, "token");
    }

    @Test
    @DisplayName("OFF-TC41: getByIdDetail - handle missing fields")
    void getByIdDetail_WithMissingEmployeePositionName_ShouldHandleGracefully() throws IdInvalidException {
        OfferRepository mockRepo = org.mockito.Mockito.mock(OfferRepository.class);
        OfferService localService = new OfferService(mockRepo, userService, workflowProducer, workflowServiceClient, candidateClient, jobPositionService);

        Offer mockOffer = new Offer();
        mockOffer.setOwnerUserId(20L);
        when(mockRepo.findById(999L)).thenReturn(Optional.of(mockOffer));

        ObjectNode empNoName = objectMapper.createObjectNode();
        empNoName.set("position", objectMapper.createObjectNode());
        when(userService.getEmployeeById(20L, "token")).thenReturn(ResponseEntity.ok(empNoName));
        
        localService.getByIdDetail(999L, "token");
        
        when(workflowServiceClient.getWorkflowInfoByRequestId(any(), any(), any(), any())).thenReturn(null);
        mockOffer.setWorkflowId(100L);
        localService.getByIdDetail(999L, "token");
    }
}
