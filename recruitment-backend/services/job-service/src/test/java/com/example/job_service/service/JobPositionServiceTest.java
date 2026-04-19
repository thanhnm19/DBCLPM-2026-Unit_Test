package com.example.job_service.service;

import com.example.job_service.dto.PaginationDTO;
import com.example.job_service.dto.jobposition.CreateJobPositionDTO;
import com.example.job_service.dto.jobposition.UpdateJobPositionDTO;
import com.example.job_service.exception.IdInvalidException;
import com.example.job_service.model.JobPosition;
import com.example.job_service.model.RecruitmentRequest;
import com.example.job_service.repository.JobPositionRepository;
import com.example.job_service.utils.enums.JobPositionStatus;
import com.example.job_service.utils.enums.RecruitmentRequestStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho JobPositionService - Module 6: Vị trí tuyển dụng.
 *
 * Chiến lược:
 * - Sử dụng Mockito để mock tất cả dependency (Repository, RecruitmentRequestService, ...).
 * - Mỗi test method là độc lập, không ảnh hưởng đến test khác (Rollback tự động qua Mockito).
 * - CheckDB được thực hiện bằng ArgumentCaptor hoặc verify() để xác nhận tham số truyền vào save().
 * - Đạt Branch Coverage: mỗi nhánh if/else, try/catch đều có ít nhất 1 test case kiểm tra.
 *
 * Rollback: Không dùng DB thật. Mockito reset sau mỗi test nên không có trạng thái tồn dư.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JobPositionService Unit Tests")
class JobPositionServiceTest {

    // -----------------------------------------------------------------------
    // Mock dependencies - tất cả giao tiếp ngoài service đều được mock
    // -----------------------------------------------------------------------

    @Mock
    private JobPositionRepository jobPositionRepository;

    @Mock
    private RecruitmentRequestService recruitmentRequestService;

    @Mock
    private UserClient userService;

    @Mock
    private CandidateClient candidateClient;

    // Service đang được kiểm tra (System Under Test)
    @InjectMocks
    private JobPositionService jobPositionService;

    // -----------------------------------------------------------------------
    // Dữ liệu dùng chung (Fixture)
    // -----------------------------------------------------------------------

    private RecruitmentRequest sampleRR;
    private JobPosition samplePosition;

    @BeforeEach
    void setUp() {
        // Tạo RecruitmentRequest mẫu để tái sử dụng trong nhiều test
        sampleRR = new RecruitmentRequest();
        sampleRR.setId(10L);
        sampleRR.setSalaryMin(new BigDecimal("15000000"));
        sampleRR.setSalaryMax(new BigDecimal("25000000"));
        sampleRR.setDepartmentId(2L);

        // Tạo JobPosition mẫu ở trạng thái DRAFT
        samplePosition = new JobPosition();
        samplePosition.setId(1L);
        samplePosition.setTitle("Software Engineer");
        samplePosition.setStatus(JobPositionStatus.DRAFT);
        samplePosition.setRecruitmentRequest(sampleRR);
    }

    // =======================================================================
    // PHẦN 1: Hàm create()
    // =======================================================================

    // Test Case ID: JOB-TC01
    // Mục tiêu: Nếu DTO có salary, phải lấy từ DTO chứ không lấy từ RecruitmentRequest
    @Test
    @DisplayName("JOB-TC01: create - DTO có salaryMin/Max, phải lưu với salary từ DTO")
    void create_ValidDtoWithSalary_ShouldSaveWithDtoSalaryAndSetDraftStatus() throws IdInvalidException {
        // Chuẩn bị: DTO với salary đầy đủ
        CreateJobPositionDTO dto = new CreateJobPositionDTO();
        dto.setTitle("Dev Java");
        dto.setRecruitmentRequestId(10L);
        dto.setSalaryMin(new BigDecimal("20000000"));
        dto.setSalaryMax(new BigDecimal("30000000"));
        dto.setIsRemote(true);

        // Mock: recruitmentRequestService trả về RR hợp lệ
        when(recruitmentRequestService.findById(10L)).thenReturn(sampleRR);
        // Mock: jobPositionRepository.save() trả về đối tượng đã được lưu
        when(jobPositionRepository.save(any(JobPosition.class))).thenAnswer(inv -> inv.getArgument(0));

        // Thực thi: Gọi hàm cần kiểm tra
        JobPosition result = jobPositionService.create(dto);

        // Kiểm tra: Salary lấy từ DTO (không phải từ RR)
        assertThat(result.getSalaryMin()).isEqualByComparingTo(new BigDecimal("20000000"));
        assertThat(result.getSalaryMax()).isEqualByComparingTo(new BigDecimal("30000000"));
        // Kiểm tra status được set là DRAFT theo đặc tả hệ thống
        assertThat(result.getStatus()).isEqualTo(JobPositionStatus.DRAFT);

        // Minh chứng (CheckDB): Xác nhận repository.save() được gọi đúng 1 lần
        verify(jobPositionRepository, times(1)).save(any(JobPosition.class));
        // Dọn dẹp (Rollback): Không có DB thật, Mockito reset tự động sau khi test kết thúc
    }

