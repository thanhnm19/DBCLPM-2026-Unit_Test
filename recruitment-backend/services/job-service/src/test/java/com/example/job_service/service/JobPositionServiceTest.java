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
import org.springframework.data.domain.Page;
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
 * Chiến lược:
 * - Không mock Repository nội bộ. Sử dụng @DataJpaTest để kết nối H2 DB In-memory.
 * - Các Service ngoài (UserClient, CandidateClient, RecruitmentRequest) được Mock qua @Mock.
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

    // TESTS

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
        // 1. Chuẩn bị dữ liệu
        JobPosition jp = createSampleJobPosition("Test JP Simple", JobPositionStatus.DRAFT);
        jp = jobPositionRepository.save(jp);

        // 2. Thực thi
        JobPosition found = jobPositionService.getByIdSimple(jp.getId());

        // 3. Kiểm tra
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
        
        // 3. Kiểm tra
        assertThat(result).hasSize(2);
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
        Pageable pageable = PageRequest.of(0, 2); //Trang đầu tiên và mỗi trang tối đa 2 jobposition
        PaginationDTO result = jobPositionService.findAllWithFiltersSimplePaged(null, null, null, null, ids, pageable);

        // 3. Kiểm tra
        assertThat(result.getMeta().getTotal()).isEqualTo(3); // tổng jobposition là 3
        assertThat(((List<?>) result.getResult())).hasSize(2); // page size 2: page1 có jp1, jp2; page2 có jp3
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC12: findAllWithFiltersSimplePaged - IDs sai định dạng")
    void findAllWithFiltersSimplePaged_WithInvalidIds_ShouldFallbackToEmpty() {
        // 1. Chuẩn bị
        Pageable pageable = PageRequest.of(0, 2);

        // 2. Thực thi
        // Case null ids (should go to findByFilters branch)
        PaginationDTO resultNull = jobPositionService.findAllWithFiltersSimplePaged(null, null, null, null, null, pageable);
        
        // 3. Kiểm tra
        assertThat((List<?>) resultNull.getResult()).isEmpty();
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
    @DisplayName("JOB-TC15: update - DTO")
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
        jobPositionService.publish(jp.getId());

        // 3. Kiểm tra
        // Lấy lại dữ liệu từ Database để đảm bảo đã được persist thành công
        JobPosition publishedInDb = jobPositionRepository.findById(jp.getId()).orElse(null);
        
        assertThat(publishedInDb).isNotNull();
        assertThat(publishedInDb.getStatus()).isEqualTo(JobPositionStatus.PUBLISHED);
        assertThat(publishedInDb.getPublishedAt()).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC18: close - Thành công")
    void close_PublishedPosition_ShouldSetClosedStatus() throws Exception {
        // 1. Chuẩn bị
        JobPosition jp = createSampleJobPosition("Published to Close", JobPositionStatus.PUBLISHED);
        jp = jobPositionRepository.save(jp);

        // 2. Thực thi
        jobPositionService.close(jp.getId());

        // 3. Kiểm tra
        // Lấy từ DB lên check
        JobPosition closedInDb = jobPositionRepository.findById(jp.getId()).orElse(null);
        assertThat(closedInDb).isNotNull();
        assertThat(closedInDb.getStatus()).isEqualTo(JobPositionStatus.CLOSED);
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC19: reopen - Thành công")
    void reopen_ClosedPosition_ShouldSetPublishedStatus() throws Exception {
        // 1. Chuẩn bị
        JobPosition jp = createSampleJobPosition("Closed to Reopen", JobPositionStatus.CLOSED);
        jp = jobPositionRepository.save(jp);

        // 2. Thực thi
        jobPositionService.reopen(jp.getId());

        // 3. Kiểm tra
        // Lấy từ DB lên check
        JobPosition reopenedInDb = jobPositionRepository.findById(jp.getId()).orElse(null);
        assertThat(reopenedInDb).isNotNull();
        assertThat(reopenedInDb.getStatus()).isEqualTo(JobPositionStatus.PUBLISHED);
    }

    @Test
    @DisplayName("JOB-TC20: create - RR không tồn tại")
    void create_RecruitmentRequestNotFound_ShouldThrowIdInvalidException() throws Exception {
        // 1. Chuẩn bị
        when(recruitmentRequestService.findById(999L)).thenThrow(new IdInvalidException("Not found"));
        CreateJobPositionDTO dto = new CreateJobPositionDTO();
        dto.setRecruitmentRequestId(999L);

        // 2. Thực thi & 3. Kiểm tra
        assertThatThrownBy(() -> jobPositionService.create(dto))
                .isInstanceOf(IdInvalidException.class);
    }

    @Test
    @DisplayName("JOB-TC21: findById - ID không tồn tại")
    void findById_NonExistingId_ShouldThrowIdInvalidException() {
        // 1. Chuẩn bị (Không cần thiết lập)
        
        // 2. Thực thi & 3. Kiểm tra
        assertThatThrownBy(() -> jobPositionService.findById(999L))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("không tồn tại");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC22: getByIdWithPublished - Không phải PUBLISHED")
    void getByIdWithPublished_NotPublished_ShouldThrowIdInvalidException() {
        // 1. Chuẩn bị
        JobPosition jp = createSampleJobPosition("Not Published", JobPositionStatus.DRAFT);
        JobPosition finalJp = jobPositionRepository.save(jp);

        // 2. Thực thi & 3. Kiểm tra
        assertThatThrownBy(() -> jobPositionService.getByIdWithPublished(finalJp.getId()))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("chưa được xuất bản");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC23: publish - Không phải DRAFT")
    void publish_NotDraftPosition_ShouldThrowIdInvalidException() {
        // 1. Chuẩn bị
        JobPosition jp = createSampleJobPosition("Already Published", JobPositionStatus.PUBLISHED);
        JobPosition finalJp = jobPositionRepository.save(jp);

        // 2. Thực thi & 3. Kiểm tra
        assertThatThrownBy(() -> jobPositionService.publish(finalJp.getId()))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Chỉ có thể publish vị trí ở trạng thái DRAFT");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC24: close - Không phải PUBLISHED")
    void close_NotPublishedPosition_ShouldThrowIdInvalidException() {
        // 1. Chuẩn bị
        JobPosition jp = createSampleJobPosition("Still Draft", JobPositionStatus.DRAFT);
        JobPosition finalJp = jobPositionRepository.save(jp);

        // 2. Thực thi & 3. Kiểm tra
        assertThatThrownBy(() -> jobPositionService.close(finalJp.getId()))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Chỉ có thể đóng vị trí ở trạng thái PUBLISHED");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC25: reopen - Không phải CLOSED")
    void reopen_NotClosedPosition_ShouldThrowIdInvalidException() {
        // 1. Chuẩn bị
        JobPosition jp = createSampleJobPosition("Still Draft", JobPositionStatus.DRAFT);
        JobPosition finalJp = jobPositionRepository.save(jp);

        // 2. Thực thi & 3. Kiểm tra
        assertThatThrownBy(() -> jobPositionService.reopen(finalJp.getId()))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Chỉ có thể mở lại vị trí ở trạng thái CLOSED");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC26 create - Deadline quá khứ")
    void create_PastDeadline_ShouldThrowException_ButBugAllowsIt() throws Exception {
        // Lưu deadline trong quá khứ.
        // 1. Chuẩn bị
        RecruitmentRequest rr = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr.setDepartmentId(1L);
        rr = recruitmentRequestRepository.save(rr);
        
        CreateJobPositionDTO dto = createSampleCreateJobPositionDTO(rr.getId());
        dto.setDeadline(LocalDate.now().minusDays(1)); // Deadline quá khứ

        when(recruitmentRequestService.findById(rr.getId())).thenReturn(rr);

        // 2. Thực thi & Kiểm tra
        assertThatThrownBy(() -> jobPositionService.create(dto))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Hạn nộp hồ sơ không được ở trong quá khứ");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC27 create - Title > 200 ký tự")
    void create_TitleTooLong_ShouldThrowException_ButBugAllowsIt() throws Exception {
        // 1. Chuẩn bị
        RecruitmentRequest rr = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr.setDepartmentId(1L);
        rr = recruitmentRequestRepository.save(rr);
        
        CreateJobPositionDTO dto = createSampleCreateJobPositionDTO(rr.getId());
        String longTitle = "a".repeat(201);
        dto.setTitle(longTitle);

        when(recruitmentRequestService.findById(rr.getId())).thenReturn(rr);

        // 2. Thực thi & Kiểm tra
        assertThatThrownBy(() -> jobPositionService.create(dto))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Tiêu đề không được quá 200 ký tự");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC28 create - SalaryMin > SalaryMax")
    void create_SalaryMinGreaterThanMax_ShouldThrowException_ButBugAllowsIt() throws Exception {
        // 1. Chuẩn bị
        RecruitmentRequest rr = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr.setDepartmentId(1L);
        rr = recruitmentRequestRepository.save(rr);
        
        CreateJobPositionDTO dto = createSampleCreateJobPositionDTO(rr.getId());
        dto.setSalaryMin(new BigDecimal("30000000"));
        dto.setSalaryMax(new BigDecimal("20000000")); // Max < Min

        when(recruitmentRequestService.findById(rr.getId())).thenReturn(rr);

        // 2. Thực thi & Kiểm tra
        assertThatThrownBy(() -> jobPositionService.create(dto))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Lương tối thiểu không được lớn hơn lương tối đa");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC29 create - Requirements rỗng")
    void create_EmptyRequirements_ShouldThrowException_ButBugAllowsIt() throws Exception {
        // 1. Chuẩn bị
        RecruitmentRequest rr = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr.setDepartmentId(1L);
        rr = recruitmentRequestRepository.save(rr);
        
        CreateJobPositionDTO dto = createSampleCreateJobPositionDTO(rr.getId());
        dto.setRequirements("");

        when(recruitmentRequestService.findById(rr.getId())).thenReturn(rr);

        // 2. Thực thi & Kiểm tra
        assertThatThrownBy(() -> jobPositionService.create(dto))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Yêu cầu công việc không được để trống");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC30 create - Bỏ trống trường bắt buộc")
    void create_EmptyRequiredFields_ShouldThrowException_ButBugAllowsIt() throws Exception {
        // lưu location rỗng.
        // 1. Chuẩn bị
        RecruitmentRequest rr = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr.setDepartmentId(1L);
        rr = recruitmentRequestRepository.save(rr);
        
        CreateJobPositionDTO dto = createSampleCreateJobPositionDTO(rr.getId());
        dto.setLocation(""); // Bỏ trống địa điểm

        when(recruitmentRequestService.findById(rr.getId())).thenReturn(rr);

        // 2. Thực thi & Kiểm tra
        assertThatThrownBy(() -> jobPositionService.create(dto))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Địa điểm không được để trống");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC31 publish - Publish quá hạn")
    void publish_PastDeadline_ShouldThrowException_ButBugAllowsIt() throws Exception {
        // 1. Chuẩn bị
        JobPosition jp = createSampleJobPosition("Past Deadline", JobPositionStatus.DRAFT);
        jp.setDeadline(LocalDate.now().minusDays(5)); // Quá hạn 5 ngày
        jp = jobPositionRepository.save(jp);

        // 2. Thực thi & Kiểm tra
        final Long jpId = jp.getId();
        assertThatThrownBy(() -> jobPositionService.publish(jpId))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Không thể publish vị trí đã quá hạn");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC32: create - isRemote default")
    void create_DtoWithNullIsRemote_ShouldDefaultToFalse() throws IdInvalidException {
        // 1. Chuẩn bị
        RecruitmentRequest rr = new RecruitmentRequest();
        rr.setDepartmentId(10L);
        rr.setStatus(RecruitmentRequestStatus.DRAFT);
        rr = recruitmentRequestRepository.save(rr);
        when(recruitmentRequestService.findById(rr.getId())).thenReturn(rr);

        CreateJobPositionDTO dto = new CreateJobPositionDTO();
        dto.setRecruitmentRequestId(rr.getId());
        dto.setTitle("Dev");
        dto.setIsRemote(null); 
        
        // 2. Thực thi
        JobPosition saved = jobPositionService.create(dto);

        // 3. Kiểm tra
        assertThat(saved.isRemote()).isFalse();
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC33: findAllSimple - xử lý chuỗi ids có thành phần rỗng")
    void findAllWithFiltersSimple_WithEmptyIds_ShouldHandleGracefully() {
        // 1. Chuẩn bị
        JobPosition jp1 = jobPositionRepository.save(createSampleJobPosition("Job 1", JobPositionStatus.DRAFT));
        JobPosition jp2 = jobPositionRepository.save(createSampleJobPosition("Job 2", JobPositionStatus.DRAFT));

        // 2. Thực thi (Truyền chuỗi có dấu phẩy thừa "1,,2")
        List<JobPosition> result = jobPositionService.findAllWithFiltersSimple(null, null, null, null, jp1.getId() + ", , " + jp2.getId());

        // 3. Kiểm tra
        assertThat(result).hasSize(2);
        assertThat(result).extracting("id").containsExactlyInAnyOrder(jp1.getId(), jp2.getId());
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC34: findAllSimple - xử lý ids sai định dạng (fallback)")
    void findAllWithFiltersSimple_WithInvalidIdFormat_ShouldCatchExceptionAndFallback() {
        // 1. Chuẩn bị
        jobPositionRepository.save(createSampleJobPosition("Job Fallback", JobPositionStatus.DRAFT));

        // 2. Thực thi (Truyền "invalid" thay vì số)
        List<JobPosition> result = jobPositionService.findAllWithFiltersSimple(null, null, null, null, "invalid");

        // 3. Kiểm tra (Không crash, trả về kết quả từ bộ lọc fallback)
        assertThat(result).isNotEmpty();
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC35: findAllPaged - xử lý chuỗi ids có thành phần rỗng (Paged)")
    void findAllWithFiltersSimplePaged_WithEmptyIds_ShouldHandleGracefully() {
        // 1. Chuẩn bị
        JobPosition jp = jobPositionRepository.save(createSampleJobPosition("Job Paged", JobPositionStatus.DRAFT));

        // 2. Thực thi
        PaginationDTO result = jobPositionService.findAllWithFiltersSimplePaged(null, null, null, null, ", " + jp.getId() + ",", PageRequest.of(0, 10));

        // 3. Kiểm tra
        assertThat(result.getResult()).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC36: findAllPaged - xử lý ids sai định dạng (Paged)")
    void findAllWithFiltersSimplePaged_WithInvalidIdFormat_ShouldCatchException() {
        // 2. Thực thi
        PaginationDTO result = jobPositionService.findAllWithFiltersSimplePaged(null, null, null, null, "invalid", PageRequest.of(0, 10));

        // 3. Kiểm tra
        assertThat(result.getResult()).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC37: findAllWithFilters - Xử lý đặc biệt departmentId = 1")
    void findAllWithFilters_WithSpecialDepartmentId_ShouldReturnAll() {
        // 1. Chuẩn bị
        // Tạo 2 JobPosition ở 2 phòng ban khác nhau (10 và 20)
        RecruitmentRequest rr1 = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr1.setDepartmentId(10L);
        rr1 = recruitmentRequestRepository.save(rr1);
        JobPosition jp1 = createSampleJobPosition("Job 1", JobPositionStatus.PUBLISHED);
        jp1.setRecruitmentRequest(rr1);
        jobPositionRepository.save(jp1);

        RecruitmentRequest rr2 = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr2.setDepartmentId(20L);
        rr2 = recruitmentRequestRepository.save(rr2);
        JobPosition jp2 = createSampleJobPosition("Job 2", JobPositionStatus.PUBLISHED);
        jp2.setRecruitmentRequest(rr2);
        jobPositionRepository.save(jp2);

        when(userService.getDepartmentsByIds(any(), anyString())).thenReturn(Map.of());
        when(candidateClient.countCandidatesByJobPositionIds(any(), anyString())).thenReturn(Map.of());

        // 2. Thực thi
        // Truyền departmentId = 1L (Giá trị đặc biệt sẽ được service chuyển về null)
        PaginationDTO result = jobPositionService.findAllWithFilters(1L, null, null, null, PageRequest.of(0, 10), "token");

        // 3. Kiểm tra
        // Kết quả phải trả về cả 2 bản ghi (vì null nghĩa là không lọc theo phòng ban)
        assertThat(result.getMeta().getTotal()).isEqualTo(2);
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC38: update - Cập nhật toàn bộ các trường (All fields)")
    void update_AllFieldsProvided_ShouldUpdateEverything() throws IdInvalidException {
        // 1. Chuẩn bị
        // Tạo JobPosition cũ với các giá trị ban đầu
        JobPosition pos = new JobPosition();
        pos.setTitle("Old Title");
        pos.setDescription("Old Desc");
        pos.setRequirements("Old Req");
        pos.setBenefits("Old Ben");
        pos.setSalaryMin(java.math.BigDecimal.valueOf(500));
        pos.setSalaryMax(java.math.BigDecimal.valueOf(1000));
        pos.setEmploymentType("PT");
        pos.setExperienceLevel("INTERN");
        pos.setLocation("HCM");
        pos.setRemote(false);
        pos.setQuantity(5);
        pos.setDeadline(LocalDate.now().plusDays(1));
        pos.setYearsOfExperience("0 year");
        pos.setStatus(JobPositionStatus.DRAFT);
        pos = jobPositionRepository.save(pos);

        // Chuẩn bị DTO với các giá trị mới hoàn toàn
        UpdateJobPositionDTO dto = new UpdateJobPositionDTO();
        dto.setTitle("New Title");
        dto.setDescription("New Desc");
        dto.setRequirements("New Req");
        dto.setBenefits("New Ben");
        dto.setSalaryMin(java.math.BigDecimal.valueOf(1500));
        dto.setSalaryMax(java.math.BigDecimal.valueOf(2500));
        dto.setEmploymentType("FT");
        dto.setExperienceLevel("SENIOR");
        dto.setLocation("HANOI");
        dto.setIsRemote(true);
        dto.setQuantity(20);
        dto.setDeadline(LocalDate.now().plusDays(10));
        dto.setYearsOfExperience("5 years");

        // 2. Thực thi
        JobPosition updated = jobPositionService.update(pos.getId(), dto);

        // 3. Kiểm tra
        assertThat(updated.getTitle()).isEqualTo("New Title");
        assertThat(updated.getDescription()).isEqualTo("New Desc");
        assertThat(updated.getRequirements()).isEqualTo("New Req");
        assertThat(updated.getBenefits()).isEqualTo("New Ben");
        assertThat(updated.getSalaryMin()).isEqualByComparingTo(java.math.BigDecimal.valueOf(1500));
        assertThat(updated.getSalaryMax()).isEqualByComparingTo(java.math.BigDecimal.valueOf(2500));
        assertThat(updated.getEmploymentType()).isEqualTo("FT");
        assertThat(updated.getExperienceLevel()).isEqualTo("SENIOR");
        assertThat(updated.getLocation()).isEqualTo("HANOI");
        assertThat(updated.isRemote()).isTrue();
        assertThat(updated.getQuantity()).isEqualTo(20);
        assertThat(updated.getDeadline()).isEqualTo(dto.getDeadline());
        assertThat(updated.getYearsOfExperience()).isEqualTo("5 years");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC39: findAllWithFilters - Trường hợp departmentId = null")
    void findAllWithFilters_WithNullDepartmentId_ShouldReturnAll() {
        // 1. Chuẩn bị
        // Tạo 2 JobPosition ở các phòng ban khác nhau
        RecruitmentRequest rr1 = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr1.setDepartmentId(10L);
        rr1 = recruitmentRequestRepository.save(rr1);
        JobPosition jp1 = createSampleJobPosition("Job 1", JobPositionStatus.PUBLISHED);
        jp1.setRecruitmentRequest(rr1);
        jobPositionRepository.save(jp1);

        RecruitmentRequest rr2 = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr2.setDepartmentId(20L);
        rr2 = recruitmentRequestRepository.save(rr2);
        JobPosition jp2 = createSampleJobPosition("Job 2", JobPositionStatus.PUBLISHED);
        jp2.setRecruitmentRequest(rr2);
        jobPositionRepository.save(jp2);

        when(userService.getDepartmentsByIds(any(), anyString())).thenReturn(Map.of());
        when(candidateClient.countCandidatesByJobPositionIds(any(), anyString())).thenReturn(Map.of());

        // 2. Thực thi
        // Truyền departmentId = null -> Không lọc theo phòng ban
        PaginationDTO result = jobPositionService.findAllWithFilters(null, null, null, null, PageRequest.of(0, 10), "token");

        // 3. Kiểm tra
        // Phải trả về toàn bộ 2 bản ghi
        assertThat(result.getMeta().getTotal()).isEqualTo(2);
    }
    @Test
    @Transactional
    @DisplayName("JOB-TC40: findAllWithFiltersSimplified - Xử lý đặc biệt departmentId = 1")
    void findAllWithFiltersSimplified_WithSpecialDepartmentId_ShouldReturnAll() {
        // 1. Chuẩn bị
        // Tạo 2 JobPosition thuộc 2 phòng ban khác nhau (10 và 20)
        RecruitmentRequest rr1 = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr1.setDepartmentId(10L);
        rr1 = recruitmentRequestRepository.save(rr1);
        JobPosition jp1 = createSampleJobPosition("Job 1", JobPositionStatus.PUBLISHED);
        jp1.setRecruitmentRequest(rr1);
        jobPositionRepository.save(jp1);

        RecruitmentRequest rr2 = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr2.setDepartmentId(20L);
        rr2 = recruitmentRequestRepository.save(rr2);
        JobPosition jp2 = createSampleJobPosition("Job 2", JobPositionStatus.PUBLISHED);
        jp2.setRecruitmentRequest(rr2);
        jobPositionRepository.save(jp2);

        when(userService.getDepartmentsByIds(any(), anyString())).thenReturn(Map.of());
        when(candidateClient.countCandidatesByJobPositionIds(any(), anyString())).thenReturn(Map.of());

        // 2. Thực thi
        // Theo nghiệp vụ: departmentId = 1L nghĩa là "Tất cả phòng ban" -> Phải trả về cả 2 bản ghi
        PaginationDTO result = jobPositionService.findAllWithFiltersSimplified(1L, null, null, null, null, PageRequest.of(0, 10), "token");

        // 3. Kiểm tra
        assertThat(result.getMeta().getTotal()).isEqualTo(2);
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC41: findAllWithFiltersSimplified - Trường hợp departmentId = null")
    void findAllWithFiltersSimplified_WithNullDepartmentId_ShouldReturnAll() {
        // 1. Chuẩn bị
        // Tạo 2 JobPosition ở các phòng ban khác nhau
        RecruitmentRequest rr1 = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr1.setDepartmentId(10L);
        rr1 = recruitmentRequestRepository.save(rr1);
        JobPosition jp1 = createSampleJobPosition("Job 1", JobPositionStatus.PUBLISHED);
        jp1.setRecruitmentRequest(rr1);
        jobPositionRepository.save(jp1);

        RecruitmentRequest rr2 = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr2.setDepartmentId(20L);
        rr2 = recruitmentRequestRepository.save(rr2);
        JobPosition jp2 = createSampleJobPosition("Job 2", JobPositionStatus.PUBLISHED);
        jp2.setRecruitmentRequest(rr2);
        jobPositionRepository.save(jp2);

        when(userService.getDepartmentsByIds(any(), anyString())).thenReturn(Map.of());
        when(candidateClient.countCandidatesByJobPositionIds(any(), anyString())).thenReturn(Map.of());

        // 2. Thực thi
        // Truyền departmentId = null -> Không lọc theo phòng ban
        PaginationDTO result = jobPositionService.findAllWithFiltersSimplified(null, null, null, null, null, PageRequest.of(0, 10), "token");

        // 3. Kiểm tra
        // Phải trả về ít nhất 2 bản ghi vừa tạo
        assertThat(result.getMeta().getTotal()).isGreaterThanOrEqualTo(2);
    }
}

