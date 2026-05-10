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
import java.math.BigDecimal;
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
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Unit tests (Mockito): {@link CandidateRepository} và các service tích hợp được mock — không có DB thật.
 * Kiểm tra tương tác persistence qua {@link org.mockito.Mockito#verify} và {@link ArgumentCaptor}.
 * <p>
 * Annotation {@code org.springframework.transaction.annotation.Transactional} <strong>không</strong> áp dụng
 * ở đây vì lớp không chạy trong Spring TestContext; rollback/kiểm tra bản ghi thật nằm ở
 * {@link CandidateServicePersistenceIT}.
 */
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
        void findByEmail_returnsCandidate_whenEmailExists() throws Exception {
                // Test Case ID: CS-TC-001
                // Objective: Xác nhận tìm đúng ứng viên khi email tồn tại

                // Arrange
                when(candidateRepository.findByEmail("a@gmail.com"))
                                .thenReturn(Optional.of(buildCandidate(10L, "A", "a@gmail.com", 101L)));

                // Act
                Candidate candidateFromService = candidateService.findByEmail("a@gmail.com");

                // Assert
                assertThat(candidateFromService).isNotNull();
                assertThat(candidateFromService.getEmail()).isEqualTo("a@gmail.com");
                verify(candidateRepository, times(1)).findByEmail("a@gmail.com");
                // CheckDB: không có trong unit (mock). Truy vấn DB + rollback: CandidateServicePersistenceIT.CS-INT-001
        }

        @Test
        @DisplayName("CS-TC-002: findByEmail - email không tồn tại ném IdInvalidException")
        void findByEmail_throwsIdInvalidException_whenEmailMissing() {
                // Test Case ID: CS-TC-002
                // Objective: Xác nhận ném lỗi khi email không tồn tại

                // Arrange
                when(candidateRepository.findByEmail("missing@gmail.com")).thenReturn(Optional.empty());

                // Act & Assert
                assertThatThrownBy(() -> candidateService.findByEmail("missing@gmail.com"))
                                .isInstanceOf(IdInvalidException.class)
                                .hasMessageContaining("ứng viên không tồn tại");

                verify(candidateRepository, times(1)).findByEmail("missing@gmail.com");
                // CheckDB: CandidateServicePersistenceIT.CS-INT-002
        }

        // =========================
        // 2. CandidateService.getAllWithFilters
        // =========================

        @Test
        @DisplayName("CS-TC-003: getAllWithFilters - lọc danh sách và enrich đúng jobPositionTitle, departmentId")
        void testGetAllWithFilters_EnrichJobPosition_CS_TC_003() {
                // Test Case ID: CS-TC-003
                // Objective: Lọc danh sách và enrich đúng thông tin job position

                // Arrange
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

                // Act
                PaginationDTO result = candidateService.getAllWithFilters(
                                candidateId, jobPositionId, status,
                                startDate, endDate, keyword, departmentId,
                                pageable, token);

                // Assert
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
                // Test Case ID: CS-TC-004
                // Objective: Xác nhận trả danh sách rỗng khi department không có jobPosition
                // nào

                // Arrange
                Long departmentId = 99L;
                Pageable pageable = PageRequest.of(0, 5);
                when(jobService.getJobPositionsByDepartmentId(eq(departmentId), eq(token)))
                                .thenReturn(Map.of());

                // Act
                PaginationDTO result = candidateService.getAllWithFilters(
                                null, null, null,
                                "2026-01-01", "2026-01-31", null,
                                departmentId,
                                pageable,
                                token);

                // Assert
                assertThat(result).isNotNull();
                assertThat(result.getMeta()).isNotNull();
                assertThat(result.getMeta().getPage()).isEqualTo(1);
                assertThat(result.getMeta().getPageSize()).isEqualTo(5);
                assertThat(result.getMeta().getTotal()).isEqualTo(0);
                assertThat(result.getMeta().getPages()).isEqualTo(0);
                assertThat(result.getResult()).isInstanceOf(List.class);
                assertThat((List<?>) result.getResult()).isEmpty();

                verify(candidateRepository, never()).findByFilters(any(), any(), any(), any(), any(), any(), any(),
                                any());
        }

        @Test
        @DisplayName("CS-TC-005: getAllWithFilters - parse ngày lỗi -> không crash và vẫn trả dữ liệu")
        void testGetAllWithFilters_InvalidDateFormat_CS_TC_005() {
                // Test Case ID: CS-TC-005
                // Objective: Xác nhận hàm không crash khi parse ngày lỗi

                // Arrange
                Pageable pageable = PageRequest.of(0, 10);
                Page<Candidate> page = new PageImpl<>(List.of(candidate1), pageable, 1);

                when(candidateRepository.findByFilters(
                                eq(null), eq(null), eq(null),
                                eq(null), eq(null),
                                eq(null), eq(null), eq(pageable)))
                                .thenReturn(page);

                // Act
                PaginationDTO result = candidateService.getAllWithFilters(
                                null, null, null,
                                "2026/01/01", "abc", null,
                                null,
                                pageable,
                                token);

                // Assert
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
                // Test Case ID: CS-TC-006
                // Objective: Lấy chi tiết ứng viên thành công

                // Arrange
                when(candidateRepository.findById(1L)).thenReturn(Optional.of(candidate1));

                // Act
                CandidateDetailResponseDTO dto = candidateService.getById(1L);

                // Assert
                assertThat(dto).isNotNull();
                assertThat(dto.getId()).isEqualTo(1L);
                assertThat(dto.getEmail()).isEqualTo("alice@gmail.com");
                assertThat(dto.getJobPositionId()).isEqualTo(101L);
                verify(candidateRepository, times(1)).findById(1L);
        }

        @Test
        @DisplayName("CS-TC-007: getById - ID không tồn tại -> ném IdInvalidException")
        void testGetById_NotFound_CS_TC_007() {
                // Test Case ID: CS-TC-007
                // Objective: Xác nhận lỗi khi ID không tồn tại

                // Arrange
                when(candidateRepository.findById(999L)).thenReturn(Optional.empty());

                // Act & Assert
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
                // Test Case ID: CS-TC-008
                // Objective: Tạo mới ứng viên với giá trị mặc định đúng

                // Arrange
                CreateCandidateDTO dto = buildCreateCandidateDTO();
                when(candidateRepository.existsByEmailAndJobPositionId(dto.getEmail(), dto.getJobPositionId()))
                                .thenReturn(false);
                when(candidateRepository.save(any(Candidate.class))).thenAnswer(inv -> {
                        Candidate c = inv.getArgument(0, Candidate.class);
                        c.setId(11L);
                        return c;
                });

                // Act
                Candidate saved = candidateService.create(dto);

                // Assert
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
                // Test Case ID: CS-TC-009
                // Objective: Chặn tạo trùng ứng viên theo email + jobPosition

                // Arrange
                CreateCandidateDTO dto = buildCreateCandidateDTO();
                when(candidateRepository.existsByEmailAndJobPositionId(dto.getEmail(), dto.getJobPositionId()))
                                .thenReturn(true);

                // Act & Assert
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
                // Test Case ID: CS-TC-010
                // Objective: Cập nhật chọn lọc các field được truyền

                // Arrange
                Candidate existing = buildCandidate(5L, "Old", "old@gmail.com", 101L);
                existing.setPhone("000");
                when(candidateRepository.findById(5L)).thenReturn(Optional.of(existing));
                when(candidateRepository.save(any(Candidate.class)))
                                .thenAnswer(inv -> inv.getArgument(0, Candidate.class));

                UpdateCandidateDTO dto = new UpdateCandidateDTO();
                dto.setName("New Name");
                dto.setDateOfBirth("2000-02-29");
                dto.setPhone(null);

                // Act
                CandidateDetailResponseDTO updated = candidateService.update(5L, dto);

                // Assert
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
                // Test Case ID: CS-TC-011
                // Objective: Xác nhận lỗi khi cập nhật ứng viên không tồn tại

                // Arrange
                when(candidateRepository.findById(999L)).thenReturn(Optional.empty());
                UpdateCandidateDTO dto = new UpdateCandidateDTO();
                dto.setName("X");

                // Act & Assert
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
                // Test Case ID: CS-TC-012
                // Objective: Xóa ứng viên thành công khi tồn tại

                // Arrange
                when(candidateRepository.findById(1L)).thenReturn(Optional.of(candidate1));

                // Act
                candidateService.delete(1L);

                // Assert
                verify(candidateRepository, times(1)).delete(eq(candidate1));
        }

        @Test
        @DisplayName("CS-TC-013: delete - ID không tồn tại -> ném IdInvalidException")
        void testDelete_NotFound_CS_TC_013() {
                // Test Case ID: CS-TC-013
                // Objective: Xác nhận lỗi khi xóa ID không tồn tại

                // Arrange
                when(candidateRepository.findById(999L)).thenReturn(Optional.empty());

                // Act & Assert
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
                // Test Case ID: CS-TC-014
                // Objective: Xác nhận convert đúng danh sách entity sang DTO

                // Arrange
                when(candidateRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(candidate1, candidate2));

                // Act
                List<CandidateDetailResponseDTO> result = candidateService.getByIds(List.of(1L, 2L));

                // Assert
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
                // Test Case ID: CS-TC-015
                // Objective: Tạo ứng viên từ CV upload thành công

                // Arrange
                UploadCVDTO dto = buildUploadCVDTO();
                when(candidateRepository.existsByEmailAndJobPositionId(dto.getEmail(), dto.getJobPositionId()))
                                .thenReturn(false);
                when(candidateRepository.save(any(Candidate.class))).thenAnswer(inv -> {
                        Candidate c = inv.getArgument(0, Candidate.class);
                        c.setId(12L);
                        return c;
                });

                // Act
                Candidate saved = candidateService.createCandidateFromApplication(dto);

                // Assert
                assertThat(saved.getId()).isEqualTo(12L);
                assertThat(saved.getStatus()).isEqualTo(CandidateStatus.SUBMITTED);
                assertThat(saved.getAppliedDate()).isEqualTo(LocalDate.now());
                assertThat(saved.getResumeUrl()).isEqualTo(dto.getCvUrl());
                assertThat(saved.getNotes()).isEqualTo(dto.getNotes());
        }

        @Test
        @DisplayName("CS-TC-016: createCandidateFromApplication - chặn tạo trùng ứng viên")
        void testCreateCandidateFromApplication_Duplicate_CS_TC_016() {
                // Test Case ID: CS-TC-016
                // Objective: Chặn tạo trùng từ application

                // Arrange
                UploadCVDTO dto = buildUploadCVDTO();
                when(candidateRepository.existsByEmailAndJobPositionId(dto.getEmail(), dto.getJobPositionId()))
                                .thenReturn(true);

                // Act & Assert
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
                // Test Case ID: CS-TC-017
                // Objective: Xác nhận set trạng thái từ chối và lưu rejectionReason

                // Arrange
                Candidate existing = buildCandidate(8L, "A", "a2@x.com", 101L);
                when(candidateRepository.findById(8L)).thenReturn(Optional.of(existing));
                when(candidateRepository.save(any(Candidate.class)))
                                .thenAnswer(inv -> inv.getArgument(0, Candidate.class));

                // Act
                CandidateDetailResponseDTO dto = candidateService.updateCandidateStatus(8L, "REJECTED", "Không phù hợp",
                                100L);

                // Assert
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
                // Test Case ID: CS-TC-018
                // Objective: Xác nhận lỗi khi status không nằm trong enum

                // Arrange
                when(candidateRepository.findById(9L))
                                .thenReturn(Optional.of(buildCandidate(9L, "A", "a3@x.com", 101L)));

                // Act & Assert
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
                // Test Case ID: CS-TC-019
                // Objective: Xác nhận aggregate đủ comments, reviews, jobPosition,
                // upcomingSchedules

                // Arrange
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

                // Act
                CandidateDetailResponseDTO dto = candidateService.getCandidateDetailById(20L, token);

                // Assert
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
                // Test Case ID: CS-TC-020
                // Objective: Xác nhận fallback reviews rỗng khi review-service lỗi

                // Arrange
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

                // Act
                CandidateDetailResponseDTO dto = candidateService.getCandidateDetailById(21L, token);

                // Assert
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
                // Test Case ID: CS-TC-021
                // Objective: Xác nhận parse đúng departmentId từ JSON lồng

                // Arrange
                Candidate existing = buildCandidate(30L, "A", "x@x.com", 101L);
                when(candidateRepository.findById(30L)).thenReturn(Optional.of(existing));

                JsonNode jobPositionSimple = buildJobPositionJson(101L, "Java Intern", 55L);
                when(jobService.getJobPositionByIdSimple(101L, token))
                                .thenReturn(ResponseEntity.ok(jobPositionSimple));

                // Act
                Long deptId = candidateService.getDepartmentIdByCandidateId(30L, token);

                // Assert
                assertThat(deptId).isEqualTo(55L);
        }

        @Test
        @DisplayName("CS-TC-022: getDepartmentIdByCandidateId - không lấy được department -> trả null")
        void testGetDepartmentIdByCandidateId_ReturnNullOnErrorOrMissing_CS_TC_022() {
                // Test Case ID: CS-TC-022
                // Objective: Xác nhận trả null khi không lấy được department

                // Arrange (candidate null)
                when(candidateRepository.findById(999L)).thenReturn(Optional.empty());

                // Act
                Long deptId = candidateService.getDepartmentIdByCandidateId(999L, token);

                // Assert
                assertThat(deptId).isNull();

                // Arrange (jobService error)
                Candidate existing = buildCandidate(31L, "A", "y@y.com", 101L);
                when(candidateRepository.findById(31L)).thenReturn(Optional.of(existing));
                when(jobService.getJobPositionByIdSimple(101L, token)).thenThrow(new RuntimeException("boom"));

                // Act
                Long deptId2 = candidateService.getDepartmentIdByCandidateId(31L, token);

                // Assert
                assertThat(deptId2).isNull();
        }

        // =========================
        // 12. CandidateService.getCandidatesByInterviewer
        // =========================

        @Test
        @DisplayName("CS-TC-023: getCandidatesByInterviewer - lấy đúng danh sách & enrich title/departmentId")
        void testGetCandidatesByInterviewer_ReturnCandidatesAndEnrich_CS_TC_023() {
                // Test Case ID: CS-TC-023
                // Objective: Xác nhận lấy đúng danh sách ứng viên interviewer đã tham gia

                // Arrange
                when(communicationService.getCandidateIdsByInterviewer(10L, token)).thenReturn(List.of(1L, 2L));
                when(candidateRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(candidate1, candidate2));

                when(jobService.getJobPositionsByIdsSimple(anyList(), eq(token)))
                                .thenReturn(Map.of(
                                                101L, buildJobPositionJson(101L, "Java Intern", 10L),
                                                102L, buildJobPositionJson(102L, "QA Intern", 11L)));

                // Act
                List<CandidateGetAllResponseDTO> result = candidateService.getCandidatesByInterviewer(10L, token);

                // Assert
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
                // Test Case ID: CS-TC-024
                // Objective: Xác nhận trả list rỗng khi interviewer chưa tham gia lịch nào

                // Arrange
                when(communicationService.getCandidateIdsByInterviewer(10L, token)).thenReturn(List.of());

                // Act
                List<CandidateGetAllResponseDTO> result = candidateService.getCandidatesByInterviewer(10L, token);

                // Assert
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
                // Test Case ID: CS-TC-025
                // Objective: Xác nhận chuyển ứng viên thành nhân viên thành công với fallback
                // departmentId

                // Arrange
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

                // Act
                JsonNode result = candidateService.convertCandidateToEmployee(40L, null, 2L, token);

                // Assert
                assertThat(result.get("employeeId").asInt()).isEqualTo(9000);
                verify(jobService, times(1)).getJobPositionById(101L, token);
                verify(userService, times(1)).createEmployeeFromCandidate(
                                anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                                anyLong(), anyLong(), any(), any());
        }

        @Test
        @DisplayName("CS-TC-026: convertCandidateToEmployee - thiếu departmentId -> ném IdInvalidException")
        void testConvertCandidateToEmployee_MissingDepartmentId_CS_TC_026() {
                // Test Case ID: CS-TC-026
                // Objective: Xác nhận validate thiếu departmentId

                // Arrange
                Candidate existing = buildCandidate(41L, "A", "c@d.com", 101L);
                when(candidateRepository.findById(41L)).thenReturn(Optional.of(existing));
                when(jobService.getJobPositionById(101L, token)).thenThrow(new RuntimeException("boom"));

                // Act & Assert
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
                // Test Case ID: CS-TC-027
                // Objective: Xác nhận validate thiếu positionId

                // Arrange
                Candidate existing = buildCandidate(42L, "A", "e@f.com", 101L);
                when(candidateRepository.findById(42L)).thenReturn(Optional.of(existing));

                // Act & Assert
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
                // Test Case ID: CS-TC-028
                // Objective: Xác nhận lọc thống kê và map department đúng

                // Arrange
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

                // Act
                List<CandidateStatisticsDTO> stats = candidateService.getCandidatesForStatistics(
                                CandidateStatus.SUBMITTED,
                                "2026-01-01", "2026-01-31",
                                null,
                                null,
                                token);

                // Assert
                assertThat(stats).hasSize(2);
                assertThat(stats).extracting(CandidateStatisticsDTO::getDepartmentId)
                                .containsExactlyInAnyOrder(10L, 11L);

                verify(jobService, times(1)).getJobPositionsByIds(anyList(), eq(token));
        }

        @Test
        @DisplayName("CS-TC-029: getCandidatesForStatistics - filter theo departmentId hoạt động đúng")
        void testGetCandidatesForStatistics_FilterByDepartment_CS_TC_029() {
                // Test Case ID: CS-TC-029
                // Objective: Xác nhận filter theo department hoạt động đúng

                // Arrange
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

                // Act
                List<CandidateStatisticsDTO> stats = candidateService.getCandidatesForStatistics(
                                CandidateStatus.SUBMITTED,
                                "2026-01-01", "2026-01-31",
                                null,
                                10L,
                                token);

                // Assert
                assertThat(stats).hasSize(1);
                assertThat(stats.get(0).getJobPositionId()).isEqualTo(101L);
                assertThat(stats.get(0).getDepartmentId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("CS-TC-030: updateCandidateStatus - status khác REJECTED -> không set rejectionReason")
        void testUpdateCandidateStatus_NotRejected_DoesNotSetRejectionReason_CS_TC_030() throws Exception {
                // Test Case ID: CS-TC-030
                // Objective: status khác REJECTED để kiểm tra không set rejectionReason

                // Arrange
                Candidate existing = buildCandidate(50L, "A", "notrejected@x.com", 101L);
                existing.setRejectionReason(null);
                when(candidateRepository.findById(50L)).thenReturn(Optional.of(existing));
                when(candidateRepository.save(any(Candidate.class)))
                                .thenAnswer(inv -> inv.getArgument(0, Candidate.class));

                // Act
                CandidateDetailResponseDTO dto = candidateService.updateCandidateStatus(50L, "OFFER", "ignored", 7L);

                // Assert
                assertThat(dto.getStatus()).isEqualTo(CandidateStatus.OFFER);
                assertThat(dto.getRejectionReason()).isNull();

                ArgumentCaptor<Candidate> candCaptor = ArgumentCaptor.forClass(Candidate.class);
                verify(candidateRepository, times(1)).save(candCaptor.capture());
                assertThat(candCaptor.getValue().getUpdatedBy()).isEqualTo(7L);
                assertThat(candCaptor.getValue().getRejectionReason()).isNull();
        }

        @Test
        @DisplayName("CS-TC-031: update - DTO toàn bộ field null -> không đổi dữ liệu cũ")
        void testUpdate_AllFieldsNull_DoesNotChangeExisting_CS_TC_031() throws Exception {
                // Test Case ID: CS-TC-031
                // Objective: DTO toàn bộ field null → không đổi dữ liệu cũ

                // Arrange
                Candidate existing = buildCandidate(51L, "Old Name", "old51@gmail.com", 101L);
                existing.setPhone("051");
                existing.setGender("F");
                existing.setNationality("VN");
                existing.setAddress("Addr");
                existing.setAvatarUrl("http://ava.png");
                existing.setHighestEducation("Bachelor");
                existing.setUniversity("U");
                existing.setGraduationYear("2024");
                existing.setNotes("note51");
                existing.setDateOfBirth(LocalDate.of(1999, 12, 31));

                when(candidateRepository.findById(51L)).thenReturn(Optional.of(existing));
                when(candidateRepository.save(any(Candidate.class)))
                                .thenAnswer(inv -> inv.getArgument(0, Candidate.class));

                UpdateCandidateDTO dto = new UpdateCandidateDTO(); // all null

                // Act
                CandidateDetailResponseDTO updated = candidateService.update(51L, dto);

                // Assert
                assertThat(updated.getId()).isEqualTo(51L);
                assertThat(updated.getName()).isEqualTo("Old Name");
                assertThat(updated.getEmail()).isEqualTo("old51@gmail.com");
                assertThat(updated.getPhone()).isEqualTo("051");
                assertThat(updated.getGender()).isEqualTo("F");
                assertThat(updated.getNationality()).isEqualTo("VN");
                assertThat(updated.getAddress()).isEqualTo("Addr");
                assertThat(updated.getAvatarUrl()).isEqualTo("http://ava.png");
                assertThat(updated.getHighestEducation()).isEqualTo("Bachelor");
                assertThat(updated.getUniversity()).isEqualTo("U");
                assertThat(updated.getGraduationYear()).isEqualTo("2024");
                assertThat(updated.getNotes()).isEqualTo("note51");
                assertThat(updated.getDateOfBirth()).isEqualTo(LocalDate.of(1999, 12, 31));
        }

        @Test
        @DisplayName("CS-TC-032: getAllWithFilters - không truyền departmentId -> gọi jobPositionsByIdsSimple để enrich")
        void testGetAllWithFilters_NoDepartmentId_EnrichViaIdsSimple_CS_TC_032() {
                // Test Case ID: CS-TC-032
                // Objective: không truyền departmentId

                // Arrange
                Pageable pageable = PageRequest.of(0, 10);
                Page<Candidate> page = new PageImpl<>(List.of(candidate1, candidate2), pageable, 2);
                when(candidateRepository.findByFilters(
                                eq(null), eq(null), eq(null),
                                any(LocalDate.class), any(LocalDate.class),
                                eq("kw"), eq(null), eq(pageable)))
                                .thenReturn(page);

                when(jobService.getJobPositionsByIdsSimple(anyList(), eq(token)))
                                .thenReturn(Map.of(
                                                101L, buildJobPositionJson(101L, "Java Intern", 10L),
                                                102L, buildJobPositionJson(102L, "QA Intern", 11L)));

                // Act
                PaginationDTO result = candidateService.getAllWithFilters(
                                null, null, null,
                                "2026-01-01", "2026-01-31", "kw",
                                null,
                                pageable,
                                token);

                // Assert
                @SuppressWarnings("unchecked")
                List<CandidateGetAllResponseDTO> dtos = (List<CandidateGetAllResponseDTO>) result.getResult();
                assertThat(dtos).hasSize(2);
                assertThat(dtos).extracting(CandidateGetAllResponseDTO::getJobPositionTitle)
                                .containsExactlyInAnyOrder("Java Intern", "QA Intern");
                verify(jobService, times(1)).getJobPositionsByIdsSimple(anyList(), eq(token));
                verify(jobService, never()).getJobPositionsByDepartmentId(anyLong(), any());
        }

        @Test
        @DisplayName("CS-TC-033: getAllWithFilters - có departmentId nhưng job-service lỗi -> propagate exception")
        void testGetAllWithFilters_DepartmentId_JobServiceThrows_CS_TC_033() {
                // Test Case ID: CS-TC-033
                // Objective: có departmentId nhưng job-service lỗi

                // Arrange
                when(jobService.getJobPositionsByDepartmentId(eq(10L), eq(token)))
                                .thenThrow(new RuntimeException("job-service down"));

                // Act & Assert (service hiện tại không fallback)
                assertThatThrownBy(() -> candidateService.getAllWithFilters(
                                null, null, null,
                                "2026-01-01", "2026-01-31", null,
                                10L,
                                PageRequest.of(0, 10),
                                token))
                                .isInstanceOf(RuntimeException.class)
                                .hasMessageContaining("job-service down");
        }

        @Test
        @DisplayName("CS-TC-034: getCandidateDetailById - comment-service lỗi (candidate có comments != null) -> propagate")
        void testGetCandidateDetailById_CommentServiceThrows_CS_TC_034() throws Exception {
                // Test Case ID: CS-TC-034
                // Objective: comment-service lỗi

                // Arrange
                Candidate existing = buildCandidate(60L, "A", "cmt@x.com", 101L);
                existing.setComments(java.util.Set.of()); // trigger commentService call
                when(candidateRepository.findById(60L)).thenReturn(Optional.of(existing));
                when(commentService.getByCandidateId(60L, token)).thenThrow(new RuntimeException("comment down"));

                // Act & Assert (service hiện tại không catch comment-service)
                assertThatThrownBy(() -> candidateService.getCandidateDetailById(60L, token))
                                .isInstanceOf(RuntimeException.class)
                                .hasMessageContaining("comment down");
        }

        @Test
        @DisplayName("CS-TC-035: getCandidateDetailById - schedule-service lỗi -> propagate")
        void testGetCandidateDetailById_ScheduleServiceThrows_CS_TC_035() {
                // Test Case ID: CS-TC-035
                // Objective: schedule-service lỗi

                // Arrange
                Candidate existing = buildCandidate(61L, "A", "sch@x.com", 101L);
                existing.setComments(null); // skip commentService
                when(candidateRepository.findById(61L)).thenReturn(Optional.of(existing));

                when(reviewCandidateService.getByCandidateId(61L, token)).thenReturn(List.of());
                when(jobService.getJobPositionById(101L, token))
                                .thenReturn(ResponseEntity.ok(buildJobPositionJson(101L, "Java Intern", 10L)));
                when(communicationService.getUpcomingSchedulesForCandidate(61L, token))
                                .thenThrow(new RuntimeException("schedule down"));

                // Act & Assert
                assertThatThrownBy(() -> candidateService.getCandidateDetailById(61L, token))
                                .isInstanceOf(RuntimeException.class)
                                .hasMessageContaining("schedule down");
        }

        @Test
        @DisplayName("CS-TC-036: getCandidatesByInterviewer - schedule trả candidateId nhưng candidate không tồn tại")
        void testGetCandidatesByInterviewer_CandidateIdsButNoCandidates_CS_TC_036() {
                // Test Case ID: CS-TC-036
                // Objective: schedule có candidateId nhưng candidate không tồn tại

                // Arrange
                when(communicationService.getCandidateIdsByInterviewer(10L, token)).thenReturn(List.of(999L));
                when(candidateRepository.findAllById(List.of(999L))).thenReturn(List.of());

                // Act
                List<CandidateGetAllResponseDTO> result = candidateService.getCandidatesByInterviewer(10L, token);

                // Assert
                assertThat(result).isNotNull();
                assertThat(result).isEmpty();
                verify(jobService, never()).getJobPositionsByIdsSimple(anyList(), any());
        }

        @Test
        @DisplayName("CS-TC-037: convertCandidateToEmployee - truyền sẵn departmentId, không cần fallback job-service")
        void testConvertCandidateToEmployee_DepartmentProvided_NoFallback_CS_TC_037() throws Exception {
                // Test Case ID: CS-TC-037
                // Objective: truyền sẵn departmentId, không cần fallback

                // Arrange
                Candidate existing = buildCandidate(70L, "A", "dept@x.com", 101L);
                when(candidateRepository.findById(70L)).thenReturn(Optional.of(existing));

                ObjectNode employeeNode = objectMapper.createObjectNode().put("employeeId", 7000);
                when(userService.createEmployeeFromCandidate(
                                eq(70L), eq("A"), eq("dept@x.com"), eq(existing.getPhone()), eq(null),
                                eq(existing.getGender()), eq(existing.getNationality()), eq(existing.getIdNumber()),
                                eq(existing.getAddress()), eq(existing.getAvatarUrl()),
                                eq(55L), eq(2L), eq("PROBATION"), eq(token)))
                                .thenReturn(new ResponseEntity<>(employeeNode, HttpStatus.OK));

                // Act
                JsonNode result = candidateService.convertCandidateToEmployee(70L, 55L, 2L, token);

                // Assert
                assertThat(result.get("employeeId").asInt()).isEqualTo(7000);
                verify(jobService, never()).getJobPositionById(anyLong(), any());
        }

        @Test
        @DisplayName("CS-TC-038: convertCandidateToEmployee - candidate không tồn tại -> ném IdInvalidException")
        void testConvertCandidateToEmployee_CandidateNotFound_CS_TC_038() {
                // Test Case ID: CS-TC-038
                // Objective: candidate không tồn tại

                // Arrange
                when(candidateRepository.findById(9999L)).thenReturn(Optional.empty());

                // Act & Assert
                assertThatThrownBy(() -> candidateService.convertCandidateToEmployee(9999L, 1L, 1L, token))
                                .isInstanceOf(IdInvalidException.class)
                                .hasMessageContaining("Ứng viên không tồn tại");
        }

        @Test
        @DisplayName("CS-TC-039: getCandidatesForStatistics - không truyền departmentId -> không filter")
        void testGetCandidatesForStatistics_NoDepartmentId_NoFilter_CS_TC_039() {
                // Test Case ID: CS-TC-039
                // Objective: không truyền departmentId

                // Arrange
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

                // Act
                List<CandidateStatisticsDTO> stats = candidateService.getCandidatesForStatistics(
                                CandidateStatus.SUBMITTED,
                                "2026-01-01", "2026-01-31",
                                null,
                                null,
                                token);

                // Assert
                assertThat(stats).hasSize(2);
                assertThat(stats).extracting(CandidateStatisticsDTO::getDepartmentId)
                                .containsExactlyInAnyOrder(10L, 11L);
        }

        @Test
        @DisplayName("CS-TC-040: getByIds - truyền list rỗng -> trả list rỗng")
        void testGetByIds_EmptyList_CS_TC_040() {
                // Test Case ID: CS-TC-040
                // Objective: truyền list rỗng

                // Arrange
                when(candidateRepository.findAllById(List.of())).thenReturn(List.of());

                // Act
                List<CandidateDetailResponseDTO> result = candidateService.getByIds(List.of());

                // Assert
                assertThat(result).isNotNull();
                assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("CS-TC-041: getCandidateDetailById - schedules trả body có field data -> cover nhánh has('data')")
        void testGetCandidateDetailById_SchedulesWrappedDataArray_CoversDataBranch() throws Exception {
                // Test Case ID: CS-TC-041
                // Arrange
                Candidate existing = buildCandidate(80L, "A", "wrap@x.com", 101L);
                existing.setComments(null); // skip commentService
                when(candidateRepository.findById(80L)).thenReturn(Optional.of(existing));
                when(reviewCandidateService.getByCandidateId(80L, token)).thenReturn(List.of());
                when(jobService.getJobPositionById(101L, token))
                                .thenReturn(ResponseEntity.ok(buildJobPositionJson(101L, "Java Intern", 10L)));

                ObjectNode wrapper = objectMapper.createObjectNode();
                ArrayNode data = objectMapper.createArrayNode();
                data.add(objectMapper.createObjectNode().put("id", 1));
                data.add(objectMapper.createObjectNode().put("id", 2));
                wrapper.set("data", data);

                when(communicationService.getUpcomingSchedulesForCandidate(80L, token))
                                .thenReturn(new ResponseEntity<>(wrapper, HttpStatus.OK));

                // Act
                CandidateDetailResponseDTO dto = candidateService.getCandidateDetailById(80L, token);

                // Assert
                assertThat(dto.getUpcomingSchedules()).hasSize(2);
        }

        @Test
        @DisplayName("CS-TC-042: convertCandidateToEmployee - employeeResponse non-2xx -> ném IdInvalidException")
        void testConvertCandidateToEmployee_UserServiceReturnsNon2xx_Throws() throws Exception {
                // Test Case ID: CS-TC-042
                // Arrange
                Candidate existing = buildCandidate(81L, "A", "bad@x.com", 101L);
                when(candidateRepository.findById(81L)).thenReturn(Optional.of(existing));

                ObjectNode err = objectMapper.createObjectNode().put("error", "fail");
                when(userService.createEmployeeFromCandidate(
                                anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                                anyLong(), anyLong(), any(), any()))
                                .thenReturn(new ResponseEntity<>(err, HttpStatus.BAD_REQUEST));

                // Act & Assert
                assertThatThrownBy(() -> candidateService.convertCandidateToEmployee(81L, 1L, 2L, token))
                                .isInstanceOf(IdInvalidException.class)
                                .hasMessageContaining("Không thể tạo nhân viên từ ứng viên");
        }

        @Test
        @DisplayName("CS-TC-043: getAllWithFilters - token rỗng -> không enrich, title/departmentId null")
        void testGetAllWithFilters_EmptyToken_NoEnrich() {
                // Test Case ID: CS-TC-043
                // Arrange
                Pageable pageable = PageRequest.of(0, 10);
                Page<Candidate> page = new PageImpl<>(List.of(candidate1), pageable, 1);
                when(candidateRepository.findByFilters(
                                eq(null), eq(null), eq(null),
                                eq(null), eq(null),
                                eq(null), eq(null), eq(pageable)))
                                .thenReturn(page);

                // Act
                PaginationDTO result = candidateService.getAllWithFilters(
                                null, null, null,
                                "", "", null,
                                null,
                                pageable,
                                "");

                // Assert
                @SuppressWarnings("unchecked")
                List<CandidateGetAllResponseDTO> dtos = (List<CandidateGetAllResponseDTO>) result.getResult();
                assertThat(dtos).hasSize(1);
                assertThat(dtos.get(0).getJobPositionTitle()).isNull();
                assertThat(dtos.get(0).getDepartmentId()).isNull();
                verify(jobService, never()).getJobPositionsByIdsSimple(anyList(), any());
        }

        @Test
        @DisplayName("CS-TC-044: existsByEmail - cover pass-through repository call")
        void testExistsByEmail_PassThrough() {
                // Test Case ID: CS-TC-044
                when(candidateRepository.existsByEmail("exists@x.com")).thenReturn(true);
                assertThat(candidateService.existsByEmail("exists@x.com")).isTrue();
                verify(candidateRepository, times(1)).existsByEmail("exists@x.com");
        }

        @Test
        @DisplayName("CS-TC-045: saveCandidate - cover pass-through repository save call")
        void testSaveCandidate_PassThrough() {
                // Test Case ID: CS-TC-045
                Candidate c = buildCandidate(null, "A", "save@x.com", 101L);
                when(candidateRepository.save(any(Candidate.class))).thenAnswer(inv -> {
                        Candidate saved = inv.getArgument(0, Candidate.class);
                        saved.setId(999L);
                        return saved;
                });
                Candidate saved = candidateService.saveCandidate(c);
                assertThat(saved.getId()).isEqualTo(999L);
                verify(candidateRepository, times(1)).save(any(Candidate.class));
        }

        @Test
        @DisplayName("CS-TC-046: countCandidatesByJobPositionId - cover pass-through repository count call")
        void testCountCandidatesByJobPositionId_PassThrough() {
                // Test Case ID: CS-TC-046
                when(candidateRepository.countByJobPositionId(101L)).thenReturn(3L);
                assertThat(candidateService.countCandidatesByJobPositionId(101L)).isEqualTo(3L);
                verify(candidateRepository, times(1)).countByJobPositionId(101L);
        }

        @Test
        @DisplayName("CS-TC-047: create - createdBy null -> không set createdBy explicit (cover nhánh dto.getCreatedBy == null)")
        void testCreate_CreatedByNull_BranchCoverage() throws Exception {
                // Test Case ID: CS-TC-047
                CreateCandidateDTO dto = buildCreateCandidateDTO();
                dto.setCreatedBy(null);
                when(candidateRepository.existsByEmailAndJobPositionId(dto.getEmail(), dto.getJobPositionId()))
                                .thenReturn(false);
                when(candidateRepository.save(any(Candidate.class))).thenAnswer(inv -> inv.getArgument(0, Candidate.class));

                Candidate saved = candidateService.create(dto);

                assertThat(saved.getStatus()).isEqualTo(CandidateStatus.SUBMITTED);
                // createdBy có thể null ở unit-test vì @PrePersist không chạy với mock repo
                assertThat(saved.getCreatedBy()).isNull();
        }

        @Test
        @DisplayName("CS-TC-048: createCandidateFromApplication - notes null -> không set notes (cover nhánh dto.getNotes == null)")
        void testCreateCandidateFromApplication_NotesNull_BranchCoverage() throws Exception {
                // Test Case ID: CS-TC-048
                UploadCVDTO dto = buildUploadCVDTO();
                dto.setNotes(null);
                when(candidateRepository.existsByEmailAndJobPositionId(dto.getEmail(), dto.getJobPositionId()))
                                .thenReturn(false);
                when(candidateRepository.save(any(Candidate.class))).thenAnswer(inv -> inv.getArgument(0, Candidate.class));

                Candidate saved = candidateService.createCandidateFromApplication(dto);

                assertThat(saved.getNotes()).isNull();
                assertThat(saved.getStatus()).isEqualTo(CandidateStatus.SUBMITTED);
        }

        @Test
        @DisplayName("CS-TC-049: updateCandidateStatus - REJECTED nhưng feedback null -> không set rejectionReason")
        void testUpdateCandidateStatus_Rejected_FeedbackNull_BranchCoverage() throws Exception {
                // Test Case ID: CS-TC-049
                Candidate existing = buildCandidate(91L, "A", "rej@x.com", 101L);
                existing.setRejectionReason(null);
                when(candidateRepository.findById(91L)).thenReturn(Optional.of(existing));
                when(candidateRepository.save(any(Candidate.class))).thenAnswer(inv -> inv.getArgument(0, Candidate.class));

                CandidateDetailResponseDTO dto = candidateService.updateCandidateStatus(91L, "REJECTED", null, 1L);
                assertThat(dto.getStatus()).isEqualTo(CandidateStatus.REJECTED);
                assertThat(dto.getRejectionReason()).isNull();
        }

        @Test
        @DisplayName("CS-TC-050: getCandidateDetailById - comments null -> skip commentService; schedules non-2xx -> not set")
        void testGetCandidateDetailById_CommentsNull_SchedulesNon2xx_BranchCoverage() throws Exception {
                // Test Case ID: CS-TC-050
                Candidate existing = buildCandidate(92L, "A", "skipcmt@x.com", null);
                existing.setComments(null); // skip commentService
                when(candidateRepository.findById(92L)).thenReturn(Optional.of(existing));
                when(reviewCandidateService.getByCandidateId(92L, token)).thenReturn(List.of());
                when(communicationService.getUpcomingSchedulesForCandidate(92L, token))
                                .thenReturn(new ResponseEntity<>(objectMapper.createArrayNode(), HttpStatus.BAD_REQUEST));

                CandidateDetailResponseDTO dto = candidateService.getCandidateDetailById(92L, token);

                assertThat(dto.getComments()).isNull();
                assertThat(dto.getJobPosition()).isNull(); // jobPositionId null -> skip
                assertThat(dto.getUpcomingSchedules()).isNull(); // non-2xx -> skip set
        }

        @Test
        @DisplayName("CS-TC-051: getDepartmentIdByCandidateId - response non-2xx / missing recruitmentRequest -> null")
        void testGetDepartmentIdByCandidateId_Non2xxOrMissingRecruitmentRequest_BranchCoverage() {
                // Test Case ID: CS-TC-051
                Candidate existing = buildCandidate(93L, "A", "dept@x.com", 101L);
                when(candidateRepository.findById(93L)).thenReturn(Optional.of(existing));
                when(jobService.getJobPositionByIdSimple(101L, token))
                                .thenReturn(new ResponseEntity<>(buildJobPositionJson(101L, "Java", null), HttpStatus.OK));
                Long deptMissing = candidateService.getDepartmentIdByCandidateId(93L, token);
                assertThat(deptMissing).isNull();

                when(jobService.getJobPositionByIdSimple(101L, token))
                                .thenReturn(new ResponseEntity<>(buildJobPositionJson(101L, "Java", 10L), HttpStatus.BAD_REQUEST));
                Long deptNon2xx = candidateService.getDepartmentIdByCandidateId(93L, token);
                assertThat(deptNon2xx).isNull();
        }

        @Test
        @DisplayName("CS-TC-052: convertCandidateToEmployee - fallback dùng body direct có departmentId field")
        void testConvertCandidateToEmployee_Fallback_DepartmentIdDirectField_BranchCoverage() throws Exception {
                // Test Case ID: CS-TC-052
                Candidate existing = buildCandidate(94L, "A", "directdept@x.com", 101L);
                when(candidateRepository.findById(94L)).thenReturn(Optional.of(existing));

                ObjectNode jobPositionDirect = objectMapper.createObjectNode();
                jobPositionDirect.put("departmentId", 66L);
                when(jobService.getJobPositionById(101L, token)).thenReturn(ResponseEntity.ok(jobPositionDirect));

                ObjectNode employeeNode = objectMapper.createObjectNode().put("employeeId", 9400);
                when(userService.createEmployeeFromCandidate(
                                anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                                eq(66L), eq(2L), any(), eq(token)))
                                .thenReturn(new ResponseEntity<>(employeeNode, HttpStatus.OK));

                JsonNode result = candidateService.convertCandidateToEmployee(94L, null, 2L, token);
                assertThat(result.get("employeeId").asInt()).isEqualTo(9400);
        }

        @Test
        @DisplayName("CS-TC-053: convertCandidateToEmployee - employeeResponse 2xx nhưng body null -> ném IdInvalidException")
        void testConvertCandidateToEmployee_2xxButNullBody_Throws() {
                // Test Case ID: CS-TC-053
                Candidate existing = buildCandidate(95L, "A", "nullbody@x.com", 101L);
                when(candidateRepository.findById(95L)).thenReturn(Optional.of(existing));
                when(userService.createEmployeeFromCandidate(
                                anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                                anyLong(), anyLong(), any(), any()))
                                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

                assertThatThrownBy(() -> candidateService.convertCandidateToEmployee(95L, 1L, 2L, token))
                                .isInstanceOf(IdInvalidException.class)
                                .hasMessageContaining("Unknown error");
        }

        // =========================
        // CS-TC-054 … CS-TC-087: bổ sung nhánh / lambda (JaCoCo — statement + branch CandidateService)
        // =========================

        @Test
        @DisplayName("CS-TC-054: updateCandidateStatus - ứng viên không tồn tại -> IdInvalidException (lambda orElseThrow)")
        void coverage_updateCandidateStatus_notFound() {
                // Test Case ID: CS-TC-054
                when(candidateRepository.findById(888L)).thenReturn(Optional.empty());
                assertThatThrownBy(() -> candidateService.updateCandidateStatus(888L, "OFFER", null, 1L))
                                .isInstanceOf(IdInvalidException.class)
                                .hasMessageContaining("Ứng viên không tồn tại");
                verify(candidateRepository, never()).save(any(Candidate.class));
        }

        @Test
        @DisplayName("CS-TC-055: getCandidateDetailById - ứng viên không tồn tại -> IdInvalidException (lambda orElseThrow)")
        void coverage_getCandidateDetailById_notFound() {
                // Test Case ID: CS-TC-055
                when(candidateRepository.findById(888L)).thenReturn(Optional.empty());
                assertThatThrownBy(() -> candidateService.getCandidateDetailById(888L, token))
                                .isInstanceOf(IdInvalidException.class)
                                .hasMessageContaining("Ứng viên không tồn tại");
        }

        @Test
        @DisplayName("CS-TC-056: getAllWithFilters - token null -> không enrich; có candidate jobPositionId null")
        void coverage_getAllWithFilters_tokenNull_andNullJobPositionId() {
                // Test Case ID: CS-TC-056
                Candidate noJp = buildCandidate(3L, "NoJp", "noj@gmail.com", null);
                Pageable pageable = PageRequest.of(0, 10);
                Page<Candidate> page = new PageImpl<>(List.of(candidate1, noJp), pageable, 2);
                when(candidateRepository.findByFilters(
                                eq(null), eq(null), eq(null),
                                eq(null), eq(null),
                                eq(null), eq(null), eq(pageable)))
                                .thenReturn(page);

                PaginationDTO result = candidateService.getAllWithFilters(
                                null, null, null, null, null, null, null, pageable, null);

                @SuppressWarnings("unchecked")
                List<CandidateGetAllResponseDTO> dtos = (List<CandidateGetAllResponseDTO>) result.getResult();
                assertThat(dtos).hasSize(2);
                verify(jobService, never()).getJobPositionsByIdsSimple(anyList(), any());
        }

        @Test
        @DisplayName("CS-TC-057: getAllWithFilters - enrich: map thiếu jp -> body null; JSON không title; rr null / không departmentId")
        void coverage_getAllWithFilters_enrichPartialJsonNodes() {
                // Test Case ID: CS-TC-057
                Pageable pageable = PageRequest.of(0, 10);
                Candidate c103 = buildCandidate(4L, "D", "d@gmail.com", 103L);
                Page<Candidate> page = new PageImpl<>(List.of(candidate1, c103), pageable, 2);
                when(candidateRepository.findByFilters(
                                eq(null), eq(null), eq(null),
                                any(LocalDate.class), any(LocalDate.class),
                                eq(null), eq(null), eq(pageable)))
                                .thenReturn(page);

                ObjectNode onlyDeptNoTitle = objectMapper.createObjectNode();
                ObjectNode rr1 = objectMapper.createObjectNode();
                rr1.put("departmentId", 7L);
                onlyDeptNoTitle.set("recruitmentRequest", rr1);

                ObjectNode titleOnly = objectMapper.createObjectNode();
                titleOnly.put("title", "Title Only");

                when(jobService.getJobPositionsByIdsSimple(anyList(), eq(token)))
                                .thenReturn(Map.of(
                                                101L, onlyDeptNoTitle,
                                                103L, titleOnly));

                PaginationDTO result = candidateService.getAllWithFilters(
                                null, null, null,
                                "2026-01-01", "2026-01-31", null,
                                null, pageable, token);

                @SuppressWarnings("unchecked")
                List<CandidateGetAllResponseDTO> dtos = (List<CandidateGetAllResponseDTO>) result.getResult();
                assertThat(dtos).hasSize(2);
                CandidateGetAllResponseDTO dto101 = dtos.stream()
                                .filter(d -> d.getJobPositionId().equals(101L)).findFirst().orElseThrow();
                assertThat(dto101.getJobPositionTitle()).isNull();
                assertThat(dto101.getDepartmentId()).isEqualTo(7L);
        }

        @Test
        @DisplayName("CS-TC-058: getCandidateDetailById - 2xx nhưng body null; object không phải array và không có data")
        void coverage_getCandidateDetailById_schedulesNoArrayNoData() throws Exception {
                // Test Case ID: CS-TC-058
                Candidate existing = buildCandidate(96L, "A", "sch2@x.com", 101L);
                existing.setComments(null);
                when(candidateRepository.findById(96L)).thenReturn(Optional.of(existing));
                when(reviewCandidateService.getByCandidateId(96L, token)).thenReturn(List.of());
                when(jobService.getJobPositionById(101L, token))
                                .thenReturn(ResponseEntity.ok(buildJobPositionJson(101L, "JP", 1L)));

                ObjectNode body = objectMapper.createObjectNode().put("message", "ok");
                when(communicationService.getUpcomingSchedulesForCandidate(96L, token))
                                .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

                CandidateDetailResponseDTO dto = candidateService.getCandidateDetailById(96L, token);
                assertThat(dto.getUpcomingSchedules()).isNull();

                when(communicationService.getUpcomingSchedulesForCandidate(96L, token))
                                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));
                CandidateDetailResponseDTO dto2 = candidateService.getCandidateDetailById(96L, token);
                assertThat(dto2.getUpcomingSchedules()).isNull();
        }

        @Test
        @DisplayName("CS-TC-059: getDepartmentIdByCandidateId - jobPositionId null; rr không có departmentId")
        void coverage_getDepartmentIdByCandidateId_edgeBranches() {
                // Test Case ID: CS-TC-059
                Candidate noJp = buildCandidate(97L, "A", "njp@x.com", null);
                when(candidateRepository.findById(97L)).thenReturn(Optional.of(noJp));
                assertThat(candidateService.getDepartmentIdByCandidateId(97L, token)).isNull();

                Candidate ok = buildCandidate(98L, "B", "b@x.com", 101L);
                when(candidateRepository.findById(98L)).thenReturn(Optional.of(ok));
                ObjectNode jp = objectMapper.createObjectNode();
                jp.set("recruitmentRequest", objectMapper.createObjectNode());
                when(jobService.getJobPositionByIdSimple(101L, token)).thenReturn(ResponseEntity.ok(jp));
                assertThat(candidateService.getDepartmentIdByCandidateId(98L, token)).isNull();
        }

        @Test
        @DisplayName("CS-TC-060: getCandidatesByInterviewer - token null; candidate jobPositionId null; map thiếu jp")
        void coverage_getCandidatesByInterviewer_tokenNull_nullJp_partialMap() {
                // Test Case ID: CS-TC-060
                Candidate noJp = buildCandidate(99L, "NJ", "nj@x.com", null);
                when(communicationService.getCandidateIdsByInterviewer(20L, null)).thenReturn(List.of(1L, 99L));
                when(candidateRepository.findAllById(List.of(1L, 99L))).thenReturn(List.of(candidate1, noJp));

                List<CandidateGetAllResponseDTO> out = candidateService.getCandidatesByInterviewer(20L, null);
                assertThat(out).hasSize(2);
                verify(jobService, never()).getJobPositionsByIdsSimple(anyList(), any());

                when(communicationService.getCandidateIdsByInterviewer(21L, token)).thenReturn(List.of(1L));
                when(candidateRepository.findAllById(List.of(1L))).thenReturn(List.of(candidate1));
                when(jobService.getJobPositionsByIdsSimple(anyList(), eq(token)))
                                .thenReturn(Map.of());
                assertThat(candidateService.getCandidatesByInterviewer(21L, token)).hasSize(1);
        }

        @Test
        @DisplayName("CS-TC-061: convertCandidateToEmployee - wrap data + departmentId từ recruitmentRequest; job response không 2xx")
        void coverage_convertCandidateToEmployee_wrappedRecruitmentRequest_andNon2xxJob() throws Exception {
                // Test Case ID: CS-TC-061
                Candidate existing = buildCandidate(100L, "A", "wraprr@x.com", 101L);
                when(candidateRepository.findById(100L)).thenReturn(Optional.of(existing));

                ObjectNode wrapped = objectMapper.createObjectNode();
                ObjectNode data = objectMapper.createObjectNode();
                ObjectNode rr = objectMapper.createObjectNode();
                rr.put("departmentId", 77L);
                data.set("recruitmentRequest", rr);
                wrapped.set("data", data);
                when(jobService.getJobPositionById(101L, token)).thenReturn(ResponseEntity.ok(wrapped));

                ObjectNode employeeNode = objectMapper.createObjectNode().put("employeeId", 10000);
                when(userService.createEmployeeFromCandidate(
                                eq(100L), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                                eq(77L), eq(3L), any(), eq(token)))
                                .thenReturn(new ResponseEntity<>(employeeNode, HttpStatus.OK));

                JsonNode ok = candidateService.convertCandidateToEmployee(100L, null, 3L, token);
                assertThat(ok.get("employeeId").asInt()).isEqualTo(10000);

                when(candidateRepository.findById(101L)).thenReturn(Optional.of(buildCandidate(101L, "B", "b@x.com", 105L)));
                when(jobService.getJobPositionById(105L, token))
                                .thenReturn(new ResponseEntity<>(objectMapper.createObjectNode(), HttpStatus.BAD_REQUEST));
                assertThatThrownBy(() -> candidateService.convertCandidateToEmployee(101L, null, 3L, token))
                                .isInstanceOf(IdInvalidException.class)
                                .hasMessageContaining("Department ID là bắt buộc");
        }

        @Test
        @DisplayName("CS-TC-062: convertCandidateToEmployee - try/catch nuốt lỗi job-service")
        void coverage_convertCandidateToEmployee_jobServiceThrows_swallowed() {
                // Test Case ID: CS-TC-062
                Candidate existing = buildCandidate(102L, "C", "c@x.com", 106L);
                when(candidateRepository.findById(102L)).thenReturn(Optional.of(existing));
                when(jobService.getJobPositionById(106L, token)).thenThrow(new RuntimeException("network"));

                assertThatThrownBy(() -> candidateService.convertCandidateToEmployee(102L, null, 3L, token))
                                .isInstanceOf(IdInvalidException.class)
                                .hasMessageContaining("Department ID là bắt buộc");
        }

        @Test
        @DisplayName("CS-TC-063: convertCandidateToEmployee - 2xx nhưng body null -> không fallback department")
        void coverage_convertCandidateToEmployee_job2xxNullBody() {
                // Test Case ID: CS-TC-063
                Candidate existing = buildCandidate(103L, "D", "d@x.com", 107L);
                when(candidateRepository.findById(103L)).thenReturn(Optional.of(existing));
                when(jobService.getJobPositionById(107L, token)).thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

                assertThatThrownBy(() -> candidateService.convertCandidateToEmployee(103L, null, 3L, token))
                                .isInstanceOf(IdInvalidException.class)
                                .hasMessageContaining("Department ID là bắt buộc");
        }

        @Test
        @DisplayName("CS-TC-064: getCandidatesForStatistics - parse ngày lỗi + token rỗng + candidate jobPositionId null")
        void coverage_getCandidatesForStatistics_invalidDates_emptyToken_nullJp() {
                // Test Case ID: CS-TC-064
                Pageable pageable = PageRequest.of(0, 10000);
                Candidate noJp = buildCandidate(200L, "NP", "np@x.com", null);
                Page<Candidate> page = new PageImpl<>(List.of(candidate1, noJp), pageable, 2);
                when(candidateRepository.findByFilters(
                                eq(null), eq(null), eq(null),
                                eq(null), eq(null),
                                eq(null), eq(null), any(Pageable.class)))
                                .thenReturn(page);

                List<CandidateStatisticsDTO> emptyToken = candidateService.getCandidatesForStatistics(
                                null, "not-a-date", "also-bad", null, null, "");
                assertThat(emptyToken).hasSize(2);
                verify(jobService, never()).getJobPositionsByIds(anyList(), any());
        }

        @Test
        @DisplayName("CS-TC-065: getCandidatesForStatistics - job JSON chỉ có title (không departmentId)")
        void coverage_getCandidatesForStatistics_titleOnly_noDepartmentInMap() {
                // Test Case ID: CS-TC-065
                Pageable pageable = PageRequest.of(0, 10000);
                Page<Candidate> page = new PageImpl<>(List.of(candidate1), pageable, 1);
                when(candidateRepository.findByFilters(
                                eq(null), eq(null), eq(null),
                                any(LocalDate.class), any(LocalDate.class),
                                eq(null), eq(null), any(Pageable.class)))
                                .thenReturn(page);
                when(jobService.getJobPositionsByIds(anyList(), eq(token)))
                                .thenReturn(Map.of(101L, objectMapper.createObjectNode().put("title", "only-title")));

                List<CandidateStatisticsDTO> mapped = candidateService.getCandidatesForStatistics(
                                null, "2026-01-01", "2026-01-31", null, null, token);
                assertThat(mapped).hasSize(1);
                assertThat(mapped.get(0).getDepartmentId()).isNull();
        }

        @Test
        @DisplayName("CS-TC-066: getCandidatesForStatistics - filter department loại candidate không map được dept")
        void coverage_getCandidatesForStatistics_filterDepartment_skipsUnknownDept() {
                // Test Case ID: CS-TC-066
                Pageable pageable = PageRequest.of(0, 10000);
                Page<Candidate> page = new PageImpl<>(List.of(candidate1, candidate2), pageable, 2);
                when(candidateRepository.findByFilters(
                                eq(null), eq(null), eq(null),
                                any(LocalDate.class), any(LocalDate.class),
                                eq(null), eq(null), any(Pageable.class)))
                                .thenReturn(page);
                when(jobService.getJobPositionsByIds(anyList(), eq(token)))
                                .thenReturn(Map.of(
                                                101L, buildJobPositionJson(101L, "J", 10L),
                                                102L, objectMapper.createObjectNode().put("title", "no-rr")));

                List<CandidateStatisticsDTO> filtered = candidateService.getCandidatesForStatistics(
                                null, "2026-01-01", "2026-01-31", null, 10L, token);
                assertThat(filtered).hasSize(1);
                assertThat(filtered.get(0).getJobPositionId()).isEqualTo(101L);
                assertThat(filtered.get(0).getDepartmentId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("CS-TC-067: update - set GPA (BigDecimal)")
        void coverage_update_setsGpa() throws Exception {
                // Test Case ID: CS-TC-067
                Candidate existing = buildCandidate(105L, "G", "g@x.com", 101L);
                when(candidateRepository.findById(105L)).thenReturn(Optional.of(existing));
                when(candidateRepository.save(any(Candidate.class))).thenAnswer(inv -> inv.getArgument(0, Candidate.class));
                UpdateCandidateDTO dto = new UpdateCandidateDTO();
                dto.setGpa(new BigDecimal("3.50"));
                CandidateDetailResponseDTO out = candidateService.update(105L, dto);
                assertThat(out.getGpa()).isEqualByComparingTo("3.50");
        }

        @Test
        @DisplayName("CS-TC-068: CandidateService constructor - khởi tạo thủ công (field injection không gọi <init>)")
        void coverage_candidateService_constructor() {
                // Test Case ID: CS-TC-068
                CandidateService svc = new CandidateService(
                                candidateRepository,
                                jobService,
                                communicationService,
                                commentService,
                                reviewCandidateService,
                                userService);
                assertThat(svc).isNotNull();
        }

        @Test
        @DisplayName("CS-TC-069: getAllWithFilters - uniqueJobIds rỗng nhưng token khác null/rỗng (full evaluate điều kiện &&)")
        void coverage_getAllWithFilters_uniqueJobIdsEmpty_nonEmptyToken() {
                // Test Case ID: CS-TC-069
                Candidate noJp = buildCandidate(300L, "Z", "z@gmail.com", null);
                Pageable pageable = PageRequest.of(0, 5);
                Page<Candidate> page = new PageImpl<>(List.of(noJp), pageable, 1);
                when(candidateRepository.findByFilters(
                                eq(null), eq(null), eq(null),
                                eq(null), eq(null),
                                eq(null), eq(null), eq(pageable)))
                                .thenReturn(page);

                PaginationDTO result = candidateService.getAllWithFilters(
                                null, null, null, null, null, null, null, pageable, token);

                assertThat(result.getMeta().getTotal()).isEqualTo(1);
                verify(jobService, never()).getJobPositionsByIdsSimple(anyList(), any());
        }

        @Test
        @DisplayName("CS-TC-070: getAllWithFilters - recruitmentRequest JSON null; departmentId JSON null")
        void coverage_getAllWithFilters_enrichRecruitmentRequestNull_andDeptIdNull() {
                // Test Case ID: CS-TC-070
                Pageable pageable = PageRequest.of(0, 10);
                Page<Candidate> page = new PageImpl<>(List.of(candidate1, candidate2), pageable, 2);
                when(candidateRepository.findByFilters(
                                eq(null), eq(null), eq(null),
                                any(LocalDate.class), any(LocalDate.class),
                                eq(null), eq(null), eq(pageable)))
                                .thenReturn(page);

                ObjectNode rrNull = objectMapper.createObjectNode();
                rrNull.put("title", "T1");
                rrNull.set("recruitmentRequest", NullNode.instance);

                ObjectNode deptNull = objectMapper.createObjectNode();
                deptNull.put("title", "T2");
                ObjectNode rr = objectMapper.createObjectNode();
                rr.set("departmentId", NullNode.instance);
                deptNull.set("recruitmentRequest", rr);

                when(jobService.getJobPositionsByIdsSimple(anyList(), eq(token)))
                                .thenReturn(Map.of(101L, rrNull, 102L, deptNull));

                PaginationDTO result = candidateService.getAllWithFilters(
                                null, null, null,
                                "2026-01-01", "2026-01-31", null,
                                null, pageable, token);

                @SuppressWarnings("unchecked")
                List<CandidateGetAllResponseDTO> dtos = (List<CandidateGetAllResponseDTO>) result.getResult();
                assertThat(dtos).extracting(CandidateGetAllResponseDTO::getDepartmentId).containsOnlyNulls();
        }

        @Test
        @DisplayName("CS-TC-071: getCandidateDetailById - candidate.id null -> không gọi schedule-service")
        void coverage_getCandidateDetailById_nullCandidateId_skipsSchedules() throws Exception {
                // Test Case ID: CS-TC-071
                Candidate existing = buildCandidate(null, "Noid", "noid@x.com", null);
                existing.setComments(null);
                when(candidateRepository.findById(400L)).thenReturn(Optional.of(existing));
                when(reviewCandidateService.getByCandidateId(null, token)).thenReturn(List.of());

                CandidateDetailResponseDTO dto = candidateService.getCandidateDetailById(400L, token);

                assertThat(dto.getId()).isNull();
                verify(communicationService, never()).getUpcomingSchedulesForCandidate(any(), any());
        }

        @Test
        @DisplayName("CS-TC-072: getDepartmentIdByCandidateId - 2xx nhưng body null")
        void coverage_getDepartmentIdByCandidateId_okButNullBody() {
                // Test Case ID: CS-TC-072
                Candidate existing = buildCandidate(401L, "A", "b401@x.com", 101L);
                when(candidateRepository.findById(401L)).thenReturn(Optional.of(existing));
                when(jobService.getJobPositionByIdSimple(101L, token))
                                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));
                assertThat(candidateService.getDepartmentIdByCandidateId(401L, token)).isNull();
        }

        @Test
        @DisplayName("CS-TC-073: getDepartmentIdByCandidateId - recruitmentRequest có departmentId null JSON")
        void coverage_getDepartmentIdByCandidateId_departmentIdJsonNull() {
                // Test Case ID: CS-TC-073
                Candidate existing = buildCandidate(402L, "A", "b402@x.com", 101L);
                when(candidateRepository.findById(402L)).thenReturn(Optional.of(existing));
                ObjectNode jp = objectMapper.createObjectNode();
                ObjectNode rr = objectMapper.createObjectNode();
                rr.set("departmentId", NullNode.instance);
                jp.set("recruitmentRequest", rr);
                when(jobService.getJobPositionByIdSimple(101L, token)).thenReturn(ResponseEntity.ok(jp));
                assertThat(candidateService.getDepartmentIdByCandidateId(402L, token)).isNull();
        }

        @Test
        @DisplayName("CS-TC-074: getCandidatesByInterviewer - chỉ candidate jobPositionId null -> không gọi job-service")
        void coverage_getCandidatesByInterviewer_onlyNullJobPositionIds() {
                // Test Case ID: CS-TC-074
                Candidate noJp = buildCandidate(403L, "NJ", "nj403@x.com", null);
                when(communicationService.getCandidateIdsByInterviewer(22L, token)).thenReturn(List.of(403L));
                when(candidateRepository.findAllById(List.of(403L))).thenReturn(List.of(noJp));

                List<CandidateGetAllResponseDTO> out = candidateService.getCandidatesByInterviewer(22L, token);
                assertThat(out).hasSize(1);
                verify(jobService, never()).getJobPositionsByIdsSimple(anyList(), any());
        }

        @Test
        @DisplayName("CS-TC-075: convertCandidateToEmployee - candidate không có jobPositionId -> không gọi job-service")
        void coverage_convertCandidateToEmployee_noJobPositionId_skipsJobFetch() throws Exception {
                // Test Case ID: CS-TC-075
                Candidate existing = buildCandidate(404L, "NP", "np404@x.com", null);
                when(candidateRepository.findById(404L)).thenReturn(Optional.of(existing));
                ObjectNode employeeNode = objectMapper.createObjectNode().put("employeeId", 40400);
                when(userService.createEmployeeFromCandidate(
                                eq(404L), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                                eq(8L), eq(4L), any(), eq(token)))
                                .thenReturn(new ResponseEntity<>(employeeNode, HttpStatus.OK));

                JsonNode res = candidateService.convertCandidateToEmployee(404L, 8L, 4L, token);
                assertThat(res.get("employeeId").asInt()).isEqualTo(40400);
                verify(jobService, never()).getJobPositionById(anyLong(), any());
        }

        @Test
        @DisplayName("CS-TC-076: convertCandidateToEmployee - đã có departmentId param, thiếu positionId -> vẫn parse job nhưng không lấy dept từ JSON; fail vì thiếu position")
        void coverage_convertCandidateToEmployee_paramDept_skipsJobDeptExtraction_missingPosition() {
                // Test Case ID: CS-TC-076
                Candidate existing = buildCandidate(405L, "X", "x405@x.com", 110L);
                when(candidateRepository.findById(405L)).thenReturn(Optional.of(existing));

                ObjectNode jobWithDept = objectMapper.createObjectNode();
                jobWithDept.put("departmentId", 999L);
                when(jobService.getJobPositionById(110L, token)).thenReturn(ResponseEntity.ok(jobWithDept));

                assertThatThrownBy(() -> candidateService.convertCandidateToEmployee(405L, 12L, null, token))
                                .isInstanceOf(IdInvalidException.class)
                                .hasMessageContaining("Position ID là bắt buộc");
                verify(jobService, times(1)).getJobPositionById(110L, token);
                verify(userService, never()).createEmployeeFromCandidate(
                                anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                                anyLong(), anyLong(), any(), any());
        }

        @Test
        @DisplayName("CS-TC-077: getCandidatesForStatistics - startDate null / endDate null (nhánh parse)")
        void coverage_getCandidatesForStatistics_nullDates() {
                // Test Case ID: CS-TC-077
                Pageable pageable = PageRequest.of(0, 10000);
                Page<Candidate> page = new PageImpl<>(List.of(candidate1), pageable, 1);
                when(candidateRepository.findByFilters(
                                eq(null), eq(null), eq(null),
                                any(), any(),
                                eq(null), eq(null), any(Pageable.class)))
                                .thenReturn(page);

                candidateService.getCandidatesForStatistics(null, null, "2026-01-31", null, null, "");
                candidateService.getCandidatesForStatistics(null, "2026-01-01", null, null, null, "");
                verify(candidateRepository, times(2)).findByFilters(
                                eq(null), eq(null), eq(null),
                                any(), any(),
                                eq(null), eq(null), any(Pageable.class));
        }

        @Test
        @DisplayName("CS-TC-078: getCandidatesForStatistics - uniqueJobIds rỗng + token khác rỗng")
        void coverage_getCandidatesForStatistics_emptyJobIds_nonEmptyToken() {
                // Test Case ID: CS-TC-078
                Pageable pageable = PageRequest.of(0, 10000);
                Candidate noJp = buildCandidate(406L, "E", "e406@x.com", null);
                Page<Candidate> page = new PageImpl<>(List.of(noJp), pageable, 1);
                when(candidateRepository.findByFilters(
                                eq(null), eq(null), eq(null),
                                any(LocalDate.class), any(LocalDate.class),
                                eq(null), eq(null), any(Pageable.class)))
                                .thenReturn(page);

                List<CandidateStatisticsDTO> stats = candidateService.getCandidatesForStatistics(
                                null, "2026-01-01", "2026-01-31", null, null, token);
                assertThat(stats).hasSize(1);
                verify(jobService, never()).getJobPositionsByIds(anyList(), any());
        }

        @Test
        @DisplayName("CS-TC-079: getCandidatesForStatistics - startDate/endDate chuỗi rỗng (không parse)")
        void coverage_getCandidatesForStatistics_emptyStringDates() {
                // Test Case ID: CS-TC-079
                Pageable pageable = PageRequest.of(0, 10000);
                Page<Candidate> page = new PageImpl<>(List.of(candidate1), pageable, 1);
                when(candidateRepository.findByFilters(
                                eq(null), eq(null), eq(null),
                                any(), any(),
                                eq(null), eq(null), any(Pageable.class)))
                                .thenReturn(page);
                candidateService.getCandidatesForStatistics(null, "", "2026-01-31", null, null, "");
                candidateService.getCandidatesForStatistics(null, "2026-01-01", "", null, null, "");
                verify(candidateRepository, times(2)).findByFilters(
                                eq(null), eq(null), eq(null),
                                any(), any(),
                                eq(null), eq(null), any(Pageable.class));
        }

        @Test
        @DisplayName("CS-TC-080: getCandidatesForStatistics - token null nhưng có jobPositionId -> không gọi job-service")
        void coverage_getCandidatesForStatistics_tokenNull_skipsJobMap() {
                // Test Case ID: CS-TC-080
                Pageable pageable = PageRequest.of(0, 10000);
                Page<Candidate> page = new PageImpl<>(List.of(candidate1), pageable, 1);
                when(candidateRepository.findByFilters(
                                eq(null), eq(null), eq(null),
                                any(LocalDate.class), any(LocalDate.class),
                                eq(null), eq(null), any(Pageable.class)))
                                .thenReturn(page);
                List<CandidateStatisticsDTO> stats = candidateService.getCandidatesForStatistics(
                                null, "2026-01-01", "2026-01-31", null, null, null);
                assertThat(stats).hasSize(1);
                verify(jobService, never()).getJobPositionsByIds(anyList(), any());
        }

        @Test
        @DisplayName("CS-TC-081: getDepartmentIdByCandidateId - JSON không có field recruitmentRequest")
        void coverage_getDepartmentIdByCandidateId_noRecruitmentRequestField() {
                // Test Case ID: CS-TC-081
                Candidate existing = buildCandidate(407L, "A", "b407@x.com", 101L);
                when(candidateRepository.findById(407L)).thenReturn(Optional.of(existing));
                ObjectNode jp = objectMapper.createObjectNode().put("id", 101L).put("title", "T");
                when(jobService.getJobPositionByIdSimple(101L, token)).thenReturn(ResponseEntity.ok(jp));
                assertThat(candidateService.getDepartmentIdByCandidateId(407L, token)).isNull();
        }

        @Test
        @DisplayName("CS-TC-082: getCandidatesByInterviewer - token null, có jobPosition -> không enrich")
        void coverage_getCandidatesByInterviewer_tokenNull_skipsEnrich() {
                // Test Case ID: CS-TC-082
                when(communicationService.getCandidateIdsByInterviewer(23L, null)).thenReturn(List.of(1L));
                when(candidateRepository.findAllById(List.of(1L))).thenReturn(List.of(candidate1));
                List<CandidateGetAllResponseDTO> out = candidateService.getCandidatesByInterviewer(23L, null);
                assertThat(out.get(0).getJobPositionTitle()).isNull();
                verify(jobService, never()).getJobPositionsByIdsSimple(anyList(), any());
        }

        @Test
        @DisplayName("CS-TC-083: convertCandidateToEmployee - departmentId null từ param (short-circuit ||), position có -> parse job")
        void coverage_convertCandidateToEmployee_nullDeptParam_shortCircuitOr() throws Exception {
                // Test Case ID: CS-TC-083
                Candidate existing = buildCandidate(408L, "Y", "y408@x.com", 111L);
                when(candidateRepository.findById(408L)).thenReturn(Optional.of(existing));
                ObjectNode job = objectMapper.createObjectNode();
                job.put("departmentId", 44L);
                when(jobService.getJobPositionById(111L, token)).thenReturn(ResponseEntity.ok(job));
                ObjectNode employeeNode = objectMapper.createObjectNode().put("employeeId", 40800);
                when(userService.createEmployeeFromCandidate(
                                eq(408L), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                                eq(44L), eq(6L), any(), eq(token)))
                                .thenReturn(new ResponseEntity<>(employeeNode, HttpStatus.OK));
                JsonNode res = candidateService.convertCandidateToEmployee(408L, null, 6L, token);
                assertThat(res.get("employeeId").asInt()).isEqualTo(40800);
        }

        @Test
        @DisplayName("CS-TC-084: getCandidatesForStatistics - loop map: deptId null -> không put idToDepartmentId")
        void coverage_getCandidatesForStatistics_deptIdNull_skipPut() {
                // Test Case ID: CS-TC-084
                Pageable pageable = PageRequest.of(0, 10000);
                Page<Candidate> page = new PageImpl<>(List.of(candidate1), pageable, 1);
                when(candidateRepository.findByFilters(
                                eq(null), eq(null), eq(null),
                                any(LocalDate.class), any(LocalDate.class),
                                eq(null), eq(null), any(Pageable.class)))
                                .thenReturn(page);
                ObjectNode bodyNoDept = objectMapper.createObjectNode();
                ObjectNode rr = objectMapper.createObjectNode();
                bodyNoDept.set("recruitmentRequest", rr);
                when(jobService.getJobPositionsByIds(anyList(), eq(token)))
                                .thenReturn(Map.of(101L, bodyNoDept));
                List<CandidateStatisticsDTO> stats = candidateService.getCandidatesForStatistics(
                                null, "2026-01-01", "2026-01-31", null, null, token);
                assertThat(stats.get(0).getDepartmentId()).isNull();
        }

        @Test
        @DisplayName("CS-TC-085: getCandidatesByInterviewer - body null / không title trong map job positions")
        void coverage_getCandidatesByInterviewer_partialJobBodies() {
                // Test Case ID: CS-TC-085
                when(communicationService.getCandidateIdsByInterviewer(24L, token)).thenReturn(List.of(1L, 2L));
                when(candidateRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(candidate1, candidate2));
                ObjectNode empty = objectMapper.createObjectNode();
                ObjectNode noTitle = (ObjectNode) buildJobPositionJson(102L, "QA", 11L);
                noTitle.remove("title");
                when(jobService.getJobPositionsByIdsSimple(anyList(), eq(token)))
                                .thenReturn(Map.of(
                                                101L, empty,
                                                102L, noTitle));
                List<CandidateGetAllResponseDTO> out = candidateService.getCandidatesByInterviewer(24L, token);
                assertThat(out).hasSize(2);
                assertThat(out.stream().filter(d -> d.getJobPositionId().equals(101L)).findFirst().orElseThrow()
                                .getJobPositionTitle()).isNull();
        }

        @Test
        @DisplayName("CS-TC-086: convertCandidateToEmployee - jobPosition không có departmentId root nhưng có recruitmentRequest (else-if)")
        void coverage_convertCandidateToEmployee_onlyRecruitmentRequestDept() throws Exception {
                // Test Case ID: CS-TC-086
                Candidate existing = buildCandidate(409L, "Z", "z409@x.com", 112L);
                when(candidateRepository.findById(409L)).thenReturn(Optional.of(existing));
                ObjectNode job = objectMapper.createObjectNode();
                ObjectNode rr = objectMapper.createObjectNode();
                rr.put("departmentId", 88L);
                job.set("recruitmentRequest", rr);
                when(jobService.getJobPositionById(112L, token)).thenReturn(ResponseEntity.ok(job));
                ObjectNode employeeNode = objectMapper.createObjectNode().put("employeeId", 40900);
                when(userService.createEmployeeFromCandidate(
                                eq(409L), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                                eq(88L), eq(7L), any(), eq(token)))
                                .thenReturn(new ResponseEntity<>(employeeNode, HttpStatus.OK));
                JsonNode res = candidateService.convertCandidateToEmployee(409L, null, 7L, token);
                assertThat(res.get("employeeId").asInt()).isEqualTo(40900);
        }

        @Test
        @DisplayName("CS-TC-087: getCandidatesForStatistics - job JSON có recruitmentRequest null; rr không có departmentId")
        void coverage_getCandidatesForStatistics_recruitmentRequestVariants() {
                // Test Case ID: CS-TC-087
                Pageable pageable = PageRequest.of(0, 10000);
                Page<Candidate> page = new PageImpl<>(List.of(candidate1, candidate2), pageable, 2);
                when(candidateRepository.findByFilters(
                                eq(null), eq(null), eq(null),
                                any(LocalDate.class), any(LocalDate.class),
                                eq(null), eq(null), any(Pageable.class)))
                                .thenReturn(page);

                ObjectNode rrMissingDept = objectMapper.createObjectNode();
                rrMissingDept.put("foo", "bar");
                ObjectNode j101 = objectMapper.createObjectNode();
                j101.set("recruitmentRequest", rrMissingDept);

                ObjectNode j102 = objectMapper.createObjectNode();
                j102.set("recruitmentRequest", NullNode.instance);

                when(jobService.getJobPositionsByIds(anyList(), eq(token)))
                                .thenReturn(Map.of(101L, j101, 102L, j102));

                List<CandidateStatisticsDTO> stats = candidateService.getCandidatesForStatistics(
                                null, "2026-01-01", "2026-01-31", null, null, token);
                assertThat(stats).hasSize(2);
                assertThat(stats).extracting(CandidateStatisticsDTO::getDepartmentId).containsOnlyNulls();
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
