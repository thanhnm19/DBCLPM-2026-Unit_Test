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

    @Test
    @Transactional
    @DisplayName("OFF-TC01: create - DTO hợp lệ, phải lưu với status DRAFT")
    void create_ValidDto_ShouldSaveOfferWithDraftStatus() {
        // [Chuẩn bị - Prepare]
        // Khởi tạo DTO chứa thông tin ứng viên, lương cơ bản và các thông số thử việc
        CreateOfferDTO dto = new CreateOfferDTO();
        dto.setCandidateId(100L);
        dto.setBasicSalary(20000000L);
        dto.setProbationSalaryRate(85);
        dto.setOnboardingDate(LocalDate.now().plusDays(15));
        dto.setProbationPeriod(2);
        dto.setWorkflowId(50L);

        // [Thực thi - Act]
        // Gọi hàm tạo Offer mới từ DTO đầu vào
        Offer saved = offerService.create(dto);

        // [Kiểm tra - Assert]
        // 1. Xác nhận bản ghi đã được lưu vào Database (có ID)
        assertThat(saved.getId()).isNotNull();
        // 2. Xác nhận trạng thái mặc định ban đầu phải là DRAFT và đang ở trạng thái hoạt động (Active)
        assertThat(saved.getStatus()).isEqualTo(OfferStatus.DRAFT);
        assertThat(saved.getIsActive()).isTrue();
        assertThat(offerRepository.findById(saved.getId())).isPresent();
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC02: update - Offer DRAFT, cập nhật đầy đủ các trường")
    void update_DraftOffer_ShouldUpdateFields() throws IdInvalidException {
        // [Chuẩn bị - Prepare]
        // Lưu một bản ghi Offer mẫu ở trạng thái DRAFT vào DB để chuẩn bị chỉnh sửa
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer = offerRepository.save(offer);

        // Khởi tạo DTO cập nhật với thông tin ứng viên mới và mức lương mới
        UpdateOfferDTO dto = new UpdateOfferDTO();
        dto.setCandidateId(101L);
        dto.setBasicSalary(25000000L);
        dto.setProbationSalaryRate(90);
        dto.setNotes("Updated notes");

        // [Thực thi - Act]
        // Thực hiện cập nhật thông tin Offer thông qua Service
        Offer updated = offerService.update(offer.getId(), dto);

        // [Kiểm tra - Assert]
        // Xác nhận các trường dữ liệu trong Database đã được thay đổi chính xác theo thông tin trong DTO
        assertThat(updated.getCandidateId()).isEqualTo(101L);
        assertThat(updated.getBasicSalary()).isEqualTo(25000000L);
        assertThat(updated.getNotes()).isEqualTo("Updated notes");
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC03: update - Offer PENDING, phải throw IllegalStateException")
    void update_PendingOffer_ShouldThrowException() {
        // [Chuẩn bị - Prepare]
        // Thiết lập một Offer đang trong quá trình chờ phê duyệt (PENDING) - không cho phép sửa
        Offer offer = createSampleOffer(100L, OfferStatus.PENDING);
        offer = offerRepository.save(offer);

        UpdateOfferDTO dto = new UpdateOfferDTO();
        dto.setBasicSalary(30000000L);

        Long id = offer.getId();

        // [Thực thi & Kiểm tra - Act & Assert]
        // Xác nhận hệ thống ném lỗi IllegalStateException khi cố tình sửa Offer không ở trạng thái DRAFT
        assertThatThrownBy(() -> offerService.update(id, dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC04: update - Offer APPROVED, phải throw IllegalStateException")
    void update_ApprovedOffer_ShouldThrowException() {
        // [Chuẩn bị - Prepare]
        // Thiết lập Offer đã được phê duyệt (APPROVED) để kiểm tra tính toàn vẹn dữ liệu
        Offer offer = createSampleOffer(100L, OfferStatus.APPROVED);
        offer = offerRepository.save(offer);

        Long id = offer.getId();

        // [Thực thi & Kiểm tra - Act & Assert]
        // Xác nhận không thể chỉnh sửa thông tin sau khi Offer đã hoàn tất quy trình phê duyệt
        assertThatThrownBy(() -> offerService.update(id, new UpdateOfferDTO()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC05: submit - DRAFT -> PENDING")
    void submit_DraftOffer_ShouldChangeToPending() throws IdInvalidException {
        // [Chuẩn bị - Prepare]
        // Thiết lập một Offer nháp kèm mã quy trình phê duyệt (WorkflowId)
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer.setWorkflowId(50L);
        offer = offerRepository.save(offer);

        // [Thực thi - Act]
        // Thực hiện lệnh gửi phê duyệt (Submit)
        Offer result = offerService.submit(offer.getId(), 1L, "token");

        // [Kiểm tra - Assert]
        // 1. Xác nhận trạng thái chuyển từ DRAFT sang PENDING và có ghi nhận thời điểm gửi
        assertThat(result.getStatus()).isEqualTo(OfferStatus.PENDING);
        assertThat(result.getSubmittedAt()).isNotNull();
        // 2. Xác nhận hệ thống đã gửi sự kiện thông báo tới Workflow Service
        verify(workflowProducer, times(1)).publishEvent(any());
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC06: submit - workflowId null")
    void submit_NullWorkflowId_ShouldThrowException() {
        // [Chuẩn bị - Prepare]
        // Lưu Offer nhưng cố ý bỏ trống WorkflowId để kiểm tra ràng buộc quy trình
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer.setWorkflowId(null);
        offer = offerRepository.save(offer);

        Long id = offer.getId();

        // [Thực thi & Kiểm tra - Act & Assert]
        // Xác nhận không cho phép gửi phê duyệt nếu chưa được gán mã quy trình (Workflow)
        assertThatThrownBy(() -> offerService.submit(id, 1L, "token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WorkflowId");
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC07: approveStep - PENDING")
    void approveStep_Pending_ShouldPublishEvent() throws IdInvalidException {
        // [Chuẩn bị - Prepare]
        // Lưu Offer đang ở trạng thái PENDING (đang chờ duyệt)
        Offer offer = createSampleOffer(100L, OfferStatus.PENDING);
        offer = offerRepository.save(offer);

        // [Thực thi - Act]
        // Thực hiện phê duyệt một bước (Approve Step)
        offerService.approveStep(offer.getId(), new ApproveOfferDTO(), 1L, "token");

        // [Kiểm tra - Assert]
        // Xác nhận sự kiện "REQUEST_APPROVED" đã được gửi đi để Workflow Service xử lý bước tiếp theo
        verify(workflowProducer).publishEvent(argThat(event -> event.getEventType().equals("REQUEST_APPROVED")));
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC08: rejectStep - PENDING -> REJECTED")
    void rejectStep_Pending_ShouldSetRejected() throws IdInvalidException {
        // [Chuẩn bị - Prepare]
        // Thiết lập Offer đang chờ duyệt để kiểm tra tính năng từ chối
        Offer offer = createSampleOffer(100L, OfferStatus.PENDING);
        offer = offerRepository.save(offer);

        // [Thực thi - Act]
        // Thực hiện hành động từ chối (Reject)
        Offer result = offerService.rejectStep(offer.getId(), new RejectOfferDTO(), 1L, "token");

        // [Kiểm tra - Assert]
        // 1. Xác nhận trạng thái Offer chuyển sang REJECTED
        assertThat(result.getStatus()).isEqualTo(OfferStatus.REJECTED);
        // 2. Xác nhận sự kiện "REQUEST_REJECTED" đã được thông báo cho các bên liên quan
        verify(workflowProducer).publishEvent(argThat(event -> event.getEventType().equals("REQUEST_REJECTED")));
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC09: cancel - Idempotent check")
    void cancel_AlreadyCancelled_ShouldReturnImmediately() throws IdInvalidException {
        // [Chuẩn bị - Prepare]
        // Lưu Offer đã ở trạng thái CANCELLED từ trước để kiểm chứng tính Idempotent (không thực hiện lại nếu đã xong)
        Offer offer = createSampleOffer(100L, OfferStatus.CANCELLED);
        offer = offerRepository.save(offer);

        // [Thực thi - Act]
        // Gọi lệnh hủy lần nữa trên bản ghi đã bị hủy
        Offer result = offerService.cancel(offer.getId(), new CancelOfferDTO(), 1L, "token");

        // [Kiểm tra - Assert]
        // Xác nhận trạng thái vẫn là CANCELLED và hệ thống không gửi thêm bất kỳ sự kiện Workflow nào
        assertThat(result.getStatus()).isEqualTo(OfferStatus.CANCELLED);
        verify(workflowProducer, times(0)).publishEvent(any());
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC10: withdraw - Owner can withdraw")
    void withdraw_ByOwner_ShouldSetWithdrawn() throws IdInvalidException {
        // [Chuẩn bị - Prepare]
        // Thiết lập Offer được tạo bởi người dùng có ID = 20
        Offer offer = createSampleOffer(100L, OfferStatus.PENDING);
        offer.setOwnerUserId(20L);
        offer = offerRepository.save(offer);

        // [Thực thi - Act]
        // Người sở hữu (ID 20) thực hiện rút lại (Withdraw) yêu cầu phê duyệt
        Offer result = offerService.withdraw(offer.getId(), new WithdrawOfferDTO(), 20L, "token");

        // [Kiểm tra - Assert]
        // Xác nhận trạng thái bản ghi đã được cập nhật thành WITHDRAWN
        assertThat(result.getStatus()).isEqualTo(OfferStatus.WITHDRAWN);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC11: withdraw - Unauthorized user")
    void withdraw_ByUnauthorized_ShouldThrowException() {
        // [Chuẩn bị - Prepare]
        // Lưu Offer thuộc quyền sở hữu của người dùng ID = 20
        Offer offer = createSampleOffer(100L, OfferStatus.PENDING);
        offer.setOwnerUserId(20L);
        offer.setRequesterId(20L);
        offer = offerRepository.save(offer);

        Long id = offer.getId();

        // [Thực thi & Kiểm tra - Act & Assert]
        // Xác nhận hệ thống ngăn chặn người dùng lạ (ID 99) thực hiện rút hồ sơ của người khác
        assertThatThrownBy(() -> offerService.withdraw(id, new WithdrawOfferDTO(), 99L, "token"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC12: findById - Existing vs Non-existing")
    void findById_ShouldWork() throws IdInvalidException {
        // [Chuẩn bị - Prepare]
        // Lưu một Offer thực tế vào DB để kiểm tra truy vấn
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer = offerRepository.save(offer);

        // [Thực thi - Act]
        // Tìm kiếm bản ghi bằng ID hợp lệ và ID không tồn tại (999)
        Offer found = offerService.findById(offer.getId());

        // [Kiểm tra - Assert]
        // 1. Xác nhận tìm thấy đúng bản ghi khi ID tồn tại
        assertThat(found).isNotNull();
        // 2. Xác nhận ném ngoại lệ IdInvalidException khi tìm ID rác
        assertThatThrownBy(() -> offerService.findById(999L)).isInstanceOf(IdInvalidException.class);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC13: getByIdWithUser - Aggregation test")
    void getByIdWithUser_ShouldAggregateData() throws Exception {
        // [Chuẩn bị - Prepare]
        // 1. Lưu Offer và liên kết với phòng ban, vị trí công việc mẫu
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer.setRequesterId(10L);
        offer = offerRepository.save(offer);

        RecruitmentRequest rr = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr.setDepartmentId(9L);
        rr = recruitmentRequestRepository.save(rr);

        JobPosition jp = new JobPosition();
        jp.setTitle("Java Dev");
        jp.setRecruitmentRequest(rr);

        // 2. Mock các phản hồi từ User Service, Candidate Service và Workflow Service
        ObjectNode emp = objectMapper.createObjectNode().put("name", "John Doe");
        ObjectNode pos = objectMapper.createObjectNode().put("level", "Senior");
        emp.set("position", pos);
        ObjectNode cand = objectMapper.createObjectNode().put("name", "Jane Smith").put("jobPositionId", 500L);
        ObjectNode dept = objectMapper.createObjectNode().put("name", "HR");
        ObjectNode workflow = objectMapper.createObjectNode().put("status", "ACTIVE");

        when(userService.getEmployeeById(10L, "token")).thenReturn(ResponseEntity.ok(emp));
        when(candidateClient.getCandidateById(100L, "token")).thenReturn(ResponseEntity.ok(cand));
        when(jobPositionService.findById(500L)).thenReturn(jp);
        when(userService.getDepartmentById(9L, "token")).thenReturn(ResponseEntity.ok(dept));
        when(workflowServiceClient.getWorkflowInfoByRequestId(any(), any(), any(), any())).thenReturn(workflow);

        // [Thực thi - Act]
        // Thực hiện lấy dữ liệu Offer đã được tổng hợp thông tin từ nhiều microservices
        OfferWithUserDTO result = offerService.getByIdWithUser(offer.getId(), "token");

        // [Kiểm tra - Assert]
        // Xác nhận các thông tin bổ trợ (Tiêu đề Job, Tên phòng ban, Cấp bậc) đã được ánh xạ chính xác vào DTO
        assertThat(result.getJobPositionTitle()).isEqualTo("Java Dev");
        assertThat(result.getDepartmentName()).isEqualTo("HR");
        assertThat(result.getLevelName()).isEqualTo("Senior");
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC14: getByIdDetail - Detail aggregation")
    void getByIdDetail_ShouldWork() throws Exception {
        // [Chuẩn bị - Prepare]
        // 1. Thiết lập Offer cho ứng viên ID 100
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer = offerRepository.save(offer);

        // 2. Mock thông tin chi tiết ứng viên và vị trí công việc từ các Service bên ngoài
        ObjectNode cand = objectMapper.createObjectNode().put("name", "Jane").put("email", "jane@test.com").put("jobPositionId", 500L);
        when(candidateClient.getCandidateById(100L, "token")).thenReturn(ResponseEntity.ok(cand));
        when(userService.getEmployeeById(anyLong(), anyString())).thenReturn(ResponseEntity.ok(objectMapper.createObjectNode()));
        when(workflowServiceClient.getWorkflowInfoByRequestId(any(), any(), any(), any())).thenReturn(objectMapper.createObjectNode());
        
        JobPosition jp = new JobPosition();
        jp.setTitle("Dev");
        when(jobPositionService.findById(500L)).thenReturn(jp);

        // [Thực thi - Act]
        // Thực hiện lấy thông tin chi tiết đầy đủ của Offer
        OfferDetailDTO result = offerService.getByIdDetail(offer.getId(), "token");

        // [Kiểm tra - Assert]
        // Xác nhận DTO kết quả chứa đúng tên ứng viên và tiêu đề công việc đã được tổng hợp
        assertThat(result.getCandidateName()).isEqualTo("Jane");
        assertThat(result.getJobPositionTitle()).isEqualTo("Dev");
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC15: getAllWithFilters - Paged results")
    void getAllWithFilters_ShouldReturnPaged() {
        // [Chuẩn bị - Prepare]
        // Lưu 1 Offer vào DB để kiểm tra tính năng phân trang và lọc
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offerRepository.save(offer);

        // Mock danh sách nhân viên để phục vụ việc map metadata
        when(userService.getEmployeesByIds(any(), anyString())).thenReturn(Map.of());

        // [Thực thi - Act]
        // Thực hiện truy vấn danh sách Offer có phân trang (Trang 0, tối đa 10 bản ghi) với lọc trạng thái DRAFT
        PaginationDTO result = offerService.getAllWithFilters("DRAFT", 1L, null, "token", PageRequest.of(0, 10));

        // [Kiểm tra - Assert]
        // Xác nhận tổng số bản ghi trong metadata phải là 1
        assertThat(result.getMeta().getTotal()).isEqualTo(1);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC16: findAllWithFilters - List results")
    void findAllWithFilters_ShouldReturnList() {
        // [Chuẩn bị - Prepare]
        // Thiết lập dữ liệu mẫu trong DB
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offerRepository.save(offer);

        // [Thực thi - Act]
        // Tìm kiếm danh sách Offer theo các bộ lọc chi tiết (Trạng thái, ứng viên, người gửi)
        List<Offer> results = offerService.findAllWithFilters("DRAFT", 100L, 100L, 1L, null, null, null, null, null);

        // [Kiểm tra - Assert]
        // Xác nhận danh sách kết quả không rỗng (khớp với dữ liệu đã lưu)
        assertThat(results).isNotEmpty();
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC17: submit - IDs already set")
    void submit_WithIdsSet_ShouldNotOverwrite() throws IdInvalidException {
        // [Chuẩn bị - Prepare]
        // Khởi tạo Offer đã được gán sẵn người yêu cầu (500) và người sở hữu (600)
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer.setRequesterId(500L);
        offer.setOwnerUserId(600L);
        offer = offerRepository.save(offer);

        // [Thực thi - Act]
        // Thực hiện gửi phê duyệt với tham số người dùng thực thi là ID = 1
        Offer result = offerService.submit(offer.getId(), 1L, "token");

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống tôn trọng dữ liệu cũ: ID người yêu cầu vẫn là 500 chứ không bị ghi đè thành 1
        assertThat(result.getRequesterId()).isEqualTo(500L);
        assertThat(result.getOwnerUserId()).isEqualTo(600L);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC18: getAllWithFilters - Invalid status")
    void getAllWithFilters_InvalidStatus_ShouldIgnore() {
        // [Chuẩn bị - Prepare]
        // Lưu Offer vào DB để thử lọc với tham số trạng thái không hợp lệ
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offerRepository.save(offer);

        // [Thực thi - Act]
        // Truy vấn với trạng thái INVALID_STATUS
        PaginationDTO result = offerService.getAllWithFilters("INVALID_STATUS", null, null, "token", PageRequest.of(0, 10));

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống bỏ qua lọc lỗi và vẫn trả về kết quả
        assertThat(result.getMeta().getTotal()).isGreaterThan(0);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC19: delete - Soft delete check")
    void delete_ShouldSetIsActiveFalse() throws IdInvalidException {
        // [Chuẩn bị - Prepare]
        // Tạo Offer mới đang hoạt động
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer = offerRepository.save(offer);

        // [Thực thi - Act]
        // Thực hiện xóa mềm Offer
        offerService.delete(offer.getId());

        // [Kiểm tra - Assert]
        // Xác nhận trường isActive đã chuyển sang false
        Offer deleted = offerRepository.findById(offer.getId()).get();
        assertThat(deleted.getIsActive()).isFalse();
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC20: getByIdWithUser - UserClient error")
    void getByIdWithUser_UserClientError_ShouldThrowException() {
        // [Chuẩn bị - Prepare]
        // Tạo Offer thuộc quyền sở hữu của người dùng 10
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer.setRequesterId(10L);
        offer = offerRepository.save(offer);

        // Mock User service trả về lỗi 500
        when(userService.getEmployeeById(10L, "token")).thenReturn(ResponseEntity.status(500).build());

        // [Thực thi & Kiểm tra - Act & Assert]
        // Xác nhận hệ thống ném ngoại lệ khi User Service gặp lỗi
        Long id = offer.getId();
        assertThatThrownBy(() -> offerService.getByIdWithUser(id, "token"))
                .isInstanceOf(com.example.job_service.exception.UserClientException.class);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC21: submit - requesterId null")
    void submit_DraftOfferWithNullRequesterId_ShouldSetRequesterIdFromActor() throws IdInvalidException {
        // [Chuẩn bị - Prepare]
        // Khởi tạo một Offer nháp nhưng chưa gán ID người yêu cầu (RequesterId)
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer.setRequesterId(null);
        offer.setWorkflowId(50L);
        offer = offerRepository.save(offer);

        // [Thực thi - Act]
        // Thực hiện gửi phê duyệt bởi người dùng có ID = 99
        offerService.submit(offer.getId(), 99L, "token");

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống đã tự động gán ID của người thực hiện (99) vào trường RequesterId của Offer
        Offer updated = offerRepository.findById(offer.getId()).get();
        assertThat(updated.getRequesterId()).isEqualTo(99L);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC22: submit - Offer PENDING")
    void submit_PendingOffer_ShouldThrowIllegalStateException() {
        // [Chuẩn bị - Prepare]
        // Thiết lập Offer đã ở trạng thái PENDING (đang chờ duyệt)
        Offer offer = createSampleOffer(100L, OfferStatus.PENDING);
        offer = offerRepository.save(offer);

        Long id = offer.getId();

        // [Thực thi & Kiểm tra - Act & Assert]
        // Xác nhận hệ thống ngăn chặn việc gửi phê duyệt lần thứ hai khi hồ sơ đang trong quy trình
        assertThatThrownBy(() -> offerService.submit(id, 20L, "token"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC23: approveStep - Offer DRAFT")
    void approveStep_DraftOffer_ShouldThrowIllegalStateException() {
        // [Chuẩn bị - Prepare]
        // Thiết lập Offer đang ở trạng thái DRAFT (chưa được submit vào quy trình)
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer = offerRepository.save(offer);

        Long id = offer.getId();

        // [Thực thi & Kiểm tra - Act & Assert]
        // Xác nhận không thể thực hiện lệnh phê duyệt nếu Offer chưa được chuyển sang trạng thái PENDING
        assertThatThrownBy(() -> offerService.approveStep(id, new ApproveOfferDTO(), 50L, "token"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC24: rejectStep - Offer DRAFT")
    void rejectStep_DraftOffer_ShouldThrowIllegalStateException() {
        // [Chuẩn bị - Prepare]
        // Thiết lập Offer ở trạng thái bản nháp (DRAFT)
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer = offerRepository.save(offer);

        Long id = offer.getId();

        // [Thực thi & Kiểm tra - Act & Assert]
        // Xác nhận hành động từ chối chỉ hợp lệ khi Offer đang trong trạng thái chờ phê duyệt (PENDING)
        assertThatThrownBy(() -> offerService.rejectStep(id, new RejectOfferDTO(), 50L, "token"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC25: returnOffer - Không hỗ trợ")
    void returnOffer_AnyOffer_ShouldAlwaysThrowIllegalStateException() {
        // [Chuẩn bị - Prepare]
        // Sử dụng một ID bất kỳ để kiểm tra tính năng chưa được triển khai
        Long id = 1L;

        // [Thực thi & Kiểm tra - Act & Assert]
        // Xác nhận hệ thống luôn ném ngoại lệ khi gọi tính năng Return (không được hỗ trợ trong phiên bản này)
        assertThatThrownBy(() -> offerService.returnOffer(id, new ReturnOfferDTO(), 10L, "token"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("return");
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC26: cancel - Offer PENDING")
    void cancel_PendingOffer_ShouldSetCancelledStatusAndPublishEvent() throws IdInvalidException {
        // [Chuẩn bị - Prepare]
        // Thiết lập Offer đang ở trạng thái chờ duyệt (PENDING)
        Offer offer = createSampleOffer(100L, OfferStatus.PENDING);
        offer = offerRepository.save(offer);

        // [Thực thi - Act]
        // Thực hiện lệnh hủy bỏ (Cancel) yêu cầu
        Offer result = offerService.cancel(offer.getId(), new CancelOfferDTO(), 20L, "token");

        // [Kiểm tra - Assert]
        // 1. Xác nhận trạng thái chuyển sang CANCELLED
        assertThat(result.getStatus()).isEqualTo(OfferStatus.CANCELLED);
        // 2. Xác nhận sự kiện "REQUEST_CANCELLED" đã được phát đi cho Workflow Service
        verify(workflowProducer).publishEvent(argThat(e -> e.getEventType().equals("REQUEST_CANCELLED")));
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC27: cancel - Offer APPROVED")
    void cancel_ApprovedOffer_ShouldThrowIllegalStateException() {
        // [Chuẩn bị - Prepare]
        // Thiết lập Offer đã được phê duyệt thành công (APPROVED)
        Offer offer = createSampleOffer(100L, OfferStatus.APPROVED);
        offer = offerRepository.save(offer);

        Long id = offer.getId();

        // [Thực thi & Kiểm tra - Act & Assert]
        // Xác nhận không cho phép hủy bỏ khi quy trình phê duyệt đã hoàn tất thành công
        assertThatThrownBy(() -> offerService.cancel(id, new CancelOfferDTO(), 10L, "token"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC28: withdraw - by Requester")
    void withdraw_PendingOfferByRequester_ShouldSetWithdrawnStatus() throws IdInvalidException {
        // [Chuẩn bị - Prepare]
        // Thiết lập Offer có người yêu cầu (Requester) là người dùng ID 30
        Offer offer = createSampleOffer(100L, OfferStatus.PENDING);
        offer.setOwnerUserId(20L);
        offer.setRequesterId(30L);
        offer = offerRepository.save(offer);

        // [Thực thi - Act]
        // Người yêu cầu (ID 30) thực hiện lệnh rút hồ sơ (Withdraw)
        Offer result = offerService.withdraw(offer.getId(), new WithdrawOfferDTO(), 30L, "token");

        // [Kiểm tra - Assert]
        // Xác nhận trạng thái hồ sơ được cập nhật sang WITHDRAWN thành công
        assertThat(result.getStatus()).isEqualTo(OfferStatus.WITHDRAWN);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC29: withdraw - Offer DRAFT")
    void withdraw_DraftOffer_ShouldThrowIllegalStateException() {
        // [Chuẩn bị - Prepare]
        // Lưu Offer đang ở trạng thái bản nháp (DRAFT)
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer = offerRepository.save(offer);

        Long id = offer.getId();

        // [Thực thi & Kiểm tra - Act & Assert]
        // Xác nhận không thể thực hiện lệnh rút hồ sơ nếu nó chưa từng được gửi đi phê duyệt
        assertThatThrownBy(() -> offerService.withdraw(id, new WithdrawOfferDTO(), 10L, "token"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC30: getByIdWithUserAndMetadata - Wrapper")
    void getByIdWithUserAndMetadata_ShouldWrapInSingleResponseDTO() throws Exception {
        // [Chuẩn bị - Prepare]
        // Thiết lập dữ liệu mẫu trong DB
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer = offerRepository.save(offer);

        // [Thực thi - Act]
        // Thực hiện lấy dữ liệu thông qua hàm Wrapper SingleResponseDTO
        SingleResponseDTO<OfferWithUserDTO> result = offerService.getByIdWithUserAndMetadata(offer.getId(), "token");

        // [Kiểm tra - Assert]
        // Xác nhận dữ liệu bao bọc bên trong (Data) không rỗng và khớp với ID yêu cầu
        assertThat(result.getData()).isNotNull();
        assertThat(result.getData().getId()).isEqualTo(offer.getId());
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC31: convertToDetailDTO - Level fallback")
    void convertToDetailDTO_LevelNameFallback_ShouldUsePositionName() throws Exception {
        // [Chuẩn bị - Prepare]
        // 1. Lưu Offer liên kết với người yêu cầu ID 10
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer.setRequesterId(10L);
        offer = offerRepository.save(offer);

        // 2. Mock dữ liệu nhân viên từ User Service: chỉ có tên vị trí (Position Name), không có tên cấp bậc (Level Name)
        ObjectNode emp = objectMapper.createObjectNode();
        ObjectNode pos = objectMapper.createObjectNode().put("name", "Team Lead"); 
        emp.set("position", pos);

        when(userService.getEmployeeById(10L, "token")).thenReturn(ResponseEntity.ok(emp));

        // [Thực thi - Act]
        // Thực hiện lấy thông tin chi tiết Offer để kiểm tra cơ chế Fallback của metadata
        OfferDetailDTO result = offerService.getByIdDetail(offer.getId(), "token");

        // [Kiểm tra - Assert]
        // Xác nhận trường levelName đã được kế thừa giá trị từ tên vị trí ("Team Lead") khi thiếu dữ liệu cấp bậc
        assertThat(result.getLevelName()).isEqualTo("Team Lead");
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC32 create - Onboarding quá khứ")
    void create_PastOnboardingDate_ShouldFail_ButAllows() {
        // [Chuẩn bị - Prepare]
        // Khởi tạo DTO với ngày nhận việc (Onboarding Date) đã trôi qua 10 ngày trước
        CreateOfferDTO dto = new CreateOfferDTO();
        dto.setCandidateId(100L);
        dto.setOnboardingDate(LocalDate.now().minusDays(10)); 

        // [Thực thi & Kiểm tra - Act & Assert]
        // Kiểm chứng tính đúng đắn của việc kiểm tra logic nghiệp vụ: Ngày nhận việc phải ở tương lai
        assertThatThrownBy(() -> offerService.create(dto))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Ngày nhận việc không được ở trong quá khứ");
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC33 create - Lương âm")
    void create_NegativeSalary_ShouldFail_ButAllows() {
        // [Chuẩn bị - Prepare]
        // Thiết lập DTO với giá trị lương cơ bản âm (phi logic)
        CreateOfferDTO dto = new CreateOfferDTO();
        dto.setCandidateId(100L);
        dto.setBasicSalary(-1000000L); 

        // [Thực thi & Kiểm tra - Act & Assert]
        // Kiểm chứng tính đúng đắn của việc kiểm tra ràng buộc dữ liệu mức lương
        assertThatThrownBy(() -> offerService.create(dto))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Lương cơ bản không được là số âm");
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC34: update - Partial update")
    void update_PartialUpdate_ShouldOnlyUpdateNonNullFields() throws IdInvalidException {
        // [Chuẩn bị - Prepare]
        // 1. Lưu Offer gốc với mức lương 20tr cho ứng viên 100
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer.setBasicSalary(20000000L);
        offer = offerRepository.save(offer);

        // 2. Khởi tạo DTO cập nhật chỉ chứa mức lương mới (22tr), để trống (null) mã ứng viên
        UpdateOfferDTO dto = new UpdateOfferDTO();
        dto.setBasicSalary(22000000L);
        dto.setCandidateId(null); 

        // [Thực thi - Act]
        // Gọi hàm cập nhật bán phần (Partial Update)
        Offer updated = offerService.update(offer.getId(), dto);
        
        // [Kiểm tra - Assert]
        // Xác nhận lương đã được đổi sang 22tr, trong khi mã ứng viên vẫn giữ nguyên giá trị cũ (100) thay vì bị ghi đè thành null
        assertThat(updated.getBasicSalary()).isEqualTo(22000000L);
        assertThat(updated.getCandidateId()).isEqualTo(100L);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC35: cancel - Offer REJECTED")
    void cancel_RejectedOffer_ShouldThrowIllegalStateException() {
        // [Chuẩn bị - Prepare]
        // Thiết lập Offer đã ở trạng thái bị từ chối (REJECTED)
        Offer offer = createSampleOffer(100L, OfferStatus.REJECTED);
        offer = offerRepository.save(offer);

        Long id = offer.getId();

        // [Thực thi & Kiểm tra - Act & Assert]
        // Xác nhận không thể thực hiện lệnh hủy nếu Offer đã kết thúc quy trình với kết quả bị từ chối
        assertThatThrownBy(() -> offerService.cancel(id, new CancelOfferDTO(), 1L, "token"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC36: getAllWithFilters - Empty status")
    void getAllWithFilters_EmptyStatus_ShouldIgnore() {
        // [Chuẩn bị - Prepare]
        // Lưu Offer vào DB để thử lọc với tham số trạng thái là khoảng trắng
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offerRepository.save(offer);

        // [Thực thi - Act]
        // Truy vấn danh sách Offer với tham số status rỗng ("   ")
        PaginationDTO result = offerService.getAllWithFilters("   ", null, null, "token", PageRequest.of(0, 10));

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống bỏ qua lọc theo trạng thái rỗng và vẫn trả về kết quả
        assertThat(result.getMeta().getTotal()).isGreaterThan(0);
    }
    @Test
    @Transactional
    @DisplayName("OFF-TC37: getByIdWithUser - Candidate body null")
    void getByIdWithUser_CandidateBodyNull_ShouldHandleGracefully() throws IdInvalidException {
        // [Chuẩn bị - Prepare]
        // 1. Lưu Offer vào DB
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        Offer saved = offerRepository.save(offer);

        // 2. Mock Candidate Service trả về body null (mặc dù status là 200 OK)
        when(candidateClient.getCandidateById(anyLong(), anyString())).thenReturn(ResponseEntity.ok(null));
        
        // [Thực thi - Act]
        // Thực hiện lấy thông tin Offer tổng hợp
        OfferWithUserDTO result = offerService.getByIdWithUser(saved.getId(), "token");

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống xử lý mượt mà, không bị crash và trường thông tin Candidate trong DTO trả về là null
        assertThat(result.getCandidate()).isNull();
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC41: getByIdWithUser - Candidate no jobPositionId")
    void getByIdWithUser_CandidateNoJobPositionId_ShouldHandleGracefully() throws IdInvalidException {
        // [Chuẩn bị - Prepare]
        // 1. Lưu Offer vào DB
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        Offer saved = offerRepository.save(offer);

        // 2. Mock Candidate Service trả về ứng viên hợp lệ nhưng không có thông tin vị trí tuyển dụng (jobPositionId)
        ObjectNode candNoJp = objectMapper.createObjectNode().put("name", "No JP");
        when(candidateClient.getCandidateById(anyLong(), anyString())).thenReturn(ResponseEntity.ok(candNoJp));

        // [Thực thi - Act]
        // Thực hiện truy vấn Offer
        OfferWithUserDTO result = offerService.getByIdWithUser(saved.getId(), "token");

        // [Kiểm tra - Assert]
        // Xác nhận trường tiêu đề công việc (jobPositionTitle) trả về null thay vì gây lỗi logic
        assertThat(result.getJobPositionTitle()).isNull();
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC42: getByIdWithUser - JobPositionService exception")
    void getByIdWithUser_JobPositionServiceException_ShouldHandleGracefully() throws IdInvalidException {
        // [Chuẩn bị - Prepare]
        // 1. Lưu Offer vào DB
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer = offerRepository.save(offer);

        // 2. Mock Candidate hợp lệ, nhưng khi gọi JobPosition Service lấy chi tiết thì gặp lỗi RuntimeException
        ObjectNode candWithJp = objectMapper.createObjectNode().put("jobPositionId", 501L);
        when(candidateClient.getCandidateById(anyLong(), anyString())).thenReturn(ResponseEntity.ok(candWithJp));
        when(jobPositionService.findById(501L)).thenThrow(new RuntimeException("Service Error"));

        // [Thực thi - Act]
        // Thực hiện truy vấn Offer kèm cơ chế xử lý lỗi dịch vụ ngoài
        OfferWithUserDTO result = offerService.getByIdWithUser(offer.getId(), "token");

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống vẫn trả về Offer DTO thành công, chỉ bỏ qua thông tin từ JobPosition (null)
        assertThat(result.getJobPositionTitle()).isNull();
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC43: getByIdWithUser - JobPosition no RecruitmentRequest")
    void getByIdWithUser_JobPositionNoRR_ShouldHandleGracefully() throws IdInvalidException {
        // [Chuẩn bị - Prepare]
        // 1. Lưu Offer vào DB
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer = offerRepository.save(offer);

        // 2. Mock JobPosition tồn tại nhưng không liên kết với yêu cầu tuyển dụng (RecruitmentRequest) nào
        ObjectNode candWithJp = objectMapper.createObjectNode().put("jobPositionId", 502L);
        when(candidateClient.getCandidateById(anyLong(), anyString())).thenReturn(ResponseEntity.ok(candWithJp));
        JobPosition jpNoRR = new JobPosition();
        when(jobPositionService.findById(502L)).thenReturn(jpNoRR);

        // [Thực thi - Act]
        // Thực hiện lấy dữ liệu Offer
        OfferWithUserDTO result = offerService.getByIdWithUser(offer.getId(), "token");

        // [Kiểm tra - Assert]
        // Xác nhận trường tên phòng ban (DepartmentName) là null do không có thông tin từ RR
        assertThat(result.getDepartmentName()).isNull();
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC44: getByIdWithUser - Dept response body null")
    void getByIdWithUser_DeptBodyNull_ShouldHandleGracefully() throws IdInvalidException {
        // [Chuẩn bị - Prepare]
        // 1. Lưu Offer và mock JobPosition liên kết với phòng ban số 9
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer = offerRepository.save(offer);

        ObjectNode candWithJp = objectMapper.createObjectNode().put("jobPositionId", 503L);
        when(candidateClient.getCandidateById(anyLong(), anyString())).thenReturn(ResponseEntity.ok(candWithJp));
        
        JobPosition jpWithRR = new JobPosition();
        RecruitmentRequest rr = new RecruitmentRequest();
        rr.setDepartmentId(9L);
        jpWithRR.setRecruitmentRequest(rr);
        when(jobPositionService.findById(503L)).thenReturn(jpWithRR);

        // 2. Mock User Service trả về body rỗng khi truy vấn phòng ban 9
        when(userService.getDepartmentById(9L, "token")).thenReturn(ResponseEntity.ok(null));

        // [Thực thi - Act]
        // Thực hiện lấy dữ liệu Offer
        OfferWithUserDTO result = offerService.getByIdWithUser(offer.getId(), "token");

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống vẫn hoạt động và để trống tên phòng ban
        assertThat(result.getDepartmentName()).isNull();
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC45: getByIdWithUser - Employee no position")
    void getByIdWithUser_EmployeeNoPosition_ShouldHandleGracefully() throws IdInvalidException {
        // [Chuẩn bị - Prepare]
        // 1. Lưu Offer của người yêu cầu ID 10
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer.setRequesterId(10L);
        offer = offerRepository.save(offer);

        // 2. Mock User Service trả về thông tin nhân viên nhưng không có dữ liệu vị trí (Position)
        ObjectNode empNoPos = objectMapper.createObjectNode().put("name", "No Pos");
        when(userService.getEmployeeById(anyLong(), anyString())).thenReturn(ResponseEntity.ok(empNoPos));

        // [Thực thi - Act]
        // Thực hiện truy vấn Offer
        OfferWithUserDTO result = offerService.getByIdWithUser(offer.getId(), "token");

        // [Kiểm tra - Assert]
        // Xác nhận trường levelName trả về null thay vì gây lỗi NullPointerException
        assertThat(result.getLevelName()).isNull();
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC46: getByIdWithUser - WorkflowInfo null")
    void getByIdWithUser_WorkflowInfoNull_ShouldHandleGracefully() throws IdInvalidException {
        // [Chuẩn bị - Prepare]
        // 1. Lưu Offer vào DB
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer = offerRepository.save(offer);

        // 2. Mock Workflow Client trả về null (trạng thái workflow không tồn tại)
        when(workflowServiceClient.getWorkflowInfoByRequestId(any(), any(), any(), any())).thenReturn(null);

        // [Thực thi - Act]
        // Thực hiện truy vấn Offer
        OfferWithUserDTO result = offerService.getByIdWithUser(offer.getId(), "token");

        // [Kiểm tra - Assert]
        // Xác nhận trường thông tin workflow (WorkflowInfo) trả về null
        assertThat(result.getWorkflowInfo()).isNull();
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC47: getByIdWithUser - Use OwnerUserId when Requester null")
    void getByIdWithUser_UseOwnerWhenRequesterNull_ShouldWork() throws IdInvalidException {
        // [Chuẩn bị - Prepare]
        // 1. Lưu Offer không có người yêu cầu (RequesterId = null) nhưng có người sở hữu (OwnerUserId = 20)
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer.setRequesterId(null);
        offer.setOwnerUserId(20L);
        offer = offerRepository.save(offer);

        // 2. Mock thông tin nhân viên cho ID 20 (Owner)
        ObjectNode emp = objectMapper.createObjectNode().put("name", "Owner Name");
        when(userService.getEmployeeById(20L, "token")).thenReturn(ResponseEntity.ok(emp));

        // [Thực thi - Act]
        // Thực hiện truy vấn Offer
        OfferWithUserDTO result = offerService.getByIdWithUser(offer.getId(), "token");

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống tự động sử dụng OwnerUserId để lấy thông tin nhân viên khi không tìm thấy RequesterId
        assertThat(result).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC38: getByIdWithUser - Candidate ID null")
    void getByIdWithUser_CandidateIdNull_ShouldHandleGracefully() throws IdInvalidException {
        // [Chuẩn bị - Prepare]
        // Lưu Offer vào DB sau đó cập nhật CandidateId thành null để kiểm tra case biên
        Offer offer = new Offer();
        offer.setCandidateId(100L);
        offer.setBasicSalary(1000L);
        Offer saved = offerRepository.save(offer);
        saved.setCandidateId(null); 

        // [Thực thi - Act]
        // Thực hiện truy vấn Offer có ID ứng viên là null
        OfferWithUserDTO result = offerService.getByIdWithUser(saved.getId(), "token");

        // [Kiểm tra - Assert]
        // Xác nhận trường thông tin Candidate trong kết quả trả về là null
        assertThat(result.getCandidate()).isNull();
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC52: getByIdWithUser - All User IDs null")
    void getByIdWithUser_AllUserIdsNull_ShouldHandleGracefully() throws IdInvalidException {
        // [Chuẩn bị - Prepare]
        // Lưu Offer hoàn toàn không có thông tin định danh người dùng (cả Requester và Owner đều null)
        Offer offer = new Offer();
        offer.setCandidateId(100L);
        offer.setRequesterId(null);
        offer.setOwnerUserId(null);
        Offer saved = offerRepository.save(offer);

        // [Thực thi - Act]
        // Thực hiện truy vấn Offer
        OfferWithUserDTO result = offerService.getByIdWithUser(saved.getId(), "token");

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống vẫn trả về DTO hợp lệ thay vì ném lỗi khi không có người dùng liên quan
        assertThat(result).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC39: getByIdWithUser - User Service 500 Error")
    void getByIdWithUser_UserService500_ShouldHandleGracefully() throws IdInvalidException {
        // [Chuẩn bị - Prepare]
        // 1. Lưu Offer của người dùng ID 20
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer.setOwnerUserId(20L);
        Offer saved = offerRepository.save(offer);
        
        // 2. Mock User Service phản hồi lỗi 500 Internal Server Error
        when(userService.getEmployeeById(20L, "token")).thenReturn(ResponseEntity.status(500).build());
        
        // [Thực thi & Kiểm tra - Act & Assert]
        // Xác nhận hệ thống ném ngoại lệ UserClientException để báo cáo lỗi từ dịch vụ ngoài đúng quy trình
        assertThatThrownBy(() -> offerService.getByIdWithUser(saved.getId(), "token"))
                .isInstanceOf(com.example.job_service.exception.UserClientException.class);
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC53: getByIdWithUser - User Service null response")
    void getByIdWithUser_UserServiceNullResponse_ShouldHandleGracefully() throws IdInvalidException {
        // [Chuẩn bị - Prepare]
        // 1. Lưu Offer cho người dùng 20
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer.setOwnerUserId(20L);
        Offer saved = offerRepository.save(offer);
        
        // 2. Mock User Service trả về phản hồi OK (200) nhưng body rỗng (null)
        when(userService.getEmployeeById(20L, "token")).thenReturn(ResponseEntity.ok(null));
        
        // [Thực thi - Act]
        // Thực hiện truy vấn Offer
        OfferWithUserDTO result = offerService.getByIdWithUser(saved.getId(), "token");

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống vẫn trả về kết quả DTO thành công
        assertThat(result).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC40: getByIdDetail - Requester ID null")
    void getByIdDetail_RequesterIdNull_ShouldHandleGracefully() throws IdInvalidException {
        // [Chuẩn bị - Prepare]
        // Lưu Offer không có mã người yêu cầu để kiểm tra hàm lấy chi tiết (Detail)
        Offer offer = new Offer();
        offer.setCandidateId(100L);
        offer.setRequesterId(null);
        Offer saved = offerRepository.save(offer);
        
        // [Thực thi - Act]
        // Thực hiện lấy thông tin chi tiết Offer
        OfferDetailDTO result = offerService.getByIdDetail(saved.getId(), "token");

        // [Kiểm tra - Assert]
        // Xác nhận trường tên người yêu cầu (RequesterName) trả về null một cách an toàn
        assertThat(result.getRequesterName()).isNull();
    }

    @Test
    @Transactional
    @DisplayName("OFF-TC54: getByIdDetail - User Service 404 Error")
    void getByIdDetail_UserService404_ShouldHandleGracefully() throws IdInvalidException {
        // [Chuẩn bị - Prepare]
        // 1. Lưu Offer của người yêu cầu ID 10
        Offer offer = createSampleOffer(100L, OfferStatus.DRAFT);
        offer.setRequesterId(10L);
        Offer saved = offerRepository.save(offer);
        
        // 2. Mock User Service phản hồi lỗi 404 (Không tìm thấy nhân viên)
        when(userService.getEmployeeById(10L, "token")).thenReturn(ResponseEntity.status(404).build());
        
        // [Thực thi - Act]
        // Thực hiện lấy thông tin chi tiết Offer
        OfferDetailDTO result = offerService.getByIdDetail(saved.getId(), "token");

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống không bị crash và trả về tên người yêu cầu là null (xử lý lỗi mềm)
        assertThat(result.getRequesterName()).isNull();
    }
}
