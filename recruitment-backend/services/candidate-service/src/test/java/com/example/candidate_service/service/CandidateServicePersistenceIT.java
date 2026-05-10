package com.example.candidate_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.example.candidate_service.dto.candidate.CreateCandidateDTO;
import com.example.candidate_service.dto.candidate.UpdateCandidateDTO;
import com.example.candidate_service.exception.IdInvalidException;
import com.example.candidate_service.model.Candidate;
import com.example.candidate_service.repository.CandidateRepository;
import com.example.candidate_service.utils.enums.CandidateStatus;

/**
 * Integration tests against H2 ({@code application-test}): real {@link CandidateRepository} and
 * {@link CandidateService}, external collaborators mocked. Each test method runs in a transaction that
 * rolls back automatically so the database stays clean.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CandidateServicePersistenceIT {

        @Autowired
        private CandidateRepository candidateRepository;

        @Autowired
        private CandidateService candidateService;

        @MockBean
        private JobService jobService;

        @MockBean
        private ScheduleService communicationService;

        @MockBean
        private CommentService commentService;

        @MockBean
        private ReviewCandidateService reviewCandidateService;

        @MockBean
        private UserService userService;

        @MockBean
        private KafkaTemplate<String, String> kafkaTemplate;

        @BeforeEach
        void clearCandidates() {
                candidateRepository.deleteAll();
        }

        @Test
        @DisplayName("CS-INT-001: findByEmail — có bản ghi trong DB")
        void findByEmail_returnsMappedEntity_whenRowExistsInDatabase() throws Exception {
                // Test Case ID: CS-INT-001

                // Arrange
                Candidate seeded = new Candidate();
                seeded.setName("Integration A");
                seeded.setEmail("int-a@gmail.com");
                seeded.setPhone("0900000001");
                seeded.setJobPositionId(101L);
                seeded.setAppliedDate(LocalDate.of(2026, 2, 1));
                seeded.setStatus(CandidateStatus.SUBMITTED);
                Candidate persisted = candidateRepository.save(seeded);

                // Act
                Candidate found = candidateService.findByEmail("int-a@gmail.com");

                // Assert
                assertThat(found.getId()).isEqualTo(persisted.getId());
                assertThat(found.getEmail()).isEqualTo("int-a@gmail.com");

                // CheckDB — đọc lại từ persistence layer
                assertThat(candidateRepository.findByEmail("int-a@gmail.com"))
                                .isPresent()
                                .get()
                                .extracting(Candidate::getName, Candidate::getStatus)
                                .containsExactly("Integration A", CandidateStatus.SUBMITTED);
        }

        @Test
        @DisplayName("CS-INT-002: findByEmail — không có bản ghi")
        void findByEmail_throws_whenNoRowInDatabase() {
                // Test Case ID: CS-INT-002

                // Arrange — không seed

                // Act & Assert
                assertThatThrownBy(() -> candidateService.findByEmail("ghost@gmail.com"))
                                .isInstanceOf(IdInvalidException.class)
                                .hasMessageContaining("ứng viên không tồn tại");

                // CheckDB
                assertThat(candidateRepository.findByEmail("ghost@gmail.com")).isEmpty();
        }

        @Test
        @DisplayName("CS-INT-003: create — insert và đọc lại DB")
        void create_persistsNewCandidate_withExpectedDefaults() throws Exception {
                // Test Case ID: CS-INT-003

                // Arrange
                CreateCandidateDTO dto = new CreateCandidateDTO();
                dto.setName("New Hire");
                dto.setEmail("newhire@gmail.com");
                dto.setPhone("0911111111");
                dto.setJobPositionId(202L);
                dto.setCvUrl("http://cv.pdf");
                dto.setNotes("note");
                dto.setCreatedBy(42L);

                // Act
                Candidate saved = candidateService.create(dto);

                // Assert
                assertThat(saved.getEmail()).isEqualTo("newhire@gmail.com");
                assertThat(saved.getStatus()).isEqualTo(CandidateStatus.SUBMITTED);

                // CheckDB — truy vấn lại theo email (đảm bảo đã flush xuống DB)
                Candidate fromDb = candidateRepository.findByEmail("newhire@gmail.com").orElseThrow();
                assertThat(fromDb.getId()).isNotNull();
                assertThat(fromDb.getName()).isEqualTo("New Hire");
                assertThat(fromDb.getStatus()).isEqualTo(CandidateStatus.SUBMITTED);
                // @PrePersist ghi đè createdBy bằng SecurityUtil.extractEmployeeId() (không có JWT trong IT)
                assertThat(fromDb.getCreatedBy()).isNull();
        }

        @Test
        @DisplayName("CS-INT-004: create — trùng email + jobPosition không tạo thêm dòng")
        void create_rejectsDuplicate_andDatabaseRowCountUnchanged() throws Exception {
                // Test Case ID: CS-INT-004

                // Arrange
                CreateCandidateDTO dto = new CreateCandidateDTO();
                dto.setName("Dup");
                dto.setEmail("dup@gmail.com");
                dto.setPhone("0922222222");
                dto.setJobPositionId(303L);
                dto.setCvUrl("http://a.pdf");
                dto.setNotes("n");
                dto.setCreatedBy(1L);
                candidateService.create(dto);
                long countAfterFirst = candidateRepository.count();

                CreateCandidateDTO duplicate = new CreateCandidateDTO();
                duplicate.setName("Dup2");
                duplicate.setEmail("dup@gmail.com");
                duplicate.setPhone("0933333333");
                duplicate.setJobPositionId(303L);
                duplicate.setCvUrl("http://b.pdf");
                duplicate.setNotes("n2");
                duplicate.setCreatedBy(1L);

                // Act & Assert
                assertThatThrownBy(() -> candidateService.create(duplicate))
                                .isInstanceOf(IdInvalidException.class)
                                .hasMessageContaining("Ứng viên đã nộp hồ sơ cho vị trí này");

                // CheckDB — không thêm bản ghi
                assertThat(candidateRepository.count()).isEqualTo(countAfterFirst);
        }

        @Test
        @DisplayName("CS-INT-005: update — field đổi trên DB")
        void update_partialFields_reflectedInDatabase() throws Exception {
                // Test Case ID: CS-INT-005

                // Arrange
                Candidate existing = new Candidate();
                existing.setName("Old");
                existing.setEmail("old@gmail.com");
                existing.setPhone("000");
                existing.setJobPositionId(101L);
                existing = candidateRepository.save(existing);
                Long id = existing.getId();

                UpdateCandidateDTO dto = new UpdateCandidateDTO();
                dto.setName("New Name");
                dto.setDateOfBirth("2000-02-29");

                // Act
                candidateService.update(id, dto);

                // Assert — service đã trả DTO; kiểm tra DB
                // CheckDB
                Candidate fromDb = candidateRepository.findById(id).orElseThrow();
                assertThat(fromDb.getName()).isEqualTo("New Name");
                assertThat(fromDb.getDateOfBirth()).isEqualTo(LocalDate.of(2000, 2, 29));
                assertThat(fromDb.getPhone()).isEqualTo("000");
        }

        @Test
        @DisplayName("CS-INT-006: delete — bản ghi biến mất khỏi DB")
        void delete_removesRowFromDatabase() throws Exception {
                // Test Case ID: CS-INT-006

                // Arrange
                Candidate row = new Candidate();
                row.setName("ToDelete");
                row.setEmail("del@gmail.com");
                row.setPhone("0944444444");
                row.setJobPositionId(101L);
                row = candidateRepository.save(row);
                Long id = row.getId();

                // Act
                candidateService.delete(id);

                // Assert
                // CheckDB
                assertThat(candidateRepository.findById(id)).isEmpty();
        }

        @Test
        @DisplayName("CS-INT-007: updateCandidateStatus — REJECTED lưu lý do trên DB")
        void updateCandidateStatus_rejected_persistsReasonInDatabase() throws Exception {
                // Test Case ID: CS-INT-007

                // Arrange
                Candidate row = new Candidate();
                row.setName("R");
                row.setEmail("r@gmail.com");
                row.setPhone("0955555555");
                row.setJobPositionId(101L);
                row.setStatus(CandidateStatus.SUBMITTED);
                row = candidateRepository.save(row);
                Long id = row.getId();

                // Act
                candidateService.updateCandidateStatus(id, "REJECTED", "Không phù hợp", 100L);

                // Assert
                // CheckDB
                Candidate fromDb = candidateRepository.findById(id).orElseThrow();
                assertThat(fromDb.getStatus()).isEqualTo(CandidateStatus.REJECTED);
                assertThat(fromDb.getRejectionReason()).isEqualTo("Không phù hợp");
                assertThat(fromDb.getUpdatedBy()).isEqualTo(100L);
        }
}
