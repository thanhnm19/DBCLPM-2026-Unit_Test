package com.example.candidate_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

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

@SpringBootTest
@Transactional
@ActiveProfiles("test")
@DisplayName("ReviewCandidateService Integration Test (H2 Database)")
class ReviewCandidateServiceTest {

        @Autowired
        private ReviewCandidateRepository reviewCandidateRepository;

        @Autowired
        private CandidateRepository candidateRepository;

        @Autowired
        private ReviewCandidateService reviewCandidateService;

        @MockitoBean
        private UserService userService;

        @MockitoBean
        private KafkaTemplate<String, String> kafkaTemplate;

        private final ObjectMapper objectMapper = new ObjectMapper();

        // --- HELPER METHODS ---
        private Candidate createAndSaveCandidate() {
                Candidate c = new Candidate();
                return candidateRepository.save(c);
        }

        private ReviewCandidate createAndSaveReview(Candidate candidate, Long reviewerId,
                        Integer pro, Integer comm, Integer exp,
                        String strengths, String weaknesses, Boolean conclusion) {
                ReviewCandidate r = new ReviewCandidate();
                r.setCandidate(candidate);
                r.setReviewerId(reviewerId);
                r.setProfessionalSkillScore(pro);
                r.setCommunicationSkillScore(comm);
                r.setWorkExperienceScore(exp);
                r.setStrengths(strengths);
                r.setWeaknesses(weaknesses);
                r.setConclusion(conclusion);
                // Lưu xuống DB H2
                return reviewCandidateRepository.save(r);
        }

        private ResponseEntity<JsonNode> okBody(String json) throws Exception {
                return new ResponseEntity<>(objectMapper.readTree(json), HttpStatus.OK);
        }

        // --- TEST CASES ---

        @Test
        @DisplayName("RC-TC-001: getAllWithFilters - normalize page limit và enrich reviewerName")
        void testGetAllWithFilters_RC_TC_001() throws Exception {
                // arrange
                Candidate c = createAndSaveCandidate();
                Long candidateId = c.getId();
                LocalDateTime start = LocalDateTime.now().minusDays(7);
                LocalDateTime end = LocalDateTime.now().plusDays(1); // Mở rộng end để bao trọn thời gian save DB
                int page = 0; // should normalize to 1
                int limit = 500; // should normalize to 10
                String sortBy = "createdAt";
                String sortOrder = "desc";
                String token = "Bearer token";

                createAndSaveReview(c, 7L, 4, 3, 5, "S1", "W1", true);
                createAndSaveReview(c, 8L, 2, 2, 2, "S2", "W2", false);

                when(userService.getEmployeeNames(anyList(), eq(token)))
                                .thenReturn(okBody("{\"7\":\"Nguyen Van A\",\"8\":\"Tran Van B\"}"));

                // act
                // Chú ý: Truyền reviewerId = null để DB H2 lấy lên CẢ 2 bản ghi (để test logic
                // gom ID)
                PaginationDTO result = reviewCandidateService.getAllWithFilters(candidateId, null, start, end,
                                page, limit, sortBy, sortOrder, token);

                // assert
                assertNotNull(result);
                assertNotNull(result.getMeta());
                assertEquals(1, result.getMeta().getPage());
                assertEquals(10, result.getMeta().getPageSize());
                assertEquals(2, result.getMeta().getTotal()); // H2 thật chỉ có 2 bản ghi vừa tạo

                @SuppressWarnings("unchecked")
                List<ReviewCandidateResponseDTO> list = (List<ReviewCandidateResponseDTO>) result.getResult();
                assertEquals(2, list.size());

                // Kiểm tra logic tính trung bình và enrich tên
                ReviewCandidateResponseDTO dto1 = list.stream().filter(r -> r.getReviewerId().equals(7L)).findFirst()
                                .get();
                assertEquals("Nguyen Van A", dto1.getReviewerName());
                assertEquals((4.0 + 3.0 + 5.0) / 3.0, dto1.getAverageScore());

                // Kiểm chứng bắt tham số gọi ra ngoài
                @SuppressWarnings("unchecked")
                ArgumentCaptor<List<Long>> idsCaptor = (ArgumentCaptor<List<Long>>) (ArgumentCaptor<?>) ArgumentCaptor
                                .forClass(List.class);
                verify(userService, times(1)).getEmployeeNames(idsCaptor.capture(), eq(token));
                assertTrue(idsCaptor.getValue().containsAll(Arrays.asList(7L, 8L)));
        }

        @Test
        @DisplayName("RC-TC-002: getByCandidateId - convert đúng list review theo candidate")
        void testGetByCandidateId_RC_TC_002() throws Exception {
                Candidate c = createAndSaveCandidate();
                Long candidateId = c.getId();
                String token = "Bearer token";

                createAndSaveReview(c, 7L, 5, 5, 4, "S", "W", true);
                createAndSaveReview(c, 8L, 1, 2, 3, "S2", "W2", false);

                when(userService.getEmployeeNames(anyList(), eq(token)))
                                .thenReturn(okBody("{\"7\":\"Nguyen Van A\",\"8\":\"Tran Van B\"}"));

                // act
                List<ReviewCandidateResponseDTO> result = reviewCandidateService.getByCandidateId(candidateId, token);

                // assert
                assertEquals(2, result.size());
                ReviewCandidateResponseDTO dto1 = result.stream().filter(r -> r.getReviewerId().equals(7L)).findFirst()
                                .get();
                assertEquals(candidateId, dto1.getCandidateId());
                assertEquals("Nguyen Van A", dto1.getReviewerName());
                assertEquals((5.0 + 5.0 + 4.0) / 3.0, dto1.getAverageScore());
        }

        @Test
        @DisplayName("RC-TC-003: getById - lấy đúng review theo ID")
        void testGetById_RC_TC_003() throws Exception {
                Candidate c = createAndSaveCandidate();
                ReviewCandidate r = createAndSaveReview(c, 7L, 3, 4, 5, "S", "W", true);
                Long id = r.getId();
                String token = "Bearer token";

                when(userService.getEmployeeNames(anyList(), eq(token)))
                                .thenReturn(okBody("{\"7\":\"Nguyen Van A\"}"));

                // act
                ReviewCandidateResponseDTO dto = reviewCandidateService.getById(id, token);

                // assert
                assertNotNull(dto);
                assertEquals(id, dto.getId());
                assertEquals(c.getId(), dto.getCandidateId());
                assertEquals(7L, dto.getReviewerId());
                assertEquals("Nguyen Van A", dto.getReviewerName());
                assertEquals((3.0 + 4.0 + 5.0) / 3.0, dto.getAverageScore());
        }

        @Test
        @DisplayName("RC-TC-004: getById - review không tồn tại ném IdInvalidException")
        void testGetById_NotFound_RC_TC_004() {
                Long id = 999L; // DB không có
                IdInvalidException ex = assertThrows(IdInvalidException.class,
                                () -> reviewCandidateService.getById(id, "Bearer token"));
                assertEquals("Đánh giá không tồn tại", ex.getMessage());
        }

        @Test
        @DisplayName("RC-TC-005: create - validate candidateId bắt buộc")
        void testCreate_MissingCandidateId_RC_TC_005() {
                CreateReviewCandidateDTO dto = new CreateReviewCandidateDTO();
                dto.setCandidateId(null);

                IdInvalidException ex = assertThrows(IdInvalidException.class,
                                () -> reviewCandidateService.create(dto, 7L));
                assertEquals("Candidate ID là bắt buộc cho đánh giá phỏng vấn", ex.getMessage());
        }

        @Test
        @DisplayName("RC-TC-006: create - tạo review thành công với 3 tiêu chí và nhận xét")
        void testCreate_Success_RC_TC_006() throws IdInvalidException {
                Candidate c = createAndSaveCandidate();
                Long candidateId = c.getId();
                Long reviewerId = 7L;

                CreateReviewCandidateDTO dto = new CreateReviewCandidateDTO();
                dto.setCandidateId(candidateId);
                dto.setProfessionalSkillScore(4);
                dto.setCommunicationSkillScore(3);
                dto.setWorkExperienceScore(5);
                dto.setStrengths("Good");
                dto.setWeaknesses("None");
                dto.setConclusion(true);

                // act
                ReviewCandidateResponseDTO result = reviewCandidateService.create(dto, reviewerId);

                // assert DTO trả về
                assertNotNull(result.getId());
                assertEquals(candidateId, result.getCandidateId());
                assertEquals((4.0 + 3.0 + 5.0) / 3.0, result.getAverageScore());

                // assert DB thật
                ReviewCandidate savedEntity = reviewCandidateRepository.findById(result.getId()).orElseThrow();
                assertEquals(reviewerId, savedEntity.getReviewerId());
                assertEquals(candidateId, savedEntity.getCandidate().getId());
                assertEquals("Good", savedEntity.getStrengths());
        }

