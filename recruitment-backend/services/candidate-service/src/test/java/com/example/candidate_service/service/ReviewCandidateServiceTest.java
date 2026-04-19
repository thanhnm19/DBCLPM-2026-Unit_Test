package com.example.candidate_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.candidate_service.dto.PaginationDTO;
import com.example.candidate_service.dto.review.CreateReviewCandidateDTO;
import com.example.candidate_service.dto.review.ReviewCandidateResponseDTO;
import com.example.candidate_service.dto.review.UpdateReviewCandidateDTO;
import com.example.candidate_service.exception.IdInvalidException;
import com.example.candidate_service.model.Candidate;
import com.example.candidate_service.model.ReviewCandidate;
import com.example.candidate_service.repository.CandidateRepository;
import com.example.candidate_service.repository.ReviewCandidateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewCandidateService Unit Test")
class ReviewCandidateServiceTest {

    // ===== Mocks & SUT =====
    @Mock
    private ReviewCandidateRepository reviewCandidateRepository;

    @Mock
    private CandidateRepository candidateRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private ReviewCandidateService reviewCandidateService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ===== Helpers =====
    private Candidate candidate(Long id) {
        Candidate c = new Candidate();
        c.setId(id);
        return c;
    }

    private ReviewCandidate review(Long id, Long candidateId, Long reviewerId,
            Integer pro, Integer comm, Integer exp,
            String strengths, String weaknesses, Boolean conclusion) {
        ReviewCandidate r = new ReviewCandidate();
        r.setId(id);
        r.setCandidate(candidate(candidateId));
        r.setReviewerId(reviewerId);
        r.setProfessionalSkillScore(pro);
        r.setCommunicationSkillScore(comm);
        r.setWorkExperienceScore(exp);
        r.setStrengths(strengths);
        r.setWeaknesses(weaknesses);
        r.setConclusion(conclusion);
        r.setCreatedAt(LocalDateTime.now().minusDays(1));
        r.setUpdatedAt(LocalDateTime.now());
        return r;
    }

    private ResponseEntity<JsonNode> userServiceOkResponse(String json) throws Exception {
        JsonNode body = objectMapper.readTree(json);
        return new ResponseEntity<>(body, HttpStatus.OK);
    }

