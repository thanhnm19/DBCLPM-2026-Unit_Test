package com.example.candidate_service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.example.candidate_service.dto.comment.CommentResponseDTO;
import com.example.candidate_service.dto.comment.CreateCommentDTO;
import com.example.candidate_service.dto.comment.UpdateCommentDTO;
import com.example.candidate_service.exception.IdInvalidException;
import com.example.candidate_service.model.Candidate;
import com.example.candidate_service.model.Comment;
import com.example.candidate_service.repository.CandidateRepository;
import com.example.candidate_service.repository.CommentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private CandidateRepository candidateRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private CommentService commentService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("CS-TC-030: getByCandidateId - lấy danh sách bình luận và enrich employeeName")
    void testGetByCandidateId_CS_TC_030() throws Exception {
        // Testcase ID: CS-TC-030
        // Objective: Xác nhận lấy danh sách bình luận và enrich employeeName

        // arrange
        Long candidateId = 1L;
        String token = "Bearer test-token";

        when(candidateRepository.existsById(candidateId)).thenReturn(true);

        LocalDateTime t1 = LocalDateTime.of(2026, 4, 19, 0, 0, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 4, 19, 0, 0, 1);

        Comment c1 = new Comment();
        c1.setId(10L);
        c1.setEmployeeId(5L);
        c1.setContent("Hello");
        c1.setCreatedAt(t1);

        Comment c2 = new Comment();
        c2.setId(11L);
        c2.setEmployeeId(6L);
        c2.setContent("Hi");
        c2.setCreatedAt(t2);

        when(commentRepository.findByCandidate_Id(candidateId)).thenReturn(Arrays.asList(c1, c2));

        ObjectNode idToName = objectMapper.createObjectNode();
        idToName.put("5", "Nguyen Van A");
        // intentionally leave "6" missing to cover null-branch in mapping
        when(userService.getEmployeeNames(anyList(), eq(token))).thenReturn(ResponseEntity.ok(idToName));

        // act
        List<CommentResponseDTO> result = commentService.getByCandidateId(candidateId, token);

        // assert
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(2, result.size());

        CommentResponseDTO d1 = result.get(0);
        assertEquals(10L, d1.getId());
        assertEquals(5L, d1.getEmployeeId());
        assertEquals("Hello", d1.getContent());
        assertEquals(t1, d1.getCreatedAt());
        assertEquals("Nguyen Van A", d1.getEmployeeName());

        CommentResponseDTO d2 = result.get(1);
        assertEquals(11L, d2.getId());
        assertEquals(6L, d2.getEmployeeId());
        assertEquals("Hi", d2.getContent());
        assertEquals(t2, d2.getCreatedAt());
        assertNull(d2.getEmployeeName());

        verify(candidateRepository, times(1)).existsById(candidateId);
        verify(commentRepository, times(1)).findByCandidate_Id(candidateId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Long>> idsCaptor = (ArgumentCaptor<List<Long>>) (ArgumentCaptor<?>) ArgumentCaptor
                .forClass(List.class);
        verify(userService, times(1)).getEmployeeNames(idsCaptor.capture(), eq(token));
        List<Long> capturedIds = idsCaptor.getValue();
        assertNotNull(capturedIds);
        assertEquals(2, capturedIds.size());
        assertTrue(capturedIds.containsAll(Arrays.asList(5L, 6L)));

        verifyNoMoreInteractions(candidateRepository, commentRepository, userService);
    }

    @Test
    @DisplayName("CS-TC-031: getByCandidateId - candidate không tồn tại ném IdInvalidException")
    void testGetByCandidateId_CandidateNotFound_CS_TC_031() {
        // Testcase ID: CS-TC-031
        // Objective: Xác nhận lỗi khi candidate không tồn tại

        // arrange
        Long candidateId = 999L;
        String token = "Bearer test-token";
        when(candidateRepository.existsById(candidateId)).thenReturn(false);

        // act
        IdInvalidException ex = assertThrows(IdInvalidException.class,
                () -> commentService.getByCandidateId(candidateId, token));

        // assert
        assertEquals("Ứng viên không tồn tại", ex.getMessage());

        verify(candidateRepository, times(1)).existsById(candidateId);
        verify(commentRepository, never()).findByCandidate_Id(anyLong());
        verify(userService, never()).getEmployeeNames(anyList(), anyString());
        verifyNoMoreInteractions(candidateRepository, commentRepository, userService);
    }

    @Test
    @DisplayName("CS-TC-032: getById - lấy đúng comment theo ID")
    void testGetById_CS_TC_032() throws Exception {
        // Testcase ID: CS-TC-032
        // Objective: Lấy đúng comment theo ID

        // arrange
        Long id = 1L;
        LocalDateTime t = LocalDateTime.of(2026, 4, 19, 0, 0, 0);

        Comment c = new Comment();
        c.setId(id);
        c.setEmployeeId(5L);
        c.setContent("Content");
        c.setCreatedAt(t);

        when(commentRepository.findById(id)).thenReturn(Optional.of(c));

        // act
        CommentResponseDTO result = commentService.getById(id);

        // assert
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(5L, result.getEmployeeId());
        assertEquals("Content", result.getContent());
        assertEquals(t, result.getCreatedAt());
        assertNull(result.getEmployeeName());

        verify(commentRepository, times(1)).findById(id);
        verifyNoMoreInteractions(commentRepository, candidateRepository, userService);
    }

    @Test
    @DisplayName("CS-TC-033: getById - comment không tồn tại ném IdInvalidException")
    void testGetById_NotFound_CS_TC_033() {
        // Testcase ID: CS-TC-033
        // Objective: Xác nhận lỗi khi comment không tồn tại

        // arrange
        Long id = 999L;
        when(commentRepository.findById(id)).thenReturn(Optional.empty());

        // act
        IdInvalidException ex = assertThrows(IdInvalidException.class, () -> commentService.getById(id));

        // assert
        assertEquals("Bình luận không tồn tại", ex.getMessage());

        verify(commentRepository, times(1)).findById(id);
        verifyNoMoreInteractions(commentRepository, candidateRepository, userService);
    }

    @Test
    @DisplayName("CS-TC-034: create - tạo bình luận thành công")
    void testCreate_CS_TC_034() throws Exception {
        // Testcase ID: CS-TC-034
        // Objective: Tạo bình luận thành công

        // arrange
        Long employeeId = 5L;

        CreateCommentDTO dto = new CreateCommentDTO();
        dto.setCandidateId(1L);
        dto.setContent("New comment");

        Candidate candidate = new Candidate();
        candidate.setId(1L);

        when(candidateRepository.findById(dto.getCandidateId())).thenReturn(Optional.of(candidate));

        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 19, 0, 0, 0);
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment arg = invocation.getArgument(0, Comment.class);
            arg.setId(100L);
            arg.setCreatedAt(createdAt);
            return arg;
        });

        // act
        CommentResponseDTO result = commentService.create(dto, employeeId);

        // assert
        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals(employeeId, result.getEmployeeId());
        assertEquals("New comment", result.getContent());
        assertEquals(createdAt, result.getCreatedAt());

        ArgumentCaptor<Comment> commentCaptor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository, times(1)).save(commentCaptor.capture());
        Comment savedEntity = commentCaptor.getValue();
        assertNotNull(savedEntity);
        assertNotNull(savedEntity.getCandidate());
        assertEquals(1L, savedEntity.getCandidate().getId());
        assertEquals(5L, savedEntity.getEmployeeId());
        assertEquals("New comment", savedEntity.getContent());

        verify(candidateRepository, times(1)).findById(1L);
        verifyNoMoreInteractions(commentRepository, candidateRepository, userService);
    }

    @Test
    @DisplayName("CS-TC-035: update - cập nhật nội dung bình luận thành công")
    void testUpdate_CS_TC_035() throws Exception {
        // Testcase ID: CS-TC-035
        // Objective: Cập nhật nội dung bình luận thành công

        // arrange
        Long id = 1L;
        Long employeeId = 5L;

        UpdateCommentDTO dto = new UpdateCommentDTO();
        dto.setContent("Updated");

        LocalDateTime t = LocalDateTime.of(2026, 4, 19, 0, 0, 0);

        Comment existing = new Comment();
        existing.setId(id);
        existing.setEmployeeId(employeeId);
        existing.setContent("Old");
        existing.setCreatedAt(t);

        when(commentRepository.findById(id)).thenReturn(Optional.of(existing));
        when(commentRepository.save(any(Comment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Comment.class));

        // act
        CommentResponseDTO result = commentService.update(id, dto, employeeId);

        // assert
        assertNotNull(result);
        assertEquals(id, result.getId());
        assertEquals(employeeId, result.getEmployeeId());
        assertEquals("Updated", result.getContent());
        assertEquals(t, result.getCreatedAt());
        assertNull(result.getEmployeeName());

        ArgumentCaptor<Comment> commentCaptor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository, times(1)).save(commentCaptor.capture());
        Comment saved = commentCaptor.getValue();
        assertEquals("Updated", saved.getContent());

        verify(commentRepository, times(1)).findById(id);
        verifyNoMoreInteractions(commentRepository, candidateRepository, userService);
    }

    @Test
    @DisplayName("CS-TC-036: delete - xóa bình luận thành công")
    void testDelete_CS_TC_036() throws Exception {
        // Testcase ID: CS-TC-036
        // Objective: Xóa bình luận thành công

        // arrange
        Long id = 1L;
        when(commentRepository.existsById(id)).thenReturn(true);

        // act
        commentService.delete(id);

        // assert
        verify(commentRepository, times(1)).existsById(id);
        verify(commentRepository, times(1)).deleteById(id);
        verifyNoMoreInteractions(commentRepository, candidateRepository, userService);
    }

    @Test
    @DisplayName("CS-TC-037: delete - comment không tồn tại ném IdInvalidException")
    void testDelete_NotFound_CS_TC_037() {
        // Testcase ID: CS-TC-037
        // Objective: Xác nhận lỗi khi xóa comment không tồn tại

        // arrange
        Long id = 999L;
        when(commentRepository.existsById(id)).thenReturn(false);

        // act
        IdInvalidException ex = assertThrows(IdInvalidException.class, () -> commentService.delete(id));

        // assert
        assertEquals("Bình luận không tồn tại", ex.getMessage());

        verify(commentRepository, times(1)).existsById(id);
        verify(commentRepository, never()).deleteById(anyLong());
        verifyNoMoreInteractions(commentRepository, candidateRepository, userService);
    }

    @Test
    @DisplayName("CS-TC-038: create - candidate không tồn tại ném IdInvalidException")
    void testCreate_CandidateNotFound_CS_TC_038() {
        // Testcase ID: CS-TC-038
        // Objective: Xác nhận lỗi khi tạo bình luận cho candidate không tồn tại

        CreateCommentDTO dto = new CreateCommentDTO();
        dto.setCandidateId(999L);
        dto.setContent("Should fail");

        when(candidateRepository.findById(dto.getCandidateId())).thenReturn(Optional.empty());

        IdInvalidException ex = assertThrows(IdInvalidException.class,
                () -> commentService.create(dto, 1L));

        assertEquals("Ứng viên không tồn tại", ex.getMessage());

        verify(candidateRepository, times(1)).findById(dto.getCandidateId());
        verify(commentRepository, never()).save(any(Comment.class));
        verifyNoMoreInteractions(candidateRepository, commentRepository, userService);
    }

    @Test
    @DisplayName("CS-TC-039: update - comment không tồn tại ném IdInvalidException")
    void testUpdate_NotFound_CS_TC_039() {
        // Testcase ID: CS-TC-039
        // Objective: Xác nhận lỗi khi cập nhật comment không tồn tại

        Long id = 999L;
        UpdateCommentDTO dto = new UpdateCommentDTO();
        dto.setContent("x");

        when(commentRepository.findById(id)).thenReturn(Optional.empty());

        IdInvalidException ex = assertThrows(IdInvalidException.class,
                () -> commentService.update(id, dto, 1L));

        assertEquals("Bình luận không tồn tại", ex.getMessage());

        verify(commentRepository, times(1)).findById(id);
        verify(commentRepository, never()).save(any(Comment.class));
        verifyNoMoreInteractions(commentRepository, candidateRepository, userService);
    }

    @Test
    @DisplayName("CS-TC-040: getByCandidateId - comments có employeeId null không gọi userService")
    void testGetByCandidateId_WithNullEmployeeIds_CS_TC_040() throws Exception {
        // Testcase ID: CS-TC-040
        // Objective: Khi tất cả comment có employeeId null, không gọi userService và
        // employeeName là null

        Long candidateId = 2L;
        String token = "Bearer t";

        when(candidateRepository.existsById(candidateId)).thenReturn(true);

        Comment c = new Comment();
        c.setId(20L);
        c.setEmployeeId(null);
        c.setContent("No employee");

        when(commentRepository.findByCandidate_Id(candidateId)).thenReturn(Arrays.asList(c));

        List<CommentResponseDTO> result = commentService.getByCandidateId(candidateId, token);

        assertNotNull(result);
        assertEquals(1, result.size());
        CommentResponseDTO d = result.get(0);
        assertEquals(20L, d.getId());
        assertNull(d.getEmployeeName());

        verify(candidateRepository, times(1)).existsById(candidateId);
        verify(commentRepository, times(1)).findByCandidate_Id(candidateId);
        verify(userService, never()).getEmployeeNames(anyList(), anyString());
        verifyNoMoreInteractions(candidateRepository, commentRepository, userService);
    }

    @Test
    @DisplayName("CS-TC-041: getByCandidateId - tất cả employeeName có trong idToName")
    void testGetByCandidateId_AllNamesPresent_CS_TC_041() throws Exception {
        // Testcase ID: CS-TC-041
        // Objective: Khi tất cả employeeId có tên trong idToName, employeeName được set

        Long candidateId = 3L;
        String token = "Bearer t";
        when(candidateRepository.existsById(candidateId)).thenReturn(true);

        Comment c1 = new Comment();
        c1.setId(30L);
        c1.setEmployeeId(7L);
        c1.setContent("A");

        Comment c2 = new Comment();
        c2.setId(31L);
        c2.setEmployeeId(8L);
        c2.setContent("B");

        when(commentRepository.findByCandidate_Id(candidateId)).thenReturn(Arrays.asList(c1, c2));

        ObjectNode idToName = objectMapper.createObjectNode();
        idToName.put("7", "E7");
        idToName.put("8", "E8");
        when(userService.getEmployeeNames(anyList(), eq(token))).thenReturn(ResponseEntity.ok(idToName));

        List<CommentResponseDTO> result = commentService.getByCandidateId(candidateId, token);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("E7", result.get(0).getEmployeeName());
        assertEquals("E8", result.get(1).getEmployeeName());

        verify(candidateRepository, times(1)).existsById(candidateId);
        verify(commentRepository, times(1)).findByCandidate_Id(candidateId);
        verify(userService, times(1)).getEmployeeNames(anyList(), eq(token));
        verifyNoMoreInteractions(candidateRepository, commentRepository, userService);
    }

    @Test
    @DisplayName("CS-TC-042: update - khi dto.content null, nội dung giữ nguyên")
    void testUpdate_WithNullContent_CS_TC_042() throws Exception {
        // Testcase ID: CS-TC-042
        // Objective: Khi dto.content = null, update không thay đổi content

        Long id = 2L;
        Long employeeId = 5L;

        UpdateCommentDTO dto = new UpdateCommentDTO();
        dto.setContent(null);

        LocalDateTime t = LocalDateTime.of(2026, 4, 19, 0, 0, 0);

        Comment existing = new Comment();
        existing.setId(id);
        existing.setEmployeeId(employeeId);
        existing.setContent("Original");
        existing.setCreatedAt(t);

        when(commentRepository.findById(id)).thenReturn(Optional.of(existing));
        when(commentRepository.save(any(Comment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Comment.class));

        CommentResponseDTO result = commentService.update(id, dto, employeeId);

        assertNotNull(result);
        assertEquals("Original", result.getContent());

        ArgumentCaptor<Comment> commentCaptor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository, times(1)).save(commentCaptor.capture());
        Comment saved = commentCaptor.getValue();
        assertEquals("Original", saved.getContent());

        verify(commentRepository, times(1)).findById(id);
        verifyNoMoreInteractions(commentRepository, candidateRepository, userService);
    }
}