        @Test
        @DisplayName("RC-TC-007: update - owner được quyền cập nhật review")
        void testUpdate_OwnerCanUpdate_RC_TC_007() throws IdInvalidException {
                Candidate c = createAndSaveCandidate();
                Long reviewerId = 7L;
                ReviewCandidate existing = createAndSaveReview(c, reviewerId, 1, 1, 1, "oldS", "oldW", true);
                Long id = existing.getId();

                UpdateReviewCandidateDTO dto = new UpdateReviewCandidateDTO();
                dto.setProfessionalSkillScore(4);
                dto.setCommunicationSkillScore(null); // keep old
                dto.setWorkExperienceScore(3);
                dto.setStrengths("newS");
                dto.setWeaknesses(null); // keep old
                dto.setConclusion(false);

                // act
                ReviewCandidateResponseDTO result = reviewCandidateService.update(id, dto, reviewerId);

                // assert DTO
                assertEquals(4, result.getProfessionalSkillScore());
                assertEquals(1, result.getCommunicationSkillScore()); // old
                assertEquals("newS", result.getStrengths());
                assertEquals("oldW", result.getWeaknesses()); // old

                // assert DB thật
                ReviewCandidate dbEntity = reviewCandidateRepository.findById(id).orElseThrow();
                assertEquals(4, dbEntity.getProfessionalSkillScore());
                assertEquals("newS", dbEntity.getStrengths());
        }

        @Test
        @DisplayName("RC-TC-008: update - chặn người không phải owner sửa review")
        void testUpdate_NonOwnerCannotUpdate_RC_TC_008() {
                Candidate c = createAndSaveCandidate();
                ReviewCandidate existing = createAndSaveReview(c, 999L, 1, 1, 1, "S", "W", true);
                Long id = existing.getId();

                UpdateReviewCandidateDTO dto = new UpdateReviewCandidateDTO();
                dto.setStrengths("new");

                IdInvalidException ex = assertThrows(IdInvalidException.class,
                                () -> reviewCandidateService.update(id, dto, 7L));
                assertEquals("Bạn không có quyền cập nhật đánh giá này", ex.getMessage());
        }

        @Test
        @DisplayName("RC-TC-009: delete - owner xóa review thành công")
        void testDelete_OwnerCanDelete_RC_TC_009() throws IdInvalidException {
                Candidate c = createAndSaveCandidate();
                Long reviewerId = 7L;
                ReviewCandidate existing = createAndSaveReview(c, reviewerId, 1, 2, 3, "S", "W", true);
                Long id = existing.getId();

                // act
                reviewCandidateService.delete(id, reviewerId);

                // assert DB thật
                assertTrue(reviewCandidateRepository.findById(id).isEmpty());
        }

        @Test
        @DisplayName("RC-TC-010: delete - chặn người không phải owner xóa review")
        void testDelete_NonOwnerCannotDelete_RC_TC_010() {
                Candidate c = createAndSaveCandidate();
                ReviewCandidate existing = createAndSaveReview(c, 999L, 1, 2, 3, "S", "W", true);
                Long id = existing.getId();

                IdInvalidException ex = assertThrows(IdInvalidException.class,
                                () -> reviewCandidateService.delete(id, 7L));
                assertEquals("Bạn không có quyền xóa đánh giá này", ex.getMessage());
        }

        @Test
        @DisplayName("RC-TC-011: create - candidate không tồn tại ném IdInvalidException")
        void testCreate_CandidateNotFound_RC_TC_011() {
                Long reviewerId = 7L;
                CreateReviewCandidateDTO dto = new CreateReviewCandidateDTO();
                dto.setCandidateId(99L); // DB Không có Candidate này

                IdInvalidException ex = assertThrows(IdInvalidException.class,
                                () -> reviewCandidateService.create(dto, reviewerId));
                assertEquals("Ứng viên không tồn tại", ex.getMessage());
        }

        @Test
        @DisplayName("RC-TC-012: getAllWithFilters - không có token thì không gọi userService")
        void testGetAllWithFilters_NoTokenOrReviewerId_RC_TC_012() throws Exception {
                Candidate c = createAndSaveCandidate();
                // FIX: Đổi null thành 7L vì H2 không cho phép lưu reviewerId = null
                createAndSaveReview(c, 7L, 2, 3, 3, "S", "W", true); 

                PaginationDTO result = reviewCandidateService.getAllWithFilters(c.getId(), null, null, null,
                        1, 10, "createdAt", "asc", null); // token = null

                @SuppressWarnings("unchecked")
                List<ReviewCandidateResponseDTO> list = (List<ReviewCandidateResponseDTO>) result.getResult();
                assertEquals(1, list.size());
                assertNull(list.get(0).getReviewerName());
                verifyNoMoreInteractions(userService);
        }

        @Test
        @DisplayName("RC-TC-013: getById - nếu một trong 3 điểm là null thì average không được tính")
        void testGetById_PartialScores_RC_TC_013() throws IdInvalidException, Exception {
                Candidate c = createAndSaveCandidate();
                ReviewCandidate r = createAndSaveReview(c, 7L, null, 4, 5, "S", "W", true);

                when(userService.getEmployeeNames(anyList(), anyString()))
                                .thenReturn(ResponseEntity.ok().body(objectMapper.createObjectNode().put("7", "Ng A")));

                ReviewCandidateResponseDTO dto = reviewCandidateService.getById(r.getId(), "Bearer token");
                assertNull(dto.getAverageScore());
        }

        @Test
        @DisplayName("RC-TC-014: update - review không tồn tại ném IdInvalidException")
        void testUpdate_NotFound_RC_TC_014() {
                IdInvalidException ex = assertThrows(IdInvalidException.class,
                                () -> reviewCandidateService.update(400L, new UpdateReviewCandidateDTO(), 7L));
                assertEquals("Đánh giá không tồn tại", ex.getMessage());
        }

        @Test
        @DisplayName("RC-TC-015: delete - review không tồn tại ném IdInvalidException")
        void testDelete_NotFound_RC_TC_015() {
                IdInvalidException ex = assertThrows(IdInvalidException.class,
                                () -> reviewCandidateService.delete(500L, 7L));
                assertEquals("Đánh giá không tồn tại", ex.getMessage());
        }

        @Test
        @DisplayName("RC-TC-016: getByCandidateId - không có token thì không enrich reviewerName")
        void testGetByCandidateId_NoToken_RC_TC_016() {
                Candidate c = createAndSaveCandidate();
                createAndSaveReview(c, 7L, 4, 4, 4, "S", "W", true);

                List<ReviewCandidateResponseDTO> result = reviewCandidateService.getByCandidateId(c.getId(), null);

                assertEquals(1, result.size());
                assertNull(result.get(0).getReviewerName());
                verifyNoMoreInteractions(userService);
        }

        @Test
        @DisplayName("RC-TC-017: update - owner cập nhật tất cả các trường (non-null)")
        void testUpdate_OwnerUpdatesAllFields_RC_TC_017() throws IdInvalidException {
                Candidate c = createAndSaveCandidate();
                ReviewCandidate existing = createAndSaveReview(c, 7L, 1, 1, 1, "oldS", "oldW", true);

                UpdateReviewCandidateDTO dto = new UpdateReviewCandidateDTO();
                dto.setProfessionalSkillScore(5);
                dto.setCommunicationSkillScore(4);
                dto.setWorkExperienceScore(3);
                dto.setStrengths("sNew");
                dto.setWeaknesses("wNew");
                dto.setConclusion(false);

                ReviewCandidateResponseDTO result = reviewCandidateService.update(existing.getId(), dto, 7L);

                assertEquals(5, result.getProfessionalSkillScore());
                assertEquals("sNew", result.getStrengths());

                // Assert DB
                ReviewCandidate dbEntity = reviewCandidateRepository.findById(existing.getId()).orElseThrow();
                assertEquals(5, dbEntity.getProfessionalSkillScore());
        }

