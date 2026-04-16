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
    // Mục tiêu: Offer ở DRAFT, cập nhật trường không null
    @Test
    @DisplayName("OFF-TC02: update - Offer DRAFT, phải cập nhật trường không null")
    void update_DraftOfferWithValidFields_ShouldUpdateNonNullFields() throws IdInvalidException {
        // Chuẩn bị
        UpdateOfferDTO dto = new UpdateOfferDTO();
        dto.setBasicSalary(25_000_000L);
        dto.setNotes("Updated notes");
        // candidateId = null -> không cập nhật

        when(offerRepository.findById(1L)).thenReturn(Optional.of(draftOffer));
        when(offerRepository.save(any(Offer.class))).thenAnswer(inv -> inv.getArgument(0));

        // Thực thi
        Offer result = offerService.update(1L, dto);

        // Kiểm tra: Salary và notes được cập nhật; candidateId giữ nguyên
        assertThat(result.getBasicSalary()).isEqualTo(25_000_000L);
        assertThat(result.getNotes()).isEqualTo("Updated notes");
        assertThat(result.getCandidateId()).isEqualTo(100L); // giữ nguyên

        // Minh chứng (CheckDB): save() được gọi
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
}
