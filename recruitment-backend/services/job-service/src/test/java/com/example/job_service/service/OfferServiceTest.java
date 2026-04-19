package com.example.job_service.service;

import com.example.job_service.dto.offer.ApproveOfferDTO;
import com.example.job_service.dto.offer.CancelOfferDTO;
import com.example.job_service.dto.offer.CreateOfferDTO;
import com.example.job_service.dto.offer.RejectOfferDTO;
import com.example.job_service.dto.offer.ReturnOfferDTO;
import com.example.job_service.dto.offer.UpdateOfferDTO;
import com.example.job_service.dto.offer.WithdrawOfferDTO;
import com.example.job_service.exception.IdInvalidException;
import com.example.job_service.messaging.OfferWorkflowProducer;
import com.example.job_service.model.Offer;
import com.example.job_service.repository.OfferRepository;
import com.example.job_service.utils.enums.OfferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho OfferService - Module 8: Offer (Hợp đồng / Lương thưởng).
 *
 * Chiến lược:
 * - Tất cả dependency (OfferRepository, UserClient, OfferWorkflowProducer,...) đều được mock.
 * - State Machine Offer: DRAFT -> PENDING -> APPROVED/REJECTED/WITHDRAWN/CANCELLED
 * - Kiểm chứng (CheckDB): verify(offerRepository).save(argThat(...)) hoặc ArgumentCaptor.
 * - Dọn dẹp (Rollback): Không dùng DB thật. Mockito tự động reset sau mỗi test.
 *
 * Lưu ý: OfferService có các hàm publishWorkflowEvent() - mock workflowProducer.publishEvent().
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OfferService Unit Tests")
class OfferServiceTest {

    // -----------------------------------------------------------------------
    // Mock dependencies - tất cả giao tiếp ngoài service đều được mock
    // -----------------------------------------------------------------------

    @Mock
    private OfferRepository offerRepository;

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

    // Service đang được kiểm tra (System Under Test)
    @InjectMocks
    private OfferService offerService;

    // -----------------------------------------------------------------------
    // Dữ liệu dùng chung (Fixture)
    // -----------------------------------------------------------------------

    private Offer draftOffer;
    private Offer pendingOffer;

    @BeforeEach
    void setUp() {
        // Offer ở trạng thái DRAFT (bản nháp)
        draftOffer = new Offer();
        draftOffer.setId(1L);
        draftOffer.setCandidateId(100L);
        draftOffer.setBasicSalary(20_000_000L);
        draftOffer.setProbationSalaryRate(85);
        draftOffer.setOnboardingDate(LocalDate.of(2026, 6, 1));
        draftOffer.setProbationPeriod(2);
        draftOffer.setStatus(OfferStatus.DRAFT);
        draftOffer.setIsActive(true);
        draftOffer.setWorkflowId(50L);
        draftOffer.setOwnerUserId(10L);
        draftOffer.setRequesterId(10L);

        // Offer ở trạng thái PENDING (chờ duyệt)
        pendingOffer = new Offer();
        pendingOffer.setId(2L);
        pendingOffer.setCandidateId(200L);
        pendingOffer.setBasicSalary(30_000_000L);
        pendingOffer.setStatus(OfferStatus.PENDING);
        pendingOffer.setIsActive(true);
        pendingOffer.setWorkflowId(60L);
        pendingOffer.setOwnerUserId(20L);
        pendingOffer.setRequesterId(20L);
    }

    // =======================================================================
    // PHẦN 1: Hàm create()
    // =======================================================================