        @Test
        @DisplayName("RC-TC-018: getAllWithFilters - khi userService trả về không thành công thì không enrich")
        void testGetAllWithFilters_UserServiceNon2xx_RC_TC_018() throws Exception {
                Candidate c = createAndSaveCandidate();
                createAndSaveReview(c, 7L, 4, 4, 4, "S", "W", true);
                String token = "Bearer token";

                when(userService.getEmployeeNames(anyList(), eq(token)))
                                .thenReturn(ResponseEntity.<JsonNode>status(HttpStatus.INTERNAL_SERVER_ERROR).build());

                PaginationDTO result = reviewCandidateService.getAllWithFilters(c.getId(), 7L, null, null,
                                1, 10, null, null, token);

                @SuppressWarnings("unchecked")
                List<ReviewCandidateResponseDTO> list = (List<ReviewCandidateResponseDTO>) result.getResult();
                assertEquals(1, list.size());
                assertNull(list.get(0).getReviewerName()); // Lỗi API nên không sập, chỉ trả về null
        }

        @Test
        @DisplayName("RC-TC-019: getById - nếu reviewerId null thì không gọi userService và reviewerName null")
        void testGetById_ReviewerIdNull_RC_TC_019() throws IdInvalidException {
                // FIX: Vì DB H2 cấm lưu reviewerId = null, ta phải dùng Mock cục bộ để giả lập DB bị lỗi/legacy data
                ReviewCandidateRepository mockRepo = org.mockito.Mockito.mock(ReviewCandidateRepository.class);
                org.springframework.test.util.ReflectionTestUtils.setField(reviewCandidateService, "reviewCandidateRepository", mockRepo);

                try {
                ReviewCandidate r = new ReviewCandidate();
                r.setId(10L);
                r.setReviewerId(null); // Giả lập dữ liệu mồ côi
                when(mockRepo.findById(10L)).thenReturn(java.util.Optional.of(r));

                ReviewCandidateResponseDTO dto = reviewCandidateService.getById(10L, "Bearer token");

                assertNull(dto.getReviewerName());
                verifyNoMoreInteractions(userService);
                } finally {
                // Trả lại Repo thật cho các test case khác chạy H2
                org.springframework.test.util.ReflectionTestUtils.setField(reviewCandidateService, "reviewCandidateRepository", reviewCandidateRepository);
                }
        }

        @Test
        @DisplayName("RC-TC-020: getByCandidateId - khi body trả về không chứa key thì reviewerName null")
        void testGetByCandidateId_MissingKeyInResponse_RC_TC_020() throws Exception {
                Candidate c = createAndSaveCandidate();
                createAndSaveReview(c, 77L, 4, 4, 4, "S", "W", true);

                when(userService.getEmployeeNames(anyList(), anyString()))
                                .thenReturn(okBody("{}")); // API trả về rỗng

                List<ReviewCandidateResponseDTO> result = reviewCandidateService.getByCandidateId(c.getId(),
                                "Bearer token");
                assertNull(result.get(0).getReviewerName());
        }

        @Test
        @DisplayName("RC-TC-021: getByCandidateId - review.candidate là null thì candidateId null trong DTO")
        void testGetByCandidateId_CandidateNull_RC_TC_021() {
                // FIX: Tương tự TC-019, H2 cấm lưu candidate_id = null. Phải dùng Mock.
                ReviewCandidateRepository mockRepo = org.mockito.Mockito.mock(ReviewCandidateRepository.class);
                org.springframework.test.util.ReflectionTestUtils.setField(reviewCandidateService, "reviewCandidateRepository", mockRepo);

                try {
                ReviewCandidate r = new ReviewCandidate();
                r.setId(11L);
                r.setCandidate(null); // Giả lập dữ liệu mồ côi
                when(mockRepo.findByCandidate_Id(null)).thenReturn(List.of(r));

                List<ReviewCandidateResponseDTO> result = reviewCandidateService.getByCandidateId(null, null);

                assertTrue(result.size() > 0);
                assertNull(result.get(0).getCandidateId());
                } finally {
                org.springframework.test.util.ReflectionTestUtils.setField(reviewCandidateService, "reviewCandidateRepository", reviewCandidateRepository);
                }
        }

        @Test
        @DisplayName("RC-TC-022: update - khi review.reviewerId là null ném NullPointerException")
        void testUpdate_ExistingReviewerIdNull_RC_TC_022() {
                // FIX: H2 cấm lưu reviewerId = null. Phải dùng Mock.
                ReviewCandidateRepository mockRepo = org.mockito.Mockito.mock(ReviewCandidateRepository.class);
                org.springframework.test.util.ReflectionTestUtils.setField(reviewCandidateService, "reviewCandidateRepository", mockRepo);

                try {
                ReviewCandidate existing = new ReviewCandidate();
                existing.setId(12L);
                existing.setReviewerId(null);
                when(mockRepo.findById(12L)).thenReturn(java.util.Optional.of(existing));

                UpdateReviewCandidateDTO dto = new UpdateReviewCandidateDTO();
                assertThrows(NullPointerException.class, () -> reviewCandidateService.update(12L, dto, 7L));
                } finally {
                org.springframework.test.util.ReflectionTestUtils.setField(reviewCandidateService, "reviewCandidateRepository", reviewCandidateRepository);
                }
        }

        @Test
        @DisplayName("RC-TC-023: getById - token rỗng thì không gọi userService")
        void testGetById_TokenEmpty_RC_TC_023() throws IdInvalidException {
                Candidate c = createAndSaveCandidate();
                ReviewCandidate r = createAndSaveReview(c, 77L, 4, 4, 4, "S", "W", true);

                ReviewCandidateResponseDTO dto = reviewCandidateService.getById(r.getId(), "");
                assertNull(dto.getReviewerName());
                verifyNoMoreInteractions(userService);
        }

        @Test
        @DisplayName("RC-TC-024: getAllWithFilters - token rỗng thì không gọi userService")
        void testGetAllWithFilters_TokenEmpty_RC_TC_024() {
                Candidate c = createAndSaveCandidate();
                createAndSaveReview(c, 88L, 3, 3, 3, "S", "W", true);

                PaginationDTO result = reviewCandidateService.getAllWithFilters(c.getId(), 88L, null, null,
                                1, 10, null, null, "");

                @SuppressWarnings("unchecked")
                List<ReviewCandidateResponseDTO> list = (List<ReviewCandidateResponseDTO>) result.getResult();
                assertEquals(1, list.size());
                assertNull(list.get(0).getReviewerName());
                verifyNoMoreInteractions(userService);
        }

        @Test
        @DisplayName("RC-TC-025: update - DTO toàn null không thay đổi entity")
        void testUpdate_AllNullDTO_RC_TC_025() throws IdInvalidException {
                Candidate c = createAndSaveCandidate();
                ReviewCandidate existing = createAndSaveReview(c, 7L, 4, 4, 4, "oldS", "oldW", true);
                Long id = existing.getId();

                UpdateReviewCandidateDTO dto = new UpdateReviewCandidateDTO(); // all null
                ReviewCandidateResponseDTO result = reviewCandidateService.update(id, dto, 7L);

                assertEquals("oldS", result.getStrengths());

                ReviewCandidate dbEntity = reviewCandidateRepository.findById(id).orElseThrow();
                assertEquals("oldS", dbEntity.getStrengths());
        }

        @Test
        @DisplayName("RC-TC-026: getAllWithFilters - userService trả 2xx nhưng body null thì không enrich")
        void testGetAllWithFilters_UserService2xxBodyNull_RC_TC_026() throws Exception {
                Candidate c = createAndSaveCandidate();
                createAndSaveReview(c, 7L, 4, 4, 4, "S", "W", true);

                when(userService.getEmployeeNames(anyList(), anyString()))
                                .thenReturn(ResponseEntity.ok().body(null)); // Body rỗng

                PaginationDTO result = reviewCandidateService.getAllWithFilters(c.getId(), 7L, null, null,
                                1, 10, null, null, "Bearer token");

                @SuppressWarnings("unchecked")
                List<ReviewCandidateResponseDTO> list = (List<ReviewCandidateResponseDTO>) result.getResult();
                assertNull(list.get(0).getReviewerName());
        }