    // Test Case ID: JOB-TC02
    // Mục tiêu: Nếu DTO không có salary (null), phải fallback lấy từ RecruitmentRequest
    @Test
    @DisplayName("JOB-TC02: create - DTO không có salary, phải fallback từ RecruitmentRequest")
    void create_DtoWithNullSalary_ShouldFallbackToRecruitmentRequestSalary() throws IdInvalidException {
        // Chuẩn bị: DTO không có salary
        CreateJobPositionDTO dto = new CreateJobPositionDTO();
        dto.setTitle("Product Manager");
        dto.setRecruitmentRequestId(10L);
        dto.setSalaryMin(null);
        dto.setSalaryMax(null);

        when(recruitmentRequestService.findById(10L)).thenReturn(sampleRR);
        when(jobPositionRepository.save(any(JobPosition.class))).thenAnswer(inv -> inv.getArgument(0));

        // Thực thi
        JobPosition result = jobPositionService.create(dto);

        // Kiểm tra: Salary phải được lấy từ sampleRR (15M - 25M)
        assertThat(result.getSalaryMin()).isEqualByComparingTo(new BigDecimal("15000000"));
        assertThat(result.getSalaryMax()).isEqualByComparingTo(new BigDecimal("25000000"));

        // Minh chứng (CheckDB): save() phải được gọi
        verify(jobPositionRepository, times(1)).save(any(JobPosition.class));
    }

