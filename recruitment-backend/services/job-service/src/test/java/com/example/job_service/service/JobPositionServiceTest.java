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
        // [Chuẩn bị - Prepare]
        // Khởi tạo RecruitmentRequest với mức lương thấp hơn DTO để kiểm chứng tính ưu tiên của DTO
        RecruitmentRequest rr = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr.setSalaryMin(new BigDecimal("15000000")); 
        rr.setSalaryMax(new BigDecimal("25000000"));
        rr.setDepartmentId(1L);
        rr = recruitmentRequestRepository.save(rr);

        // Tạo DTO đầu vào với thông tin mức lương mới cao hơn RR
        CreateJobPositionDTO dto = createSampleCreateJobPositionDTO(rr.getId());
        dto.setTitle("Software Engineer");
        dto.setSalaryMin(new BigDecimal("20000000"));
        dto.setSalaryMax(new BigDecimal("30000000"));
        dto.setIsRemote(true);

        // Mock hành vi tìm kiếm RR từ RecruitmentRequestService
        when(recruitmentRequestService.findById(rr.getId())).thenReturn(rr);

        // [Thực thi - Act]
        // Gọi hàm nghiệp vụ thực hiện tạo vị trí tuyển dụng mới
        JobPosition saved = jobPositionService.create(dto);

        // [Kiểm tra - Assert]
        // 1. Xác nhận dữ liệu được lưu đúng theo DTO và trạng thái mặc định là DRAFT
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTitle()).isEqualTo("Software Engineer");
        assertThat(saved.getSalaryMin()).isEqualByComparingTo(new BigDecimal("20000000"));
        assertThat(saved.getSalaryMax()).isEqualByComparingTo(new BigDecimal("30000000"));
        assertThat(saved.isRemote()).isTrue();
        assertThat(saved.getStatus()).isEqualTo(JobPositionStatus.DRAFT);
        
        // 2. Xác nhận RecruitmentRequest đã được chuyển trạng thái sang COMPLETED sau khi tạo Job thành công
        verify(recruitmentRequestService).changeStatus(rr.getId(), RecruitmentRequestStatus.COMPLETED);
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC02: create - DTO không có salary, phải fallback từ RecruitmentRequest")
    void create_DtoWithNullSalary_ShouldFallbackToRecruitmentRequestSalary() throws Exception {
        // [Chuẩn bị - Prepare]
        // Khởi tạo RR có mức lương cố định để làm nguồn Fallback
        RecruitmentRequest rr = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr.setSalaryMin(new BigDecimal("15000000"));
        rr.setSalaryMax(new BigDecimal("25000000"));
        rr.setDepartmentId(1L);
        rr = recruitmentRequestRepository.save(rr);

        // Tạo DTO không chứa thông tin lương để kiểm chứng cơ chế Fallback tự động
        CreateJobPositionDTO dto = createSampleCreateJobPositionDTO(rr.getId());
        dto.setTitle("Product Manager");
        dto.setSalaryMin(null);
        dto.setSalaryMax(null);

        when(recruitmentRequestService.findById(rr.getId())).thenReturn(rr);

        // [Thực thi - Act]
        // Thực hiện tạo vị trí tuyển dụng khi thiếu thông tin lương trong DTO
        JobPosition saved = jobPositionService.create(dto);

        // [Kiểm tra - Assert]
        // Xác nhận mức lương của JobPosition được kế thừa chính xác từ RecruitmentRequest gốc
        assertThat(saved.getSalaryMin()).isEqualByComparingTo(new BigDecimal("15000000"));
        assertThat(saved.getSalaryMax()).isEqualByComparingTo(new BigDecimal("25000000"));
        assertThat(saved.isRemote()).isFalse(); // Default
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC03: findById - ID tồn tại")
    void findById_ExistingId_ShouldReturnJobPosition() throws Exception {
        // [Chuẩn bị - Prepare]
        // Lưu một bản ghi JobPosition mẫu trực tiếp vào cơ sở dữ liệu H2 để kiểm tra truy vấn
        JobPosition jp = createSampleJobPosition("Test JP", JobPositionStatus.DRAFT);
        jp = jobPositionRepository.save(jp);

        // [Thực thi - Act]
        // Thực hiện tìm kiếm thông tin vị trí bằng ID vừa khởi tạo
        JobPosition found = jobPositionService.findById(jp.getId());

        // [Kiểm tra - Assert]
        // Xác nhận kết quả tìm thấy phải khớp hoàn toàn với dữ liệu đã lưu trong DB
        assertThat(found).isNotNull();
        assertThat(found.getTitle()).isEqualTo("Test JP");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC04: getByIdSimple - ID tồn tại")
    void getByIdSimple_ExistingId_ShouldReturnJobPosition() throws Exception {
        // [Chuẩn bị - Prepare]
        // Lưu một bản ghi JobPosition đơn giản để kiểm chứng hàm lấy dữ liệu rút gọn
        JobPosition jp = createSampleJobPosition("Test JP Simple", JobPositionStatus.DRAFT);
        jp = jobPositionRepository.save(jp);

        // [Thực thi - Act]
        // Truy vấn dữ liệu theo phương thức getByIdSimple
        JobPosition found = jobPositionService.getByIdSimple(jp.getId());

        // [Kiểm tra - Assert]
        // Hệ thống phải trả về đúng Object JobPosition tương ứng với ID yêu cầu
        assertThat(found).isNotNull();
        assertThat(found.getTitle()).isEqualTo("Test JP Simple");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC05: getByIdWithDepartmentName - Lấy đủ metadata")
    void getByIdWithDepartmentName_ShouldReturnDtoWithDepartmentAndCount() throws Exception {
        // [Chuẩn bị - Prepare]
        // Thiết lập JobPosition liên kết với RR thuộc phòng ban ID = 5
        RecruitmentRequest rr = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr.setDepartmentId(5L);
        rr = recruitmentRequestRepository.save(rr);

        JobPosition jp = createSampleJobPosition("Test JP Dept", JobPositionStatus.DRAFT);
        jp.setRecruitmentRequest(rr);
        jp = jobPositionRepository.save(jp);

        // Mock API từ User Service để lấy tên phòng ban và API Candidate để lấy số lượng ứng viên
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode deptNode = mapper.createObjectNode();
        deptNode.put("name", "IT Department");
        when(userService.getDepartmentById(eq(5L), anyString())).thenReturn(ResponseEntity.ok(deptNode));
        when(candidateClient.countCandidatesByJobPositionId(eq(jp.getId()), anyString())).thenReturn(42);

        // [Thực thi - Act]
        // Thực hiện lấy thông tin chi tiết kèm metadata tên phòng ban
        JobPositionResponseDTO dto = jobPositionService.getByIdWithDepartmentName(jp.getId(), "token");

        // [Kiểm tra - Assert]
        // Xác nhận DTO kết quả đã được tổng hợp tên phòng ban và số lượng ứng viên chính xác
        assertThat(dto.getDepartmentName()).isEqualTo("IT Department");
        assertThat(dto.getApplicationCount()).isEqualTo(42);
        assertThat(dto.getTitle()).isEqualTo("Test JP Dept");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC06: getByIdsWithDepartmentName - Lấy theo list IDs")
    void getByIdsWithDepartmentName_ShouldReturnDtoList() throws Exception {
        // [Chuẩn bị - Prepare]
        // Tạo 2 vị trí công việc thuộc 2 phòng ban khác nhau (ID 5 và 6) để kiểm tra tổng hợp Metadata
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

        // Mock kết quả trả về từ User Service cho danh sách phòng ban tương ứng
        when(userService.getDepartmentsByIds(any(), anyString())).thenReturn(Map.of(5L, "Dept 5", 6L, "Dept 6"));

        // [Thực thi - Act]
        // Thực hiện lấy danh sách JobPositionResponseDTO theo tập hợp IDs
        List<JobPositionResponseDTO> dtoList = jobPositionService.getByIdsWithDepartmentName(List.of(jp1.getId(), jp2.getId()), "token");

        // [Kiểm tra - Assert]
        // Xác nhận danh sách trả về chứa đủ 2 phần tử và tên phòng ban đã được ánh xạ chính xác
        assertThat(dtoList).hasSize(2);
        assertThat(dtoList).extracting("departmentName").containsExactlyInAnyOrder("Dept 5", "Dept 6");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC07: getByIdsWithDepartmentName - Danh sách IDs trống")
    void getByIdsWithDepartmentName_EmptyIds_ShouldReturnEmptyList() {
        // [Chuẩn bị - Prepare]
        // Sử dụng danh sách rỗng và giá trị null để kiểm tra tính an toàn của hệ thống khi không có dữ liệu đầu vào
        List<Long> emptyIds = List.of();

        // [Thực thi - Act]
        // Thực hiện truy vấn với các trường hợp đầu vào rỗng/null
        List<JobPositionResponseDTO> dtoList = jobPositionService.getByIdsWithDepartmentName(emptyIds, "token");
        List<JobPositionResponseDTO> dtoListNull = jobPositionService.getByIdsWithDepartmentName(null, "token");
        
        // [Kiểm tra - Assert]
        // Hệ thống phải xử lý an toàn và trả về danh sách rỗng thay vì gây lỗi NullPointerException
        assertThat(dtoList).isEmpty();
        assertThat(dtoListNull).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC08: getByIdWithPublished - Status PUBLISHED")
    void getByIdWithPublished_PublishedStatus_ShouldReturnJobPosition() throws Exception {
        // [Chuẩn bị - Prepare]
        // Thiết lập một vị trí tuyển dụng đã được hoàn tất và xuất bản (PUBLISHED)
        JobPosition jp = createSampleJobPosition("Published Job", JobPositionStatus.PUBLISHED);
        jp = jobPositionRepository.save(jp);

        // [Thực thi - Act]
        // Thực hiện tìm kiếm công khai bằng ID dành cho ứng viên
        JobPosition found = jobPositionService.getByIdWithPublished(jp.getId());

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống cho phép truy xuất dữ liệu vì trạng thái hợp lệ là PUBLISHED
        assertThat(found.getStatus()).isEqualTo(JobPositionStatus.PUBLISHED);
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC09: findAllWithFiltersSimple - Có ids hợp lệ")
    void findAllWithFiltersSimple_WithIds_ShouldReturnMatchingIds() {
        // [Chuẩn bị - Prepare]
        // Tạo và lưu 2 bản ghi riêng biệt vào DB H2 để kiểm chứng khả năng lọc theo chuỗi ID
        RecruitmentRequest rr1 = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr1 = recruitmentRequestRepository.save(rr1);
        JobPosition jp1 = createSampleJobPosition("JP1", JobPositionStatus.DRAFT);
        jp1.setRecruitmentRequest(rr1);

        RecruitmentRequest rr2 = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr2 = recruitmentRequestRepository.save(rr2);
        JobPosition jp2 = createSampleJobPosition("JP2", JobPositionStatus.DRAFT);
        jp2.setRecruitmentRequest(rr2);

        jobPositionRepository.saveAll(List.of(jp1, jp2));
 
        // [Thực thi - Act]
        // Thực hiện lọc dữ liệu bằng chuỗi kết hợp IDs (ví dụ: "1,2")
        List<JobPosition> result = jobPositionService.findAllWithFiltersSimple(null, null, null, null, jp1.getId() + "," + jp2.getId());
        
        // [Kiểm tra - Assert]
        // Hệ thống phải bóc tách chuỗi và trả về đúng 2 bản ghi khớp với danh sách IDs đã truyền
        assertThat(result).hasSize(2);
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC10: findAllWithFiltersSimple - Tìm theo filters")
    void findAllWithFiltersSimple_WithoutIds_ShouldReturnByFilters() {
        // [Chuẩn bị - Prepare]
        // Lưu bản ghi có tiêu đề cụ thể "Backend" để kiểm tra bộ lọc văn bản
        RecruitmentRequest rr = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr = recruitmentRequestRepository.save(rr);

        JobPosition jp1 = createSampleJobPosition("Backend", JobPositionStatus.DRAFT);
        jp1.setRecruitmentRequest(rr);
        jobPositionRepository.save(jp1);

        // [Thực thi - Act]
        // Truy vấn danh sách vị trí với từ khóa tiêu đề là "Backend" và không dùng IDs
        List<JobPosition> result = jobPositionService.findAllWithFiltersSimple(null, null, null, "Backend", null);

        // [Kiểm tra - Assert]
        // Xác nhận danh sách kết quả chứa đúng bản ghi có tiêu đề khớp với từ khóa đã tìm
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Backend");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC11: findAllWithFiltersSimplePaged - Phân trang IDs")
    void findAllWithFiltersSimplePaged_WithIds_ShouldReturnPagedIds() {
        // [Chuẩn bị - Prepare]
        // Lưu 3 bản ghi mẫu và tạo chuỗi IDs kết hợp để kiểm tra phân trang
        JobPosition jp1 = createSampleJobPosition("JP1", JobPositionStatus.DRAFT);
        JobPosition jp2 = createSampleJobPosition("JP2", JobPositionStatus.DRAFT);
        JobPosition jp3 = createSampleJobPosition("JP3", JobPositionStatus.DRAFT);
        jobPositionRepository.saveAll(List.of(jp1, jp2, jp3));
        
        String ids = jp1.getId() + "," + jp2.getId() + "," + jp3.getId();

        // [Thực thi - Act]
        // Thực hiện phân trang với PageSize = 2 (Trang đầu tiên)
        Pageable pageable = PageRequest.of(0, 2); 
        PaginationDTO result = jobPositionService.findAllWithFiltersSimplePaged(null, null, null, null, ids, pageable);

        // [Kiểm tra - Assert]
        // 1. Tổng số bản ghi (Meta Total) phải là 3
        assertThat(result.getMeta().getTotal()).isEqualTo(3); 
        // 2. Danh sách trả về thực tế trong trang 1 chỉ có 2 bản ghi (jp1, jp2) do giới hạn PageSize
        assertThat(((List<?>) result.getResult())).hasSize(2); 
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC12: findAllWithFiltersSimplePaged - IDs sai định dạng")
    void findAllWithFiltersSimplePaged_WithInvalidIds_ShouldFallbackToEmpty() {
        // [Chuẩn bị - Prepare]
        // Thiết lập cấu hình phân trang cơ bản
        Pageable pageable = PageRequest.of(0, 2);

        // [Thực thi - Act]
        // Truyền giá trị null cho tham số IDs để hệ thống rẽ nhánh sang bộ lọc thông thường
        PaginationDTO resultNull = jobPositionService.findAllWithFiltersSimplePaged(null, null, null, null, null, pageable);
        
        // [Kiểm tra - Assert]
        // Hệ thống phải xử lý an toàn và trả về kết quả rỗng (do không có job nào khớp filter mặc định) thay vì crash
        assertThat((List<?>) resultNull.getResult()).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC13: findAllWithFilters - Trả về PaginationDTO")
    void findAllWithFilters_ShouldReturnPaginationDTO() {
        // [Chuẩn bị - Prepare]
        // Thiết lập dữ liệu liên kết phòng ban ID = 5 và lưu JobPosition tương ứng
        RecruitmentRequest rr = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr.setDepartmentId(5L);
        rr = recruitmentRequestRepository.save(rr);
        JobPosition jp = createSampleJobPosition("JP for count", JobPositionStatus.DRAFT);
        jp.setRecruitmentRequest(rr);
        jobPositionRepository.save(jp);

        // Mock các API ngoài để lấy Metadata đi kèm (Tên phòng ban và Số ứng viên)
        when(userService.getDepartmentsByIds(any(), anyString())).thenReturn(Map.of(5L, "Dept 5"));
        when(candidateClient.countCandidatesByJobPositionIds(any(), anyString())).thenReturn(Map.of(jp.getId(), 10));

        // [Thực thi - Act]
        // Truy vấn danh sách vị trí có phân trang theo phòng ban ID = 5
        Pageable pageable = PageRequest.of(0, 10);
        PaginationDTO result = jobPositionService.findAllWithFilters(5L, null, null, null, pageable, "token");

        // [Kiểm tra - Assert]
        // Xác nhận DTO kết quả được tổng hợp đầy đủ thông tin: tổng số bản ghi và dữ liệu chi tiết của Item đầu tiên
        assertThat(result.getMeta().getTotal()).isEqualTo(1);
        List<JobPositionResponseDTO> dtoList = (List<JobPositionResponseDTO>) result.getResult();
        assertThat(dtoList.get(0).getDepartmentName()).isEqualTo("Dept 5");
        assertThat(dtoList.get(0).getApplicationCount()).isEqualTo(10);
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC14: findAllWithFiltersSimplified - Trả về PaginationDTO")
    void findAllWithFiltersSimplified_ShouldReturnPaginationDTO() {
        // [Chuẩn bị - Prepare]
        // Thiết lập bộ dữ liệu mẫu cho phiên bản truy vấn rút gọn
        RecruitmentRequest rr = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr.setDepartmentId(5L);
        rr = recruitmentRequestRepository.save(rr);
        JobPosition jp = createSampleJobPosition("JP simplified", JobPositionStatus.DRAFT);
        jp.setRecruitmentRequest(rr);
        jobPositionRepository.save(jp);

        // Mock các dịch vụ hỗ trợ metadata
        when(userService.getDepartmentsByIds(any(), anyString())).thenReturn(Map.of(5L, "Dept 5"));
        when(candidateClient.countCandidatesByJobPositionIds(any(), anyString())).thenReturn(Map.of(jp.getId(), 10));

        // [Thực thi - Act]
        // Thực hiện truy vấn rút gọn (Simplified) có phân trang
        Pageable pageable = PageRequest.of(0, 10);
        PaginationDTO result = jobPositionService.findAllWithFiltersSimplified(5L, null, null, null, null, pageable, "token");

        // [Kiểm tra - Assert]
        // Xác nhận dữ liệu phân trang trả về hợp lệ và đúng số lượng dự kiến
        assertThat(result.getMeta().getTotal()).isEqualTo(1);
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC15: update - DTO")
    void update_ExistingId_ShouldUpdateFields() throws Exception {
        // [Chuẩn bị - Prepare]
        // Lưu bản ghi gốc vào DB H2 trước khi thực hiện chỉnh sửa
        JobPosition jp = createSampleJobPosition("Old Title", JobPositionStatus.DRAFT);
        jp = jobPositionRepository.save(jp);

        // Khởi tạo DTO cập nhật với tiêu đề mới và mức lương tối thiểu mới
        UpdateJobPositionDTO dto = new UpdateJobPositionDTO();
        dto.setTitle("New Title");
        dto.setSalaryMin(new BigDecimal("100"));

        // [Thực thi - Act]
        // Gọi hàm nghiệp vụ thực hiện cập nhật thông tin vị trí
        JobPosition updated = jobPositionService.update(jp.getId(), dto);

        // [Kiểm tra - Assert]
        // Xác nhận các trường dữ liệu thực tế trong DB đã được thay đổi chính xác theo DTO
        assertThat(updated.getTitle()).isEqualTo("New Title");
        assertThat(updated.getSalaryMin()).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC16: delete - Xóa thành công")
    void delete_ExistingId_ShouldDeleteJobPosition() throws Exception {
        // [Chuẩn bị - Prepare]
        // Khởi tạo và lưu bản ghi JobPosition mục tiêu cần xóa
        JobPosition jp = createSampleJobPosition("To Delete", JobPositionStatus.DRAFT);
        jp = jobPositionRepository.save(jp);

        // [Thực thi - Act]
        // Thực hiện hành động xóa bản ghi khỏi hệ thống bằng ID
        boolean result = jobPositionService.delete(jp.getId());

        // [Kiểm tra - Assert]
        // 1. Xác nhận hàm xóa trả về true
        assertThat(result).isTrue();
        // 2. Truy vấn trực tiếp Repository để đảm bảo bản ghi đã thực sự bị loại bỏ khỏi Database
        assertThat(jobPositionRepository.findById(jp.getId())).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC17: publish - Thành công")
    void publish_DraftPosition_ShouldSetPublishedStatusAndPublishedAt() throws Exception {
        // [Chuẩn bị - Prepare]
        // Thiết lập vị trí tuyển dụng đang ở trạng thái bản nháp (DRAFT)
        JobPosition jp = createSampleJobPosition("Draft to Publish", JobPositionStatus.DRAFT);
        jp = jobPositionRepository.save(jp);

        // [Thực thi - Act]
        // Thực hiện lệnh xuất bản (Publish) vị trí tuyển dụng
        jobPositionService.publish(jp.getId());

        // [Kiểm tra - Assert]
        // Truy vấn lại từ DB để xác nhận trạng thái chuyển sang PUBLISHED và thời điểm xuất bản được ghi nhận
        JobPosition publishedInDb = jobPositionRepository.findById(jp.getId()).orElse(null);
        assertThat(publishedInDb).isNotNull();
        assertThat(publishedInDb.getStatus()).isEqualTo(JobPositionStatus.PUBLISHED);
        assertThat(publishedInDb.getPublishedAt()).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC18: close - Thành công")
    void close_PublishedPosition_ShouldSetClosedStatus() throws Exception {
        // [Chuẩn bị - Prepare]
        // Thiết lập vị trí tuyển dụng đang hiển thị công khai (PUBLISHED)
        JobPosition jp = createSampleJobPosition("Published to Close", JobPositionStatus.PUBLISHED);
        jp = jobPositionRepository.save(jp);

        // [Thực thi - Act]
        // Thực hiện lệnh đóng (Close) vị trí tuyển dụng
        jobPositionService.close(jp.getId());

        // [Kiểm tra - Assert]
        // Xác nhận bản ghi trong cơ sở dữ liệu đã chuyển sang trạng thái CLOSED thành công
        JobPosition closedInDb = jobPositionRepository.findById(jp.getId()).orElse(null);
        assertThat(closedInDb).isNotNull();
        assertThat(closedInDb.getStatus()).isEqualTo(JobPositionStatus.CLOSED);
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC19: reopen - Thành công")
    void reopen_ClosedPosition_ShouldSetPublishedStatus() throws Exception {
        // [Chuẩn bị - Prepare]
        // Thiết lập một vị trí tuyển dụng đã bị đóng (CLOSED) từ trước
        JobPosition jp = createSampleJobPosition("Closed to Reopen", JobPositionStatus.CLOSED);
        jp = jobPositionRepository.save(jp);

        // [Thực thi - Act]
        // Thực hiện mở lại (Reopen) vị trí này để cho phép ứng viên ứng tuyển tiếp
        jobPositionService.reopen(jp.getId());

        // [Kiểm tra - Assert]
        // Xác nhận trạng thái vị trí đã quay trở lại PUBLISHED trong Database
        JobPosition reopenedInDb = jobPositionRepository.findById(jp.getId()).orElse(null);
        assertThat(reopenedInDb).isNotNull();
        assertThat(reopenedInDb.getStatus()).isEqualTo(JobPositionStatus.PUBLISHED);
    }

    @Test
    @DisplayName("JOB-TC20: create - RR không tồn tại")
    void create_RecruitmentRequestNotFound_ShouldThrowIdInvalidException() throws Exception {
        // [Chuẩn bị - Prepare]
        // Mô phỏng kịch bản RecruitmentRequestService ném ngoại lệ khi không tìm thấy mã yêu cầu (ID 999)
        when(recruitmentRequestService.findById(999L)).thenThrow(new IdInvalidException("Not found"));
        CreateJobPositionDTO dto = new CreateJobPositionDTO();
        dto.setRecruitmentRequestId(999L);

        // [Thực thi & Kiểm tra - Act & Assert]
        // Thực hiện gọi hàm tạo và xác nhận hệ thống phải ném đúng loại ngoại lệ IdInvalidException
        assertThatThrownBy(() -> jobPositionService.create(dto))
                .isInstanceOf(IdInvalidException.class);
    }

    @Test
    @DisplayName("JOB-TC21: findById - ID không tồn tại")
    void findById_NonExistingId_ShouldThrowIdInvalidException() {
        // [Chuẩn bị - Prepare]
        // Sử dụng một ID giả định (999) chắc chắn không tồn tại trong DB H2 rỗng
        Long nonExistentId = 999L;
        
        // [Thực thi & Kiểm tra - Act & Assert]
        // Thực hiện tìm kiếm và xác nhận hệ thống ném ngoại lệ IdInvalidException kèm thông báo lỗi phù hợp
        assertThatThrownBy(() -> jobPositionService.findById(nonExistentId))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("không tồn tại");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC22: getByIdWithPublished - Không phải PUBLISHED")
    void getByIdWithPublished_NotPublished_ShouldThrowIdInvalidException() {
        // [Chuẩn bị - Prepare]
        // Thiết lập JobPosition ở trạng thái DRAFT (chưa xuất bản)
        JobPosition jp = createSampleJobPosition("Not Published", JobPositionStatus.DRAFT);
        JobPosition finalJp = jobPositionRepository.save(jp);

        // [Thực thi & Kiểm tra - Act & Assert]
        // Xác nhận hàm truy cập công khai ném ngoại lệ khi vị trí chưa ở trạng thái PUBLISHED
        assertThatThrownBy(() -> jobPositionService.getByIdWithPublished(finalJp.getId()))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("chưa được xuất bản");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC23: publish - Không phải DRAFT")
    void publish_NotDraftPosition_ShouldThrowIdInvalidException() {
        // [Chuẩn bị - Prepare]
        // Thiết lập JobPosition đã ở trạng thái PUBLISHED để kiểm tra ràng buộc chuyển đổi trạng thái
        JobPosition jp = createSampleJobPosition("Already Published", JobPositionStatus.PUBLISHED);
        JobPosition finalJp = jobPositionRepository.save(jp);

        // [Thực thi & Kiểm tra - Act & Assert]
        // Xác nhận không thể thực hiện lệnh publish một lần nữa nếu trạng thái hiện tại không phải DRAFT
        assertThatThrownBy(() -> jobPositionService.publish(finalJp.getId()))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Chỉ có thể publish vị trí ở trạng thái DRAFT");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC24: close - Không phải PUBLISHED")
    void close_NotPublishedPosition_ShouldThrowIdInvalidException() {
        // [Chuẩn bị - Prepare]
        // Thiết lập JobPosition đang ở trạng thái DRAFT (không thể đóng nếu chưa mở)
        JobPosition jp = createSampleJobPosition("Still Draft", JobPositionStatus.DRAFT);
        JobPosition finalJp = jobPositionRepository.save(jp);

        // [Thực thi & Kiểm tra - Act & Assert]
        // Xác nhận hệ thống ngăn chặn việc đóng các vị trí tuyển dụng chưa được xuất bản
        assertThatThrownBy(() -> jobPositionService.close(finalJp.getId()))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Chỉ có thể đóng vị trí ở trạng thái PUBLISHED");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC25: reopen - Không phải CLOSED")
    void reopen_NotClosedPosition_ShouldThrowIdInvalidException() {
        // [Chuẩn bị - Prepare]
        // Thiết lập JobPosition ở trạng thái DRAFT (không hợp lệ để thực hiện Reopen)
        JobPosition jp = createSampleJobPosition("Still Draft", JobPositionStatus.DRAFT);
        JobPosition finalJp = jobPositionRepository.save(jp);

        // [Thực thi & Kiểm tra - Act & Assert]
        // Xác nhận hệ thống chỉ cho phép mở lại các vị trí đã bị đóng từ trước
        assertThatThrownBy(() -> jobPositionService.reopen(finalJp.getId()))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Chỉ có thể mở lại vị trí ở trạng thái CLOSED");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC26 create - Deadline quá khứ")
    void create_PastDeadline_ShouldThrowException_ButBugAllowsIt() throws Exception {
        // [Chuẩn bị - Prepare]
        // Khởi tạo RR và DTO với hạn nộp hồ sơ đã trôi qua (hôm qua)
        RecruitmentRequest rr = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr.setDepartmentId(1L);
        rr = recruitmentRequestRepository.save(rr);
        
        CreateJobPositionDTO dto = createSampleCreateJobPositionDTO(rr.getId());
        dto.setDeadline(LocalDate.now().minusDays(1)); 

        when(recruitmentRequestService.findById(rr.getId())).thenReturn(rr);

        // [Thực thi & Kiểm tra - Act & Assert]
        // Kiểm chứng tính đúng đắn của việc kiểm tra logic nghiệp vụ: Không cho phép ngày hết hạn ở quá khứ
        assertThatThrownBy(() -> jobPositionService.create(dto))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Hạn nộp hồ sơ không được ở trong quá khứ");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC27 create - Title > 200 ký tự")
    void create_TitleTooLong_ShouldThrowException_ButBugAllowsIt() throws Exception {
        // [Chuẩn bị - Prepare]
        // Thiết lập tiêu đề vượt quá giới hạn tối đa (201 ký tự)
        RecruitmentRequest rr = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr.setDepartmentId(1L);
        rr = recruitmentRequestRepository.save(rr);
        
        CreateJobPositionDTO dto = createSampleCreateJobPositionDTO(rr.getId());
        String longTitle = "a".repeat(201);
        dto.setTitle(longTitle);

        when(recruitmentRequestService.findById(rr.getId())).thenReturn(rr);

        // [Thực thi & Kiểm tra - Act & Assert]
        // Kiểm chứng tính đúng đắn của việc kiểm tra độ dài tiêu đề để bảo vệ cơ sở dữ liệu
        assertThatThrownBy(() -> jobPositionService.create(dto))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Tiêu đề không được quá 200 ký tự");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC28 create - SalaryMin > SalaryMax")
    void create_SalaryMinGreaterThanMax_ShouldThrowException_ButBugAllowsIt() throws Exception {
        // [Chuẩn bị - Prepare]
        // Thiết lập khoảng lương phi logic (Min: 30tr > Max: 20tr)
        RecruitmentRequest rr = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr.setDepartmentId(1L);
        rr = recruitmentRequestRepository.save(rr);
        
        CreateJobPositionDTO dto = createSampleCreateJobPositionDTO(rr.getId());
        dto.setSalaryMin(new BigDecimal("30000000"));
        dto.setSalaryMax(new BigDecimal("20000000")); 

        when(recruitmentRequestService.findById(rr.getId())).thenReturn(rr);

        // [Thực thi & Kiểm tra - Act & Assert]
        // Kiểm chứng tính đúng đắn của logic so khớp khoảng lương
        assertThatThrownBy(() -> jobPositionService.create(dto))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Lương tối thiểu không được lớn hơn lương tối đa");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC29 create - Requirements rỗng")
    void create_EmptyRequirements_ShouldThrowException_ButBugAllowsIt() throws Exception {
        // [Chuẩn bị - Prepare]
        // Thiết lập DTO với trường yêu cầu công việc là chuỗi rỗng
        RecruitmentRequest rr = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr.setDepartmentId(1L);
        rr = recruitmentRequestRepository.save(rr);
        
        CreateJobPositionDTO dto = createSampleCreateJobPositionDTO(rr.getId());
        dto.setRequirements("");

        when(recruitmentRequestService.findById(rr.getId())).thenReturn(rr);

        // [Thực thi & Kiểm tra - Act & Assert]
        // Kiểm chứng tính đúng đắn của việc kiểm tra dữ liệu bắt buộc (Yêu cầu công việc)
        assertThatThrownBy(() -> jobPositionService.create(dto))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Yêu cầu công việc không được để trống");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC30 create - Bỏ trống trường bắt buộc")
    void create_EmptyRequiredFields_ShouldThrowException_ButBugAllowsIt() throws Exception {
        // [Chuẩn bị - Prepare]
        // Thiết lập DTO với trường địa điểm làm việc là chuỗi rỗng
        RecruitmentRequest rr = createSampleRecruitmentRequest(RecruitmentRequestStatus.PENDING);
        rr.setDepartmentId(1L);
        rr = recruitmentRequestRepository.save(rr);
        
        CreateJobPositionDTO dto = createSampleCreateJobPositionDTO(rr.getId());
        dto.setLocation(""); 

        when(recruitmentRequestService.findById(rr.getId())).thenReturn(rr);

        // [Thực thi & Kiểm tra - Act & Assert]
        // Kiểm chứng tính đúng đắn của việc kiểm tra dữ liệu bắt buộc (Địa điểm)
        assertThatThrownBy(() -> jobPositionService.create(dto))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Địa điểm không được để trống");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC31 publish - Publish quá hạn")
    void publish_PastDeadline_ShouldThrowException_ButBugAllowsIt() throws Exception {
        // [Chuẩn bị - Prepare]
        // Lưu JobPosition đã hết hạn nộp hồ sơ từ 5 ngày trước
        JobPosition jp = createSampleJobPosition("Past Deadline", JobPositionStatus.DRAFT);
        jp.setDeadline(LocalDate.now().minusDays(5)); 
        jp = jobPositionRepository.save(jp);

        // [Thực thi & Kiểm tra - Act & Assert]
        // Xác nhận hệ thống ngăn chặn việc xuất bản các tin tuyển dụng đã quá hạn
        final Long jpId = jp.getId();
        assertThatThrownBy(() -> jobPositionService.publish(jpId))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Không thể publish vị trí đã quá hạn");
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC32: create - isRemote default")
    void create_DtoWithNullIsRemote_ShouldDefaultToFalse() throws IdInvalidException {
        // [Chuẩn bị - Prepare]
        // Thiết lập RR và DTO nhưng bỏ trống thông tin IsRemote
        RecruitmentRequest rr = new RecruitmentRequest();
        rr.setDepartmentId(10L);
        rr.setStatus(RecruitmentRequestStatus.DRAFT);
        rr = recruitmentRequestRepository.save(rr);
        when(recruitmentRequestService.findById(rr.getId())).thenReturn(rr);

        CreateJobPositionDTO dto = new CreateJobPositionDTO();
        dto.setRecruitmentRequestId(rr.getId());
        dto.setTitle("Dev");
        dto.setIsRemote(null); 
        
        // [Thực thi - Act]
        // Thực hiện lệnh tạo vị trí mới
        JobPosition saved = jobPositionService.create(dto);

        // [Kiểm tra - Assert]
        // Xác nhận giá trị mặc định của trường isRemote phải là false khi không được cung cấp
        assertThat(saved.isRemote()).isFalse();
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC33: findAllSimple - xử lý chuỗi ids có thành phần rỗng")
    void findAllWithFiltersSimple_WithEmptyIds_ShouldHandleGracefully() {
        // [Chuẩn bị - Prepare]
        // Lưu 2 bản ghi mẫu để chuẩn bị cho việc lọc theo chuỗi IDs "bẩn"
        JobPosition jp1 = jobPositionRepository.save(createSampleJobPosition("Job 1", JobPositionStatus.DRAFT));
        JobPosition jp2 = jobPositionRepository.save(createSampleJobPosition("Job 2", JobPositionStatus.DRAFT));

        // [Thực thi - Act]
        // Thực hiện lọc dữ liệu bằng chuỗi chứa ký tự trống và dấu phẩy thừa (ví dụ: "1, , 2")
        List<JobPosition> result = jobPositionService.findAllWithFiltersSimple(null, null, null, null, jp1.getId() + ", , " + jp2.getId());

        // [Kiểm tra - Assert]
        // Hệ thống phải bóc tách dữ liệu thông minh, loại bỏ rác và trả về đúng 2 bản ghi hợp lệ
        assertThat(result).hasSize(2);
        assertThat(result).extracting("id").containsExactlyInAnyOrder(jp1.getId(), jp2.getId());
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC34: findAllSimple - xử lý ids sai định dạng (fallback)")
    void findAllWithFiltersSimple_WithInvalidIdFormat_ShouldCatchExceptionAndFallback() {
        // [Chuẩn bị - Prepare]
        // Đảm bảo có ít nhất một bản ghi trong DB để kiểm tra cơ chế Fallback
        jobPositionRepository.save(createSampleJobPosition("Job Fallback", JobPositionStatus.DRAFT));

        // [Thực thi - Act]
        // Truyền chuỗi không phải số ("invalid") vào tham số IDs để kích hoạt cơ chế bẫy lỗi
        List<JobPosition> result = jobPositionService.findAllWithFiltersSimple(null, null, null, null, "invalid");

        // [Kiểm tra - Assert]
        // Hệ thống không được gây lỗi Runtime và phải chuyển sang bộ lọc mặc định (trả về danh sách thay vì rỗng)
        assertThat(result).isNotEmpty();
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC35: findAllPaged - xử lý chuỗi ids có thành phần rỗng (Paged)")
    void findAllWithFiltersSimplePaged_WithEmptyIds_ShouldHandleGracefully() {
        // [Chuẩn bị - Prepare]
        // Lưu bản ghi mẫu để kiểm tra phân trang với dữ liệu IDs không chuẩn
        JobPosition jp = jobPositionRepository.save(createSampleJobPosition("Job Paged", JobPositionStatus.DRAFT));

        // [Thực thi - Act]
        // Thực hiện phân trang với chuỗi IDs chứa các thành phần rỗng kèm dấu phẩy
        PaginationDTO result = jobPositionService.findAllWithFiltersSimplePaged(null, null, null, null, ", " + jp.getId() + ",", PageRequest.of(0, 10));

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống vẫn xử lý phân trang bình thường và trả về kết quả hợp lệ cho IDs đúng
        assertThat(result.getResult()).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC36: findAllPaged - xử lý ids sai định dạng (Paged)")
    void findAllWithFiltersSimplePaged_WithInvalidIdFormat_ShouldCatchException() {
        // [Chuẩn bị - Prepare]
        // Thiết lập Pageable cơ bản
        Pageable pageable = PageRequest.of(0, 10);

        // [Thực thi - Act]
        // Truyền chuỗi văn bản thay vì mã số vào tham số IDs trong bối cảnh phân trang
        PaginationDTO result = jobPositionService.findAllWithFiltersSimplePaged(null, null, null, null, "invalid", pageable);

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống tự động bắt lỗi và trả về Object phân trang rỗng thay vì ném ngoại lệ
        assertThat(result.getResult()).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC37: findAllWithFilters - Xử lý đặc biệt departmentId = 1")
    void findAllWithFilters_WithSpecialDepartmentId_ShouldReturnAll() {
        // [Chuẩn bị - Prepare]
        // Tạo 2 vị trí công việc tại 2 phòng ban khác nhau (ID 10 và 20) để kiểm tra logic lọc tổng thể
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

        // [Thực thi - Act]
        // Thực hiện truy vấn với mã phòng ban đặc biệt (ID 1) - theo nghiệp vụ được hiểu là "Tất cả phòng ban"
        PaginationDTO result = jobPositionService.findAllWithFilters(1L, null, null, null, PageRequest.of(0, 10), "token");

        // [Kiểm tra - Assert]
        // Kết quả trả về phải chứa đầy đủ cả 2 vị trí của các phòng ban khác nhau (không bị lọc mất)
        assertThat(result.getMeta().getTotal()).isEqualTo(2);
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC38: update - Cập nhật toàn bộ các trường (All fields)")
    void update_AllFieldsProvided_ShouldUpdateEverything() throws IdInvalidException {
        // [Chuẩn bị - Prepare]
        // Thiết lập JobPosition gốc với các thông tin ban đầu để làm căn cứ so sánh sau khi cập nhật
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

        // Khởi tạo DTO cập nhật chứa đầy đủ các thông tin mới cho tất cả các trường dữ liệu
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

        // [Thực thi - Act]
        // Gọi hàm cập nhật cho bản ghi vừa tạo bằng DTO mới
        JobPosition updated = jobPositionService.update(pos.getId(), dto);

        // [Kiểm tra - Assert]
        // Xác nhận tỉ mỉ từng trường dữ liệu một để đảm bảo toàn bộ thông tin mới đã được ghi đè chính xác
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
        // [Chuẩn bị - Prepare]
        // Thiết lập 2 vị trí tuyển dụng tại 2 phòng ban khác nhau để làm tập dữ liệu test cho việc lọc rỗng
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

        // [Thực thi - Act]
        // Thực hiện truy vấn với departmentId = null - nghĩa là hệ thống không được lọc theo phòng ban nào cả
        PaginationDTO result = jobPositionService.findAllWithFilters(null, null, null, null, PageRequest.of(0, 10), "token");

        // [Kiểm tra - Assert]
        // Xác nhận danh sách trả về chứa đủ cả 2 vị trí của cả 2 phòng ban khác nhau
        assertThat(result.getMeta().getTotal()).isEqualTo(2);
    }
    @Test
    @Transactional
    @DisplayName("JOB-TC40: findAllWithFiltersSimplified - Xử lý đặc biệt departmentId = 1")
    void findAllWithFiltersSimplified_WithSpecialDepartmentId_ShouldReturnAll() {
        // [Chuẩn bị - Prepare]
        // Khởi tạo dữ liệu mẫu cho bộ lọc rút gọn với 2 phòng ban ID 10 và 20
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

        // [Thực thi - Act]
        // Thực hiện truy vấn rút gọn với departmentId = 1L (Giá trị đặc biệt mang ý nghĩa "Tất cả")
        PaginationDTO result = jobPositionService.findAllWithFiltersSimplified(1L, null, null, null, null, PageRequest.of(0, 10), "token");

        // [Kiểm tra - Assert]
        // Xác nhận hệ thống hiểu giá trị đặc biệt và trả về đầy đủ danh sách tin từ tất cả phòng ban
        assertThat(result.getMeta().getTotal()).isEqualTo(2);
    }

    @Test
    @Transactional
    @DisplayName("JOB-TC41: findAllWithFiltersSimplified - Trường hợp departmentId = null")
    void findAllWithFiltersSimplified_WithNullDepartmentId_ShouldReturnAll() {
        // [Chuẩn bị - Prepare]
        // Tạo 2 vị trí tuyển dụng mẫu tại các phòng ban khác nhau để kiểm tra truy vấn rút gọn không lọc
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

        // [Thực thi - Act]
        // Thực hiện truy vấn rút gọn với departmentId = null (Không áp dụng bộ lọc theo phòng ban)
        PaginationDTO result = jobPositionService.findAllWithFiltersSimplified(null, null, null, null, null, PageRequest.of(0, 10), "token");

        // [Kiểm tra - Assert]
        // Xác nhận kết quả phân trang bao gồm ít nhất 2 bản ghi vừa khởi tạo (do null = không lọc)
        assertThat(result.getMeta().getTotal()).isGreaterThanOrEqualTo(2);
    }
}