        @Test
        @DisplayName("RC-TC-027: getById - communicationScore null thì average không tính")
        void testGetById_CommunicationNull_RC_TC_027() throws Exception {
                Candidate c = createAndSaveCandidate();
                ReviewCandidate r = createAndSaveReview(c, 7L, 3, null, 5, "S", "W", true);

                // FIX: Bổ sung mock userService để không bị NPE
                when(userService.getEmployeeNames(anyList(), anyString()))
                        .thenReturn(okBody("{\"7\":\"Nguyen Van A\"}"));

                ReviewCandidateResponseDTO dto = reviewCandidateService.getById(r.getId(), "Bearer token");
                assertNull(dto.getAverageScore());
        }

        @Test
        @DisplayName("RC-TC-028: getById - workExperienceScore null thì average không tính")
        void testGetById_WorkExpNull_RC_TC_028() throws Exception {
                Candidate c = createAndSaveCandidate();
                ReviewCandidate r = createAndSaveReview(c, 7L, 3, 4, null, "S", "W", true);

                // FIX: Bổ sung mock userService để không bị NPE
                when(userService.getEmployeeNames(anyList(), anyString()))
                        .thenReturn(okBody("{\"7\":\"Nguyen Van A\"}"));

                ReviewCandidateResponseDTO dto = reviewCandidateService.getById(r.getId(), "Bearer token");
                assertNull(dto.getAverageScore());
        }
        
        @Test
        @DisplayName("RC-TC-029: getAllWithFilters - limit < 1 được normalize về 10")
        void testGetAllWithFilters_LimitTooSmall_RC_TC_029() throws Exception {
                Candidate c = createAndSaveCandidate();
                createAndSaveReview(c, 7L, 4, 4, 4, "S", "W", true);

                when(userService.getEmployeeNames(anyList(), anyString()))
                                .thenReturn(okBody("{\"7\":\"Nguyen Van A\"}"));

                PaginationDTO result = reviewCandidateService.getAllWithFilters(c.getId(), 7L, null, null,
                                1, 0, null, null, "Bearer token"); // Truyền limit = 0

                assertNotNull(result.getMeta());
                assertEquals(10, result.getMeta().getPageSize()); // Hệ thống tự ép về 10
        }

        // @Mock
        // private ReviewCandidateRepository reviewCandidateRepository;

        // @Mock
        // private CandidateRepository candidateRepository;

        // @Mock
        // private UserService userService;

        // @InjectMocks
        // private ReviewCandidateService reviewCandidateService;

        // private final ObjectMapper objectMapper = new ObjectMapper();

        // private Candidate candidate(Long id) {
        // Candidate c = new Candidate();
        // c.setId(id);
        // return c;
        // }

        // private ReviewCandidate review(Long id, Long candidateId, Long reviewerId,
        // Integer pro, Integer comm, Integer exp,
        // String strengths, String weaknesses, Boolean conclusion) {
        // ReviewCandidate r = new ReviewCandidate();
        // r.setId(id);
        // r.setCandidate(candidate(candidateId));
        // r.setReviewerId(reviewerId);
        // r.setProfessionalSkillScore(pro);
        // r.setCommunicationSkillScore(comm);
        // r.setWorkExperienceScore(exp);
        // r.setStrengths(strengths);
        // r.setWeaknesses(weaknesses);
        // r.setConclusion(conclusion);
        // r.setCreatedAt(LocalDateTime.now().minusDays(1));
        // r.setUpdatedAt(LocalDateTime.now());
        // return r;
        // }

        // private ResponseEntity<JsonNode> okBody(String json) throws Exception {
        // return new ResponseEntity<>(objectMapper.readTree(json), HttpStatus.OK);
        // }

        // @Test
        // @DisplayName("RC-TC-001: getAllWithFilters - normalize page limit và enrich
        // reviewerName")
        // void testGetAllWithFilters_RC_TC_001() throws Exception {
        // // Testcase ID: RC-TC-001
        // // Objective: Xác nhận normalize page/limit và enrich reviewerName

        // // arrange
        // Long candidateId = 1L;
        // Long reviewerId = 7L;
        // LocalDateTime start = LocalDateTime.now().minusDays(7);
        // LocalDateTime end = LocalDateTime.now();
        // int page = 0; // should normalize to 1
        // int limit = 500; // should normalize to 10
        // String sortBy = "createdAt";
        // String sortOrder = "desc";
        // String token = "Bearer token";

        // ReviewCandidate r1 = review(101L, candidateId, reviewerId, 4, 3, 5, "S1",
        // "W1", true);
        // ReviewCandidate r2 = review(102L, candidateId, 8L, 2, 2, 2, "S2", "W2",
        // false);

        // Page<ReviewCandidate> mockPage = new PageImpl<>(List.of(r1, r2),
        // PageRequest.of(0, 10), 12);

        // when(reviewCandidateRepository.findByFilters(eq(candidateId), eq(reviewerId),
        // eq(start), eq(end),
        // any(Pageable.class)))
        // .thenReturn(mockPage);

        // when(userService.getEmployeeNames(eq(List.of(7L, 8L)), eq(token)))
        // .thenReturn(okBody("{\"7\":\"Nguyen Van A\",\"8\":\"Tran Van B\"}"));

        // // act
        // PaginationDTO result = reviewCandidateService.getAllWithFilters(candidateId,
        // reviewerId, start, end,
        // page,
        // limit, sortBy, sortOrder, token);

        // // assert
        // assertNotNull(result);
        // assertNotNull(result.getMeta());
        // assertEquals(1, result.getMeta().getPage());
        // assertEquals(10, result.getMeta().getPageSize());
        // assertEquals(12, result.getMeta().getTotal());
        // assertEquals(mockPage.getTotalPages(), result.getMeta().getPages());

        // @SuppressWarnings("unchecked")
        // List<ReviewCandidateResponseDTO> list = (List<ReviewCandidateResponseDTO>)
        // result.getResult();
        // assertNotNull(list);
        // assertEquals(2, list.size());
        // assertTrue(list.get(0).getReviewerName() != null &&
        // !list.get(0).getReviewerName().isEmpty());

        // // mapping & averageScore
        // ReviewCandidateResponseDTO dto1 = list.get(0);
        // assertEquals(101L, dto1.getId());
        // assertEquals(candidateId, dto1.getCandidateId());
        // assertEquals(7L, dto1.getReviewerId());
        // assertEquals("Nguyen Van A", dto1.getReviewerName());
        // assertEquals((4.0 + 3.0 + 5.0) / 3.0, dto1.getAverageScore());

        // // interaction: capture pageable to ensure normalization applied
        // ArgumentCaptor<Pageable> pageableCaptor =
        // ArgumentCaptor.forClass(Pageable.class);
        // verify(reviewCandidateRepository, times(1))
        // .findByFilters(eq(candidateId), eq(reviewerId), eq(start), eq(end),
        // pageableCaptor.capture());
        // Pageable pageable = pageableCaptor.getValue();
        // assertEquals(0, pageable.getPageNumber());
        // assertEquals(10, pageable.getPageSize());

        // verify(userService, times(1)).getEmployeeNames(eq(List.of(7L, 8L)),
        // eq(token));
        // verifyNoMoreInteractions(userService);
        // }

        // @Test
        // @DisplayName("RC-TC-002: getByCandidateId - convert đúng list review theo
        // candidate")
        // void testGetByCandidateId_RC_TC_002() throws Exception {
        // // Testcase ID: RC-TC-002
        // // Objective: Xác nhận convert đúng list review theo candidate

        // // arrange
        // Long candidateId = 1L;
        // String token = "Bearer token";

        // ReviewCandidate r1 = review(201L, candidateId, 7L, 5, 5, 4, "S", "W", true);
        // ReviewCandidate r2 = review(202L, candidateId, 8L, 1, 2, 3, "S2", "W2",
        // false);

        // when(reviewCandidateRepository.findByCandidate_Id(candidateId)).thenReturn(List.of(r1,
        // r2));
        // when(userService.getEmployeeNames(eq(List.of(7L, 8L)), eq(token)))
        // .thenReturn(okBody("{\"7\":\"Nguyen Van A\",\"8\":\"Tran Van B\"}"));

        // // act
        // List<ReviewCandidateResponseDTO> result =
        // reviewCandidateService.getByCandidateId(candidateId, token);

        // // assert
        // assertNotNull(result);
        // assertEquals(2, result.size());