    // =========================================================
    // RCS-TC01
    // =========================================================
    @Test
    @DisplayName("RCS-TC01: getAllWithFilters - lọc đúng danh sách review, phân trang đúng và enrich dữ liệu đúng")
    void RCS_TC01_getAllWithFilters_shouldFilterPaginateAndEnrichCorrectly() throws Exception {
        // Arrange
        Long candidateId = 1L;
        Long reviewerId = 10L;
        LocalDateTime start = LocalDateTime.now().minusDays(7);
        LocalDateTime end = LocalDateTime.now();
        int page = 2;
        int limit = 5;
        String sortBy = "createdAt";
        String sortOrder = "desc";
        String token = "Bearer token";

        ReviewCandidate r1 = review(100L, candidateId, reviewerId, 4, 3, 5, "S1", "W1", true);
        ReviewCandidate r2 = review(101L, candidateId, 11L, 2, 2, 2, "S2", "W2", false);
        List<ReviewCandidate> content = List.of(r1, r2);

        Page<ReviewCandidate> mockPage = new PageImpl<>(content,
                org.springframework.data.domain.PageRequest.of(page - 1, limit), 12);

        when(reviewCandidateRepository.findByFilters(eq(candidateId), eq(reviewerId), eq(start), eq(end),
                any(Pageable.class)))
                .thenReturn(mockPage);

        when(userService.getEmployeeNames(eq(List.of(reviewerId, 11L)), eq(token)))
                .thenReturn(userServiceOkResponse("{\"10\":\"Alice\",\"11\":\"Bob\"}"));

        // Act
        PaginationDTO result = reviewCandidateService.getAllWithFilters(candidateId, reviewerId, start, end, page,
                limit, sortBy, sortOrder, token);

        // Assert - meta
        assertThat(result).isNotNull();
        assertThat(result.getMeta()).isNotNull();
        assertThat(result.getMeta().getPage()).isEqualTo(page);
        assertThat(result.getMeta().getPageSize()).isEqualTo(limit);
        assertThat(result.getMeta().getTotal()).isEqualTo(12);
        assertThat(result.getMeta().getPages()).isEqualTo(mockPage.getTotalPages());

        // Assert - list & enrich
        @SuppressWarnings("unchecked")
        List<ReviewCandidateResponseDTO> list = (List<ReviewCandidateResponseDTO>) result.getResult();
        assertThat(list).hasSize(2);

        ReviewCandidateResponseDTO dto1 = list.get(0);
        ReviewCandidateResponseDTO dto2 = list.get(1);

        assertThat(dto1.getId()).isEqualTo(100L);
        assertThat(dto1.getCandidateId()).isEqualTo(candidateId);
        assertThat(dto1.getReviewerId()).isEqualTo(reviewerId);
        assertThat(dto1.getReviewerName()).isEqualTo("Alice");
        assertThat(dto1.getAverageScore()).isEqualTo((4.0 + 3.0 + 5.0) / 3.0);

        assertThat(dto2.getId()).isEqualTo(101L);
        assertThat(dto2.getCandidateId()).isEqualTo(candidateId);
        assertThat(dto2.getReviewerId()).isEqualTo(11L);
        assertThat(dto2.getReviewerName()).isEqualTo("Bob");
        assertThat(dto2.getAverageScore()).isEqualTo((2.0 + 2.0 + 2.0) / 3.0);

        // Verify interactions (capture pageable)
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(reviewCandidateRepository, times(1))
                .findByFilters(eq(candidateId), eq(reviewerId), eq(start), eq(end), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(page - 1);
        assertThat(pageable.getPageSize()).isEqualTo(limit);

        verify(userService, times(1)).getEmployeeNames(eq(List.of(reviewerId, 11L)), eq(token));
    }

    // =========================================================
    // RCS-TC02
    // =========================================================
    @Test
    @DisplayName("RCS-TC02: getByCandidateId - lấy danh sách review theo candidateId và map reviewerName đúng")
    void RCS_TC02_getByCandidateId_shouldReturnListWithReviewerName() throws Exception {
        // Arrange
        Long candidateId = 2L;
        String token = "Bearer token";

        ReviewCandidate r1 = review(200L, candidateId, 21L, 5, 5, 4, "S", "W", true);
        ReviewCandidate r2 = review(201L, candidateId, 22L, 1, 2, 3, "S2", "W2", false);

        when(reviewCandidateRepository.findByCandidate_Id(candidateId)).thenReturn(List.of(r1, r2));
        when(userService.getEmployeeNames(eq(List.of(21L, 22L)), eq(token)))
                .thenReturn(userServiceOkResponse("{\"21\":\"U21\",\"22\":\"U22\"}"));

        // Act
        List<ReviewCandidateResponseDTO> result = reviewCandidateService.getByCandidateId(candidateId, token);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getReviewerName()).isEqualTo("U21");
        assertThat(result.get(1).getReviewerName()).isEqualTo("U22");

        verify(reviewCandidateRepository, times(1)).findByCandidate_Id(candidateId);
        verify(userService, times(1)).getEmployeeNames(eq(List.of(21L, 22L)), eq(token));
    }