    // Test Case ID: JOB-TC03
    // Mục tiêu: Nếu isRemote = null trong DTO, giá trị mặc định phải là false
    @Test
    @DisplayName("JOB-TC03: create - isRemote null trong DTO, phải mặc định là false")
    void create_DtoWithNullIsRemote_ShouldDefaultToFalse() throws IdInvalidException {
        // Chuẩn bị: isRemote không được truyền vào
        CreateJobPositionDTO dto = new CreateJobPositionDTO();
        dto.setTitle("Data Analyst");
        dto.setRecruitmentRequestId(10L);
        dto.setIsRemote(null); // Tình huống cần kiểm tra

        when(recruitmentRequestService.findById(10L)).thenReturn(sampleRR);
        // Dùng ArgumentCaptor để bắt đối tượng được truyền vào save()
        ArgumentCaptor<JobPosition> positionCaptor = ArgumentCaptor.forClass(JobPosition.class);
        when(jobPositionRepository.save(positionCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        // Thực thi
        jobPositionService.create(dto);

        // Kiểm tra: Giá trị isRemote trong đối tượng đã được truyền vào save()
        JobPosition savedPosition = positionCaptor.getValue();
        assertThat(savedPosition.isRemote()).isFalse();

        // Minh chứng (CheckDB): ArgumentCaptor đã xác nhận giá trị chính xác
        verify(jobPositionRepository, times(1)).save(any(JobPosition.class));
    }

    // Test Case ID: JOB-TC04
    // Mục tiêu: Sau khi tạo JobPosition, phải gọi recruitmentRequestService.changeStatus(COMPLETED)
    @Test
    @DisplayName("JOB-TC04: create - Phải gọi changeStatus(COMPLETED) trên RecruitmentRequest")
    void create_ValidDto_ShouldChangeRecruitmentRequestStatusToCompleted() throws IdInvalidException {
        // Chuẩn bị
        CreateJobPositionDTO dto = new CreateJobPositionDTO();
        dto.setTitle("QA Engineer");
        dto.setRecruitmentRequestId(10L);

        when(recruitmentRequestService.findById(10L)).thenReturn(sampleRR);
        when(jobPositionRepository.save(any(JobPosition.class))).thenAnswer(inv -> inv.getArgument(0));

        // Thực thi
        jobPositionService.create(dto);

        // Kiểm tra: Xác nhận changeStatus được gọi với đúng tham số
        verify(recruitmentRequestService, times(1))
                .changeStatus(eq(10L), eq(RecruitmentRequestStatus.COMPLETED));
    }

    // Test Case ID: JOB-TC05
    // Mục tiêu: Nếu recruitmentRequestService ném exception, exception phải lan truyền
    @Test
    @DisplayName("JOB-TC05: create - recruitmentRequestId không tồn tại, phải throw IdInvalidException")
    void create_WithNonExistentRecruitmentRequestId_ShouldThrowIdInvalidException() throws IdInvalidException {
        // Chuẩn bị: recruitmentRequestService throw exception khi findById
        CreateJobPositionDTO dto = new CreateJobPositionDTO();
        dto.setTitle("DevOps Engineer");
        dto.setRecruitmentRequestId(999L);

        when(recruitmentRequestService.findById(999L))
                .thenThrow(new IdInvalidException("Recruitment request khong ton tai"));

        // Thực thi & Kiểm tra: Kiểm tra exception lan truyền lên
        assertThatThrownBy(() -> jobPositionService.create(dto))
                .isInstanceOf(IdInvalidException.class);

        // Minh chứng (CheckDB): save() KHÔNG được gọi khi có exception
        verify(jobPositionRepository, never()).save(any());
    }

    // =======================================================================
    // PHẦN 2: Hàm findById()
    // =======================================================================

    // Test Case ID: JOB-TC06
    // Mục tiêu: Tìm thấy JobPosition theo ID tồn tại
    @Test
    @DisplayName("JOB-TC06: findById - ID tồn tại, phải trả về JobPosition đúng")
    void findById_ExistingId_ShouldReturnJobPosition() throws IdInvalidException {
        // Chuẩn bị
        when(jobPositionRepository.findById(1L)).thenReturn(Optional.of(samplePosition));

        // Thực thi
        JobPosition result = jobPositionService.findById(1L);

        // Kiểm tra: Kết quả phải là đối tượng đúng
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("Software Engineer");
    }

    // Test Case ID: JOB-TC07
    // Mục tiêu: Khi ID không tồn tại, phải throw IdInvalidException
    @Test
    @DisplayName("JOB-TC07: findById - ID không tồn tại, phải throw IdInvalidException")
    void findById_NonExistentId_ShouldThrowIdInvalidException() {
        // Chuẩn bị
        when(jobPositionRepository.findById(999L)).thenReturn(Optional.empty());

        // Thực thi & Kiểm tra: Phải throw IdInvalidException với message phù hợp
        assertThatThrownBy(() -> jobPositionService.findById(999L))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Vị trí tuyển dụng không tồn tại");
    }

    // =======================================================================
    // PHẦN 3: Hàm getByIdWithPublished()
    // =======================================================================

    // Test Case ID: JOB-TC08
    // Mục tiêu: Status = PUBLISHED phải trả về Position
    @Test
    @DisplayName("JOB-TC08: getByIdWithPublished - Status PUBLISHED, phải trả về Position")
    void getByIdWithPublished_PublishedPosition_ShouldReturnPosition() throws IdInvalidException {
        // Chuẩn bị: Position có status = PUBLISHED
        samplePosition.setStatus(JobPositionStatus.PUBLISHED);
        when(jobPositionRepository.findById(1L)).thenReturn(Optional.of(samplePosition));

        // Thực thi
        JobPosition result = jobPositionService.getByIdWithPublished(1L);

        // Kiểm tra: Trả về chính position đó
        assertThat(result.getStatus()).isEqualTo(JobPositionStatus.PUBLISHED);
    }

    // Test Case ID: JOB-TC09
    // Mục tiêu: Status = DRAFT phải throw IdInvalidException (không cho public truy cập)
    @Test
    @DisplayName("JOB-TC09: getByIdWithPublished - Status DRAFT, phải throw IdInvalidException")
    void getByIdWithPublished_DraftPosition_ShouldThrowIdInvalidException() {
        // Chuẩn bị: Position ở trạng thái DRAFT
        samplePosition.setStatus(JobPositionStatus.DRAFT);
        when(jobPositionRepository.findById(1L)).thenReturn(Optional.of(samplePosition));

        // Thực thi & Kiểm tra: Nhánh status != PUBLISHED phải throw
        assertThatThrownBy(() -> jobPositionService.getByIdWithPublished(1L))
                .isInstanceOf(IdInvalidException.class);
    }

    // Test Case ID: JOB-TC10
    // Mục tiêu: Status = CLOSED phải throw IdInvalidException
    @Test
    @DisplayName("JOB-TC10: getByIdWithPublished - Status CLOSED, phải throw IdInvalidException")
    void getByIdWithPublished_ClosedPosition_ShouldThrowIdInvalidException() {
        // Chuẩn bị: Position ở trạng thái CLOSED
        samplePosition.setStatus(JobPositionStatus.CLOSED);
        when(jobPositionRepository.findById(1L)).thenReturn(Optional.of(samplePosition));

        // Thực thi & Kiểm tra
        assertThatThrownBy(() -> jobPositionService.getByIdWithPublished(1L))
                .isInstanceOf(IdInvalidException.class);
    }

    // =======================================================================
    // PHẦN 4: Hàm findAllWithFiltersSimple()
    // =======================================================================

    // Test Case ID: JOB-TC11
    // Mục tiêu: Khi có ids hợp lệ, phải gọi repository.findByIdIn() với đúng list ID
    @Test
    @DisplayName("JOB-TC11: findAllWithFiltersSimple - Có ids hợp lệ, phải gọi findByIdIn")
    void findAllWithFiltersSimple_WithValidIds_ShouldQueryByIdIn() {
        // Chuẩn bị: ids là chuỗi "1,2,3"
        List<JobPosition> mockResult = Arrays.asList(samplePosition);
        when(jobPositionRepository.findByIdIn(Arrays.asList(1L, 2L, 3L))).thenReturn(mockResult);

        // Thực thi
        List<JobPosition> result = jobPositionService.findAllWithFiltersSimple(
                null, null, null, null, "1,2,3");

        // Kiểm tra: Phải trả về kết quả từ findByIdIn
        assertThat(result).hasSize(1);
        // Minh chứng (CheckDB): findByIdIn được gọi với đúng list [1,2,3]
        verify(jobPositionRepository, times(1)).findByIdIn(Arrays.asList(1L, 2L, 3L));
        // Xác nhận findByFilters KHÔNG được gọi khi đã có ids
        verify(jobPositionRepository, never()).findByFilters(any(), any(), any(), any(), any());
    }

    // Test Case ID: JOB-TC12
    // Mục tiêu: Khi ids = null, phải gọi repository.findByFilters()
    @Test
    @DisplayName("JOB-TC12: findAllWithFiltersSimple - ids null, phải gọi findByFilters")
    void findAllWithFiltersSimple_WithNullIds_ShouldQueryByFilters() {
        // Chuẩn bị
        Page<JobPosition> mockPage = new PageImpl<>(Collections.singletonList(samplePosition));
        when(jobPositionRepository.findByFilters(any(), any(), any(), any(), any()))
                .thenReturn(mockPage);

        // Thực thi: ids = null, fallback về findByFilters
        List<JobPosition> result = jobPositionService.findAllWithFiltersSimple(
                null, null, null, null, null);

        // Kiểm tra: Phải trả về kết quả từ findByFilters
        assertThat(result).isNotEmpty();
        // Minh chứng (CheckDB): findByFilters phải được gọi
        verify(jobPositionRepository, times(1)).findByFilters(any(), any(), any(), any(), any());
    }

    // Test Case ID: JOB-TC13
    // Mục tiêu: ids là chuỗi trống, phải fallback về queryByFilters
    @Test
    @DisplayName("JOB-TC13: findAllWithFiltersSimple - ids trắng, phải fallback về findByFilters")
    void findAllWithFiltersSimple_WithBlankIds_ShouldQueryByFilters() {
        // Chuẩn bị: ids chỉ là khoảng trắng
        Page<JobPosition> mockPage = new PageImpl<>(Collections.emptyList());
        when(jobPositionRepository.findByFilters(any(), any(), any(), any(), any()))
                .thenReturn(mockPage);

        // Thực thi
        List<JobPosition> result = jobPositionService.findAllWithFiltersSimple(
                null, null, null, null, "   ");

        // Kiểm tra: Kết quả từ findByFilters (danh sách rỗng)
        assertThat(result).isEmpty();
        // Minh chứng (CheckDB): findByFilters được gọi, findByIdIn không được gọi
        verify(jobPositionRepository, times(1)).findByFilters(any(), any(), any(), any(), any());
        verify(jobPositionRepository, never()).findByIdIn(any());
    }

    // Test Case ID: JOB-TC14
    // Mục tiêu: ids chứa ký tự không hợp lệ, phải fallback về findByFilters (không throw exception)
    @Test
    @DisplayName("JOB-TC14: findAllWithFiltersSimple - ids sai định dạng, không được throw và phải fallback")
    void findAllWithFiltersSimple_WithInvalidIdFormat_ShouldNotThrowAndFallbackToFilters() {
        // Chuẩn bị: ids chứa chữ (không phải số nguyên)
        Page<JobPosition> mockPage = new PageImpl<>(Collections.emptyList());
        when(jobPositionRepository.findByFilters(any(), any(), any(), any(), any()))
                .thenReturn(mockPage);

        // Thực thi & Kiểm tra: Không được throw exception
        List<JobPosition> result = jobPositionService.findAllWithFiltersSimple(
                null, null, null, null, "abc,def");

        // Minh chứng (CheckDB): Sau khi catch exception, phải gọi findByFilters
        verify(jobPositionRepository, times(1)).findByFilters(any(), any(), any(), any(), any());
    }

    // =======================================================================
    // PHẦN 5: Hàm findAllWithFiltersSimplePaged()
    // =======================================================================

    // Test Case ID: JOB-TC15
    // Mục tiêu: Kiểm tra phân trang thủ công khi có ids hợp lệ
    @Test
    @DisplayName("JOB-TC15: findAllWithFiltersSimplePaged - Có ids, kiểm tra phân trang và meta")
    void findAllWithFiltersSimplePaged_WithIds_ShouldReturnCorrectPageAndMeta() {
        // Chuẩn bị: 4 item, page 1, pageSize 2 -> trả về item 0,1
        JobPosition p2 = new JobPosition(); p2.setId(2L);
        JobPosition p3 = new JobPosition(); p3.setId(3L);
        JobPosition p4 = new JobPosition(); p4.setId(4L);
        List<JobPosition> allPositions = Arrays.asList(samplePosition, p2, p3, p4);

        when(jobPositionRepository.findByIdIn(any())).thenReturn(allPositions);
        Pageable pageable = PageRequest.of(0, 2); // page=0 (index), size=2

        // Thực thi
        PaginationDTO result = jobPositionService.findAllWithFiltersSimplePaged(
                null, null, null, null, "1,2,3,4", pageable);

        // Kiểm tra: Nội dung và meta
        assertThat(result.getMeta().getTotal()).isEqualTo(4L);
        assertThat(result.getMeta().getPageSize()).isEqualTo(2);
        // Trang đầu phải có 2 phần tử
        assertThat(((List<?>) result.getResult())).hasSize(2);
    }

    // Test Case ID: JOB-TC16
    // Mục tiêu: ids = null -> gọi findByFilters
    @Test
    @DisplayName("JOB-TC16: findAllWithFiltersSimplePaged - ids null, phải gọi findByFilters")
    void findAllWithFiltersSimplePaged_WithNullIds_ShouldQueryByFilters() {
        // Chuẩn bị
        Pageable pageable = PageRequest.of(0, 10);
        Page<JobPosition> mockPage = new PageImpl<>(Collections.singletonList(samplePosition), pageable, 1);
        when(jobPositionRepository.findByFilters(any(), any(), any(), any(), any())).thenReturn(mockPage);

        // Thực thi
        PaginationDTO result = jobPositionService.findAllWithFiltersSimplePaged(
                null, null, null, null, null, pageable);

        // Minh chứng (CheckDB): findByFilters được gọi
        verify(jobPositionRepository, times(1)).findByFilters(any(), any(), any(), any(), any());
        assertThat(result.getMeta().getTotal()).isEqualTo(1L);
    }

    // Test Case ID: JOB-TC17
    // Mục tiêu: Page vượt quá số lượng item -> content phải rỗng
    @Test
    @DisplayName("JOB-TC17: findAllWithFiltersSimplePaged - Page vượt quá tổng, content phải rỗng")
    void findAllWithFiltersSimplePaged_WithIds_PageBeyondTotal_ShouldReturnEmptyContent() {
        // Chuẩn bị: Chỉ có 2 position, nhưng request page 3 (offset=20)
        when(jobPositionRepository.findByIdIn(any()))
                .thenReturn(Arrays.asList(samplePosition, new JobPosition()));
        Pageable pageable = PageRequest.of(10, 2); // offset = 20, vượt quá 2 item

        // Thực thi
        PaginationDTO result = jobPositionService.findAllWithFiltersSimplePaged(
                null, null, null, null, "1,2", pageable);

        // Kiểm tra: Nội dung rỗng
        assertThat(((List<?>) result.getResult())).isEmpty();
    }

    // =======================================================================
    // PHẦN 6: Hàm update()
    // =======================================================================

    // Test Case ID: JOB-TC18
    // Mục tiêu: Tất cả trường được cập nhật khi DTO đầy đủ 
    @Test
    @DisplayName("JOB-TC18: update - DTO đầy đủ, phải cập nhật tất cả trường")
    void update_AllFieldsProvided_ShouldUpdateAllFields() throws IdInvalidException {
        // Chuẩn bị: DTO có tất cả trường để phủ hết các nhánh if (dto.getXXX() != null)
        UpdateJobPositionDTO dto = new UpdateJobPositionDTO();
        dto.setTitle("Senior Developer");
        dto.setDescription("New description");
        dto.setRequirements("Skills: Java, Spring");
        dto.setBenefits("Insurance, Bonus");
        dto.setSalaryMin(new BigDecimal("30000000"));
        dto.setSalaryMax(new BigDecimal("50000000"));
        dto.setEmploymentType("FULL_TIME");
        dto.setExperienceLevel("SENIOR");
        dto.setLocation("Hanoi");
        dto.setIsRemote(true);
        dto.setQuantity(5);
        dto.setDeadline(LocalDate.of(2026, 12, 31));
        dto.setYearsOfExperience("3-5 years");

        when(jobPositionRepository.findById(1L)).thenReturn(Optional.of(samplePosition));
        when(jobPositionRepository.save(any(JobPosition.class))).thenAnswer(inv -> inv.getArgument(0));

        // Thực thi
        JobPosition result = jobPositionService.update(1L, dto);

        // Kiểm tra: Tất cả trường đã cập nhật chính xác
        assertThat(result.getTitle()).isEqualTo("Senior Developer");
        assertThat(result.getDescription()).isEqualTo("New description");
        assertThat(result.getRequirements()).isEqualTo("Skills: Java, Spring");
        assertThat(result.getBenefits()).isEqualTo("Insurance, Bonus");
        assertThat(result.getSalaryMin()).isEqualByComparingTo(new BigDecimal("30000000"));
        assertThat(result.getSalaryMax()).isEqualByComparingTo(new BigDecimal("50000000"));
        assertThat(result.getEmploymentType()).isEqualTo("FULL_TIME");
        assertThat(result.getExperienceLevel()).isEqualTo("SENIOR");
        assertThat(result.getLocation()).isEqualTo("Hanoi");
        assertThat(result.isRemote()).isTrue();
        assertThat(result.getQuantity()).isEqualTo(5);
        assertThat(result.getDeadline()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(result.getYearsOfExperience()).isEqualTo("3-5 years");

        // Minh chứng (CheckDB): save() được gọi 1 lần
        verify(jobPositionRepository, times(1)).save(any(JobPosition.class));
    }

    // Test Case ID: JOB-TC19
    // Mục tiêu: Chỉ title được cập nhật, các trường khác giữ nguyên
    @Test
    @DisplayName("JOB-TC19: update - Chỉ có title, chỉ title thay đổi")
    void update_OnlyTitleProvided_ShouldUpdateOnlyTitle() throws IdInvalidException {
        // Chuẩn bị: Chỉ set title, các trường khác null
        samplePosition.setDescription("Old description");
        samplePosition.setSalaryMin(new BigDecimal("10000000"));

        UpdateJobPositionDTO dto = new UpdateJobPositionDTO();
        dto.setTitle("New Title Only");
        // Không set các trường khác (null)

        when(jobPositionRepository.findById(1L)).thenReturn(Optional.of(samplePosition));
        when(jobPositionRepository.save(any(JobPosition.class))).thenAnswer(inv -> inv.getArgument(0));

        // Thực thi
        JobPosition result = jobPositionService.update(1L, dto);

        // Kiểm tra: Chỉ có title thay đổi, description và salary giữ nguyên
        assertThat(result.getTitle()).isEqualTo("New Title Only");
        assertThat(result.getDescription()).isEqualTo("Old description"); // giữ nguyên
        assertThat(result.getSalaryMin()).isEqualByComparingTo(new BigDecimal("10000000")); // giữ nguyên
    }

    // Test Case ID: JOB-TC20
    // Mục tiêu: ID không tồn tại -> throw IdInvalidException
    @Test
    @DisplayName("JOB-TC20: update - ID không tồn tại, phải throw IdInvalidException")
    void update_NonExistentId_ShouldThrowIdInvalidException() {
        // Chuẩn bị: Repository không tìm thấy ID
        when(jobPositionRepository.findById(999L)).thenReturn(Optional.empty());

        UpdateJobPositionDTO dto = new UpdateJobPositionDTO();
        dto.setTitle("Anything");

        // Thực thi & Kiểm tra
        assertThatThrownBy(() -> jobPositionService.update(999L, dto))
                .isInstanceOf(IdInvalidException.class);

        // Minh chứng (CheckDB): save() KHÔNG được gọi
        verify(jobPositionRepository, never()).save(any());
    }

    // =======================================================================
    // PHẦN 7: Hàm delete()
    // =======================================================================

    // Test Case ID: JOB-TC21
    // Mục tiêu: Xóa thành công -> gọi repository.delete() và trả về true
    @Test
    @DisplayName("JOB-TC21: delete - ID tồn tại, phải gọi repository.delete() và trả về true")
    void delete_ExistingId_ShouldCallRepositoryDeleteAndReturnTrue() throws IdInvalidException {
        // Chuẩn bị
        when(jobPositionRepository.findById(1L)).thenReturn(Optional.of(samplePosition));
        doNothing().when(jobPositionRepository).delete(samplePosition);

        // Thực thi
        boolean result = jobPositionService.delete(1L);

        // Kiểm tra: Phải trả về true
        assertThat(result).isTrue();
        // Minh chứng (CheckDB): delete() phải được gọi đúng 1 lần
        verify(jobPositionRepository, times(1)).delete(samplePosition);
    }

    // Test Case ID: JOB-TC22
    // Mục tiêu: ID không tồn tại -> throw exception
    @Test
    @DisplayName("JOB-TC22: delete - ID không tồn tại, phải throw IdInvalidException")
    void delete_NonExistentId_ShouldThrowIdInvalidException() {
        // Chuẩn bị
        when(jobPositionRepository.findById(999L)).thenReturn(Optional.empty());

        // Thực thi & Kiểm tra
        assertThatThrownBy(() -> jobPositionService.delete(999L))
                .isInstanceOf(IdInvalidException.class);

        // Minh chứng (CheckDB): delete() KHÔNG được gọi
        verify(jobPositionRepository, never()).delete(any());
    }

    // =======================================================================
    // PHẦN 8: Hàm publish()
    // =======================================================================

    // Test Case ID: JOB-TC23
    // Mục tiêu: Status DRAFT -> cho phép publish, set PUBLISHED và publishedAt
    @Test
    @DisplayName("JOB-TC23: publish - Status DRAFT, phải set PUBLISHED và publishedAt != null")
    void publish_DraftPosition_ShouldSetPublishedStatusAndPublishedAt() throws IdInvalidException {
        // Chuẩn bị: Position ở DRAFT
        samplePosition.setStatus(JobPositionStatus.DRAFT);
        when(jobPositionRepository.findById(1L)).thenReturn(Optional.of(samplePosition));
        when(jobPositionRepository.save(any(JobPosition.class))).thenAnswer(inv -> inv.getArgument(0));

        // Thực thi
        JobPosition result = jobPositionService.publish(1L);

        // Kiểm tra: Status PUBLISHED và publishedAt được set
        assertThat(result.getStatus()).isEqualTo(JobPositionStatus.PUBLISHED);
        assertThat(result.getPublishedAt()).isNotNull();

        // Minh chứng (CheckDB): save() được gọi
        verify(jobPositionRepository, times(1)).save(any(JobPosition.class));
    }

    // Test Case ID: JOB-TC24
    // Mục tiêu: Status PUBLISHED (không phải DRAFT) -> throw exception
    @Test
    @DisplayName("JOB-TC24: publish - Status không phải DRAFT, phải throw IdInvalidException")
    void publish_PublishedPosition_ShouldThrowIdInvalidException() {
        // Chuẩn bị: Position đã PUBLISHED
        samplePosition.setStatus(JobPositionStatus.PUBLISHED);
        when(jobPositionRepository.findById(1L)).thenReturn(Optional.of(samplePosition));

        // Thực thi & Kiểm tra
        assertThatThrownBy(() -> jobPositionService.publish(1L))
                .isInstanceOf(IdInvalidException.class);

        // Minh chứng (CheckDB): save() KHÔNG được gọi
        verify(jobPositionRepository, never()).save(any());
    }

    // =======================================================================
    // PHẦN 9: Hàm close()
    // =======================================================================

    // Test Case ID: JOB-TC25
    // Mục tiêu: Status PUBLISHED -> cho phép close, set CLOSED
    @Test
    @DisplayName("JOB-TC25: close - Status PUBLISHED, phải set CLOSED")
    void close_PublishedPosition_ShouldSetClosedStatus() throws IdInvalidException {
        // Chuẩn bị
        samplePosition.setStatus(JobPositionStatus.PUBLISHED);
        when(jobPositionRepository.findById(1L)).thenReturn(Optional.of(samplePosition));
        when(jobPositionRepository.save(any(JobPosition.class))).thenAnswer(inv -> inv.getArgument(0));

        // Thực thi
        JobPosition result = jobPositionService.close(1L);

        // Kiểm tra: Status phải là CLOSED
        assertThat(result.getStatus()).isEqualTo(JobPositionStatus.CLOSED);
        // Minh chứng (CheckDB): save() được gọi
        verify(jobPositionRepository, times(1)).save(any(JobPosition.class));
    }

    // Test Case ID: JOB-TC26
    // Mục tiêu: Status DRAFT (không phải PUBLISHED) -> throw exception
    @Test
    @DisplayName("JOB-TC26: close - Status DRAFT, phải throw IdInvalidException")
    void close_DraftPosition_ShouldThrowIdInvalidException() {
        // Chuẩn bị: Position ở DRAFT, không thể close
        samplePosition.setStatus(JobPositionStatus.DRAFT);
        when(jobPositionRepository.findById(1L)).thenReturn(Optional.of(samplePosition));

        // Thực thi & Kiểm tra
        assertThatThrownBy(() -> jobPositionService.close(1L))
                .isInstanceOf(IdInvalidException.class);
    }

    // =======================================================================
    // PHẦN 10: Hàm reopen()
    // =======================================================================

    // Test Case ID: JOB-TC27
    // Mục tiêu: Status CLOSED -> cho phép reopen, set PUBLISHED
    @Test
    @DisplayName("JOB-TC27: reopen - Status CLOSED, phải set PUBLISHED")
    void reopen_ClosedPosition_ShouldSetPublishedStatus() throws IdInvalidException {
        // Chuẩn bị
        samplePosition.setStatus(JobPositionStatus.CLOSED);
        when(jobPositionRepository.findById(1L)).thenReturn(Optional.of(samplePosition));
        when(jobPositionRepository.save(any(JobPosition.class))).thenAnswer(inv -> inv.getArgument(0));

        // Thực thi
        JobPosition result = jobPositionService.reopen(1L);

        // Kiểm tra: Status phải là PUBLISHED
        assertThat(result.getStatus()).isEqualTo(JobPositionStatus.PUBLISHED);
        // Minh chứng (CheckDB): save() được gọi
        verify(jobPositionRepository, times(1)).save(any(JobPosition.class));
    }
}