        // ReviewCandidateResponseDTO dto1 = result.get(0);
        // assertEquals(201L, dto1.getId());
        // assertEquals(candidateId, dto1.getCandidateId());
        // assertEquals(7L, dto1.getReviewerId());
        // assertEquals("Nguyen Van A", dto1.getReviewerName());
        // assertEquals(5, dto1.getProfessionalSkillScore());
        // assertEquals(5, dto1.getCommunicationSkillScore());
        // assertEquals(4, dto1.getWorkExperienceScore());
        // assertEquals((5.0 + 5.0 + 4.0) / 3.0, dto1.getAverageScore());

        // verify(reviewCandidateRepository, times(1)).findByCandidate_Id(candidateId);
        // verify(userService, times(1)).getEmployeeNames(eq(List.of(7L, 8L)),
        // eq(token));
        // }

        // @Test
        // @DisplayName("RC-TC-003: getById - lấy đúng review theo ID")
        // void testGetById_RC_TC_003() throws Exception {
        // // Testcase ID: RC-TC-003
        // // Objective: Lấy đúng review theo ID

        // // arrange
        // Long id = 1L;
        // String token = "Bearer token";
        // ReviewCandidate r = review(id, 11L, 7L, 3, 4, 5, "S", "W", true);

        // when(reviewCandidateRepository.findById(id)).thenReturn(Optional.of(r));
        // when(userService.getEmployeeNames(eq(List.of(7L)), eq(token)))
        // .thenReturn(okBody("{\"7\":\"Nguyen Van A\"}"));

        // // act
        // ReviewCandidateResponseDTO dto = reviewCandidateService.getById(id, token);

        // // assert
        // assertNotNull(dto);
        // assertEquals(id, dto.getId());
        // assertEquals(11L, dto.getCandidateId());
        // assertEquals(7L, dto.getReviewerId());
        // assertEquals("Nguyen Van A", dto.getReviewerName());
        // assertEquals(3, dto.getProfessionalSkillScore());
        // assertEquals(4, dto.getCommunicationSkillScore());
        // assertEquals(5, dto.getWorkExperienceScore());
        // assertEquals((3.0 + 4.0 + 5.0) / 3.0, dto.getAverageScore());
        // assertEquals("S", dto.getStrengths());
        // assertEquals("W", dto.getWeaknesses());
        // assertEquals(true, dto.getConclusion());

        // verify(reviewCandidateRepository, times(1)).findById(id);
        // verify(userService, times(1)).getEmployeeNames(eq(List.of(7L)), eq(token));
        // }

        // @Test
        // @DisplayName("RC-TC-004: getById - review không tồn tại ném
        // IdInvalidException")
        // void testGetById_NotFound_RC_TC_004() {
        // // Testcase ID: RC-TC-004
        // // Objective: Xác nhận lỗi khi review không tồn tại

        // // arrange
        // Long id = 999L;
        // when(reviewCandidateRepository.findById(id)).thenReturn(Optional.empty());

        // // act
        // IdInvalidException ex = assertThrows(IdInvalidException.class,
        // () -> reviewCandidateService.getById(id, "Bearer token"));

        // // assert
        // assertEquals("Đánh giá không tồn tại", ex.getMessage());
        // verify(reviewCandidateRepository, times(1)).findById(id);
        // verify(userService, never()).getEmployeeNames(any(), any());
        // }

        // @Test
        // @DisplayName("RC-TC-005: create - validate candidateId bắt buộc")
        // void testCreate_MissingCandidateId_RC_TC_005() {
        // // Testcase ID: RC-TC-005
        // // Objective: Validate candidateId bắt buộc

        // // arrange
        // CreateReviewCandidateDTO dto = new CreateReviewCandidateDTO();
        // dto.setCandidateId(null);

        // // act
        // IdInvalidException ex = assertThrows(IdInvalidException.class,
        // () -> reviewCandidateService.create(dto, 7L));

        // // assert
        // assertEquals("Candidate ID là bắt buộc cho đánh giá phỏng vấn",
        // ex.getMessage());
        // verify(candidateRepository, never()).findById(any());
        // verify(reviewCandidateRepository, never()).save(any());
        // }

        // @Test
        // @DisplayName("RC-TC-006: create - tạo review thành công với 3 tiêu chí và
        // nhận xét")
        // void testCreate_Success_RC_TC_006() throws IdInvalidException {
        // // Testcase ID: RC-TC-006
        // // Objective: Tạo review thành công với 3 tiêu chí và nhận xét

        // // arrange
        // Long reviewerId = 7L;
        // Long candidateId = 1L;

        // CreateReviewCandidateDTO dto = new CreateReviewCandidateDTO();
        // dto.setCandidateId(candidateId);
        // dto.setProfessionalSkillScore(4);
        // dto.setCommunicationSkillScore(3);
        // dto.setWorkExperienceScore(5);
        // dto.setStrengths("Good");
        // dto.setWeaknesses("None");
        // dto.setConclusion(true);

        // Candidate candidate = candidate(candidateId);
        // when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));

        // when(reviewCandidateRepository.save(any(ReviewCandidate.class)))
        // .thenAnswer(inv -> {
        // ReviewCandidate saved = inv.getArgument(0);
        // saved.setId(100L);
        // saved.setCreatedAt(LocalDateTime.now().minusHours(1));
        // saved.setUpdatedAt(LocalDateTime.now());
        // return saved;
        // });

        // // act
        // ReviewCandidateResponseDTO result = reviewCandidateService.create(dto,
        // reviewerId);

        // // assert
        // assertNotNull(result);
        // assertEquals(100L, result.getId());
        // assertEquals(candidateId, result.getCandidateId());
        // assertEquals(reviewerId, result.getReviewerId());
        // assertEquals(4, result.getProfessionalSkillScore());
        // assertEquals(3, result.getCommunicationSkillScore());
        // assertEquals(5, result.getWorkExperienceScore());
        // assertEquals((4.0 + 3.0 + 5.0) / 3.0, result.getAverageScore());
        // assertEquals("Good", result.getStrengths());
        // assertEquals("None", result.getWeaknesses());
        // assertEquals(true, result.getConclusion());

        // ArgumentCaptor<ReviewCandidate> captor =
        // ArgumentCaptor.forClass(ReviewCandidate.class);
        // verify(reviewCandidateRepository, times(1)).save(captor.capture());
        // ReviewCandidate savedEntity = captor.getValue();
        // assertEquals(reviewerId, savedEntity.getReviewerId());
        // assertNotNull(savedEntity.getCandidate());
        // assertEquals(candidateId, savedEntity.getCandidate().getId());

        // verify(candidateRepository, times(1)).findById(candidateId);
        // verifyNoMoreInteractions(userService);
        // }

        // @Test
        // @DisplayName("RC-TC-007: update - owner được quyền cập nhật review")
        // void testUpdate_OwnerCanUpdate_RC_TC_007() throws IdInvalidException {
        // // Testcase ID: RC-TC-007
        // // Objective: Xác nhận owner được quyền cập nhật review

        // // arrange
        // Long id = 1L;
        // Long reviewerId = 7L;

        // ReviewCandidate existing = review(id, 1L, reviewerId, 1, 1, 1, "oldS",
        // "oldW", true);
        // when(reviewCandidateRepository.findById(id)).thenReturn(Optional.of(existing));

        // UpdateReviewCandidateDTO dto = new UpdateReviewCandidateDTO();
        // dto.setProfessionalSkillScore(4);
        // dto.setCommunicationSkillScore(null); // keep old
        // dto.setWorkExperienceScore(3);
        // dto.setStrengths("newS");
        // dto.setWeaknesses(null); // keep old
        // dto.setConclusion(false);

        // when(reviewCandidateRepository.save(any(ReviewCandidate.class))).thenAnswer(inv
        // -> inv.getArgument(0));

        // // act
        // ReviewCandidateResponseDTO result = reviewCandidateService.update(id, dto,
        // reviewerId);

        // // assert
        // assertNotNull(result);
        // assertEquals(id, result.getId());
        // assertEquals(reviewerId, result.getReviewerId());
        // assertEquals(1L, result.getCandidateId());
        // assertEquals(4, result.getProfessionalSkillScore());
        // assertEquals(1, result.getCommunicationSkillScore());
        // assertEquals(3, result.getWorkExperienceScore());
        // assertEquals((4.0 + 1.0 + 3.0) / 3.0, result.getAverageScore());
        // assertEquals("newS", result.getStrengths());
        // assertEquals("oldW", result.getWeaknesses());
        // assertEquals(false, result.getConclusion());

