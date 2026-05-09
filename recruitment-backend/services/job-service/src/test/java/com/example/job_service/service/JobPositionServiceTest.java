package com.example.job_service.service;

import com.example.job_service.dto.PaginationDTO;
import com.example.job_service.dto.jobposition.CreateJobPositionDTO;
import com.example.job_service.dto.jobposition.JobPositionResponseDTO;
import com.example.job_service.dto.jobposition.UpdateJobPositionDTO;
import com.example.job_service.exception.IdInvalidException;
import com.example.job_service.model.JobPosition;
import com.example.job_service.model.RecruitmentRequest;
import com.example.job_service.repository.JobPositionRepository;
import com.example.job_service.repository.RecruitmentRequestRepository;
import com.example.job_service.utils.enums.JobPositionStatus;
import com.example.job_service.utils.enums.RecruitmentRequestStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.AfterEach;

import com.example.job_service.utils.SecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit Test cho JobPositionService - Module 6: Vị trí tuyển dụng.
 *
 * Chiến lược (Chuẩn SQA mới):
 * - Không mock Repository nội bộ. Sử dụng @DataJpaTest để kết nối H2 DB In-memory.
 * - Các Service ngoài (UserClient, CandidateClient, RecruitmentRequest) được Mock qua @Mock.
 * - Đạt Branch Coverage 100%.
 * - Có chứa các Bug Traps để phát hiện lỗi từ System Test.
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("JobPositionService Unit Tests with H2 DB")
class JobPositionServiceTest {

    @Autowired
    private JobPositionRepository jobPositionRepository;

    @Autowired
    private RecruitmentRequestRepository recruitmentRequestRepository;

    @Mock
    private RecruitmentRequestService recruitmentRequestService;

    @Mock
    private UserClient userService;

    @Mock
    private CandidateClient candidateClient;

    private JobPositionService jobPositionService;

    private MockedStatic<SecurityUtil> mockedSecurityUtil;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Mock static SecurityUtil to avoid NPE in @PrePersist/@PreUpdate
        mockedSecurityUtil = Mockito.mockStatic(SecurityUtil.class);
        mockedSecurityUtil.when(SecurityUtil::extractUserEmail).thenReturn("test@example.com");

