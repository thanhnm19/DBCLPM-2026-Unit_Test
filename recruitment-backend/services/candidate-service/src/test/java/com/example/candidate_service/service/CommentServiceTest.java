package com.example.candidate_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.candidate_service.dto.comment.CommentResponseDTO;
import com.example.candidate_service.dto.comment.CreateCommentDTO;
import com.example.candidate_service.dto.comment.UpdateCommentDTO;
import com.example.candidate_service.exception.IdInvalidException;
import com.example.candidate_service.model.Candidate;
import com.example.candidate_service.model.Comment;
import com.example.candidate_service.repository.CandidateRepository;
import com.example.candidate_service.repository.CommentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@DisplayName("CommentService Unit Test")
@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private CandidateRepository candidateRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private CommentService commentService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Helper methods
    private Candidate buildCandidate(Long id) {
        Candidate c = new Candidate();
        c.setId(id);
        return c;
    }

    private Comment buildComment(Long id, Long candidateId, Long employeeId, String content) {
        Comment c = new Comment();
        c.setId(id);
        c.setEmployeeId(employeeId);
        c.setContent(content);
        c.setCandidate(buildCandidate(candidateId));
        // createdAt may be set by JPA auditing; set if setter exists
        try {
            c.getClass().getMethod("setCreatedAt", Instant.class).invoke(c, Instant.parse("2026-04-17T00:00:00Z"));
        } catch (Exception ignored) {
            // ignore if model doesn't expose setCreatedAt
        }
        return c;
    }

    private CreateCommentDTO buildCreateDTO(Long candidateId, String content) {
        CreateCommentDTO dto = new CreateCommentDTO();
        dto.setCandidateId(candidateId);
        dto.setContent(content);
        return dto;
    }

    private UpdateCommentDTO buildUpdateDTO(String content) {
        UpdateCommentDTO dto = new UpdateCommentDTO();
        dto.setContent(content);
        return dto;
    }

    @Test
    @DisplayName("CMS-TC01: getByCandidateId - lấy danh sách comment theo candidateId và map employeeName đúng")
    void CMS_TC01_getByCandidateId_shouldReturnMappedComments_andBatchFetchEmployeeNames() throws Exception {
        // CMS-TC01: Ensure candidate exists + batch lấy employee names + mapping DTO
        // (join employeeName)
        Long candidateId = 10L;
        String token = "Bearer test-token";

        Comment c1 = buildComment(1L, candidateId, 100L, "note-1");
        Comment c2 = buildComment(2L, candidateId, 200L, "note-2");

        JsonNode idToName = objectMapper.readTree("{\"100\":\"Alice\",\"200\":\"Bob\"}");

        when(candidateRepository.existsById(candidateId)).thenReturn(true);
        when(commentRepository.findByCandidate_Id(candidateId)).thenReturn(List.of(c1, c2));
        when(userService.getEmployeeNames(eq(List.of(100L, 200L)), eq(token)))
                .thenReturn(new ResponseEntity<>(idToName, HttpStatus.OK));

        List<CommentResponseDTO> result = commentService.getByCandidateId(candidateId, token);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(CommentResponseDTO::getId).containsExactly(1L, 2L);
        assertThat(result).extracting(CommentResponseDTO::getEmployeeId).containsExactly(100L, 200L);
        assertThat(result).extracting(CommentResponseDTO::getContent).containsExactly("note-1", "note-2");
        assertThat(result).extracting(CommentResponseDTO::getEmployeeName).containsExactly("Alice", "Bob");

        verify(candidateRepository, times(1)).existsById(candidateId);
        verify(commentRepository, times(1)).findByCandidate_Id(candidateId);
        verify(userService, times(1)).getEmployeeNames(eq(List.of(100L, 200L)), eq(token));
    }

    @Test
    @DisplayName("CMS-TC01: getByCandidateId - lấy danh sách comment theo candidateId và map employeeName đúng")
    void CMS_TC01_getByCandidateId_whenCandidateNotExists_shouldThrow() {
        // CMS-TC01: Candidate không tồn tại → throw exception
        Long candidateId = 404L;

        when(candidateRepository.existsById(candidateId)).thenReturn(false);

        assertThatThrownBy(() -> commentService.getByCandidateId(candidateId, "token"))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Ứng viên không tồn tại");

        verify(candidateRepository, times(1)).existsById(candidateId);
        verify(commentRepository, never()).findByCandidate_Id(any());
        verify(userService, never()).getEmployeeNames(any(), any());
    }

    @Test
    @DisplayName("CMS-TC01: getByCandidateId - lấy danh sách comment theo candidateId và map employeeName đúng")
    void CMS_TC01_getByCandidateId_whenNoComments_shouldReturnEmptyList_andNotCallUserService() throws Exception {
        // CMS-TC01: Comments rỗng → trả về list rỗng
        Long candidateId = 11L;

        when(candidateRepository.existsById(candidateId)).thenReturn(true);
        when(commentRepository.findByCandidate_Id(candidateId)).thenReturn(List.of());

        List<CommentResponseDTO> result = commentService.getByCandidateId(candidateId, "token");

        assertThat(result).isNotNull();
        assertThat(result).isEmpty();

        verify(candidateRepository, times(1)).existsById(candidateId);
        verify(commentRepository, times(1)).findByCandidate_Id(candidateId);
        verify(userService, never()).getEmployeeNames(any(), any());
    }

    @Test
    @DisplayName("CMS-TC02: getById - id tồn tại trả CommentDTO, id không tồn tại ném IdInvalidException")
    void CMS_TC02_getById_whenExists_shouldReturnCommentResponse() throws Exception {
        // CMS-TC02: ID tồn tại → trả về comment
        Comment c = buildComment(1L, 10L, 100L, "hello");
        when(commentRepository.findById(1L)).thenReturn(Optional.of(c));

        CommentResponseDTO dto = commentService.getById(1L);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getEmployeeId()).isEqualTo(100L);
        assertThat(dto.getContent()).isEqualTo("hello");

        verify(commentRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("CMS-TC02: getById - id tồn tại trả CommentDTO, id không tồn tại ném IdInvalidException")
    void CMS_TC02_getById_whenNotExists_shouldThrow() {
        // CMS-TC02: ID không tồn tại → throw exception
        when(commentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.getById(999L))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Bình luận không tồn tại");

        verify(commentRepository, times(1)).findById(999L);
    }

    @Test
    @DisplayName("CMS-TC03: create - dữ liệu hợp lệ -> tạo mới comment và gán employeeId đúng")
    void CMS_TC03_create_whenValid_shouldAttachFieldsAndSave() throws Exception {
        // CMS-TC03: Input hợp lệ → save thành công (gắn candidate + employeeId +
        // content)
        Long candidateId = 10L;
        Long employeeId = 123L;
        CreateCommentDTO dto = buildCreateDTO(candidateId, "new-comment");

        Candidate candidate = buildCandidate(candidateId);

        when(candidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> {
            Comment arg = inv.getArgument(0);
            arg.setId(77L);
            return arg;
        });

        CommentResponseDTO saved = commentService.create(dto, employeeId);

        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isEqualTo(77L);
        assertThat(saved.getEmployeeId()).isEqualTo(employeeId);
        assertThat(saved.getContent()).isEqualTo("new-comment");

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository, times(1)).save(captor.capture());
        Comment toSave = captor.getValue();
        assertThat(toSave.getCandidate()).isNotNull();
        assertThat(toSave.getCandidate().getId()).isEqualTo(candidateId);
        assertThat(toSave.getEmployeeId()).isEqualTo(employeeId);
        assertThat(toSave.getContent()).isEqualTo("new-comment");

        verify(candidateRepository, times(1)).findById(candidateId);
    }

    @Test
    @DisplayName("CMS-TC03: create - dữ liệu hợp lệ -> tạo mới comment và gán employeeId đúng")
    void CMS_TC03_create_whenCandidateNotExists_shouldThrow() {
        // CMS-TC03: Candidate không tồn tại → throw exception
        Long candidateId = 404L;
        CreateCommentDTO dto = buildCreateDTO(candidateId, "x");

        when(candidateRepository.findById(candidateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.create(dto, 1L))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Ứng viên không tồn tại");

        verify(candidateRepository, times(1)).findById(candidateId);
        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("CMS-TC04: update - chỉ cập nhật các field có giá trị và trả về comment đã cập nhật")
    void CMS_TC04_update_whenContentProvided_shouldUpdateAndSave() throws Exception {
        // CMS-TC04: Update thành công khi có field hợp lệ
        Comment existing = buildComment(5L, 10L, 100L, "old");
        when(commentRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCommentDTO dto = buildUpdateDTO("new");
        CommentResponseDTO updated = commentService.update(5L, dto, 999L);

        assertThat(updated.getId()).isEqualTo(5L);
        assertThat(updated.getContent()).isEqualTo("new");

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("new");

        verify(commentRepository, times(1)).findById(5L);
    }

    @Test
    @DisplayName("CMS-TC04: update - chỉ cập nhật các field có giá trị và trả về comment đã cập nhật")
    void CMS_TC04_update_whenContentNull_shouldNotOverwriteExistingContent() throws Exception {
        // CMS-TC04: DTO có field null → không overwrite
        Comment existing = buildComment(6L, 10L, 100L, "keep-me");
        when(commentRepository.findById(6L)).thenReturn(Optional.of(existing));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCommentDTO dto = buildUpdateDTO(null);
        CommentResponseDTO updated = commentService.update(6L, dto, 999L);

        assertThat(updated.getId()).isEqualTo(6L);
        assertThat(updated.getContent()).isEqualTo("keep-me");

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("keep-me");

        verify(commentRepository, times(1)).findById(6L);
    }

    @Test
    @DisplayName("CMS-TC04: update - chỉ cập nhật các field có giá trị và trả về comment đã cập nhật")
    void CMS_TC04_update_whenCommentNotExists_shouldThrow() {
        // CMS-TC04: Comment không tồn tại → throw exception
        when(commentRepository.findById(999L)).thenReturn(Optional.empty());

        UpdateCommentDTO dto = buildUpdateDTO("x");

        assertThatThrownBy(() -> commentService.update(999L, dto, 1L))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Bình luận không tồn tại");

        verify(commentRepository, times(1)).findById(999L);
        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("CMS-TC05: delete - id tồn tại xóa thành công, id không tồn tại ném IdInvalidException")
    void CMS_TC05_delete_whenExists_shouldDeleteById() throws Exception {
        // CMS-TC05: ID tồn tại → delete thành công
        when(commentRepository.existsById(1L)).thenReturn(true);

        commentService.delete(1L);

        verify(commentRepository, times(1)).existsById(1L);
        verify(commentRepository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("CMS-TC05: delete - id tồn tại xóa thành công, id không tồn tại ném IdInvalidException")
    void CMS_TC05_delete_whenNotExists_shouldThrow() {
        // CMS-TC05: ID không tồn tại → throw exception
        when(commentRepository.existsById(2L)).thenReturn(false);

        assertThatThrownBy(() -> commentService.delete(2L))
                .isInstanceOf(IdInvalidException.class)
                .hasMessageContaining("Bình luận không tồn tại");

        verify(commentRepository, times(1)).existsById(2L);
        verify(commentRepository, never()).deleteById(any());
    }
}