        // verify(reviewCandidateRepository, times(1)).findById(id);
        // verify(reviewCandidateRepository, times(1)).save(existing);
        // verifyNoMoreInteractions(userService);
        // }

        // @Test
        // @DisplayName("RC-TC-008: update - chặn người không phải owner sửa review")
        // void testUpdate_NonOwnerCannotUpdate_RC_TC_008() {
        // // Testcase ID: RC-TC-008
        // // Objective: Chặn người không phải owner sửa review

        // // arrange
        // Long id = 1L;
        // ReviewCandidate existing = review(id, 1L, 999L, 1, 1, 1, "S", "W", true);
        // when(reviewCandidateRepository.findById(id)).thenReturn(Optional.of(existing));

        // UpdateReviewCandidateDTO dto = new UpdateReviewCandidateDTO();
        // dto.setStrengths("new");

        // // act
        // IdInvalidException ex = assertThrows(IdInvalidException.class,
        // () -> reviewCandidateService.update(id, dto, 7L));

        // // assert
        // assertEquals("Bạn không có quyền cập nhật đánh giá này", ex.getMessage());
        // verify(reviewCandidateRepository, times(1)).findById(id);
        // verify(reviewCandidateRepository, never()).save(any());
        // }

        // @Test
        // @DisplayName("RC-TC-009: delete - owner xóa review thành công")
        // void testDelete_OwnerCanDelete_RC_TC_009() throws IdInvalidException {
        // // Testcase ID: RC-TC-009
        // // Objective: Owner xóa review thành công

        // // arrange
        // Long id = 1L;
        // Long reviewerId = 7L;
        // ReviewCandidate existing = review(id, 1L, reviewerId, 1, 2, 3, "S", "W",
        // true);
        // when(reviewCandidateRepository.findById(id)).thenReturn(Optional.of(existing));

        // // act
        // reviewCandidateService.delete(id, reviewerId);

        // // assert
        // verify(reviewCandidateRepository, times(1)).findById(id);
        // verify(reviewCandidateRepository, times(1)).deleteById(id);
        // verifyNoMoreInteractions(userService);
        // }

        // @Test
        // @DisplayName("RC-TC-010: delete - chặn người không phải owner xóa review")
        // void testDelete_NonOwnerCannotDelete_RC_TC_010() {
        // // Testcase ID: RC-TC-010
        // // Objective: Chặn người không phải owner xóa review

        // // arrange
        // Long id = 1L;
        // ReviewCandidate existing = review(id, 1L, 999L, 1, 2, 3, "S", "W", true);
        // when(reviewCandidateRepository.findById(id)).thenReturn(Optional.of(existing));

        // // act
        // IdInvalidException ex = assertThrows(IdInvalidException.class,
        // () -> reviewCandidateService.delete(id, 7L));

        // // assert
        // assertEquals("Bạn không có quyền xóa đánh giá này", ex.getMessage());
        // verify(reviewCandidateRepository, times(1)).findById(id);
        // verify(reviewCandidateRepository, never()).deleteById(any());
        // verifyNoMoreInteractions(userService);
        // }

        // @Test
        // @DisplayName("RC-TC-011: create - candidate không tồn tại ném
        // IdInvalidException")
        // void testCreate_CandidateNotFound_RC_TC_011() {
        // // arrange
        // Long reviewerId = 7L;
        // Long candidateId = 99L;
        // CreateReviewCandidateDTO dto = new CreateReviewCandidateDTO();
        // dto.setCandidateId(candidateId);

        // when(candidateRepository.findById(candidateId)).thenReturn(Optional.empty());

        // // act
        // IdInvalidException ex = assertThrows(IdInvalidException.class,
        // () -> reviewCandidateService.create(dto, reviewerId));

        // // assert
        // assertEquals("Ứng viên không tồn tại", ex.getMessage());
        // verify(candidateRepository, times(1)).findById(candidateId);
        // verify(reviewCandidateRepository, never()).save(any());
        // }

        // @Test
        // @DisplayName("RC-TC-012: getAllWithFilters - không có token hoặc reviewerId
        // null không gọi userService")
        // void testGetAllWithFilters_NoTokenOrReviewerId_RC_TC_012() throws Exception {
        // // arrange
        // Long candidateId = 1L;
        // LocalDateTime start = LocalDateTime.now().minusDays(7);
        // LocalDateTime end = LocalDateTime.now();
        // int page = 1;
        // int limit = 10;
        // String sortBy = "createdAt";
        // String sortOrder = "asc"; // exercise asc branch
        // String token = null; // no token provided

        // ReviewCandidate r1 = review(301L, candidateId, null, null, 2, 3, "S", "W",
        // true);
        // Page<ReviewCandidate> mockPage = new PageImpl<>(List.of(r1),
        // PageRequest.of(0, 10), 1);

        // when(reviewCandidateRepository.findByFilters(eq(candidateId), eq(null),
        // eq(start), eq(end),
        // any(Pageable.class))).thenReturn(mockPage);

        // // act
        // PaginationDTO result = reviewCandidateService.getAllWithFilters(candidateId,
        // null, start, end,
        // page, limit, sortBy, sortOrder, token);

        // // assert
        // assertNotNull(result);
        // @SuppressWarnings("unchecked")
        // List<ReviewCandidateResponseDTO> list = (List<ReviewCandidateResponseDTO>)
        // result.getResult();
        // assertEquals(1, list.size());
        // ReviewCandidateResponseDTO dto = list.get(0);
        // // since reviewerId is null and token is null, reviewerName must remain null
        // assertEquals(null, dto.getReviewerName());

        // verify(reviewCandidateRepository, times(1))
        // .findByFilters(eq(candidateId), eq(null), eq(start), eq(end),
        // any(Pageable.class));
        // verifyNoMoreInteractions(userService);
        // }

        // @Test
        // @DisplayName("RC-TC-013: getById - nếu một trong 3 điểm là null thì average
        // không được tính")
        // void testGetById_PartialScores_RC_TC_013() throws IdInvalidException {
        // // arrange
        // Long id = 5L;
        // String token = "Bearer token";
        // // professionalSkillScore is null
        // ReviewCandidate r = review(id, 11L, 7L, null, 4, 5, "S", "W", true);

        // when(reviewCandidateRepository.findById(id)).thenReturn(Optional.of(r));
        // when(userService.getEmployeeNames(eq(List.of(7L)), eq(token)))
        // .thenReturn(ResponseEntity.ok().body(objectMapper.createObjectNode().put("7",
        // "Ng A")));

        // // act
        // ReviewCandidateResponseDTO dto = reviewCandidateService.getById(id, token);

        // // assert
        // assertNotNull(dto);
        // assertEquals(id, dto.getId());
        // assertEquals(11L, dto.getCandidateId());
        // assertEquals(7L, dto.getReviewerId());
        // assertEquals("Ng A", dto.getReviewerName());
        // // average should be null because one score is missing
        // assertEquals(null, dto.getAverageScore());
        // }

        // @Test
        // @DisplayName("RC-TC-014: update - review không tồn tại ném
        // IdInvalidException")
        // void testUpdate_NotFound_RC_TC_014() {
        // // arrange
        // Long id = 400L;
        // UpdateReviewCandidateDTO dto = new UpdateReviewCandidateDTO();
        // when(reviewCandidateRepository.findById(id)).thenReturn(Optional.empty());

        // // act
        // IdInvalidException ex = assertThrows(IdInvalidException.class,
        // () -> reviewCandidateService.update(id, dto, 7L));

        // // assert
        // assertEquals("Đánh giá không tồn tại", ex.getMessage());
        // verify(reviewCandidateRepository, times(1)).findById(id);
        // }

        // @Test
        // @DisplayName("RC-TC-015: delete - review không tồn tại ném
        // IdInvalidException")
        // void testDelete_NotFound_RC_TC_015() {
        // // arrange
        // Long id = 500L;
        // when(reviewCandidateRepository.findById(id)).thenReturn(Optional.empty());

        // // act
        // IdInvalidException ex = assertThrows(IdInvalidException.class,
        // () -> reviewCandidateService.delete(id, 7L));

