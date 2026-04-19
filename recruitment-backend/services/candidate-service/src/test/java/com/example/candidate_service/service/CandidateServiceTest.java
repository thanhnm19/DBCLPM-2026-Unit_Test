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
    // 1. CandidateService.findByEmail
    // =========================

    @Test
    @DisplayName("CS-TC-001: findByEmail - tìm đúng ứng viên khi email tồn tại")
    void testFindByEmail_CS_TC_001() throws Exception {
        // Testcase ID: CS-TC-001
        // Objective: Xác nhận tìm đúng ứng viên khi email tồn tại

        // arrange
        when(candidateRepository.findByEmail("a@gmail.com"))
                .thenReturn(Optional.of(buildCandidate(10L, "A", "a@gmail.com", 101L)));

        // act
        Candidate found = candidateService.findByEmail("a@gmail.com");

        // assert
        assertThat(found).isNotNull();
        assertThat(found.getEmail()).isEqualTo("a@gmail.com");
        verify(candidateRepository, times(1)).findByEmail("a@gmail.com");
    }

    @Test
    @DisplayName("CS-TC-002: findByEmail - email không tồn tại ném IdInvalidException")
    void testFindByEmail_NotFound_CS_TC_002() {
        // Testcase ID: CS-TC-002
        // Objective: Xác nhận ném lỗi khi email không tồn tại

        // arrange
        when(candidateRepository.findByEmail("missing@gmail.com")).thenReturn(Optional.empty());

        // act + assert
        assertThatThrownBy(() -> candidateService.findByEmail("missing@gmail.com"))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("ứng viên không tồn tại");

        verify(candidateRepository, times(1)).findByEmail("missing@gmail.com");
    }

    // =========================
    // 2. CandidateService.getAllWithFilters
    // =========================

    @Test
    @DisplayName("CS-TC-003: getAllWithFilters - lọc danh sách và enrich đúng jobPositionTitle, departmentId")
    void testGetAllWithFilters_EnrichJobPosition_CS_TC_003() {
        // Testcase ID: CS-TC-003
        // Objective: Lọc danh sách và enrich đúng thông tin job position

        // arrange
        Long candidateId = null;
        Long jobPositionId = null;
        CandidateStatus status = null;
        String startDate = "2026-01-01";
        String endDate = "2026-01-31";
        String keyword = "a";
        Long departmentId = 10L;
        Pageable pageable = PageRequest.of(0, 2);

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

        // act
        PaginationDTO result = candidateService.getAllWithFilters(
                candidateId, jobPositionId, status,
                startDate, endDate, keyword, departmentId,
                pageable, token);

        // assert
        assertThat(result).isNotNull();
        assertThat(result.getMeta()).isNotNull();
        assertThat(result.getMeta().getPage()).isEqualTo(1);
        assertThat(result.getMeta().getPageSize()).isEqualTo(2);
        assertThat(result.getMeta().getPages()).isEqualTo(1);
        assertThat(result.getMeta().getTotal()).isEqualTo(2);

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

        verify(jobService, never()).getJobPositionsByIdsSimple(anyList(), any());
    }

    @Test
    @DisplayName("CS-TC-004: getAllWithFilters - department không có jobPosition nào -> trả rỗng")
    void testGetAllWithFilters_EmptyDepartmentJobPositions_CS_TC_004() {
        // Testcase ID: CS-TC-004
        // Objective: Xác nhận trả danh sách rỗng khi department không có jobPosition
        // nào

        // arrange
        Long departmentId = 99L;
        Pageable pageable = PageRequest.of(0, 5);
        when(jobService.getJobPositionsByDepartmentId(eq(departmentId), eq(token)))
                .thenReturn(Map.of());

        // act
        PaginationDTO result = candidateService.getAllWithFilters(
                null, null, null,
                "2026-01-01", "2026-01-31", null,
                departmentId,
                pageable,
                token);

        // assert
        assertThat(result).isNotNull();
        assertThat(result.getMeta()).isNotNull();
        assertThat(result.getMeta().getPage()).isEqualTo(1);
        assertThat(result.getMeta().getPageSize()).isEqualTo(5);
        assertThat(result.getMeta().getTotal()).isEqualTo(0);
        assertThat(result.getMeta().getPages()).isEqualTo(0);
        assertThat(result.getResult()).isInstanceOf(List.class);
        assertThat((List<?>) result.getResult()).isEmpty();

        verify(candidateRepository, never()).findByFilters(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("CS-TC-005: getAllWithFilters - parse ngày lỗi -> không crash và vẫn trả dữ liệu")
    void testGetAllWithFilters_InvalidDateFormat_CS_TC_005() {
        // Testcase ID: CS-TC-005
        // Objective: Xác nhận hàm không crash khi parse ngày lỗi

        // arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<Candidate> page = new PageImpl<>(List.of(candidate1), pageable, 1);

        when(candidateRepository.findByFilters(
                eq(null), eq(null), eq(null),
                eq(null), eq(null),
                eq(null), eq(null), eq(pageable)))
                .thenReturn(page);

        // act
        PaginationDTO result = candidateService.getAllWithFilters(
                null, null, null,
                "2026/01/01", "abc", null,
                null,
                pageable,
                token);

        // assert
        assertThat(result).isNotNull();
        assertThat(result.getMeta().getTotal()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<CandidateGetAllResponseDTO> dtos = (List<CandidateGetAllResponseDTO>) result.getResult();
        assertThat(dtos).hasSize(1);

        verify(candidateRepository, times(1)).findByFilters(
                eq(null), eq(null), eq(null),
                eq(null), eq(null),
                eq(null), eq(null), eq(pageable));
    }

    // =========================
    // 3. CandidateService.getById
    // =========================

    @Test
    @DisplayName("CS-TC-006: getById - lấy chi tiết ứng viên thành công")
    void testGetById_Success_CS_TC_006() throws Exception {
        // Testcase ID: CS-TC-006
        // Objective: Lấy chi tiết ứng viên thành công

        // arrange
        when(candidateRepository.findById(1L)).thenReturn(Optional.of(candidate1));

        // act
        CandidateDetailResponseDTO dto = candidateService.getById(1L);

        // assert
        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getEmail()).isEqualTo("alice@gmail.com");
        assertThat(dto.getJobPositionId()).isEqualTo(101L);
        verify(candidateRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("CS-TC-007: getById - ID không tồn tại -> ném IdInvalidException")
    void testGetById_NotFound_CS_TC_007() {
        // Testcase ID: CS-TC-007
        // Objective: Xác nhận lỗi khi ID không tồn tại

        // arrange
        when(candidateRepository.findById(999L)).thenReturn(Optional.empty());

        // act + assert
        assertThatThrownBy(() -> candidateService.getById(999L))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("ứng viên không tồn tại");
        verify(candidateRepository, times(1)).findById(999L);
    }

    // =========================
    // 4. CandidateService.create
    // =========================

    @Test
    @DisplayName("CS-TC-008: create - tạo mới ứng viên với giá trị mặc định đúng")
    void testCreate_DefaultValues_CS_TC_008() throws Exception {
        // Testcase ID: CS-TC-008
        // Objective: Tạo mới ứng viên với giá trị mặc định đúng

        // arrange
        CreateCandidateDTO dto = buildCreateCandidateDTO();
        when(candidateRepository.existsByEmailAndJobPositionId(dto.getEmail(), dto.getJobPositionId()))
                .thenReturn(false);
        when(candidateRepository.save(any(Candidate.class))).thenAnswer(inv -> {
            Candidate c = inv.getArgument(0, Candidate.class);
            c.setId(11L);
            return c;
        });

        // act
        Candidate saved = candidateService.create(dto);

        // assert
        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isEqualTo(11L);
        assertThat(saved.getEmail()).isEqualTo(dto.getEmail());
        assertThat(saved.getStatus()).isEqualTo(CandidateStatus.SUBMITTED);
        assertThat(saved.getAppliedDate()).isEqualTo(LocalDate.now());
        assertThat(saved.getCreatedBy()).isEqualTo(dto.getCreatedBy());

        ArgumentCaptor<Candidate> captor = ArgumentCaptor.forClass(Candidate.class);
        verify(candidateRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(CandidateStatus.SUBMITTED);
    }

    @Test
    @DisplayName("CS-TC-009: create - chặn tạo trùng theo email + jobPosition")
    void testCreate_DuplicateEmailAndJobPosition_CS_TC_009() {
        // Testcase ID: CS-TC-009
        // Objective: Chặn tạo trùng ứng viên theo email + jobPosition

        // arrange
        CreateCandidateDTO dto = buildCreateCandidateDTO();
        when(candidateRepository.existsByEmailAndJobPositionId(dto.getEmail(), dto.getJobPositionId()))
                .thenReturn(true);

        // act + assert
        assertThatThrownBy(() -> candidateService.create(dto))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Ứng viên đã nộp hồ sơ cho vị trí này");

        verify(candidateRepository, never()).save(any(Candidate.class));
    }

    // =========================
    // 5. CandidateService.update
    // =========================

    @Test
    @DisplayName("CS-TC-010: update - cập nhật chọn lọc field được truyền, field null giữ nguyên")
    void testUpdate_PartialFields_CS_TC_010() throws Exception {
        // Testcase ID: CS-TC-010
        // Objective: Cập nhật chọn lọc các field được truyền

        // arrange
        Candidate existing = buildCandidate(5L, "Old", "old@gmail.com", 101L);
        existing.setPhone("000");
        when(candidateRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(candidateRepository.save(any(Candidate.class))).thenAnswer(inv -> inv.getArgument(0, Candidate.class));

        UpdateCandidateDTO dto = new UpdateCandidateDTO();
        dto.setName("New Name");
        dto.setDateOfBirth("2000-02-29");
        dto.setPhone(null);

        // act
        CandidateDetailResponseDTO updated = candidateService.update(5L, dto);

        // assert
        assertThat(updated.getId()).isEqualTo(5L);
        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.getDateOfBirth()).isEqualTo(LocalDate.of(2000, 2, 29));
        assertThat(updated.getPhone()).isEqualTo("000");

        ArgumentCaptor<Candidate> candidateCaptor = ArgumentCaptor.forClass(Candidate.class);
        verify(candidateRepository, times(1)).save(candidateCaptor.capture());
        assertThat(candidateCaptor.getValue().getDateOfBirth()).isEqualTo(LocalDate.of(2000, 2, 29));
    }

    @Test
    @DisplayName("CS-TC-011: update - ứng viên không tồn tại -> ném IdInvalidException")
    void testUpdate_NotFound_CS_TC_011() {
        // Testcase ID: CS-TC-011
        // Objective: Xác nhận lỗi khi cập nhật ứng viên không tồn tại

        // arrange
        when(candidateRepository.findById(999L)).thenReturn(Optional.empty());
        UpdateCandidateDTO dto = new UpdateCandidateDTO();
        dto.setName("X");

        // act + assert
        assertThatThrownBy(() -> candidateService.update(999L, dto))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("ứng viên không tồn tại");

        verify(candidateRepository, never()).save(any(Candidate.class));
    }

    // =========================
    // 6. CandidateService.delete
    // =========================

    @Test
    @DisplayName("CS-TC-012: delete - xóa ứng viên thành công khi tồn tại")
    void testDelete_Success_CS_TC_012() throws Exception {
        // Testcase ID: CS-TC-012
        // Objective: Xóa ứng viên thành công khi tồn tại

        // arrange
        when(candidateRepository.findById(1L)).thenReturn(Optional.of(candidate1));

        // act
        candidateService.delete(1L);

        // assert
        verify(candidateRepository, times(1)).delete(eq(candidate1));
    }

    @Test
    @DisplayName("CS-TC-013: delete - ID không tồn tại -> ném IdInvalidException")
    void testDelete_NotFound_CS_TC_013() {
        // Testcase ID: CS-TC-013
        // Objective: Xác nhận lỗi khi xóa ID không tồn tại

        // arrange
        when(candidateRepository.findById(999L)).thenReturn(Optional.empty());

        // act + assert
        assertThatThrownBy(() -> candidateService.delete(999L))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("ứng viên không tồn tại");

        verify(candidateRepository, never()).delete(any(Candidate.class));
    }

    // =========================
    // 7. CandidateService.getByIds
    // =========================

    @Test
    @DisplayName("CS-TC-014: getByIds - convert đúng danh sách entity sang DTO")
    void testGetByIds_ConvertToDto_CS_TC_014() {
        // Testcase ID: CS-TC-014
        // Objective: Xác nhận convert đúng danh sách entity sang DTO

        // arrange
        when(candidateRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(candidate1, candidate2));

        // act
        List<CandidateDetailResponseDTO> result = candidateService.getByIds(List.of(1L, 2L));

        // assert
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result).extracting(CandidateDetailResponseDTO::getId)
                .containsExactlyInAnyOrder(1L, 2L);
        verify(candidateRepository, times(1)).findAllById(List.of(1L, 2L));
    }

    // =========================
    // 8. CandidateService.createCandidateFromApplication
    // =========================

    @Test
    @DisplayName("CS-TC-015: createCandidateFromApplication - tạo ứng viên từ CV upload thành công")
    void testCreateCandidateFromApplication_Success_CS_TC_015() throws IOException, IdInvalidException {
        // Testcase ID: CS-TC-015
        // Objective: Tạo ứng viên từ CV upload thành công

        // arrange
        UploadCVDTO dto = buildUploadCVDTO();
        when(candidateRepository.existsByEmailAndJobPositionId(dto.getEmail(), dto.getJobPositionId()))
                .thenReturn(false);
        when(candidateRepository.save(any(Candidate.class))).thenAnswer(inv -> {
            Candidate c = inv.getArgument(0, Candidate.class);
            c.setId(12L);
            return c;
        });

        // act
        Candidate saved = candidateService.createCandidateFromApplication(dto);

        // assert
        assertThat(saved.getId()).isEqualTo(12L);
        assertThat(saved.getStatus()).isEqualTo(CandidateStatus.SUBMITTED);
        assertThat(saved.getAppliedDate()).isEqualTo(LocalDate.now());
        assertThat(saved.getResumeUrl()).isEqualTo(dto.getCvUrl());
        assertThat(saved.getNotes()).isEqualTo(dto.getNotes());
    }

    @Test
    @DisplayName("CS-TC-016: createCandidateFromApplication - chặn tạo trùng ứng viên")
    void testCreateCandidateFromApplication_Duplicate_CS_TC_016() {
        // Testcase ID: CS-TC-016
        // Objective: Chặn tạo trùng từ application

        // arrange
        UploadCVDTO dto = buildUploadCVDTO();
        when(candidateRepository.existsByEmailAndJobPositionId(dto.getEmail(), dto.getJobPositionId()))
                .thenReturn(true);

        // act + assert
        assertThatThrownBy(() -> candidateService.createCandidateFromApplication(dto))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Ứng viên đã nộp hồ sơ cho vị trí này");

        verify(candidateRepository, never()).save(any(Candidate.class));
    }

    // =========================
    // 9. CandidateService.updateCandidateStatus
    // =========================

    @Test
    @DisplayName("CS-TC-017: updateCandidateStatus - REJECTED -> set rejectionReason và updatedBy")
    void testUpdateCandidateStatus_Rejected_SetReason_CS_TC_017() throws Exception {
        // Testcase ID: CS-TC-017
        // Objective: Xác nhận set trạng thái từ chối và lưu rejectionReason

        // arrange
        Candidate existing = buildCandidate(8L, "A", "a2@x.com", 101L);
        when(candidateRepository.findById(8L)).thenReturn(Optional.of(existing));
        when(candidateRepository.save(any(Candidate.class))).thenAnswer(inv -> inv.getArgument(0, Candidate.class));

        // act
        CandidateDetailResponseDTO dto = candidateService.updateCandidateStatus(8L, "REJECTED", "Không phù hợp", 100L);

        // assert
        assertThat(dto.getStatus()).isEqualTo(CandidateStatus.REJECTED);
        assertThat(dto.getRejectionReason()).isEqualTo("Không phù hợp");

        ArgumentCaptor<Candidate> candCaptor = ArgumentCaptor.forClass(Candidate.class);
        verify(candidateRepository, times(1)).save(candCaptor.capture());
        assertThat(candCaptor.getValue().getUpdatedBy()).isEqualTo(100L);
        assertThat(candCaptor.getValue().getRejectionReason()).isEqualTo("Không phù hợp");
    }

    @Test
    @DisplayName("CS-TC-018: updateCandidateStatus - status không nằm trong enum -> ném IllegalArgumentException")
    void testUpdateCandidateStatus_InvalidEnum_CS_TC_018() {
        // Testcase ID: CS-TC-018
        // Objective: Xác nhận lỗi khi status không nằm trong enum

        // arrange
        when(candidateRepository.findById(9L)).thenReturn(Optional.of(buildCandidate(9L, "A", "a3@x.com", 101L)));

        // act + assert
        assertThatThrownBy(() -> candidateService.updateCandidateStatus(9L, "INVALID_STATUS", null, 1L))
                .isInstanceOf(IllegalArgumentException.class);

        verify(candidateRepository, never()).save(any(Candidate.class));
    }

    // =========================
    // 10. CandidateService.getCandidateDetailById
    // =========================

    @Test
    @DisplayName("CS-TC-019: getCandidateDetailById - aggregate đủ comments, reviews, jobPosition, upcomingSchedules")
    void testGetCandidateDetailById_AggregateAll_CS_TC_019() throws Exception {
        // Testcase ID: CS-TC-019
        // Objective: Xác nhận aggregate đủ comments, reviews, jobPosition,
        // upcomingSchedules

        // arrange
        Candidate existing = buildCandidate(20L, "A", "a@a.com", 101L);
        existing.setComments(java.util.Set.of());
        when(candidateRepository.findById(20L)).thenReturn(Optional.of(existing));

        List<CommentResponseDTO> comments = List.of(buildComment(1L, 100L, "c1"));
        when(commentService.getByCandidateId(20L, token)).thenReturn(comments);

        List<ReviewCandidateResponseDTO> reviews = List.of(buildReview(1L, 200L));
        when(reviewCandidateService.getByCandidateId(20L, token)).thenReturn(reviews);

        JsonNode jobPosition = buildJobPositionJson(101L, "Java Intern", 10L);
        when(jobService.getJobPositionById(101L, token)).thenReturn(ResponseEntity.ok(jobPosition));

        ArrayNode schedulesArray = objectMapper.createArrayNode();
        schedulesArray.add(objectMapper.createObjectNode().put("id", 1));
        when(communicationService.getUpcomingSchedulesForCandidate(20L, token))
                .thenReturn(ResponseEntity.ok(schedulesArray));

        // act
        CandidateDetailResponseDTO dto = candidateService.getCandidateDetailById(20L, token);

        // assert
        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(20L);
        assertThat(dto.getComments()).hasSize(1);
        assertThat(dto.getReviews()).hasSize(1);
        assertThat(dto.getJobPosition()).isNotNull();
        assertThat(dto.getUpcomingSchedules()).hasSize(1);

        verify(commentService, times(1)).getByCandidateId(20L, token);
        verify(reviewCandidateService, times(1)).getByCandidateId(20L, token);
        verify(jobService, times(1)).getJobPositionById(101L, token);
        verify(communicationService, times(1)).getUpcomingSchedulesForCandidate(20L, token);
    }

    @Test
    @DisplayName("CS-TC-020: getCandidateDetailById - review-service lỗi -> fallback reviews rỗng")
    void testGetCandidateDetailById_FallbackEmptyReviews_CS_TC_020() throws Exception {
        // Testcase ID: CS-TC-020
        // Objective: Xác nhận fallback reviews rỗng khi review-service lỗi

        // arrange
        Candidate existing = buildCandidate(21L, "A", "a2@a.com", 101L);
        existing.setComments(java.util.Set.of());
        when(candidateRepository.findById(21L)).thenReturn(Optional.of(existing));

        when(reviewCandidateService.getByCandidateId(21L, token)).thenThrow(new RuntimeException("down"));

        JsonNode jobPosition = buildJobPositionJson(101L, "Java Intern", 10L);
        when(jobService.getJobPositionById(101L, token)).thenReturn(ResponseEntity.ok(jobPosition));

        ArrayNode schedulesArray = objectMapper.createArrayNode();
        schedulesArray.add(objectMapper.createObjectNode().put("id", 1));
        when(communicationService.getUpcomingSchedulesForCandidate(21L, token))
                .thenReturn(ResponseEntity.ok(schedulesArray));

        // act
        CandidateDetailResponseDTO dto = candidateService.getCandidateDetailById(21L, token);

        // assert
        assertThat(dto).isNotNull();
        assertThat(dto.getReviews()).isNotNull();
        assertThat(dto.getReviews()).isEmpty();
        verify(reviewCandidateService, times(1)).getByCandidateId(21L, token);
    }

    // =========================
    // 11. CandidateService.getDepartmentIdByCandidateId
    // =========================

    @Test
    @DisplayName("CS-TC-021: getDepartmentIdByCandidateId - parse đúng departmentId từ JSON lồng")
    void testGetDepartmentIdByCandidateId_ParseNestedDepartmentId_CS_TC_021() {
        // Testcase ID: CS-TC-021
        // Objective: Xác nhận parse đúng departmentId từ JSON lồng

        // arrange
        Candidate existing = buildCandidate(30L, "A", "x@x.com", 101L);
        when(candidateRepository.findById(30L)).thenReturn(Optional.of(existing));

        JsonNode jobPositionSimple = buildJobPositionJson(101L, "Java Intern", 55L);
        when(jobService.getJobPositionByIdSimple(101L, token))
                .thenReturn(ResponseEntity.ok(jobPositionSimple));

        // act
        Long deptId = candidateService.getDepartmentIdByCandidateId(30L, token);

        // assert
        assertThat(deptId).isEqualTo(55L);
    }

    @Test
    @DisplayName("CS-TC-022: getDepartmentIdByCandidateId - không lấy được department -> trả null")
    void testGetDepartmentIdByCandidateId_ReturnNullOnErrorOrMissing_CS_TC_022() {
        // Testcase ID: CS-TC-022
        // Objective: Xác nhận trả null khi không lấy được department

        // arrange (candidate null)
        when(candidateRepository.findById(999L)).thenReturn(Optional.empty());

        // act
        Long deptId = candidateService.getDepartmentIdByCandidateId(999L, token);

        // assert
        assertThat(deptId).isNull();

        // arrange (jobService error)
        Candidate existing = buildCandidate(31L, "A", "y@y.com", 101L);
        when(candidateRepository.findById(31L)).thenReturn(Optional.of(existing));
        when(jobService.getJobPositionByIdSimple(101L, token)).thenThrow(new RuntimeException("boom"));

        // act
        Long deptId2 = candidateService.getDepartmentIdByCandidateId(31L, token);

        // assert
        assertThat(deptId2).isNull();
    }

    // =========================
    // 12. CandidateService.getCandidatesByInterviewer
    // =========================

    @Test
    @DisplayName("CS-TC-023: getCandidatesByInterviewer - lấy đúng danh sách & enrich title/departmentId")
    void testGetCandidatesByInterviewer_ReturnCandidatesAndEnrich_CS_TC_023() {
        // Testcase ID: CS-TC-023
        // Objective: Xác nhận lấy đúng danh sách ứng viên interviewer đã tham gia

        // arrange
        when(communicationService.getCandidateIdsByInterviewer(10L, token)).thenReturn(List.of(1L, 2L));
        when(candidateRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(candidate1, candidate2));

        when(jobService.getJobPositionsByIdsSimple(anyList(), eq(token)))
                .thenReturn(Map.of(
                        101L, buildJobPositionJson(101L, "Java Intern", 10L),
                        102L, buildJobPositionJson(102L, "QA Intern", 11L)));

        // act
        List<CandidateGetAllResponseDTO> result = candidateService.getCandidatesByInterviewer(10L, token);

        // assert
        assertThat(result).hasSize(2);
        assertThat(result).extracting(CandidateGetAllResponseDTO::getJobPositionTitle)
                .containsExactlyInAnyOrder("Java Intern", "QA Intern");
        assertThat(result).filteredOn(r -> r.getJobPositionId().equals(101L))
                .first().extracting(CandidateGetAllResponseDTO::getDepartmentId).isEqualTo(10L);

        verify(communicationService, times(1)).getCandidateIdsByInterviewer(10L, token);
        verify(candidateRepository, times(1)).findAllById(List.of(1L, 2L));
        verify(jobService, times(1)).getJobPositionsByIdsSimple(anyList(), eq(token));
    }

    @Test
    @DisplayName("CS-TC-024: getCandidatesByInterviewer - interviewer chưa tham gia lịch nào -> trả list rỗng")
    void testGetCandidatesByInterviewer_EmptyCandidateIds_CS_TC_024() {
        // Testcase ID: CS-TC-024
        // Objective: Xác nhận trả list rỗng khi interviewer chưa tham gia lịch nào

        // arrange
        when(communicationService.getCandidateIdsByInterviewer(10L, token)).thenReturn(List.of());

        // act
        List<CandidateGetAllResponseDTO> result = candidateService.getCandidatesByInterviewer(10L, token);

        // assert
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();

        verify(candidateRepository, never()).findAllById(anyList());
        verify(jobService, never()).getJobPositionsByIdsSimple(anyList(), any());
    }

    // =========================
    // 13. CandidateService.convertCandidateToEmployee
    // =========================

    @Test
    @DisplayName("CS-TC-025: convertCandidateToEmployee - chuyển thành công với fallback departmentId")
    void testConvertCandidateToEmployee_Success_FallbackDepartmentId_CS_TC_025() throws Exception {
        // Testcase ID: CS-TC-025
        // Objective: Xác nhận chuyển ứng viên thành nhân viên thành công với fallback
        // departmentId

        // arrange
        Candidate existing = buildCandidate(40L, "A", "a@b.com", 101L);
        existing.setDateOfBirth(LocalDate.of(2000, 1, 1));
        when(candidateRepository.findById(40L)).thenReturn(Optional.of(existing));

        // jobService.getJobPositionById returns wrapped body having
        // data.recruitmentRequest.departmentId
        ObjectNode wrapped = objectMapper.createObjectNode();
        ObjectNode data = objectMapper.createObjectNode();
        ObjectNode rr = objectMapper.createObjectNode();
        rr.put("departmentId", 5L);
        data.set("recruitmentRequest", rr);
        wrapped.set("data", data);

        when(jobService.getJobPositionById(101L, token)).thenReturn(ResponseEntity.ok(wrapped));

        ObjectNode employeeNode = objectMapper.createObjectNode().put("employeeId", 9000);
        when(userService.createEmployeeFromCandidate(
                eq(40L), eq("A"), eq("a@b.com"), eq(existing.getPhone()), eq("2000-01-01"),
                eq(existing.getGender()), eq(existing.getNationality()), eq(existing.getIdNumber()),
                eq(existing.getAddress()), eq(existing.getAvatarUrl()),
                eq(5L), eq(2L), eq("PROBATION"), eq(token)))
                .thenReturn(new ResponseEntity<>(employeeNode, HttpStatus.OK));

        // act
        JsonNode result = candidateService.convertCandidateToEmployee(40L, null, 2L, token);

        // assert
        assertThat(result.get("employeeId").asInt()).isEqualTo(9000);
        verify(jobService, times(1)).getJobPositionById(101L, token);
        verify(userService, times(1)).createEmployeeFromCandidate(
                anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("CS-TC-026: convertCandidateToEmployee - thiếu departmentId -> ném IdInvalidException")
    void testConvertCandidateToEmployee_MissingDepartmentId_CS_TC_026() {
        // Testcase ID: CS-TC-026
        // Objective: Xác nhận validate thiếu departmentId

        // arrange
        Candidate existing = buildCandidate(41L, "A", "c@d.com", 101L);
        when(candidateRepository.findById(41L)).thenReturn(Optional.of(existing));
        when(jobService.getJobPositionById(101L, token)).thenThrow(new RuntimeException("boom"));

        // act + assert
        assertThatThrownBy(() -> candidateService.convertCandidateToEmployee(41L, null, 2L, token))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Department ID là bắt buộc");

        verify(userService, never()).createEmployeeFromCandidate(
                anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("CS-TC-027: convertCandidateToEmployee - thiếu positionId -> ném IdInvalidException")
    void testConvertCandidateToEmployee_MissingPositionId_CS_TC_027() {
        // Testcase ID: CS-TC-027
        // Objective: Xác nhận validate thiếu positionId

        // arrange
        Candidate existing = buildCandidate(42L, "A", "e@f.com", 101L);
        when(candidateRepository.findById(42L)).thenReturn(Optional.of(existing));

        // act + assert
        assertThatThrownBy(() -> candidateService.convertCandidateToEmployee(42L, 5L, null, token))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Position ID là bắt buộc");

        verify(userService, never()).createEmployeeFromCandidate(
                anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                anyLong(), anyLong(), any(), any());
    }

    // =========================
    // 14. CandidateService.getCandidatesForStatistics
    // =========================

    @Test
    @DisplayName("CS-TC-028: getCandidatesForStatistics - lọc thống kê và map department đúng")
    void testGetCandidatesForStatistics_MapDepartment_CS_TC_028() {
        // Testcase ID: CS-TC-028
        // Objective: Xác nhận lọc thống kê và map department đúng

        // arrange
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
                        102L, buildJobPositionJson(102L, "QA Intern", 11L)));

        // act
        List<CandidateStatisticsDTO> stats = candidateService.getCandidatesForStatistics(
                CandidateStatus.SUBMITTED,
                "2026-01-01", "2026-01-31",
                null,
                null,
                token);

        // assert
        assertThat(stats).hasSize(2);
        assertThat(stats).extracting(CandidateStatisticsDTO::getDepartmentId)
                .containsExactlyInAnyOrder(10L, 11L);

        verify(jobService, times(1)).getJobPositionsByIds(anyList(), eq(token));
    }

    @Test
    @DisplayName("CS-TC-029: getCandidatesForStatistics - filter theo departmentId hoạt động đúng")
    void testGetCandidatesForStatistics_FilterByDepartment_CS_TC_029() {
        // Testcase ID: CS-TC-029
        // Objective: Xác nhận filter theo department hoạt động đúng

        // arrange
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

        // act
        List<CandidateStatisticsDTO> stats = candidateService.getCandidatesForStatistics(
                CandidateStatus.SUBMITTED,
                "2026-01-01", "2026-01-31",
                null,
                10L,
                token);

        // assert
        assertThat(stats).hasSize(1);
        assertThat(stats.get(0).getJobPositionId()).isEqualTo(101L);
        assertThat(stats.get(0).getDepartmentId()).isEqualTo(10L);
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

    private ReviewCandidateResponseDTO buildReview(Long id, Long reviewerId) {
        ReviewCandidateResponseDTO dto = new ReviewCandidateResponseDTO();
        dto.setId(id);
        dto.setReviewerId(reviewerId);
        return dto;
    }
}