    // =========================================================
    // RCS-TC03
    // =========================================================
    @Test
    @DisplayName("RCS-TC03: getById - id tồn tại trả ReviewCandidateDTO, id không tồn tại ném IdInvalidException")
    void RCS_TC03_getById_shouldReturnDtoWhenExists_andThrowWhenNotFound() throws Exception {
        // Arrange - exists
        Long id = 300L;
        Long candidateId = 3L;
        Long reviewerId = 31L;
        String token = "Bearer token";

        ReviewCandidate r = review(id, candidateId, reviewerId, 3, 4, 5, "S", "W", true);
        when(reviewCandidateRepository.findById(id)).thenReturn(Optional.of(r));
        when(userService.getEmployeeNames(eq(List.of(reviewerId)), eq(token)))
                .thenReturn(userServiceOkResponse("{\"31\":\"Reviewer31\"}"));

        // Act - exists
        ReviewCandidateResponseDTO dto = reviewCandidateService.getById(id, token);

        // Assert - exists
        assertThat(dto.getId()).isEqualTo(id);
        assertThat(dto.getCandidateId()).isEqualTo(candidateId);
        assertThat(dto.getReviewerId()).isEqualTo(reviewerId);
        assertThat(dto.getReviewerName()).isEqualTo("Reviewer31");
        assertThat(dto.getAverageScore()).isEqualTo((3.0 + 4.0 + 5.0) / 3.0);

        verify(reviewCandidateRepository, times(1)).findById(id);
        verify(userService, times(1)).getEmployeeNames(eq(List.of(reviewerId)), eq(token));

        // Arrange - not found
        Long notFoundId = 999L;
        when(reviewCandidateRepository.findById(notFoundId)).thenReturn(Optional.empty());

        // Act + Assert - not found
        assertThatThrownBy(() -> reviewCandidateService.getById(notFoundId, token))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("không tồn tại");

        verify(reviewCandidateRepository, times(1)).findById(notFoundId);
        verify(userService, times(1)).getEmployeeNames(eq(List.of(reviewerId)), eq(token));
    }