        // // assert
        // assertEquals("Đánh giá không tồn tại", ex.getMessage());
        // verify(reviewCandidateRepository, times(1)).findById(id);
        // }

        // @Test
        // @DisplayName("RC-TC-016: getByCandidateId - không có token thì không enrich
        // reviewerName")
        // void testGetByCandidateId_NoToken_RC_TC_016() {
        // // arrange
        // Long candidateId = 2L;
        // ReviewCandidate r1 = review(601L, candidateId, 7L, 4, 4, 4, "S", "W", true);
        // when(reviewCandidateRepository.findByCandidate_Id(candidateId)).thenReturn(List.of(r1));

        // // act
        // List<ReviewCandidateResponseDTO> result =
        // reviewCandidateService.getByCandidateId(candidateId, null);

        // // assert
        // assertNotNull(result);
        // assertEquals(1, result.size());
        // assertEquals(null, result.get(0).getReviewerName());
        // verify(reviewCandidateRepository, times(1)).findByCandidate_Id(candidateId);
        // verifyNoMoreInteractions(userService);
        // }

        // @Test
        // @DisplayName("RC-TC-017: update - owner cập nhật tất cả các trường
        // (non-null)")
        // void testUpdate_OwnerUpdatesAllFields_RC_TC_017() throws IdInvalidException {
        // // arrange
        // Long id = 11L;
        // Long reviewerId = 7L;
        // ReviewCandidate existing = review(id, 2L, reviewerId, 1, 1, 1, "oldS",
        // "oldW", true);
        // when(reviewCandidateRepository.findById(id)).thenReturn(Optional.of(existing));

        // UpdateReviewCandidateDTO dto = new UpdateReviewCandidateDTO();
        // dto.setProfessionalSkillScore(5);
        // dto.setCommunicationSkillScore(4);
        // dto.setWorkExperienceScore(3);
        // dto.setStrengths("sNew");
        // dto.setWeaknesses("wNew");
        // dto.setConclusion(false);

        // when(reviewCandidateRepository.save(any(ReviewCandidate.class))).thenAnswer(inv
        // -> inv.getArgument(0));

        // // act
        // ReviewCandidateResponseDTO result = reviewCandidateService.update(id, dto,
        // reviewerId);

        // // assert
        // assertNotNull(result);
        // assertEquals(5, result.getProfessionalSkillScore());
        // assertEquals(4, result.getCommunicationSkillScore());
        // assertEquals(3, result.getWorkExperienceScore());
        // assertEquals((5.0 + 4.0 + 3.0) / 3.0, result.getAverageScore());
        // assertEquals("sNew", result.getStrengths());
        // assertEquals("wNew", result.getWeaknesses());
        // assertEquals(false, result.getConclusion());

        // verify(reviewCandidateRepository, times(1)).findById(id);
        // verify(reviewCandidateRepository, times(1)).save(existing);
        // }

        // @Test
        // @DisplayName("RC-TC-018: getAllWithFilters - khi userService trả về không
        // thành công thì không enrich")
        // void testGetAllWithFilters_UserServiceNon2xx_RC_TC_018() throws Exception {
        // // arrange
        // Long candidateId = 3L;
        // Long reviewerId = 7L;
        // LocalDateTime start = LocalDateTime.now().minusDays(7);
        // LocalDateTime end = LocalDateTime.now();
        // int page = 1;
        // int limit = 10;
        // String token = "Bearer token";

        // ReviewCandidate r1 = review(801L, candidateId, reviewerId, 4, 4, 4, "S", "W",
        // true);
        // Page<ReviewCandidate> mockPage = new PageImpl<>(List.of(r1),
        // PageRequest.of(0, 10), 1);

        // when(reviewCandidateRepository.findByFilters(eq(candidateId), eq(reviewerId),
        // eq(start), eq(end),
        // any(Pageable.class))).thenReturn(mockPage);
        // // when(userService.getEmployeeNames(eq(List.of(reviewerId)), eq(token)))
        // // .thenReturn(okBody("{\"7\":\"Nguyen Van A\"}"));
        // when(userService.getEmployeeNames(eq(List.of(7L)), eq(token)))
        // .thenReturn(ResponseEntity.<JsonNode>status(HttpStatus.INTERNAL_SERVER_ERROR).build());

        // // act
        // PaginationDTO result = reviewCandidateService.getAllWithFilters(candidateId,
        // reviewerId, start, end,
        // page, limit, null, null, token);

        // // assert
        // @SuppressWarnings("unchecked")
        // List<ReviewCandidateResponseDTO> list = (List<ReviewCandidateResponseDTO>)
        // result.getResult();
        // assertEquals(1, list.size());
        // assertEquals(null, list.get(0).getReviewerName());

        // verify(reviewCandidateRepository, times(1)).findByFilters(eq(candidateId),
        // eq(reviewerId), eq(start),
        // eq(end), any(Pageable.class));
        // verify(userService, times(1)).getEmployeeNames(eq(List.of(7L)), eq(token));
        // }

        // @Test
        // @DisplayName("RC-TC-019: getById - nếu reviewerId null thì không gọi
        // userService và reviewerName null")
        // void testGetById_ReviewerIdNull_RC_TC_019() throws IdInvalidException {
        // // arrange
        // Long id = 9L;
        // ReviewCandidate r = review(id, 12L, null, 4, 4, 4, "S", "W", true);
        // when(reviewCandidateRepository.findById(id)).thenReturn(Optional.of(r));

        // // act
        // ReviewCandidateResponseDTO dto = reviewCandidateService.getById(id, "Bearer
        // token");

        // // assert
        // assertNotNull(dto);
        // assertEquals(null, dto.getReviewerName());
        // verify(reviewCandidateRepository, times(1)).findById(id);
        // verifyNoMoreInteractions(userService);
        // }

        // @Test
        // @DisplayName("RC-TC-020: getByCandidateId - khi body trả về không chứa key
        // thì reviewerName null")
        // void testGetByCandidateId_MissingKeyInResponse_RC_TC_020() throws Exception {
        // // arrange
        // Long candidateId = 4L;
        // ReviewCandidate r1 = review(901L, candidateId, 77L, 4, 4, 4, "S", "W", true);
        // when(reviewCandidateRepository.findByCandidate_Id(candidateId)).thenReturn(List.of(r1));

        // // response missing key "77"
        // when(userService.getEmployeeNames(eq(List.of(77L)), eq("Bearer token")))
        // .thenReturn(okBody("{}"));

        // // act
        // List<ReviewCandidateResponseDTO> result =
        // reviewCandidateService.getByCandidateId(candidateId,
        // "Bearer token");

        // // assert
        // assertNotNull(result);
        // assertEquals(1, result.size());
        // assertEquals(null, result.get(0).getReviewerName());
        // }

        // @Test
        // @DisplayName("RC-TC-021: getByCandidateId - review.candidate là null thì
        // candidateId null trong DTO")
        // void testGetByCandidateId_CandidateNull_RC_TC_021() {
        // // arrange
        // Long candidateId = 5L;
        // ReviewCandidate r1 = new ReviewCandidate();
        // r1.setId(1001L);
        // r1.setReviewerId(12L);
        // // candidate intentionally null
        // when(reviewCandidateRepository.findByCandidate_Id(candidateId)).thenReturn(List.of(r1));

        // // act
        // List<ReviewCandidateResponseDTO> result =
        // reviewCandidateService.getByCandidateId(candidateId, null);

        // // assert
        // assertNotNull(result);
        // assertEquals(1, result.size());
        // assertEquals(null, result.get(0).getCandidateId());
        // }

        // @Test
        // @DisplayName("RC-TC-022: update - khi review.reviewerId là null ném
        // IdInvalidException")
        // void testUpdate_ExistingReviewerIdNull_RC_TC_022() {
        // // arrange
        // Long id = 222L;
        // ReviewCandidate existing = review(id, 2L, null, 1, 1, 1, "S", "W", true);
        // when(reviewCandidateRepository.findById(id)).thenReturn(Optional.of(existing));

        // UpdateReviewCandidateDTO dto = new UpdateReviewCandidateDTO();
        // dto.setStrengths("new");

        // // act & assert: original production code calls equals on null -> NPE
        // assertThrows(NullPointerException.class, () ->
        // reviewCandidateService.update(id, dto, 7L));
        // }

