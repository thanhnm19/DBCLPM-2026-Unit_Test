package com.example.candidate_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.candidate_service.dto.PaginationDTO;
import com.example.candidate_service.dto.candidate.CandidateDetailResponseDTO;
import com.example.candidate_service.dto.candidate.CandidateGetAllResponseDTO;
import com.example.candidate_service.dto.candidate.CandidateStatisticsDTO;
import com.example.candidate_service.dto.candidate.CreateCandidateDTO;
import com.example.candidate_service.dto.candidate.UpdateCandidateDTO;
import com.example.candidate_service.dto.candidate.UploadCVDTO;
import com.example.candidate_service.dto.comment.CommentResponseDTO;
import com.example.candidate_service.dto.review.ReviewCandidateResponseDTO;
import com.example.candidate_service.exception.IdInvalidException;
import com.example.candidate_service.model.Candidate;
import com.example.candidate_service.repository.CandidateRepository;
import com.example.candidate_service.utils.enums.CandidateStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
class CandidateServiceTest {

    @Mock
    private CandidateRepository candidateRepository;

    @Mock
    private JobService jobService;

    @Mock
    private ScheduleService communicationService;

    @Mock
    private CommentService commentService;

    @Mock
    private ReviewCandidateService reviewCandidateService;

    @Mock
    private UserService userService;

    @InjectMocks
    private CandidateService candidateService;

    private ObjectMapper objectMapper;

    private String token;

    private Candidate candidate1;
    private Candidate candidate2;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        token = "test-token";

        candidate1 = buildCandidate(1L, "Alice", "alice@gmail.com", 101L);
        candidate1.setAppliedDate(LocalDate.of(2026, 1, 10));
        candidate1.setStatus(CandidateStatus.SUBMITTED);