    // =========================================================
    // RCS-TC04
    // =========================================================
    @Test
    @DisplayName("RCS-TC04: create - dữ liệu hợp lệ -> tạo mới review và gán reviewerId đúng")
    void RCS_TC04_create_shouldCreateReviewAndAssignReviewerId() throws Exception {
        // Arrange
        Long reviewerId = 41L;
        Long candidateId = 4L;

        CreateReviewCandidateDTO dto = new CreateReviewCandidateDTO();
        dto.setCandidateId(candidateId);
        dto.setProfessionalSkillScore(4);
        dto.setCommunicationSkillScore(3);
        dto.setWorkExperienceScore(5);
        dto.setStrengths("S");
        dto.setWeaknesses("W");
        dto.setConclusion(true);

        Candidate candidate = candidate(candidateId);
        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));

        // Save returns entity with id
        when(reviewCandidateRepository.save(any(ReviewCandidate.class)))
                .thenAnswer(inv -> {
                    ReviewCandidate arg = inv.getArgument(0);
                    arg.setId(400L);
                    return arg;
                });

        // Act
        ReviewCandidateResponseDTO result = reviewCandidateService.create(dto, reviewerId);

        // Assert
        assertThat(result.getId()).isEqualTo(400L);
        assertThat(result.getCandidateId()).isEqualTo(candidateId);
        assertThat(result.getReviewerId()).isEqualTo(reviewerId);
        assertThat(result.getAverageScore()).isEqualTo((4.0 + 3.0 + 5.0) / 3.0);
        assertThat(result.getStrengths()).isEqualTo("S");
        assertThat(result.getWeaknesses()).isEqualTo("W");
        assertThat(result.getConclusion()).isTrue();

        // Verify save payload
        ArgumentCaptor<ReviewCandidate> captor = ArgumentCaptor.forClass(ReviewCandidate.class);
        verify(reviewCandidateRepository, times(1)).save(captor.capture());
        ReviewCandidate saved = captor.getValue();
        assertThat(saved.getReviewerId()).isEqualTo(reviewerId);
        assertThat(saved.getCandidate()).isNotNull();
        assertThat(saved.getCandidate().getId()).isEqualTo(candidateId);

        verify(candidateRepository, times(1)).findById(candidateId);
    }

    // =========================================================
    // RCS-TC05
    // =========================================================
    @Test
    @DisplayName("RCS-TC05: update - chỉ cập nhật các field có giá trị hợp lệ và trả về review đã cập nhật")
    void RCS_TC05_update_shouldUpdateOnlyProvidedFields() throws Exception {
        // Arrange
        Long id = 500L;
        Long reviewerId = 51L;

        ReviewCandidate existing = review(id, 5L, reviewerId, 1, 1, 1, "oldS", "oldW", true);
        when(reviewCandidateRepository.findById(id)).thenReturn(Optional.of(existing));

        UpdateReviewCandidateDTO dto = new UpdateReviewCandidateDTO();
        dto.setProfessionalSkillScore(4); // update
        dto.setCommunicationSkillScore(null); // keep
        dto.setWorkExperienceScore(3); // update
        dto.setStrengths("newS"); // update
        dto.setWeaknesses(null); // keep
        dto.setConclusion(false); // update

        when(reviewCandidateRepository.save(any(ReviewCandidate.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        ReviewCandidateResponseDTO result = reviewCandidateService.update(id, dto, reviewerId);

        // Assert
        assertThat(result.getId()).isEqualTo(id);
        assertThat(result.getReviewerId()).isEqualTo(reviewerId);
        assertThat(result.getProfessionalSkillScore()).isEqualTo(4);
        assertThat(result.getCommunicationSkillScore()).isEqualTo(1);
        assertThat(result.getWorkExperienceScore()).isEqualTo(3);
        assertThat(result.getStrengths()).isEqualTo("newS");
        assertThat(result.getWeaknesses()).isEqualTo("oldW");
        assertThat(result.getConclusion()).isFalse();

        // AverageScore only set when all 3 scores non-null (existing comm=1.0)
        assertThat(result.getAverageScore()).isEqualTo((4.0 + 1.0 + 3.0) / 3.0);

        verify(reviewCandidateRepository, times(1)).findById(id);
        verify(reviewCandidateRepository, times(1)).save(existing);
    }

    // =========================================================
    // RCS-TC06
    // =========================================================
    @Test
    @DisplayName("RCS-TC06: delete - id hợp lệ xóa thành công, id không tồn tại hoặc không hợp lệ ném exception")
    void RCS_TC06_delete_shouldDeleteWhenAuthorized_andThrowOtherwise() throws Exception {
        // Arrange - authorized
        Long id = 600L;
        Long reviewerId = 61L;
        ReviewCandidate existing = review(id, 6L, reviewerId, 1, 2, 3, "S", "W", true);
        when(reviewCandidateRepository.findById(id)).thenReturn(Optional.of(existing));

        // Act - authorized
        reviewCandidateService.delete(id, reviewerId);

        // Assert - authorized
        verify(reviewCandidateRepository, times(1)).deleteById(id);

        // Arrange - not found
        Long notFoundId = 601L;
        when(reviewCandidateRepository.findById(notFoundId)).thenReturn(Optional.empty());

        // Act + Assert - not found
        assertThatThrownBy(() -> reviewCandidateService.delete(notFoundId, reviewerId))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("không tồn tại");

        verify(reviewCandidateRepository, never()).deleteById(notFoundId);

        // Arrange - forbidden
        Long forbiddenId = 602L;
        ReviewCandidate forbidden = review(forbiddenId, 6L, 999L, 1, 2, 3, "S", "W", true);
        when(reviewCandidateRepository.findById(forbiddenId)).thenReturn(Optional.of(forbidden));

        // Act + Assert - forbidden
        assertThatThrownBy(() -> reviewCandidateService.delete(forbiddenId, reviewerId))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("quyền");

        verify(reviewCandidateRepository, never()).deleteById(forbiddenId);
    }

    // =========================================================
    // Additional coverage
    // =========================================================

    @Test
    @DisplayName("[COVER] getByCandidateId - empty list -> không gọi userService và trả về list rỗng")
    void cover_getByCandidateId_emptyList_shouldNotCallUserService() {
        // Arrange
        Long candidateId = 77L;
        when(reviewCandidateRepository.findByCandidate_Id(candidateId)).thenReturn(Collections.emptyList());

        // Act
        List<ReviewCandidateResponseDTO> result = reviewCandidateService.getByCandidateId(candidateId, "Bearer token");

        // Assert
        assertThat(result).isEmpty();
        verify(userService, never()).getEmployeeNames(any(), any());
    }

    @Test
    @DisplayName("[COVER] getAllWithFilters - token rỗng -> không gọi userService và reviewerName null")
    void cover_getAllWithFilters_emptyToken_shouldNotCallUserService() {
        // Arrange
        ReviewCandidate r1 = review(700L, 7L, 71L, 4, 4, 4, "S", "W", true);
        Page<ReviewCandidate> mockPage = new PageImpl<>(List.of(r1),
                org.springframework.data.domain.PageRequest.of(0, 10), 1);

        when(reviewCandidateRepository.findByFilters(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(mockPage);

        // Act
        PaginationDTO result = reviewCandidateService.getAllWithFilters(null, null, null, null, 1, 10, null, null, "");

        // Assert
        @SuppressWarnings("unchecked")
        List<ReviewCandidateResponseDTO> list = (List<ReviewCandidateResponseDTO>) result.getResult();
        assertThat(list).hasSize(1);
        ReviewCandidateResponseDTO dto = list.get(0);
        assertThat(dto.getReviewerName()).isNull();
        verify(userService, never()).getEmployeeNames(any(), any());
    }

    @Test
    @DisplayName("[COVER] getById - userService trả non-2xx -> vẫn trả DTO nhưng reviewerName null")
    void cover_getById_userServiceNon2xx_shouldNotEnrichName() throws Exception {
        // Arrange
        Long id = 800L;
        Long reviewerId = 81L;
        ReviewCandidate r = review(id, 8L, reviewerId, 2, 3, 4, "S", "W", true);
        when(reviewCandidateRepository.findById(id)).thenReturn(Optional.of(r));
        when(userService.getEmployeeNames(eq(List.of(reviewerId)), any()))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.BAD_REQUEST));

        // Act
        ReviewCandidateResponseDTO dto = reviewCandidateService.getById(id, "Bearer token");

        // Assert
        assertThat(dto.getReviewerId()).isEqualTo(reviewerId);
        assertThat(dto.getReviewerName()).isNull();
        verify(userService, times(1)).getEmployeeNames(eq(List.of(reviewerId)), any());
    }

    @Test
    @DisplayName("[COVER] update - reviewerId không khớp -> ném exception và không save")
    void cover_update_forbidden_shouldThrowAndNotSave() {
        // Arrange
        Long id = 900L;
        ReviewCandidate existing = review(id, 9L, 999L, 1, 1, 1, "S", "W", true);
        when(reviewCandidateRepository.findById(id)).thenReturn(Optional.of(existing));

        UpdateReviewCandidateDTO dto = new UpdateReviewCandidateDTO();
        dto.setStrengths("new");

        // Act + Assert
        assertThatThrownBy(() -> reviewCandidateService.update(id, dto, 91L))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("quyền");

        verify(reviewCandidateRepository, never()).save(any());
    }

    @Test
    @DisplayName("[COVER] create - candidateId null -> ném IdInvalidException và không gọi repository")
    void cover_create_candidateIdNull_shouldThrowAndNotSave() {
        // Arrange
        CreateReviewCandidateDTO dto = new CreateReviewCandidateDTO();
        dto.setCandidateId(null);

        // Act + Assert
        assertThatThrownBy(() -> reviewCandidateService.create(dto, 123L))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("bắt buộc");

        verify(candidateRepository, never()).findById(any());
        verify(reviewCandidateRepository, never()).save(any());
    }

    @Test
    @DisplayName("[COVER] create - candidate không tồn tại -> ném IdInvalidException và không save")
    void cover_create_candidateNotFound_shouldThrowAndNotSave() {
        // Arrange
        Long candidateId = 1000L;
        CreateReviewCandidateDTO dto = new CreateReviewCandidateDTO();
        dto.setCandidateId(candidateId);

        when(candidateRepository.findById(candidateId)).thenReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() -> reviewCandidateService.create(dto, 123L))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("không tồn tại");

        verify(reviewCandidateRepository, never()).save(any());
    }
}
