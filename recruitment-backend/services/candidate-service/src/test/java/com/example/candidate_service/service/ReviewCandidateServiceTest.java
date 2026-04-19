package com.example.candidate_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
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
import org.springframework.data.domain.PageRequest;
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

        @Mock
        private ReviewCandidateRepository reviewCandidateRepository;

        @Mock
        private CandidateRepository candidateRepository;

        @Mock
        private UserService userService;

        @InjectMocks
        private ReviewCandidateService reviewCandidateService;

        private final ObjectMapper objectMapper = new ObjectMapper();

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

        private ResponseEntity<JsonNode> okBody(String json) throws Exception {
                return new ResponseEntity<>(objectMapper.readTree(json), HttpStatus.OK);
        }

        @Test
        @DisplayName("RC-TC-001: getAllWithFilters - normalize page limit và enrich reviewerName")
        void testGetAllWithFilters_RC_TC_001() throws Exception {
                // Testcase ID: RC-TC-001
                // Objective: Xác nhận normalize page/limit và enrich reviewerName

                // arrange
                Long candidateId = 1L;
                Long reviewerId = 7L;
                LocalDateTime start = LocalDateTime.now().minusDays(7);
                LocalDateTime end = LocalDateTime.now();
                int page = 0; // should normalize to 1
                int limit = 500; // should normalize to 10
                String sortBy = "createdAt";
                String sortOrder = "desc";
                String token = "Bearer token";

                ReviewCandidate r1 = review(101L, candidateId, reviewerId, 4, 3, 5, "S1", "W1", true);
                ReviewCandidate r2 = review(102L, candidateId, 8L, 2, 2, 2, "S2", "W2", false);

                Page<ReviewCandidate> mockPage = new PageImpl<>(List.of(r1, r2),
                                PageRequest.of(0, 10), 12);

                when(reviewCandidateRepository.findByFilters(eq(candidateId), eq(reviewerId), eq(start), eq(end),
                                any(Pageable.class)))
                                .thenReturn(mockPage);

                when(userService.getEmployeeNames(eq(List.of(7L, 8L)), eq(token)))
                                .thenReturn(okBody("{\"7\":\"Nguyen Van A\",\"8\":\"Tran Van B\"}"));

                // act
                PaginationDTO result = reviewCandidateService.getAllWithFilters(candidateId, reviewerId, start, end,
                                page,
                                limit, sortBy, sortOrder, token);

                // assert
                assertNotNull(result);
                assertNotNull(result.getMeta());
                assertEquals(1, result.getMeta().getPage());
                assertEquals(10, result.getMeta().getPageSize());
                assertEquals(12, result.getMeta().getTotal());
                assertEquals(mockPage.getTotalPages(), result.getMeta().getPages());

                @SuppressWarnings("unchecked")
                List<ReviewCandidateResponseDTO> list = (List<ReviewCandidateResponseDTO>) result.getResult();
                assertNotNull(list);
                assertEquals(2, list.size());
                assertTrue(list.get(0).getReviewerName() != null && !list.get(0).getReviewerName().isEmpty());

                // mapping & averageScore
                ReviewCandidateResponseDTO dto1 = list.get(0);
                assertEquals(101L, dto1.getId());
                assertEquals(candidateId, dto1.getCandidateId());
                assertEquals(7L, dto1.getReviewerId());
                assertEquals("Nguyen Van A", dto1.getReviewerName());
                assertEquals((4.0 + 3.0 + 5.0) / 3.0, dto1.getAverageScore());

                // interaction: capture pageable to ensure normalization applied
                ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
                verify(reviewCandidateRepository, times(1))
                                .findByFilters(eq(candidateId), eq(reviewerId), eq(start), eq(end),
                                                pageableCaptor.capture());
                Pageable pageable = pageableCaptor.getValue();
                assertEquals(0, pageable.getPageNumber());
                assertEquals(10, pageable.getPageSize());

                verify(userService, times(1)).getEmployeeNames(eq(List.of(7L, 8L)), eq(token));
                verifyNoMoreInteractions(userService);
        }

        @Test
        @DisplayName("RC-TC-002: getByCandidateId - convert đúng list review theo candidate")
        void testGetByCandidateId_RC_TC_002() throws Exception {
                // Testcase ID: RC-TC-002
                // Objective: Xác nhận convert đúng list review theo candidate

                // arrange
                Long candidateId = 1L;
                String token = "Bearer token";

                ReviewCandidate r1 = review(201L, candidateId, 7L, 5, 5, 4, "S", "W", true);
                ReviewCandidate r2 = review(202L, candidateId, 8L, 1, 2, 3, "S2", "W2", false);

                when(reviewCandidateRepository.findByCandidate_Id(candidateId)).thenReturn(List.of(r1, r2));
                when(userService.getEmployeeNames(eq(List.of(7L, 8L)), eq(token)))
                                .thenReturn(okBody("{\"7\":\"Nguyen Van A\",\"8\":\"Tran Van B\"}"));

                // act
                List<ReviewCandidateResponseDTO> result = reviewCandidateService.getByCandidateId(candidateId, token);

                // assert
                assertNotNull(result);
                assertEquals(2, result.size());

                ReviewCandidateResponseDTO dto1 = result.get(0);
                assertEquals(201L, dto1.getId());
                assertEquals(candidateId, dto1.getCandidateId());
                assertEquals(7L, dto1.getReviewerId());
                assertEquals("Nguyen Van A", dto1.getReviewerName());
                assertEquals(5, dto1.getProfessionalSkillScore());
                assertEquals(5, dto1.getCommunicationSkillScore());
                assertEquals(4, dto1.getWorkExperienceScore());
                assertEquals((5.0 + 5.0 + 4.0) / 3.0, dto1.getAverageScore());

                verify(reviewCandidateRepository, times(1)).findByCandidate_Id(candidateId);
                verify(userService, times(1)).getEmployeeNames(eq(List.of(7L, 8L)), eq(token));
        }

        @Test
        @DisplayName("RC-TC-003: getById - lấy đúng review theo ID")
        void testGetById_RC_TC_003() throws Exception {
                // Testcase ID: RC-TC-003
                // Objective: Lấy đúng review theo ID

                // arrange
                Long id = 1L;
                String token = "Bearer token";
                ReviewCandidate r = review(id, 11L, 7L, 3, 4, 5, "S", "W", true);

                when(reviewCandidateRepository.findById(id)).thenReturn(Optional.of(r));
                when(userService.getEmployeeNames(eq(List.of(7L)), eq(token)))
                                .thenReturn(okBody("{\"7\":\"Nguyen Van A\"}"));

                // act
                ReviewCandidateResponseDTO dto = reviewCandidateService.getById(id, token);

                // assert
                assertNotNull(dto);
                assertEquals(id, dto.getId());
                assertEquals(11L, dto.getCandidateId());
                assertEquals(7L, dto.getReviewerId());
                assertEquals("Nguyen Van A", dto.getReviewerName());
                assertEquals(3, dto.getProfessionalSkillScore());
                assertEquals(4, dto.getCommunicationSkillScore());
                assertEquals(5, dto.getWorkExperienceScore());
                assertEquals((3.0 + 4.0 + 5.0) / 3.0, dto.getAverageScore());
                assertEquals("S", dto.getStrengths());
                assertEquals("W", dto.getWeaknesses());
                assertEquals(true, dto.getConclusion());

                verify(reviewCandidateRepository, times(1)).findById(id);
                verify(userService, times(1)).getEmployeeNames(eq(List.of(7L)), eq(token));
        }

        @Test
        @DisplayName("RC-TC-004: getById - review không tồn tại ném IdInvalidException")
        void testGetById_NotFound_RC_TC_004() {
                // Testcase ID: RC-TC-004
                // Objective: Xác nhận lỗi khi review không tồn tại

                // arrange
                Long id = 999L;
                when(reviewCandidateRepository.findById(id)).thenReturn(Optional.empty());

                // act
                IdInvalidException ex = assertThrows(IdInvalidException.class,
                                () -> reviewCandidateService.getById(id, "Bearer token"));

                // assert
                assertEquals("Đánh giá không tồn tại", ex.getMessage());
                verify(reviewCandidateRepository, times(1)).findById(id);
                verify(userService, never()).getEmployeeNames(any(), any());
        }

        @Test
        @DisplayName("RC-TC-005: create - validate candidateId bắt buộc")
        void testCreate_MissingCandidateId_RC_TC_005() {
                // Testcase ID: RC-TC-005
                // Objective: Validate candidateId bắt buộc

                // arrange
                CreateReviewCandidateDTO dto = new CreateReviewCandidateDTO();
                dto.setCandidateId(null);

                // act
                IdInvalidException ex = assertThrows(IdInvalidException.class,
                                () -> reviewCandidateService.create(dto, 7L));

                // assert
                assertEquals("Candidate ID là bắt buộc cho đánh giá phỏng vấn", ex.getMessage());
                verify(candidateRepository, never()).findById(any());
                verify(reviewCandidateRepository, never()).save(any());
        }

        @Test
        @DisplayName("RC-TC-006: create - tạo review thành công với 3 tiêu chí và nhận xét")
        void testCreate_Success_RC_TC_006() throws IdInvalidException {
                // Testcase ID: RC-TC-006
                // Objective: Tạo review thành công với 3 tiêu chí và nhận xét

                // arrange
                Long reviewerId = 7L;
                Long candidateId = 1L;

                CreateReviewCandidateDTO dto = new CreateReviewCandidateDTO();
                dto.setCandidateId(candidateId);
                dto.setProfessionalSkillScore(4);
                dto.setCommunicationSkillScore(3);
                dto.setWorkExperienceScore(5);
                dto.setStrengths("Good");
                dto.setWeaknesses("None");
                dto.setConclusion(true);

                Candidate candidate = candidate(candidateId);
                when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));

                when(reviewCandidateRepository.save(any(ReviewCandidate.class)))
                                .thenAnswer(inv -> {
                                        ReviewCandidate saved = inv.getArgument(0);
                                        saved.setId(100L);
                                        saved.setCreatedAt(LocalDateTime.now().minusHours(1));
                                        saved.setUpdatedAt(LocalDateTime.now());
                                        return saved;
                                });

                // act
                ReviewCandidateResponseDTO result = reviewCandidateService.create(dto, reviewerId);

                // assert
                assertNotNull(result);
                assertEquals(100L, result.getId());
                assertEquals(candidateId, result.getCandidateId());
                assertEquals(reviewerId, result.getReviewerId());
                assertEquals(4, result.getProfessionalSkillScore());
                assertEquals(3, result.getCommunicationSkillScore());
                assertEquals(5, result.getWorkExperienceScore());
                assertEquals((4.0 + 3.0 + 5.0) / 3.0, result.getAverageScore());
                assertEquals("Good", result.getStrengths());
                assertEquals("None", result.getWeaknesses());
                assertEquals(true, result.getConclusion());

                ArgumentCaptor<ReviewCandidate> captor = ArgumentCaptor.forClass(ReviewCandidate.class);
                verify(reviewCandidateRepository, times(1)).save(captor.capture());
                ReviewCandidate savedEntity = captor.getValue();
                assertEquals(reviewerId, savedEntity.getReviewerId());
                assertNotNull(savedEntity.getCandidate());
                assertEquals(candidateId, savedEntity.getCandidate().getId());

                verify(candidateRepository, times(1)).findById(candidateId);
                verifyNoMoreInteractions(userService);
        }

        @Test
        @DisplayName("RC-TC-007: update - owner được quyền cập nhật review")
        void testUpdate_OwnerCanUpdate_RC_TC_007() throws IdInvalidException {
                // Testcase ID: RC-TC-007
                // Objective: Xác nhận owner được quyền cập nhật review

                // arrange
                Long id = 1L;
                Long reviewerId = 7L;

                ReviewCandidate existing = review(id, 1L, reviewerId, 1, 1, 1, "oldS", "oldW", true);
                when(reviewCandidateRepository.findById(id)).thenReturn(Optional.of(existing));

                UpdateReviewCandidateDTO dto = new UpdateReviewCandidateDTO();
                dto.setProfessionalSkillScore(4);
                dto.setCommunicationSkillScore(null); // keep old
                dto.setWorkExperienceScore(3);
                dto.setStrengths("newS");
                dto.setWeaknesses(null); // keep old
                dto.setConclusion(false);

                when(reviewCandidateRepository.save(any(ReviewCandidate.class))).thenAnswer(inv -> inv.getArgument(0));

                // act
                ReviewCandidateResponseDTO result = reviewCandidateService.update(id, dto, reviewerId);

                // assert
                assertNotNull(result);
                assertEquals(id, result.getId());
                assertEquals(reviewerId, result.getReviewerId());
                assertEquals(1L, result.getCandidateId());
                assertEquals(4, result.getProfessionalSkillScore());
                assertEquals(1, result.getCommunicationSkillScore());
                assertEquals(3, result.getWorkExperienceScore());
                assertEquals((4.0 + 1.0 + 3.0) / 3.0, result.getAverageScore());
                assertEquals("newS", result.getStrengths());
                assertEquals("oldW", result.getWeaknesses());
                assertEquals(false, result.getConclusion());

                verify(reviewCandidateRepository, times(1)).findById(id);
                verify(reviewCandidateRepository, times(1)).save(existing);
                verifyNoMoreInteractions(userService);
        }

        @Test
        @DisplayName("RC-TC-008: update - chặn người không phải owner sửa review")
        void testUpdate_NonOwnerCannotUpdate_RC_TC_008() {
                // Testcase ID: RC-TC-008
                // Objective: Chặn người không phải owner sửa review

                // arrange
                Long id = 1L;
                ReviewCandidate existing = review(id, 1L, 999L, 1, 1, 1, "S", "W", true);
                when(reviewCandidateRepository.findById(id)).thenReturn(Optional.of(existing));

                UpdateReviewCandidateDTO dto = new UpdateReviewCandidateDTO();
                dto.setStrengths("new");

                // act
                IdInvalidException ex = assertThrows(IdInvalidException.class,
                                () -> reviewCandidateService.update(id, dto, 7L));

                // assert
                assertEquals("Bạn không có quyền cập nhật đánh giá này", ex.getMessage());
                verify(reviewCandidateRepository, times(1)).findById(id);
                verify(reviewCandidateRepository, never()).save(any());
        }

        @Test
        @DisplayName("RC-TC-009: delete - owner xóa review thành công")
        void testDelete_OwnerCanDelete_RC_TC_009() throws IdInvalidException {
                // Testcase ID: RC-TC-009
                // Objective: Owner xóa review thành công

                // arrange
                Long id = 1L;
                Long reviewerId = 7L;
                ReviewCandidate existing = review(id, 1L, reviewerId, 1, 2, 3, "S", "W", true);
                when(reviewCandidateRepository.findById(id)).thenReturn(Optional.of(existing));

                // act
                reviewCandidateService.delete(id, reviewerId);

                // assert
                verify(reviewCandidateRepository, times(1)).findById(id);
                verify(reviewCandidateRepository, times(1)).deleteById(id);
                verifyNoMoreInteractions(userService);
        }

        @Test
        @DisplayName("RC-TC-010: delete - chặn người không phải owner xóa review")
        void testDelete_NonOwnerCannotDelete_RC_TC_010() {
                // Testcase ID: RC-TC-010
                // Objective: Chặn người không phải owner xóa review

                // arrange
                Long id = 1L;
                ReviewCandidate existing = review(id, 1L, 999L, 1, 2, 3, "S", "W", true);
                when(reviewCandidateRepository.findById(id)).thenReturn(Optional.of(existing));

                // act
                IdInvalidException ex = assertThrows(IdInvalidException.class,
                                () -> reviewCandidateService.delete(id, 7L));

                // assert
                assertEquals("Bạn không có quyền xóa đánh giá này", ex.getMessage());
                verify(reviewCandidateRepository, times(1)).findById(id);
                verify(reviewCandidateRepository, never()).deleteById(any());
                verifyNoMoreInteractions(userService);
        }
}