        candidate2 = buildCandidate(2L, "Bob", "bob@gmail.com", 102L);
        candidate2.setAppliedDate(LocalDate.of(2026, 1, 12));
        candidate2.setStatus(CandidateStatus.INTERVIEW);
    }

    // =========================
    // CS-TC01 - getAllWithFilters
    // =========================

    @Test
    @DisplayName("CS-TC01: getAllWithFilters - input hợp lệ -> lọc + parse ngày + enrich title/departmentId đúng")
    void CS_TC01_getAllWithFilters_shouldReturnFilteredPagination_andEnrichedFields() {
        // Test kiểm tra: parse start/end date, gọi repository với LocalDate đã parse,
        // và DTO result được enrich jobPositionTitle + departmentId từ job-service.

        Long candidateId = null;
        Long jobPositionId = null;
        CandidateStatus status = null;
        String startDate = "2026-01-01";
        String endDate = "2026-01-31";
        String keyword = "a";
        Long departmentId = 10L;
        Pageable pageable = PageRequest.of(0, 2);

        // departmentId -> job positions cached (1 api call)
        JsonNode jp101 = buildJobPositionJson(101L, "Java Intern", 10L);
        JsonNode jp102 = buildJobPositionJson(102L, "QA Intern", 10L);
        when(jobService.getJobPositionsByDepartmentId(eq(departmentId), eq(token)))
                .thenReturn(Map.of(101L, jp101, 102L, jp102));

        Page<Candidate> page = new PageImpl<>(List.of(candidate1, candidate2), pageable, 2);
        when(candidateRepository.findByFilters(
                eq(jobPositionId), eq(status), eq(candidateId),
                any(LocalDate.class), any(LocalDate.class),
                eq(keyword), anyList(), eq(pageable)))
                .thenReturn(page);

        PaginationDTO result = candidateService.getAllWithFilters(
                candidateId, jobPositionId, status,
                startDate, endDate, keyword, departmentId,
                pageable, token);

        assertThat(result).isNotNull();
        assertThat(result.getMeta()).isNotNull();
        assertThat(result.getMeta().getPage()).isEqualTo(1);
        assertThat(result.getMeta().getPageSize()).isEqualTo(2);
        assertThat(result.getMeta().getPages()).isEqualTo(1);
        assertThat(result.getMeta().getTotal()).isEqualTo(2);

        assertThat(result.getResult()).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<CandidateGetAllResponseDTO> dtos = (List<CandidateGetAllResponseDTO>) result.getResult();

        assertThat(dtos).hasSize(2);
        assertThat(dtos.get(0).getJobPositionTitle()).isEqualTo("Java Intern");
        assertThat(dtos.get(0).getDepartmentId()).isEqualTo(10L);
        assertThat(dtos.get(1).getJobPositionTitle()).isEqualTo("QA Intern");
        assertThat(dtos.get(1).getDepartmentId()).isEqualTo(10L);

        @SuppressWarnings({ "unchecked", "rawtypes" })
        ArgumentCaptor<List<Long>> jobIdsCaptor = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<LocalDate> startCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> endCaptor = ArgumentCaptor.forClass(LocalDate.class);

        verify(candidateRepository, times(1)).findByFilters(
                eq(jobPositionId), eq(status), eq(candidateId),
                startCaptor.capture(), endCaptor.capture(),
                eq(keyword), jobIdsCaptor.capture(), eq(pageable));

        assertThat(startCaptor.getValue()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(endCaptor.getValue()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(jobIdsCaptor.getValue()).containsExactlyInAnyOrder(101L, 102L);

        // Vì dữ liệu job positions đã cache từ departmentId, không cần gọi
        // getJobPositionsByIdsSimple
        verify(jobService, never()).getJobPositionsByIdsSimple(anyList(), any());
    }

    // =========================
    // CS-TC02 - getById
    // =========================

    @Test
    @DisplayName("CS-TC02: getById - id tồn tại -> trả về DTO đúng")
    void CS_TC02_getById_shouldReturnDtoWhenIdExists() throws Exception {
        // Test kiểm tra: repository trả candidate -> map sang
        // CandidateDetailResponseDTO.
        when(candidateRepository.findById(1L)).thenReturn(Optional.of(candidate1));

        CandidateDetailResponseDTO dto = candidateService.getById(1L);

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getEmail()).isEqualTo("alice@gmail.com");
        assertThat(dto.getJobPositionId()).isEqualTo(101L);
    }

    @Test
    @DisplayName("CS-TC02: getById - id không tồn tại -> ném IdInvalidException")
    void CS_TC02_getById_shouldThrowIdInvalidExceptionWhenIdNotExists() {
        // Test kiểm tra: id không tồn tại -> throw đúng custom exception.
        when(candidateRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> candidateService.getById(999L))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("ứng viên không tồn tại");
    }

    // =========================
    // CS-TC03 - create
    // =========================

    @Test
    @DisplayName("CS-TC03: create - input hợp lệ, không trùng email+jobPosition -> tạo mới status SUBMITTED & appliedDate")
    void CS_TC03_create_shouldCreateCandidateWithSubmittedStatus_andAppliedDate() throws Exception {
        // Test kiểm tra: không trùng -> save Candidate với appliedDate=now và
        // status=SUBMITTED.
        CreateCandidateDTO dto = buildCreateCandidateDTO();

        when(candidateRepository.existsByEmailAndJobPositionId(dto.getEmail(), dto.getJobPositionId()))
                .thenReturn(false);
        when(candidateRepository.save(any(Candidate.class))).thenAnswer(inv -> {
            Candidate c = inv.getArgument(0, Candidate.class);
            c.setId(11L);
            return c;
        });

        Candidate saved = candidateService.create(dto);

        assertThat(saved.getId()).isEqualTo(11L);
        assertThat(saved.getEmail()).isEqualTo(dto.getEmail());
        assertThat(saved.getStatus()).isEqualTo(CandidateStatus.SUBMITTED);
        assertThat(saved.getAppliedDate()).isNotNull();
        assertThat(saved.getAppliedDate()).isEqualTo(LocalDate.now());

        verify(candidateRepository).save(any(Candidate.class));
    }

    // =========================
    // CS-TC04 - update
    // =========================

    @Test
    @DisplayName("CS-TC04: update - id tồn tại -> chỉ update field != null và parse dateOfBirth đúng")
    void CS_TC04_update_shouldUpdateOnlyNonNullFields_andParseDateOfBirth() throws Exception {
        // Test kiểm tra: dto chỉ có name/dateOfBirth -> chỉ 2 field này đổi.
        Candidate existing = buildCandidate(5L, "Old", "old@gmail.com", 101L);
        existing.setPhone("000");
        when(candidateRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(candidateRepository.save(any(Candidate.class))).thenAnswer(inv -> inv.getArgument(0, Candidate.class));

        UpdateCandidateDTO dto = new UpdateCandidateDTO();
        dto.setName("New Name");
        dto.setDateOfBirth("2000-02-29");
        dto.setPhone(null); // explicitly null: should keep existing phone

        CandidateDetailResponseDTO updated = candidateService.update(5L, dto);

        assertThat(updated.getId()).isEqualTo(5L);
        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.getDateOfBirth()).isEqualTo(LocalDate.of(2000, 2, 29));
        assertThat(updated.getPhone()).isEqualTo("000");

        ArgumentCaptor<Candidate> candidateCaptor = ArgumentCaptor.forClass(Candidate.class);
        verify(candidateRepository).save(candidateCaptor.capture());
        assertThat(candidateCaptor.getValue().getDateOfBirth()).isEqualTo(LocalDate.of(2000, 2, 29));
    }

    // =========================
    // CS-TC05 - delete
    // =========================

    @Test
    @DisplayName("CS-TC05: delete - id tồn tại -> xóa thành công")
    void CS_TC05_delete_shouldDeleteWhenIdExists() throws Exception {
        // Test kiểm tra: findById có -> delete được gọi.
        when(candidateRepository.findById(1L)).thenReturn(Optional.of(candidate1));

        candidateService.delete(1L);

        verify(candidateRepository).delete(eq(candidate1));
    }

    @Test
    @DisplayName("CS-TC05: delete - id không tồn tại -> ném IdInvalidException")
    void CS_TC05_delete_shouldThrowWhenIdNotExists() {
        // Test kiểm tra: findById empty -> throw IdInvalidException.
        when(candidateRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> candidateService.delete(404L))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("ứng viên không tồn tại");
    }

    // =========================
    // CS-TC06 - createCandidateFromApplication
    // =========================

    @Test
    @DisplayName("CS-TC06: createCandidateFromApplication - input hợp lệ -> tạo mới status SUBMITTED")
    void CS_TC06_createCandidateFromApplication_shouldCreateCandidateWithSubmittedStatus()
            throws IOException, IdInvalidException {
        // Test kiểm tra: không trùng -> status SUBMITTED và appliedDate được gán.
        UploadCVDTO dto = buildUploadCVDTO();
        when(candidateRepository.existsByEmailAndJobPositionId(dto.getEmail(), dto.getJobPositionId()))
                .thenReturn(false);
        when(candidateRepository.save(any(Candidate.class))).thenAnswer(inv -> {
            Candidate c = inv.getArgument(0, Candidate.class);
            c.setId(12L);
            return c;
        });

        Candidate saved = candidateService.createCandidateFromApplication(dto);

        assertThat(saved.getId()).isEqualTo(12L);
        assertThat(saved.getStatus()).isEqualTo(CandidateStatus.SUBMITTED);
        assertThat(saved.getAppliedDate()).isEqualTo(LocalDate.now());
        assertThat(saved.getResumeUrl()).isEqualTo(dto.getCvUrl());
    }

    // =========================
    // CS-TC07 - updateCandidateStatus
    // =========================

    @Test
    @DisplayName("CS-TC07: updateCandidateStatus - status hợp lệ -> cập nhật trạng thái đúng")
    void CS_TC07_updateCandidateStatus_shouldUpdateStatusWhenValid() throws Exception {
        // Test kiểm tra: status=INTERVIEWING -> candidate.status đổi.
        Candidate existing = buildCandidate(7L, "A", "a@x.com", 101L);
        existing.setStatus(CandidateStatus.SUBMITTED);
        when(candidateRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(candidateRepository.save(any(Candidate.class))).thenAnswer(inv -> inv.getArgument(0, Candidate.class));

        CandidateDetailResponseDTO dto = candidateService.updateCandidateStatus(7L, "INTERVIEW", null, 99L);

        assertThat(dto.getStatus()).isEqualTo(CandidateStatus.INTERVIEW);
        verify(candidateRepository).save(any(Candidate.class));
    }

    @Test
    @DisplayName("CS-TC07: updateCandidateStatus - REJECTED -> gán rejectionReason")
    void CS_TC07_updateCandidateStatus_shouldSetRejectionReasonWhenRejected() throws Exception {
        // Test kiểm tra: khi REJECTED + feedback != null -> set rejectionReason.
        Candidate existing = buildCandidate(8L, "A", "a2@x.com", 101L);
        when(candidateRepository.findById(8L)).thenReturn(Optional.of(existing));
        when(candidateRepository.save(any(Candidate.class))).thenAnswer(inv -> inv.getArgument(0, Candidate.class));

        CandidateDetailResponseDTO dto = candidateService.updateCandidateStatus(8L, "REJECTED", "Not fit", 100L);

        assertThat(dto.getStatus()).isEqualTo(CandidateStatus.REJECTED);
        assertThat(dto.getRejectionReason()).isEqualTo("Not fit");
    }

    @Test
    @DisplayName("CS-TC07: updateCandidateStatus - status sai -> ném IllegalArgumentException")
    void CS_TC07_updateCandidateStatus_shouldThrowWhenStatusInvalid() {
        // Test kiểm tra: CandidateStatus.valueOf(status) ném IllegalArgumentException.
        when(candidateRepository.findById(9L)).thenReturn(Optional.of(buildCandidate(9L, "A", "a3@x.com", 101L)));

        assertThatThrownBy(() -> candidateService.updateCandidateStatus(9L, "INVALID_STATUS", null, 1L))
                .isInstanceOf(IllegalArgumentException.class);

        verify(candidateRepository, never()).save(any(Candidate.class));
    }

    // =========================
    // CS-TC08 - getCandidateDetailById
    // =========================

    @Test
    @DisplayName("CS-TC08: getCandidateDetailById - aggregate comments/reviews/jobPosition/schedules; review lỗi -> fallback empty")
    void CS_TC08_getCandidateDetailById_shouldAggregateData_andFallbackEmptyReviewsOnError() throws Exception {
        // Test kiểm tra: comments set, jobPosition set, schedules set; reviews lỗi ->
        // empty list.
        Candidate existing = buildCandidate(20L, "A", "a@a.com", 101L);
        existing.setComments(java.util.Set.of()); // điều kiện để set comments
        when(candidateRepository.findById(20L)).thenReturn(Optional.of(existing));

        List<CommentResponseDTO> comments = List.of(buildComment(1L, 100L, "c1"));
        when(commentService.getByCandidateId(20L, token)).thenReturn(comments);

        when(reviewCandidateService.getByCandidateId(20L, token))
                .thenThrow(new RuntimeException("review-service down"));

        JsonNode jobPosition = buildJobPositionJson(101L, "Java Intern", 10L);
        when(jobService.getJobPositionById(101L, token)).thenReturn(ResponseEntity.ok(jobPosition));

        ArrayNode schedulesArray = objectMapper.createArrayNode();
        schedulesArray.add(objectMapper.createObjectNode().put("id", 1));
        when(communicationService.getUpcomingSchedulesForCandidate(20L, token))
                .thenReturn(ResponseEntity.ok(schedulesArray));

        CandidateDetailResponseDTO dto = candidateService.getCandidateDetailById(20L, token);

        assertThat(dto.getId()).isEqualTo(20L);
        assertThat(dto.getComments()).hasSize(1);
        assertThat(dto.getReviews()).isNotNull();
        assertThat(dto.getReviews()).isEmpty();
        assertThat(dto.getJobPosition()).isNotNull();
        assertThat(dto.getUpcomingSchedules()).hasSize(1);
    }

    // =========================
    // CS-TC09 - getDepartmentIdByCandidateId
    // =========================

    @Test
    @DisplayName("CS-TC09: getDepartmentIdByCandidateId - job service có recruitmentRequest.departmentId -> trả đúng")
    void CS_TC09_getDepartmentIdByCandidateId_shouldReturnDepartmentIdWhenAvailable() {
        // Test kiểm tra: parse response jobPosition.simple ->
        // recruitmentRequest.departmentId.
        Candidate existing = buildCandidate(30L, "A", "x@x.com", 101L);
        when(candidateRepository.findById(30L)).thenReturn(Optional.of(existing));

        JsonNode jobPositionSimple = buildJobPositionJson(101L, "Java Intern", 55L);
        when(jobService.getJobPositionByIdSimple(101L, token))
                .thenReturn(ResponseEntity.ok(jobPositionSimple));

        Long deptId = candidateService.getDepartmentIdByCandidateId(30L, token);

        assertThat(deptId).isEqualTo(55L);
    }

    @Test
    @DisplayName("CS-TC09: getDepartmentIdByCandidateId - lỗi/thiếu data -> trả null")
    void CS_TC09_getDepartmentIdByCandidateId_shouldReturnNullWhenErrorOrMissingData() {
        // Test kiểm tra: job-service ném exception -> service catch và return null.
        Candidate existing = buildCandidate(31L, "A", "y@y.com", 101L);
        when(candidateRepository.findById(31L)).thenReturn(Optional.of(existing));
        when(jobService.getJobPositionByIdSimple(101L, token)).thenThrow(new RuntimeException("boom"));

        Long deptId = candidateService.getDepartmentIdByCandidateId(31L, token);

        assertThat(deptId).isNull();
    }

    // =========================
    // CS-TC10 - getCandidatesByInterviewer
    // =========================

    @Test
    @DisplayName("CS-TC10: getCandidatesByInterviewer - schedule-service trả candidateIds -> lấy candidates & enrich đúng")
    void CS_TC10_getCandidatesByInterviewer_shouldReturnCandidatesAndEnrichFields() {
        // Test kiểm tra: schedule-service -> ids -> repository.findAllById -> enrich
        // title+departmentId.
        when(communicationService.getCandidateIdsByInterviewer(500L, token)).thenReturn(List.of(1L, 2L));
        when(candidateRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(candidate1, candidate2));

        when(jobService.getJobPositionsByIdsSimple(anyList(), eq(token)))
                .thenReturn(Map.of(
                        101L, buildJobPositionJson(101L, "Java Intern", 10L),
                        102L, buildJobPositionJson(102L, "QA Intern", 11L)));

        List<CandidateGetAllResponseDTO> result = candidateService.getCandidatesByInterviewer(500L, token);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(CandidateGetAllResponseDTO::getJobPositionTitle)
                .containsExactlyInAnyOrder("Java Intern", "QA Intern");
        assertThat(result).filteredOn(r -> r.getJobPositionId().equals(101L))
                .first().extracting(CandidateGetAllResponseDTO::getDepartmentId).isEqualTo(10L);
    }

    // =========================
    // CS-TC11 - convertCandidateToEmployee
    // =========================

    @Test
    @DisplayName("CS-TC11: convertCandidateToEmployee - input hợp lệ -> chuyển thành employee thành công")
    void CS_TC11_convertCandidateToEmployee_shouldReturnEmployeeResponseWhenValid() throws Exception {
        // Test kiểm tra: gọi userService.createEmployeeFromCandidate và return body khi
        // 2xx.
        Candidate existing = buildCandidate(40L, "A", "a@b.com", 101L);
        existing.setDateOfBirth(LocalDate.of(2000, 1, 1));
        when(candidateRepository.findById(40L)).thenReturn(Optional.of(existing));

        ObjectNode employeeNode = objectMapper.createObjectNode().put("employeeId", 9000);
        when(userService.createEmployeeFromCandidate(
                eq(40L), eq("A"), eq("a@b.com"), eq(existing.getPhone()), eq("2000-01-01"),
                any(), any(), any(), any(), any(),
                eq(1L), eq(2L), eq("PROBATION"), eq(token)))
                .thenReturn(new ResponseEntity<>(employeeNode, HttpStatus.OK));

        JsonNode result = candidateService.convertCandidateToEmployee(40L, 1L, 2L, token);

        assertThat(result.get("employeeId").asInt()).isEqualTo(9000);
    }

    @Test
    @DisplayName("CS-TC11: convertCandidateToEmployee - thiếu departmentId/positionId -> ném IdInvalidException")
    void CS_TC11_convertCandidateToEmployee_shouldThrowWhenDepartmentIdOrPositionIdMissing() {
        // Test kiểm tra: departmentId null và jobService không cung cấp được
        // departmentId -> throw.
        Candidate existing = buildCandidate(41L, "A", "c@d.com", null);
        when(candidateRepository.findById(41L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> candidateService.convertCandidateToEmployee(41L, null, null, token))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Department ID là bắt buộc");

        verify(userService, never()).createEmployeeFromCandidate(
                anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                anyLong(), anyLong(), any(), any());
    }

    // =========================
    // CS-TC12 - getCandidatesForStatistics
    // =========================

    @Test
    @DisplayName("CS-TC12: getCandidatesForStatistics - input hợp lệ -> parse ngày đúng, filter theo departmentId đúng")
    void CS_TC12_getCandidatesForStatistics_shouldParseDates_andFilterByDepartmentId() {
        // Test kiểm tra: repository receive LocalDate parsed; kết quả bị filter theo
        // departmentId.
        Pageable pageable = PageRequest.of(0, 10000);
        Page<Candidate> page = new PageImpl<>(List.of(candidate1, candidate2), pageable, 2);
        when(candidateRepository.findByFilters(
                eq(null), eq(CandidateStatus.SUBMITTED), eq(null),
                any(LocalDate.class), any(LocalDate.class),
                eq(null), eq(null), any(Pageable.class)))
                .thenReturn(page);

        when(jobService.getJobPositionsByIds(anyList(), eq(token)))
                .thenReturn(Map.of(
                        101L, buildJobPositionJson(101L, "Java Intern", 10L),
                        102L, buildJobPositionJson(102L, "QA Intern", 99L)));

        List<CandidateStatisticsDTO> stats = candidateService.getCandidatesForStatistics(
                CandidateStatus.SUBMITTED,
                "2026-01-01", "2026-01-31",
                null,
                10L,
                token);

        assertThat(stats).hasSize(1);
        assertThat(stats.get(0).getJobPositionId()).isEqualTo(101L);
        assertThat(stats.get(0).getDepartmentId()).isEqualTo(10L);

        ArgumentCaptor<LocalDate> startCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> endCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(candidateRepository).findByFilters(
                eq(null), eq(CandidateStatus.SUBMITTED), eq(null),
                startCaptor.capture(), endCaptor.capture(),
                eq(null), eq(null), any(Pageable.class));
        assertThat(startCaptor.getValue()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(endCaptor.getValue()).isEqualTo(LocalDate.of(2026, 1, 31));
    }

    // =========================
    // CS-TC13 - existsByEmail
    // =========================

    @Test
    @DisplayName("CS-TC13: existsByEmail - email tồn tại -> true")
    void CS_TC13_existsByEmail_shouldReturnTrueWhenExists() {
        // Test kiểm tra: pass-through từ repository.
        when(candidateRepository.existsByEmail("a@a.com")).thenReturn(true);
        assertThat(candidateService.existsByEmail("a@a.com")).isTrue();
    }

    @Test
    @DisplayName("CS-TC13: existsByEmail - email không tồn tại -> false")
    void CS_TC13_existsByEmail_shouldReturnFalseWhenNotExists() {
        // Test kiểm tra: pass-through từ repository.
        when(candidateRepository.existsByEmail("b@b.com")).thenReturn(false);
        assertThat(candidateService.existsByEmail("b@b.com")).isFalse();
    }

    // =========================
    // CS-TC14 - findByEmail
    // =========================

    @Test
    @DisplayName("CS-TC14: findByEmail - email tồn tại -> trả Candidate")
    void CS_TC14_findByEmail_shouldReturnCandidateWhenExists() throws Exception {
        // Test kiểm tra: repository.findByEmail -> return candidate.
        when(candidateRepository.findByEmail("alice@gmail.com")).thenReturn(Optional.of(candidate1));

        Candidate found = candidateService.findByEmail("alice@gmail.com");

        assertThat(found.getId()).isEqualTo(1L);
        assertThat(found.getEmail()).isEqualTo("alice@gmail.com");
    }

    @Test
    @DisplayName("CS-TC14: findByEmail - email không tồn tại -> ném IdInvalidException")
    void CS_TC14_findByEmail_shouldThrowWhenNotExists() {
        // Test kiểm tra: Optional.empty -> throw IdInvalidException.
        when(candidateRepository.findByEmail("missing@gmail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> candidateService.findByEmail("missing@gmail.com"))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("ứng viên không tồn tại");
    }

    // =========================
    // CS-TC15 - saveCandidate
    // =========================

    @Test
    @DisplayName("CS-TC15: saveCandidate - candidate hợp lệ -> lưu thành công")
    void CS_TC15_saveCandidate_shouldSaveSuccessfully() {
        // Test kiểm tra: repository.save được gọi và trả về entity.
        Candidate c = buildCandidate(null, "X", "x@x.com", 101L);
        when(candidateRepository.save(any(Candidate.class))).thenAnswer(inv -> {
            Candidate arg = inv.getArgument(0, Candidate.class);
            arg.setId(100L);
            return arg;
        });

        Candidate saved = candidateService.saveCandidate(c);

        assertThat(saved.getId()).isEqualTo(100L);
        verify(candidateRepository).save(eq(c));
    }

    // =========================
    // CS-TC16 - countCandidatesByJobPositionId
    // =========================

    @Test
    @DisplayName("CS-TC16: countCandidatesByJobPositionId - jobPositionId hợp lệ -> trả đúng số lượng")
    void CS_TC16_countCandidatesByJobPositionId_shouldReturnCount() {
        // Test kiểm tra: pass-through.countByJobPositionId.
        when(candidateRepository.countByJobPositionId(101L)).thenReturn(5L);
        assertThat(candidateService.countCandidatesByJobPositionId(101L)).isEqualTo(5L);
    }

    // =========================
    // CS-TC17 - getByCandidateId
    // =========================

    @Test
    @DisplayName("CS-TC17: getByCandidateId (assumption) - hiện CandidateService không có hàm này -> cover bằng getCandidateDetailById")
    void CS_TC17_getByCandidateId_assumption_shouldBehaveLikeGetCandidateDetailById() throws Exception {
        // Assumption ngắn: Source CandidateService hiện tại không có method
        // getByCandidateId(Long,String)
        // như testcase mô tả. Trên codebase, hành vi gần nhất là
        // getCandidateDetailById(id, token)
        // để aggregate dữ liệu và join tên employee ở các sub-service (comment/review).
        // Test này giữ mapping CS-TC17 và verify được candidate không tồn tại -> throw.

        when(candidateRepository.findById(9999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> candidateService.getCandidateDetailById(9999L, token))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Ứng viên không tồn tại");
    }

    // ============== helpers ==============

    private Candidate buildCandidate(Long id, String name, String email, Long jobPositionId) {
        Candidate c = new Candidate();
        c.setId(id);
        c.setName(name);
        c.setEmail(email);
        c.setPhone("0123456789");
        c.setJobPositionId(jobPositionId);
        c.setResumeUrl("http://cv" + (id != null ? id : "") + ".pdf");
        return c;
    }

    private CreateCandidateDTO buildCreateCandidateDTO() {
        CreateCandidateDTO dto = new CreateCandidateDTO();
        dto.setName("New Candidate");
        dto.setEmail("new@gmail.com");
        dto.setPhone("0999999999");
        dto.setJobPositionId(101L);
        dto.setCvUrl("http://cv-new.pdf");
        dto.setNotes("note");
        dto.setCreatedBy(1L);
        return dto;
    }

    private UploadCVDTO buildUploadCVDTO() {
        UploadCVDTO dto = new UploadCVDTO();
        dto.setName("App Candidate");
        dto.setEmail("app@gmail.com");
        dto.setPhone("0888888888");
        dto.setJobPositionId(101L);
        dto.setCvUrl("http://cv-app.pdf");
        dto.setNotes("notes");
        return dto;
    }

    private JsonNode buildJobPositionJson(Long id, String title, Long departmentId) {
        ObjectNode jp = objectMapper.createObjectNode();
        jp.put("id", id);
        jp.put("title", title);
        ObjectNode rr = objectMapper.createObjectNode();
        if (departmentId != null) {
            rr.put("departmentId", departmentId);
        }
        jp.set("recruitmentRequest", rr);
        return jp;
    }

    private CommentResponseDTO buildComment(Long id, Long employeeId, String content) {
        CommentResponseDTO dto = new CommentResponseDTO();
        dto.setId(id);
        dto.setEmployeeId(employeeId);
        dto.setContent(content);
        return dto;
    }

    @SuppressWarnings("unused")
    private ReviewCandidateResponseDTO buildReview(Long id, Long reviewerId) {
        ReviewCandidateResponseDTO dto = new ReviewCandidateResponseDTO();
        dto.setId(id);
        dto.setReviewerId(reviewerId);
        return dto;
    }
}