        // @Test
        // @DisplayName("RC-TC-023: getById - token rỗng thì không gọi userService")
        // void testGetById_TokenEmpty_RC_TC_023() throws IdInvalidException {
        // // arrange
        // Long id = 333L;
        // ReviewCandidate r = review(id, 12L, 77L, 4, 4, 4, "S", "W", true);
        // when(reviewCandidateRepository.findById(id)).thenReturn(Optional.of(r));

        // // act
        // ReviewCandidateResponseDTO dto = reviewCandidateService.getById(id, "");

        // // assert
        // assertNotNull(dto);
        // assertEquals(null, dto.getReviewerName());
        // verifyNoMoreInteractions(userService);
        // }

        // @Test
        // @DisplayName("RC-TC-024: getAllWithFilters - token rỗng thì không gọi
        // userService")
        // void testGetAllWithFilters_TokenEmpty_RC_TC_024() {
        // // arrange
        // Long candidateId = 6L;
        // Long reviewerId = 88L;
        // LocalDateTime start = LocalDateTime.now().minusDays(2);
        // LocalDateTime end = LocalDateTime.now();
        // ReviewCandidate r1 = review(444L, candidateId, reviewerId, 3, 3, 3, "S", "W",
        // true);
        // Page<ReviewCandidate> mockPage = new PageImpl<>(List.of(r1),
        // PageRequest.of(0, 10), 1);
        // when(reviewCandidateRepository.findByFilters(eq(candidateId), eq(reviewerId),
        // eq(start), eq(end),
        // any(Pageable.class))).thenReturn(mockPage);

        // // act
        // PaginationDTO result = reviewCandidateService.getAllWithFilters(candidateId,
        // reviewerId, start, end,
        // 1, 10, null, null, "");

        // // assert
        // @SuppressWarnings("unchecked")
        // List<ReviewCandidateResponseDTO> list = (List<ReviewCandidateResponseDTO>)
        // result.getResult();
        // assertEquals(1, list.size());
        // assertEquals(null, list.get(0).getReviewerName());
        // verifyNoMoreInteractions(userService);
        // }

        // @Test
        // @DisplayName("RC-TC-025: update - DTO toàn null không thay đổi entity")
        // void testUpdate_AllNullDTO_RC_TC_025() throws IdInvalidException {
        // // arrange
        // Long id = 777L;
        // Long reviewerId = 7L;
        // ReviewCandidate existing = review(id, 3L, reviewerId, 4, 4, 4, "oldS",
        // "oldW", true);
        // when(reviewCandidateRepository.findById(id)).thenReturn(Optional.of(existing));

        // UpdateReviewCandidateDTO dto = new UpdateReviewCandidateDTO(); // all fields
        // null

        // when(reviewCandidateRepository.save(any(ReviewCandidate.class))).thenAnswer(inv
        // -> inv.getArgument(0));

        // // act
        // ReviewCandidateResponseDTO result = reviewCandidateService.update(id, dto,
        // reviewerId);

        // // assert: nothing changed
        // assertNotNull(result);
        // assertEquals(4, result.getProfessionalSkillScore());
        // assertEquals(4, result.getCommunicationSkillScore());
        // assertEquals(4, result.getWorkExperienceScore());
        // assertEquals((4.0 + 4.0 + 4.0) / 3.0, result.getAverageScore());
        // assertEquals("oldS", result.getStrengths());
        // assertEquals("oldW", result.getWeaknesses());
        // assertEquals(true, result.getConclusion());
        // }

        // @Test
        // @DisplayName("RC-TC-026: getAllWithFilters - userService trả 2xx nhưng body
        // null thì không enrich")
        // void testGetAllWithFilters_UserService2xxBodyNull_RC_TC_026() throws
        // Exception {
        // // arrange
        // Long candidateId = 9L;
        // Long reviewerId = 7L;
        // LocalDateTime start = LocalDateTime.now().minusDays(1);
        // LocalDateTime end = LocalDateTime.now();
        // ReviewCandidate r1 = review(100L, candidateId, reviewerId, 4, 4, 4, "S", "W",
        // true);
        // Page<ReviewCandidate> mockPage = new PageImpl<>(List.of(r1),
        // PageRequest.of(0, 10), 1);

        // when(reviewCandidateRepository.findByFilters(eq(candidateId), eq(reviewerId),
        // eq(start), eq(end),
        // any(Pageable.class))).thenReturn(mockPage);

        // when(userService.getEmployeeNames(eq(List.of(reviewerId)), eq("Bearer
        // token")))
        // .thenReturn(ResponseEntity.ok().body(null));

        // // act
        // PaginationDTO result = reviewCandidateService.getAllWithFilters(candidateId,
        // reviewerId, start, end,
        // 1, 10, null, null, "Bearer token");

        // // assert
        // @SuppressWarnings("unchecked")
        // List<ReviewCandidateResponseDTO> list = (List<ReviewCandidateResponseDTO>)
        // result.getResult();
        // assertEquals(1, list.size());
        // assertEquals(null, list.get(0).getReviewerName());
        // }

        // @Test
        // @DisplayName("RC-TC-027: getById - communicationScore null thì average không
        // tính")
        // void testGetById_CommunicationNull_RC_TC_027() throws Exception {
        // // arrange
        // Long id = 801L;
        // String token = "Bearer token";
        // ReviewCandidate r = review(id, 21L, 7L, 3, null, 5, "S", "W", true);
        // when(reviewCandidateRepository.findById(id)).thenReturn(Optional.of(r));
        // when(userService.getEmployeeNames(eq(List.of(7L)), eq(token)))
        // .thenReturn(okBody("{\"7\":\"Nguyen\"}"));

        // // act
        // ReviewCandidateResponseDTO dto = reviewCandidateService.getById(id, token);

        // // assert
        // assertNotNull(dto);
        // assertEquals(null, dto.getAverageScore());
        // assertEquals("Nguyen", dto.getReviewerName());
        // }

        // @Test
        // @DisplayName("RC-TC-028: getById - workExperienceScore null thì average không
        // tính")
        // void testGetById_WorkExpNull_RC_TC_028() throws Exception {
        // // arrange
        // Long id = 802L;
        // String token = "Bearer token";
        // ReviewCandidate r = review(id, 22L, 7L, 3, 4, null, "S", "W", true);
        // when(reviewCandidateRepository.findById(id)).thenReturn(Optional.of(r));
        // when(userService.getEmployeeNames(eq(List.of(7L)), eq(token)))
        // .thenReturn(okBody("{\"7\":\"Nguyen\"}"));

        // // act
        // ReviewCandidateResponseDTO dto = reviewCandidateService.getById(id, token);

        // // assert
        // assertNotNull(dto);
        // assertEquals(null, dto.getAverageScore());
        // assertEquals("Nguyen", dto.getReviewerName());
        // }

        // @Test
        // @DisplayName("RC-TC-029: getAllWithFilters - limit < 1 được normalize về 10")
        // void testGetAllWithFilters_LimitTooSmall_RC_TC_029() throws Exception {
        // // arrange
        // Long candidateId = 11L;
        // Long reviewerId = 7L;
        // LocalDateTime start = LocalDateTime.now().minusDays(7);
        // LocalDateTime end = LocalDateTime.now();
        // int page = 1;
        // int limit = 0; // should normalize to 10
        // String token = "Bearer token";

        // ReviewCandidate r1 = review(901L, candidateId, reviewerId, 4, 4, 4, "S", "W",
        // true);
        // Page<ReviewCandidate> mockPage = new PageImpl<>(List.of(r1),
        // PageRequest.of(0, 10), 1);

        // when(reviewCandidateRepository.findByFilters(eq(candidateId), eq(reviewerId),
        // eq(start), eq(end),
        // any(Pageable.class))).thenReturn(mockPage);
        // // THÊM DÒNG NÀY: Giả lập API gọi lấy tên thành công
        // when(userService.getEmployeeNames(eq(List.of(7L)), eq(token)))
        // .thenReturn(ResponseEntity.ok(objectMapper.createObjectNode().put("7",
        // "Nguyen Van A")));
        // // act
        // PaginationDTO result = reviewCandidateService.getAllWithFilters(candidateId,
        // reviewerId, start, end,
        // page, limit, null, null, token);

        // // assert
        // assertNotNull(result.getMeta());
        // assertEquals(10, result.getMeta().getPageSize());
        // }
}