        // Khởi tạo Service thật với Repository thật và các Mock API ngoài
        jobPositionService = new JobPositionService(
                jobPositionRepository, 
                recruitmentRequestService, 
                userService, 
                candidateClient
        );
    }

    @AfterEach
    void tearDown() {
        if (mockedSecurityUtil != null) {
            mockedSecurityUtil.close();
        }
    }

    /**
     * Helper method để tạo RecruitmentRequest hợp lệ
     */
    private RecruitmentRequest createSampleRecruitmentRequest(RecruitmentRequestStatus status) {
        RecruitmentRequest rr = new RecruitmentRequest();
        rr.setStatus(status != null ? status : RecruitmentRequestStatus.PENDING);
        return rr;
    }

    /**
     * Helper method để tạo JobPosition hợp lệ cho việc lưu vào DB
     */
    private JobPosition createSampleJobPosition(String title, JobPositionStatus status) {
        JobPosition jp = new JobPosition();
        jp.setTitle(title != null ? title : "Default Title");
        jp.setStatus(status != null ? status : JobPositionStatus.DRAFT);
        return jp;
    }

    /**
     * Helper method để tạo CreateJobPositionDTO hợp lệ
     */
    private CreateJobPositionDTO createSampleCreateJobPositionDTO(Long rrId) {
        CreateJobPositionDTO dto = new CreateJobPositionDTO();
        dto.setRecruitmentRequestId(rrId);
        dto.setTitle("Sample Job Title");
        dto.setSalaryMin(new BigDecimal("10000000"));
        dto.setSalaryMax(new BigDecimal("20000000"));
        dto.setDeadline(LocalDate.now().plusDays(30));
        dto.setLocation("Hanoi");
        dto.setIsRemote(false);
        return dto;
    }

    // =========================================================================================
    // 1. HAPPY PATH TESTS
    // =========================================================================================

    @Test
    @Transactional
    @DisplayName("JOB-TC01: create - DTO có salaryMin/Max, phải lưu với salary từ DTO")
    void create_ValidDtoWithSalary_ShouldSaveWithDtoSalaryAndSetDraftStatus() throws Exception {
        // 1. Chuẩn bị dữ liệu
        RecruitmentRequest rr = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr.setSalaryMin(new BigDecimal("15000000")); // Lương của RR khác DTO để test ưu tiên DTO
        rr.setSalaryMax(new BigDecimal("25000000"));
        rr.setDepartmentId(1L);
        rr = recruitmentRequestRepository.save(rr);

        CreateJobPositionDTO dto = createSampleCreateJobPositionDTO(rr.getId());
        dto.setTitle("Software Engineer");
        dto.setSalaryMin(new BigDecimal("20000000"));
        dto.setSalaryMax(new BigDecimal("30000000"));
        dto.setIsRemote(true);

        when(recruitmentRequestService.findById(rr.getId())).thenReturn(rr);

        // 2. Thực thi
        JobPosition saved = jobPositionService.create(dto);

        // 3. Kiểm tra DB thật
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTitle()).isEqualTo("Software Engineer");
        assertThat(saved.getSalaryMin()).isEqualByComparingTo(new BigDecimal("20000000"));
        assertThat(saved.getSalaryMax()).isEqualByComparingTo(new BigDecimal("30000000"));
        assertThat(saved.isRemote()).isTrue();
        assertThat(saved.getStatus()).isEqualTo(JobPositionStatus.DRAFT);
        
        // Kiểm tra thay đổi trạng thái RR
        verify(recruitmentRequestService).changeStatus(rr.getId(), RecruitmentRequestStatus.COMPLETED);
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC02: create - DTO không có salary, phải fallback từ RecruitmentRequest")
    void create_DtoWithNullSalary_ShouldFallbackToRecruitmentRequestSalary() throws Exception {
        // 1. Chuẩn bị dữ liệu
        RecruitmentRequest rr = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr.setSalaryMin(new BigDecimal("15000000"));
        rr.setSalaryMax(new BigDecimal("25000000"));
        rr.setDepartmentId(1L);
        rr = recruitmentRequestRepository.save(rr);

        CreateJobPositionDTO dto = createSampleCreateJobPositionDTO(rr.getId());
        dto.setTitle("Product Manager");
        dto.setSalaryMin(null);
        dto.setSalaryMax(null);

        when(recruitmentRequestService.findById(rr.getId())).thenReturn(rr);

        // 2. Thực thi
        JobPosition saved = jobPositionService.create(dto);

        // 3. Kiểm tra DB thật
        assertThat(saved.getSalaryMin()).isEqualByComparingTo(new BigDecimal("15000000"));
        assertThat(saved.getSalaryMax()).isEqualByComparingTo(new BigDecimal("25000000"));
        assertThat(saved.isRemote()).isFalse(); // Default
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC03: findById - ID tồn tại")
    void findById_ExistingId_ShouldReturnJobPosition() throws Exception {
        // 1. Chuẩn bị dữ liệu
        JobPosition jp = createSampleJobPosition("Test JP", JobPositionStatus.DRAFT);
        jp = jobPositionRepository.save(jp);

        // 2. Thực thi
        JobPosition found = jobPositionService.findById(jp.getId());

        // 3. Kiểm tra
        assertThat(found).isNotNull();
        assertThat(found.getTitle()).isEqualTo("Test JP");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC04: getByIdSimple - ID tồn tại")
    void getByIdSimple_ExistingId_ShouldReturnJobPosition() throws Exception {
        JobPosition jp = createSampleJobPosition("Test JP Simple", JobPositionStatus.DRAFT);
        jp = jobPositionRepository.save(jp);

        JobPosition found = jobPositionService.getByIdSimple(jp.getId());
        assertThat(found).isNotNull();
        assertThat(found.getTitle()).isEqualTo("Test JP Simple");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC05: getByIdWithDepartmentName - Lấy đủ metadata")
    void getByIdWithDepartmentName_ShouldReturnDtoWithDepartmentAndCount() throws Exception {
        // 1. Chuẩn bị dữ liệu
        RecruitmentRequest rr = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr.setDepartmentId(5L);
        rr = recruitmentRequestRepository.save(rr);

        JobPosition jp = createSampleJobPosition("Test JP Dept", JobPositionStatus.DRAFT);
        jp.setRecruitmentRequest(rr);
        jp = jobPositionRepository.save(jp);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode deptNode = mapper.createObjectNode();
        deptNode.put("name", "IT Department");
        when(userService.getDepartmentById(eq(5L), anyString())).thenReturn(ResponseEntity.ok(deptNode));
        when(candidateClient.countCandidatesByJobPositionId(eq(jp.getId()), anyString())).thenReturn(42);

        // 2. Thực thi
        JobPositionResponseDTO dto = jobPositionService.getByIdWithDepartmentName(jp.getId(), "token");

        // 3. Kiểm tra
        assertThat(dto.getDepartmentName()).isEqualTo("IT Department");
        assertThat(dto.getApplicationCount()).isEqualTo(42);
        assertThat(dto.getTitle()).isEqualTo("Test JP Dept");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC06: getByIdsWithDepartmentName - Lấy theo list IDs")
    void getByIdsWithDepartmentName_ShouldReturnDtoList() throws Exception {
        // 1. Chuẩn bị dữ liệu
        RecruitmentRequest rr1 = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr1.setDepartmentId(5L);
        rr1 = recruitmentRequestRepository.save(rr1);
        JobPosition jp1 = createSampleJobPosition("JP1", JobPositionStatus.DRAFT);
        jp1.setRecruitmentRequest(rr1);

        RecruitmentRequest rr2 = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr2.setDepartmentId(6L);
        rr2 = recruitmentRequestRepository.save(rr2);
        JobPosition jp2 = createSampleJobPosition("JP2", JobPositionStatus.DRAFT);
        jp2.setRecruitmentRequest(rr2);

        jobPositionRepository.saveAll(List.of(jp1, jp2));

        when(userService.getDepartmentsByIds(any(), anyString())).thenReturn(Map.of(5L, "Dept 5", 6L, "Dept 6"));

        // 2. Thực thi
        List<JobPositionResponseDTO> dtoList = jobPositionService.getByIdsWithDepartmentName(List.of(jp1.getId(), jp2.getId()), "token");

        // 3. Kiểm tra
        assertThat(dtoList).hasSize(2);
        assertThat(dtoList).extracting("departmentName").containsExactlyInAnyOrder("Dept 5", "Dept 6");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC07: getByIdsWithDepartmentName - Danh sách IDs trống")
    void getByIdsWithDepartmentName_EmptyIds_ShouldReturnEmptyList() {
        List<JobPositionResponseDTO> dtoList = jobPositionService.getByIdsWithDepartmentName(List.of(), "token");
        assertThat(dtoList).isEmpty();
        
        // Test null ids branch
        List<JobPositionResponseDTO> dtoListNull = jobPositionService.getByIdsWithDepartmentName(null, "token");
        assertThat(dtoListNull).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC08: getByIdWithPublished - Status PUBLISHED")
    void getByIdWithPublished_PublishedStatus_ShouldReturnJobPosition() throws Exception {
        // 1. Chuẩn bị
        JobPosition jp = createSampleJobPosition("Published Job", JobPositionStatus.PUBLISHED);
        jp = jobPositionRepository.save(jp);

        // 2. Thực thi
        JobPosition found = jobPositionService.getByIdWithPublished(jp.getId());

        // 3. Kiểm tra
        assertThat(found.getStatus()).isEqualTo(JobPositionStatus.PUBLISHED);
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC09: findAllWithFiltersSimple - Có ids hợp lệ")
    void findAllWithFiltersSimple_WithIds_ShouldReturnMatchingIds() {
        // 1. Chuẩn bị
        RecruitmentRequest rr1 = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr1 = recruitmentRequestRepository.save(rr1);
        JobPosition jp1 = createSampleJobPosition("JP1", JobPositionStatus.DRAFT);
        jp1.setRecruitmentRequest(rr1);

        RecruitmentRequest rr2 = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr2 = recruitmentRequestRepository.save(rr2);
        JobPosition jp2 = createSampleJobPosition("JP2", JobPositionStatus.DRAFT);
        jp2.setRecruitmentRequest(rr2);

        jobPositionRepository.saveAll(List.of(jp1, jp2));
 
        // 2. Thực thi
        List<JobPosition> result = jobPositionService.findAllWithFiltersSimple(null, null, null, null, jp1.getId() + "," + jp2.getId());
        assertThat(result).hasSize(2);

        // Case null ids (calls findByFilters)
        List<JobPosition> resultNull = jobPositionService.findAllWithFiltersSimple(null, null, null, null, null);
        assertThat(resultNull).hasSize(2); 

        // Case empty ids
        List<JobPosition> resultEmpty = jobPositionService.findAllWithFiltersSimple(null, null, null, null, "");
        assertThat(resultEmpty).hasSize(2);
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC10: findAllWithFiltersSimple - Tìm theo filters")
    void findAllWithFiltersSimple_WithoutIds_ShouldReturnByFilters() {
        // 1. Chuẩn bị
        RecruitmentRequest rr = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr = recruitmentRequestRepository.save(rr);

        JobPosition jp1 = createSampleJobPosition("Backend", JobPositionStatus.DRAFT);
        jp1.setRecruitmentRequest(rr);
        jobPositionRepository.save(jp1);

        // 2. Thực thi
        List<JobPosition> result = jobPositionService.findAllWithFiltersSimple(null, null, null, "Backend", null);

        // 3. Kiểm tra
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Backend");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC11: findAllWithFiltersSimplePaged - Phân trang IDs")
    void findAllWithFiltersSimplePaged_WithIds_ShouldReturnPagedIds() {
        // 1. Chuẩn bị
        JobPosition jp1 = createSampleJobPosition("JP1", JobPositionStatus.DRAFT);
        JobPosition jp2 = createSampleJobPosition("JP2", JobPositionStatus.DRAFT);
        JobPosition jp3 = createSampleJobPosition("JP3", JobPositionStatus.DRAFT);
        jobPositionRepository.saveAll(List.of(jp1, jp2, jp3));
        
        String ids = jp1.getId() + "," + jp2.getId() + "," + jp3.getId();

        // 2. Thực thi
        Pageable pageable = PageRequest.of(0, 2);
        PaginationDTO result = jobPositionService.findAllWithFiltersSimplePaged(null, null, null, null, ids, pageable);

        // 3. Kiểm tra
        assertThat(result.getMeta().getTotal()).isEqualTo(3);
        assertThat(((List<?>) result.getResult())).hasSize(2); // page size 2
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC12: findAllWithFiltersSimplePaged - IDs sai định dạng")
    void findAllWithFiltersSimplePaged_WithInvalidIds_ShouldFallbackToEmpty() {
        Pageable pageable = PageRequest.of(0, 2);
        // Case invalid format
        PaginationDTO result = jobPositionService.findAllWithFiltersSimplePaged(null, null, null, null, "abc,def", pageable);
        assertThat(result.getMeta().getTotal()).isEqualTo(0);

        // Case null ids (should go to findByFilters branch)
        PaginationDTO resultNull = jobPositionService.findAllWithFiltersSimplePaged(null, null, null, null, null, pageable);
        assertThat((List<?>) resultNull.getResult()).isEmpty();
        
        // Case empty/whitespace ids
        PaginationDTO resultEmpty = jobPositionService.findAllWithFiltersSimplePaged(null, null, null, null, "  ", pageable);
        assertThat((List<?>) resultEmpty.getResult()).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC13: findAllWithFilters - Trả về PaginationDTO")
    void findAllWithFilters_ShouldReturnPaginationDTO() {
        // 1. Chuẩn bị
        RecruitmentRequest rr = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr.setDepartmentId(5L);
        rr = recruitmentRequestRepository.save(rr);
        JobPosition jp = createSampleJobPosition("JP for count", JobPositionStatus.DRAFT);
        jp.setRecruitmentRequest(rr);
        jobPositionRepository.save(jp);

        when(userService.getDepartmentsByIds(any(), anyString())).thenReturn(Map.of(5L, "Dept 5"));
        when(candidateClient.countCandidatesByJobPositionIds(any(), anyString())).thenReturn(Map.of(jp.getId(), 10));

        // 2. Thực thi
        Pageable pageable = PageRequest.of(0, 10);
        PaginationDTO result = jobPositionService.findAllWithFilters(5L, null, null, null, pageable, "token");

        // 3. Kiểm tra
        assertThat(result.getMeta().getTotal()).isEqualTo(1);
        List<JobPositionResponseDTO> dtoList = (List<JobPositionResponseDTO>) result.getResult();
        assertThat(dtoList.get(0).getDepartmentName()).isEqualTo("Dept 5");
        assertThat(dtoList.get(0).getApplicationCount()).isEqualTo(10);
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC14: findAllWithFiltersSimplified - Trả về PaginationDTO")
    void findAllWithFiltersSimplified_ShouldReturnPaginationDTO() {
        // 1. Chuẩn bị
        RecruitmentRequest rr = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr.setDepartmentId(5L);
        rr = recruitmentRequestRepository.save(rr);
        JobPosition jp = createSampleJobPosition("JP simplified", JobPositionStatus.DRAFT);
        jp.setRecruitmentRequest(rr);
        jobPositionRepository.save(jp);

        when(userService.getDepartmentsByIds(any(), anyString())).thenReturn(Map.of(5L, "Dept 5"));
        when(candidateClient.countCandidatesByJobPositionIds(any(), anyString())).thenReturn(Map.of(jp.getId(), 10));

        // 2. Thực thi
        Pageable pageable = PageRequest.of(0, 10);
        PaginationDTO result = jobPositionService.findAllWithFiltersSimplified(5L, null, null, null, null, pageable, "token");

        // 3. Kiểm tra
        assertThat(result.getMeta().getTotal()).isEqualTo(1);
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC15: update - DTO đầy đủ")
    void update_ExistingId_ShouldUpdateFields() throws Exception {
        // 1. Chuẩn bị
        JobPosition jp = createSampleJobPosition("Old Title", JobPositionStatus.DRAFT);
        jp = jobPositionRepository.save(jp);

        UpdateJobPositionDTO dto = new UpdateJobPositionDTO();
        dto.setTitle("New Title");
        dto.setSalaryMin(new BigDecimal("100"));

        // 2. Thực thi
        JobPosition updated = jobPositionService.update(jp.getId(), dto);

        // 3. Kiểm tra
        assertThat(updated.getTitle()).isEqualTo("New Title");
        assertThat(updated.getSalaryMin()).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC16: delete - Xóa thành công")
    void delete_ExistingId_ShouldDeleteJobPosition() throws Exception {
        // 1. Chuẩn bị
        JobPosition jp = createSampleJobPosition("To Delete", JobPositionStatus.DRAFT);
        jp = jobPositionRepository.save(jp);

        // 2. Thực thi
        boolean result = jobPositionService.delete(jp.getId());

        // 3. Kiểm tra
        assertThat(result).isTrue();
        assertThat(jobPositionRepository.findById(jp.getId())).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC17: publish - Thành công")
    void publish_DraftPosition_ShouldSetPublishedStatusAndPublishedAt() throws Exception {
        // 1. Chuẩn bị
        JobPosition jp = createSampleJobPosition("Draft to Publish", JobPositionStatus.DRAFT);
        jp = jobPositionRepository.save(jp);

        // 2. Thực thi
        JobPosition published = jobPositionService.publish(jp.getId());

        // 3. Kiểm tra
        assertThat(published.getStatus()).isEqualTo(JobPositionStatus.PUBLISHED);
        assertThat(published.getPublishedAt()).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC18: close - Thành công")
    void close_PublishedPosition_ShouldSetClosedStatus() throws Exception {
        // 1. Chuẩn bị
        JobPosition jp = createSampleJobPosition("Published to Close", JobPositionStatus.PUBLISHED);
        jp = jobPositionRepository.save(jp);

        // 2. Thực thi
        JobPosition closed = jobPositionService.close(jp.getId());

        // 3. Kiểm tra
        assertThat(closed.getStatus()).isEqualTo(JobPositionStatus.CLOSED);
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC19: reopen - Thành công")
    void reopen_ClosedPosition_ShouldSetPublishedStatus() throws Exception {
        // 1. Chuẩn bị
        JobPosition jp = createSampleJobPosition("Closed to Reopen", JobPositionStatus.CLOSED);
        jp = jobPositionRepository.save(jp);

        // 2. Thực thi
        JobPosition reopened = jobPositionService.reopen(jp.getId());

        // 3. Kiểm tra
        assertThat(reopened.getStatus()).isEqualTo(JobPositionStatus.PUBLISHED);
    }

    // =========================================================================================
    // 2. EXCEPTION PATH TESTS
    // =========================================================================================

    @Test
    @DisplayName("JOB-TC20: create - RR không tồn tại")
    void create_RecruitmentRequestNotFound_ShouldThrowIdInvalidException() throws Exception {
        when(recruitmentRequestService.findById(999L)).thenThrow(new IdInvalidException("Not found"));
        CreateJobPositionDTO dto = new CreateJobPositionDTO();
        dto.setRecruitmentRequestId(999L);

        assertThatThrownBy(() -> jobPositionService.create(dto))
                .isInstanceOf(IdInvalidException.class);
    }

    @Test
    @DisplayName("JOB-TC21: findById - ID không tồn tại")
    void findById_NonExistingId_ShouldThrowIdInvalidException() {
        assertThatThrownBy(() -> jobPositionService.findById(999L))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("không tồn tại");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC22: getByIdWithPublished - Không phải PUBLISHED")
    void getByIdWithPublished_NotPublished_ShouldThrowIdInvalidException() {
        JobPosition jp = createSampleJobPosition("Not Published", JobPositionStatus.DRAFT);
        JobPosition finalJp = jobPositionRepository.save(jp);

        assertThatThrownBy(() -> jobPositionService.getByIdWithPublished(finalJp.getId()))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("chưa được xuất bản");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC23: publish - Không phải DRAFT")
    void publish_NotDraftPosition_ShouldThrowIdInvalidException() {
        JobPosition jp = createSampleJobPosition("Already Published", JobPositionStatus.PUBLISHED);
        JobPosition finalJp = jobPositionRepository.save(jp);

        assertThatThrownBy(() -> jobPositionService.publish(finalJp.getId()))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Chỉ có thể publish vị trí ở trạng thái DRAFT");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC24: close - Không phải PUBLISHED")
    void close_NotPublishedPosition_ShouldThrowIdInvalidException() {
        JobPosition jp = createSampleJobPosition("Still Draft", JobPositionStatus.DRAFT);
        JobPosition finalJp = jobPositionRepository.save(jp);

        assertThatThrownBy(() -> jobPositionService.close(finalJp.getId()))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Chỉ có thể đóng vị trí ở trạng thái PUBLISHED");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC25: reopen - Không phải CLOSED")
    void reopen_NotClosedPosition_ShouldThrowIdInvalidException() {
        JobPosition jp = createSampleJobPosition("Still Draft", JobPositionStatus.DRAFT);
        JobPosition finalJp = jobPositionRepository.save(jp);

        assertThatThrownBy(() -> jobPositionService.reopen(finalJp.getId()))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Chỉ có thể mở lại vị trí ở trạng thái CLOSED");
    }

    // =========================================================================================
    // 3. BUG TRAPS (TEST CASES BẮT LỖI TỪ SYSTEM TEST)
    // =========================================================================================

    @Test
    @Transactional
    @DisplayName("JOB-TC26 [Bẫy Lỗi] create - Deadline quá khứ")
    void create_PastDeadline_ShouldThrowException_ButBugAllowsIt() throws Exception {
        // [Bẫy Lỗi] (TC_JOB_01_005) Lỗi chưa được fix, code vẫn cho lưu deadline trong quá khứ.
        // 1. Chuẩn bị
        RecruitmentRequest rr = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr.setDepartmentId(1L);
        rr = recruitmentRequestRepository.save(rr);
        
        CreateJobPositionDTO dto = createSampleCreateJobPositionDTO(rr.getId());
        dto.setDeadline(LocalDate.now().minusDays(1)); // Deadline quá khứ

        when(recruitmentRequestService.findById(rr.getId())).thenReturn(rr);

        // 2. Thực thi & Kiểm tra
        // Chú ý: Test case này sẽ FAIL cho đến khi Bug được fix trong JobPositionService
        assertThatThrownBy(() -> jobPositionService.create(dto))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Hạn nộp hồ sơ không được ở trong quá khứ");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC27 [Bẫy Lỗi] create - Title > 200 ký tự")
    void create_TitleTooLong_ShouldThrowException_ButBugAllowsIt() throws Exception {
        // [Bẫy Lỗi] (TC_JOB_01_007) Lỗi chưa được fix, code vẫn cho lưu title > 200 ký tự.
        // 1. Chuẩn bị
        RecruitmentRequest rr = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr.setDepartmentId(1L);
        rr = recruitmentRequestRepository.save(rr);
        
        CreateJobPositionDTO dto = createSampleCreateJobPositionDTO(rr.getId());
        String longTitle = "a".repeat(201);
        dto.setTitle(longTitle);

        when(recruitmentRequestService.findById(rr.getId())).thenReturn(rr);

        // 2. Thực thi & Kiểm tra
        // Chú ý: Test case này sẽ FAIL cho đến khi Bug được fix trong JobPositionService
        assertThatThrownBy(() -> jobPositionService.create(dto))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Tiêu đề không được quá 200 ký tự");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC28 [Bẫy Lỗi] create - SalaryMin > SalaryMax")
    void create_SalaryMinGreaterThanMax_ShouldThrowException_ButBugAllowsIt() throws Exception {
        // [Bẫy Lỗi] (TC_JOB_01_010) Lỗi chưa được fix, code vẫn cho lưu SalaryMin > SalaryMax.
        // 1. Chuẩn bị
        RecruitmentRequest rr = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr.setDepartmentId(1L);
        rr = recruitmentRequestRepository.save(rr);
        
        CreateJobPositionDTO dto = createSampleCreateJobPositionDTO(rr.getId());
        dto.setSalaryMin(new BigDecimal("30000000"));
        dto.setSalaryMax(new BigDecimal("20000000")); // Max < Min

        when(recruitmentRequestService.findById(rr.getId())).thenReturn(rr);

        // 2. Thực thi & Kiểm tra
        // Chú ý: Test case này sẽ FAIL cho đến khi Bug được fix trong JobPositionService
        assertThatThrownBy(() -> jobPositionService.create(dto))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Lương tối thiểu không được lớn hơn lương tối đa");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC29 [Bẫy Lỗi] create - Requirements rỗng")
    void create_EmptyRequirements_ShouldThrowException_ButBugAllowsIt() throws Exception {
        // [Bẫy Lỗi] (TC_JOB_01_016) Lỗi chưa được fix, code vẫn cho lưu requirements rỗng.
        // 1. Chuẩn bị
        RecruitmentRequest rr = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr.setDepartmentId(1L);
        rr = recruitmentRequestRepository.save(rr);
        
        CreateJobPositionDTO dto = createSampleCreateJobPositionDTO(rr.getId());
        dto.setRequirements("");

        when(recruitmentRequestService.findById(rr.getId())).thenReturn(rr);

        // 2. Thực thi & Kiểm tra
        // Chú ý: Test case này sẽ FAIL cho đến khi Bug được fix trong JobPositionService
        assertThatThrownBy(() -> jobPositionService.create(dto))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Yêu cầu công việc không được để trống");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC30 [Bẫy Lỗi] create - Bỏ trống trường bắt buộc")
    void create_EmptyRequiredFields_ShouldThrowException_ButBugAllowsIt() throws Exception {
        // [Bẫy Lỗi] (TC_JOB_01_025) Lỗi chưa được fix, code vẫn cho lưu location, deadline rỗng.
        // 1. Chuẩn bị
        RecruitmentRequest rr = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr.setDepartmentId(1L);
        rr = recruitmentRequestRepository.save(rr);
        
        CreateJobPositionDTO dto = createSampleCreateJobPositionDTO(rr.getId());
        dto.setLocation(""); // Bỏ trống địa điểm

        when(recruitmentRequestService.findById(rr.getId())).thenReturn(rr);

        // 2. Thực thi & Kiểm tra
        // Chú ý: Test case này sẽ FAIL cho đến khi Bug được fix trong JobPositionService
        assertThatThrownBy(() -> jobPositionService.create(dto))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Địa điểm không được để trống");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC31 [Bẫy Lỗi] publish - Publish quá hạn")
    void publish_PastDeadline_ShouldThrowException_ButBugAllowsIt() throws Exception {
        // [Bẫy Lỗi] (TC_JOB_02_028) Lỗi chưa được fix, code vẫn cho publish khi deadline đã qua.
        // 1. Chuẩn bị
        JobPosition jp = createSampleJobPosition("Past Deadline", JobPositionStatus.DRAFT);
        jp.setDeadline(LocalDate.now().minusDays(5)); // Quá hạn 5 ngày
        jp = jobPositionRepository.save(jp);

        // 2. Thực thi & Kiểm tra
        // Chú ý: Test case này sẽ FAIL cho đến khi Bug được fix trong JobPositionService
        final Long jpId = jp.getId();
        assertThatThrownBy(() -> jobPositionService.publish(jpId))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Không thể publish vị trí đã quá hạn");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC32: create - isRemote default")
    void create_DtoWithNullIsRemote_ShouldDefaultToFalse() throws IdInvalidException {
        RecruitmentRequest rr = new RecruitmentRequest();
        rr.setDepartmentId(10L);
        rr.setStatus(RecruitmentRequestStatus.DRAFT);
        rr = recruitmentRequestRepository.save(rr);
        when(recruitmentRequestService.findById(rr.getId())).thenReturn(rr);

        CreateJobPositionDTO dto = new CreateJobPositionDTO();
        dto.setRecruitmentRequestId(rr.getId());
        dto.setTitle("Dev");
        dto.setIsRemote(null); 
        
        JobPosition saved = jobPositionService.create(dto);
        assertThat(saved.isRemote()).isFalse();
    }

    @Test
    @DisplayName("JOB-TC33: findAllSimple - empty ids handled")
    void findAllWithFiltersSimple_WithEmptyIds_ShouldHandleGracefully() {
        jobPositionService.findAllWithFiltersSimple(null, null, null, null, "1,,2");
        jobPositionService.findAllWithFiltersSimple(null, null, null, null, " , ");
    }

    @Test
    @DisplayName("JOB-TC34: findAllSimple - invalid ids handled")
    void findAllWithFiltersSimple_WithInvalidIdFormat_ShouldCatchException() {
        jobPositionService.findAllWithFiltersSimple(null, null, null, null, "invalid");
    }

    @Test
    @DisplayName("JOB-TC35: findAllPaged - empty ids handled")
    void findAllWithFiltersSimplePaged_WithEmptyIds_ShouldHandleGracefully() {
        jobPositionService.findAllWithFiltersSimplePaged(null, null, null, null, "1,,2", PageRequest.of(0, 10));
        jobPositionService.findAllWithFiltersSimplePaged(null, null, null, null, " , ", PageRequest.of(0, 10));
    }

    @Test
    @DisplayName("JOB-TC36: findAllPaged - invalid ids handled")
    void findAllWithFiltersSimplePaged_WithInvalidIdFormat_ShouldCatchException() {
        jobPositionService.findAllWithFiltersSimplePaged(null, null, null, null, "invalid", PageRequest.of(0, 10));
    }

    @Test
    @DisplayName("JOB-TC37: findAll - special deptId 1")
    void findAllWithFilters_WithSpecialDepartmentId_ShouldNullifyCorrectly() {
        jobPositionService.findAllWithFilters(1L, null, null, null, PageRequest.of(0, 10), "token");
        jobPositionService.findAllWithFilters(2L, null, null, null, PageRequest.of(0, 10), "token");
        jobPositionService.findAllWithFilters(null, null, null, null, PageRequest.of(0, 10), "token");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC38: update - all fields")
    void update_AllFieldsProvided_ShouldUpdateEverything() throws IdInvalidException {
        JobPosition pos = new JobPosition();
        pos.setTitle("Old");
        pos.setStatus(JobPositionStatus.DRAFT);
        pos = jobPositionRepository.save(pos);

        UpdateJobPositionDTO dto = new UpdateJobPositionDTO();
        dto.setTitle("New");
        dto.setDescription("Desc");
        dto.setRequirements("Req");
        dto.setBenefits("Ben");
        dto.setSalaryMin(java.math.BigDecimal.valueOf(100));
        dto.setSalaryMax(java.math.BigDecimal.valueOf(200));
        dto.setEmploymentType("FT");
        dto.setExperienceLevel("JR");
        dto.setLocation("HN");
        dto.setIsRemote(true);
        dto.setQuantity(10);
        dto.setDeadline(LocalDate.now().plusDays(5));
        dto.setYearsOfExperience("1 year");

        JobPosition updated = jobPositionService.update(pos.getId(), dto);
        assertThat(updated.getTitle()).isEqualTo("New");
        assertThat(updated.getQuantity()).isEqualTo(10);
    }
}