    // Test Case ID: OFF-TC01
    // Mục tiêu: Tạo offer mới hợp lệ, phải lưu với DRAFT, isActive=true
    @Test
    @DisplayName("OFF-TC01: create - DTO hợp lệ, phải lưu với status DRAFT và isActive=true")
    void create_ValidDto_ShouldSaveOfferWithDraftStatusAndIsActiveTrue() {
        // Chuẩn bị: DTO hợp lệ
        CreateOfferDTO dto = new CreateOfferDTO();
        dto.setCandidateId(100L);
        dto.setBasicSalary(20_000_000L);
        dto.setOnboardingDate(LocalDate.of(2026, 6, 1));
        dto.setProbationPeriod(2);
        dto.setWorkflowId(50L);

        // Dùng ArgumentCaptor để bắt đối tượng truyền vào save()
        ArgumentCaptor<Offer> offerCaptor = ArgumentCaptor.forClass(Offer.class);
        when(offerRepository.save(offerCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        // Thực thi
        Offer result = offerService.create(dto);

        // Kiểm tra: Giá trị đã được set đúng theo đặc tả hệ thống
        Offer captured = offerCaptor.getValue();
        assertThat(captured.getStatus()).isEqualTo(OfferStatus.DRAFT);
        assertThat(captured.getIsActive()).isTrue();
        assertThat(captured.getCandidateId()).isEqualTo(100L);
        assertThat(captured.getBasicSalary()).isEqualTo(20_000_000L);

        // Minh chứng (CheckDB): save() được gọi đúng 1 lần
        verify(offerRepository, times(1)).save(any(Offer.class));
    }

    // =======================================================================
    // PHẦN 2: Hàm update()
    // =======================================================================

    // Test Case ID: OFF-TC02
    // Mục tiêu: Offer ở DRAFT, cập nhật đầy đủ các trường (Đạt 100% Branch Coverage cho hàm update)
    @Test
    @DisplayName("OFF-TC02: update - Offer DRAFT, phải cập nhật đầy đủ các trường không null")
    void update_DraftOfferWithFullFields_ShouldUpdateAllNonNullFields() throws IdInvalidException {
        // Chuẩn bị: DTO có tất cả các trường để phủ hết các nhánh if (dto.getXXX() != null)
        UpdateOfferDTO dto = new UpdateOfferDTO();
        dto.setCandidateId(101L);
        dto.setBasicSalary(25_000_000L);
        dto.setProbationSalaryRate(90);
        dto.setOnboardingDate(LocalDate.of(2026, 7, 1));
        dto.setProbationPeriod(3);
        dto.setNotes("Updated notes and conditions");

        when(offerRepository.findById(1L)).thenReturn(Optional.of(draftOffer));
        when(offerRepository.save(any(Offer.class))).thenAnswer(inv -> inv.getArgument(0));

        // Thực thi
        Offer result = offerService.update(1L, dto);

        // Kiểm tra: Tất cả các trường đã được cập nhật chính xác từ DTO
        assertThat(result.getCandidateId()).isEqualTo(101L);
        assertThat(result.getBasicSalary()).isEqualTo(25_000_000L);
        assertThat(result.getProbationSalaryRate()).isEqualTo(90);
        assertThat(result.getOnboardingDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(result.getProbationPeriod()).isEqualTo(3);
        assertThat(result.getNotes()).isEqualTo("Updated notes and conditions");

        // Minh chứng (CheckDB): save() được gọi đúng 1 lần
        verify(offerRepository, times(1)).save(any(Offer.class));
    }

    // Test Case ID: OFF-TC03
    // Mục tiêu: Offer ở PENDING, phải throw IllegalStateException (không cho update)
    @Test
    @DisplayName("OFF-TC03: update - Offer PENDING, phải throw IllegalStateException")
    void update_PendingOffer_ShouldThrowIllegalStateException() {
        // Chuẩn bị: Offer đang PENDING
        when(offerRepository.findById(2L)).thenReturn(Optional.of(pendingOffer));

        UpdateOfferDTO dto = new UpdateOfferDTO();
        dto.setBasicSalary(99_000_000L);

        // Thực thi & Kiểm tra: Nhánh status != DRAFT -> throw
        assertThatThrownBy(() -> offerService.update(2L, dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT");

        // Minh chứng (CheckDB): save() KHÔNG được gọi
        verify(offerRepository, never()).save(any());
    }

    // Test Case ID: OFF-TC04
    // Mục tiêu: Offer ở APPROVED, phải throw IllegalStateException
    @Test
    @DisplayName("OFF-TC04: update - Offer APPROVED, phải throw IllegalStateException")
    void update_ApprovedOffer_ShouldThrowIllegalStateException() {
        // Chuẩn bị
        Offer approvedOffer = new Offer();
        approvedOffer.setId(3L);
        approvedOffer.setStatus(OfferStatus.APPROVED);
        when(offerRepository.findById(3L)).thenReturn(Optional.of(approvedOffer));

        // Thực thi & Kiểm tra
        assertThatThrownBy(() -> offerService.update(3L, new UpdateOfferDTO()))
                .isInstanceOf(IllegalStateException.class);
    }

    // =======================================================================
    // PHẦN 3: Hàm submit()
    // =======================================================================

    // Test Case ID: OFF-TC05
    // Mục tiêu: Offer DRAFT với workflowId -> chuyển sang PENDING, gọi workflowProducer
    @Test
    @DisplayName("OFF-TC05: submit - Offer DRAFT có workflowId, phải PENDING và publish event")
    void submit_DraftOfferWithWorkflowId_ShouldChangeStatusToPendingAndPublishEvent() throws IdInvalidException {
        // Chuẩn bị: draftOffer có workflowId=50L
        when(offerRepository.findById(1L)).thenReturn(Optional.of(draftOffer));
        when(offerRepository.save(any(Offer.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(workflowProducer).publishEvent(any());

        // Thực thi
        Offer result = offerService.submit(1L, 10L, "Bearer token");

        // Kiểm tra: Status chuyển sang PENDING, submittedAt được set
        assertThat(result.getStatus()).isEqualTo(OfferStatus.PENDING);
        assertThat(result.getSubmittedAt()).isNotNull();

        // Minh chứng (CheckDB): save() và publishEvent() được gọi
        verify(offerRepository, times(1)).save(any(Offer.class));
        verify(workflowProducer, times(1)).publishEvent(any());
    }

    // Test Case ID: OFF-TC06
    // Mục tiêu: Offer DRAFT, requesterId null -> phải set requesterId bằng actorId
    @Test
    @DisplayName("OFF-TC06: submit - requesterId null, phải set requesterId từ actorId")
    void submit_DraftOfferWithNullRequesterId_ShouldSetRequesterIdFromActor() throws IdInvalidException {
        // Chuẩn bị: requesterId chưa được set
        draftOffer.setRequesterId(null);
        ArgumentCaptor<Offer> captor = ArgumentCaptor.forClass(Offer.class);
        when(offerRepository.findById(1L)).thenReturn(Optional.of(draftOffer));
        when(offerRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(workflowProducer).publishEvent(any());

        // Thực thi
        offerService.submit(1L, 99L, "Bearer token");

        // Kiểm tra: requesterId phải được set bằng actorId=99
        assertThat(captor.getValue().getRequesterId()).isEqualTo(99L);
    }

    // Test Case ID: OFF-TC07
    // Mục tiêu: Offer đã PENDING -> throw IllegalStateException
    @Test
    @DisplayName("OFF-TC07: submit - Offer PENDING, phải throw IllegalStateException")
    void submit_PendingOffer_ShouldThrowIllegalStateException() {
        // Chuẩn bị: Offer đang PENDING, không thể submit lại
        when(offerRepository.findById(2L)).thenReturn(Optional.of(pendingOffer));

        // Thực thi & Kiểm tra
        assertThatThrownBy(() -> offerService.submit(2L, 20L, "token"))
                .isInstanceOf(IllegalStateException.class);

        // Minh chứng (CheckDB): save() KHÔNG được gọi
        verify(offerRepository, never()).save(any());
    }

    // Test Case ID: OFF-TC08
    // Mục tiêu: Offer DRAFT nhưng workflowId = null -> throw IllegalStateException
    @Test
    @DisplayName("OFF-TC08: submit - workflowId null, phải throw IllegalStateException")
    void submit_DraftOfferWithNullWorkflowId_ShouldThrowIllegalStateException() {
        // Chuẩn bị: Offer DRAFT nhưng chưa có workflowId
        draftOffer.setWorkflowId(null);
        when(offerRepository.findById(1L)).thenReturn(Optional.of(draftOffer));

        // Thực thi & Kiểm tra
        assertThatThrownBy(() -> offerService.submit(1L, 10L, "token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WorkflowId");

        // Minh chứng (CheckDB): save() KHÔNG được gọi
        verify(offerRepository, never()).save(any());
    }

    // =======================================================================
    // PHẦN 4: Hàm approveStep()
    // =======================================================================

    // Test Case ID: OFF-TC09
    // Mục tiêu: Offer PENDING -> save và publish event "REQUEST_APPROVED"
    @Test
    @DisplayName("OFF-TC09: approveStep - Offer PENDING, phải save và publish REQUEST_APPROVED")
    void approveStep_PendingOffer_ShouldSaveAndPublishApprovalEvent() throws IdInvalidException {
        // Chuẩn bị
        ApproveOfferDTO dto = new ApproveOfferDTO();
        when(offerRepository.findById(2L)).thenReturn(Optional.of(pendingOffer));
        when(offerRepository.save(any(Offer.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(workflowProducer).publishEvent(any());

        // Thực thi
        Offer result = offerService.approveStep(2L, dto, 50L, "token");

        // Kiểm tra: save() và publishEvent() được gọi
        verify(offerRepository, times(1)).save(any(Offer.class));
        verify(workflowProducer, times(1)).publishEvent(any());
        assertThat(result).isNotNull();
    }

    // Test Case ID: OFF-TC10
    // Mục tiêu: Offer DRAFT -> throw IllegalStateException (chỉ duyệt từ PENDING)
    @Test
    @DisplayName("OFF-TC10: approveStep - Offer DRAFT, phải throw IllegalStateException")
    void approveStep_DraftOffer_ShouldThrowIllegalStateException() {
        // Chuẩn bị: Offer ở DRAFT, không phải PENDING
        when(offerRepository.findById(1L)).thenReturn(Optional.of(draftOffer));

        // Thực thi & Kiểm tra
        assertThatThrownBy(() -> offerService.approveStep(1L, new ApproveOfferDTO(), 50L, "token"))
                .isInstanceOf(IllegalStateException.class);

        // Minh chứng (CheckDB): save() KHÔNG được gọi
        verify(offerRepository, never()).save(any());
    }

    // =======================================================================
    // PHẦN 5: Hàm rejectStep()
    // =======================================================================

    // Test Case ID: OFF-TC11
    // Mục tiêu: Offer PENDING -> set REJECTED và publish event
    @Test
    @DisplayName("OFF-TC11: rejectStep - Offer PENDING, phải set REJECTED và publish event")
    void rejectStep_PendingOffer_ShouldSetRejectedStatusAndPublishEvent() throws IdInvalidException {
        // Chuẩn bị
        RejectOfferDTO dto = new RejectOfferDTO();
        when(offerRepository.findById(2L)).thenReturn(Optional.of(pendingOffer));
        when(offerRepository.save(any(Offer.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(workflowProducer).publishEvent(any());

        // Thực thi
        Offer result = offerService.rejectStep(2L, dto, 50L, "token");

        // Kiểm tra: Status là REJECTED
        assertThat(result.getStatus()).isEqualTo(OfferStatus.REJECTED);
        // Minh chứng (CheckDB): save() và publishEvent() được gọi
        verify(offerRepository, times(1)).save(any(Offer.class));
        verify(workflowProducer, times(1)).publishEvent(any());
    }

    // Test Case ID: OFF-TC12
    // Mục tiêu: Offer DRAFT -> throw IllegalStateException
    @Test
    @DisplayName("OFF-TC12: rejectStep - Offer DRAFT, phải throw IllegalStateException")
    void rejectStep_DraftOffer_ShouldThrowIllegalStateException() {
        // Chuẩn bị
        when(offerRepository.findById(1L)).thenReturn(Optional.of(draftOffer));

        // Thực thi & Kiểm tra
        assertThatThrownBy(() -> offerService.rejectStep(1L, new RejectOfferDTO(), 50L, "token"))
                .isInstanceOf(IllegalStateException.class);
    }

    // =======================================================================
    // PHẦN 6: Hàm returnOffer() - Business rule: luôn throw
    // =======================================================================

    // Test Case ID: OFF-TC13
    // Mục tiêu: Gọi returnOffer() với bất kỳ offer nào -> luôn throw IllegalStateException
    @Test
    @DisplayName("OFF-TC13: returnOffer - Bất kỳ offer nào cũng phải throw IllegalStateException")
    void returnOffer_AnyOffer_ShouldAlwaysThrowIllegalStateException() {
        // Chuẩn bị: Không cần mock vì hàm throw ngay
        // Thực thi & Kiểm tra: Business rule mới - workflow không hỗ trợ return
        assertThatThrownBy(() -> offerService.returnOffer(1L, new ReturnOfferDTO(), 10L, "token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("return");

        // Minh chứng (CheckDB): Không có bất kỳ DB operation nào
        verify(offerRepository, never()).findById(any());
        verify(offerRepository, never()).save(any());
    }

    // =======================================================================
    // PHẦN 7: Hàm cancel()
    // =======================================================================

    // Test Case ID: OFF-TC14
    // Mục tiêu: Offer đã CANCELLED -> trả về ngay, KHÔNG gọi save() (idempotent)
    @Test
    @DisplayName("OFF-TC14: cancel - Offer đã CANCELLED, phải return ngay và không gọi save()")
    void cancel_AlreadyCancelledOffer_ShouldReturnImmediatelyWithoutSaving() throws IdInvalidException {
        // Chuẩn bị: Offer đã ở trạng thái CANCELLED
        Offer cancelledOffer = new Offer();
        cancelledOffer.setId(5L);
        cancelledOffer.setStatus(OfferStatus.CANCELLED);
        when(offerRepository.findById(5L)).thenReturn(Optional.of(cancelledOffer));

        CancelOfferDTO dto = new CancelOfferDTO();

        // Thực thi
        Offer result = offerService.cancel(5L, dto, 10L, "token");

        // Kiểm tra: Trả về chính offer đó
        assertThat(result.getStatus()).isEqualTo(OfferStatus.CANCELLED);
        // Minh chứng (CheckDB): save() KHÔNG được gọi (idempotent - không cập nhật DB)
        verify(offerRepository, never()).save(any());
        // workflowProducer KHÔNG được gọi
        verify(workflowProducer, never()).publishEvent(any());
    }

    // Test Case ID: OFF-TC15
    // Mục tiêu: Offer ở PENDING -> set CANCELLED và publish event
    @Test
    @DisplayName("OFF-TC15: cancel - Offer PENDING, phải set CANCELLED và publish event")
    void cancel_PendingOffer_ShouldSetCancelledStatusAndPublishEvent() throws IdInvalidException {
        // Chuẩn bị
        CancelOfferDTO dto = new CancelOfferDTO();
        when(offerRepository.findById(2L)).thenReturn(Optional.of(pendingOffer));
        when(offerRepository.save(any(Offer.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(workflowProducer).publishEvent(any());

        // Thực thi
        Offer result = offerService.cancel(2L, dto, 20L, "token");

        // Kiểm tra: Status là CANCELLED
        assertThat(result.getStatus()).isEqualTo(OfferStatus.CANCELLED);
        // Minh chứng (CheckDB): save() và publishEvent() được gọi
        verify(offerRepository, times(1)).save(any(Offer.class));
        verify(workflowProducer, times(1)).publishEvent(any());
    }

    // Test Case ID: OFF-TC16
    // Mục tiêu: Offer ở APPROVED -> throw (không thể cancel offer đã duyệt)
    @Test
    @DisplayName("OFF-TC16: cancel - Offer APPROVED, phải throw IllegalStateException")
    void cancel_ApprovedOffer_ShouldThrowIllegalStateException() {
        // Chuẩn bị
        Offer approvedOffer = new Offer();
        approvedOffer.setId(6L);
        approvedOffer.setStatus(OfferStatus.APPROVED);
        when(offerRepository.findById(6L)).thenReturn(Optional.of(approvedOffer));

        // Thực thi & Kiểm tra
        assertThatThrownBy(() -> offerService.cancel(6L, new CancelOfferDTO(), 10L, "token"))
                .isInstanceOf(IllegalStateException.class);

        // Minh chứng (CheckDB): save() KHÔNG được gọi
        verify(offerRepository, never()).save(any());
    }

    // =======================================================================
    // PHẦN 8: Hàm withdraw()
    // =======================================================================

    // Test Case ID: OFF-TC17
    // Mục tiêu: actorId là owner -> set WITHDRAWN và publish event
    @Test
    @DisplayName("OFF-TC17: withdraw - actorId là owner, phải set WITHDRAWN và publish event")
    void withdraw_PendingOfferByOwner_ShouldSetWithdrawnStatusAndPublishEvent() throws IdInvalidException {
        // Chuẩn bị: pendingOffer có ownerUserId=20, actorId=20
        WithdrawOfferDTO dto = new WithdrawOfferDTO();
        when(offerRepository.findById(2L)).thenReturn(Optional.of(pendingOffer));
        when(offerRepository.save(any(Offer.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(workflowProducer).publishEvent(any());

        // Thực thi: actorId=20 chính là owner
        Offer result = offerService.withdraw(2L, dto, 20L, "token");

        // Kiểm tra: Status là WITHDRAWN
        assertThat(result.getStatus()).isEqualTo(OfferStatus.WITHDRAWN);
        // Minh chứng (CheckDB): save() và publishEvent() được gọi
        verify(offerRepository, times(1)).save(any(Offer.class));
        verify(workflowProducer, times(1)).publishEvent(any());
    }

    // Test Case ID: OFF-TC18
    // Mục tiêu: actorId là requester (khác owner) -> vẫn được phép withdraw
    @Test
    @DisplayName("OFF-TC18: withdraw - actorId là requester khác owner, vẫn được phép")
    void withdraw_PendingOfferByRequester_ShouldSetWithdrawnStatus() throws IdInvalidException {
        // Chuẩn bị: ownerUserId=20, requesterId=30 (khác nhau)
        pendingOffer.setOwnerUserId(20L);
        pendingOffer.setRequesterId(30L);

        when(offerRepository.findById(2L)).thenReturn(Optional.of(pendingOffer));
        when(offerRepository.save(any(Offer.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(workflowProducer).publishEvent(any());

        // Thực thi: actorId=30 là requester (khác owner)
        Offer result = offerService.withdraw(2L, new WithdrawOfferDTO(), 30L, "token");

        // Kiểm tra: Vẫn WITHDRAWN thành công
        assertThat(result.getStatus()).isEqualTo(OfferStatus.WITHDRAWN);
    }

    // Test Case ID: OFF-TC19
    // Mục tiêu: Offer ở DRAFT -> throw (chỉ withdraw khi PENDING)
    @Test
    @DisplayName("OFF-TC19: withdraw - Offer DRAFT, phải throw IllegalStateException")
    void withdraw_DraftOffer_ShouldThrowIllegalStateException() {
        // Chuẩn bị
        when(offerRepository.findById(1L)).thenReturn(Optional.of(draftOffer));

        // Thực thi & Kiểm tra: Status không phải PENDING -> throw
        assertThatThrownBy(() -> offerService.withdraw(1L, new WithdrawOfferDTO(), 10L, "token"))
                .isInstanceOf(IllegalStateException.class);
    }

    // Test Case ID: OFF-TC20
    // Mục tiêu: actorId không phải owner lẫn requester -> throw (bảo mật)
    @Test
    @DisplayName("OFF-TC20: withdraw - actorId không phải owner/requester, phải throw IllegalStateException")
    void withdraw_PendingOfferByUnauthorizedUser_ShouldThrowIllegalStateException() {
        // Chuẩn bị: pendingOffer có ownerUserId=20, requesterId=20; actorId=99 (người lạ)
        when(offerRepository.findById(2L)).thenReturn(Optional.of(pendingOffer));

        // Thực thi & Kiểm tra: actorId=99 không phải owner lẫn requester -> throw
        assertThatThrownBy(() -> offerService.withdraw(2L, new WithdrawOfferDTO(), 99L, "token"))
                .isInstanceOf(IllegalStateException.class);

        // Minh chứng (CheckDB): save() KHÔNG được gọi
        verify(offerRepository, never()).save(any());
    }

    // =======================================================================
    // PHẦN 9: Hàm delete() - Soft delete
    // =======================================================================

    // Test Case ID: OFF-TC21
    // Mục tiêu: Soft delete -> set isActive=false, KHÔNG gọi repository.delete() vật lý
    @Test
    @DisplayName("OFF-TC21: delete - Phải set isActive=false, không gọi repository.delete()")
    void delete_ExistingOffer_ShouldSetIsActiveFalseAndReturnTrue() throws IdInvalidException {
        // Chuẩn bị: Offer đang active
        when(offerRepository.findById(1L)).thenReturn(Optional.of(draftOffer));
        when(offerRepository.save(any(Offer.class))).thenAnswer(inv -> inv.getArgument(0));

        // Thực thi
        boolean result = offerService.delete(1L);

        // Kiểm tra: Trả về true
        assertThat(result).isTrue();
        // Minh chứng (CheckDB): save() được gọi với isActive=false
        ArgumentCaptor<Offer> captor = ArgumentCaptor.forClass(Offer.class);
        verify(offerRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getIsActive()).isFalse();

        // Xác nhận delete() vật lý KHÔNG được gọi
        verify(offerRepository, never()).delete(any(Offer.class));
    }

    // =======================================================================
    // PHẦN 10: Các hàm Read / Aggregate Data (DTO)
    // =======================================================================

    // Test Case ID: OFF-TC22
    // Mục tiêu: Tìm kiếm Offer theo ID, trả về Offer nếu tồn tại, throw IdInvalidException nếu không
    @Test
    @DisplayName("OFF-TC22: findById - Trả về Offer nếu tồn tại, throw nếu không")
    void findById_ShouldReturnOffer_OrThrow() throws IdInvalidException {
        // Chuẩn bị: Mocks cho trường hợp ID hợp lệ và không hợp lệ
        when(offerRepository.findById(1L)).thenReturn(Optional.of(draftOffer));
        
        // Thực thi (Case 1)
        Offer result = offerService.findById(1L);
        
        // Kiểm tra (Case 1): ID trả về phải trùng khớp
        assertThat(result.getId()).isEqualTo(1L);

        // Chuẩn bị & Thực thi (Case 2): ID không tồn tại
        when(offerRepository.findById(99L)).thenReturn(Optional.empty());
        
        // Kiểm tra (Case 2): Phải throw IdInvalidException
        assertThatThrownBy(() -> offerService.findById(99L))
                .isInstanceOf(IdInvalidException.class);
    }

    // Test Case ID: OFF-TC23
    // Mục tiêu: Giao tiếp với các HTTP Client để tổng hợp đầy đủ dữ liệu cho OfferWithUserDTO
    @Test
    @DisplayName("OFF-TC23: getByIdWithUser - Data Aggregation đầy đủ các Client")
    void getByIdWithUser_ShouldAggregateDataCorrectly() throws Exception {
        // Chuẩn bị: Dữ liệu Mock JSON đại diện cho response từ external services (User, Candidate, Workflow)
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        
        // Mock Employee Response
        com.fasterxml.jackson.databind.node.ObjectNode employeeNode = mapper.createObjectNode();
        employeeNode.put("name", "Nguyen Van A");
        com.fasterxml.jackson.databind.node.ObjectNode positionNode = mapper.createObjectNode();
        positionNode.put("level", "Senior");
        employeeNode.set("position", positionNode);

        // Mock Candidate Response
        com.fasterxml.jackson.databind.node.ObjectNode candidateNode = mapper.createObjectNode();
        candidateNode.put("name", "Candidate B");
        candidateNode.put("jobPositionId", 500L);

        // Mock Department Response
        com.fasterxml.jackson.databind.node.ObjectNode deptNode = mapper.createObjectNode();
        deptNode.put("name", "Phòng IT");
        
        // Mock Workflow Response
        com.fasterxml.jackson.databind.node.ObjectNode workflowNode = mapper.createObjectNode();
        workflowNode.put("status", "ACTIVE");

        // Mock JobPosition
        com.example.job_service.model.JobPosition jobPos = new com.example.job_service.model.JobPosition();
        jobPos.setTitle("Java Dev");
        com.example.job_service.model.RecruitmentRequest req = new com.example.job_service.model.RecruitmentRequest();
        req.setDepartmentId(9L);
        jobPos.setRecruitmentRequest(req);

        // Mock behaviors cho toàn bộ các Feign Clients
        when(offerRepository.findById(1L)).thenReturn(Optional.of(draftOffer));
        when(userService.getEmployeeById(10L, "token")).thenReturn(org.springframework.http.ResponseEntity.ok(employeeNode));
        when(candidateClient.getCandidateById(100L, "token")).thenReturn(org.springframework.http.ResponseEntity.ok(candidateNode));
        when(jobPositionService.findById(500L)).thenReturn(jobPos);
        when(userService.getDepartmentById(9L, "token")).thenReturn(org.springframework.http.ResponseEntity.ok(deptNode));
        when(workflowServiceClient.getWorkflowInfoByRequestId(any(), any(), any(), any())).thenReturn(workflowNode);

        // Thực thi
        com.example.job_service.dto.offer.OfferWithUserDTO result = offerService.getByIdWithUser(1L, "token");

        // Kiểm tra: Các thuộc tính phân mảnh được gộp lại chính xác vào thành 1 DTO duy nhất
        assertThat(result).isNotNull();
        assertThat(result.getLevelName()).isEqualTo("Senior");
        assertThat(result.getDepartmentName()).isEqualTo("Phòng IT");
        assertThat(result.getJobPositionTitle()).isEqualTo("Java Dev");
    }

    // Test Case ID: OFF-TC24
    // Mục tiêu: Tổng hợp DTO nhưng bọc trong chuẩn SingleResponseDTO trả về cho Controller theo Base API
    @Test
    @DisplayName("OFF-TC24: getByIdWithUserAndMetadata - Bao bọc bởi SingleResponseDTO")
    void getByIdWithUserAndMetadata_ShouldWrapInSingleResponseDTO() throws Exception {
        // Chuẩn bị: Tái sử dụng/Mock nhánh tối giản, bỏ qua các client phụ bằng cách set ID = null để tránh lỗi NullPointerException
        draftOffer.setRequesterId(null);
        draftOffer.setCandidateId(null);
        draftOffer.setOwnerUserId(null);
        draftOffer.setWorkflowId(null);
        when(offerRepository.findById(1L)).thenReturn(Optional.of(draftOffer));
        
        // Thực thi
        com.example.job_service.dto.SingleResponseDTO<com.example.job_service.dto.offer.OfferWithUserDTO> result = 
            offerService.getByIdWithUserAndMetadata(1L, "token");
            
        // Kiểm tra: Dữ liệu được bọc đúng chuẩn SingleResponseDTO
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getId()).isEqualTo(1L);
    }

    // Test Case ID: OFF-TC25
    // Mục tiêu: Hàm xem chi tiết trang OfferDetail, phải map và tổng hợp đúng tên requester, candidate email/phone và thông tin JobPosition/Department
    @Test
    @DisplayName("OFF-TC25: getByIdDetail - Aggregation chi tiết cho trang Offer Detail")
    void getByIdDetail_ShouldAggregateDetailCorrectly() throws Exception {
        // Chuẩn bị: Mock Data đầy đủ để đi qua các nhánh logic JSON Parsing, JobPosition và Department
        draftOffer.setRequesterId(10L);
        draftOffer.setOwnerUserId(10L);
        draftOffer.setCandidateId(100L);
        draftOffer.setWorkflowId(50L);

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        com.fasterxml.jackson.databind.node.ObjectNode employeeNode = mapper.createObjectNode();
        employeeNode.put("name", "Nguyen Van A");
        // Thêm position node để phủ nhánh if (employee.has("position"))
        com.fasterxml.jackson.databind.node.ObjectNode posNode = mapper.createObjectNode();
        posNode.put("level", "Senior");
        employeeNode.set("position", posNode);

        com.fasterxml.jackson.databind.node.ObjectNode candidateNode = mapper.createObjectNode();
        candidateNode.put("name", "Candidate B");
        candidateNode.put("email", "b@gmail.com");
        candidateNode.put("phone", "0123");
        candidateNode.put("jobPositionId", 500L); // Để phủ nhánh lấy JobPosition

        com.fasterxml.jackson.databind.node.ObjectNode deptNode = mapper.createObjectNode();
        deptNode.put("name", "Phòng Nhân Sự");

        com.fasterxml.jackson.databind.node.ObjectNode workflowNode = mapper.createObjectNode();
        workflowNode.put("status", "COMPLETED");

        // Mock JobPosition và RecruitmentRequest
        com.example.job_service.model.JobPosition mockJob = new com.example.job_service.model.JobPosition();
        mockJob.setTitle("Java Developer");
        com.example.job_service.model.RecruitmentRequest mockRR = new com.example.job_service.model.RecruitmentRequest();
        mockRR.setDepartmentId(9L);
        mockJob.setRecruitmentRequest(mockRR);

        when(offerRepository.findById(1L)).thenReturn(Optional.of(draftOffer));

        // Sử dụng lenient() để tránh lỗi UnnecessaryStubbingException
        lenient().when(userService.getEmployeeById(10L, "token")).thenReturn(org.springframework.http.ResponseEntity.ok(employeeNode));
        lenient().when(candidateClient.getCandidateById(100L, "token")).thenReturn(org.springframework.http.ResponseEntity.ok(candidateNode));
        lenient().when(jobPositionService.findById(500L)).thenReturn(mockJob);
        lenient().when(userService.getDepartmentById(9L, "token")).thenReturn(org.springframework.http.ResponseEntity.ok(deptNode));
        lenient().when(workflowServiceClient.getWorkflowInfoByRequestId(1L, 50L, "OFFER", "token")).thenReturn(workflowNode);

        // Thực thi
        com.example.job_service.dto.offer.OfferDetailDTO result = offerService.getByIdDetail(1L, "token");

        // Kiểm tra: Các field của DetailDTO được map đúng giá trị từ nhiều nguồn
        assertThat(result.getRequesterName()).isEqualTo("Nguyen Van A");
        assertThat(result.getCandidateName()).isEqualTo("Candidate B");
        assertThat(result.getJobPositionTitle()).isEqualTo("Java Developer");
        assertThat(result.getDepartmentName()).isEqualTo("Phòng Nhân Sự");
        assertThat(result.getLevelName()).isEqualTo("Senior");
    }

    // Test Case ID: OFF-TC28
    // Mục tiêu: Kiểm tra fallback level name (sử dụng position.name nếu position.level không có)
    @Test
    @DisplayName("OFF-TC28: convertToDetailDTO - Fallback level name sang position.name")
    void convertToDetailDTO_LevelNameFallback_ShouldUsePositionName() throws Exception {
        // Chuẩn bị: Employee có position node nhưng không có level, chỉ có name
        draftOffer.setRequesterId(10L);
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode employeeNode = mapper.createObjectNode();
        com.fasterxml.jackson.databind.node.ObjectNode posNode = mapper.createObjectNode();
        posNode.put("name", "Team Lead"); // Fallback field
        employeeNode.set("position", posNode);

        when(offerRepository.findById(1L)).thenReturn(Optional.of(draftOffer));
        lenient().when(userService.getEmployeeById(10L, "token")).thenReturn(org.springframework.http.ResponseEntity.ok(employeeNode));

        // Thực thi
        com.example.job_service.dto.offer.OfferDetailDTO result = offerService.getByIdDetail(1L, "token");

        // Kiểm tra: Phải lấy từ position.name
        assertThat(result.getLevelName()).isEqualTo("Team Lead");
    }

    // Test Case ID: OFF-TC26
    // Mục tiêu: Lấy danh sách Offer có hỗ trợ filter phân trang và user name map.
    @Test
    @DisplayName("OFF-TC26: getAllWithFilters - Trả về phân trang và mapping đúng")
    void getAllWithFilters_ShouldReturnPaginationDTO() {
        // Chuẩn bị: Dữ liệu phân trang
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        org.springframework.data.domain.Page<Offer> page = new org.springframework.data.domain.PageImpl<>(java.util.List.of(draftOffer), pageable, 1);
        
        // Setup draftOffer loại bỏ các key để tránh gọi mock client không cần thiết
        draftOffer.setRequesterId(null);
        draftOffer.setCandidateId(null);
        draftOffer.setWorkflowId(null);

        when(offerRepository.findByFilters(OfferStatus.DRAFT, 10L, "keyword", pageable)).thenReturn(page);
        when(userService.getEmployeesByIds(any(), eq("token"))).thenReturn(java.util.Map.of());

        // Thực thi
        com.example.job_service.dto.PaginationDTO result = offerService.getAllWithFilters("DRAFT", 10L, "keyword", "token", pageable);

        // Kiểm tra: Phân trang tổng hợp đúng Metadata và số lượng List Result
        assertThat(result.getMeta().getTotal()).isEqualTo(1);
        assertThat(result.getResult()).isInstanceOf(java.util.List.class);
    }

    // Test Case ID: OFF-TC27
    // Mục tiêu: Hàm tìm kiếm dạng List không Support phân trang (dùng cho export, batch update)
    @Test
    @DisplayName("OFF-TC27: findAllWithFilters - Tìm kiếm danh sách List Offer")
    void findAllWithFilters_ShouldReturnList() {
        // Chuẩn bị
        when(offerRepository.findByFiltersList(OfferStatus.DRAFT, 1L, 2L, 3L, 1000L, 2000L, null, null, "key"))
            .thenReturn(java.util.List.of(draftOffer));
            
        // Thực thi
        java.util.List<Offer> results = offerService.findAllWithFilters("DRAFT", 1L, 2L, 3L, 1000L, 2000L, null, null, "key");
        
        // Kiểm tra: Trả về nguyên mẫu List Data
        assertThat(results).hasSize(1);
    }
}
